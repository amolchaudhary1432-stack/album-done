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

    /** Face ids per person, biggest person first. Faces that match nobody are left out. */
    fun cluster(faces: List<FaceVector>, join: Float = JOIN): List<List<Long>> {
        if (faces.isEmpty()) return emptyList()
        val dim = faces.first().embedding.size
        val ordered = faces.sortedByDescending { it.quality }
        val sums = ArrayList<FloatArray>()
        val members = ArrayList<MutableList<FaceVector>>()

        for (f in ordered) {
            val (best, sim) = closest(f.embedding, sums)
            val needed = if (f.quality >= GOOD_FACE) join else join + 0.05f
            if (best >= 0 && sim >= needed) {
                add(sums[best], f.embedding)
                members[best] += f
            } else if (f.quality >= GOOD_FACE) {
                sums += f.embedding.copyOf()
                members += mutableListOf(f)
            }
        }

        // Merge people whose averages are nearly the same (the same person seen in different light).
        // Single pass with union-find; only groups of 2+ faces start a merge, which keeps it fast.
        val parent = IntArray(sums.size) { it }
        fun root(i: Int): Int { var r = i; while (parent[r] != r) r = parent[r]; parent[i] = r; return r }
        val unit = sums.map { normalized(it) }
        for (i in sums.indices) {
            if (members[i].size < 2) continue
            for (j in sums.indices) {
                if (j == i || (members[j].size >= 2 && j < i)) continue
                if (dot(unit[i], unit[j]) >= MERGE) {
                    val ri = root(i); val rj = root(j)
                    if (ri != rj) parent[rj] = ri
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

        // One more pass: every face goes to the person it is now closest to.
        val alive = sums.indices.filter { members[it].isNotEmpty() }
        val centres = alive.map { normalized(sums[it]) }
        val result = HashMap<Int, MutableList<Long>>()
        for (f in faces) {
            var best = -1; var bestSim = -2f
            for (k in centres.indices) {
                val s = dot(f.embedding, centres[k])
                if (s > bestSim) { bestSim = s; best = k }
            }
            val needed = if (f.quality >= GOOD_FACE) join else join + 0.05f
            if (best >= 0 && bestSim >= needed) result.getOrPut(best) { mutableListOf() } += f.faceId
        }
        check(dim > 0)
        return result.values.sortedByDescending { it.size }
    }

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
        return candidates
            .filter { it.faceId !in own && candidateGroupSize(it.faceId) <= 2 }
            .filter { dot(it.embedding, centre) >= FIND_MORE }
            .map { it.faceId }
    }

    /**
     * The couple: of the most photographed people, the pair that is photographed together most.
     * [photosOf] maps person to the photos they appear in. Returns (bride, groom) or null.
     * [brideHints] are photos from bride-focused events (Mehendi, bridal getting ready); whoever
     * of the pair appears more there is taken as the bride. Amol's tagging screen can swap them.
     */
    fun detectCouple(photosOf: Map<Long, Set<Long>>, brideHints: Set<Long> = emptySet()): Pair<Long, Long>? {
        val top = photosOf.entries.sortedByDescending { it.value.size }.take(6)
        if (top.size < 2) return null
        var best: Pair<Long, Long>? = null
        var bestScore = -1.0
        for (i in top.indices) for (j in i + 1 until top.size) {
            val a = top[i]; val b = top[j]
            val together = a.value.count { it in b.value }
            if (together < 2) continue
            val score = 2.0 * together + min(a.value.size, b.value.size)
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

    fun cosine(a: FloatArray, b: FloatArray): Float {
        var d = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        if (na <= 0 || nb <= 0) return 0f
        return (d / (sqrt(na) * sqrt(nb))).toFloat()
    }

    private fun closest(e: FloatArray, sums: List<FloatArray>): Pair<Int, Float> {
        var best = -1; var bestSim = -2f
        for (k in sums.indices) {
            val s = sums[k]
            var d = 0.0; var n = 0.0
            for (i in e.indices) { d += e[i] * s[i]; n += s[i] * s[i] }
            if (n <= 0) continue
            val sim = (d / sqrt(n)).toFloat()
            if (sim > bestSim) { bestSim = sim; best = k }
        }
        return best to bestSim
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
