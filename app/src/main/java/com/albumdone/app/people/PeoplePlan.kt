package com.albumdone.app.people

import com.albumdone.app.plan.AlbumPlanner
import com.albumdone.app.plan.EventKind
import com.albumdone.app.plan.Occasion
import com.albumdone.app.plan.PeopleTags
import com.albumdone.app.plan.PersonTag
import com.albumdone.app.plan.PlanPhoto

/**
 * Who the album is about and who the family is, from the photos and the faces in them (people
 * already grouped). Pure Kotlin: the app uses it through [PeopleRepository], and the photo harvest
 * tool (tools/harvest) uses the very same rules on a computer.
 */
object PeoplePlan {

    /** The couple (or the main person, or two), and whether that is clear enough not to ask. */
    data class Main(val first: Long?, val second: Long?, val sure: Boolean)

    fun findMain(occasion: Occasion, photos: List<PlanPhoto>): Main {
        val clear = photos.flatMap { p -> p.faces.filter { it.box.width >= 0.03f }.map { p.id to it } }
        val photosOf = clear.filter { it.second.personId != null }
            .groupBy { it.second.personId!! }.mapValues { e -> e.value.map { it.first }.toSet() }
        val byPhoto = clear.groupBy { it.first }
        if (!occasion.twoMain) {
            // A birthday, a baby, a trip: the album is about the most photographed person (or two).
            val main = PeopleClusterer.mainPeople(photosOf, occasion.pairs) ?: return Main(null, null, false)
            return Main(main.first, main.second, PeopleClusterer.mainSure(photosOf, main))
        }
        val hints = photos.filter { isBrideEvent(it.folder) }.map { it.id }.toSet()
        // Where each photo was taken decides how much "together" counts (see PeopleClusterer.detectCouple).
        val eventOf = AlbumPlanner.assignEvents(photos.map { PlanPhoto(id = it.id, name = it.name, folder = it.folder) })
        val weight = photos.associate { p ->
            val event = eventOf[p.id] ?: ""
            p.id to when {
                AlbumPlanner.ownFunction(event) -> PeopleClusterer.OWN_FUNCTION_WEIGHT
                AlbumPlanner.eventKind(event) == EventKind.RITUAL || AlbumPlanner.eventKind(event) == EventKind.PARTY -> PeopleClusterer.WEDDING_WEIGHT
                else -> 1.0
            }
        }
        val twoShots = byPhoto.filter { it.value.size == 2 }.keys
        val portraits = byPhoto.filter { (_, f) -> f.size == 2 && f.all { it.second.box.width >= 0.08f } }.keys
        val couple = PeopleClusterer.detectCouple(photosOf, hints, { weight[it] ?: 1.0 }, twoShots) ?: return Main(null, null, false)
        return Main(couple.first, couple.second, PeopleClusterer.coupleSure(photosOf, couple, { weight[it] ?: 1.0 }, portraits))
    }

    /**
     * The planner's tags: what the family said ([userTags]; [userTagged] are the people they set
     * themselves) wins, and the app's guess of the close family fills in everyone else, so the album
     * covers the family even if nobody tags anyone. A child's mummy and papa are added for child occasions.
     */
    fun tags(
        occasion: Occasion,
        photos: List<PlanPhoto>,
        first: Long?,
        second: Long?,
        userTags: Map<Long, PersonTag> = emptyMap(),
        userTagged: Set<Long> = emptySet(),
    ): PeopleTags {
        val eventOf = AlbumPlanner.assignEvents(photos)
        val forGuess = photos.map { p ->
            val event = eventOf[p.id] ?: ""
            FamilyGuess.PhotoPeople(
                photoId = p.id,
                event = event,
                familyEvent = AlbumPlanner.eventKind(event) != EventKind.PARTY,
                people = p.faces.filter { it.box.width >= AlbumPlanner.VISIBLE_FACE }.mapNotNull { it.personId }.toSet(),
                takenAt = p.takenAt,
            )
        }
        val guesses = FamilyGuess.guess(forGuess, first, second, sides = occasion.couple)
        val tags = FamilyGuess.mergeWithUserTags(guesses, PeopleTags(first, second, userTags, occasion = occasion), userTagged)
        // A child's album: mummy and papa as the family named them, else the app's guess.
        val parents = if (!occasion.child) emptyList() else
            userTags.values.filter { FamilyGuess.isParent(it.name) && it.id != first }.map { it.id }.take(2)
                .ifEmpty { guesses.values.filter { it.parent }.sortedByDescending { it.closeness }.map { it.personId } }
        return tags.copy(parents = parents)
    }

    private val BRIDE_WORDS = listOf("mehendi", "mehndi", "mehandi", "bride", "bridal", "dulhan", "chooda", "chuda")

    /** The bride's own function (her mehendi): tells the bride from the groom. */
    fun isBrideEvent(folder: String): Boolean = folder.lowercase().let { f -> BRIDE_WORDS.any { f.contains(it) } }
}
