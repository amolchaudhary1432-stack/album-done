package com.albumdone.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
