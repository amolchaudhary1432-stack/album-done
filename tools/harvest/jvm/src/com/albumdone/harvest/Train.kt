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
            if (!header) { out.println((listOf("set", "split", "community", "name", "picked", "moment", "order", "repeat") + f.keys).joinToString(",")); header = true }
            out.println((listOf(s.id, split(s.id), s.community, label(p), if (label(p) in picked) "1" else "0", "${p.momentId}", "${p.order}", "${p.repeatGroup}") + f.values.map { "%.5f".format(it) }).joinToString(","))
        }
    }
    out.close()
}

fun harvestScore(args: List<String>) {
    val total = sortedMapOf<String, IntArray>()
    val appealDir = args.getOrNull(2)?.takeIf { it != "-" }?.let(::File)
    args.getOrNull(3)?.toDoubleOrNull()?.let { AlbumPlanner.appealMix = it }
    val worthDir = args.getOrNull(4)?.takeIf { it != "-" }?.let(::File)
    args.getOrNull(5)?.let { AlbumPlanner.anchorEveryMoment = it != "0"; AlbumPlanner.anchorOnlyIfRoom = it == "room" }
    for (s in labelled(File(args[0]), File(args[1]))) {
        // A learned appeal for this set (APPEAL_DIR/<set>.tsv, "name<TAB>0..1"), else the app's own.
        Appeal.external = appealDir?.let { File(it, "${s.id}.tsv") }?.takeIf { it.exists() }?.readLines()
            ?.map { it.split('\t') }?.filter { it.size == 2 }?.associate { it[0] to it[1].toDouble() }
        // A learned scene judge's worth per moment (WORTH_DIR/<set>.tsv), and every-moment-first on or off.
        AlbumPlanner.momentWorth = worthDir?.let { File(it, "${s.id}.tsv") }?.takeIf { it.exists() }?.readLines()
            ?.map { it.split('\t') }?.filter { it.size == 2 }?.associate { it[0].toInt() to it[1].toDouble() }
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

/** Every photo's appeal features for one plan dump (the couple found again): features PLAN OUT.csv */
fun planFeatures(args: List<String>) {
    val (photos, stored) = PlanDump.read(File(args[0]).readText())
    val main = com.albumdone.app.people.PeoplePlan.findMain(stored.occasion, photos)
    val tags = com.albumdone.app.people.PeoplePlan.tags(stored.occasion, photos, main.first, main.second)
    val a = AlbumPlanner.analyze(photos, tags)
    val medPeak = AlbumPlanner.median(a.photos.map { it.photo.peakSharpness })
    val medBright = AlbumPlanner.median(a.photos.map { it.photo.brightness })
    File(args[1]).printWriter().use { out ->
        var header = false
        for (p in a.photos) {
            val f = Appeal.features(p, a, medPeak, medBright)
            if (!header) { out.println((listOf("name", "moment", "order", "repeat", "event", "usable", "taken", "who", "family") + f.keys).joinToString(",")); header = true }
            val label = if (p.photo.folder.isEmpty()) p.photo.name else "${p.photo.folder}/${p.photo.name}"
            // Who is clearly in it, and which of them the app takes for close family (or the couple).
            val who = p.people.joinToString(";")
            val family = p.people.filter { tags.isCouple(it) || tags.tag(it).priority.name in setOf("MUST", "IMPORTANT") }.joinToString(";")
            out.println((listOf(label, "${p.momentId}", "${p.order}", "${p.repeatGroup}", p.event.replace(',', ' '), if (p.usable) "1" else "0",
                "${p.photo.takenAt}", who, family) + f.values.map { "%.5f".format(it) }).joinToString(","))
        }
    }
}

/** Moment by moment: photos, the couple's picks and the app's picks (moments PLAN COUPLE SIZE OUT.tsv). */
fun momentTable(args: List<String>) {
    val (photos, stored) = PlanDump.read(File(args[0]).readText())
    val main = com.albumdone.app.people.PeoplePlan.findMain(stored.occasion, photos)
    val tags = com.albumdone.app.people.PeoplePlan.tags(stored.occasion, photos, main.first, main.second)
    val a = AlbumPlanner.analyze(photos, tags)
    val key = photos.associateBy { if (it.folder.isEmpty()) it.name else "${it.folder}/${it.name}" }
    val couple = File(args[1]).readLines().mapNotNull { key[it.trim()]?.id }.toSet()
    val app = AlbumPlanner.shortlist(a, args[2].toInt()).ids.toSet()
    args.getOrNull(4)?.let { f -> File(f).writeText(app.mapNotNull { a.byId[it] }.joinToString("") { (if (it.photo.folder.isEmpty()) it.photo.name else "${it.photo.folder}/${it.photo.name}") + "\n" }) }
    File(args[3]).printWriter().use { out ->
        out.println("moment\tevent\tkey\tphotos\tcouple\tapp\tboth\tfirst\tlast\tminutes")
        for (m in a.moments) {
            val ids = m.photoIds
            val orders = ids.mapNotNull { a.byId[it]?.order }
            out.println("${m.id}\t${m.event}\t${m.key}\t${ids.size}\t${ids.count { it in couple }}\t${ids.count { it in app }}\t${ids.count { it in couple && it in app }}\t${orders.minOrNull()}\t${orders.maxOrNull()}\t${(m.end - m.start) / 60000}")
        }
    }
}
