package com.albumdone.app.analysis

import com.albumdone.app.plan.Box
import com.albumdone.app.plan.EyeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class YuNetTest {

    /** Empty model outputs for a [pw] x [ph] padded input. */
    private fun empty(pw: Int, ph: Int): MutableMap<String, FloatArray> {
        val m = HashMap<String, FloatArray>()
        for (s in YuNet.STRIDES) {
            val n = (pw / s) * (ph / s)
            m["cls_$s"] = FloatArray(n); m["obj_$s"] = FloatArray(n)
            m["bbox_$s"] = FloatArray(4 * n); m["kps_$s"] = FloatArray(10 * n)
        }
        return m
    }

    private fun put(m: MutableMap<String, FloatArray>, s: Int, cols: Int, r: Int, c: Int, score: Float, dx: Float, dy: Float, w: Float, h: Float) {
        val i = r * cols + c
        m.getValue("cls_$s")[i] = score; m.getValue("obj_$s")[i] = score
        val b = m.getValue("bbox_$s")
        b[4 * i] = dx; b[4 * i + 1] = dy; b[4 * i + 2] = ln(w / s); b[4 * i + 3] = ln(h / s)
        val k = m.getValue("kps_$s")
        // Eyes, nose, mouth corners relative to the cell, in cells.
        floatArrayOf(-0.3f, -0.3f, 0.3f, -0.3f, 0f, 0f, -0.2f, 0.3f, 0.2f, 0.3f).forEachIndexed { j, v -> k[10 * i + j] = v + 0.5f }
    }

    @Test
    fun decodesBoxesAndPointsLikeOpenCv() {
        // 64 x 64 input, image 64 x 48 before padding. One face in the stride-16 grid at row 1, col 2.
        val m = empty(64, 64)
        put(m, 16, 4, 1, 2, score = 0.9f, dx = 0.5f, dy = 0.5f, w = 20f, h = 24f)
        val faces = YuNet.decode(YuNet.Outputs(m, 64, 64), 64, 48)
        assertEquals(1, faces.size)
        val f = faces[0]
        // Centre (2.5 * 16, 1.5 * 16) = (40, 24); size 20 x 24.
        assertEquals(30f / 64, f.box.left, 1e-4f)
        assertEquals(12f / 48, f.box.top, 1e-4f)
        assertEquals(50f / 64, f.box.right, 1e-4f)
        assertEquals(36f / 48, f.box.bottom, 1e-4f)
        assertEquals(0.9f, f.score, 1e-4f)
        // First eye at ((0.2 + 2) * 16, (0.2 + 1) * 16) = (35.2, 19.2).
        assertEquals(35.2f / 64, f.points[0], 1e-4f)
        assertEquals(19.2f / 48, f.points[1], 1e-4f)
        assertTrue("image-left eye first", f.points[0] < f.points[2])
    }

    @Test
    fun weakCellsAreIgnoredAndOverlapsMerged() {
        val m = empty(64, 64)
        put(m, 8, 8, 2, 2, score = 0.95f, dx = 0.5f, dy = 0.5f, w = 16f, h = 16f)
        put(m, 8, 8, 2, 3, score = 0.8f, dx = 0f, dy = 0.5f, w = 16f, h = 16f)   // same face, shifted 4 px
        put(m, 8, 8, 6, 6, score = 0.4f, dx = 0.5f, dy = 0.5f, w = 16f, h = 16f)  // too weak
        val faces = YuNet.decode(YuNet.Outputs(m, 64, 64), 64, 64)
        assertEquals(1, faces.size)
        assertEquals(0.95f, faces[0].score, 1e-4f)
    }

    @Test
    fun separateFacesStaySeparate() {
        val a = YuNet.Found(Box(0.1f, 0.1f, 0.2f, 0.2f), FloatArray(10), 0.9f)
        val b = YuNet.Found(Box(0.25f, 0.1f, 0.35f, 0.2f), FloatArray(10), 0.8f)
        assertEquals(2, YuNet.suppress(listOf(a, b)).size)
    }

    @Test
    fun sideOnFaceIsCountedButItsEyesAreNotJudged() {
        // Nose far to the image right of the eyes: a face turned to the side.
        val points = floatArrayOf(0.40f, 0.30f, 0.44f, 0.30f, 0.50f, 0.34f, 0.41f, 0.38f, 0.45f, 0.38f)
        val r = FaceMetrics.readFromPoints(Box(0.36f, 0.22f, 0.52f, 0.44f), points, 1.5f)
        assertEquals(EyeState.UNKNOWN, r.eyes)
        assertFalse(r.lookingAtCamera)
        assertTrue("turned: ${r.yaw}", r.yaw > 45f)
        assertTrue("faces image right", r.directionX > 0.5f)
        // Same width; height trimmed to the face mesh's proportions.
        assertEquals(0.16f, r.box.width, 1e-4f)
        assertEquals(0.22f * FaceMetrics.YUNET_HEIGHT_TO_MESH, r.box.height, 1e-4f)

        val frontal = floatArrayOf(0.40f, 0.30f, 0.48f, 0.30f, 0.44f, 0.34f, 0.41f, 0.38f, 0.47f, 0.38f)
        assertTrue(FaceMetrics.readFromPoints(Box(0.36f, 0.22f, 0.52f, 0.44f), frontal, 1.5f).yaw < 5f)
    }
}
