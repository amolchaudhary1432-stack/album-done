package com.albumdone.app.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayTest {

    @Test
    fun eventNamesWithoutThePhotographersNumbers() {
        assertEquals("Haldi", Display.eventName("01 Haldi"))
        assertEquals("Wedding", Display.eventName("3. Wedding"))
        assertEquals("Reception Party", Display.eventName("02_Reception_Party"))
        assertEquals("Mehendi", Display.eventName("Mehendi"))
        assertEquals("2023", Display.eventName("2023"))
    }

    @Test
    fun tallPhotoThumbnailCentresOnTheFace() {
        // Found on the phone test: a full-length portrait (970 x 2204) showed only the suit.
        val face = Box(0.44f, 0.10f, 0.73f, 0.26f)
        val (h, v) = Display.cropBias(970, 2204, listOf(face))
        assertEquals(0f, h, 1e-6f)
        assertEquals(-1f, v, 1e-6f) // face near the top: show the top of the photo
        // A face in the middle of a tall photo: show the middle.
        val (_, mid) = Display.cropBias(1000, 2000, listOf(Box(0.4f, 0.45f, 0.6f, 0.55f)))
        assertEquals(0f, mid, 1e-6f)
    }

    @Test
    fun widePhotoThumbnailKeepsEveryoneIn() {
        val left = Box(0.21f, 0.07f, 0.34f, 0.34f)
        val right = Box(0.70f, 0.11f, 0.84f, 0.38f)
        val (h, v) = Display.cropBias(1126, 661, listOf(left, right))
        assertEquals(0f, v, 1e-6f)
        assertTrue(h > -0.2f && h < 0.2f) // both faces: stay near the middle
        val (onlyRight, _) = Display.cropBias(1126, 661, listOf(right))
        assertTrue(onlyRight > 0.5f)
    }

    @Test
    fun noFacesKeepsTheUsualCrop() {
        assertEquals(0f to 0f, Display.cropBias(1500, 1000, emptyList()))
        assertTrue(Display.cropBias(1000, 1500, emptyList()).second < 0f)
    }
}
