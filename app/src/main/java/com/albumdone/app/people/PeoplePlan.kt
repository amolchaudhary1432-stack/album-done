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
        val found = PeopleClusterer.detectCouple(photosOf, hints, { weight[it] ?: 1.0 }, twoShots) ?: return Main(null, null, false)
        val couple = brideFirst(found, photos, byPhoto)
        return Main(couple.first, couple.second, PeopleClusterer.coupleSure(photosOf, couple, { weight[it] ?: 1.0 }, portraits))
    }

    /** Photos of one person alone needed on each side before the app trusts what it sees. */
    const val SOLO_PHOTOS = 3
    /** How much more "woman" the bride's own photos must look than the groom's to swap them. */
    const val WOMAN_MARGIN = 0.01

    /**
     * Which of the two is the bride, from what the photos show (the album brain's eyes, SigLIP 2): the
     * one whose photos alone look more like a woman. The most photographed is not always the bride (a
     * groom's own haldi folder is mostly him). On two real weddings her solo photos leaned "woman"
     * 96-97% of the time, his 23-41%. Unchanged when the brain is missing or there are too few photos.
     */
    fun brideFirst(couple: Pair<Long, Long>, photos: List<PlanPhoto>, byPhoto: Map<Long, List<Pair<Long, com.albumdone.app.plan.PlanFace>>>): Pair<Long, Long> {
        val a = womanScore(couple.first, photos, byPhoto).second ?: return couple
        val b = womanScore(couple.second, photos, byPhoto).second ?: return couple
        return if (b - a > WOMAN_MARGIN) couple.second to couple.first else couple
    }

    /** (photos of [person] alone that the eyes saw, how much they look like a woman; null below [SOLO_PHOTOS]). */
    fun womanScore(person: Long, photos: List<PlanPhoto>, byPhoto: Map<Long, List<Pair<Long, com.albumdone.app.plan.PlanFace>>>): Pair<Int, Double?> {
        val woman = com.albumdone.app.plan.AlbumMind.weights?.direction("woman", "man") ?: return 0 to null
        val byId = photos.associateBy { it.id }
        val solo = byPhoto.filter { (_, f) -> f.size == 1 && f[0].second.personId == person }.keys.mapNotNull { byId[it]?.scene }
        if (solo.size < SOLO_PHOTOS) return solo.size to null
        return solo.size to solo.map { v ->
            var n = 0.0; var d = 0.0
            for (i in v.indices) { n += v[i].toDouble() * v[i]; d += v[i] * woman[i] }
            d / kotlin.math.sqrt(n.coerceAtLeast(1e-12))
        }.average()
    }

    /** In plain numbers, why this couple is the bride and groom (for the phone test's report). */
    fun explainCouple(brideId: Long?, groomId: Long?, photos: List<PlanPhoto>): String {
        val clear = photos.flatMap { p -> p.faces.filter { it.box.width >= 0.03f }.map { p.id to it } }
        val byPhoto = clear.groupBy { it.first }
        val brain = com.albumdone.app.plan.AlbumMind.weights
        return buildString {
            append("photos ${photos.size}, with what they show (SigLIP) ${photos.count { it.scene != null }}, brain loaded ${brain != null}, ")
            append("woman/man vectors ${brain?.direction("woman", "man") != null}\n")
            for ((role, id) in listOf("bride" to brideId, "groom" to groomId)) {
                if (id == null) { append("$role: none\n"); continue }
                val (n, s) = womanScore(id, photos, byPhoto)
                append("$role person $id: in ${clear.count { it.second.personId == id }} photos, alone in $n with scene numbers, woman-minus-man ${s?.let { "%.4f".format(it) } ?: "-"}\n")
            }
        }
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
