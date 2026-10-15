package com.albumdone.app.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanDumpTest {

    @Test
    fun aWeddingSurvivesTheRoundTrip() {
        val face = PlanFace(personId = 7L, box = Box(0.1f, 0.2f, 0.3f, 0.4f), eyes = EyeState.UNKNOWN, expression = Expression.LAUGH,
            smile = 0.8f, lookingAtCamera = false, directionX = -0.4f, sharpness = 512.5, brightness = 99.0, backlit = true,
            occlusion = 0.2f, cutOff = 0.1f)
        val photos = listOf(
            PlanPhoto(id = 1, name = "IMG_0001.jpg", folder = "01 Haldi", takenAt = 1_700_000_000_000L, width = 1920, height = 1280,
                sharpness = 321.0, peakSharpness = 900.0, brightness = 140.0, darkFraction = 0.01, brightFraction = 0.02,
                hash = -1234567890123L, tilt = 1.5f, faces = listOf(face, face.copy(personId = null)),
                phones = listOf(Box(0.5f, 0.5f, 0.6f, 0.7f)), foregroundBacks = 1, peopleArea = 0.3f),
            PlanPhoto(id = 2, name = "IMG_0002.jpg", folder = "", failed = true),
        )
        val tags = PeopleTags(7L, 8L, mapOf(9L to PersonTag(9L, "Aai", Priority.MUST, Side.BRIDE, elder = true, guessed = false)))
        val (back, backTags) = PlanDump.read(PlanDump.write(photos, tags))
        assertEquals(photos, back)
        assertEquals(tags, backTags)
    }
}
