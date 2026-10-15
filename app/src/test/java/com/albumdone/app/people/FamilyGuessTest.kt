package com.albumdone.app.people

import com.albumdone.app.people.FamilyGuess.PhotoPeople
import com.albumdone.app.plan.PeopleTags
import com.albumdone.app.plan.PersonTag
import com.albumdone.app.plan.Priority
import com.albumdone.app.plan.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyGuessTest {

    private val BRIDE = 1L
    private val GROOM = 2L
    private var nextPhoto = 1L

    private fun photos(event: String, n: Int, people: Set<Long>, familyEvent: Boolean = event != "Reception") =
        List(n) { PhotoPeople(nextPhoto++, event, familyEvent, people) }

    /** A small wedding: bride's parents, groom's parents, an aunt, guests and a busy assistant. */
    private fun wedding(): List<PhotoPeople> {
        val aai = 10L; val baba = 11L; val sasu = 20L; val sasre = 21L; val mavshi = 12L
        val guests = (100L..140L)
        val assistant = 99L
        return photos("Haldi (bride)", 12, setOf(BRIDE, aai)) +
            photos("Haldi (bride)", 6, setOf(BRIDE, baba, aai)) +
            photos("Haldi (bride)", 4, setOf(BRIDE, mavshi)) +
            photos("Haldi (groom)", 10, setOf(GROOM, sasu)) +
            photos("Haldi (groom)", 6, setOf(GROOM, sasre)) +
            photos("Mehendi", 8, setOf(BRIDE, aai, mavshi)) +
            photos("Mehendi", 2, setOf(BRIDE, baba)) +
            photos("Wedding", 10, setOf(BRIDE, GROOM, aai, baba)) +
            photos("Wedding", 8, setOf(BRIDE, GROOM, sasu, sasre)) +
            photos("Wedding", 3, setOf(BRIDE, GROOM, mavshi)) +
            photos("Reception", 6, setOf(BRIDE, GROOM, aai, baba, sasu, sasre)) +
            guests.flatMap { g -> photos("Reception", 2, setOf(BRIDE, GROOM, g)) } +
            // The photographer's assistant keeps walking through the reception, never with the couple.
            photos("Reception", 15, setOf(assistant))
    }

    @Test
    fun familyIsFoundAndGuestsAreNot() {
        val g = FamilyGuess.guess(wedding(), BRIDE, GROOM)
        for (id in listOf(10L, 11L, 20L, 21L, 12L)) assertTrue("person $id", g.getValue(id).closeFamily)
        for (id in 100L..140L) assertFalse("guest $id", g.getValue(id).closeFamily)
        assertFalse("assistant", g.getValue(99L).closeFamily)
    }

    @Test
    fun sidesComeFromWhoTheyArePhotographedWith() {
        val g = FamilyGuess.guess(wedding(), BRIDE, GROOM)
        assertEquals(Side.BRIDE, g.getValue(10L).side)
        assertEquals(Side.BRIDE, g.getValue(12L).side)
        assertEquals(Side.GROOM, g.getValue(20L).side)
        assertEquals(Side.GROOM, g.getValue(21L).side)
        assertEquals(Side.UNKNOWN, g.getValue(105L).side)
    }

    @Test
    fun closestTwoOnEachSideGetTheMostPhotos() {
        val g = FamilyGuess.guess(wedding(), BRIDE, GROOM)
        assertEquals(Priority.MUST, g.getValue(10L).priority)
        assertEquals(Priority.MUST, g.getValue(11L).priority)
        assertEquals(Priority.MUST, g.getValue(20L).priority)
        assertEquals(Priority.MUST, g.getValue(21L).priority)
        assertEquals(Priority.IMPORTANT, g.getValue(12L).priority) // the aunt: in, but fewer photos
        assertEquals(Priority.NORMAL, g.getValue(120L).priority)
    }

    @Test
    fun peopleSeenOnceOrTwiceAreNeverGuessed() {
        val g = FamilyGuess.guess(photos("Reception", 2, setOf(BRIDE, GROOM, 50L)), BRIDE, GROOM)
        assertFalse(g.getValue(50L).closeFamily)
        assertEquals(0.0, g.getValue(50L).closeness, 1e-9)
    }

    @Test
    fun whatTheCoupleSaysWinsOverTheGuess() {
        val g = FamilyGuess.guess(wedding(), BRIDE, GROOM)
        val user = PeopleTags(BRIDE, GROOM, mapOf(
            12L to PersonTag(12L, "Mavshi", Priority.MUST, Side.BRIDE, elder = false), // looked at and set
            99L to PersonTag(99L, "", Priority.SKIP),                                  // "not family"
            20L to PersonTag(20L, "Sasubai"),                                          // only named
        ))
        val merged = FamilyGuess.mergeWithUserTags(g, user, userTagged = setOf(12L, 99L))
        assertEquals(Priority.MUST, merged.tag(12L).priority)
        assertEquals(Priority.SKIP, merged.tag(99L).priority)
        // Only a name was given: the guess still decides how important and which side.
        assertEquals("Sasubai", merged.tag(20L).name)
        assertEquals(Priority.MUST, merged.tag(20L).priority)
        assertEquals(Side.GROOM, merged.tag(20L).side)
        // Untouched people keep the guess.
        assertEquals(Priority.MUST, merged.tag(10L).priority)
        assertEquals(BRIDE, merged.brideId)
    }

    @Test
    fun relationsDecideImportance() {
        assertEquals(Priority.MUST to true, FamilyGuess.fromRelation("Aaji"))
        assertEquals(Priority.MUST to true, FamilyGuess.fromRelation("nani"))
        assertEquals(Priority.MUST to false, FamilyGuess.fromRelation("Aai"))
        assertEquals(Priority.MUST to false, FamilyGuess.fromRelation("Tai"))
        assertEquals(Priority.IMPORTANT to false, FamilyGuess.fromRelation("Mama"))
        assertEquals(Priority.IMPORTANT to false, FamilyGuess.fromRelation("Friend"))
        assertEquals(Priority.SKIP to false, FamilyGuess.fromRelation("Not family"))
    }

    @Test
    fun noCoupleStillWorks() {
        val g = FamilyGuess.guess(wedding(), null, null)
        assertTrue(g.isNotEmpty())
        assertTrue(g.values.all { it.side == Side.UNKNOWN })
    }
}
