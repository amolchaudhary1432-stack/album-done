package com.albumdone.harvest

import com.albumdone.app.plan.AlbumPlanner
import com.albumdone.app.plan.AssessedPhoto
import com.albumdone.app.plan.PlanDump
import java.io.File
import kotlin.math.abs

/**
 * Scores albums chosen from the same raw folder against the couple's real album: the app's own
 * album (the planner, at the couple's album size) and any other picker's (Claude's blind picks).
 *
 *     gradle run --args="score PLAN.txt COUPLE.txt REPORT.txt [NAME=PICKS.txt ...]"
 *
 * Picks files hold one "folder/file" per line. Measures, each out of the couple's album:
 *  - same photo: the very photo the couple chose;
 *  - same moment: that photo, a near-identical one (same burst or stage group), or one taken within
 *    3 photos of it in the same moment;
 *  - wrong moments: picks with no couple's photo in their moment (the share of the album spent on
 *    moments the couple left out);
 *  - events: how far each event's share of the album is from the couple's (0 = the same balance);
 *  - people: of the people the couple's album shows, how many this album shows too.
 * The report names photos (locked before it is kept); the console prints numbers only.
 */
fun score(args: List<String>) {
    val (photos, tags) = PlanDump.read(File(args[0]).readText())
    val key = photos.associateBy { if (it.folder.isEmpty()) it.name else "${it.folder}/${it.name}" }
    fun read(f: String) = File(f).readLines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { key[it]?.id }.distinct()
    val couple = read(args[1])
    val a = AlbumPlanner.analyze(photos, tags)
    val n = couple.size
    val pickers = linkedMapOf("app" to AlbumPlanner.shortlist(a, n).ids)
    for (spec in args.drop(3)) {
        val (name, file) = spec.split('=', limit = 2)
        pickers[name] = read(file)
    }
    val by = a.byId
    fun sameMoment(x: AssessedPhoto, y: AssessedPhoto) =
        x.id == y.id || (x.repeatGroup >= 0 && x.repeatGroup == y.repeatGroup) ||
            (x.momentId >= 0 && x.momentId == y.momentId && abs(x.order - y.order) <= 3)
    val human = couple.map { by.getValue(it) }
    fun label(p: AssessedPhoto) = if (p.photo.folder.isEmpty()) p.photo.name else "${p.photo.folder}/${p.photo.name}"
    val humanEvents = human.groupingBy { it.event }.eachCount()
    val humanPeople = human.flatMap { it.people }.toSet()
    val report = StringBuilder()
    report.appendLine("Couple's album: $n photos from ${photos.size} raw (${"%.1f".format(100.0 * n / photos.size)}%), " +
        "${humanEvents.size} events, ${humanPeople.size} people shown")
    report.appendLine("Couple's album by event: " + humanEvents.entries.joinToString { "${it.key} ${it.value}" })
    for ((name, ids) in pickers) {
        val mine = ids.mapNotNull { by[it] }
        val exact = couple.count { it in ids }
        val moment = human.count { h -> mine.any { sameMoment(h, it) } }
        val wrong = mine.count { p -> human.none { sameMoment(it, p) } }
        val events = mine.groupingBy { it.event }.eachCount()
        val eventGap = (humanEvents.keys + events.keys).sumOf {
            abs((humanEvents[it] ?: 0).toDouble() / n - (events[it] ?: 0).toDouble() / mine.size.coerceAtLeast(1))
        } / 2
        val people = mine.flatMap { it.people }.toSet()
        val peopleShare = if (humanPeople.isEmpty()) 1.0 else humanPeople.count { it in people }.toDouble() / humanPeople.size
        val line = "%-8s %4d photos | same photo %5.1f%% | same moment %5.1f%% | wrong moments %5.1f%% | events off by %4.1f%% | people %5.1f%%"
            .format(name, mine.size, 100.0 * exact / n, 100.0 * moment / n, 100.0 * wrong / mine.size.coerceAtLeast(1),
                100.0 * eventGap, 100.0 * peopleShare)
        println(line)
        report.appendLine(line)
        report.appendLine("  by event: " + events.entries.joinToString { "${it.key} ${it.value}" })
        for (h in human.filter { h -> mine.none { sameMoment(h, it) } }) {
            report.appendLine("  missed ${label(h)}: shot=${h.shot} quality=${"%.2f".format(h.quality)} issues=${h.issues} people=${h.people.size}")
        }
        for (p in mine.filter { p -> human.none { sameMoment(it, p) } }) {
            report.appendLine("  extra  ${label(p)}: shot=${p.shot} quality=${"%.2f".format(p.quality)} issues=${p.issues}")
        }
    }
    File(args[2]).writeText(report.toString())
}
