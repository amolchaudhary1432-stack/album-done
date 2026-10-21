package com.albumdone.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.albumdone.app.data.AppDatabase
import com.albumdone.app.data.PersonEntity
import com.albumdone.app.data.PersonPriority
import com.albumdone.app.data.PersonSide
import com.albumdone.app.data.PersonWithFace
import com.albumdone.app.data.PhotoEntity
import com.albumdone.app.data.ProjectEntity
import com.albumdone.app.data.ProjectStatus
import com.albumdone.app.data.ProjectSummary
import com.albumdone.app.people.FaceCrops
import com.albumdone.app.people.FamilyGuess
import com.albumdone.app.people.PeopleRepository
import com.albumdone.app.plan.AlbumAnalysis
import com.albumdone.app.plan.AlbumPlanner
import com.albumdone.app.plan.AlbumSize
import com.albumdone.app.plan.Box
import com.albumdone.app.plan.Display
import com.albumdone.app.plan.Issue
import com.albumdone.app.plan.Occasion
import com.albumdone.app.plan.PlanPhoto
import com.albumdone.app.plan.Priority
import com.albumdone.app.plan.Side
import com.albumdone.app.plan.StageGroups
import com.albumdone.app.scan.FolderScanner
import com.albumdone.app.scan.FoundPhoto
import com.albumdone.app.work.AnalyzeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data class NewAlbum(val treeUri: Uri, val name: String) : Screen
    data class Progress(val projectId: Long) : Screen
    data class Album(val projectId: Long) : Screen
    /** One photo; [fromLeftOut] when opened from the photos left out (Back returns there). */
    data class Viewer(val projectId: Long, val photoId: Long, val fromLeftOut: Boolean = false) : Screen
    /** Every photo not in the album, with why, to put one back. */
    data class LeftOut(val projectId: Long) : Screen
    data class Send(val projectId: Long) : Screen
    /** Everyone in the photos, to say who is who. */
    data class People(val projectId: Long) : Screen
    /** "Who's who": one face at a time, one tap each. */
    data class QuickTag(val projectId: Long) : Screen
    /** Family groups photographed with the couple (the stage), one best photo each. */
    data class Stage(val projectId: Long) : Screen
}

/** What the folder scan found, before analysis starts. */
data class ScanState(
    val scanning: Boolean = true,
    val found: Int = 0,
    val photos: List<FoundPhoto> = emptyList(),
    /** Event name (as the photographer named the folder) to number of photos, in order. */
    val events: List<Pair<String, Int>> = emptyList(),
    val error: String? = null,
) {
    /** More photos than one album works from (see [AlbumSize.MAX_PHOTOS]). */
    val tooMany: Boolean get() = found > AlbumSize.MAX_PHOTOS
}

/**
 * A face to show as a small round picture: a sharp picture of the face cut from the original
 * photo when there is one ([facePath]), otherwise the photo's thumbnail and where the face is in it.
 */
data class FaceCrop(val thumbPath: String?, val box: Box?, val facePath: String? = null)

/** Someone in the family strip. */
data class PersonChip(
    val personId: Long,
    val label: String,
    /** The couple has said who this is. */
    val known: Boolean,
    val face: FaceCrop,
    val inAlbum: Int,
    val target: Int,
    val warning: String?,
    /** Photos this person is in, in the whole wedding. */
    val photoCount: Int = 0,
    /** Whose side the app thinks they are on (PersonSide). */
    val side: String = PersonSide.UNKNOWN,
)

data class AlbumPhoto(
    val photo: PhotoEntity,
    val event: String,
    val reasons: List<String>,
    val cover: Boolean,
    /** Where to centre the square thumbnail so the faces show (-1..1 each way). */
    val crop: Pair<Float, Float>,
)

data class EventSection(val name: String, val title: String, val photos: List<AlbumPhoto>)

/** One family group photographed with the couple, shown by its best photo (or the one in the album). */
data class StageItem(
    val photo: AlbumPhoto,
    val inAlbum: Boolean,
    val guests: List<PersonChip>,
    /** Every frame of this group. */
    val frames: List<Long>,
)

data class LeftOut(val repeats: Int, val blurry: Int, val eyes: Int, val light: Int, val faces: Int, val distractions: Int)

data class AlbumState(
    val project: ProjectEntity,
    val total: Int,
    /** All photos in the folder. */
    val folderPhotos: Int,
    val cover: AlbumPhoto?,
    val bride: PersonChip?,
    val groom: PersonChip?,
    val family: List<PersonChip>,
    /** Frequent people not in the family strip, to add someone the app missed. */
    val others: List<PersonChip>,
    /** Family and frequent people the couple has not named yet, family first ("Who's who"). */
    val toName: List<PersonChip>,
    val warnings: List<String>,
    /** Family groups photographed with the couple, as they happened. */
    val stage: List<StageItem>,
    val events: List<EventSection>,
    val leftOut: LeftOut,
    /** Who the album is about, in a sentence: "the two of you", "the birthday star", "Riya". */
    val mainWords: String = "the two of you",
    /** A child's album: mummy and papa (named, or the app's guess). */
    val parents: List<PersonChip> = emptyList(),
) {
    val occasion: Occasion get() = Occasion.of(project.occasion)
}

data class PersonDetail(
    val person: PersonEntity,
    val face: FaceCrop,
    val samples: List<String>,
    val role: String?,
    val guessedSide: String,
)

data class Alternative(val photo: PhotoEntity, val note: String, val crop: Pair<Float, Float>)

/** [position] of [ofTotal]: where the photo sits among the album's photos of its event (0 when not in the album). */
/** Why a photo is not in the album, in plain words. */
enum class LeftOutReason(val label: String) {
    GOOD("Good, not needed"), REPEAT("A similar one is in"), BLURRY("Blurry"), EYES("Eyes closed"),
    LIGHT("Too dark or bright"), OTHER("Other flaw"),
}

data class LeftOutPhoto(val photo: PhotoEntity, val event: String, val reason: LeftOutReason, val crop: Pair<Float, Float>)

data class ViewerState(val photo: AlbumPhoto, val alternatives: List<Alternative>, val inAlbum: Boolean, val position: Int = 0, val ofTotal: Int = 0,
    /** For a photo not in the album: why, in plain words. */
    val leftOutWhy: String? = null)

private const val TAG = "AlbumDone"

/**
 * Screens always get new values on the main thread. Work inside the flows runs elsewhere; without
 * this, a value could arrive from a worker thread and (in the phone test's tools) the screen would
 * be drawn there, which crashes lists.
 */
private fun <T> Flow<T>.onMain(): Flow<T> = flowOn(Dispatchers.Main)

/** People listed on the people screen besides the family; a big wedding has many. */
private const val MAX_PEOPLE_SHOWN = 80

/** At most this many faces in one round of "Who's who". */
private const val QUICK_TAG_MAX = 40

class AlbumViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).dao()
    private val people = PeopleRepository(dao)

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    var scan by mutableStateOf(ScanState())
        private set

    /** Album size chosen on the new-album screen. */
    var newAlbumSize by mutableStateOf(150)
    /** What the new album is for; guessed from the folder names, one tap to change. */
    var newOccasion by mutableStateOf(Occasion.OTHER)

    var busy by mutableStateOf(false)
        private set

    /** A short message to show once, with an optional undo. [messageId] changes for each new one. */
    var message by mutableStateOf<String?>(null)
        private set
    var undo by mutableStateOf<(() -> Unit)?>(null)
        private set
    var messageId by mutableStateOf(0)
        private set

    val projects: StateFlow<List<ProjectSummary>> = dao.projectSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun projectSummary(id: Long) = dao.projectSummary(id).onMain()

    fun recentThumbs(projectId: Long) = dao.recentThumbs(projectId, 5).onMain()

    // The planner's view of a wedding. Worked out once and reused until the family changes.
    private val analysisLock = Mutex()
    @Volatile private var cachedAnalysis: Pair<Long, AlbumAnalysis>? = null

    private suspend fun analysisFor(projectId: Long): AlbumAnalysis = analysisLock.withLock {
        cachedAnalysis?.let { (id, a) -> if (id == projectId) return@withLock a }
        val started = System.currentTimeMillis()
        val project = dao.project(projectId) ?: error("No such album")
        val (photos, tags) = people.planInput(project)
        val analysis = withContext(Dispatchers.Default) { AlbumPlanner.analyze(photos, tags) }
        cachedAnalysis = projectId to analysis
        Log.d(TAG, "analysis of ${photos.size} photos in ${System.currentTimeMillis() - started} ms")
        analysis
    }

    private fun forgetAnalysis() { cachedAnalysis = null }

    private fun say(text: String, undoAction: (() -> Unit)? = null) {
        message = text
        undo = undoAction
        messageId++
    }

    // ---- Navigation ----

    fun back() {
        screen = when (val s = screen) {
            is Screen.Viewer -> if (s.fromLeftOut) Screen.LeftOut(s.projectId) else Screen.Album(s.projectId)
            is Screen.LeftOut -> Screen.Album(s.projectId)
            is Screen.Send -> Screen.Album(s.projectId)
            is Screen.People -> Screen.Album(s.projectId)
            is Screen.Stage -> Screen.Album(s.projectId)
            is Screen.QuickTag -> { finishQuickTag(s.projectId); return }
            else -> Screen.Home
        }
    }

    fun openProject(id: Long) {
        viewModelScope.launch {
            val project = dao.project(id) ?: return@launch
            if (project.status != ProjectStatus.DONE) {
                // Starts the work again if Android stopped it; does nothing if it is running.
                AnalyzeWorker.start(getApplication(), id)
                screen = Screen.Progress(id)
            } else {
                openAlbum(id)
            }
        }
    }

    /** Analysis finished: the album is made straight away, so the couple sees results first. */
    fun openAlbum(projectId: Long) {
        viewModelScope.launch {
            Log.d(TAG, "open album $projectId")
            forgetAnalysis()
            val photos = dao.photos(projectId)
            if (photos.none { it.selected || it.removed }) {
                busy = true
                replan(projectId)
                busy = false
            }
            Log.d(TAG, "album $projectId ready to show")
            screen = Screen.Album(projectId)
        }
    }

    fun openPhoto(projectId: Long, photoId: Long, fromLeftOut: Boolean = false) { screen = Screen.Viewer(projectId, photoId, fromLeftOut) }

    fun openLeftOut(projectId: Long) { screen = Screen.LeftOut(projectId) }

    fun openSend(projectId: Long) { screen = Screen.Send(projectId) }

    fun openPeople(projectId: Long) { screen = Screen.People(projectId) }

    fun openStage(projectId: Long) { screen = Screen.Stage(projectId) }

    fun openQuickTag(projectId: Long) {
        quickTagged = 0
        screen = Screen.QuickTag(projectId)
    }

    // ---- New album ----

    fun onFolderPicked(uri: Uri) {
        val app = getApplication<Application>()
        try {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some folders cannot be kept open after a restart; scanning still works now.
        }
        val name = DocumentFile.fromTreeUri(app, uri)?.name ?: "Our album"
        screen = Screen.NewAlbum(uri, name)
        scan = ScanState()
        viewModelScope.launch {
            val photos = withContext(Dispatchers.IO) {
                try {
                    FolderScanner.scan(app, uri) { count -> scan = scan.copy(found = count) }
                } catch (e: Exception) {
                    null
                }
            }
            if (photos == null) scan = ScanState(scanning = false, error = "Could not read this folder. Please pick it again.")
            else showScanned(uri, name, photos)
        }
    }

    /** The folder has been read: what is in it, and first guesses for the album. The phone test calls this directly. */
    fun showScanned(uri: Uri, name: String, photos: List<FoundPhoto>) {
        val plan = photos.mapIndexed { i, p -> PlanPhoto(id = i.toLong(), name = p.name, folder = p.folder) }
        val eventOf = AlbumPlanner.assignEvents(plan)
        val order = AlbumPlanner.orderEvents(plan, eventOf)
        val counts = eventOf.values.groupingBy { it }.eachCount()
        newAlbumSize = AlbumSize.suggested(photos.size)
        newOccasion = Occasion.guess(listOf(name) + photos.map { it.folder }.distinct())
        screen = Screen.NewAlbum(uri, name)
        scan = ScanState(scanning = false, found = photos.size, photos = photos, events = order.map { it to (counts[it] ?: 0) })
    }

    fun startAnalysis(name: String) {
        val s = screen as? Screen.NewAlbum ?: return
        val photos = scan.photos
        if (photos.isEmpty() || scan.tooMany || busy) return
        busy = true
        viewModelScope.launch {
            val projectId = withContext(Dispatchers.IO) {
                val id = dao.insertProject(
                    ProjectEntity(
                        name = name.trim().ifEmpty { s.name },
                        treeUri = s.treeUri.toString(),
                        createdAt = System.currentTimeMillis(),
                        albumSize = newAlbumSize,
                        occasion = newOccasion.name,
                    )
                )
                photos.chunked(1000).forEach { chunk ->
                    dao.insertPhotos(chunk.map {
                        PhotoEntity(
                            projectId = id, docUri = it.docUri.toString(), name = it.name, folder = it.folder,
                            size = it.size, lastModified = it.lastModified,
                        )
                    })
                }
                id
            }
            AnalyzeWorker.start(getApplication(), projectId)
            scan = ScanState()
            busy = false
            screen = Screen.Progress(projectId)
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            WorkManager.getInstance(app).cancelUniqueWork(AnalyzeWorker.workName(id))
            withContext(Dispatchers.IO) {
                val project = dao.project(id)
                dao.deleteProjectWithPhotos(id)
                File(app.filesDir, "thumbs/$id").deleteRecursively()
                FaceCrops.deleteAll(app, id)
                project?.let {
                    try {
                        app.contentResolver.releasePersistableUriPermission(Uri.parse(it.treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: SecurityException) {
                    }
                }
            }
            if (cachedAnalysis?.first == id) forgetAnalysis()
            albums.remove(id)
        }
    }

    // ---- The album ----

    /** Makes (or remakes) the album, always keeping photos the couple chose themselves. */
    private suspend fun replan(projectId: Long) {
        val project = dao.project(projectId) ?: return
        val analysis = analysisFor(projectId)
        withContext(Dispatchers.Default) {
            val photos = dao.photos(projectId)
            val keep = photos.filter { it.selected && it.userChoice }.map { it.id }.toSet()
            val removed = photos.filter { it.removed }.map { it.id }.toSet()
            val list = AlbumPlanner.shortlist(analysis, project.albumSize, removed, keep)
            Log.d(TAG, "album planned: ${list.ids.size} of ${photos.size} photos")
            dao.replaceSelection(projectId, list.ids)
            dao.setPickReasons(list.ids.filter { it !in keep }.associateWith { list.reasons[it].orEmpty().joinToString("\n") })
            dao.setCover(projectId, list.coverId)
            Log.d(TAG, "album saved")
        }
    }

    // One live album per project, shared by every screen (album, people, who's who, send), so
    // moving between them shows the album at once instead of working it out again.
    private val albums = HashMap<Long, StateFlow<AlbumState?>>()

    fun album(projectId: Long): StateFlow<AlbumState?> = albums.getOrPut(projectId) {
        albumFlow(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    private fun albumFlow(projectId: Long): Flow<AlbumState?> =
        combine(dao.photosFlow(projectId), dao.peopleWithFaces(projectId), dao.projectSummary(projectId)) { photos, persons, summary ->
            val project = summary?.project ?: return@combine null
            val selected = photos.count { it.selected }
            Log.d(TAG, "album update: $selected selected of ${photos.size}, ${persons.size} people")
            if (selected == 0) return@combine null
            val analysis = analysisFor(projectId)
            withContext(Dispatchers.Default) { buildAlbum(project, photos, persons, analysis) }
        }.catch { e ->
            Log.e(TAG, "album could not be shown", e)
            throw e
        }.onMain()

    private fun buildAlbum(project: ProjectEntity, photos: List<PhotoEntity>, persons: List<PersonWithFace>, analysis: AlbumAnalysis): AlbumState {
        val byId = photos.associateBy { it.id }
        val selected = photos.filter { it.selected }
        val selectedIds = selected.map { it.id }.toSet()
        fun albumPhoto(id: Long): AlbumPhoto? {
            val p = byId[id] ?: return null
            val a = analysis.byId[id] ?: return null
            return AlbumPhoto(
                photo = p,
                event = a.event,
                reasons = p.pickReason?.split('\n')?.filter(String::isNotBlank).orEmpty(),
                cover = id == project.coverPhotoId,
                crop = Display.cropBias(p.width, p.height, a.photo.faces.map { it.box }),
            )
        }
        val faceOf = persons.associate { it.person.id to faceCrop(project.id, it) }
        val personById = persons.associateBy { it.person.id }
        val coverage = AlbumPlanner.coverage(analysis, selectedIds).associateBy { it.personId }
        val tags = analysis.tags

        fun chip(id: Long, label: String? = null, family: Boolean = false): PersonChip {
            val entity = personById[id]?.person
            val c = coverage[id]
            val known = entity?.name?.isNotBlank() == true
            // Not named yet: say what the app thinks, humbly, instead of a row of question marks.
            val guess = when {
                id in tags.parents -> "Parent"
                tags.tag(id).side == com.albumdone.app.plan.Side.BRIDE -> "Bride's side"
                tags.tag(id).side == com.albumdone.app.plan.Side.GROOM -> "Groom's side"
                else -> if (family) "Family" else "Not named"
            }
            return PersonChip(
                personId = id,
                label = label ?: entity?.name?.takeIf { it.isNotBlank() } ?: guess,
                known = known || label != null,
                face = faceOf[id] ?: FaceCrop(null, null),
                inAlbum = analysis.photos.count { it.id in selectedIds && id in it.people },
                target = c?.target ?: 0,
                warning = c?.warning,
                photoCount = entity?.photoCount ?: 0,
                side = when (tags.tag(id).side) {
                    com.albumdone.app.plan.Side.BRIDE -> PersonSide.BRIDE
                    com.albumdone.app.plan.Side.GROOM -> PersonSide.GROOM
                    else -> PersonSide.UNKNOWN
                },
            )
        }

        val familyIds = tags.tags.values
            .filter { !tags.isCouple(it.id) && (it.priority == Priority.MUST || it.priority == Priority.IMPORTANT) }
            .filter { personById.containsKey(it.id) }
            .sortedWith(compareBy<com.albumdone.app.plan.PersonTag> { it.priority }.thenByDescending { it.elder }.thenBy { it.side }
                .thenByDescending { personById[it.id]?.person?.photoCount ?: 0 })
            .map { it.id }
        val family = familyIds.map { chip(it, family = true) }
        val others = persons.asSequence()
            .map { it.person }
            .filter { it.id !in familyIds && !tags.isCouple(it.id) && it.priority != PersonPriority.SKIP && it.photoCount >= 3 }
            .sortedByDescending { it.photoCount }
            .take(MAX_PEOPLE_SHOWN)
            .map { chip(it.id) }
            .toList()
        val toName = (family + others).filter { !it.known }.take(QUICK_TAG_MAX)
        val warnings = family.filter { it.warning != null && it.inAlbum < it.target }.take(2).map { c ->
            val who = if (c.known) c.label else "Someone close to you"
            "$who: ${c.inAlbum} of ${c.target} photos. ${c.warning}."
        }

        val stage = StageGroups.find(analysis).mapNotNull { g ->
            val shown = g.photoIds.firstOrNull { it in selectedIds } ?: g.photoId
            val ap = albumPhoto(shown) ?: return@mapNotNull null
            StageItem(ap, shown in selectedIds, g.guests.filter { personById.containsKey(it) }.map { chip(it) }, g.photoIds)
        }
        val events = analysis.events.mapNotNull { e ->
            val items = analysis.photos.filter { it.event == e && it.id in selectedIds }.mapNotNull { albumPhoto(it.id) }
            if (items.isEmpty()) return@mapNotNull null
            EventSection(e, Display.eventName(e), items)
        }
        val leftOut = LeftOut(
            repeats = analysis.countAny(Issue.DUPLICATE, Issue.GROUP_REPEAT),
            blurry = analysis.count(Issue.BLURRY),
            eyes = analysis.countAny(Issue.EYES_CLOSED, Issue.HALF_BLINK),
            light = analysis.darkOrBrightCount,
            faces = analysis.countAny(Issue.FACE_CUT_OFF, Issue.FACE_BLOCKED),
            distractions = analysis.countAny(Issue.BACK_OF_GUEST, Issue.PHONE_IN_SHOT, Issue.STRANGER),
        )
        return AlbumState(
            project = project,
            total = selected.size,
            folderPhotos = photos.size,
            cover = project.coverPhotoId?.let { id ->
                // Centre the cover on the couple, not on whoever else is in it.
                albumPhoto(id)?.let { ap ->
                    val couple = analysis.byId[id]?.photo?.faces.orEmpty().filter { tags.isCouple(it.personId) }
                    if (couple.isEmpty()) ap else ap.copy(crop = Display.cropBias(ap.photo.width, ap.photo.height, couple.map { it.box }))
                }
            },
            parents = tags.parents.map { chip(it, family = true) },
            mainWords = when {
                tags.occasion.couple -> "the two of you"
                tags.groomId != null -> tags.pair()
                else -> tags.brideId?.let { tags.who(it) } ?: "you"
            },
            bride = tags.brideId?.let { chip(it, personById[it]?.person?.name?.takeIf { n -> n.isNotBlank() } ?: tags.occasion.title(first = true)) },
            groom = tags.groomId?.let { chip(it, personById[it]?.person?.name?.takeIf { n -> n.isNotBlank() } ?: tags.occasion.title(first = false)) },
            family = family,
            others = others,
            toName = toName,
            warnings = warnings,
            stage = stage,
            events = events,
            leftOut = leftOut,
        )
    }

    private fun faceCrop(projectId: Long, p: PersonWithFace): FaceCrop =
        FaceCrop(p.thumbPath, faceBox(p), FaceCrops.file(getApplication(), projectId, p.person.id).takeIf { it.exists() }?.path)

    private fun faceBox(p: PersonWithFace): Box? {
        val l = p.faceLeft ?: return null
        return Box(l, p.faceTop ?: 0f, p.faceRight ?: 1f, p.faceBottom ?: 1f)
    }

    fun setAlbumSize(projectId: Long, size: Int) {
        if (busy || size <= 0) return
        busy = true
        viewModelScope.launch {
            dao.setAlbumSize(projectId, size)
            replan(projectId)
            busy = false
            say("Album updated to $size photos")
        }
    }

    // ---- One photo ----

    fun viewer(projectId: Long, photoId: Long): Flow<ViewerState?> =
        combine(dao.photosFlow(projectId), dao.projectSummary(projectId)) { photos, summary ->
            val project = summary?.project ?: return@combine null
            val analysis = analysisFor(projectId)
            withContext(Dispatchers.Default) { viewerState(project, photos, photoId, analysis) }
        }.onMain()

    private fun viewerState(project: ProjectEntity, photos: List<PhotoEntity>, photoId: Long, analysis: AlbumAnalysis): ViewerState? {
        val byId = photos.associateBy { it.id }
        val p = byId[photoId] ?: return null
        val a = analysis.byId[photoId] ?: return null
        val exclude = photos.filter { it.selected || it.removed }.map { it.id }.toSet()
        val inEvent = photos.filter { it.selected && analysis.byId[it.id]?.event == a.event }
            .sortedBy { analysis.byId.getValue(it.id).order }.map { it.id }
        val alts = AlbumPlanner.alternatives(analysis, photoId, exclude).mapNotNull { alt ->
            val ap = byId[alt.photoId] ?: return@mapNotNull null
            val aa = analysis.byId[alt.photoId] ?: return@mapNotNull null
            Alternative(ap, alt.note, Display.cropBias(ap.width, ap.height, aa.photo.faces.map { it.box }))
        }
        return ViewerState(
            photo = AlbumPhoto(
                photo = p,
                event = a.event,
                reasons = p.pickReason?.split('\n')?.filter(String::isNotBlank).orEmpty(),
                cover = photoId == project.coverPhotoId,
                crop = Display.cropBias(p.width, p.height, a.photo.faces.map { it.box }),
            ),
            alternatives = alts,
            inAlbum = p.selected,
            position = inEvent.indexOf(photoId) + 1,
            ofTotal = inEvent.size,
            leftOutWhy = if (p.selected) null else leftOutReason(a).label,
        )
    }

    /** The couple prefers [newId] to [oldId]: swap them, and remember it was their choice. */
    fun swap(projectId: Long, oldId: Long, newId: Long) {
        viewModelScope.launch {
            val project = dao.project(projectId) ?: return@launch
            dao.deselect(oldId)
            dao.selectAsChoice(newId, "Your choice: swapped in for a similar photo")
            if (project.coverPhotoId == oldId) dao.setCover(projectId, newId)
            screen = Screen.Viewer(projectId, newId)
            say("Swapped") { viewModelScope.launch { dao.deselect(newId); dao.restoreToShortlist(oldId); if (project.coverPhotoId == oldId) dao.setCover(projectId, oldId) } }
        }
    }

    /** Takes a photo out and fills its place with the next best one straight away. */
    /**
     * Fills the places left by photos the couple took out with the next best ones, keeping every
     * other photo where it is. Returns the photos added.
     */
    private suspend fun refill(projectId: Long, before: Set<Long>, takenOut: Set<Long>): List<Long> {
        val project = dao.project(projectId) ?: return emptyList()
        val analysis = analysisFor(projectId)
        return withContext(Dispatchers.Default) {
            val photos = dao.photos(projectId)
            val keep = photos.filter { it.selected }.map { it.id }.toSet()
            val list = AlbumPlanner.shortlist(analysis, project.albumSize, photos.filter { it.removed }.map { it.id }.toSet(), keep)
            dao.replaceSelection(projectId, list.ids)
            val new = list.ids.filter { it !in before }
            dao.setPickReasons(new.associateWith { list.reasons[it].orEmpty().joinToString("\n") })
            if (project.coverPhotoId?.let { it in takenOut } == true) dao.setCover(projectId, list.coverId)
            new
        }
    }

    fun remove(projectId: Long, photoId: Long) {
        viewModelScope.launch {
            val before = dao.photos(projectId).filter { it.selected }.map { it.id }.toSet()
            dao.removeFromShortlist(photoId)
            val project = dao.project(projectId) ?: return@launch
            val added = refill(projectId, before, setOf(photoId))
            screen = Screen.Album(projectId)
            say(if (added.isEmpty()) "Removed" else "Removed. The next best photo took its place") {
                viewModelScope.launch {
                    dao.deselectAll(added)
                    dao.restoreToShortlist(photoId)
                    if (project.coverPhotoId == photoId) dao.setCover(projectId, photoId)
                }
            }
        }
    }

    /** Puts a photo that was left out into the album (the album grows by one); Undo takes it out again. */
    fun addToAlbum(projectId: Long, photoId: Long) {
        viewModelScope.launch {
            val size = dao.project(projectId)?.albumSize ?: return@launch
            dao.selectAsChoice(photoId, "Your choice: added back")
            growToFit(projectId)
            say("Added to your album") { viewModelScope.launch { dao.deselect(photoId); dao.setAlbumSize(projectId, size) } }
        }
    }

    private fun leftOutReason(a: com.albumdone.app.plan.AssessedPhoto): LeftOutReason {
        val hard = a.issues.firstOrNull { it.hard && it != Issue.DUPLICATE && it != Issue.GROUP_REPEAT }
        return when {
            Issue.DUPLICATE in a.issues || Issue.GROUP_REPEAT in a.issues -> LeftOutReason.REPEAT
            hard == Issue.BLURRY -> LeftOutReason.BLURRY
            hard == Issue.EYES_CLOSED || hard == Issue.HALF_BLINK -> LeftOutReason.EYES
            hard == Issue.TOO_DARK || hard == Issue.TOO_BRIGHT -> LeftOutReason.LIGHT
            hard != null -> LeftOutReason.OTHER
            else -> LeftOutReason.GOOD
        }
    }

    /** The photos left out, in the photographer's order, each with the main reason. */
    fun leftOut(projectId: Long): Flow<List<LeftOutPhoto>> =
        combine(dao.photosFlow(projectId), dao.projectSummary(projectId)) { photos, _ ->
            val analysis = analysisFor(projectId)
            withContext(Dispatchers.Default) {
                val byId = photos.associateBy { it.id }
                analysis.photos.filter { a -> byId[a.id]?.let { !it.selected && !it.failed } == true }.map { a ->
                    val p = byId.getValue(a.id)
                    LeftOutPhoto(p, a.event, leftOutReason(a), Display.cropBias(p.width, p.height, a.photo.faces.map { it.box }))
                }
            }
        }.onMain()

    // ---- Family groups (the stage) ----

    /** Puts a family group's photo in the album (the album grows by one), or takes the group out. */
    fun setGroupInAlbum(projectId: Long, item: StageItem, inAlbum: Boolean) {
        viewModelScope.launch {
            if (inAlbum) {
                dao.selectAsChoice(item.photo.photo.id, "Your choice: a family group")
                growToFit(projectId)
                say("Added to your album")
            } else {
                val before = dao.photos(projectId).filter { it.selected }.map { it.id }.toSet()
                item.frames.filter { it in before }.forEach { dao.removeFromShortlist(it) }
                val added = refill(projectId, before, item.frames.toSet())
                say(if (added.isEmpty()) "Taken out" else "Taken out. The next best photo took its place")
            }
        }
    }

    /** Many families want every group that came on stage in the album: one photo each. */
    fun addAllGroups(projectId: Long, items: List<StageItem>) {
        val missing = items.filter { !it.inAlbum }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            missing.forEach { dao.selectAsChoice(it.photo.photo.id, "Your choice: every family group in the album") }
            growToFit(projectId)
            say("Added ${missing.size} family photo${if (missing.size == 1) "" else "s"}")
        }
    }

    /** The album size follows the couple's own additions. */
    private suspend fun growToFit(projectId: Long) {
        val project = dao.project(projectId) ?: return
        val n = dao.photos(projectId).count { it.selected }
        if (n > project.albumSize) dao.setAlbumSize(projectId, n)
    }

    /**
     * Names everyone in a family group at once ("Mama's family", the bride's side): one tap for the
     * whole group instead of one per face. Names already given are kept.
     */
    fun tagGroup(projectId: Long, people: List<Long>, side: String, name: String, notFamily: Boolean) {
        val label = name.trim().ifEmpty {
            when (side) {
                PersonSide.BRIDE -> "Bride's family"
                PersonSide.GROOM -> "Groom's family"
                else -> "Friend"
            }
        }
        editPeople(projectId, if (notFamily) "Album updated" else "Album updated for $label") {
            for (id in people) {
                val p = dao.person(id) ?: continue
                dao.updatePerson(
                    if (notFamily) p.copy(priority = PersonPriority.SKIP)
                    else p.copy(
                        name = p.name.ifBlank { label },
                        side = side,
                        priority = if (p.priority == PersonPriority.MUST) PersonPriority.MUST else PersonPriority.IMPORTANT,
                    )
                )
            }
        }
    }

    // ---- People ----

    /** One person for the "Who is this?" sheet. [guessedSide] is the app's guess, from the album. */
    fun personDetail(projectId: Long, personId: Long, guessedSide: String): Flow<PersonDetail?> =
        combine(dao.peopleWithFaces(projectId), dao.projectSummary(projectId)) { persons, summary ->
            val p = persons.firstOrNull { it.person.id == personId }
            Log.d(TAG, "person $personId: found=${p != null} among ${persons.size}")
            if (p == null) return@combine null
            val project = summary?.project
            val samples = dao.sampleThumbs(personId).distinct().take(4)
            PersonDetail(
                person = p.person,
                face = faceCrop(projectId, p),
                samples = samples,
                role = Occasion.of(project?.occasion).let { o ->
                    when (personId) { project?.brideId -> o.title(first = true); project?.groomId -> o.title(first = false); else -> null }
                },
                guessedSide = guessedSide,
            ).also { Log.d(TAG, "person $personId: ready, ${samples.size} samples") }
        }.catch { e ->
            Log.e(TAG, "person $personId could not be loaded", e)
            throw e
        }.onMain()

    private fun editPeople(projectId: Long, done: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            withContext(Dispatchers.IO) { block() }
            forgetAnalysis()
            replan(projectId)
            busy = false
            if (done != null) say(done)
        }
    }

    /** "Who is this?" answered: the relation decides how many photos, elder, and side. */
    fun setRelation(projectId: Long, person: PersonEntity, side: String, relation: String, name: String) {
        val (priority, elder) = if (relation.isEmpty()) Priority.IMPORTANT to person.elder else FamilyGuess.fromRelation(relation)
        val label = name.trim().ifEmpty { if (relation.isEmpty()) person.name else relationLabel(side, relation) }
        editPeople(projectId, "Album updated for $label") {
            dao.updatePerson(person.copy(name = label, priority = priority.name, elder = elder, side = side))
        }
    }

    /** "Photos of Nani in the album: 4": her best photos fill that many places (null: back to automatic). */
    fun setWanted(projectId: Long, personId: Long, wanted: Int?) {
        val n = wanted?.coerceAtLeast(0)
        editPeople(projectId) {
            val person = dao.person(personId) ?: return@editPeople
            dao.updatePerson(person.copy(wantedPhotos = n))
        }
    }

    private fun relationLabel(side: String, relation: String) = when (side) {
        PersonSide.BRIDE -> "Bride's ${relation.lowercase()}"
        PersonSide.GROOM -> "Groom's ${relation.lowercase()}"
        else -> relation
    }

    // ---- "Who's who": one face at a time. Answers are saved at once; the album is remade once at the end. ----

    private var quickTagged = 0
    private val quickTagSaves = mutableListOf<Job>()

    /** A few photos of this person, to help recognise a face. */
    suspend fun samples(personId: Long): List<String> = dao.sampleThumbs(personId).distinct().take(3)

    fun quickTag(personId: Long, side: String, relation: String) {
        quickTagged++
        quickTagSaves += viewModelScope.launch(Dispatchers.IO) {
            val person = dao.person(personId) ?: return@launch
            val (priority, elder) = FamilyGuess.fromRelation(relation)
            dao.updatePerson(person.copy(name = relationLabel(side, relation), priority = priority.name, elder = elder, side = side))
        }
    }

    fun quickNotFamily(personId: Long) {
        quickTagged++
        quickTagSaves += viewModelScope.launch(Dispatchers.IO) {
            val person = dao.person(personId) ?: return@launch
            dao.updatePerson(person.copy(priority = PersonPriority.SKIP))
        }
    }

    fun finishQuickTag(projectId: Long) {
        screen = Screen.Album(projectId)
        val n = quickTagged
        quickTagged = 0
        if (n == 0) return
        val saves = quickTagSaves.toList()
        quickTagSaves.clear()
        editPeople(projectId, "Album updated for $n ${if (n == 1) "person" else "people"}") { saves.joinAll() }
    }

    fun notFamily(projectId: Long, person: PersonEntity) =
        editPeople(projectId, "Album updated") { dao.updatePerson(person.copy(priority = PersonPriority.SKIP)) }

    fun setBride(projectId: Long, personId: Long) = editPeople(projectId, "Album updated") {
        val p = dao.project(projectId) ?: return@editPeople
        dao.setCouple(projectId, personId, if (p.groomId == personId) p.brideId else p.groomId)
        dao.setCoupleSure(projectId, true)
    }

    fun setGroom(projectId: Long, personId: Long) = editPeople(projectId, "Album updated") {
        val p = dao.project(projectId) ?: return@editPeople
        dao.setCouple(projectId, if (p.brideId == personId) p.groomId else p.brideId, personId)
        dao.setCoupleSure(projectId, true)
    }

    /** For a birthday, a baby or a trip: the photos are about this one person. */
    fun setMain(projectId: Long, personId: Long) = editPeople(projectId, "Album updated") {
        dao.setCouple(projectId, personId, null)
        dao.setCoupleSure(projectId, true)
    }

    fun swapCouple(projectId: Long) = editPeople(projectId, "Swapped bride and groom") {
        val p = dao.project(projectId) ?: return@editPeople
        dao.setCouple(projectId, p.groomId, p.brideId)
        dao.setCoupleSure(projectId, true)
    }

    /** "Yes, that is the groom." */
    fun confirmCouple(projectId: Long) {
        viewModelScope.launch { dao.setCoupleSure(projectId, true) }
    }

    fun merge(projectId: Long, keep: Long, gone: Long) = editPeople(projectId, "Joined: same person") { people.merge(projectId, keep, gone) }

    fun split(projectId: Long, personId: Long) {
        viewModelScope.launch {
            busy = true
            val parts = withContext(Dispatchers.IO) { people.split(projectId, personId) }
            forgetAnalysis()
            replan(projectId)
            busy = false
            say(if (parts > 1) "Separated into $parts people" else "These photos look like one person")
        }
    }

    fun findMore(projectId: Long, personId: Long) {
        viewModelScope.launch {
            busy = true
            val n = withContext(Dispatchers.IO) { people.findMore(projectId, personId) }
            forgetAnalysis()
            replan(projectId)
            busy = false
            say(if (n > 0) "Found $n more photo${if (n == 1) "" else "s"}" else "No more photos found")
        }
    }

    // ---- Sending ----

    /**
     * The list for one family's own copy of the album (both families usually keep one): the same
     * folder and the couple's own choices, with that family's relatives first.
     */
    suspend fun familyListText(projectId: Long, side: Side): String {
        val project = dao.project(projectId) ?: return ""
        val (photos, tags) = people.planInput(project)
        val saved = dao.photos(projectId)
        return withContext(Dispatchers.Default) {
            val a = AlbumPlanner.analyze(photos, tags.forFamily(side))
            val keep = saved.filter { it.selected && it.userChoice }.map { it.id }.toSet()
            val removed = saved.filter { it.removed }.map { it.id }.toSet()
            val list = AlbumPlanner.shortlist(a, project.albumSize, removed, keep)
            val byId = saved.associateBy { it.id }
            val picked = list.ids.mapNotNull { id -> a.byId[id]?.let { it to byId[id] } }
            val who = if (side == Side.BRIDE) "Bride's family" else "Groom's family"
            buildString {
                append("Album selection: ").append(project.name).append(" (").append(who).append(" album)\n")
                append(picked.size).append(" photos\n")
                for (e in a.events) {
                    val inEvent = picked.filter { it.first.event == e }
                    if (inEvent.isEmpty()) continue
                    append('\n').append(Display.eventName(e)).append(" (").append(inEvent.size).append(")\n")
                    for ((ap, entity) in inEvent) {
                        val p = entity ?: continue
                        if (p.folder.isEmpty()) append(p.name) else append(p.folder).append('/').append(p.name)
                        if (ap.id == list.coverId) append("  (cover)")
                        append('\n')
                    }
                }
            }
        }
    }

    /** Plain text for the photographer: event headings, then file names. */
    fun shareText(state: AlbumState): String = buildString {
        append("Album selection: ").append(state.project.name).append('\n')
        append(state.total).append(" photos\n")
        for (e in state.events) {
            append('\n').append(e.title).append(" (").append(e.photos.size).append(")\n")
            for (item in e.photos) {
                val p = item.photo
                if (p.folder.isEmpty()) append(p.name) else append(p.folder).append('/').append(p.name)
                if (item.cover) append("  (cover)")
                append('\n')
            }
        }
    }
}
