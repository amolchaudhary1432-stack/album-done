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
import com.albumdone.app.people.FamilyGuess
import com.albumdone.app.people.PeopleRepository
import com.albumdone.app.plan.AlbumAnalysis
import com.albumdone.app.plan.AlbumPlanner
import com.albumdone.app.plan.Box
import com.albumdone.app.plan.Display
import com.albumdone.app.plan.Issue
import com.albumdone.app.plan.PlanPhoto
import com.albumdone.app.plan.Priority
import com.albumdone.app.scan.FolderScanner
import com.albumdone.app.scan.FoundPhoto
import com.albumdone.app.work.AnalyzeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Screen {
    data object Home : Screen
    data class NewAlbum(val treeUri: Uri, val name: String) : Screen
    data class Progress(val projectId: Long) : Screen
    data class Album(val projectId: Long) : Screen
    data class Viewer(val projectId: Long, val photoId: Long) : Screen
    data class Send(val projectId: Long) : Screen
}

/** What the folder scan found, before analysis starts. */
data class ScanState(
    val scanning: Boolean = true,
    val found: Int = 0,
    val photos: List<FoundPhoto> = emptyList(),
    /** Event name (as the photographer named the folder) to number of photos, in order. */
    val events: List<Pair<String, Int>> = emptyList(),
    val error: String? = null,
)

/** A face to show as a small round picture: the photo's thumbnail and where the face is in it. */
data class FaceCrop(val thumbPath: String?, val box: Box?)

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

data class EventSection(val name: String, val title: String, val photos: List<AlbumPhoto>, val keyMoments: List<String>)

data class LeftOut(val repeats: Int, val blurry: Int, val eyes: Int, val light: Int, val faces: Int, val distractions: Int)

data class AlbumState(
    val project: ProjectEntity,
    val total: Int,
    val cover: AlbumPhoto?,
    val bride: PersonChip?,
    val groom: PersonChip?,
    val family: List<PersonChip>,
    /** Frequent people not in the family strip, to add someone the app missed. */
    val others: List<PersonChip>,
    val warnings: List<String>,
    val events: List<EventSection>,
    val leftOut: LeftOut,
)

data class PersonDetail(
    val person: PersonEntity,
    val face: FaceCrop,
    val samples: List<String>,
    val role: String?,
    val guessedSide: String,
)

data class Alternative(val photo: PhotoEntity, val note: String, val crop: Pair<Float, Float>)

data class ViewerState(val photo: AlbumPhoto, val alternatives: List<Alternative>, val inAlbum: Boolean)

private const val TAG = "AlbumDone"

class AlbumViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).dao()
    private val people = PeopleRepository(dao)

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    var scan by mutableStateOf(ScanState())
        private set

    /** Album size chosen on the new-album screen. */
    var newAlbumSize by mutableStateOf(300)

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

    fun projectSummary(id: Long) = dao.projectSummary(id)

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
            is Screen.Viewer -> Screen.Album(s.projectId)
            is Screen.Send -> Screen.Album(s.projectId)
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

    fun openPhoto(projectId: Long, photoId: Long) { screen = Screen.Viewer(projectId, photoId) }

    fun openSend(projectId: Long) { screen = Screen.Send(projectId) }

    // ---- New album ----

    fun onFolderPicked(uri: Uri) {
        val app = getApplication<Application>()
        try {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some folders cannot be kept open after a restart; scanning still works now.
        }
        val name = DocumentFile.fromTreeUri(app, uri)?.name ?: "Our wedding"
        screen = Screen.NewAlbum(uri, name)
        scan = ScanState()
        viewModelScope.launch {
            scan = withContext(Dispatchers.IO) {
                try {
                    val photos = FolderScanner.scan(app, uri) { count -> scan = scan.copy(found = count) }
                    val plan = photos.mapIndexed { i, p -> PlanPhoto(id = i.toLong(), name = p.name, folder = p.folder) }
                    val eventOf = AlbumPlanner.assignEvents(plan)
                    val order = AlbumPlanner.orderEvents(plan, eventOf)
                    val counts = eventOf.values.groupingBy { it }.eachCount()
                    ScanState(scanning = false, found = photos.size, photos = photos, events = order.map { it to (counts[it] ?: 0) })
                } catch (e: Exception) {
                    ScanState(scanning = false, error = "Could not read this folder. Please pick it again.")
                }
            }
        }
    }

    fun startAnalysis(name: String) {
        val s = screen as? Screen.NewAlbum ?: return
        val photos = scan.photos
        if (photos.isEmpty() || busy) return
        busy = true
        viewModelScope.launch {
            val projectId = withContext(Dispatchers.IO) {
                val id = dao.insertProject(
                    ProjectEntity(
                        name = name.trim().ifEmpty { s.name },
                        treeUri = s.treeUri.toString(),
                        createdAt = System.currentTimeMillis(),
                        albumSize = newAlbumSize,
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
                project?.let {
                    try {
                        app.contentResolver.releasePersistableUriPermission(Uri.parse(it.treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: SecurityException) {
                    }
                }
            }
            if (cachedAnalysis?.first == id) forgetAnalysis()
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

    fun album(projectId: Long): Flow<AlbumState?> =
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
        }

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
        val faceOf = persons.associate { it.person.id to FaceCrop(it.thumbPath, faceBox(it)) }
        val personById = persons.associateBy { it.person.id }
        val coverage = AlbumPlanner.coverage(analysis, selectedIds).associateBy { it.personId }
        val tags = analysis.tags

        fun chip(id: Long, label: String? = null): PersonChip {
            val entity = personById[id]?.person
            val c = coverage[id]
            val known = entity?.name?.isNotBlank() == true
            return PersonChip(
                personId = id,
                label = label ?: entity?.name?.takeIf { it.isNotBlank() } ?: "Who is this?",
                known = known || label != null,
                face = faceOf[id] ?: FaceCrop(null, null),
                inAlbum = analysis.photos.count { it.id in selectedIds && id in it.people },
                target = c?.target ?: 0,
                warning = c?.warning,
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
        val family = familyIds.map { chip(it) }
        val others = persons.asSequence()
            .map { it.person }
            .filter { it.id !in familyIds && !tags.isCouple(it.id) && it.priority != PersonPriority.SKIP && it.photoCount >= 3 }
            .sortedByDescending { it.photoCount }
            .take(30)
            .map { chip(it.id) }
            .toList()
        val warnings = family.filter { it.warning != null && it.inAlbum < it.target }.take(2).map { c ->
            val who = if (c.known) c.label else "Someone close to you"
            "$who: ${c.inAlbum} of ${c.target} photos. ${c.warning}."
        }

        val time = SimpleDateFormat("h:mm a", Locale.ENGLISH)
        val events = analysis.events.mapNotNull { e ->
            val items = analysis.photos.filter { it.event == e && it.id in selectedIds }.mapNotNull { albumPhoto(it.id) }
            if (items.isEmpty()) return@mapNotNull null
            val keys = analysis.moments.filter { it.event == e && it.key && it.start > 0 }
                .sortedBy { it.start }.map { "Key moment · " + time.format(Date(it.start)).lowercase() }.distinct().take(4)
            EventSection(e, Display.eventName(e), items, keys)
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
            cover = project.coverPhotoId?.let { albumPhoto(it) },
            bride = tags.brideId?.let { chip(it, personById[it]?.person?.name?.takeIf { n -> n.isNotBlank() } ?: "Bride") },
            groom = tags.groomId?.let { chip(it, personById[it]?.person?.name?.takeIf { n -> n.isNotBlank() } ?: "Groom") },
            family = family,
            others = others,
            warnings = warnings,
            events = events,
            leftOut = leftOut,
        )
    }

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
        }

    private fun viewerState(project: ProjectEntity, photos: List<PhotoEntity>, photoId: Long, analysis: AlbumAnalysis): ViewerState? {
        val byId = photos.associateBy { it.id }
        val p = byId[photoId] ?: return null
        val a = analysis.byId[photoId] ?: return null
        val exclude = photos.filter { it.selected || it.removed }.map { it.id }.toSet()
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
    fun remove(projectId: Long, photoId: Long) {
        viewModelScope.launch {
            val before = dao.photos(projectId).filter { it.selected }.map { it.id }.toSet()
            dao.removeFromShortlist(photoId)
            val project = dao.project(projectId) ?: return@launch
            val analysis = analysisFor(projectId)
            val added = withContext(Dispatchers.Default) {
                val photos = dao.photos(projectId)
                val keep = photos.filter { it.selected }.map { it.id }.toSet()
                val list = AlbumPlanner.shortlist(analysis, project.albumSize, photos.filter { it.removed }.map { it.id }.toSet(), keep)
                dao.replaceSelection(projectId, list.ids)
                val new = list.ids.filter { it !in before }
                dao.setPickReasons(new.associateWith { list.reasons[it].orEmpty().joinToString("\n") })
                if (project.coverPhotoId == photoId) dao.setCover(projectId, list.coverId)
                new
            }
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
                face = FaceCrop(p.thumbPath, faceBox(p)),
                samples = samples,
                role = when (personId) { project?.brideId -> "Bride"; project?.groomId -> "Groom"; else -> null },
                guessedSide = guessedSide,
            ).also { Log.d(TAG, "person $personId: ready, ${samples.size} samples") }
        }.catch { e ->
            Log.e(TAG, "person $personId could not be loaded", e)
            throw e
        }

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
        val label = name.trim().ifEmpty {
            when {
                relation.isEmpty() -> person.name
                side == PersonSide.BRIDE -> "Bride's ${relation.lowercase()}"
                side == PersonSide.GROOM -> "Groom's ${relation.lowercase()}"
                else -> relation
            }
        }
        editPeople(projectId, "Album updated for $label") {
            dao.updatePerson(person.copy(name = label, priority = priority.name, elder = elder, side = side))
        }
    }

    fun notFamily(projectId: Long, person: PersonEntity) =
        editPeople(projectId, "Album updated") { dao.updatePerson(person.copy(priority = PersonPriority.SKIP)) }

    fun setBride(projectId: Long, personId: Long) = editPeople(projectId, "Bride updated") {
        val p = dao.project(projectId) ?: return@editPeople
        dao.setCouple(projectId, personId, if (p.groomId == personId) p.brideId else p.groomId)
    }

    fun setGroom(projectId: Long, personId: Long) = editPeople(projectId, "Groom updated") {
        val p = dao.project(projectId) ?: return@editPeople
        dao.setCouple(projectId, if (p.brideId == personId) p.groomId else p.brideId, personId)
    }

    fun swapCouple(projectId: Long) = editPeople(projectId, "Swapped bride and groom") {
        val p = dao.project(projectId) ?: return@editPeople
        dao.setCouple(projectId, p.groomId, p.brideId)
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
