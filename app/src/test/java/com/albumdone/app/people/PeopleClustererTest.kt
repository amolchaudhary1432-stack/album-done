package com.albumdone.app.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.sqrt

class PeopleClustererTest {

    private val random = Random(7)
    private var nextFace = 1L

    private fun unit(v: FloatArray): FloatArray {
        val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(v.size) { v[it] / n }
    }

    private fun randomUnit(): FloatArray = unit(FloatArray(128) { random.nextGaussian().toFloat() })

    /** A face of [person]: their identity plus noise, like real photos of one person. */
    private fun faceOf(person: FloatArray, noise: Float = 0.9f, quality: Float = 0.8f, photo: Long = nextFace): FaceVector {
        val n = randomUnit()
        return FaceVector(nextFace++, photo, unit(FloatArray(128) { person[it] + noise * n[it] }), quality)
    }

    @Test
    fun groupsFacesIntoPeople() {
        val people = List(5) { randomUnit() }
        val sizes = listOf(40, 25, 12, 6, 3)
        val faces = people.flatMapIndexed { i, p -> List(sizes[i]) { faceOf(p) } }.shuffled(random)
        val owner = HashMap<Long, Int>()
        faces.forEach { f -> owner[f.faceId] = people.indices.maxBy { PeopleClusterer.cosine(people[it], f.embedding) } }

        val groups = PeopleClusterer.cluster(faces)
        assertEquals(5, groups.size)
        assertEquals(sizes, groups.map { it.size })
        for (g in groups) assertEquals(1, g.map { owner.getValue(it) }.toSet().size)
    }

    @Test
    fun poorFacesOnlyJoinExistingPeople() {
        val a = randomUnit()
        val good = List(10) { faceOf(a) }
        val poorMatch = faceOf(a, noise = 0.6f, quality = 0.1f)
        val poorStranger = faceOf(randomUnit(), quality = 0.1f)
        val groups = PeopleClusterer.cluster(good + poorMatch + poorStranger)
        assertEquals(1, groups.size)
        assertTrue(poorMatch.faceId in groups[0])
        assertTrue(groups.flatten().none { it == poorStranger.faceId })
    }

    @Test
    fun samePersonSplitAcrossLightingIsMerged() {
        // Two looks of one person (day and stage light) share most of their identity.
        val base = randomUnit()
        val day = unit(FloatArray(128) { base[it] + 0.35f * randomUnit()[it] })
        val stage = unit(FloatArray(128) { base[it] + 0.35f * randomUnit()[it] })
        val faces = List(15) { faceOf(day, noise = 0.6f) } + List(15) { faceOf(stage, noise = 0.6f) }
        assertEquals(1, PeopleClusterer.cluster(faces).size)
    }

    @Test
    fun splitSeparatesTwoPeopleWronglyJoined() {
        val a = randomUnit(); val b = randomUnit()
        val faces = List(8) { faceOf(a, noise = 0.5f) } + List(5) { faceOf(b, noise = 0.5f) }
        val parts = PeopleClusterer.split(faces)
        assertEquals(2, parts.size)
        assertEquals(8, parts[0].size)
        assertEquals(13, parts.sumOf { it.size })
    }

    @Test
    fun findMoreLooksInLooseFacesOnly() {
        val a = randomUnit()
        val person = List(10) { faceOf(a) }
        val looseSame = faceOf(a, noise = 1.4f)          // a weaker match, sitting alone
        val inBigGroup = faceOf(a, noise = 1.4f)         // same, but already in a big group
        val other = faceOf(randomUnit())
        val found = PeopleClusterer.findMore(person, listOf(looseSame, inBigGroup, other)) { id ->
            if (id == inBigGroup.faceId) 20 else 1
        }
        assertEquals(listOf(looseSame.faceId), found)
    }

    @Test
    fun coupleIsTheMostPhotographedPairTogether() {
        // 1 and 2 are the couple; 3 is a busy photographer's assistant often in frame alone.
        val photosOf = mapOf(
            1L to (1L..60L).toSet(),
            2L to (20L..80L).toSet(),
            3L to (100L..170L).toSet(),
            4L to (1L..10L).toSet(),
        )
        val (bride, groom) = PeopleClusterer.detectCouple(photosOf)!!
        assertEquals(setOf(1L, 2L), setOf(bride, groom))
    }

    @Test
    fun brideIsTheOneSeenMoreInBrideEvents() {
        val photosOf = mapOf(1L to (1L..50L).toSet(), 2L to (1L..60L).toSet())
        // Photos 41..50 are from Mehendi, and only person 1 is in them.
        val withHints = mapOf(1L to (1L..50L).toSet() + (200L..230L), 2L to (1L..40L).toSet() + (300L..330L))
        assertEquals(1L to 2L, PeopleClusterer.detectCouple(withHints, (41L..50L).toSet()))
        // Without hints the more photographed person is taken as the bride (the couple can swap).
        assertEquals(2L to 1L, PeopleClusterer.detectCouple(photosOf))
    }

    @Test
    fun groomIsWhoIsBesideTheBrideAtTheWeddingNotAtHerHaldi() {
        // A real Bengali wedding, photographed from the bride's side: her aunt is beside her all
        // through adhibaas and haldi (photos 1-40); the groom appears only at the rituals (41-64).
        val ownFunction = (1L..40L).toSet()
        val photosOf = mapOf(
            1L to (1L..64L).toSet(),                 // bride
            2L to (1L..12L).toSet(),                 // aunt, at the bride's own functions
            3L to setOf(50L, 51L, 52L, 55L, 58L, 60L, 61L), // groom
            4L to (41L..44L).toSet() + 90L,          // priest
        )
        val weight = { id: Long -> if (id in ownFunction) PeopleClusterer.OWN_FUNCTION_WEIGHT else PeopleClusterer.WEDDING_WEIGHT }
        assertEquals(1L to 3L, PeopleClusterer.detectCouple(photosOf, emptySet(), weight, twoShots = setOf(50L, 51L, 60L)))
        // Counting photos alone, the aunt looks like the partner.
        assertEquals(setOf(1L, 2L), PeopleClusterer.detectCouple(photosOf)!!.toList().toSet())
    }

    @Test
    fun noCoupleWithoutTwoPeopleTogether() {
        assertNull(PeopleClusterer.detectCouple(mapOf(1L to setOf(1L, 2L))))
        assertNull(PeopleClusterer.detectCouple(mapOf(1L to setOf(1L, 2L), 2L to setOf(3L, 4L))))
    }

    @Test
    fun emptyInput() {
        assertTrue(PeopleClusterer.cluster(emptyList()).isEmpty())
    }
}
