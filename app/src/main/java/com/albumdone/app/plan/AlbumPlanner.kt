package com.albumdone.app.plan

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Issue(val hard: Boolean, val label: String) {
    UNREADABLE(true, "Could not be opened"),
    BLURRY(true, "Blurry"),
    TOO_DARK(true, "Too dark"),
    TOO_BRIGHT(true, "Too bright"),
    EYES_CLOSED(true, "Eyes closed"),
    DUPLICATE(true, "Extra shot of the same moment"),
    GROUP_REPEAT(true, "Same group as a better photo"),
    FACE_CUT_OFF(true, "Face cut off by the edge"),
    FACE_BLOCKED(true, "Face blocked by someone or something"),
    BACK_OF_GUEST(true, "Guest's back in front of the camera"),
    HALF_BLINK(false, "Half-blink"),
    MID_TALK(false, "Caught mid-talk or chewing"),
    NOT_LOOKING(false, "Someone not looking at the camera"),
    FACE_DARK(false, "Face in shadow or backlit"),
    FACE_PARTLY_HIDDEN(false, "Face partly hidden"),
    PHONE_IN_SHOT(false, "Phone in the shot"),
    STRANGER(false, "Stranger in the shot"),
    TILTED(false, "Slightly crooked"),
    FACES_TURNED_AWAY(false, "People with their backs or faces turned away"),
}

enum class ShotType(val label: String) {
    DETAIL("Detail"),
    COUPLE_CLOSE("Couple close-up"),
    COUPLE_FULL("Couple full-length"),
    BRIDE_SOLO("Bride portrait"),
    GROOM_SOLO("Groom portrait"),
    GROUP("Family group"),
    CANDID("Candid"),
    GUEST("Portrait"),
}

enum class EventKind { RITUAL, VIDAAI, PARTY, PREP, OTHER }

data class Moment(
    val id: Int,
    val event: String,
    /** Photos in time order. */
    val photoIds: List<Long>,
    val start: Long,
    val end: Long,
    /** Many photos taken close together: a key moment (varmala, pheras, sindoor...). */
    val key: Boolean,
)

class AssessedPhoto(
    val photo: PlanPhoto,
    val event: String,
    val momentId: Int,
    val issues: Set<Issue>,
    val quality: Double,
    val shot: ShotType,
    /** People whose face is visible (big enough to recognise). */
    val people: Set<Long>,
    val coupleLookingAtEachOther: Boolean,
    val ritual: Boolean,
    val interaction: Boolean,
    val emotional: Boolean,
    val allEyesOpenAndLooking: Boolean,
    /** For the best shot of a burst or repeated group: how many similar shots it beat. */
    val similarShots: Int,
    /** Photos in the same burst, repeat or stage group share this number; -1 when alone. */
    val repeatGroup: Int,
    /** Position in capture order across the whole wedding. */
    val order: Int,
) {
    val id: Long get() = photo.id
    val usable: Boolean get() = issues.none { it.hard }
    val hardIssues: List<Issue> get() = issues.filter { it.hard }
}

data class EventStats(val name: String, val total: Int, val usable: Int, val kind: EventKind)

/** Result of checking every photo of one wedding. Photos are in capture order. */
class AlbumAnalysis(
    val photos: List<AssessedPhoto>,
    val events: List<String>,
    val moments: List<Moment>,
    val eventKinds: Map<String, EventKind>,
    val tags: PeopleTags,
    /** Number of photos each person is visible in. */
    val personPhotoCount: Map<Long, Int>,
) {
    val byId: Map<Long, AssessedPhoto> = photos.associateBy { it.id }
    val momentById: Map<Int, Moment> = moments.associateBy { it.id }

    val usableCount: Int get() = photos.count { it.usable }
    fun count(issue: Issue): Int = photos.count { issue in it.issues }
    fun countAny(vararg issues: Issue): Int = photos.count { p -> issues.any { it in p.issues } }
    val darkOrBrightCount: Int get() = countAny(Issue.TOO_DARK, Issue.TOO_BRIGHT)

    val eventStats: List<EventStats>
        get() = events.map { e ->
            val inEvent = photos.filter { it.event == e }
            EventStats(e, inEvent.size, inEvent.count { it.usable }, eventKinds[e] ?: EventKind.OTHER)
        }
}

/** How well one tagged person is covered. */
data class Coverage(
    val personId: Long,
    val name: String,
    val priority: Priority,
    val side: Side,
    val elder: Boolean,
    val picked: Int,
    val target: Int,
    /** Clean photos of them with the couple. */
    val goodAvailable: Int,
    val warning: String?,
)

class Shortlist(
    /** In story order: events in the photographer's order, then capture time. */
    val ids: List<Long>,
    val reasons: Map<Long, List<String>>,
    val coverId: Long?,
    val coverage: List<Coverage>,
)

/**
 * The selection brain. Pure Kotlin, no Android classes, every rule unit-tested.
 *
 * Checking ([analyze]): each photo and each face is judged in its context (rituals, vidaai,
 * candids, stage lighting), repeats are collapsed to the best shot, and every event is cut
 * into moments.
 *
 * Choosing ([shortlist]): people first (the couple, then the family members the couple
 * marked), then moments (every part of each event's story, key moments getting more), then
 * variety (shot types, no repeated pose, portrait/landscape balance), then quality.
 */
object AlbumPlanner {

    // ---- Photo checks ----
    const val BLUR_FACTOR = 0.3
    /** A face at least this sharp, compared with the sharpest face in the photo, is part of the subject. */
    const val SUBJECT_SHARPNESS = 0.5
    /** Photos this close in time are the same scene for judging sharpness. */
    const val SCENE_MILLIS = 20 * 60_000L
    const val SCENE_NEIGHBOURS = 12
    const val SCENE_MIN_FACES = 6
    const val SCENE_MIN_PHOTOS = 5
    /** A scene is never let off more than this: below it the whole scene is simply blurry. */
    const val SCENE_FLOOR = 0.15
    /** Other photos of the same minutes needed to judge a photo's light by its scene. */
    const val SCENE_MIN_LIGHT = 3
    const val DARK_BRIGHTNESS = 40.0
    const val DARK_SHARE = 0.65
    // From the replica dump: real photos reach brightness 168 and 25% burnt white at most;
    // flash-blown ones 193-207 and 42-49%.
    const val BRIGHT_BRIGHTNESS = 185.0
    const val BLOWN_SHARE = 0.35
    const val MIN_TILT = 2.5f
    const val MAX_TILT = 12f

    // ---- Faces ----
    /** A face this wide (share of photo width) is clearly visible and must look good. */
    const val CLEAR_FACE = 0.06f
    /** A face this wide is big enough to recognise the person. */
    const val VISIBLE_FACE = 0.03f
    const val CUT_OFF = 0.12f
    /** Up to this much of a face may be above the photo's top edge (forehead and hair). */
    const val FOREHEAD_CROP = 0.3f
    const val BLOCKED_OVERLAP = 0.25f
    const val HIDDEN = 0.6f
    const val CLOSE_UP_FACE = 0.12f

    // ---- Repeats ----
    const val BURST_GAP_MILLIS = 3_000L
    const val BURST_HASH_DISTANCE = 12
    const val SAME_SHOT_HASH = 6
    const val SAME_SHOT_MILLIS = 60_000L
    const val GROUP_REPEAT_MILLIS = 10 * 60_000L
    const val GROUP_SAME_PEOPLE = 0.6
    const val GROUP_HASH = 16
    const val SAME_POSE_HASH = 22

    // ---- Moments ----
    const val MIN_MOMENT_GAP = 45_000L
    const val MAX_MOMENT_GAP = 10 * 60_000L
    const val UNTIMED_MOMENT = 15

    // ---- People ----
    const val MUST_TARGET = 5
    /** Targets for family the app only guessed: it may be wrong about who they are. */
    const val GUESSED_MUST_TARGET = 3
    const val GUESSED_IMPORTANT_TARGET = 1
    const val IMPORTANT_TARGET = 2
    const val RARE_PERSON = 2
    const val FREQUENT_PERSON = 5

    // ---- Variety ----
    /** A photo this much covered by people, with no face visible, shows people facing away. */
    const val PEOPLE_NOT_DETAIL = 0.15f
    /** Pull of a moment that has not yet had its share of the event's places. */
    const val MOMENT_BONUS = 0.3
    /** Less value for each photo already picked from the same moment, so picks spread over the day. */
    const val MOMENT_CROWDING = 0.06
    val TYPE_SHARE = mapOf(
        ShotType.DETAIL to 0.10, ShotType.COUPLE_CLOSE to 0.15, ShotType.COUPLE_FULL to 0.12,
        ShotType.BRIDE_SOLO to 0.06, ShotType.GROOM_SOLO to 0.05, ShotType.GROUP to 0.20,
        ShotType.CANDID to 0.25, ShotType.GUEST to 0.07,
    )

    const val OTHER_PHOTOS = "Other photos"
    const val ALL_PHOTOS = "All photos"

    // =====================================================================================
    // Checking
    // =====================================================================================

    fun analyze(photos: List<PlanPhoto>, tags: PeopleTags = PeopleTags()): AlbumAnalysis {
        val eventOf = assignEvents(photos)
        val events = orderEvents(photos, eventOf)
        val eventIndex = events.withIndex().associate { it.value to it.index }
        val kinds = events.associateWith { eventKind(it) }

        val ordered = photos.sortedWith(
            compareBy<PlanPhoto> { eventIndex.getValue(eventOf.getValue(it.id)) }
                .thenBy { if (it.takenAt > 0) it.takenAt else Long.MAX_VALUE }
                .thenBy { it.folder }
                .thenBy { it.name }
        )
        val readable = ordered.filter { !it.failed }

        val personPhotos = HashMap<Long, Int>()
        for (p in readable) {
            p.faces.filter { it.box.width >= VISIBLE_FACE }.mapNotNull { it.personId }.toSet()
                .forEach { personPhotos[it] = (personPhotos[it] ?: 0) + 1 }
        }

        val clearFaces = readable.flatMap { p -> p.faces.filter { it.box.width >= CLEAR_FACE } }
        // Small faces (group photos) measure less sharp than big ones (close-ups) even when both
        // are in focus, so each face is compared with faces of a similar size.
        val bySize = clearFaces.groupBy { sizeBucket(it.box.width) }
        val norms = Norms(
            peakSharpness = median(readable.map { it.peakSharpness }),
            faceSharpness = median(clearFaces.map { it.sharpness }),
            faceSharpnessBySize = bySize.filterValues { it.size >= 5 }.mapValues { (_, f) -> median(f.map { it.sharpness }) },
            eventBrightness = events.associateWith { e -> median(readable.filter { eventOf[it.id] == e }.map { it.brightness }) },
            eventDark = events.associateWith { e -> median(readable.filter { eventOf[it.id] == e }.map { it.darkFraction }) },
            eventBlown = events.associateWith { e -> median(readable.filter { eventOf[it.id] == e }.map { it.brightFraction }) },
            eventFaceBrightness = events.associateWith { e ->
                median(readable.filter { eventOf[it.id] == e }.flatMap { p ->
                    p.faces.filter { it.box.width >= CLEAR_FACE }.map { it.brightness }
                })
            },
        )
        sceneSharpness(readable, eventOf, norms)

        // First pass: judge each photo on its own.
        val judged = HashMap<Long, Judgement>()
        for (p in ordered) {
            val event = eventOf.getValue(p.id)
            judged[p.id] = judge(p, kinds.getValue(event), event, norms, tags, personPhotos)
        }

        // Moments and repeats per event.
        val moments = ArrayList<Moment>()
        val momentOf = HashMap<Long, Int>()
        val repeatIssue = HashMap<Long, Issue>()
        val similar = HashMap<Long, Int>()
        val groupOf = HashMap<Long, Int>()
        var nextGroup = 0
        for (event in events) {
            val inEvent = ordered.filter { eventOf[it.id] == event && !it.failed }
            for (m in findMoments(event, inEvent, moments.size)) {
                moments += m
                m.photoIds.forEach { momentOf[it] = m.id }
            }
            for (group in findRepeats(inEvent, judged, tags)) {
                val keeper = group.members.minWith(
                    compareBy<PlanPhoto> { judged.getValue(it.id).issues.count { i -> i.hard } }
                        .thenByDescending { judged.getValue(it.id).quality }
                        .thenBy { it.name }
                )
                similar[keeper.id] = group.members.size
                val g = nextGroup++
                group.members.forEach { groupOf[it.id] = g }
                val issue = if (group.isGroupPhoto) Issue.GROUP_REPEAT else Issue.DUPLICATE
                group.members.filter { it.id != keeper.id }.forEach { repeatIssue[it.id] = issue }
            }
        }

        // A long burst of near-identical frames is one shot, not a key moment: count distinct shots.
        val distinct = moments.associate { m -> m.id to m.photoIds.map { id -> groupOf[id]?.let { -1L - it } ?: id }.distinct().size }
        for (event in moments.map { it.event }.distinct()) {
            val inEvent = moments.withIndex().filter { it.value.event == event }
            val keySize = max(8.0, 2.0 * median(inEvent.map { distinct.getValue(it.value.id).toDouble() }))
            for ((i, m) in inEvent) moments[i] = m.copy(key = distinct.getValue(m.id) >= keySize)
        }

        val assessed = ordered.mapIndexed { i, p ->
            val j = judged.getValue(p.id)
            val issues = j.issues + listOfNotNull(repeatIssue[p.id])
            AssessedPhoto(
                photo = p,
                event = eventOf.getValue(p.id),
                momentId = momentOf[p.id] ?: -1,
                issues = issues,
                quality = if (issues.any { it.hard }) j.quality * 0.5 else j.quality,
                shot = j.shot,
                people = j.people,
                coupleLookingAtEachOther = j.coupleLooking,
                ritual = j.ritual,
                interaction = j.interaction,
                emotional = j.emotional,
                allEyesOpenAndLooking = j.allEyesOpenAndLooking,
                similarShots = similar[p.id] ?: 1,
                repeatGroup = groupOf[p.id] ?: -1,
                order = i,
            )
        }
        return AlbumAnalysis(assessed, events, moments, kinds, tags, personPhotos)
    }

    /** Face size groups (share of photo width) for comparing sharpness fairly. */
    fun sizeBucket(width: Float): Int = when {
        width < 0.09f -> 0
        width < 0.15f -> 1
        width < 0.25f -> 2
        else -> 3
    }

    private class Norms(
        val peakSharpness: Double,
        val faceSharpness: Double,
        val faceSharpnessBySize: Map<Int, Double>,
        val eventBrightness: Map<String, Double>,
        val eventDark: Map<String, Double>,
        val eventBlown: Map<String, Double>,
        val eventFaceBrightness: Map<String, Double>,
    ) {
        /** Per photo: how sharp its scene is compared with the whole wedding (0.15..1), faces and whole photo. */
        val sceneFace = HashMap<Long, Double>()
        val scenePeak = HashMap<Long, Double>()
        /** Per photo: the light of its scene (median brightness and share of near-black), when it has one. */
        val sceneBrightness = HashMap<Long, Double>()
        val sceneDark = HashMap<Long, Double>()
    }

    /**
     * Sharpness is judged against photos taken around the same time in the same function: a
     * night ritual in dim light, shot with a long lens, measures far softer than daylight
     * portraits from the same folder, yet looks fine in an album. On a real wedding, 12 of 20
     * night-ritual photos were wrongly "blurry" against the daytime portraits, among them the
     * couple's only close photo. A shaky frame still stands out against its own scene, and a
     * scene is never judged more strictly than the whole wedding.
     */
    private fun sceneSharpness(readable: List<PlanPhoto>, eventOf: Map<Long, String>, norms: Norms) {
        val faceRatios = readable.associate { p ->
            p.id to p.faces.filter { it.box.width >= CLEAR_FACE }.map { f ->
                val norm = norms.faceSharpnessBySize[sizeBucket(f.box.width)] ?: norms.faceSharpness
                if (norm > 0) f.sharpness / norm else 1.0
            }
        }
        val peakRatio = readable.associate { p -> p.id to if (norms.peakSharpness > 0) p.peakSharpness / norms.peakSharpness else 1.0 }
        for ((_, inEvent) in readable.groupBy { eventOf[it.id] }) {
            val timed = inEvent.filter { it.takenAt > 0 }.sortedBy { it.takenAt }
            val untimed = inEvent.filter { it.takenAt <= 0 }
            var lo = 0; var hi = 0
            for (p in timed) {
                while (timed[lo].takenAt < p.takenAt - SCENE_MILLIS) lo++
                while (hi < timed.size && timed[hi].takenAt <= p.takenAt + SCENE_MILLIS) hi++
                val scene = timed.subList(lo, hi)
                norms.sceneFace[p.id] = sceneFactor(scene.flatMap { faceRatios.getValue(it.id) }, SCENE_MIN_FACES)
                norms.scenePeak[p.id] = sceneFactor(scene.map { peakRatio.getValue(it.id) }, SCENE_MIN_PHOTOS)
                // A night scene (sky lanterns, a lit palace, a dance floor) is judged by its own light:
                // the other photos of those minutes, so one black frame cannot excuse itself.
                val others = scene.filter { it.id != p.id }
                if (others.size >= SCENE_MIN_LIGHT) {
                    norms.sceneBrightness[p.id] = median(others.map { it.brightness })
                    norms.sceneDark[p.id] = median(others.map { it.darkFraction })
                }
            }
            // Without capture times, neighbours in file order stand in for the scene.
            for ((i, p) in untimed.withIndex()) {
                val scene = untimed.subList(max(0, i - SCENE_NEIGHBOURS), min(untimed.size, i + SCENE_NEIGHBOURS + 1))
                norms.sceneFace[p.id] = sceneFactor(scene.flatMap { faceRatios.getValue(it.id) }, SCENE_MIN_FACES)
                norms.scenePeak[p.id] = sceneFactor(scene.map { peakRatio.getValue(it.id) }, SCENE_MIN_PHOTOS)
            }
        }
    }

    private fun sceneFactor(ratios: List<Double>, minCount: Int): Double =
        if (ratios.size < minCount) 1.0 else median(ratios).coerceIn(SCENE_FLOOR, 1.0)

    private class Judgement(
        val issues: Set<Issue>,
        val quality: Double,
        val shot: ShotType,
        val people: Set<Long>,
        val coupleLooking: Boolean,
        val ritual: Boolean,
        val interaction: Boolean,
        val emotional: Boolean,
        val allEyesOpenAndLooking: Boolean,
    )

    /** Event type from the photographer's folder name; decides which context rules apply. */
    /**
     * The bride's or groom's own function at home (haldi, mehendi, adhibaas...): their families are
     * there, the other half of the couple usually is not.
     */
    fun ownFunction(name: String): Boolean {
        val n = name.lowercase()
        return listOf("haldi", "halad", "holud", "mehendi", "mehndi", "mehandi", "adhibas", "adhibaas", "adhivas",
            "aiburobhat", "pellikuthuru", "pellikoduku", "nalangu", "maiyan", "pithi", "chooda", "chuda", "tilak",
            "getting ready", "makeup").any { n.contains(it) }
    }

    fun eventKind(name: String): EventKind {
        val n = name.lowercase()
        fun has(vararg words: String) = words.any { n.contains(it) }
        // Names photographers use across India (Hindi, Marathi, Bengali, Gujarati, Punjabi, Telugu, Tamil).
        return when {
            has("vidaai", "vidai", "bidaai", "bidai", "bidaayi", "doli", "kanakanjali", "pathavani") -> EventKind.VIDAAI
            has("sangeet", "reception", "cocktail", "party", "dj ", "dance", "garba", "dandiya", "bou bhaat",
                "boubhat", "bauvat", "engagement", "ring ceremony", "sagai", "roka", "sakharpuda") -> EventKind.PARTY
            has("haldi", "halad", "holud", "mehendi", "mehndi", "mehandi", "tilak", "chooda", "chuda", "getting ready",
                "makeup", "aiburobhat", "pellikuthuru", "pellikoduku", "nalangu", "maiyan", "pithi") -> EventKind.PREP
            has("wedding", "shaadi", "shadi", "vivah", "phera", "phere", "mandap", "ceremony", "puja", "pooja",
                "muhurt", "nikah", "kanyadaan", "kanyadan", "saptapadi", "varmala", "jaimala", "sindoor", "sindur",
                "mangalsutra", "lagan", "lagna", "anand karaj", "lavaan", "havan", "baraat", "biye", "bibaho",
                "kalyanam", "kalyana", "talambralu", "mangalya", "saat paak", "shubho drishti", "mala badal",
                "sampradan", "hast melap", "mangalashtak", "adhibas", "adhibaas", "adhivas") -> EventKind.RITUAL
            else -> EventKind.OTHER
        }
    }

    private fun judge(
        p: PlanPhoto,
        kind: EventKind,
        event: String,
        norms: Norms,
        tags: PeopleTags,
        personPhotos: Map<Long, Int>,
    ): Judgement {
        if (p.failed) {
            return Judgement(setOf(Issue.UNREADABLE), 0.0, ShotType.DETAIL, emptySet(), false, false, false, false, false)
        }
        val issues = mutableSetOf<Issue>()
        val scene = norms.sceneFace[p.id] ?: 1.0
        val scenePeak = norms.scenePeak[p.id] ?: 1.0

        // Strangers walking through: someone barely in the wedding, not posing, blurred or cut off,
        // next to people who are in it a lot. Their face is then left out of the face checks.
        val frequentPresent = p.faces.any { f ->
            f.box.width >= VISIBLE_FACE && f.personId != null &&
                (tags.isCouple(f.personId) || (personPhotos[f.personId] ?: 0) >= FREQUENT_PERSON)
        }
        val strangers = p.faces.filter { f ->
            f.box.width >= CLEAR_FACE && !tags.isCouple(f.personId) &&
                (f.personId == null || (personPhotos[f.personId] ?: 0) <= RARE_PERSON) &&
                !f.lookingAtCamera && frequentPresent &&
                (relativeSharpness(f, norms, scene) < 0.5 || f.cutOff >= 0.05f)
        }.toSet()
        if (strangers.isNotEmpty()) issues += Issue.STRANGER

        val faces = p.faces.filter { it !in strangers }
        // Clear faces are the photo's subjects: the main people, named close family, or faces near
        // the size and sharpness of the biggest one (not a small soft face at the back).
        val largest = faces.maxOfOrNull { it.box.width } ?: 0f
        val sharpestSeen = faces.maxOfOrNull { it.sharpness } ?: 0.0
        val clear = faces.filter { f ->
            f.box.width >= CLEAR_FACE && (tags.isCouple(f.personId) ||
                f.personId?.let { tags.tag(it).let { t -> !t.guessed && t.priority in setOf(Priority.MUST, Priority.IMPORTANT) } } == true ||
                (f.box.width >= 0.45f * largest && f.sharpness >= 0.35 * sharpestSeen))
        }
        val visible = faces.filter { it.box.width >= VISIBLE_FACE }
        val people = visible.mapNotNull { it.personId }.toSet()

        // Subject sharpness: the faces when there are any, else the sharpest part of the photo.
        if (clear.isNotEmpty()) {
            if (clear.maxOf { relativeSharpness(it, norms, scene) } < BLUR_FACTOR) issues += Issue.BLURRY
        } else if (norms.peakSharpness > 0 && p.peakSharpness < BLUR_FACTOR * norms.peakSharpness * scenePeak) {
            issues += Issue.BLURRY
        }

        // Light, judged against this event's own lighting (stage lights, night functions), and
        // against its own scene when that is darker (the night part of a day-long folder).
        val evBright = norms.eventBrightness[event] ?: 128.0
        val lightRef = min(evBright, norms.sceneBrightness[p.id] ?: evBright)
        val darkLimit = min(DARK_BRIGHTNESS, 0.45 * lightRef)
        val darkShareLimit = max(DARK_SHARE, max(norms.eventDark[event] ?: 0.0, norms.sceneDark[p.id] ?: 0.0) + 0.25)
        val blownLimit = max(BLOWN_SHARE, (norms.eventBlown[event] ?: 0.0) + 0.25)
        if (p.brightness < darkLimit || p.darkFraction > darkShareLimit) issues += Issue.TOO_DARK
        val brightLimit = max(BRIGHT_BRIGHTNESS, evBright + 60.0)
        if (p.brightness > brightLimit || p.brightFraction > blownLimit) issues += Issue.TOO_BRIGHT

        // Context.
        val lookingShare = if (clear.isEmpty()) 0.0 else clear.count { it.lookingAtCamera }.toDouble() / clear.size
        val posed = clear.isNotEmpty() && lookingShare >= 0.5
        val interaction = clear.size >= 2 && (facingEachOther(clear) || clear.any { it.expression == Expression.LAUGH })
        val ritual = kind == EventKind.RITUAL && !(clear.size >= 3 && lookingShare >= 0.6)
        val vidaai = kind == EventKind.VIDAAI
        // Kept low on purpose: darker skin is not a shadow.
        val faceDarkLimit = min(45.0, 0.4 * (norms.eventFaceBrightness[event]?.takeIf { it > 0 } ?: 130.0))

        val faceScores = HashMap<PlanFace, Double>()
        val sharpestFace = clear.maxOfOrNull { it.sharpness } ?: 0.0
        for (f in clear) {
            val lenient = vidaai || f.expression == Expression.EMOTIONAL
            val laughingCandid = f.expression == Expression.LAUGH && interaction
            var eyes = 1.0
            when (f.eyes) {
                EyeState.CLOSED -> if (lenient) eyes = 0.85 else if (laughingCandid) eyes = 0.8 else {
                    issues += Issue.EYES_CLOSED; eyes = 0.1
                }
                EyeState.HALF -> if (lenient) eyes = 0.9 else { issues += Issue.HALF_BLINK; eyes = 0.45 }
                EyeState.LOOKING_DOWN -> eyes = if (ritual || lenient) 1.0 else 0.8
                EyeState.UNKNOWN -> eyes = 0.9
                EyeState.OPEN -> {}
            }
            var attention = 1.0
            if (!f.lookingAtCamera) {
                // A small cost only: people keep a good group photo with one person glancing away.
                if (posed && !interaction && !ritual && !vidaai) { issues += Issue.NOT_LOOKING; attention = 0.85 }
                else attention = 0.95
            }
            val expr = when (f.expression) {
                Expression.SMILE, Expression.LAUGH, Expression.EMOTIONAL -> 1.0
                Expression.NATURAL -> if (ritual || vidaai) 1.0 else 0.8
                Expression.MID_TALK -> when {
                    vidaai -> 0.9
                    ritual -> { issues += Issue.MID_TALK; 0.65 }
                    else -> { issues += Issue.MID_TALK; 0.4 }
                }
            }
            val sharp = min(relativeSharpness(f, norms, scene), 1.5) / 1.5
            var exposure = 1.0
            if (f.backlit || f.brightness < faceDarkLimit || f.brightness > 235.0) { issues += Issue.FACE_DARK; exposure = 0.6 }
            var visibility = 1.0
            // Someone out of focus at the edge of a crowd is background; the photographer focused on
            // the subject. A cut-off face counts when it is the couple's or as sharp as the subject.
            val subject = tags.isCouple(f.personId) || f.sharpness >= SUBJECT_SHARPNESS * sharpestFace
            if (f.cutOff >= CUT_OFF && subject && !foreheadCrop(f)) { issues += Issue.FACE_CUT_OFF; visibility *= 0.3 }
            val blockedByFace = p.faces.any { g ->
                g !== f && g.box.area > f.box.area && f.box.area > 0 && g.box.intersection(f.box) / f.box.area >= BLOCKED_OVERLAP
            }
            val blockedByPhone = p.phones.any { ph -> f.box.area > 0 && ph.intersection(f.box) / f.box.area >= 0.15f }
            if (blockedByFace || blockedByPhone) { issues += Issue.FACE_BLOCKED; visibility *= 0.3 }
            else if (f.occlusion >= HIDDEN) { issues += Issue.FACE_PARTLY_HIDDEN; visibility *= 0.75 }
            faceScores[f] = eyes * attention * expr * (0.6 + 0.4 * sharp) * exposure * visibility
        }

        val loosePhones = p.phones.filter { ph ->
            ph.area >= 0.002f && clear.none { f -> f.box.area > 0 && ph.intersection(f.box) / f.box.area >= 0.15f }
        }
        if (loosePhones.isNotEmpty()) issues += Issue.PHONE_IN_SHOT
        if (p.foregroundBacks > 0) issues += Issue.BACK_OF_GUEST
        // People fill the photo but no face can be seen: backs, not a detail shot.
        val turnedAway = visible.isEmpty() && p.peopleArea >= PEOPLE_NOT_DETAIL
        if (turnedAway) issues += Issue.FACES_TURNED_AWAY
        if (abs(p.tilt) in MIN_TILT..MAX_TILT) issues += Issue.TILTED

        // Quality: a group is as good as its worst face.
        val targetLight = lightRef.coerceIn(70.0, 150.0)
        val light = 1.0 - min(1.0, abs(p.brightness - targetLight) / 128.0)
        val clipping = (1.0 - p.darkFraction - p.brightFraction).coerceIn(0.0, 1.0)
        var quality = if (faceScores.isNotEmpty()) {
            val worst = faceScores.values.min()
            val mean = faceScores.values.average()
            var q = 0.65 * worst + 0.35 * mean
            // The bride's face must look good in every photo she is in.
            clear.firstOrNull { it.personId != null && it.personId == tags.brideId }?.let { q *= 0.75 + 0.25 * faceScores.getValue(it) }
            q * (0.8 + 0.2 * light * clipping)
        } else {
            val sharp = if (norms.peakSharpness > 0) min(p.peakSharpness / (norms.peakSharpness * scenePeak), 1.5) / 1.5 else 0.7
            0.5 * sharp + 0.5 * light * clipping
        }
        if (Issue.TILTED in issues) quality *= 0.85
        if (Issue.PHONE_IN_SHOT in issues) quality *= 0.6
        if (Issue.STRANGER in issues) quality *= 0.6
        if (turnedAway) quality *= 0.5

        val bride = visible.firstOrNull { it.personId != null && it.personId == tags.brideId }
        val groom = visible.firstOrNull { it.personId != null && it.personId == tags.groomId }
        val coupleLooking = bride != null && groom != null && bride.box.width >= CLEAR_FACE &&
            groom.box.width >= CLEAR_FACE && facingEachOther(listOf(bride, groom))

        val shot = if (turnedAway) ShotType.CANDID else shotType(visible, clear, bride, groom, lookingShare)
        val emotional = clear.any { it.expression == Expression.EMOTIONAL }
        val allGood = clear.size >= 2 && clear.all { it.eyes == EyeState.OPEN && it.lookingAtCamera }
        return Judgement(issues, quality, shot, people, coupleLooking, ritual, interaction, emotional, allGood)
    }

    /** This face's sharpness compared with faces of a similar size in this wedding (1 = typical). */
    /**
     * Only the top of the head is outside the photo: tight framing (a close portrait, an adult
     * holding the birthday child), not a face cut in half at the side.
     */
    fun foreheadCrop(f: PlanFace): Boolean =
        f.cutOff < FOREHEAD_CROP && f.box.top <= 0.02f && f.box.left > 0.02f && f.box.right < 0.98f && f.box.bottom < 0.98f

    private fun relativeSharpness(f: PlanFace, norms: Norms, scene: Double = 1.0): Double {
        val norm = norms.faceSharpnessBySize[sizeBucket(f.box.width)] ?: norms.faceSharpness
        return if (norm > 0) f.sharpness / norm / scene else 1.0
    }

    /** Two of these faces turned toward each other (people talking, laughing, the couple's candid). */
    fun facingEachOther(faces: List<PlanFace>): Boolean {
        for (a in faces) for (b in faces) {
            if (a === b || a.box.centerX >= b.box.centerX) continue
            if (a.directionX >= 0.25f && b.directionX <= -0.25f && !a.lookingAtCamera && !b.lookingAtCamera) return true
        }
        return false
    }

    private fun shotType(visible: List<PlanFace>, clear: List<PlanFace>, bride: PlanFace?, groom: PlanFace?, lookingShare: Double): ShotType {
        if (visible.isEmpty()) return ShotType.DETAIL
        if (bride != null && groom != null && clear.size <= 3) {
            val w = (bride.box.width + groom.box.width) / 2f
            return if (w >= CLOSE_UP_FACE) ShotType.COUPLE_CLOSE else ShotType.COUPLE_FULL
        }
        val clearOthers = clear.count { it !== bride && it !== groom }
        if (bride != null && groom == null && clearOthers == 0) return ShotType.BRIDE_SOLO
        if (groom != null && bride == null && clearOthers == 0) return ShotType.GROOM_SOLO
        if (clear.size >= 3 && lookingShare >= 0.6) return ShotType.GROUP
        if (clear.size == 1 && clear[0].lookingAtCamera) return ShotType.GUEST
        return ShotType.CANDID
    }

    // ---- Events ----

    /** Event name for every photo id: the photographer's sub-folders, as named. */
    fun assignEvents(photos: List<PlanPhoto>): Map<Long, String> {
        if (photos.isEmpty()) return emptyMap()
        val split = photos.map { it.folder.split('/').filter(String::isNotEmpty) }
        val common = split.reduce { acc, s -> acc.zip(s).takeWhile { it.first == it.second }.map { it.first } }
        val top = photos.indices.associate { i -> photos[i].id to split[i].getOrNull(common.size) }
        if (top.values.distinct().size >= 2) return top.mapValues { it.value ?: OTHER_PHOTOS }
        val name = common.lastOrNull() ?: ALL_PHOTOS
        return photos.associate { it.id to name }
    }

    /**
     * The photographer's order: numbered folders ("01 Haldi") by number; otherwise by when each
     * event started; events without capture times last, by name.
     */
    fun orderEvents(photos: List<PlanPhoto>, eventOf: Map<Long, String>): List<String> {
        val names = photos.map { eventOf.getValue(it.id) }.distinct()
        val number = names.associateWith { n -> Regex("^\\s*(\\d+)").find(n)?.groupValues?.get(1)?.toLongOrNull() }
        val numbered = names.filter { it != OTHER_PHOTOS }.let { real -> real.isNotEmpty() && real.all { number[it] != null } }
        if (numbered) {
            return names.sortedWith(compareBy<String> { if (it == OTHER_PHOTOS) 1 else 0 }.thenBy { number[it] ?: Long.MAX_VALUE }.thenBy { it })
        }
        val first = HashMap<String, Long>()
        for (p in photos) {
            val e = eventOf.getValue(p.id)
            val t = if (p.takenAt > 0) p.takenAt else Long.MAX_VALUE
            first[e] = min(first[e] ?: Long.MAX_VALUE, t)
        }
        return names.sortedWith(
            compareBy<String> { first.getValue(it) }.thenBy { if (it == OTHER_PHOTOS) 1 else 0 }.thenBy { it.lowercase() }
        )
    }

    // ---- Moments ----

    /**
     * Cuts an event into moments: runs of photos with no long pause. The pause that counts is
     * relative to how fast this photographer shoots. Moments with far more photos than usual
     * are key moments (the photographer kept shooting because something important happened).
     */
    fun findMoments(event: String, photos: List<PlanPhoto>, firstId: Int): List<Moment> {
        val timed = photos.filter { it.takenAt > 0 }.sortedWith(compareBy({ it.takenAt }, { it.name }))
        val untimed = photos.filter { it.takenAt <= 0 }.sortedBy { it.name }
        val groups = ArrayList<List<PlanPhoto>>()
        if (timed.isNotEmpty()) {
            val gaps = timed.zipWithNext { a, b -> b.takenAt - a.takenAt }
            val limit = (2.5 * median(gaps.map { it.toDouble() })).toLong().coerceIn(MIN_MOMENT_GAP, MAX_MOMENT_GAP)
            var current = ArrayList<PlanPhoto>()
            for (p in timed) {
                if (current.isNotEmpty() && p.takenAt - current.last().takenAt > limit) { groups += current; current = ArrayList() }
                current += p
            }
            groups += current
        }
        untimed.chunked(UNTIMED_MOMENT).forEach { groups += it }
        val typical = median(groups.map { it.size.toDouble() })
        val keySize = max(8.0, 2.0 * typical)
        return groups.mapIndexed { i, g ->
            Moment(
                id = firstId + i,
                event = event,
                photoIds = g.map { it.id },
                start = g.first().takenAt,
                end = g.last().takenAt,
                key = g.size >= keySize,
            )
        }
    }

    // ---- Repeats ----

    private class RepeatGroup(val members: List<PlanPhoto>, val isGroupPhoto: Boolean)

    /**
     * Bursts (back-to-back within 3 s, similar picture), near-identical repeats (almost the same
     * picture within a minute), and repeated stage photos of the same family group.
     * [inEvent] must be one event's photos in capture order.
     */
    private fun findRepeats(inEvent: List<PlanPhoto>, judged: Map<Long, Judgement>, tags: PeopleTags): List<RepeatGroup> {
        val timed = inEvent.filter { it.takenAt > 0 }
        val untimed = inEvent.filter { it.takenAt <= 0 }.sortedBy { it.name }
        val all = timed + untimed
        val index = all.withIndex().associate { it.value.id to it.index }
        val parent = IntArray(all.size) { it }
        val groupLink = BooleanArray(all.size)
        fun root(i: Int): Int { var r = i; while (parent[r] != r) r = parent[r]; return r }
        fun union(a: Int, b: Int, group: Boolean) {
            val ra = root(a); val rb = root(b)
            if (ra != rb) { parent[rb] = ra; groupLink[ra] = groupLink[ra] || groupLink[rb] || group }
            else if (group) groupLink[ra] = true
        }
        fun clearCount(p: PlanPhoto) = p.faces.count { it.box.width >= CLEAR_FACE }
        fun others(p: PlanPhoto) = judged.getValue(p.id).people.filter { !tags.isCouple(it) }.toSet()

        for ((seq, useTime) in listOf(timed to true, untimed to false)) {
            for (i in seq.indices) {
                val a = seq[i]
                for (j in i + 1 until min(seq.size, i + 25)) {
                    val b = seq[j]
                    val dt = if (useTime) b.takenAt - a.takenAt else (j - i).toLong()
                    if (useTime && dt > GROUP_REPEAT_MILLIS) break
                    val ham = hamming(a.hash, b.hash)
                    val ia = index.getValue(a.id); val ib = index.getValue(b.id)
                    val burst = j == i + 1 && ham <= BURST_HASH_DISTANCE && (!useTime || dt <= BURST_GAP_MILLIS)
                    val sameShot = ham <= SAME_SHOT_HASH && (if (useTime) dt <= SAME_SHOT_MILLIS else dt <= 6)
                    if (burst || sameShot) { union(ia, ib, false); continue }
                    val ca = clearCount(a); val cb = clearCount(b)
                    if (ca >= 3 && cb >= 3 && abs(ca - cb) <= 1 && (useTime || dt <= 20)) {
                        val oa = others(a); val ob = others(b)
                        val samePeople = oa.isNotEmpty() && ob.isNotEmpty() &&
                            oa.intersect(ob).size.toDouble() / oa.union(ob).size >= GROUP_SAME_PEOPLE
                        if (samePeople || ham <= GROUP_HASH) union(ia, ib, true)
                    }
                }
            }
        }
        return all.indices.groupBy { root(it) }.values.filter { it.size >= 2 }.map { members ->
            RepeatGroup(members.map { all[it] }, groupLink[root(members.first())])
        }
    }

    // =====================================================================================
    // Choosing
    // =====================================================================================

    /**
     * Picks [size] photos.
     * - [removed]: photos the user took out; never picked again.
     * - [keep]: photos already on the shortlist that must stay (used by "Replace removed").
     */
    fun shortlist(
        analysis: AlbumAnalysis,
        size: Int,
        removed: Set<Long> = emptySet(),
        keep: Set<Long> = emptySet(),
    ): Shortlist = Selector(analysis, size, removed, keep).run()

    /** A photo that could take another's place, and in plain words why it wasn't picked. */
    data class Alternative(val photoId: Long, val note: String)

    /**
     * Look-alikes of [photoId] to swap with: first the same burst or repeated shot, then the same
     * moment with the same people, then the same moment and kind of shot. Best first.
     * [exclude] are photos already in the album or removed by the couple.
     */
    fun alternatives(analysis: AlbumAnalysis, photoId: Long, exclude: Set<Long>, limit: Int = 6): List<Alternative> {
        val p = analysis.byId[photoId] ?: return emptyList()
        fun closeness(c: AssessedPhoto): Int = when {
            p.repeatGroup >= 0 && c.repeatGroup == p.repeatGroup -> 0
            c.momentId == p.momentId && c.people == p.people && c.people.isNotEmpty() -> 1
            c.momentId == p.momentId && c.shot == p.shot -> 2
            else -> 9
        }
        return analysis.photos.asSequence()
            .filter { it.id != photoId && it.id !in exclude && Issue.UNREADABLE !in it.issues }
            .map { it to closeness(it) }
            .filter { it.second < 9 }
            .sortedWith(compareBy<Pair<AssessedPhoto, Int>> { it.second }.thenByDescending { it.first.quality })
            .take(limit)
            .map { (c, _) -> Alternative(c.id, whyNot(c, p)) }
            .toList()
    }

    /** Why [c] lost to [picked], in a few words. */
    fun whyNot(c: AssessedPhoto, picked: AssessedPhoto): String {
        val problem = (c.issues.filter { it.hard && it != Issue.DUPLICATE && it != Issue.GROUP_REPEAT } +
            c.issues.filter { !it.hard }).firstOrNull()
        return when {
            problem != null -> problem.label
            c.quality >= picked.quality - 0.02 -> "Just as good"
            else -> "Fine, slightly less good"
        }
    }

    fun target(tag: PersonTag): Int = tag.wanted ?: when (tag.priority) {
        Priority.MUST -> if (tag.guessed) GUESSED_MUST_TARGET else MUST_TARGET
        Priority.IMPORTANT -> if (tag.guessed) GUESSED_IMPORTANT_TARGET else IMPORTANT_TARGET
        else -> 0
    }

    /** Most photos one non-couple person should be in, so nobody dominates the album. */
    fun cap(tag: PersonTag, size: Int): Int = tag.wanted ?: when (tag.priority) {
        Priority.MUST -> max(2 * MUST_TARGET, ceil(0.06 * size).toInt())
        Priority.IMPORTANT -> max(4, ceil(0.04 * size).toInt())
        Priority.NORMAL -> max(3, ceil(0.03 * size).toInt())
        Priority.SKIP -> 2
    }

    /** Coverage of every Must and Important person for a given list of picks. */
    fun coverage(analysis: AlbumAnalysis, picked: Collection<Long>, usedPoorPhoto: Set<Long> = emptySet()): List<Coverage> {
        val tags = analysis.tags
        val pickedPhotos = picked.mapNotNull { analysis.byId[it] }
        return tags.tags.values
            .filter { it.priority == Priority.MUST || it.priority == Priority.IMPORTANT }
            .filter { !tags.isCouple(it.id) }
            .sortedWith(compareBy<PersonTag> { it.priority }.thenByDescending { it.elder }.thenBy { it.name })
            .map { t ->
                val count = pickedPhotos.count { t.id in it.people }
                val target = target(t)
                val good = analysis.photos.count { it.usable && t.id in it.people && withCouple(it, tags) }
                val anyPhoto = analysis.photos.any { t.id in it.people && Issue.UNREADABLE !in it.issues }
                val warning = when {
                    count >= target || t.guessed -> null
                    !anyPhoto -> "Not found in any photo"
                    t.id in usedPoorPhoto -> "Only poor photos found. We used the best one"
                    good < target -> "Only $good good photo${if (good == 1) "" else "s"} with ${if (tags.groomId != null) tags.pair() else tags.who(tags.brideId ?: 0)}"
                    else -> "Below the minimum of $target"
                }
                Coverage(t.id, tags.label(t.id), t.priority, t.side, t.elder, count, target, good, warning)
            }
    }

    private fun withCouple(p: AssessedPhoto, tags: PeopleTags): Boolean =
        (tags.brideId != null && tags.brideId in p.people) || (tags.groomId != null && tags.groomId in p.people)

    private class Selector(
        val a: AlbumAnalysis,
        val size: Int,
        val removed: Set<Long>,
        keep: Set<Long>,
    ) {
        val tags = a.tags
        val picked = LinkedHashSet<Long>()
        val reasons = HashMap<Long, MutableList<String>>()
        val personCount = HashMap<Long, Int>()
        val sideCount = HashMap<Side, Int>()
        val eventCount = HashMap<String, Int>()
        val typeCount = HashMap<Pair<String, ShotType>, Int>()
        val portraitCount = HashMap<String, Int>()
        val momentPicks = HashMap<Int, MutableList<AssessedPhoto>>()
        val usedPoor = HashSet<Long>()
        var coverId: Long? = null

        val clean: List<AssessedPhoto> = a.photos.filter { it.usable && it.id !in removed }
        val quotas: Map<String, Int>

        init {
            keep.filter { it !in removed }.mapNotNull { a.byId[it] }
                .filter { Issue.UNREADABLE !in it.issues }
                .forEach { add(it, null) }
            val usableCounts = a.events.associateWith { e -> a.photos.count { it.event == e && it.usable } }
            quotas = allocate(usableCounts, size)
        }

        fun room() = size - picked.size

        fun add(p: AssessedPhoto, reason: String?) {
            if (!picked.add(p.id)) return
            if (reason != null) reasons.getOrPut(p.id) { mutableListOf() } += reason
            for (person in p.people) {
                personCount[person] = (personCount[person] ?: 0) + 1
                val side = tags.tag(person).side
                if (side != Side.UNKNOWN && !tags.isCouple(person)) sideCount[side] = (sideCount[side] ?: 0) + 1
            }
            eventCount[p.event] = (eventCount[p.event] ?: 0) + 1
            typeCount[p.event to p.shot] = (typeCount[p.event to p.shot] ?: 0) + 1
            if (p.photo.portrait) portraitCount[p.event] = (portraitCount[p.event] ?: 0) + 1
            momentPicks.getOrPut(p.momentId) { mutableListOf() } += p
        }

        fun available(p: AssessedPhoto) = p.id !in picked && p.id !in removed

        fun run(): Shortlist {
            if (size <= 0) return Shortlist(emptyList(), emptyMap(), null, emptyList())
            pickCover()
            pickCouple()
            pickParents()
            pickPeople()
            pickStory()
            fillRest()
            val ids = picked.sortedBy { a.byId.getValue(it).order }
            for (id in ids) describe(a.byId.getValue(id))
            return Shortlist(ids, reasons, coverId, coverage(a, ids, usedPoor))
        }

        // ---- 1. People: the couple ----

        /**
         * The cover: the best portrait of the couple, in wedding or reception clothes when there is
         * one; otherwise the bride's best portrait from the wedding.
         */
        fun pickCover() {
            if (room() <= 0 || (tags.brideId == null && tags.groomId == null)) return
            fun dressed(p: AssessedPhoto) = when (a.eventKinds[p.event]) {
                EventKind.RITUAL, EventKind.PARTY -> 1.0
                EventKind.PREP -> 0.75
                else -> 0.9
            }
            fun pool(ok: (AssessedPhoto) -> Boolean) =
                (clean + picked.mapNotNull { a.byId[it] }).filter { ok(it) && it.id !in removed }.distinctBy { it.id }
            val couple = pool { isCoverCandidate(it) }
                .maxByOrNull { it.quality * dressed(it) * (if (it.shot == ShotType.COUPLE_CLOSE) 1.1 else 1.0) }
            val (best, reason) = when {
                couple != null -> couple to "Cover: the best portrait of ${tags.pair()}"
                else -> (pool { it.usable && it.shot == ShotType.BRIDE_SOLO }
                    .maxByOrNull { it.quality * dressed(it) } ?: return) to "Cover: ${tags.who(tags.brideId ?: 0)}'s best portrait"
            }
            coverId = best.id
            if (best.id in picked) reasons.getOrPut(best.id) { mutableListOf() }.add(0, reason)
            else add(best, reason)
        }

        fun isCoverCandidate(p: AssessedPhoto): Boolean =
            p.usable && (p.shot == ShotType.COUPLE_CLOSE || p.shot == ShotType.COUPLE_FULL) &&
                (p.allEyesOpenAndLooking || p.coupleLookingAtEachOther)

        fun pickCouple() {
            if (tags.brideId == null && tags.groomId == null) return
            for (event in a.events) {
                val quota = quotas[event] ?: 0
                val inEvent = clean.filter { it.event == event && available(it) }
                // Solo portraits of the bride and the groom.
                val solos = if (quota >= 6) listOf(ShotType.BRIDE_SOLO, ShotType.GROOM_SOLO) else emptyList()
                for (type in solos) {
                    if (room() <= 0) return
                    if ((typeCount[event to type] ?: 0) > 0) continue
                    inEvent.filter { it.shot == type && available(it) }.maxByOrNull { it.quality }
                        ?.let { add(it, portraitOf(type)) }
                }
                // The best candid of the couple looking at each other.
                if (quota >= 8 && room() > 0 && inEvent.none { it.id in picked && it.coupleLookingAtEachOther }) {
                    inEvent.filter { it.coupleLookingAtEachOther && available(it) }.maxByOrNull { it.quality }
                        ?.let { add(it, lookingAtEachOther()) }
                }
            }
            // At least one solo portrait of each overall, even in small albums.
            for (type in listOf(ShotType.BRIDE_SOLO, ShotType.GROOM_SOLO)) {
                if (room() <= 0) return
                if (picked.any { a.byId.getValue(it).shot == type }) continue
                clean.filter { it.shot == type && available(it) }.maxByOrNull { it.quality }
                    ?.let { add(it, portraitOf(type)) }
            }
        }

        fun portraitOf(type: ShotType): String =
            "Portrait of " + tags.who((if (type == ShotType.BRIDE_SOLO) tags.brideId else tags.groomId) ?: 0)

        fun lookingAtEachOther(): String = tags.pair().capitalized() + " looking at each other"

        /**
         * A child's album (a birthday, a baby's function): the child with mummy and papa together,
         * once in every big event and at least once overall. Few others in it: a family photo.
         */
        fun pickParents() {
            val star = tags.brideId ?: return
            if (!tags.occasion.child || tags.parents.size < 2) return
            fun together(p: AssessedPhoto) = star in p.people && tags.parents.all { it in p.people }
            fun best(ok: (AssessedPhoto) -> Boolean) =
                clean.filter { available(it) && together(it) && ok(it) }.maxByOrNull { it.quality - 0.05 * (it.people.size - 3) }
            for (event in a.events) {
                if (room() <= 0) return
                if ((quotas[event] ?: 0) < 8 || picked.any { id -> a.byId.getValue(id).let { it.event == event && together(it) } }) continue
                best { it.event == event }?.let { add(it, familyPhoto()) }
            }
            if (room() > 0 && picked.none { together(a.byId.getValue(it)) }) best { true }?.let { add(it, familyPhoto()) }
        }

        /** "Family photo: the birthday star with mummy and papa", or with their names when given. */
        fun familyPhoto(): String {
            val names = tags.parents.mapNotNull { tags.tags[it]?.name?.takeIf { n -> n.isNotBlank() } }
            val who = if (names.size == 2) "${names[0]} and ${names[1]}" else "mummy and papa"
            return "Family photo: ${tags.who(tags.brideId!!)} with $who"
        }

        // ---- 1. People: the family ----

        fun weight(t: PersonTag): Double = if ((t.wanted ?: 0) > 0) 3.0 else when (t.priority) {
            Priority.MUST -> (if (t.elder) 3.0 else 2.5) * (if (t.guessed) 0.6 else 1.0)
            Priority.IMPORTANT -> (if (t.elder) 1.6 else 1.2) * (if (t.guessed) 0.6 else 1.0)
            else -> 0.0
        }

        fun need(id: Long): Int = target(tags.tag(id)) - (personCount[id] ?: 0)

        fun pickPeople() {
            val wanted = tags.tags.values.filter { target(it) > 0 && !tags.isCouple(it.id) }
                .sortedWith(compareByDescending<PersonTag> { weight(it) })
            if (wanted.isEmpty()) return
            val budget = max(1, size / 2)
            var used = 0

            // Photos with the couple, covering as many under-covered people as possible at once.
            while (room() > 0 && used < budget) {
                val needy = wanted.filter { need(it.id) > 0 }.map { it.id }.toSet()
                if (needy.isEmpty()) break
                val best = clean.filter { available(it) && withCouple(it, tags) && it.people.any { p -> p in needy } }
                    .maxByOrNull { c -> gain(c, needy) } ?: break
                add(best, peopleReason(best, needy))
                used++
            }
            // Still short: their best clean photos without the couple, then their best poor one.
            for (t in wanted) {
                while (need(t.id) > 0 && room() > 0 && used < budget) {
                    val c = clean.filter { available(it) && t.id in it.people }.maxByOrNull { it.quality } ?: break
                    add(c, peopleReason(c, setOf(t.id))); used++
                }
                val hasClean = clean.any { t.id in it.people }
                if ((personCount[t.id] ?: 0) == 0 && !hasClean && room() > 0) {
                    val poor = a.photos.filter { available(it) && t.id in it.people && Issue.UNREADABLE !in it.issues }
                        .sortedWith(compareBy<AssessedPhoto> { it.hardIssues.size }.thenByDescending { it.quality })
                        .firstOrNull() ?: continue
                    usedPoor += t.id
                    add(poor, "${tags.label(t.id)}: only poor photos found, this is the best one")
                    used++
                }
            }
        }

        fun gain(c: AssessedPhoto, needy: Set<Long>): Double {
            var g = 0.0
            for (p in c.people) if (p in needy) g += weight(tags.tag(p))
            val both = tags.brideId in c.people && tags.groomId in c.people
            return g + 0.5 * c.quality + (if (both) 0.2 else 0.0) + sideBonus(c)
        }

        /** In plain words: "Aai and Baba with the couple", "Family elder with the bride", "Nani with the birthday star". */
        fun peopleReason(c: AssessedPhoto, needy: Set<Long>): String {
            // A child's parents the family has not named yet: "Mummy and papa", or "Parent".
            val unnamedParents = c.people.filter { it in needy && it in tags.parents && tags.tag(it).name.isBlank() }
            val who = c.people.filter { it in needy }.map { id ->
                val t = tags.tag(id)
                t.name.takeIf { it.isNotBlank() } ?: when {
                    id in unnamedParents -> if (unnamedParents.size == 2) "Mummy and papa" else "Parent"
                    t.elder -> "Family elder"
                    else -> "Family"
                }
            }.distinct()
            val names = when (who.size) {
                0 -> "Family"
                1 -> who[0]
                else -> who.dropLast(1).joinToString(", ") + " and " + who.last()
            }
            val bride = tags.brideId != null && tags.brideId in c.people
            val groom = tags.groomId != null && tags.groomId in c.people
            val with = when {
                bride && groom -> " with ${tags.pair()}"
                bride -> " with ${tags.who(tags.brideId!!)}"
                groom -> " with ${tags.who(tags.groomId!!)}"
                else -> ""
            }
            return names + with
        }

        // ---- 2. Moments: the story of each event ----

        fun pickStory() {
            val remaining = room()
            if (remaining <= 0) return
            val left = a.events.associateWith { e -> max(0, (quotas[e] ?: 0) - (eventCount[e] ?: 0)) }
            val share = if (left.values.sum() > remaining) allocate(left, remaining, atLeastOne = false) else left
            for (event in a.events) {
                var budget = share[event] ?: 0
                if (budget <= 0) continue
                val eventTarget = (eventCount[event] ?: 0) + budget
                budget -= pickDetails(event, budget)
                if (budget <= 0) continue
                val moments = a.moments.filter { it.event == event }
                val plan = planMoments(moments, budget)
                val got = HashMap<Int, Int>()
                // One choice at a time: moments still short of their share get a strong bonus,
                // so the story comes before variety and quality, but people rules still apply.
                while ((eventCount[event] ?: 0) < eventTarget && room() > 0) {
                    val c = clean.filter { it.event == event && available(it) }.maxByOrNull { p ->
                        value(p) + (if ((got[p.momentId] ?: 0) < (plan[p.momentId] ?: 0)) MOMENT_BONUS else 0.0)
                    } ?: break
                    val m = a.momentById[c.momentId]
                    val planned = (got[c.momentId] ?: 0) < (plan[c.momentId] ?: 0)
                    got[c.momentId] = (got[c.momentId] ?: 0) + 1
                    add(c, if (m != null && planned) momentReason(m, moments) else if (single(event)) "Adds variety" else "Adds variety to ${Display.eventName(event)}")
                }
            }
        }

    /** About 10% of each event (a quarter on a trip): no faces (mehendi hands, jewellery, decor, food, places). */
        fun pickDetails(event: String, budget: Int): Int {
            val quota = quotas[event] ?: 0
            val want = (quota * tags.occasion.detailShare).roundToInt() - (typeCount[event to ShotType.DETAIL] ?: 0)
            var n = 0
            val details = clean.filter { it.event == event && it.shot == ShotType.DETAIL }
            while (n < min(want, budget) && room() > 0) {
                // Spread across the event: prefer moments that have no detail yet.
                val c = details.filter { available(it) }.maxByOrNull { d ->
                    val hasDetail = momentPicks[d.momentId]?.any { it.shot == ShotType.DETAIL } == true
                    d.quality - (if (hasDetail) 0.5 else 0.0)
                } ?: break
                add(c, if (tags.occasion == Occasion.TRIP) "The place, for variety (no faces)" else "Detail shot for variety (no faces)")
                n++
            }
            return n
        }

        /**
         * Shares an event's places across its moments: first one each for the key moments,
         * the opening and the close, then the rest by moment size (more photos, more picks).
         */
        fun planMoments(moments: List<Moment>, budget: Int): Map<Int, Int> {
            val plan = HashMap<Int, Int>()
            val cleanIn = moments.associate { m -> m.id to clean.count { it.momentId == m.id && available(it) } }
            var left = budget
            val anchors = moments.filter { it.key }.sortedByDescending { it.photoIds.size } +
                listOfNotNull(moments.firstOrNull(), moments.lastOrNull()) +
                moments.sortedByDescending { it.photoIds.size }
            for (m in anchors.distinctBy { it.id }) {
                if (left <= 0) break
                if ((momentPicks[m.id]?.size ?: 0) > 0 || (cleanIn[m.id] ?: 0) == 0) continue
                plan[m.id] = 1
                left--
            }
            if (left > 0) {
                // Bigger moments get more, but gently: 23 photos of one portrait session should not
                // take half the event's places from the rituals around it.
                val weights = moments.associate { m -> m.id.toString() to sqrt(m.photoIds.size.toDouble()) }
                val ideal = weights.mapValues { (k, w) -> left * w / weights.values.sum() }
                val extra = ideal.mapValues { floor(it.value).toInt() }.toMutableMap()
                var rest = left - extra.values.sum()
                ideal.entries.sortedByDescending { it.value - floor(it.value) }.forEach { if (rest > 0) { extra[it.key] = extra.getValue(it.key) + 1; rest-- } }
                for ((k, v) in extra) {
                    val id = k.toInt()
                    val cap = (cleanIn[id] ?: 0) - (plan[id] ?: 0)
                    plan[id] = (plan[id] ?: 0) + min(v, max(0, cap))
                }
            }
            return plan
        }

        /** The whole folder is one event (no sub-folders): no event name in the reasons. */
        fun single(event: String) = a.events.size == 1

        fun momentReason(m: Moment, moments: List<Moment>): String {
            val minutes = ((m.end - m.start) / 60_000L).toInt()
            return when {
                m.key && m.start > 0 -> "Big moment: ${m.photoIds.size} photos taken in ${max(1, minutes)} min"
                m.key -> "Big moment: ${m.photoIds.size} photos taken together"
                m.id == moments.first().id -> if (single(m.event)) "The start" else "Start of ${Display.eventName(m.event)}"
                m.id == moments.last().id -> if (single(m.event)) "The end" else "End of ${Display.eventName(m.event)}"
                else -> if (single(m.event)) "Part of the story" else "Part of the ${Display.eventName(m.event)} story"
            }
        }

        // ---- 3 + 4. Variety, then quality ----

        /** Value of adding [c] now: quality, plus people, variety and balance. */
        fun value(c: AssessedPhoto): Double {
            var v = c.quality
            for (p in c.people) {
                if (tags.isCouple(p)) continue
                val t = tags.tag(p)
                if (need(p) > 0) v += 0.25 * weight(t) / 2.5
                if ((personCount[p] ?: 0) >= cap(t, size)) v -= 0.5
            }
            // Someone marked Skip as the main subject.
            if (c.shot == ShotType.GUEST && c.people.any { tags.tag(it).priority == Priority.SKIP }) v -= 0.2
            if (tags.brideId in c.people && tags.groomId in c.people) v += 0.1
            if (c.coupleLookingAtEachOther) v += 0.15
            v += sideBonus(c)

            // Shot type mix within the event.
            val inEvent = (eventCount[c.event] ?: 0) + 1
            val share = (typeCount[c.event to c.shot] ?: 0).toDouble() / inEvent
            val wantShare = if (c.shot == ShotType.DETAIL) tags.occasion.detailShare else TYPE_SHARE[c.shot] ?: 0.1
            if (share < wantShare) v += 0.08
            // Details are about a tenth of each event, not more.
            if (c.shot == ShotType.DETAIL && share > 1.5 * tags.occasion.detailShare) v -= 0.15

            // No repeated pose back to back.
            if (repeatsNeighbour(c)) v -= 0.3
            // Spread the album across the day: each photo already picked from this moment counts against it.
            v -= MOMENT_CROWDING * (momentPicks[c.momentId]?.size ?: 0)

            // Portrait / landscape balance.
            val n = eventCount[c.event] ?: 0
            if (n >= 4) {
                val portraits = portraitCount[c.event] ?: 0
                val sameShare = (if (c.photo.portrait) portraits else n - portraits).toDouble() / n
                if (sameShare < 0.3) v += 0.04 else if (sameShare > 0.75) v -= 0.03
            }
            return v
        }

        fun sideBonus(c: AssessedPhoto): Double {
            val bride = sideCount[Side.BRIDE] ?: 0
            val groom = sideCount[Side.GROOM] ?: 0
            val sides = c.people.filter { !tags.isCouple(it) }.map { tags.tag(it).side }
            // One family's album leans to its own side instead of balancing the two.
            if (tags.albumFor != Side.UNKNOWN) return if (tags.albumFor in sides) 0.05 else 0.0
            return when {
                groom - bride >= 3 && Side.BRIDE in sides -> 0.05
                bride - groom >= 3 && Side.GROOM in sides -> 0.05
                else -> 0.0
            }
        }

        fun repeatsNeighbour(c: AssessedPhoto): Boolean {
            val others = momentPicks[c.momentId] ?: return false
            val before = others.filter { it.order < c.order }.maxByOrNull { it.order }
            val after = others.filter { it.order > c.order }.minByOrNull { it.order }
            return listOfNotNull(before, after).any { n ->
                n.shot == c.shot && n.people == c.people && hamming(n.photo.hash, c.photo.hash) <= SAME_POSE_HASH
            }
        }

        fun fillRest() {
            while (room() > 0) {
                val c = clean.filter { available(it) }.maxByOrNull { value(it) } ?: break
                add(c, "Strong photo to fill the album")
            }
            if (room() <= 0) return
            // Not enough clean photos: the best of the rest, fewest problems first.
            a.photos.filter { available(it) && Issue.UNREADABLE !in it.issues }
                .sortedWith(compareBy<AssessedPhoto> { it.hardIssues.size }.thenByDescending { it.quality })
                .take(room())
                .forEach { p ->
                    add(p, "Added to fill the album (not enough clean photos): " +
                        p.hardIssues.joinToString(", ") { it.label.lowercase() })
                }
        }

        fun describe(p: AssessedPhoto) {
            val list = reasons.getOrPut(p.id) { mutableListOf() }
            if (list.isEmpty()) list += "Kept from your shortlist"
            if (p.similarShots > 1) list += "Best of ${p.similarShots} similar shots"
            if (p.allEyesOpenAndLooking) list += "Everyone's eyes open and looking at the camera"
            if (p.coupleLookingAtEachOther && list.none { it.contains("looking at each other") }) list += lookingAtEachOther()
            if (p.emotional) list += "Real emotion: tears are never a flaw"
            if (p.interaction && !p.allEyesOpenAndLooking) list += "Natural moment: people interacting"
            val kind = when (p.shot) {
                ShotType.COUPLE_CLOSE -> "Close-up of ${tags.pair()}"
                ShotType.COUPLE_FULL -> "${tags.pair().capitalized()}, full length"
                ShotType.BRIDE_SOLO, ShotType.GROOM_SOLO -> portraitOf(p.shot)
                ShotType.GROUP -> if (tags.occasion.couple) "Family group photo" else "Group photo"
                ShotType.CANDID -> "Candid moment"
                ShotType.GUEST -> "Portrait"
                ShotType.DETAIL -> null
            }
            if (kind != null && list.none { it.equals(kind, ignoreCase = true) }) list += kind
            val soft = p.issues.filter { !it.hard }
            if (soft.isNotEmpty() && p.usable) list += "Small flaw: " + soft.joinToString(", ") { it.label.lowercase() }
        }
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    /**
     * Splits [total] places across groups in proportion to their counts (largest remainder),
     * at least 1 each when [atLeastOne], never more than a group has.
     */
    fun allocate(counts: Map<String, Int>, total: Int, atLeastOne: Boolean = true): Map<String, Int> {
        val active = counts.filterValues { it > 0 }
        val sum = active.values.sum()
        if (sum <= total) return active
        if (total <= 0) return emptyMap()
        if (atLeastOne && total < active.size) {
            return active.entries.sortedByDescending { it.value }.take(total).associate { it.key to 1 }
        }
        val ideal = active.mapValues { total.toDouble() * it.value / sum }
        val floorMin = if (atLeastOne) 1 else 0
        val alloc = active.keys.associateWithTo(LinkedHashMap()) { max(floorMin, floor(ideal.getValue(it)).toInt()) }
        while (alloc.values.sum() > total) {
            val e = alloc.keys.filter { alloc.getValue(it) > floorMin }.maxBy { alloc.getValue(it) - ideal.getValue(it) }
            alloc[e] = alloc.getValue(e) - 1
        }
        while (alloc.values.sum() < total) {
            val e = alloc.keys.filter { alloc.getValue(it) < active.getValue(it) }.maxBy { ideal.getValue(it) - alloc.getValue(it) }
            alloc[e] = alloc.getValue(e) + 1
        }
        return alloc
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
    }
}
