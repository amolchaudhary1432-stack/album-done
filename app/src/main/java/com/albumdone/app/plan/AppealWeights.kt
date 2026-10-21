package com.albumdone.app.plan

/**
 * Made by tools/harvest/learn.py from 1271 photos of 20 albums picked by eye (259 picks): feature
 * name to (mean, scale, weight); "bias" holds the bias. Do not edit by hand.
 */
object AppealWeights {
    val WEIGHTS: Map<String, DoubleArray> = mapOf(
        "bias" to doubleArrayOf(0.0, 1.0, -0.15965),
        "quality" to doubleArrayOf(0.51193, 0.23497, -0.03619),
        "usable" to doubleArrayOf(0.78757, 0.40903, -0.06052),
        "faces" to doubleArrayOf(1.40205, 1.67397, 0.0981),
        "people" to doubleArrayOf(1.49331, 1.84397, -0.12206),
        "mains" to doubleArrayOf(0.40441, 0.62099, -0.03371),
        "maxFace" to doubleArrayOf(0.10331, 0.10486, -0.0952),
        "smile" to doubleArrayOf(0.07466, 0.17821, 0.25655),
        "looking" to doubleArrayOf(0.16995, 0.31667, 0.16205),
        "eyesOpenAll" to doubleArrayOf(0.02124, 0.14419, -0.01113),
        "interaction" to doubleArrayOf(0.08655, 0.28117, 0.06624),
        "emotional" to doubleArrayOf(0.00629, 0.07909, -0.13186),
        "coupleLooking" to doubleArrayOf(0.00629, 0.07909, 0.02719),
        "peakRel" to doubleArrayOf(1.17927, 0.80327, -0.00699),
        "brightRel" to doubleArrayOf(0.95621, 0.29332, 0.11408),
        "dark" to doubleArrayOf(0.08144, 0.2013, -0.18293),
        "blown" to doubleArrayOf(0.01915, 0.03814, -0.02682),
        "portrait" to doubleArrayOf(0.43824, 0.49617, -0.00829),
        "peopleArea" to doubleArrayOf(0.46139, 0.3216, 0.28763),
        "i_BLURRY" to doubleArrayOf(0.05429, 0.22659, -0.14968),
        "i_TOO_DARK" to doubleArrayOf(0.02124, 0.14419, -0.11115),
        "i_TOO_BRIGHT" to doubleArrayOf(0.00236, 0.04853, 0.01844),
        "i_EYES_CLOSED" to doubleArrayOf(0.01731, 0.13042, 0.16428),
        "i_DUPLICATE" to doubleArrayOf(0.00157, 0.03964, -0.21889),
        "i_GROUP_REPEAT" to doubleArrayOf(0.08104, 0.27289, -0.30123),
        "i_FACE_CUT_OFF" to doubleArrayOf(0.00629, 0.07909, -0.03121),
        "i_FACE_BLOCKED" to doubleArrayOf(0.00708, 0.08385, 0.10206),
        "i_BACK_OF_GUEST" to doubleArrayOf(0.05271, 0.22346, -0.2314),
        "i_HALF_BLINK" to doubleArrayOf(0.0236, 0.15181, -0.01403),
        "i_MID_TALK" to doubleArrayOf(0.02911, 0.16812, 0.10638),
        "i_NOT_LOOKING" to doubleArrayOf(0.06452, 0.24567, 0.00871),
        "i_FACE_DARK" to doubleArrayOf(0.01495, 0.12135, 0.10123),
        "i_FACE_PARTLY_HIDDEN" to doubleArrayOf(0.08104, 0.27289, 0.0345),
        "i_PHONE_IN_SHOT" to doubleArrayOf(0.00629, 0.07909, -0.45034),
        "i_STRANGER" to doubleArrayOf(0.08183, 0.2741, -0.00058),
        "i_FACES_TURNED_AWAY" to doubleArrayOf(0.0834, 0.27648, 0.05379),
        "s_DETAIL" to doubleArrayOf(0.18725, 0.39012, 0.04526),
        "s_COUPLE_CLOSE" to doubleArrayOf(0.01101, 0.10437, 0.06688),
        "s_COUPLE_FULL" to doubleArrayOf(0.05114, 0.22028, 0.1133),
        "s_BRIDE_SOLO" to doubleArrayOf(0.11251, 0.31599, -0.03476),
        "s_GROOM_SOLO" to doubleArrayOf(0.03147, 0.17459, -0.05194),
        "s_GROUP" to doubleArrayOf(0.01652, 0.12747, 0.00352),
        "s_CANDID" to doubleArrayOf(0.53816, 0.49854, -0.05996),
        "s_GUEST" to doubleArrayOf(0.05193, 0.22188, -0.00042),
    )
}
