package com.albumdone.app.analysis

import com.albumdone.app.plan.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneRulesTest {

    @Test
    fun backOfAGuestInTheForeground() {
        val back = Box(0.1f, 0.5f, 0.45f, 1f)        // big, from the bottom edge, no face
        val guestFacing = Box(0.55f, 0.4f, 0.9f, 1f)  // same, but with a face in the head area
        val face = Box(0.65f, 0.42f, 0.75f, 0.55f)
        assertEquals(1, SceneRules.foregroundBacks(listOf(back, guestFacing), listOf(face)))
        // Someone small in the distance is not blocking anything.
        assertEquals(0, SceneRules.foregroundBacks(listOf(Box(0.4f, 0.3f, 0.45f, 0.5f)), emptyList()))
    }

    @Test
    fun peopleWithoutFacesAndHeadCrops() {
        val withFace = Box(0.0f, 0.2f, 0.1f, 0.9f)
        val small1 = Box(0.5f, 0.3f, 0.55f, 0.6f)
        val small2 = Box(0.56f, 0.3f, 0.61f, 0.6f)
        val farAway = Box(0.9f, 0.3f, 0.95f, 0.6f)
        val faces = listOf(Box(0.02f, 0.22f, 0.08f, 0.3f))
        val missing = SceneRules.peopleWithoutFaces(listOf(withFace, small1, small2, farAway), faces)
        assertEquals(listOf(small1, small2, farAway), missing)
        val crops = SceneRules.headCrops(missing)
        assertEquals(2, crops.size) // the two neighbours share one closer look
        assertTrue(crops.all { it.width >= 0.2f - 1e-6f && it.left >= 0f && it.right <= 1f })
        assertTrue(crops[0].left <= small1.left && crops[0].right >= small2.right)
        assertTrue(crops[0].top <= small1.top)
    }

    @Test
    fun headCropsAreLimited() {
        val people = List(8) { i -> Box(i * 0.12f, 0.3f, i * 0.12f + 0.03f, 0.6f) }
        assertEquals(3, SceneRules.headCrops(people, maxCrops = 3).size)
        assertTrue(SceneRules.headCrops(emptyList()).isEmpty())
    }

    @Test
    fun duplicateFaces() {
        val known = listOf(Box(0.1f, 0.1f, 0.2f, 0.25f))
        assertTrue(SceneRules.isDuplicate(Box(0.11f, 0.12f, 0.21f, 0.26f), known))
        assertFalse(SceneRules.isDuplicate(Box(0.5f, 0.1f, 0.6f, 0.25f), known))
    }
}
