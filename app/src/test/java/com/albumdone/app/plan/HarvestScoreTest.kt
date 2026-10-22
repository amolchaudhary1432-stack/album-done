package com.albumdone.app.plan

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scores the planner on every harvested photo set that has a person's picks, the way
 * [AlbumBenchmarkTest] does for one wedding. Skipped unless HARVEST_SCORE names a folder with
 * <id>.plan.txt files (the harvest-data branch's sets/) and HARVEST_LABELS a folder with <id>.txt
 * picks (one file name per line, tools/harvest/labels).
 *
 * About a third of the sets are held back (by a fixed hash of the set's name) and must never be
 * looked at while changing the planner: their score is the honest one.
 *
 *   HARVEST_SCORE=.../sets HARVEST_LABELS=tools/harvest/labels gradle test --tests '*HarvestScoreTest*'
 *
 * [rawFolders] is the tougher test on the same sets with bad photos planted (HARVEST_RAW).
 */
class HarvestScoreTest {

    @Test
    fun everyLabelledSet() {
        val plans = System.getenv("HARVEST_SCORE")?.let(::File)
        val labels = System.getenv("HARVEST_LABELS")?.let(::File)
        assumeTrue("set HARVEST_SCORE and HARVEST_LABELS to run", plans != null && labels != null)
        val total = HashMap<String, IntArray>()
        for (pickFile in labels!!.listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }) {
            val id = pickFile.name.removeSuffix(".txt")
            val plan = File(plans, "$id.plan.txt")
            if (!plan.exists()) continue
            val (photos, tags) = PlanDump.read(plan.readText())
            val key = photos.associateBy { if (it.folder.isEmpty()) it.name else "${it.folder}/${it.name}" }
            val human = pickFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { key[it] }
            if (human.isEmpty()) continue
            val a = AlbumPlanner.analyze(photos, tags)
            val list = AlbumPlanner.shortlist(a, human.size)
            val app = list.ids.map { a.byId.getValue(it) }
            val exact = human.count { it.id in list.ids }
            val moment = human.count { h -> app.any { sameMoment(a.byId.getValue(h.id), it) } }
            val split = split(id)
            total.getOrPut(split) { IntArray(3) }.let { it[0] += human.size; it[1] += exact; it[2] += moment }
            // Held-back sets only count in the total: their per-set results stay unseen.
            if (split == "tune") println("SET $split $id: ${human.size} picks of ${photos.size}; same photo $exact, same moment $moment")
        }
        for ((split, t) in total.toSortedMap()) {
            println("TOTAL $split: ${t[0]} picks; same photo ${pct(t[1], t[0])}, same moment ${pct(t[2], t[0])}")
        }
    }

    /**
     * The tougher test: the same sets measured again with bad twins planted next to their photos
     * (harvest option rawify, then tools/harvest/raw_labels.py into HARVEST_RAW). A mistake is a
     * planted photo in the album that is flawed (shaky, out of focus, dark, blown out, crooked, face
     * cut by the edge, accidental), or a planted copy (burst frame, black-and-white copy) next to the
     * photo it copies. "Clean" is the share of album places without a mistake. Agreement with the
     * person counts a burst frame of their pick as their pick. Shown next to two dumb pickers.
     */
    @Test
    fun rawFolders() {
        val plans = System.getenv("HARVEST_SCORE")?.let(::File)
        val raw = System.getenv("HARVEST_RAW")?.let(::File)
        assumeTrue("set HARVEST_SCORE and HARVEST_RAW to run", plans != null && raw != null)
        val total = HashMap<String, Array<IntArray>>()
        val kinds = HashMap<String, IntArray>()
        for (truthFile in raw!!.listFiles { f -> f.name.endsWith(".truth.txt") }.orEmpty().sortedBy { it.name }) {
            val id = truthFile.name.removeSuffix(".truth.txt")
            val plan = File(plans, "$id.plan.txt")
            val pickFile = File(raw, "$id.txt")
            if (!plan.exists() || !pickFile.exists()) continue
            val (photos, tags) = PlanDump.read(plan.readText())
            val byName = photos.associateBy { it.name }
            // planted photo id -> (id of the photo it was made from, what is wrong with it)
            val planted = truthFile.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
                val (bad, source, defect) = line.split("\t")
                val b = byName[bad] ?: return@mapNotNull null
                b.id to ((byName[source]?.id ?: -1L) to defect)
            }.toMap()
            val human = pickFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { byName[it]?.id }
            if (human.isEmpty()) continue
            val a = AlbumPlanner.analyze(photos, tags)
            val size = human.size
            val app = AlbumPlanner.shortlist(a, size).ids.toList()
            val step = a.photos.size.toDouble() / size
            val everyNth = List(size) { a.photos[((it + 0.5) * step).toInt().coerceAtMost(a.photos.size - 1)].id }
            val randoms = List(RANDOM_RUNS) { seed -> a.photos.map { it.id }.shuffled(java.util.Random(seed.toLong())).take(size) }
            val split = split(id.removeSuffix("_raw"))
            val rows = total.getOrPut(split) { Array(3) { IntArray(4) } }
            fun score(chosen: List<Long>): IntArray {
                val inAlbum = chosen.toSet()
                val mistakes = chosen.count { c ->
                    val (source, defect) = planted[c] ?: return@count false
                    defect !in COPIES || source in inAlbum
                }
                val burstOf = planted.filterValues { it.second == "burst" }.entries.associate { it.value.first to it.key }
                val exact = human.count { it in inAlbum || burstOf[it] in inAlbum }
                val moment = human.count { h -> chosen.any { sameMoment(a.byId.getValue(h), a.byId.getValue(it)) } }
                return intArrayOf(size, mistakes, exact, moment)
            }
            val app4 = score(app)
            for ((k, v) in listOf(app4, score(everyNth)).withIndex()) for (j in 0..3) rows[k][j] += v[j]
            val r = randoms.map(::score)
            for (j in 0..3) rows[2][j] += (r.sumOf { it[j] }.toDouble() / RANDOM_RUNS).roundToInt()
            for (c in app) planted[c]?.let { (source, defect) ->
                if (defect !in COPIES || source in app) kinds.getOrPut("$split $defect") { IntArray(1) }[0]++
            }
            if (split == "tune") println("RAW $id: ${a.photos.size} photos (${planted.size} planted), album $size: " +
                "mistakes ${app4[1]}, same photo ${app4[2]}, same moment ${app4[3]}")
        }
        for ((split, rows) in total.toSortedMap()) {
            for ((name, t) in listOf("app", "every n-th", "random").zip(rows)) {
                println("RAWTOTAL $split $name: ${t[0]} places; clean ${pct(t[0] - t[1], t[0])}, " +
                    "same photo ${pct(t[2], t[0])}, same moment ${pct(t[3], t[0])}")
            }
        }
        for ((k, v) in kinds.toSortedMap()) println("RAWMISTAKE $k: ${v[0]}")
    }

    private fun sameMoment(x: AssessedPhoto, y: AssessedPhoto) =
        x.id == y.id || (x.repeatGroup >= 0 && x.repeatGroup == y.repeatGroup) ||
            (x.momentId == y.momentId && x.momentId >= 0 && abs(x.order - y.order) <= 3)

    private fun pct(n: Int, of: Int) = "$n (${if (of == 0) 0 else 100 * n / of}%)"

    companion object {
        /** "test" for about a third of the sets, fixed by the set's name; "tune" for the rest. */
        /** Planted copies of a good photo: a mistake only next to the photo they copy. */
        val COPIES = setOf("burst", "black-and-white copy")
        const val RANDOM_RUNS = 20

        fun split(id: String): String = if (CRC32().apply { update(id.toByteArray()) }.value % 3 == 0L) "test" else "tune"
    }
}
