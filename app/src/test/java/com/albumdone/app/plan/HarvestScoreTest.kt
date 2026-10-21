package com.albumdone.app.plan

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.CRC32
import kotlin.math.abs

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
            println("SET $split $id: ${human.size} picks of ${photos.size}; same photo $exact, same moment $moment")
        }
        for ((split, t) in total.toSortedMap()) {
            println("TOTAL $split: ${t[0]} picks; same photo ${pct(t[1], t[0])}, same moment ${pct(t[2], t[0])}")
        }
    }

    private fun sameMoment(x: AssessedPhoto, y: AssessedPhoto) =
        x.id == y.id || (x.repeatGroup >= 0 && x.repeatGroup == y.repeatGroup) ||
            (x.momentId == y.momentId && x.momentId >= 0 && abs(x.order - y.order) <= 3)

    private fun pct(n: Int, of: Int) = "$n (${if (of == 0) 0 else 100 * n / of}%)"

    companion object {
        /** "test" for about a third of the sets, fixed by the set's name; "tune" for the rest. */
        fun split(id: String): String = if (CRC32().apply { update(id.toByteArray()) }.value % 3 == 0L) "test" else "tune"
    }
}
