package com.albumdone.app.analysis

import com.albumdone.app.plan.Box
import com.albumdone.app.plan.EyeState
import com.albumdone.app.plan.Expression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FaceMetricsTest {

    // ---- Eyes ----

    @Test
    fun eyesOpenClosedHalfAndLookingDown() {
        assertEquals(EyeState.OPEN, FaceMetrics.eyeState(mapOf("eyeBlinkLeft" to 0.05f, "eyeBlinkRight" to 0.1f)))
        assertEquals(EyeState.CLOSED, FaceMetrics.eyeState(mapOf("eyeBlinkLeft" to 0.8f, "eyeBlinkRight" to 0.7f)))
        assertEquals(EyeState.HALF, FaceMetrics.eyeState(mapOf("eyeBlinkLeft" to 0.5f, "eyeBlinkRight" to 0.48f)))
        // A real smiling portrait (measured): slightly narrowed eyes are still open.
        assertEquals(EyeState.OPEN, FaceMetrics.eyeState(mapOf("eyeBlinkLeft" to 0.33f, "eyeBlinkRight" to 0.33f, "eyeLookDownLeft" to 0.54f)))
        // Looking down at the ritual fire lowers the eyelids: not a blink.
        assertEquals(
            EyeState.LOOKING_DOWN,
            FaceMetrics.eyeState(mapOf("eyeBlinkLeft" to 0.5f, "eyeBlinkRight" to 0.5f, "eyeLookDownLeft" to 0.6f, "eyeLookDownRight" to 0.6f)),
        )
        // A wink (one eye) is not "eyes closed".
        assertEquals(EyeState.HALF, FaceMetrics.eyeState(mapOf("eyeBlinkLeft" to 0.9f, "eyeBlinkRight" to 0.1f)))
    }

    // ---- Expression ----

    @Test
    fun smileLaughTalkTearsAndNatural() {
        assertEquals(Expression.SMILE, FaceMetrics.expression(mapOf("mouthSmileLeft" to 0.7f, "mouthSmileRight" to 0.6f)))
        assertEquals(Expression.LAUGH, FaceMetrics.expression(mapOf("mouthSmileLeft" to 0.7f, "mouthSmileRight" to 0.6f, "jawOpen" to 0.5f)))
        assertEquals(Expression.MID_TALK, FaceMetrics.expression(mapOf("jawOpen" to 0.3f)))
        assertEquals(Expression.MID_TALK, FaceMetrics.expression(mapOf("mouthFunnel" to 0.4f)))
        assertEquals(Expression.MID_TALK, FaceMetrics.expression(mapOf("mouthPucker" to 0.5f, "jawOpen" to 0.1f)))
        assertEquals(Expression.EMOTIONAL, FaceMetrics.expression(mapOf("browInnerUp" to 0.6f, "mouthFrownLeft" to 0.3f, "mouthFrownRight" to 0.3f)))
        assertEquals(Expression.NATURAL, FaceMetrics.expression(mapOf("jawOpen" to 0.05f)))
        assertEquals(Expression.NATURAL, FaceMetrics.expression(emptyMap()))
    }

    // ---- Looking at camera ----

    @Test
    fun lookingAtCameraNeedsHeadAndEyes() {
        assertTrue(FaceMetrics.lookingAtCamera(5f, -5f, 0f, 0f, EyeState.OPEN))
        // Head turned, eyes straight ahead: looking past the camera.
        assertFalse(FaceMetrics.lookingAtCamera(30f, 0f, 0f, 0f, EyeState.OPEN))
        // Head turned, eyes turned back to the camera: the classic portrait (a real bride: 35, -0.93).
        assertTrue(FaceMetrics.lookingAtCamera(35f, -3f, -0.93f, 0.1f, EyeState.OPEN))
        // Head and eyes turned the same way: looking well away (a real bride: -13, -0.61).
        assertFalse(FaceMetrics.lookingAtCamera(-13f, 0f, -0.61f, 0f, EyeState.OPEN))
        // Head down, eyes up at the camera: a shy pose (a real bride: -21, +0.44).
        assertTrue(FaceMetrics.lookingAtCamera(18f, -21f, -0.5f, 0.44f, EyeState.OPEN))
        // Head down and eyes down.
        assertFalse(FaceMetrics.lookingAtCamera(0f, -25f, 0f, -0.2f, EyeState.OPEN))
        // Eyes cannot make up for a face turned side-on.
        assertFalse(FaceMetrics.lookingAtCamera(60f, 0f, -1f, 0f, EyeState.OPEN))
        assertFalse(FaceMetrics.lookingAtCamera(5f, 5f, 0f, 0f, EyeState.LOOKING_DOWN))
    }

    @Test
    fun eyeGazeUsesStrongestDirection() {
        val b = mapOf("eyeLookOutLeft" to 0.6f, "eyeLookInRight" to 0.6f)
        assertEquals(0.6f, FaceMetrics.eyeGazeOff(b), 1e-6f)
    }

    // ---- Head angle from the face transform ----

    private fun rotationY(deg: Double): FloatArray {
        val r = Math.toRadians(deg)
        // Column-major 4x4; third column is the face's forward axis.
        return floatArrayOf(
            cos(r).toFloat(), 0f, -sin(r).toFloat(), 0f,
            0f, 1f, 0f, 0f,
            sin(r).toFloat(), 0f, cos(r).toFloat(), 0f,
            0f, 0f, -30f, 1f,
        )
    }

    private fun rotationX(deg: Double): FloatArray {
        val r = Math.toRadians(deg)
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, cos(r).toFloat(), sin(r).toFloat(), 0f,
            0f, -sin(r).toFloat(), cos(r).toFloat(), 0f,
            0f, 0f, -30f, 1f,
        )
    }

    @Test
    fun headAnglesFromTransform() {
        val (yaw0, pitch0) = FaceMetrics.headAngles(rotationY(0.0))
        assertEquals(0f, yaw0, 0.01f); assertEquals(0f, pitch0, 0.01f)
        val (yaw, pitch) = FaceMetrics.headAngles(rotationY(30.0))
        assertEquals(30f, yaw, 0.1f); assertEquals(0f, pitch, 0.1f)
        val (yaw2, pitch2) = FaceMetrics.headAngles(rotationY(-30.0))
        assertEquals(30f, yaw2, 0.1f); assertEquals(0f, pitch2, 0.1f)
        val (yaw3, pitch3) = FaceMetrics.headAngles(rotationX(25.0))
        assertEquals(0f, yaw3, 0.1f); assertEquals(25f, pitch3, 0.1f)
    }

    @Test
    fun signedHeadDirectionWhereverTheNumbersAreStored() {
        val (turn, tilt) = FaceMetrics.headDirection(rotationY(30.0))
        assertEquals(30f, turn, 0.1f); assertEquals(0f, tilt, 0.1f)
        assertEquals(-30f, FaceMetrics.headDirection(rotationY(-30.0)).first, 0.1f)
        // The same transform stored by rows.
        val c = rotationY(30.0)
        val byRows = FloatArray(16) { i -> c[(i % 4) * 4 + i / 4] }
        assertEquals(30f, FaceMetrics.headDirection(byRows).first, 0.1f)
        // Tilted: the sign is kept (down is negative).
        assertEquals(25f, kotlin.math.abs(FaceMetrics.headDirection(rotationX(25.0)).second), 0.1f)
    }

    @Test
    fun eyeDirectionFromBlendshapes() {
        val (across, up) = FaceMetrics.eyeDirection(mapOf("eyeLookInLeft" to 0.93f, "eyeLookOutRight" to 0.93f, "eyeLookUpLeft" to 0.4f, "eyeLookUpRight" to 0.4f))
        assertEquals(-0.93f, across, 1e-6f)
        assertEquals(0.4f, up, 1e-6f)
    }

    @Test
    fun turnDirectionFromLandmarks() {
        assertEquals(0f, FaceMetrics.yawRatio(0.5f, 0.4f, 0.6f), 1e-6f)
        // Nose nearer the photo-right edge of the face: turned to photo right.
        assertTrue(FaceMetrics.yawRatio(0.57f, 0.4f, 0.6f) > 0.5f)
        assertTrue(FaceMetrics.yawRatio(0.43f, 0.4f, 0.6f) < -0.5f)
        assertTrue(FaceMetrics.directionX(0.5f, 0.3f) > 0.5f)
        assertEquals(1f, FaceMetrics.directionX(1f, 1f), 1e-6f)
    }

    @Test
    fun rollFromEyeLine() {
        assertEquals(0f, FaceMetrics.roll(0f, 0f, 10f, 0f), 1e-4f)
        assertEquals(45f, FaceMetrics.roll(0f, 0f, 10f, 10f), 1e-3f)
    }

    // ---- Whole reading from fake landmarks ----

    private fun landmarks(shiftX: Float = 0f, irisShift: Float = 0f, noseX: Float = 0.5f): Pair<FloatArray, FloatArray> {
        val xs = FloatArray(478) { 0.5f + shiftX }
        val ys = FloatArray(478) { 0.5f }
        fun set(i: Int, x: Float, y: Float) { xs[i] = x + shiftX; ys[i] = y }
        set(FaceMetrics.CONTOUR_IMAGE_LEFT, 0.4f, 0.5f)
        set(FaceMetrics.CONTOUR_IMAGE_RIGHT, 0.6f, 0.5f)
        set(10, 0.5f, 0.3f)
        set(152, 0.5f, 0.75f)
        set(FaceMetrics.NOSE_TIP, noseX, 0.55f)
        set(FaceMetrics.EYE_IMAGE_LEFT_OUTER, 0.44f, 0.45f)
        set(FaceMetrics.EYE_IMAGE_LEFT_INNER, 0.48f, 0.45f)
        set(FaceMetrics.EYE_IMAGE_RIGHT_INNER, 0.52f, 0.45f)
        set(FaceMetrics.EYE_IMAGE_RIGHT_OUTER, 0.56f, 0.45f)
        set(FaceMetrics.IRIS_IMAGE_LEFT, 0.46f + irisShift, 0.45f)
        set(FaceMetrics.IRIS_IMAGE_RIGHT, 0.54f + irisShift, 0.45f)
        set(FaceMetrics.MOUTH_IMAGE_LEFT, 0.46f, 0.65f)
        set(FaceMetrics.MOUTH_IMAGE_RIGHT, 0.54f, 0.65f)
        return xs to ys
    }

    @Test
    fun readFrontFacingSmilingFace() {
        val (xs, ys) = landmarks()
        val r = FaceMetrics.read(xs, ys, mapOf("mouthSmileLeft" to 0.8f, "mouthSmileRight" to 0.8f), rotationY(0.0), 1.5f)
        assertEquals(Box(0.4f, 0.3f, 0.6f, 0.75f), r.box)
        assertEquals(EyeState.OPEN, r.eyes)
        assertEquals(Expression.SMILE, r.expression)
        assertTrue(r.lookingAtCamera)
        assertEquals(0f, r.cutOff, 1e-6f)
        assertEquals(0f, r.roll, 1e-3f)
        assertEquals(0f, r.directionX, 1e-3f)
    }

    @Test
    fun readTurnedFaceIsNotLookingAndPointsTheRightWay() {
        val (xs, ys) = landmarks(irisShift = 0.01f, noseX = 0.57f)
        val r = FaceMetrics.read(xs, ys, emptyMap(), rotationY(35.0), 1.5f)
        assertFalse(r.lookingAtCamera)
        assertEquals(35f, r.yaw, 0.2f)
        assertTrue(r.directionX > 0.5f)
    }

    @Test
    fun turnedHeadWithEyesBackToTheCameraIsLooking() {
        val (xs, ys) = landmarks(irisShift = -0.01f, noseX = 0.57f)
        val r = FaceMetrics.read(xs, ys, mapOf("eyeLookInLeft" to 0.93f, "eyeLookOutRight" to 0.93f), rotationY(35.0), 1.5f)
        assertTrue(r.lookingAtCamera)
        assertEquals(35f, r.yaw, 0.2f)
    }

    @Test
    fun cutOffFaceAtEdge() {
        val (xs, ys) = landmarks(shiftX = 0.5f) // face box now 0.9..1.1
        val r = FaceMetrics.read(xs, ys, emptyMap(), null, 1.5f)
        assertEquals(0.5f, r.cutOff, 0.01f)
        assertEquals(0.5f, FaceMetrics.cutOffFraction(Box(-0.1f, 0f, 0.1f, 0.2f)), 1e-6f)
        assertEquals(0f, FaceMetrics.cutOffFraction(Box(0.1f, 0.1f, 0.2f, 0.2f)), 1e-6f)
    }

    @Test
    fun irisOffsetTellsEyeDirection() {
        val (xs, _) = landmarks(irisShift = 0.02f)
        assertEquals(1f, FaceMetrics.irisOffset(xs), 1e-3f)
        val (xs2, _) = landmarks()
        assertEquals(0f, FaceMetrics.irisOffset(xs2), 1e-3f)
    }

    // ---- Covered faces ----

    @Test
    fun occlusionFromSkinPatches() {
        val skin = floatArrayOf(200f, 150f, 120f)
        val clear = List(5) { skin.copyOf() }
        assertEquals(0f, FaceMetrics.occlusion(clear), 0.01f)
        // Side light: one cheek darker but the same colour.
        val shaded = clear.toMutableList().also { it[0] = floatArrayOf(120f, 90f, 72f) }
        assertTrue(FaceMetrics.occlusion(shaded) < 0.3f)
        // A black phone over the chin.
        val phone = clear.toMutableList().also { it[3] = floatArrayOf(30f, 35f, 60f) }
        assertTrue(FaceMetrics.occlusion(phone) > 0.6f)
        assertEquals(0f, FaceMetrics.occlusion(clear.take(2)), 0f)
    }

    // ---- Face-matching alignment ----

    @Test
    fun similarityTransformRecoversKnownMove() {
        val src = floatArrayOf(10f, 10f, 30f, 12f, 20f, 25f, 12f, 35f, 28f, 36f)
        val a = 1.2f * cos(0.3f); val b = 1.2f * sin(0.3f)
        val dst = FloatArray(src.size)
        for (i in 0 until 5) {
            val x = src[2 * i]; val y = src[2 * i + 1]
            dst[2 * i] = a * x - b * y + 5f
            dst[2 * i + 1] = b * x + a * y - 7f
        }
        val t = FaceMetrics.similarity(src, dst)
        assertEquals(a, t[0], 1e-4f); assertEquals(b, t[1], 1e-4f)
        assertEquals(5f, t[2], 1e-3f); assertEquals(-7f, t[3], 1e-3f)
    }

    @Test
    fun alignmentPointsAreEyesNoseMouthInPixels() {
        val (xs, ys) = landmarks()
        val p = FaceMetrics.alignmentPoints(xs, ys, 1000, 800)
        assertEquals(460f, p[0], 0.01f) // eye on the photo's left first, as SFace expects
        assertEquals(540f, p[2], 0.01f)
        assertEquals(500f, p[4], 0.01f)
        assertEquals(440f, p[5], 0.01f)
        assertEquals(460f, p[6], 0.01f)
        assertEquals(540f, p[8], 0.01f)
        // Mapping to the template keeps left on the left.
        val t = FaceMetrics.similarity(p, FaceMetrics.SFACE_TEMPLATE)
        assertTrue(t[0] > 0f)
    }

    @Test
    fun normalizeMakesUnitLength() {
        val v = FaceMetrics.normalize(floatArrayOf(3f, 4f))
        assertEquals(0.6f, v[0], 1e-6f); assertEquals(0.8f, v[1], 1e-6f)
    }

    @Test
    fun faceCutByThePhotoEdgeIsSeenFromItsShape() {
        // A tall photo (1046 x 2880): half a face against the left edge, from the replica dump.
        val aspect = 1046f / 2880f
        val half = Box(0.01f, 0.52f, 0.22f, 0.70f)
        assertTrue(FaceMetrics.edgeCutOff(half, aspect) >= 0.4f)
        // A whole face of normal shape against the edge is not cut.
        val whole = Box(0.0f, 0.52f, 0.45f, 0.70f)
        assertEquals(0f, FaceMetrics.edgeCutOff(whole, aspect), 1e-6f)
        // A narrow-looking face away from the edges is just turned; that is not this check's job.
        assertEquals(0f, FaceMetrics.edgeCutOff(Box(0.4f, 0.52f, 0.61f, 0.70f), aspect), 1e-6f)
        // The chin cut off by the bottom edge: a flat box at the bottom.
        assertTrue(FaceMetrics.edgeCutOff(Box(0.4f, 0.9f, 0.6f, 1.0f), 1.5f) > 0.3f)
    }
}
