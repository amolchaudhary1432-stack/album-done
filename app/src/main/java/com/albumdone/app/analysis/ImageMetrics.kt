package com.albumdone.app.analysis

import com.albumdone.app.plan.Box
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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

    /**
     * Sharpness of the sharpest part of the photo: the photo is cut into a grid and the
     * sharpest cell is used, so a sharp subject against a deliberately soft background counts.
     */
    fun peakSharpness(gray: FloatArray, width: Int, height: Int, grid: Int = 4): Double {
        require(gray.size == width * height) { "pixel count does not match size" }
        if (width < grid * 3 || height < grid * 3) return quality(gray, width, height).sharpness
        val cells = ArrayList<Double>(grid * grid)
        for (gy in 0 until grid) for (gx in 0 until grid) {
            val x0 = gx * width / grid; val x1 = (gx + 1) * width / grid
            val y0 = gy * height / grid; val y1 = (gy + 1) * height / grid
            var sum = 0.0; var sq = 0.0; var n = 0
            for (y in max(1, y0) until min(height - 1, y1)) {
                val row = y * width
                for (x in max(1, x0) until min(width - 1, x1)) {
                    val i = row + x
                    val lap = (4f * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - width] - gray[i + width]).toDouble()
                    sum += lap; sq += lap * lap; n++
                }
            }
            if (n > 0) { val m = sum / n; cells += sq / n - m * m }
        }
        return cells.maxOrNull() ?: 0.0
    }

    /**
     * How crooked the photo is, in degrees (sign does not matter), or null when there are not
     * enough straight lines to tell. Strong edges vote for their angle folded into -45..45;
     * a level photo has its walls, pillars and horizon at 0.
     */
    fun tiltDegrees(gray: FloatArray, width: Int, height: Int): Float? {
        require(gray.size == width * height) { "pixel count does not match size" }
        if (width < 8 || height < 8) return null
        val bins = 180 // half-degree bins over -45..45
        val hist = DoubleArray(bins)
        var total = 0.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val gx = (gray[i - width + 1] + 2 * gray[i + 1] + gray[i + width + 1]) -
                    (gray[i - width - 1] + 2 * gray[i - 1] + gray[i + width - 1])
                val gy = (gray[i + width - 1] + 2 * gray[i + width] + gray[i + width + 1]) -
                    (gray[i - width - 1] + 2 * gray[i - width] + gray[i - width + 1])
                val mag = sqrt(gx * gx + gy * gy)
                if (mag < 80f) continue
                var a = Math.toDegrees(atan2(gy.toDouble(), gx.toDouble()))
                a = ((a % 90.0) + 90.0) % 90.0
                if (a >= 45.0) a -= 90.0
                val bin = ((a + 45.0) * 2.0).toInt().coerceIn(0, bins - 1)
                hist[bin] += mag.toDouble()
                total += mag
            }
        }
        if (total <= 0.0) return null
        var best = 0; var bestWeight = -1.0
        for (b in 0 until bins) {
            var w = 0.0
            for (k in -2..2) w += hist[(b + k + bins) % bins]
            if (w > bestWeight) { bestWeight = w; best = b }
        }
        // Peak must hold a clear share of all edges, or the photo has no dominant lines.
        if (bestWeight / total < 0.12) return null
        // Refine: weighted average angle around the strongest single bin in that window.
        var peak = best
        for (k in -2..2) if (hist[(best + k + bins) % bins] > hist[peak]) peak = (best + k + bins) % bins
        var sum = 0.0; var weight = 0.0
        for (k in -2..2) {
            val w = hist[(peak + k + bins) % bins]
            sum += w * ((peak + k) / 2.0 - 45.0 + 0.25)
            weight += w
        }
        return abs(sum / weight).toFloat()
    }

    /** Average of the brightness values inside [box] (0..1 coordinates), leaving out [exclude]. */
    fun regionMean(gray: FloatArray, width: Int, height: Int, box: Box, exclude: Box? = null): Double {
        val x0 = (box.left * width).toInt().coerceIn(0, width)
        val x1 = (box.right * width).toInt().coerceIn(0, width)
        val y0 = (box.top * height).toInt().coerceIn(0, height)
        val y1 = (box.bottom * height).toInt().coerceIn(0, height)
        var sum = 0.0; var n = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            if (exclude != null) {
                val fx = (x + 0.5f) / width; val fy = (y + 0.5f) / height
                if (fx >= exclude.left && fx <= exclude.right && fy >= exclude.top && fy <= exclude.bottom) continue
            }
            sum += gray[y * width + x]; n++
        }
        return if (n == 0) 0.0 else sum / n
    }

    /** Face clearly darker than the light behind or around it. */
    fun isBacklit(faceBrightness: Double, surroundBrightness: Double): Boolean =
        surroundBrightness > 110.0 && faceBrightness < 0.6 * surroundBrightness
}
