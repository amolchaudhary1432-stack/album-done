package com.albumdone.app.plan

import kotlin.math.max
import kotlin.math.min

/** A rectangle in photo coordinates, 0..1 on both axes (may reach past the edges for cut-off faces). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = max(0f, width) * max(0f, height)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun intersection(o: Box): Float {
        val w = min(right, o.right) - max(left, o.left)
        val h = min(bottom, o.bottom) - max(top, o.top)
        return if (w <= 0f || h <= 0f) 0f else w * h
    }

    fun encode(): String = "$left,$top,$right,$bottom"

    companion object {
        fun decode(s: String): Box? {
            val p = s.split(',').mapNotNull { it.toFloatOrNull() }
            return if (p.size == 4) Box(p[0], p[1], p[2], p[3]) else null
        }

        fun encodeList(boxes: List<Box>): String = boxes.joinToString(";") { it.encode() }
        fun decodeList(s: String?): List<Box> =
            if (s.isNullOrBlank()) emptyList() else s.split(';').mapNotNull { decode(it) }
    }
}

/** UNKNOWN: the face was found but its eyes could not be read (side-on, covered or very small). */
enum class EyeState { OPEN, LOOKING_DOWN, HALF, CLOSED, UNKNOWN }

enum class Expression { SMILE, LAUGH, NATURAL, MID_TALK, EMOTIONAL }

/** One face in a photo, as measured on the phone. */
data class PlanFace(
    /** The person this face belongs to, or null if not matched to anyone. */
    val personId: Long? = null,
    val box: Box,
    val eyes: EyeState = EyeState.OPEN,
    val expression: Expression = Expression.NATURAL,
    /** Smile strength 0..1. */
    val smile: Float = 0f,
    val lookingAtCamera: Boolean = true,
    /** Which way the person faces, -1 (photo left) .. +1 (photo right). */
    val directionX: Float = 0f,
    /** Sharpness measured on the face only. */
    val sharpness: Double = 100.0,
    /** Average brightness of the face, 0..255. */
    val brightness: Double = 130.0,
    /** Face much darker than what is behind it. */
    val backlit: Boolean = false,
    /** 0..1, how likely part of the face is covered (hand, phone, hair...). */
    val occlusion: Float = 0f,
    /** Share of the face that lies outside the photo. */
    val cutOff: Float = 0f,
)

/** What the planner needs to know about one photo. Plain Kotlin so it can be unit-tested. */
data class PlanPhoto(
    val id: Long,
    val name: String,
    /** Sub-folder path inside the chosen folder, "" for the top level. */
    val folder: String,
    /** Capture time in millis, 0 when unknown. */
    val takenAt: Long = 0,
    val width: Int = 1500,
    val height: Int = 1000,
    /** Whole-photo sharpness. */
    val sharpness: Double = 100.0,
    /** Sharpness of the sharpest part of the photo (the subject when there is no face). */
    val peakSharpness: Double = 100.0,
    val brightness: Double = 128.0,
    val darkFraction: Double = 0.0,
    val brightFraction: Double = 0.0,
    val hash: Long = 0,
    /** Tilt of the horizon in degrees, 0 when level or unknown. */
    val tilt: Float = 0f,
    val faces: List<PlanFace> = emptyList(),
    /** Phones seen in the photo. */
    val phones: List<Box> = emptyList(),
    /** People in the foreground with their back to the camera. */
    val foregroundBacks: Int = 0,
    /** Share of the photo covered by people, 0..1. People but no visible face: not a detail shot. */
    val peopleArea: Float = 0f,
    /** The photo could not be read. */
    val failed: Boolean = false,
) {
    val portrait: Boolean get() = height > width
}

enum class Priority { MUST, IMPORTANT, NORMAL, SKIP }

enum class Side { BRIDE, GROOM, UNKNOWN }

data class PersonTag(
    val id: Long,
    val name: String = "",
    val priority: Priority = Priority.NORMAL,
    val side: Side = Side.UNKNOWN,
    val elder: Boolean = false,
)

/** What the couple told us about their family, plus who the couple are. */
data class PeopleTags(
    val brideId: Long? = null,
    val groomId: Long? = null,
    val tags: Map<Long, PersonTag> = emptyMap(),
) {
    fun tag(id: Long): PersonTag = tags[id] ?: PersonTag(id)
    fun isCouple(id: Long?): Boolean = id != null && (id == brideId || id == groomId)
    fun label(id: Long): String = when (id) {
        brideId -> "Bride"
        groomId -> "Groom"
        else -> tags[id]?.name?.takeIf { it.isNotBlank() } ?: "Person $id"
    }
}

/** Small helpers for showing photos and names the way people expect. */
object Display {

    /** "01 Haldi" -> "Haldi", "3. Wedding" -> "Wedding", "02_Reception" -> "Reception". */
    fun eventName(folder: String): String {
        val cleaned = folder.replace(Regex("^\\s*\\d+\\s*[._\\-)]*\\s*"), "").replace('_', ' ').trim()
        return cleaned.ifEmpty { folder }
    }

    /**
     * Where to centre a square thumbnail of a [width] x [height] photo so the faces show.
     * Returns (horizontal, vertical) alignment from -1 (left/top) to 1 (right/bottom).
     */
    fun cropBias(width: Int, height: Int, faces: List<Box>): Pair<Float, Float> {
        if (width <= 0 || height <= 0) return 0f to 0f
        val visible = faces.filter { it.width >= 0.03f }
        if (visible.isEmpty()) return 0f to if (height > width) -0.4f else 0f // people's heads are usually high
        val l = visible.minOf { it.left }; val r = visible.maxOf { it.right }
        val t = visible.minOf { it.top }; val b = visible.maxOf { it.bottom }
        fun bias(center: Float, shown: Float): Float {
            if (shown >= 1f) return 0f
            val start = (center - shown / 2f).coerceIn(0f, 1f - shown)
            return start / (1f - shown) * 2f - 1f
        }
        return if (height > width) {
            0f to bias((t + b) / 2f, width.toFloat() / height)
        } else {
            bias((l + r) / 2f, height.toFloat() / width) to 0f
        }
    }
}
