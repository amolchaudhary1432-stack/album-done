package com.albumdone.app.plan

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** What the planner needs to know about one photo. Plain Kotlin so it can be unit-tested. */
data class PlanPhoto(
    val id: Long,
    val name: String,
    /** Sub-folder path inside the chosen folder, "" for the top level. */
    val folder: String,
    /** Capture time in millis, 0 when unknown. */
    val takenAt: Long,
    val sharpness: Double,
    val brightness: Double,
    val darkFraction: Double,
    val brightFraction: Double,
    val hash: Long,
    val faceCount: Int,
    /** Best smile 0..1, or -1 when there is no clear face. */
    val smile: Float,
    val eyesClosed: Boolean,
    /** The photo could not be read. */
    val failed: Boolean = false,
)

enum class Issue { UNREADABLE, BLURRY, TOO_DARK, TOO_BRIGHT, EYES_CLOSED, DUPLICATE }

data class AssessedPhoto(
    val photo: PlanPhoto,
    val event: String,
    val issues: Set<Issue>,
    val score: Double,
    /** Position in capture order across the whole wedding (events in time order). */
    val order: Int,
) {
    val id: Long get() = photo.id
    val usable: Boolean get() = issues.isEmpty()
}

data class EventStats(val name: String, val total: Int, val usable: Int)

/** Result of checking every photo of one wedding. Photos are in capture order. */
class AlbumAnalysis(val photos: List<AssessedPhoto>, val events: List<String>) {
    val byId: Map<Long, AssessedPhoto> = photos.associateBy { it.id }

    val usableCount: Int get() = photos.count { it.usable }
    fun count(issue: Issue): Int = photos.count { issue in it.issues }
    val darkOrBrightCount: Int
        get() = photos.count { Issue.TOO_DARK in it.issues || Issue.TOO_BRIGHT in it.issues }

    val eventStats: List<EventStats>
        get() = events.map { e ->
            val inEvent = photos.filter { it.event == e }
            EventStats(e, inEvent.size, inEvent.count { it.usable })
        }
}

/**
 * Turns the measurements of every photo into an album shortlist.
 * Pure Kotlin, no Android classes, so every rule here is covered by unit tests.
 */
object AlbumPlanner {

    const val BLUR_FACTOR = 0.3
    const val DARK_BRIGHTNESS = 40.0
    const val DARK_SHARE = 0.65
    const val BRIGHT_BRIGHTNESS = 220.0
    const val BLOWN_SHARE = 0.5
    const val BURST_GAP_MILLIS = 3_000L
    const val BURST_HASH_DISTANCE = 12
    const val EVENT_GAP_MILLIS = 90L * 60 * 1000
    const val SLOT_MILLIS = 5L * 60 * 1000
    /** Photos without a capture time are spread in groups of this many, by file name. */
    const val UNTIMED_SLOT_SIZE = 10
    const val OTHER_PHOTOS = "Other photos"
    const val ALL_PHOTOS = "All photos"

    fun analyze(photos: List<PlanPhoto>): AlbumAnalysis {
        val readable = photos.filter { !it.failed }
        val median = median(readable.map { it.sharpness })

        val eventOf = assignEvents(photos)
        val events = orderEvents(photos, eventOf)
        val eventIndex = events.withIndex().associate { it.value to it.index }

        val ordered = photos.sortedWith(
            compareBy<PlanPhoto> { eventIndex.getValue(eventOf.getValue(it.id)) }
                .thenBy { if (it.takenAt > 0) it.takenAt else Long.MAX_VALUE }
                .thenBy { it.folder }
                .thenBy { it.name }
        )

        val issues = HashMap<Long, MutableSet<Issue>>()
        val scores = HashMap<Long, Double>()
        for (p in photos) {
            val set = mutableSetOf<Issue>()
            if (p.failed) {
                set += Issue.UNREADABLE
            } else {
                if (median > 0 && p.sharpness < BLUR_FACTOR * median) set += Issue.BLURRY
                if (p.brightness < DARK_BRIGHTNESS || p.darkFraction > DARK_SHARE) set += Issue.TOO_DARK
                if (p.brightness > BRIGHT_BRIGHTNESS || p.brightFraction > BLOWN_SHARE) set += Issue.TOO_BRIGHT
                if (p.eyesClosed) set += Issue.EYES_CLOSED
            }
            issues[p.id] = set
            scores[p.id] = score(p, median)
        }

        // Bursts: keep the best shot (fewest problems, then highest score), the rest are duplicates.
        for (burst in findBursts(ordered.filter { !it.failed }, eventOf)) {
            val keeper = burst.minWith(
                compareBy<PlanPhoto> { issues.getValue(it.id).size }
                    .thenByDescending { scores.getValue(it.id) }
                    .thenBy { it.name }
            )
            burst.filter { it.id != keeper.id }.forEach { issues.getValue(it.id) += Issue.DUPLICATE }
        }

        val assessed = ordered.mapIndexed { i, p ->
            AssessedPhoto(p, eventOf.getValue(p.id), issues.getValue(p.id), scores.getValue(p.id), i)
        }
        return AlbumAnalysis(assessed, events)
    }

    /**
     * Picks [size] photos.
     * - [removed]: photos the user took out; never picked again.
     * - [keep]: photos already on the shortlist that must stay (used by "Replace removed").
     */
    fun shortlist(
        analysis: AlbumAnalysis,
        size: Int,
        removed: Set<Long> = emptySet(),
        keep: Set<Long> = emptySet(),
    ): List<Long> {
        if (size <= 0) return emptyList()
        val picked = LinkedHashSet<Long>()
        keep.filter { it !in removed && analysis.byId[it]?.photo?.failed == false }.forEach { picked += it }

        // Shares are based on all clean photos (including removed ones) so they stay stable
        // when the user removes a few and asks for replacements.
        val usableCounts = analysis.events.associateWith { e ->
            analysis.photos.count { it.event == e && it.usable }
        }
        val quotas = allocate(usableCounts, size)

        for (event in analysis.events) {
            val quota = quotas[event] ?: 0
            var have = picked.count { analysis.byId.getValue(it).event == event }
            if (have >= quota) continue
            val candidates = analysis.photos.filter { it.event == event && it.usable && it.id !in removed }
            for (p in spreadOverTime(candidates)) {
                if (have >= quota || picked.size >= size) break
                if (picked.add(p.id)) have++
            }
        }

        // Some events may have run short (removed photos); top up with the best clean photos left.
        if (picked.size < size) {
            analysis.photos
                .filter { it.usable && it.id !in removed && it.id !in picked }
                .sortedByDescending { it.score }
                .take(size - picked.size)
                .forEach { picked += it.id }
        }

        // Not enough clean photos: fill with the best of the rest (fewest problems first).
        if (picked.size < size) {
            analysis.photos
                .filter { Issue.UNREADABLE !in it.issues && it.id !in removed && it.id !in picked }
                .sortedWith(compareBy<AssessedPhoto> { it.issues.size }.thenByDescending { it.score })
                .take(size - picked.size)
                .forEach { picked += it.id }
        }

        return picked.sortedBy { analysis.byId.getValue(it).order }
    }

    /** Overall quality score, roughly 0..1. Higher is better. */
    fun score(p: PlanPhoto, medianSharpness: Double): Double {
        if (p.failed) return 0.0
        val sharp = if (medianSharpness > 0) min(p.sharpness / medianSharpness, 2.0) / 2.0 else 0.5
        val smile = max(p.smile, 0f).toDouble()
        val faces = if (p.faceCount > 0) 1.0 else 0.0
        val level = (1.0 - abs(p.brightness - 128.0) / 128.0).coerceIn(0.0, 1.0)
        val clipping = (1.0 - p.darkFraction - p.brightFraction).coerceIn(0.0, 1.0)
        return 0.4 * sharp + 0.25 * smile + 0.15 * faces + 0.2 * level * clipping
    }

    /**
     * Splits [total] album places across events in proportion to their usable photos
     * (largest remainder), at least 1 each, never more than an event has.
     */
    fun allocate(counts: Map<String, Int>, total: Int): Map<String, Int> {
        val active = counts.filterValues { it > 0 }
        val sum = active.values.sum()
        if (sum <= total) return active
        if (total <= 0) return emptyMap()
        if (total < active.size) {
            return active.entries.sortedByDescending { it.value }.take(total).associate { it.key to 1 }
        }
        val ideal = active.mapValues { total.toDouble() * it.value / sum }
        val alloc = active.keys.associateWithTo(LinkedHashMap()) { max(1, floor(ideal.getValue(it)).toInt()) }
        while (alloc.values.sum() > total) {
            val e = alloc.keys.filter { alloc.getValue(it) > 1 }
                .maxBy { alloc.getValue(it) - ideal.getValue(it) }
            alloc[e] = alloc.getValue(e) - 1
        }
        while (alloc.values.sum() < total) {
            val e = alloc.keys.filter { alloc.getValue(it) < active.getValue(it) }
                .maxBy { ideal.getValue(it) - alloc.getValue(it) }
            alloc[e] = alloc.getValue(e) + 1
        }
        return alloc
    }

    /**
     * Orders one event's photos so picks spread across the day: photos are grouped into
     * 5-minute slots, then taken round robin (each slot's best, then each slot's second best...),
     * best first within each round.
     */
    fun spreadOverTime(photos: List<AssessedPhoto>): List<AssessedPhoto> {
        val untimed = photos.filter { it.photo.takenAt <= 0 }.sortedBy { it.photo.name }
        val slots = photos.filter { it.photo.takenAt > 0 }
            .groupBy { it.photo.takenAt / SLOT_MILLIS }.values +
            untimed.chunked(UNTIMED_SLOT_SIZE)
        val sortedSlots = slots.map { slot -> slot.sortedByDescending { it.score } }
        val result = ArrayList<AssessedPhoto>(photos.size)
        var round = 0
        while (result.size < photos.size) {
            sortedSlots.mapNotNull { it.getOrNull(round) }
                .sortedByDescending { it.score }
                .forEach { result += it }
            round++
        }
        return result
    }

    /** Event name for every photo id. */
    fun assignEvents(photos: List<PlanPhoto>): Map<Long, String> {
        if (photos.isEmpty()) return emptyMap()
        val split = photos.map { it.folder.split('/').filter(String::isNotEmpty) }
        val common = split.reduce { acc, s -> acc.zip(s).takeWhile { it.first == it.second }.map { it.first } }
        val top = photos.indices.associate { i -> photos[i].id to split[i].getOrNull(common.size) }
        if (top.values.distinct().size >= 2) {
            return top.mapValues { it.value ?: OTHER_PHOTOS }
        }
        return splitByTime(photos, common.lastOrNull() ?: ALL_PHOTOS)
    }

    /** Everything is in one folder: start a new event wherever capture times jump by over 90 min. */
    private fun splitByTime(photos: List<PlanPhoto>, singleName: String): Map<Long, String> {
        val timed = photos.filter { it.takenAt > 0 }.sortedBy { it.takenAt }
        val groups = mutableListOf<MutableList<PlanPhoto>>()
        for (p in timed) {
            val last = groups.lastOrNull()?.last()
            if (last == null || p.takenAt - last.takenAt > EVENT_GAP_MILLIS) groups += mutableListOf(p)
            else groups.last() += p
        }
        if (groups.size <= 1) return photos.associate { it.id to singleName }

        val format = SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH)
        val result = HashMap<Long, String>()
        groups.forEachIndexed { i, g ->
            val name = "Event ${i + 1} · ${format.format(Date(g.first().takenAt))}"
            g.forEach { result[it.id] = name }
        }
        photos.filter { it.takenAt <= 0 }.forEach { result[it.id] = OTHER_PHOTOS }
        return result
    }

    /** Events in time order (by their first photo); events without any capture time go last. */
    private fun orderEvents(photos: List<PlanPhoto>, eventOf: Map<Long, String>): List<String> {
        val first = HashMap<String, Long>()
        for (p in photos) {
            val e = eventOf.getValue(p.id)
            val t = if (p.takenAt > 0) p.takenAt else Long.MAX_VALUE
            first[e] = min(first[e] ?: Long.MAX_VALUE, t)
        }
        return first.keys.sortedWith(
            compareBy<String> { first.getValue(it) }
                .thenBy { if (it == OTHER_PHOTOS) 1 else 0 }
                .thenBy { it }
        )
    }

    /**
     * Groups of near-identical shots: same event, back to back by capture time (at most 3 s
     * apart) or by file name when time is unknown, and with similar picture hashes.
     * [ordered] must be in capture order.
     */
    fun findBursts(ordered: List<PlanPhoto>, eventOf: Map<Long, String>): List<List<PlanPhoto>> {
        val result = mutableListOf<List<PlanPhoto>>()
        for ((_, inEvent) in ordered.groupBy { eventOf.getValue(it.id) }) {
            val timed = inEvent.filter { it.takenAt > 0 }.sortedWith(compareBy({ it.takenAt }, { it.name }))
            val untimed = inEvent.filter { it.takenAt <= 0 }.sortedBy { it.name }
            for ((sequence, useTime) in listOf(timed to true, untimed to false)) {
                var current = mutableListOf<PlanPhoto>()
                for (p in sequence) {
                    val prev = current.lastOrNull()
                    val linked = prev != null &&
                        (!useTime || p.takenAt - prev.takenAt <= BURST_GAP_MILLIS) &&
                        hamming(prev.hash, p.hash) <= BURST_HASH_DISTANCE
                    if (!linked) {
                        if (current.size >= 2) result += current
                        current = mutableListOf()
                    }
                    current += p
                }
                if (current.size >= 2) result += current
            }
        }
        return result
    }

    private fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
    }
}
