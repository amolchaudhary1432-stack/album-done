package com.albumdone.app.analysis

/**
 * Pure maths on pixels, kept free of Android classes so it can be unit-tested on any machine.
 */
object ImageMetrics {

    data class Quality(
        /** Variance of the Laplacian: higher means more fine detail, low means blurry. */
        val sharpness: Double,
        /** Average brightness, 0 (black) to 255 (white). */
        val brightness: Double,
        /** Share of almost-black pixels. */
        val darkFraction: Double,
        /** Share of almost-white (blown-out) pixels. */
        val brightFraction: Double,
    )

    /** Converts packed ARGB pixels to brightness values (0..255). */
    fun luma(argb: IntArray): FloatArray = FloatArray(argb.size) { i ->
        val p = argb[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        0.299f * r + 0.587f * g + 0.114f * b
    }

    fun quality(gray: FloatArray, width: Int, height: Int): Quality {
        require(gray.size == width * height) { "pixel count does not match size" }
        var sum = 0.0
        var dark = 0
        var bright = 0
        for (v in gray) {
            sum += v
            if (v < 16f) dark++
            if (v > 245f) bright++
        }
        val n = gray.size.coerceAtLeast(1)

        var lapSum = 0.0
        var lapSq = 0.0
        var count = 0
        if (width >= 3 && height >= 3) {
            for (y in 1 until height - 1) {
                val row = y * width
                for (x in 1 until width - 1) {
                    val i = row + x
                    val lap = 4f * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - width] - gray[i + width]
                    lapSum += lap
                    lapSq += lap.toDouble() * lap
                    count++
                }
            }
        }
        val sharpness = if (count == 0) 0.0 else {
            val mean = lapSum / count
            lapSq / count - mean * mean
        }
        return Quality(
            sharpness = sharpness,
            brightness = sum / n,
            darkFraction = dark.toDouble() / n,
            brightFraction = bright.toDouble() / n,
        )
    }

    /**
     * Difference hash from a 9x8 brightness grid: 64 bits that stay nearly the same for
     * near-identical photos (burst shots) and differ for different scenes.
     */
    fun dHash(gray9x8: FloatArray): Long {
        require(gray9x8.size == 72) { "dHash needs a 9x8 grid" }
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = gray9x8[y * 9 + x]
                val right = gray9x8[y * 9 + x + 1]
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
