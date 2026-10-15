package com.albumdone.app.analysis

import org.junit.Assert.assertEquals
import com.albumdone.app.plan.Box
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMetricsTest {

    private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun lumaOfBlackWhiteAndGrey() {
        val l = ImageMetrics.luma(intArrayOf(argb(0, 0, 0), argb(255, 255, 255), argb(100, 100, 100)))
        assertEquals(0f, l[0], 0.01f)
        assertEquals(255f, l[1], 0.01f)
        assertEquals(100f, l[2], 0.01f)
    }

    @Test
    fun flatImageHasNoSharpness() {
        val q = ImageMetrics.quality(FloatArray(100) { 128f }, 10, 10)
        assertEquals(0.0, q.sharpness, 1e-9)
        assertEquals(128.0, q.brightness, 1e-6)
        assertEquals(0.0, q.darkFraction, 1e-9)
        assertEquals(0.0, q.brightFraction, 1e-9)
    }

    @Test
    fun checkerboardIsSharperThanGradient() {
        val w = 32
        val checker = FloatArray(w * w) { i -> if ((i % w + i / w) % 2 == 0) 0f else 255f }
        val gradient = FloatArray(w * w) { i -> (i % w) * 8f }
        val sharp = ImageMetrics.quality(checker, w, w).sharpness
        val soft = ImageMetrics.quality(gradient, w, w).sharpness
        assertTrue(sharp > soft * 100)
    }

    @Test
    fun darkAndBlownShares() {
        val gray = FloatArray(10) { i -> if (i < 3) 5f else if (i < 5) 250f else 128f }
        val q = ImageMetrics.quality(gray, 10, 1)
        assertEquals(0.3, q.darkFraction, 1e-9)
        assertEquals(0.2, q.brightFraction, 1e-9)
        assertEquals(0.0, q.sharpness, 1e-9) // too small for the Laplacian
    }

    @Test(expected = IllegalArgumentException::class)
    fun qualityRejectsWrongSize() {
        ImageMetrics.quality(FloatArray(5), 2, 2)
    }

    @Test
    fun dHashIsStableAndDistinguishes() {
        val ramp = FloatArray(72) { i -> (i % 9) * 10f }
        val ramp2 = FloatArray(72) { i -> (i % 9) * 10f + 3f }
        val reversed = FloatArray(72) { i -> (8 - i % 9) * 10f }
        val h1 = ImageMetrics.dHash(ramp)
        assertEquals(0L, h1) // left is never brighter than right
        assertEquals(h1, ImageMetrics.dHash(ramp2))
        val h3 = ImageMetrics.dHash(reversed)
        assertEquals(-1L, h3) // every bit set
        assertEquals(64, ImageMetrics.hammingDistance(h1, h3))
        assertNotEquals(h1, h3)
    }

    @Test
    fun hammingDistance() {
        assertEquals(0, ImageMetrics.hammingDistance(5L, 5L))
        assertEquals(2, ImageMetrics.hammingDistance(0b1010L, 0b0110L))
    }

    /** Stripes at [deg] degrees: a photo of straight lines, tilted. */
    private fun stripes(w: Int, h: Int, deg: Double): FloatArray {
        val r = Math.toRadians(deg)
        return FloatArray(w * h) { i ->
            val x = i % w; val y = i / w
            val u = x * Math.cos(r) + y * Math.sin(r)
            (125.0 + 95.0 * Math.sin(2 * Math.PI * u / 20.0)).toFloat()
        }
    }

    @Test
    fun tiltOfLevelAndCrookedPhotos() {
        val level = ImageMetrics.tiltDegrees(stripes(200, 150, 0.0), 200, 150)!!
        assertEquals(0f, level, 0.8f)
        val crooked = ImageMetrics.tiltDegrees(stripes(200, 150, 6.0), 200, 150)!!
        assertEquals(6f, crooked, 1.0f)
        val otherWay = ImageMetrics.tiltDegrees(stripes(200, 150, -6.0), 200, 150)!!
        assertEquals(6f, otherWay, 1.0f)
        // Horizontal lines tilted the same amount give the same answer.
        val horizontal = ImageMetrics.tiltDegrees(stripes(200, 150, 96.0), 200, 150)!!
        assertEquals(6f, horizontal, 1.0f)
    }

    @Test
    fun noTiltWithoutLines() {
        assertNull(ImageMetrics.tiltDegrees(FloatArray(100 * 100) { 128f }, 100, 100))
        val noise = java.util.Random(4)
        assertNull(ImageMetrics.tiltDegrees(FloatArray(120 * 120) { noise.nextFloat() * 255f }, 120, 120))
    }

    @Test
    fun peakSharpnessFindsTheSharpSubject() {
        // Soft background with one sharp patch (the subject in focus).
        val w = 64
        val img = FloatArray(w * w) { i ->
            val x = i % w; val y = i / w
            if (x in 16 until 32 && y in 16 until 32) (if ((x + y) % 2 == 0) 0f else 255f) else (x * 2f)
        }
        val whole = ImageMetrics.quality(img, w, w).sharpness
        val peak = ImageMetrics.peakSharpness(img, w, w)
        assertTrue(peak > whole * 5)
        // An evenly soft photo has no sharp part.
        val soft = FloatArray(w * w) { i -> (i % w) * 2f }
        assertEquals(0.0, ImageMetrics.peakSharpness(soft, w, w), 1e-6)
    }

    @Test
    fun regionMeanAndBacklight() {
        val w = 10
        val img = FloatArray(w * w) { i -> if (i % w < 5) 50f else 200f }
        assertEquals(50.0, ImageMetrics.regionMean(img, w, w, Box(0f, 0f, 0.5f, 1f)), 1e-6)
        assertEquals(200.0, ImageMetrics.regionMean(img, w, w, Box(0f, 0f, 1f, 1f), exclude = Box(0f, 0f, 0.5f, 1f)), 1e-6)
        assertTrue(ImageMetrics.isBacklit(60.0, 200.0))
        assertFalse(ImageMetrics.isBacklit(150.0, 200.0))
        assertFalse(ImageMetrics.isBacklit(40.0, 60.0)) // dark everywhere: that is lighting, not backlight
        // Found on the phone test: a correctly lit face (darker skin) in front of a bright window.
        assertFalse(ImageMetrics.isBacklit(94.0, 190.0))
    }
}
