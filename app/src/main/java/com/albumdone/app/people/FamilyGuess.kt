package com.albumdone.app.people

import com.albumdone.app.plan.PeopleTags
import com.albumdone.app.plan.PersonTag
import com.albumdone.app.plan.Priority
import com.albumdone.app.plan.Side
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Guesses who the close family is, so the couple does not have to tag anyone.
 * Pure Kotlin, unit-tested.
 *
 * What a photographer's photos reveal:
 * - Family come to many functions (Haldi, Mehendi, Wedding, Reception); most guests only come to one.
 * - Family are photographed with the couple again and again.
 * - Family are part of the rituals and the getting-ready, not just the reception stage.
 * - Bride's family appear with the bride without the groom (her Haldi, her Mehendi) more than with
 *   the groom alone, and the other way round.
 */
object FamilyGuess {

    /** One photo, as far as people are concerned. */
    data class PhotoPeople(
        val photoId: Long,
        val event: String,
        /** A ritual or family function (Haldi, Mehendi, Wedding...), not just the reception stage. */
        val familyEvent: Boolean,
        val people: Set<Long>,
    )

    data class Guess(
        val personId: Long,
        /** 0..1, how likely this person is close family. */
        val closeness: Double,
        val closeFamily: Boolean,
        val side: Side,
        val priority: Priority,
    )

    const val MIN_PHOTOS = 3
    const val MAX_CLOSE = 14
    const val CLOSE_SHARE_OF_TOP = 0.45

    fun guess(photos: List<PhotoPeople>, brideId: Long?, groomId: Long?): Map<Long, Guess> {
        val couple = setOfNotNull(brideId, groomId)
        val events = photos.map { it.event }.toSet()
        val eventCount = max(1, events.size)

        class Stats {
            var photos = 0; var withCouple = 0; var withBrideOnly = 0; var withGroomOnly = 0; var familyEvent = 0
            val events = HashSet<String>()
        }
        val stats = HashMap<Long, Stats>()
        for (p in photos) {
            val bride = brideId != null && brideId in p.people
            val groom = groomId != null && groomId in p.people
            for (person in p.people) {
                if (person in couple) continue
                val s = stats.getOrPut(person) { Stats() }
                s.photos++
                s.events += p.event
                if (bride || groom) s.withCouple++
                if (bride && !groom) s.withBrideOnly++
                if (groom && !bride) s.withGroomOnly++
                if (p.familyEvent) s.familyEvent++
            }
        }

        val raw = stats.mapValues { (_, s) ->
            if (s.photos < MIN_PHOTOS) 0.0 else {
                val spread = s.events.size.toDouble() / eventCount                 // came to many functions
                val withCouple = ln(1.0 + s.withCouple) / ln(1.0 + 40.0)           // with the couple, often
                val ritual = s.familyEvent.toDouble() / s.photos                   // part of family moments
                val coupleShare = s.withCouple.toDouble() / s.photos              // not just in the crowd
                0.35 * spread + 0.30 * min(1.0, withCouple) + 0.15 * ritual + 0.20 * coupleShare
            }
        }
        val top = raw.values.maxOrNull() ?: 0.0
        val ranked = raw.entries.filter { it.value > 0 }.sortedByDescending { it.value }
        // A small set of photos cannot show many close relatives with any confidence.
        val maxClose = min(MAX_CLOSE, max(6, photos.size / 40))
        val close = ranked.filter { it.value >= CLOSE_SHARE_OF_TOP * top }.take(maxClose).map { it.key }.toSet()

        fun sideOf(s: Stats): Side {
            val diff = s.withBrideOnly - s.withGroomOnly
            val margin = max(2, (0.2 * (s.withBrideOnly + s.withGroomOnly)).toInt())
            return when {
                diff >= margin -> Side.BRIDE
                -diff >= margin -> Side.GROOM
                else -> Side.UNKNOWN
            }
        }

        // The two closest people on each side are most likely the parents: they get the most photos.
        val mustIds = HashSet<Long>()
        for (side in listOf(Side.BRIDE, Side.GROOM)) {
            ranked.filter { it.key in close && sideOf(stats.getValue(it.key)) == side }.take(2).forEach { mustIds += it.key }
        }

        return stats.keys.associateWith { id ->
            val s = stats.getValue(id)
            val c = if (top > 0) raw.getValue(id) / top else 0.0
            val isClose = id in close
            Guess(
                personId = id,
                closeness = c,
                closeFamily = isClose,
                side = sideOf(s),
                priority = when {
                    id in mustIds -> Priority.MUST
                    isClose -> Priority.IMPORTANT
                    else -> Priority.NORMAL
                },
            )
        }
    }

    /**
     * The tags the planner uses: what the couple said wins; the guess fills in everyone else.
     * [userTagged] are the people the couple has looked at and set themselves.
     */
    fun mergeWithUserTags(guesses: Map<Long, Guess>, user: PeopleTags, userTagged: Set<Long>): PeopleTags {
        val merged = HashMap<Long, PersonTag>()
        for ((id, g) in guesses) {
            merged[id] = PersonTag(id, priority = g.priority, side = g.side, guessed = true)
        }
        for ((id, t) in user.tags) {
            val g = guesses[id]
            merged[id] = if (id in userTagged || g == null) t else t.copy(
                priority = if (t.priority == Priority.NORMAL) g.priority else t.priority,
                side = if (t.side == Side.UNKNOWN) g.side else t.side,
                guessed = t.priority == Priority.NORMAL,
            )
        }
        return user.copy(tags = merged)
    }

    // ---- What a relation means ----

    private val PARENTS = setOf("aai", "baba", "mummy", "mom", "mother", "papa", "dad", "father", "maa", "pappa", "amma", "appa")
    private val GRANDPARENTS = setOf("aaji", "ajoba", "aji", "dadi", "nani", "nana", "grandmother", "grandfather", "grandma", "grandpa")
    private val SIBLINGS = setOf("tai", "dada", "bhau", "didi", "bhaiya", "bhai", "behen", "bahin", "sister", "brother", "best friend")

    /** "Aaji" means: at least 5 photos, and elders come first. */
    fun fromRelation(relation: String): Pair<Priority, Boolean> {
        val r = relation.trim().lowercase()
        return when {
            r in GRANDPARENTS -> Priority.MUST to true
            r in PARENTS || r in SIBLINGS -> Priority.MUST to false
            r.isEmpty() -> Priority.NORMAL to false
            r == "not family" || r == "skip" -> Priority.SKIP to false
            r.startsWith("bride's ") || r.startsWith("groom's ") -> fromRelation(r.substringAfter("'s "))
            else -> Priority.IMPORTANT to false
        }
    }
}
