package com.albumdone.app.plan

import org.junit.Assert.assertTrue
import org.junit.Test

class AppealTest {

    private fun face(smile: Float = 0.8f, eyes: EyeState = EyeState.OPEN) =
        PlanFace(box = Box(0.4f, 0.2f, 0.55f, 0.4f), eyes = eyes, expression = Expression.SMILE, smile = smile,
            lookingAtCamera = true, sharpness = 900.0, brightness = 130.0)

    @Test
    fun everyLearnedWeightIsAFeatureTheAppMeasures() {
        val a = AlbumPlanner.analyze(listOf(PlanPhoto(1, "a.jpg", "", faces = listOf(face()))), PeopleTags())
        val names = Appeal.features(a.photos[0], a, 100.0, 128.0).keys
        for (name in AppealWeights.WEIGHTS.keys - "bias") assertTrue("unknown feature $name", name in names)
    }

    @Test
    fun aSmilingClearPortraitAppealsMoreThanADarkBlurredOne() {
        val photos = listOf(
            PlanPhoto(1, "good.jpg", "", takenAt = 1_000, faces = listOf(face())),
            PlanPhoto(2, "bad.jpg", "", takenAt = 600_000, sharpness = 5.0, peakSharpness = 5.0, brightness = 20.0,
                darkFraction = 0.9, faces = listOf(face(smile = 0f, eyes = EyeState.CLOSED).copy(sharpness = 20.0, brightness = 15.0))),
        )
        val appeal = Appeal.score(AlbumPlanner.analyze(photos, PeopleTags()))
        assertTrue("$appeal", appeal.getValue(1) > appeal.getValue(2))
    }
}
