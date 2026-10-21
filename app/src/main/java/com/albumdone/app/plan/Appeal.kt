package com.albumdone.app.plan

import kotlin.math.exp

/**
 * How likely a person is to put a photo in the album, 0..1, learned from albums picked by eye
 * (tools/harvest/labels): a weighted sum of what the checks measured about the photo, its faces
 * and its moment. The weights live in [AppealWeights], made by tools/harvest/learn.py; change
 * them there, never by hand.
 */
object Appeal {

    /** Weights to use instead of [AppealWeights] (tests that leave one set out of the learning). */
    @Volatile var override: Map<String, DoubleArray>? = null

    /** What the learning sees of one photo, by name. Same names as in [AppealWeights]. */
    fun features(p: AssessedPhoto, a: AlbumAnalysis, medPeak: Double, medBright: Double): Map<String, Double> {
        val clear = p.photo.faces.filter { it.box.width >= AlbumPlanner.CLEAR_FACE }
        val mains = setOfNotNull(a.tags.brideId, a.tags.groomId)
        val f = linkedMapOf(
            "quality" to p.quality, "usable" to b(p.usable),
            "faces" to clear.size.toDouble(), "people" to p.people.size.toDouble(),
            "mains" to p.people.count { it in mains }.toDouble(),
            "maxFace" to (p.photo.faces.maxOfOrNull { it.box.width } ?: 0f).toDouble(),
            "smile" to (clear.map { it.smile.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0),
            "looking" to (if (clear.isEmpty()) 0.0 else clear.count { it.lookingAtCamera }.toDouble() / clear.size),
            "eyesOpenAll" to b(p.allEyesOpenAndLooking), "interaction" to b(p.interaction), "emotional" to b(p.emotional),
            "coupleLooking" to b(p.coupleLookingAtEachOther),
            "peakRel" to (if (medPeak > 0) p.photo.peakSharpness / medPeak else 1.0),
            "brightRel" to (if (medBright > 0) p.photo.brightness / medBright else 1.0),
            "dark" to p.photo.darkFraction, "blown" to p.photo.brightFraction,
            "portrait" to b(p.photo.portrait), "peopleArea" to p.photo.peopleArea.toDouble(),
        )
        for (i in Issue.entries) f["i_" + i.name] = b(i in p.issues)
        for (t in ShotType.entries) f["s_" + t.name] = b(p.shot == t)
        return f
    }

    /** Every photo's appeal, 0..1. */
    fun score(a: AlbumAnalysis): Map<Long, Double> {
        val weights = override ?: AppealWeights.WEIGHTS
        val bias = weights["bias"]?.get(2) ?: 0.0
        val medPeak = AlbumPlanner.median(a.photos.map { it.photo.peakSharpness })
        val medBright = AlbumPlanner.median(a.photos.map { it.photo.brightness })
        return a.photos.associate { p ->
            var z = bias
            for ((name, v) in features(p, a, medPeak, medBright)) {
                val w = weights[name] ?: continue
                z += (v - w[0]) / w[1] * w[2]
            }
            p.id to 1.0 / (1.0 + exp(-z))
        }
    }

    private fun b(x: Boolean) = if (x) 1.0 else 0.0
}
