package com.albumdone.app.plan

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Scores the planner against a person's picks on a real wedding measured on a phone. Skipped
 * unless ALBUM_BENCHMARK names a folder holding plan-input.txt (written by the real-wedding phone
 * test) and picks.txt (one "folder/file" per line: the album a person chose from the same photos).
 *
 *   ALBUM_BENCHMARK=/path/to/wedding gradle test --tests '*AlbumBenchmarkTest*'
 */
class AlbumBenchmarkTest {

    @Test
    fun albumAgainstAPersonsPicks() {
        val dir = System.getenv("ALBUM_BENCHMARK")
        assumeTrue("set ALBUM_BENCHMARK to run", dir != null)
        val (photos, tags) = PlanDump.read(File(dir, "plan-input.txt").readText())
        val picks = File(dir, "picks.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }
        val key = photos.associateBy { if (it.folder.isEmpty()) it.name else "${it.folder}/${it.name}" }
        val human = picks.mapNotNull { key[it] }
        val a = AlbumPlanner.analyze(photos, tags)
        val list = AlbumPlanner.shortlist(a, human.size)
        val app = list.ids.map { a.byId.getValue(it) }
        fun sameMoment(x: AssessedPhoto, y: AssessedPhoto) =
            x.id == y.id || (x.repeatGroup >= 0 && x.repeatGroup == y.repeatGroup) ||
                (x.momentId == y.momentId && x.momentId >= 0 && abs(x.order - y.order) <= 3)
        val exact = human.count { h -> h.id in list.ids }
        val moment = human.count { h -> app.any { sameMoment(a.byId.getValue(h.id), it) } }
        val label = { p: AssessedPhoto -> "${p.photo.folder}/${p.photo.name}" }
        println("BENCHMARK ${File(dir).name}: ${human.size} picks; same photo $exact, same moment $moment")
        for (h in human.map { a.byId.getValue(it.id) }.filter { h -> app.none { sameMoment(h, it) } }) {
            println("  missed ${label(h)}: shot=${h.shot} quality=${"%.2f".format(h.quality)} issues=${h.issues} people=${h.people}")
        }
        for (p in app.filter { p -> human.none { sameMoment(a.byId.getValue(it.id), p) } }) {
            println("  extra  ${label(p)}: shot=${p.shot} quality=${"%.2f".format(p.quality)} ${list.reasons[p.id].orEmpty().joinToString(" | ")}")
        }
    }
}
