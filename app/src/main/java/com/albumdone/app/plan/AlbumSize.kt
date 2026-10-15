package com.albumdone.app.plan

import kotlin.math.abs
import kotlin.math.roundToInt

/** How many photos one album works from, and what album sizes to offer. Pure Kotlin, unit-tested. */
object AlbumSize {

    /** One folder, up to 1,000 photos: about one big function, or the photographer's best of a wedding. */
    const val MAX_PHOTOS = 1000

    /** About 15 of every 100 photos make a good first album; the couple can change it. */
    const val SHARE = 0.15

    fun suggested(photos: Int): Int = if (photos <= 0) 0 else roundTen(photos * SHARE).coerceAtLeast(10).coerceAtMost(photos)

    /** A few sizes around the suggestion, smallest first. */
    fun choices(photos: Int): List<Int> {
        if (photos <= 0) return emptyList()
        val s = suggested(photos)
        val shares = listOf(0.1, 0.15, 0.2, 0.25).map { roundTen(photos * it) }
        val round = listOf(50, 80, 100, 120, 150, 200, 250).filter { it >= photos * 0.08 && it <= photos * 0.3 }
        return (shares + round + s)
            .map { it.coerceAtLeast(10).coerceAtMost(photos) }
            .distinct()
            .sortedBy { abs(it - s) }
            .take(4)
            .sorted()
    }

    private fun roundTen(x: Double) = (x / 10.0).roundToInt() * 10
}
