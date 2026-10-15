package com.albumdone.app.analysis

import com.albumdone.app.plan.Box
import kotlin.math.max
import kotlin.math.min

/**
 * Rules that combine the object detector's people boxes with the faces found.
 * Pure Kotlin, unit-tested. All boxes are in 0..1 photo coordinates.
 */
object SceneRules {

    /**
     * Guests with their back to the camera, blocking the view: a big person box rising from the
     * bottom edge with no face in its head area.
     */
    fun foregroundBacks(people: List<Box>, faces: List<Box>): Int = people.count { p ->
        p.bottom >= 0.95f && p.height >= 0.3f && p.area >= 0.08f &&
            faces.none { f -> f.centerX in p.left..p.right && f.centerY in p.top..(p.top + p.height * 0.5f) }
    }

    /** Person boxes whose head area has no face yet: faces too small for the first look. */
    fun peopleWithoutFaces(people: List<Box>, faces: List<Box>): List<Box> = people.filter { p ->
        faces.none { f -> f.centerX in p.left..p.right && f.centerY in p.top..(p.top + p.height * 0.6f) }
    }

    /**
     * Areas to look at more closely for small faces: the head area of each person without a
     * face, joined with its neighbours, at most [maxCrops] areas, each at least [minSize] wide.
     */
    fun headCrops(people: List<Box>, maxCrops: Int = 3, minSize: Float = 0.2f): List<Box> {
        if (people.isEmpty()) return emptyList()
        val heads = people.map { p ->
            val h = max(p.height * 0.35f, p.width * 0.8f)
            Box(p.left - p.width * 0.15f, p.top - h * 0.1f, p.right + p.width * 0.15f, p.top + h)
        }.sortedBy { it.left }
        val merged = ArrayList<Box>()
        for (h in heads) {
            val last = merged.lastOrNull()
            if (last != null && h.left <= last.right + 0.02f) merged[merged.size - 1] = union(last, h) else merged += h
        }
        // Too many areas: join the closest neighbours until few enough.
        while (merged.size > maxCrops) {
            var best = 0; var gap = Float.MAX_VALUE
            for (i in 0 until merged.size - 1) {
                val g = merged[i + 1].left - merged[i].right
                if (g < gap) { gap = g; best = i }
            }
            merged[best] = union(merged[best], merged[best + 1])
            merged.removeAt(best + 1)
        }
        return merged.map { grow(it, minSize) }
    }

    /** True when [face] is a face already found (its centre lies inside one of [known]). */
    fun isDuplicate(face: Box, known: List<Box>): Boolean =
        known.any { k -> face.centerX in k.left..k.right && face.centerY in k.top..k.bottom }

    private fun union(a: Box, b: Box) = Box(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))

    /** Widens a box to at least [minSize] on each side and keeps it inside the photo. */
    private fun grow(b: Box, minSize: Float): Box {
        var l = b.left; var r = b.right; var t = b.top; var bt = b.bottom
        if (r - l < minSize) { val c = (l + r) / 2; l = c - minSize / 2; r = c + minSize / 2 }
        if (bt - t < minSize) { val c = (t + bt) / 2; t = c - minSize / 2; bt = c + minSize / 2 }
        // Slide back inside the photo rather than shrinking.
        if (l < 0f) { r -= l; l = 0f }
        if (r > 1f) { l -= r - 1f; r = 1f }
        if (t < 0f) { bt -= t; t = 0f }
        if (bt > 1f) { t -= bt - 1f; bt = 1f }
        return Box(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), bt.coerceIn(0f, 1f))
    }
}
