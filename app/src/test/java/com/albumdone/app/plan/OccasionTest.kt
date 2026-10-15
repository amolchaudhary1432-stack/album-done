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
        assertEquals(Occasion.BIRTHDAY, Occasion.guess(listOf("Aarav 1st Bday")))
        assertEquals(Occasion.BABY, Occasion.guess(listOf("Naming ceremony")))
        assertEquals(Occasion.TRIP, Occasion.guess(listOf("Goa trip 2025")))
        assertEquals(Occasion.OTHER, Occasion.guess(listOf("Camera", "100CANON")))
    }

    @Test
    fun onlyWeddingsAndEngagementsHaveACoupleAndTwoSides() {
        assertTrue(Occasion.WEDDING.couple && Occasion.ENGAGEMENT.couple)
        assertFalse(Occasion.BIRTHDAY.couple || Occasion.BABY.couple || Occasion.TRIP.couple || Occasion.OTHER.couple)
        assertEquals("the birthday star", Occasion.BIRTHDAY.role(first = true))
        assertEquals("Baby", Occasion.BABY.title(first = true))
        assertEquals("Groom", Occasion.WEDDING.title(first = false))
        assertEquals(Occasion.WEDDING, Occasion.of(null))
        assertEquals(Occasion.TRIP, Occasion.of("TRIP"))
    }
}
