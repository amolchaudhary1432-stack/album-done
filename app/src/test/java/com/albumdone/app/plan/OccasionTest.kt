package com.albumdone.app.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OccasionTest {

    @Test
    fun guessedFromTheFolderNames() {
        assertEquals(Occasion.WEDDING, Occasion.guess(listOf("Sharma Wedding", "01 Haldi", "02 Mehendi")))
        assertEquals(Occasion.WEDDING, Occasion.guess(listOf("DCIM", "01 Engagement", "03 Reception")))
        assertEquals(Occasion.ENGAGEMENT, Occasion.guess(listOf("Riya Sakharpuda")))
        assertEquals(Occasion.KIDS_BIRTHDAY, Occasion.guess(listOf("Aarav 1st Bday")))
        assertEquals(Occasion.KIDS_BIRTHDAY, Occasion.guess(listOf("Riya birthday party")))
        assertEquals(Occasion.BIRTHDAY, Occasion.guess(listOf("Baba 60th Birthday")))
        assertEquals(Occasion.BIRTHDAY, Occasion.guess(listOf("Aaji Amrut Mahotsav")))
        assertEquals(Occasion.KIDS_BIRTHDAY, Occasion.of("BABY"))
        assertEquals(Occasion.TRIP, Occasion.guess(listOf("Goa trip 2025")))
        assertEquals(Occasion.OTHER, Occasion.guess(listOf("Camera", "100CANON")))
    }

    @Test
    fun onlyWeddingsAndEngagementsHaveACoupleAndTwoSides() {
        assertTrue(Occasion.WEDDING.couple && Occasion.ENGAGEMENT.couple)
        assertFalse(Occasion.BIRTHDAY.couple || Occasion.KIDS_BIRTHDAY.couple || Occasion.TRIP.couple || Occasion.OTHER.couple)
        assertTrue(Occasion.KIDS_BIRTHDAY.child)
        assertFalse(Occasion.BIRTHDAY.child || Occasion.WEDDING.child)
        assertEquals("Birthday", Occasion.KIDS_BIRTHDAY.label)
        assertEquals("A child's", Occasion.KIDS_BIRTHDAY.sub)
        assertEquals("the birthday star", Occasion.BIRTHDAY.role(first = true))
        assertEquals("Birthday star", Occasion.KIDS_BIRTHDAY.title(first = true))
        assertEquals("Groom", Occasion.WEDDING.title(first = false))
        assertEquals(Occasion.WEDDING, Occasion.of(null))
        assertEquals(Occasion.TRIP, Occasion.of("TRIP"))
    }
}
