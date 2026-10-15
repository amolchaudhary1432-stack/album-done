package com.albumdone.app.analysis

import com.albumdone.app.plan.Box
import com.albumdone.app.plan.EyeState
import com.albumdone.app.plan.Expression
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Face readings from MediaPipe Face Landmarker output (478 landmarks, 52 blendshapes,
 * 4x4 face transform). Pure Kotlin, no Android classes, so every rule is unit-tested.
 */
object FaceMetrics {

    // Face mesh landmark numbers. "Image left" = left side of the photo.
    const val NOSE_TIP = 1
    const val CONTOUR_IMAGE_LEFT = 234
    const val CONTOUR_IMAGE_RIGHT = 454
    const val EYE_IMAGE_LEFT_OUTER = 33
    const val EYE_IMAGE_LEFT_INNER = 133
    const val EYE_IMAGE_RIGHT_INNER = 362
    const val EYE_IMAGE_RIGHT_OUTER = 263
    const val IRIS_IMAGE_LEFT = 468
    const val IRIS_IMAGE_RIGHT = 473
    const val MOUTH_IMAGE_LEFT = 61
    const val MOUTH_IMAGE_RIGHT = 291
    const val CHEEK_IMAGE_LEFT = 50
    const val CHEEK_IMAGE_RIGHT = 280
    const val FOREHEAD = 151
    const val CHIN = 199
    const val NOSE_BRIDGE = 6

    /** Landmarks used to check whether part of the face is covered. */
    val SKIN_PATCHES = intArrayOf(CHEEK_IMAGE_LEFT, CHEEK_IMAGE_RIGHT, FOREHEAD, CHIN, NOSE_BRIDGE)

    // Thresholds, tuned on MediaPipe's blendshape scale (0..1).
    const val CLOSED_EYE = 0.55f
    // Open eyes read about 0.2-0.33 on real photos (checked on test portraits), so a half-blink
    // starts clearly above that.
    const val HALF_BLINK = 0.45f
    const val LOOK_DOWN = 0.45f
    const val SMILE = 0.35f
    const val LAUGH_JAW = 0.30f
    const val TALK_JAW_MIN = 0.12f
    const val TALK_JAW_MAX = 0.60f
    const val MAX_YAW_FOR_CAMERA = 22f
    const val MAX_PITCH_FOR_CAMERA = 20f
    const val MAX_EYE_GAZE_FOR_CAMERA = 0.5f

    /** Everything we read from one face, in photo coordinates. */
    data class Reading(
        val box: Box,
        val eyes: EyeState,
        val expression: Expression,
        val smile: Float,
        val jawOpen: Float,
        val yaw: Float,
        val pitch: Float,
        val roll: Float,
        val directionX: Float,
        val lookingAtCamera: Boolean,
        val cutOff: Float,
    )

    /**
     * @param xs, ys landmark positions, normalised to the photo (0..1, may be outside when cut off)
     * @param blend blendshape name to score
     * @param matrix 16-value face transform, or null
     * @param aspect photo width / height, so angles are measured in real proportions
     */
    fun read(xs: FloatArray, ys: FloatArray, blend: Map<String, Float>, matrix: FloatArray?, aspect: Float): Reading {
        val box = box(xs, ys)
        val eyes = eyeState(blend)
        val (yawFromMatrix, pitchFromMatrix) = matrix?.let { headAngles(it) } ?: (null to null)
        val yawRatio = if (xs.size > CONTOUR_IMAGE_RIGHT) {
            yawRatio(xs[NOSE_TIP], xs[CONTOUR_IMAGE_LEFT], xs[CONTOUR_IMAGE_RIGHT])
        } else 0f
        // The transform gives the size of the turn; the landmarks reliably give its direction.
        val yaw = yawFromMatrix ?: (yawRatio * 60f)
        val pitch = pitchFromMatrix ?: 0f
        val roll = if (xs.size > EYE_IMAGE_RIGHT_OUTER) {
            roll(
                (xs[EYE_IMAGE_LEFT_OUTER] + xs[EYE_IMAGE_LEFT_INNER]) / 2f * aspect,
                (ys[EYE_IMAGE_LEFT_OUTER] + ys[EYE_IMAGE_LEFT_INNER]) / 2f,
                (xs[EYE_IMAGE_RIGHT_OUTER] + xs[EYE_IMAGE_RIGHT_INNER]) / 2f * aspect,
                (ys[EYE_IMAGE_RIGHT_OUTER] + ys[EYE_IMAGE_RIGHT_INNER]) / 2f,
            )
        } else 0f
        val iris = if (xs.size > IRIS_IMAGE_RIGHT) irisOffset(xs) else 0f
        val direction = directionX(yawRatio, iris)
        val gaze = eyeGazeOff(blend)
        return Reading(
            box = box,
            eyes = eyes,
            expression = expression(blend),
            smile = smile(blend),
            jawOpen = blend["jawOpen"] ?: 0f,
            yaw = abs(yaw),
            pitch = abs(pitch),
            roll = roll,
            directionX = direction,
            lookingAtCamera = lookingAtCamera(abs(yaw), abs(pitch), gaze, eyes),
            cutOff = cutOffFraction(box),
        )
    }

    fun box(xs: FloatArray, ys: FloatArray): Box {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        // Iris points (468+) lie inside the eyes; the mesh outline is enough.
        val n = min(xs.size, 468)
        for (i in 0 until n) {
            l = min(l, xs[i]); r = max(r, xs[i]); t = min(t, ys[i]); b = max(b, ys[i])
        }
        return Box(l, t, r, b)
    }

    /** Share of the face box that falls outside the photo. */
    fun cutOffFraction(box: Box): Float {
        if (box.area <= 0f) return 0f
        val inside = box.intersection(Box(0f, 0f, 1f, 1f))
        return (1f - inside / box.area).coerceIn(0f, 1f)
    }

    fun eyeState(b: Map<String, Float>): EyeState {
        val l = b["eyeBlinkLeft"] ?: 0f
        val r = b["eyeBlinkRight"] ?: 0f
        val down = ((b["eyeLookDownLeft"] ?: 0f) + (b["eyeLookDownRight"] ?: 0f)) / 2f
        val avg = (l + r) / 2f
        return when {
            min(l, r) >= CLOSED_EYE && down < 0.75f -> EyeState.CLOSED
            // Looking down makes eyelids drop; that is not a blink.
            avg >= HALF_BLINK && down >= LOOK_DOWN -> EyeState.LOOKING_DOWN
            min(l, r) >= CLOSED_EYE -> EyeState.LOOKING_DOWN
            avg >= HALF_BLINK -> EyeState.HALF
            else -> EyeState.OPEN
        }
    }

    fun smile(b: Map<String, Float>): Float = ((b["mouthSmileLeft"] ?: 0f) + (b["mouthSmileRight"] ?: 0f)) / 2f

    fun expression(b: Map<String, Float>): Expression {
        val smile = smile(b)
        val jaw = b["jawOpen"] ?: 0f
        val browUp = b["browInnerUp"] ?: 0f
        val frown = ((b["mouthFrownLeft"] ?: 0f) + (b["mouthFrownRight"] ?: 0f)) / 2f
        val squint = ((b["eyeSquintLeft"] ?: 0f) + (b["eyeSquintRight"] ?: 0f)) / 2f
        val funnel = b["mouthFunnel"] ?: 0f
        val pucker = b["mouthPucker"] ?: 0f
        return when {
            smile >= SMILE && jaw >= LAUGH_JAW -> Expression.LAUGH
            smile >= SMILE -> Expression.SMILE
            // Crying: raised inner brows with a frown or squeezed eyes. Never treated as a flaw.
            browUp >= 0.45f && (frown >= 0.15f || squint >= 0.45f) -> Expression.EMOTIONAL
            smile < 0.3f && (jaw in TALK_JAW_MIN..TALK_JAW_MAX || funnel >= 0.3f ||
                (pucker >= 0.45f && jaw >= 0.08f)) -> Expression.MID_TALK
            else -> Expression.NATURAL
        }
    }

    /** How far the eyes look away from straight ahead, 0..1. */
    fun eyeGazeOff(b: Map<String, Float>): Float {
        fun eye(side: String) = maxOf(
            b["eyeLookIn$side"] ?: 0f,
            b["eyeLookOut$side"] ?: 0f,
            b["eyeLookUp$side"] ?: 0f,
            (b["eyeLookDown$side"] ?: 0f) * 0.8f,
        )
        return (eye("Left") + eye("Right")) / 2f
    }

    fun lookingAtCamera(yaw: Float, pitch: Float, gazeOff: Float, eyes: EyeState): Boolean =
        yaw <= MAX_YAW_FOR_CAMERA && pitch <= MAX_PITCH_FOR_CAMERA &&
            gazeOff <= MAX_EYE_GAZE_FOR_CAMERA && eyes != EyeState.LOOKING_DOWN

    /**
     * Yaw and pitch size in degrees from the face transform. Reads the face's forward axis,
     * which gives the same sizes whether the matrix is stored by rows or by columns.
     */
    fun headAngles(m: FloatArray): Pair<Float, Float> {
        if (m.size < 16) return 0f to 0f
        val fx = m[8]; val fy = m[9]; val fz = m[10]
        val yaw = Math.toDegrees(atan2(abs(fx).toDouble(), abs(fz).toDouble())).toFloat()
        val pitch = Math.toDegrees(atan2(abs(fy).toDouble(), hypot(fx.toDouble(), fz.toDouble()))).toFloat()
        return yaw to pitch
    }

    /** -1..1: positive when the nose is nearer the photo-right edge of the face (turned to photo right). */
    fun yawRatio(noseX: Float, contourLeftX: Float, contourRightX: Float): Float {
        val dl = noseX - contourLeftX
        val dr = contourRightX - noseX
        val sum = dl + dr
        if (sum <= 1e-6f) return 0f
        return ((dl - dr) / sum).coerceIn(-1f, 1f)
    }

    /** Head tilt in degrees from the line between the eyes (pixel-proportioned coordinates). */
    fun roll(lx: Float, ly: Float, rx: Float, ry: Float): Float =
        Math.toDegrees(atan2((ry - ly).toDouble(), (rx - lx).toDouble())).toFloat()

    /** Where the irises sit inside the eyes, -1 (photo left) .. +1 (photo right). */
    fun irisOffset(xs: FloatArray): Float {
        fun one(iris: Int, a: Int, b: Int): Float {
            val lo = min(xs[a], xs[b]); val hi = max(xs[a], xs[b])
            val w = hi - lo
            if (w <= 1e-6f) return 0f
            return (((xs[iris] - lo) / w) - 0.5f) * 2f
        }
        val l = one(IRIS_IMAGE_LEFT, EYE_IMAGE_LEFT_OUTER, EYE_IMAGE_LEFT_INNER)
        val r = one(IRIS_IMAGE_RIGHT, EYE_IMAGE_RIGHT_INNER, EYE_IMAGE_RIGHT_OUTER)
        return ((l + r) / 2f).coerceIn(-1f, 1f)
    }

    /** Which way the person is facing in the photo: head turn plus where the eyes point. */
    fun directionX(yawRatio: Float, irisOffset: Float): Float =
        (yawRatio * 1.5f + irisOffset * 0.8f).coerceIn(-1f, 1f)

    /**
     * Part of a face is covered when one skin patch looks clearly different from the others
     * (a hand, phone, glass or another head in front). Each patch is (red, green, blue) 0..255.
     * Returns 0 (clear) .. 1 (covered).
     */
    fun occlusion(patches: List<FloatArray>): Float {
        if (patches.size < 3) return 0f
        fun chroma(p: FloatArray): Pair<Float, Float> {
            val s = p[0] + p[1] + p[2] + 1f
            return p[0] / s to p[1] / s
        }
        fun luma(p: FloatArray) = 0.299f * p[0] + 0.587f * p[1] + 0.114f * p[2]
        val cr = patches.map { chroma(it).first }.sorted()
        val cg = patches.map { chroma(it).second }.sorted()
        val medR = cr[cr.size / 2]; val medG = cg[cg.size / 2]
        val lumas = patches.map { luma(it) }.sorted()
        val medL = lumas[lumas.size / 2].coerceAtLeast(1f)
        var worst = 0f
        for (p in patches) {
            val (r, g) = chroma(p)
            val colour = hypot((r - medR).toDouble(), (g - medG).toDouble()).toFloat()
            val light = luma(p) / medL
            // Side light changes brightness but keeps skin colour; covering changes both.
            val colourScore = ((colour - 0.04f) / 0.08f).coerceIn(0f, 1f)
            val lightScore = when {
                light < 0.45f -> 1f
                light > 2.2f -> 1f
                else -> 0f
            }
            worst = max(worst, max(colourScore, if (colourScore > 0.3f) lightScore else 0f))
        }
        return worst
    }

    /** Standard face positions for the 112x112 face-matching crop (OpenCV SFace). */
    val SFACE_TEMPLATE = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        56.0252f, 71.7366f,
        41.5493f, 92.3655f,
        70.7299f, 92.2041f,
    )

    /**
     * Least-squares similarity transform (turn, scale, move) taking [src] points onto [dst].
     * Points are x,y pairs. Returns a,b,tx,ty for x' = a*x - b*y + tx, y' = b*x + a*y + ty.
     */
    fun similarity(src: FloatArray, dst: FloatArray): FloatArray {
        val n = src.size / 2
        var sx = 0.0; var sy = 0.0; var dx = 0.0; var dy = 0.0
        for (i in 0 until n) { sx += src[2 * i]; sy += src[2 * i + 1]; dx += dst[2 * i]; dy += dst[2 * i + 1] }
        sx /= n; sy /= n; dx /= n; dy /= n
        var num1 = 0.0; var num2 = 0.0; var den = 0.0
        for (i in 0 until n) {
            val px = src[2 * i] - sx; val py = src[2 * i + 1] - sy
            val qx = dst[2 * i] - dx; val qy = dst[2 * i + 1] - dy
            num1 += px * qx + py * qy
            num2 += px * qy - py * qx
            den += px * px + py * py
        }
        if (den <= 1e-12) return floatArrayOf(1f, 0f, (dx - sx).toFloat(), (dy - sy).toFloat())
        val a = num1 / den
        val b = num2 / den
        val tx = dx - (a * sx - b * sy)
        val ty = dy - (b * sx + a * sy)
        return floatArrayOf(a.toFloat(), b.toFloat(), tx.toFloat(), ty.toFloat())
    }

    /** The five face points SFace expects, in pixels: eyes, nose tip, mouth corners. */
    fun alignmentPoints(xs: FloatArray, ys: FloatArray, width: Int, height: Int): FloatArray {
        fun mid(a: Int, b: Int, v: FloatArray) = (v[a] + v[b]) / 2f
        val leftEyeX = if (xs.size > IRIS_IMAGE_RIGHT) xs[IRIS_IMAGE_LEFT] else mid(EYE_IMAGE_LEFT_OUTER, EYE_IMAGE_LEFT_INNER, xs)
        val leftEyeY = if (ys.size > IRIS_IMAGE_RIGHT) ys[IRIS_IMAGE_LEFT] else mid(EYE_IMAGE_LEFT_OUTER, EYE_IMAGE_LEFT_INNER, ys)
        val rightEyeX = if (xs.size > IRIS_IMAGE_RIGHT) xs[IRIS_IMAGE_RIGHT] else mid(EYE_IMAGE_RIGHT_INNER, EYE_IMAGE_RIGHT_OUTER, xs)
        val rightEyeY = if (ys.size > IRIS_IMAGE_RIGHT) ys[IRIS_IMAGE_RIGHT] else mid(EYE_IMAGE_RIGHT_INNER, EYE_IMAGE_RIGHT_OUTER, ys)
        return floatArrayOf(
            leftEyeX * width, leftEyeY * height,
            rightEyeX * width, rightEyeY * height,
            xs[NOSE_TIP] * width, ys[NOSE_TIP] * height,
            xs[MOUTH_IMAGE_LEFT] * width, ys[MOUTH_IMAGE_LEFT] * height,
            xs[MOUTH_IMAGE_RIGHT] * width, ys[MOUTH_IMAGE_RIGHT] * height,
        )
    }

    /** Scales a vector to length 1 (in place) and returns it. */
    fun normalize(v: FloatArray): FloatArray {
        var s = 0.0
        for (x in v) s += x * x
        val n = sqrt(s).toFloat()
        if (n > 1e-9f) for (i in v.indices) v[i] /= n
        return v
    }
}
