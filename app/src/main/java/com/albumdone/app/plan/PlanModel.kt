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
    /** The app's own guess (the couple has not said who this is): softer targets, no warnings. */
    val guessed: Boolean = false,
    /** The couple asked for this many photos of this person (null: the app decides). */
    val wanted: Int? = null,
)

/**
 * What the folder is for. It decides who the album is built around and the words the app uses;
 * how photos are judged is the same for every occasion.
 */
enum class Occasion(val label: String, val sub: String? = null) {
    WEDDING("Wedding"), ENGAGEMENT("Engagement"),
    KIDS_BIRTHDAY("Birthday", "A child's"), BIRTHDAY("Birthday", "A grown-up's"),
    CHILD_FUNCTION("Family function", "Naming / annaprashan"), BABY_SHOWER("Family function", "Baby shower"),
    ANNIVERSARY("Family function", "Anniversary"), HOUSEWARMING("Family function", "Housewarming"),
    TRIP("Trip"), OTHER("Other");

    /** Built around a couple, each with their own family (the bride's side and the groom's side). */
    val couple: Boolean get() = this == WEDDING || this == ENGAGEMENT

    /** About a child: the album needs the child with mummy and papa, not just the child. */
    val child: Boolean get() = this == KIDS_BIRTHDAY || this == CHILD_FUNCTION

    /** Always about two people found the way a couple is (an anniversary), without the two families' sides. */
    val twoMain: Boolean get() = couple || this == ANNIVERSARY

    /** A second main person makes sense (a couple on a trip, a grown-up and their partner). */
    val pairs: Boolean get() = this == BIRTHDAY || this == TRIP || this == OTHER || this == BABY_SHOWER || this == HOUSEWARMING

    /** Share of each event for photos without faces: a trip is as much about the places as the people. */
    val detailShare: Double get() = if (this == TRIP) 0.25 else 0.10

    /** Who the album is about, when nobody has named them: "the bride", "the birthday star". */
    fun role(first: Boolean): String = when {
        couple -> if (first) "the bride" else "the groom"
        first && (this == KIDS_BIRTHDAY || this == BIRTHDAY) -> "the birthday star"
        first && this == CHILD_FUNCTION -> "the little one"
        this == BABY_SHOWER -> if (first) "the mother-to-be" else "the father-to-be"
        this == HOUSEWARMING -> "the host"
        else -> "the main person"
    }

    /** The same as a label: "Bride", "Birthday star", "Main person". */
    fun title(first: Boolean): String = role(first).removePrefix("the ").capitalized()

    companion object {
        // "BABY" was an occasion before baby functions became family functions.
        fun of(name: String?): Occasion = if (name == "BABY") CHILD_FUNCTION else values().firstOrNull { it.name == name } ?: WEDDING

        private val GUESS = listOf(
            // Before weddings: "25th wedding anniversary" is an anniversary.
            ANNIVERSARY to listOf("anniversary", "silver jubilee", "golden jubilee", "vardhapan din"),
            WEDDING to listOf("wedding", "shaadi", "shadi", "vivah", "lagna", "lagn", "haldi", "mehendi", "mehndi", "sangeet",
                "reception", "baraat", "pheras", "mandap", "marriage", "kalyanam"),
            ENGAGEMENT to listOf("engagement", "sagai", "sakharpuda", "roka", "ring ceremony", "mangni", "nischitartham"),
            BIRTHDAY to listOf("birthday", "bday", "b'day", "janmadin", "vadhdivas", "shashtipoorti", "amrut mahotsav"),
            CHILD_FUNCTION to listOf("naming", "namkaran", "naamkaran", "barsa", "bārsā", "annaprashan", "annaprasana", "mundan",
                "chudakarma", "cradle ceremony", "palna", "naming ceremony"),
            BABY_SHOWER to listOf("baby shower", "godh bharai", "godbharai", "dohale", "seemantham", "valaikappu", "shrimant", "sadhe"),
            HOUSEWARMING to listOf("housewarming", "house warming", "griha pravesh", "grihapravesh", "gruhapravesham", "vastu shanti", "vastushanti"),
            TRIP to listOf("trip", "tour", "travel", "holiday", "vacation", "honeymoon", "trek"),
        )

        /** "Aarav 5th birthday": a child; "Baba 60th": a grown-up. Most birthday folders are a child's. */
        private fun birthday(text: String): Occasion {
            if (listOf("shashtipoorti", "amrut mahotsav").any { it in text }) return BIRTHDAY
            val age = Regex("""(\d{1,3})\s*(st|nd|rd|th)\b""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            return if (age != null && age >= 18) BIRTHDAY else KIDS_BIRTHDAY
        }

        /** A first guess from the folder names (a wedding's folders often include the engagement), so most people never change it. */
        fun guess(names: Collection<String>): Occasion {
            val text = names.joinToString(" ").lowercase()
            val found = GUESS.firstOrNull { (_, words) -> words.any { it in text } }?.first ?: OTHER
            return if (found == BIRTHDAY) birthday(text) else found
        }
    }
}

/** What the family told us about their people, plus who the album is about (the couple, or the main people). */
data class PeopleTags(
    /** The bride, or for other occasions the main person (the birthday star, the baby). */
    val brideId: Long? = null,
    /** The groom, or for other occasions a second main person when there clearly is one. */
    val groomId: Long? = null,
    val tags: Map<Long, PersonTag> = emptyMap(),
    /** Whose album this is: UNKNOWN for the couple's own (both sides balanced), else one family's. */
    val albumFor: Side = Side.UNKNOWN,
    val occasion: Occasion = Occasion.WEDDING,
    /** A child's mummy and papa (named, or the app's guess): the album needs the three of them together. */
    val parents: List<Long> = emptyList(),
) {
    /**
     * The same wedding as one family's album (both families usually keep their own copy): their
     * relatives come first, with close relatives getting a close family's share; the other side's
     * relatives appear only where they share a photo.
     */
    fun forFamily(side: Side): PeopleTags = copy(albumFor = side, tags = tags.mapValues { (_, t) ->
        when {
            t.side == side && t.priority == Priority.IMPORTANT -> t.copy(priority = Priority.MUST)
            t.side != side && t.side != Side.UNKNOWN && (t.priority == Priority.MUST || t.priority == Priority.IMPORTANT) ->
                t.copy(priority = Priority.NORMAL)
            else -> t
        }
    })

    fun tag(id: Long): PersonTag = tags[id] ?: PersonTag(id)
    fun isCouple(id: Long?): Boolean = id != null && (id == brideId || id == groomId)
    fun label(id: Long): String = tags[id]?.name?.takeIf { it.isNotBlank() } ?: when (id) {
        brideId -> occasion.title(first = true)
        groomId -> occasion.title(first = false)
        else -> "Person $id"
    }

    /** In a sentence: their name when given, otherwise "the bride", "the birthday star". */
    fun who(id: Long): String = tags[id]?.name?.takeIf { it.isNotBlank() } ?: occasion.role(first = id == brideId)

    /** Both main people in a sentence: "the couple", "Riya and Arjun", "the two of them". */
    fun pair(): String {
        if (occasion.couple || occasion == Occasion.ANNIVERSARY) return "the couple"
        val a = brideId?.let { tags[it]?.name }?.takeIf { it.isNotBlank() }
        val b = groomId?.let { tags[it]?.name }?.takeIf { it.isNotBlank() }
        return if (a != null && b != null) "$a and $b" else when (occasion) {
            Occasion.BABY_SHOWER -> "the parents-to-be"
            Occasion.HOUSEWARMING -> "the hosts"
            else -> "the two of them"
        }
    }
}

fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }

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
