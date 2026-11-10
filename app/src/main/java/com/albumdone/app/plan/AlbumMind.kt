package com.albumdone.app.plan

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Album Mind: the learned brain that chooses the album like a person, from what it sees (SigLIP 2
 * image vectors) and what the checks measured. Pure Kotlin, the same steps as
 * tools/reasoner/mind_v2.py (BEATS=1), with numbers learned there (assets/models/album_mind.bin):
 *
 * - Scene judge: what each moment is worth to the family (learned on a real raw folder judged moment
 *   by moment, and on web sets of many communities): what it shows, who is in it and how rarely they
 *   appear, where it sits in the day.
 * - Frame judge: which frame of a moment is the one to keep (learned on people's rankings of
 *   near-identical frames, and on what bigger image models think of 3,000 web photos).
 * - Reasoning: places go one at a time to the moment that gains most. A moment's next photo is worth
 *   less each time, more slowly when the moment holds many different beats (poses, happenings); it
 *   comes from a beat the moment has not used, and is never a near-copy. Then everyone close is
 *   seen with the couple at least once.
 */
object AlbumMind {

    /** The learned numbers, set once by the app (assets/models/album_mind.bin); null: the rules plan alone. */
    @Volatile var weights: Weights? = null

    /** Share of a folder's photos that must have been seen for the brain to plan it. */
    const val SEEN = 0.95

    fun canSee(a: AlbumAnalysis): Boolean =
        a.photos.isNotEmpty() && a.photos.count { it.photo.scene != null } >= SEEN * a.photos.size

    /** Each photo's scene numbers (zeros for the few the phone could not read). */
    fun vectors(a: AlbumAnalysis): Map<Long, FloatArray> {
        val dim = a.photos.firstNotNullOfOrNull { it.photo.scene }?.size ?: 768
        return a.photos.associate { it.id to (it.photo.scene ?: FloatArray(dim)) }
    }

    /**
     * The album as the brain chooses it, with the cover and the reasons from the rules: the cover is the
     * rules' (the couple's best portrait), the brain fills every other place, then each photo is described.
     */
    fun shortlist(a: AlbumAnalysis, w: Weights, size: Int, removed: Set<Long> = emptySet(), keep: Set<Long> = emptySet(), liked: Set<Long> = emptySet()): Shortlist {
        if (size <= 0) return Shortlist(emptyList(), emptyMap(), null, emptyList())
        val coverList = AlbumPlanner.rules(a, 1, removed, emptySet())
        val cover = coverList.coverId
        val vectors = vectors(a)
        val why = HashMap<Long, String>()
        val picks = choose(a, vectors, w, size, removed, keep + listOfNotNull(cover), why = why, liked = liked)
        val described = AlbumPlanner.rules(a, picks.size, removed, picks.toSet(), why)
        val reasons = HashMap<Long, List<String>>(described.reasons)
        if (cover != null && cover in picks) reasons[cover] = coverList.reasons[cover].orEmpty() + described.reasons[cover].orEmpty()
        return Shortlist(described.ids, reasons, cover?.takeIf { it in picks }, described.coverage)
    }

    class Weights(val feat: List<String>, private val arrays: Map<String, Array<FloatArray>>) {
        fun mat(name: String): Array<FloatArray> = arrays.getValue(name)
        fun vec(name: String): DoubleArray = arrays.getValue(name).let { m -> DoubleArray(m.size) { m[it][0].toDouble() } }
        private val consts = vec("consts")
        val decay = consts[0]
        val beatLevel = consts[1]
        val nearCopy = consts[2]
        val frameMix = consts[3]
        val distilled = consts[4] > 0.5
    }

    /** Reads the numbers written by mind_v2.py (EXPORT=...). */
    fun read(bytes: ByteArray): Weights {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "AMND") { "not an Album Mind file" }
        b.position(4)
        require(b.int == 1) { "unknown Album Mind version" }
        fun text(): String { val n = b.int; val s = String(bytes, b.position(), n, Charsets.UTF_8); b.position(b.position() + n); return s }
        val feat = text().split('\n')
        val arrays = HashMap<String, Array<FloatArray>>()
        repeat(b.int) {
            val key = text(); val rows = b.int; val cols = b.int
            arrays[key] = Array(rows) { FloatArray(cols) { b.float } }
        }
        return Weights(feat, arrays)
    }

    /** What the brain worked out about one folder: each moment's worth, each photo's frame score, beats. */
    class Reading(
        val worth: Map<Int, Double>,
        val frame: Map<Long, Double>,
        val beat: Map<Long, Int>,
        val room: Map<Int, Double>,
    )

    private fun sigmoid(z: Double) = 1.0 / (1.0 + exp(-z))

    private fun normalised(v: FloatArray): DoubleArray {
        var n = 0.0
        for (x in v) n += x.toDouble() * x
        n = sqrt(n).takeIf { it > 0 } ?: 1.0
        return DoubleArray(v.size) { v[it] / n }
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double { var s = 0.0; for (i in a.indices) s += a[i] * b[i]; return s }

    /** v (768) times M (768 x k). */
    private fun project(v: DoubleArray, m: Array<FloatArray>, k: Int = m[0].size): DoubleArray {
        val out = DoubleArray(k)
        for (i in v.indices) { val x = v[i]; if (x == 0.0) continue; val row = m[i]; for (j in 0 until k) out[j] += x * row[j] }
        return out
    }

    private fun zScores(x: DoubleArray): DoubleArray {
        val mean = x.average()
        val sd = sqrt(x.sumOf { (it - mean) * (it - mean) } / x.size) + 1e-9
        return DoubleArray(x.size) { (x[it] - mean) / sd }
    }

    /** Looks at the whole folder once. Every photo of [a] needs a vector in [vectors]. */
    fun read(a: AlbumAnalysis, vectors: Map<Long, FloatArray>, w: Weights): Reading {
        val photos = a.photos
        val n = photos.size
        val e = Array(n) { normalised(vectors.getValue(photos[it].id)) }
        val medPeak = AlbumPlanner.median(photos.map { it.photo.peakSharpness })
        val medBright = AlbumPlanner.median(photos.map { it.photo.brightness })
        val nf = w.feat.size
        val f = Array(n) { i -> Appeal.features(photos[i], a, medPeak, medBright).let { m -> DoubleArray(nf) { m[w.feat[it]] ?: 0.0 } } }
        val couple = setOfNotNull(a.tags.brideId, a.tags.groomId)
        val who = Array(n) { photos[it].people }
        val fam = Array(n) { i -> photos[i].people.filter { p -> a.tags.tag(p).priority == Priority.MUST || a.tags.tag(p).priority == Priority.IMPORTANT }.toSet() - couple }

        // ---- Scene judge: one row of facts per moment ----
        val fmu = DoubleArray(nf) { j -> (0 until n).sumOf { f[it][j] } / n }
        val fsd = DoubleArray(nf) { j -> sqrt((0 until n).sumOf { (f[it][j] - fmu[j]).pow(2) } / n) + 1e-6 }
        val byEvent = (0 until n).groupBy { photos[it].event }
        val byMoment = LinkedHashMap<Pair<String, Int>, MutableList<Int>>()
        for (i in 0 until n) byMoment.getOrPut(photos[i].event to photos[i].momentId) { mutableListOf() } += i
        val seenIn = HashMap<Long, Int>()
        for (ids in byMoment.values) for (p in ids.flatMap { who[it] }.toSet()) seenIn[p] = (seenIn[p] ?: 0) + 1
        val pMat = w.mat("P"); val p2 = w.mat("P2"); val xm = w.vec("xm"); val xs = w.vec("xs")
        val w1 = w.vec("w1"); val b1 = w.vec("b1")[0]; val w2 = w.vec("w2"); val b2 = w.vec("b2")[0]
        val worth = HashMap<Int, Double>()
        for ((key, ids) in byMoment) {
            val evIds = byEvent.getValue(key.first)
            val cen = DoubleArray(e[0].size).also { c -> for (i in evIds) for (j in c.indices) c[j] += e[i][j] }.map { it / evIds.size }.toDoubleArray()
            val mean = DoubleArray(e[0].size).also { c -> for (i in ids) for (j in c.indices) c[j] += e[i][j] }.map { it / ids.size }.toDoubleArray()
            val fz = ids.map { i -> DoubleArray(nf) { (f[i][it] - fmu[it]) / fsd[it] } }
            val fMean = DoubleArray(nf) { j -> fz.sumOf { it[j] } / fz.size }
            val fMax = DoubleArray(nf) { j -> fz.maxOf { it[j] } }
            val ts = evIds.map { photos[it].photo.takenAt }.filter { it > 0 }.sorted().ifEmpty { listOf(0L) }
            val t0 = ids.map { photos[it].photo.takenAt }.filter { it > 0 }.minOrNull() ?: ts[0]
            val pos = (t0 - ts[0]).toDouble() / maxOf(1L, ts.last() - ts[0])
            val whoAll = ids.flatMap { who[it] }.toSet(); val famAll = ids.flatMap { fam[it] }.toSet()
            val withCouple = ids.count { (who[it] intersect couple).isNotEmpty() }.toDouble() / ids.size
            val both = ids.count { who[it].containsAll(couple) }.toDouble() / ids.size
            val rare = famAll.minOfOrNull { seenIn[it] ?: 0 } ?: 99
            val strangers = (whoAll - famAll - couple).size.toDouble() / ids.size
            val x = fMean + fMax + project(DoubleArray(mean.size) { mean[it] - cen[it] }, pMat) + project(mean, p2) +
                doubleArrayOf(ln(ids.size.toDouble()), ids.size.toDouble() / evIds.size, pos, withCouple, both, famAll.size.toDouble(),
                    ln(1.0 + rare), strangers, ln(1.0 + whoAll.size))
            val xn = DoubleArray(x.size) { (x[it] - xm[it]) / xs[it] }
            worth[key.second] = (sigmoid(dot(xn, w1) + b1) + sigmoid(dot(xn, w2) + b2)) / 2
        }

        // ---- Frame judge ----
        val mu = w.vec("mu"); val sd = w.vec("sd"); val fw = w.vec("fw"); val v64 = w.mat("V64")
        val old = DoubleArray(n) { i -> dot(DoubleArray(nf) { (f[i][it] - mu[it]) / sd[it] } + project(e[i], v64), fw) }
        val frameScore = if (w.distilled) {
            val wd = w.vec("wd")
            val dist = DoubleArray(n) { i -> var s = wd[wd.size - 1]; for (j in e[i].indices) s += e[i][j] * wd[j]; s }
            val zo = zScores(old); val zd = zScores(dist)
            DoubleArray(n) { zo[it] + w.frameMix * zd[it] }
        } else old

        // ---- Beats: photos stay one beat while they look alike ----
        val beat = HashMap<Long, Int>(); val room = HashMap<Int, Double>()
        for ((key, ids) in byMoment) {
            var anchor: DoubleArray? = null; var b = -1
            for (i in ids.sortedBy { photos[it].order }) {
                if (anchor == null || dot(e[i], anchor) < w.beatLevel) { anchor = e[i]; b++ }
                beat[photos[i].id] = b
            }
            room[key.second] = sqrt((b + 1).toDouble())
        }
        return Reading(worth, (0 until n).associate { photos[it].id to frameScore[it] }, beat, room)
    }

    /**
     * Chooses [size] photos. [keep] are already in the album and stay (the couple's own choices, the
     * cover); [removed] never come back. Returns the photos in the order they were chosen.
     */
    fun choose(
        a: AlbumAnalysis, vectors: Map<Long, FloatArray>, w: Weights, size: Int,
        removed: Set<Long> = emptySet(), keep: Set<Long> = emptySet(), reading: Reading = read(a, vectors, w),
        why: MutableMap<Long, String>? = null,
        liked: Set<Long> = emptySet(),
    ): List<Long> {
        val e = a.photos.associate { it.id to normalised(vectors.getValue(it.id)) }
        val moments = LinkedHashMap<Int, MutableList<AssessedPhoto>>()
        for (p in a.photos) moments.getOrPut(p.momentId) { mutableListOf() } += p
        fun ok(p: AssessedPhoto) = p.usable && p.id !in removed
        val chosen = ArrayList<AssessedPhoto>(); val taken = HashSet<Long>(); val count = HashMap<Int, Int>()
        fun take(p: AssessedPhoto) { chosen += p; taken += p.id; count[p.momentId] = (count[p.momentId] ?: 0) + 1 }
        keep.filter { it !in removed }.mapNotNull { a.byId[it] }.forEach { take(it) }
        val taste = taste(a, e, removed, liked, w)
        val frame = if (taste == null) reading.frame else reading.frame.mapValues { (id, f) -> f + (taste.photo[id] ?: 0.0) }
        val worth = if (taste == null) reading.worth else reading.worth.mapValues { (m, v) -> v * (taste.moment[m] ?: 1.0) }
        fun room(m: Int) = reading.room[m] ?: 1.0
        fun value(m: Int, already: Int) = (worth[m] ?: 0.0) * w.decay.pow(already / room(m))
        fun bestFrame(m: Int): AssessedPhoto? {
            var cands = moments.getValue(m).filter { ok(it) && it.id !in taken }
            val inMoment = chosen.filter { it.momentId == m }
            if (inMoment.isNotEmpty()) {
                cands = cands.filter { r -> inMoment.maxOf { c -> dot(e.getValue(c.id), e.getValue(r.id)) } < w.nearCopy }
                val used = inMoment.map { reading.beat[it.id] }.toSet()
                val fresh = cands.filter { reading.beat[it.id] !in used }
                if (fresh.isNotEmpty()) cands = fresh
            }
            return cands.maxByOrNull { frame.getValue(it.id) }
        }
        while (chosen.size < size) {
            var best: AssessedPhoto? = null; var bestValue = -1.0
            for (m in moments.keys) {
                val v = value(m, count[m] ?: 0)
                if (v > bestValue) { bestFrame(m)?.let { best = it; bestValue = v } }
            }
            val p = best ?: break
            why?.put(p.id, momentReason(a, p, (count[p.momentId] ?: 0) > 0, worth[p.momentId] ?: 0.0))
            take(p)
        }
        // Everyone close seen with the couple at least once.
        val mains = setOfNotNull(a.tags.brideId, a.tags.groomId)
        val family = a.photos.flatMap { p -> p.people.filter { a.tags.tag(it).priority == Priority.MUST || a.tags.tag(it).priority == Priority.IMPORTANT } }
            .toSet() - mains
        for (person in family.sortedBy { it.toString() }) {
            fun withThem(p: AssessedPhoto) = person in p.people && p.people.any { it in mains }
            if (chosen.any { withThem(it) }) continue
            val cands = a.photos.filter { ok(it) && withThem(it) && it.id !in taken }
            if (cands.isEmpty()) continue
            val drop = chosen.filter { it.id !in keep }.minByOrNull { value(it.momentId, (count[it.momentId] ?: 1) - 1) } ?: break
            chosen.remove(drop); taken.remove(drop.id); count[drop.momentId] = (count[drop.momentId] ?: 1) - 1
            why?.remove(drop.id)
            val add = cands.maxBy { frame.getValue(it.id) }
            why?.put(add.id, "${a.tags.label(person)} with ${if (a.tags.groomId != null) a.tags.pair() else a.tags.who(a.tags.brideId ?: 0)}")
            take(add)
        }
        return chosen.map { it.id }
    }

    /** What the couple's own changes to this album say about their taste: a push per photo and per moment. */
    class Taste(val photo: Map<Long, Double>, val moment: Map<Int, Double>)

    /** Changes needed before the brain reads a taste into them (one removal can be about one photo only). */
    const val TASTE_FROM = 3
    /** Changes after which the taste counts in full. */
    const val TASTE_FULL = 10

    /**
     * Learning inside one album, on the phone: photos the couple took out ([removed]) and chose themselves
     * ([liked]) show what they like. The direction from what they removed towards what they chose, in what
     * the photos show (SigLIP), pushes every other photo and moment up or down, more as they make more
     * changes; a photo that looks just like one they removed is pushed down hard. People they keep removing
     * are shown less (never the couple). Nothing is stored beyond the album and nothing leaves the phone.
     */
    fun taste(a: AlbumAnalysis, e: Map<Long, DoubleArray>, removed: Set<Long>, liked: Set<Long>, w: Weights): Taste? {
        val rem = removed.mapNotNull { e[it] }; val lik = liked.filter { it !in removed }.mapNotNull { e[it] }
        val n = rem.size + lik.size
        if (n < TASTE_FROM) return null
        val dim = e.values.first().size
        fun mean(vs: Collection<DoubleArray>) = DoubleArray(dim).also { m -> for (v in vs) for (j in 0 until dim) m[j] += v[j] / vs.size }
        val all = mean(e.values)
        val d = DoubleArray(dim)
        if (lik.isNotEmpty()) mean(lik).let { m -> for (j in 0 until dim) d[j] += m[j] - all[j] }
        if (rem.isNotEmpty()) mean(rem).let { m -> for (j in 0 until dim) d[j] -= m[j] - all[j] }
        val norm = sqrt(d.sumOf { it * it }).takeIf { it > 1e-9 } ?: return null
        val strength = minOf(1.0, n.toDouble() / TASTE_FULL)
        val ids = a.photos.map { it.id }
        val z = zScores(DoubleArray(ids.size) { i -> dot(e.getValue(ids[i]), d) / norm })
        // People in several removed photos and in none they chose: shown less.
        val couple = setOfNotNull(a.tags.brideId, a.tags.groomId)
        val out = HashMap<Long, Int>()
        for (id in removed) for (p in a.byId[id]?.people.orEmpty()) if (p !in couple) out[p] = (out[p] ?: 0) + 1
        for (id in liked) for (p in a.byId[id]?.people.orEmpty()) out.remove(p)
        val less = out.filterValues { it >= TASTE_FROM }.keys
        val photo = HashMap<Long, Double>()
        for ((i, p) in a.photos.withIndex()) {
            var t = 0.75 * strength * z[i]
            if (rem.any { r -> dot(r, e.getValue(p.id)) >= w.nearCopy }) t -= 2.0
            if (p.people.any { it in less }) t -= 1.0
            photo[p.id] = t
        }
        val moment = a.photos.withIndex().groupBy({ it.value.momentId }, { z[it.index] })
            .mapValues { (_, zs) -> exp((0.3 * strength * zs.average()).coerceIn(-0.6, 0.6)) }
        return Taste(photo, moment)
    }

    /** Why the brain gave a place to this photo, in plain words. */
    private fun momentReason(a: AlbumAnalysis, p: AssessedPhoto, more: Boolean, worth: Double): String {
        val event = if (a.events.size > 1) Display.eventName(p.event) else null
        return when {
            more -> "Another side of the same moment" + (event?.let { " ($it)" } ?: "")
            worth >= 0.75 -> "A moment that matters" + (event?.let { " in the $it" } ?: "")
            else -> if (event != null) "Part of the $event story" else "Part of the story"
        }
    }
}
