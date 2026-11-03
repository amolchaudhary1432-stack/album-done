package com.albumdone.harvest

import com.albumdone.app.plan.AlbumPlanner
import com.albumdone.app.plan.Appeal
import com.albumdone.app.plan.AssessedPhoto
import com.albumdone.app.plan.PlanDump
import java.io.File
import java.util.zip.CRC32
import kotlin.math.abs

/**
 * Teaching the brain from albums picked by eye (tools/harvest/labels), on this machine, with no
 * Android build:
 *
 *     gradle run --args="learn-data PLANS LABELS OUT.csv"   one row per photo: set, split, name, picked, features
 *     gradle run --args="harvest-score PLANS LABELS"        how close the app comes, practice and held back
 *
 * PLANS holds <set>.plan.txt (harvest-data branch). The split is HarvestScoreTest's: about a third
 * of the sets ("test") are never learned from, so their score is the honest one.
 */
fun split(id: String): String = if (CRC32().apply { update(id.toByteArray()) }.value % 3 == 0L) "test" else "tune"

private class Labelled(val id: String, val community: String, val plan: File, val picks: List<String>)

private fun labelled(plans: File, labels: File): List<Labelled> =
    labels.listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }.mapNotNull { f ->
        val id = f.name.removeSuffix(".txt")
        val plan = File(plans, "$id.plan.txt").takeIf { it.exists() } ?: return@mapNotNull null
        val lines = f.readLines().map { it.trim() }
        val community = lines.firstOrNull { it.startsWith("# community:") }?.substringAfter(':')?.trim() ?: "older"
        Labelled(id, community, plan, lines.filter { it.isNotEmpty() && !it.startsWith("#") })
    }

fun learnData(args: List<String>) {
    val out = File(args[2]).printWriter()
    var header = false
    for (s in labelled(File(args[0]), File(args[1]))) {
        val (photos, tags) = PlanDump.read(s.plan.readText())
        val a = AlbumPlanner.analyze(photos, tags)
        fun label(p: AssessedPhoto) = if (p.photo.folder.isEmpty()) p.photo.name else "${p.photo.folder}/${p.photo.name}"
        val picked = s.picks.toSet()
        if (a.photos.none { label(it) in picked }) continue
        val medPeak = AlbumPlanner.median(a.photos.map { it.photo.peakSharpness })
        val medBright = AlbumPlanner.median(a.photos.map { it.photo.brightness })
        for (p in a.photos) {
            // Planted bad copies ("IMG_0007b.jpg") were never on the sheets: not a choice anyone made.
            if (p.photo.name.matches(Regex(".*\\db\\.\\w+$"))) continue
            val f = Appeal.features(p, a, medPeak, medBright)
            if (!header) { out.println((listOf("set", "split", "community", "name", "picked") + f.keys).joinToString(",")); header = true }
            out.println((listOf(s.id, split(s.id), s.community, label(p), if (label(p) in picked) "1" else "0") + f.values.map { "%.5f".format(it) }).joinToString(","))
        }
    }
    out.close()
}

fun harvestScore(args: List<String>) {
    val total = sortedMapOf<String, IntArray>()
    for (s in labelled(File(args[0]), File(args[1]))) {
        val (photos, tags) = PlanDump.read(s.plan.readText())
        val key = photos.associateBy { if (it.folder.isEmpty()) it.name else "${it.folder}/${it.name}" }
        val human = s.picks.mapNotNull { key[it] }
        if (human.isEmpty()) continue
        val a = AlbumPlanner.analyze(photos, tags)
        val list = AlbumPlanner.shortlist(a, human.size)
        val app = list.ids.map { a.byId.getValue(it) }
        fun same(x: AssessedPhoto, y: AssessedPhoto) = x.id == y.id || (x.repeatGroup >= 0 && x.repeatGroup == y.repeatGroup) ||
            (x.momentId == y.momentId && x.momentId >= 0 && abs(x.order - y.order) <= 3)
        val exact = human.count { it.id in list.ids }
        val moment = human.count { h -> app.any { same(a.byId.getValue(h.id), it) } }
        for (k in listOf(split(s.id), "${split(s.id)} ${s.community}")) {
            total.getOrPut(k) { IntArray(4) }.let { it[0] += human.size; it[1] += exact; it[2] += moment; it[3]++ }
        }
    }
    for ((k, t) in total) println("%-24s %3d sets %4d picks | same photo %3d%% | same moment %3d%%".format(k, t[3], t[0], 100 * t[1] / t[0], 100 * t[2] / t[0]))
}
