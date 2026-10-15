package com.albumdone.app.people

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One face's identity fingerprint (SFace, 128 numbers, length 1). */
class FaceVector(
    val faceId: Long,
    val photoId: Long,
    val embedding: FloatArray,
    /** 0..1: big, sharp, front-facing faces are the most reliable. */
    val quality: Float,
)

/**
 * Groups faces into people. Pure Kotlin, unit-tested.
 *
 * Faces are taken best first and joined to the closest existing person when similar enough
 * (cosine similarity to that person's average), otherwise they start a new person. People whose
 * averages are very close are then merged, and every face is checked once more against the
 * final averages. Poor faces (tiny, blurred, side-on) only join an existing person.
 */
object PeopleClusterer {

    /** OpenCV's same-person threshold for SFace is 0.363; joining an average is a bit stricter. */
    const val JOIN = 0.40f
    const val MERGE = 0.52f
    const val SPLIT = 0.55f
    const val FIND_MORE = 0.30f
    const val GOOD_FACE = 0.35f

    /**
     * Face ids per person, biggest person first. Faces that match nobody are left out.
     * Nobody appears twice in one photo: two faces in the same photo are always two people.
     */
    fun cluster(faces: List<FaceVector>, join: Float = JOIN): List<List<Long>> {
        if (faces.isEmpty()) return emptyList()
        val dim = faces.first().embedding.size
        val ordered = faces.sortedByDescending { it.quality }
        val sums = ArrayList<FloatArray>()
        val members = ArrayList<MutableList<FaceVector>>()
        val photos = ArrayList<MutableSet<Long>>()

        for (f in ordered) {
            val needed = if (f.quality >= GOOD_FACE) join else join + 0.05f
            val best = ranked(f.embedding, sums).firstOrNull { (k, sim) -> sim >= needed && f.photoId !in photos[k] }?.first
            if (best != null) {
                add(sums[best], f.embedding)
                members[best] += f
                photos[best] += f.photoId
            } else if (f.quality >= GOOD_FACE) {
                sums += f.embedding.copyOf()
                members += mutableListOf(f)
                photos += mutableSetOf(f.photoId)
            }
        }

        // Merge people whose averages are nearly the same (the same person seen in different light),
        // unless they were ever in the same photo. Union-find; only groups of 2+ faces start a merge.
        val parent = IntArray(sums.size) { it }
        fun root(i: Int): Int { var r = i; while (parent[r] != r) r = parent[r]; parent[i] = r; return r }
        val unit = sums.map { normalized(it) }
        val groupPhotos = HashMap<Int, MutableSet<Long>>()
        fun photosOf(r: Int) = groupPhotos.getOrPut(r) { photos[r].toMutableSet() }
        for (i in sums.indices) {
            if (members[i].size < 2) continue
            for (j in sums.indices) {
                if (j == i || (members[j].size >= 2 && j < i)) continue
                if (dot(unit[i], unit[j]) >= MERGE) {
                    val ri = root(i); val rj = root(j)
                    if (ri == rj || photosOf(ri).any { it in photosOf(rj) }) continue
                    parent[rj] = ri
                    photosOf(ri).addAll(photosOf(rj))
                }
            }
        }
        for (i in sums.indices) {
            val r = root(i)
            if (r != i && members[i].isNotEmpty()) {
                add(sums[r], sums[i])
                members[r].addAll(members[i])
                members[i].clear()
            }
        }

        // One more pass: every face goes to the person it is now closest to, the surest faces first,
        // and never to someone who already has a face in that photo.
        val alive = sums.indices.filter { members[it].isNotEmpty() }
        val centres = alive.map { normalized(sums[it]) }
        val choices = faces.map { f ->
            val needed = if (f.quality >= GOOD_FACE) join else join + 0.05f
            f to centres.indices.map { k -> k to dot(f.embedding, centres[k]) }.filter { it.second >= needed }.sortedByDescending { it.second }
        }.sortedByDescending { (_, c) -> c.firstOrNull()?.second ?: -2f }
        val result = HashMap<Int, MutableList<Long>>()
        val taken = HashMap<Int, MutableSet<Long>>()
        for ((f, options) in choices) {
            val k = options.firstOrNull { (k, _) -> f.photoId !in taken.getOrPut(k) { mutableSetOf() } }?.first ?: continue
            result.getOrPut(k) { mutableListOf() } += f.faceId
            taken.getValue(k) += f.photoId
        }
        check(dim > 0)
        return result.values.sortedByDescending { it.size }
    }

    /** Existing people by similarity to [e], most similar first. */
    private fun ranked(e: FloatArray, sums: List<FloatArray>): List<Pair<Int, Float>> =
        sums.indices.map { k ->
            val s = sums[k]
            var d = 0.0; var n = 0.0
            for (i in e.indices) { d += e[i] * s[i]; n += s[i] * s[i] }
            k to if (n <= 0) -2f else (d / sqrt(n)).toFloat()
        }.sortedByDescending { it.second }

    /** Splits one wrongly-joined person into tighter groups, biggest first. */
    fun split(faces: List<FaceVector>): List<List<Long>> {
        val groups = cluster(faces.map { FaceVector(it.faceId, it.photoId, it.embedding, max(it.quality, GOOD_FACE)) }, SPLIT)
        val placed = groups.flatten().toSet()
        val leftovers = faces.map { it.faceId }.filter { it !in placed }
        return if (leftovers.isEmpty()) groups else groups.toMutableList().also {
            if (it.isEmpty()) it += leftovers else it[0] = it[0] + leftovers
        }
    }

    /**
     * "Find more of this person": faces that look like [person] a little less strictly,
     * taken only from faces that are unmatched or sit in tiny groups (likely mistakes).
     */
    fun findMore(
        person: List<FaceVector>,
        candidates: List<FaceVector>,
        candidateGroupSize: (Long) -> Int,
    ): List<Long> {
        if (person.isEmpty()) return emptyList()
        val sum = FloatArray(person.first().embedding.size)
        person.forEach { add(sum, it.embedding) }
        val centre = normalized(sum)
        val own = person.map { it.faceId }.toSet()
        val ownPhotos = person.map { it.photoId }.toSet()
        return candidates
            .filter { it.faceId !in own && it.photoId !in ownPhotos && candidateGroupSize(it.faceId) <= 2 }
            .filter { dot(it.embedding, centre) >= FIND_MORE }
            .map { it.faceId }
    }

    /**
     * The couple: the two people seen together most where a couple is together, at the wedding
     * rituals and the reception. Photos count by where they were taken ([weightOf]): at the
     * bride's own functions (haldi, adhibaas, mehendi) her mother or aunt is beside her all day
     * while the groom is not even there, so those count least. A photo of just the two of them
     * ([twoShots]) counts extra.
     *
     * @param photosOf person -> photos they are clearly visible in
     * @param brideHints photos from the bride's own functions, to tell the bride from the groom
     * @return (bride, groom), or null when no two people appear together twice
     */
    fun detectCouple(
        photosOf: Map<Long, Set<Long>>,
        brideHints: Set<Long> = emptySet(),
        weightOf: (Long) -> Double = { 1.0 },
        twoShots: Set<Long> = emptySet(),
    ): Pair<Long, Long>? {
        fun weighted(photos: Set<Long>) = photos.sumOf { weightOf(it) }
        val top = photosOf.entries.sortedByDescending { weighted(it.value) }.take(8)
        if (top.size < 2) return null
        val weddingPhotos = photosOf.values.flatten().any { weightOf(it) >= WEDDING_WEIGHT }
        var best: Pair<Long, Long>? = null
        var bestScore = -1.0
        for (i in top.indices) for (j in i + 1 until top.size) {
            val a = top[i]; val b = top[j]
            val shared = a.value.filter { it in b.value }
            if (shared.size < 2) continue
            val together = shared.sumOf { weightOf(it) * (if (it in twoShots) 1.5 else 1.0) }
            var score = 2.0 * together + min(weighted(a.value), weighted(b.value))
            // Never at the wedding or reception together: not the couple (a mother and daughter at haldi).
            if (weddingPhotos && shared.none { weightOf(it) >= WEDDING_WEIGHT }) score *= 0.3
            if (score > bestScore) { bestScore = score; best = a.key to b.key }
        }
        val (a, b) = best ?: return null
        val aHints = photosOf.getValue(a).count { it in brideHints }
        val bHints = photosOf.getValue(b).count { it in brideHints }
        return when {
            aHints > bHints -> a to b
            bHints > aHints -> b to a
            photosOf.getValue(a).size >= photosOf.getValue(b).size -> a to b
            else -> b to a
        }
    }

    /**
     * Whether the couple found is clear enough not to ask the couple: several close-up photos of
     * just the two of them ([portraits]), and nobody else nearly as often beside the bride.
     */
    fun coupleSure(
        photosOf: Map<Long, Set<Long>>,
        couple: Pair<Long, Long>,
        weightOf: (Long) -> Double,
        portraits: Set<Long>,
    ): Boolean {
        val (bride, groom) = couple
        val withBride = photosOf[bride] ?: return false
        fun together(other: Long) = (photosOf[other] ?: emptySet()).filter { it in withBride }
        val pair = together(groom)
        if (pair.count { it in portraits } < 3) return false
        val score = pair.sumOf { weightOf(it) }
        val next = photosOf.keys.filter { it != bride && it != groom }.maxOfOrNull { o -> together(o).sumOf { weightOf(it) } } ?: 0.0
        return score >= 1.5 * next
    }

    /** How much a photo together counts towards being the couple, by the kind of function. */
    const val WEDDING_WEIGHT = 2.0
    const val OWN_FUNCTION_WEIGHT = 0.4

    fun cosine(a: FloatArray, b: FloatArray): Float {
        var d = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        if (na <= 0 || nb <= 0) return 0f
        return (d / (sqrt(na) * sqrt(nb))).toFloat()
    }

    private fun add(into: FloatArray, v: FloatArray) { for (i in into.indices) into[i] += v[i] }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var d = 0f
        for (i in a.indices) d += a[i] * b[i]
        return d
    }

    private fun normalized(v: FloatArray): FloatArray {
        var s = 0.0
        for (x in v) s += x * x
        val n = sqrt(s).toFloat()
        return if (n <= 1e-9f) v.copyOf() else FloatArray(v.size) { v[it] / n }
    }
}
