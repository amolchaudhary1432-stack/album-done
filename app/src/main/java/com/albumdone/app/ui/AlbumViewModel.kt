package com.albumdone.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.albumdone.app.data.AppDatabase
import com.albumdone.app.data.PersonEntity
import com.albumdone.app.data.PersonWithFace
import com.albumdone.app.data.PhotoEntity
import com.albumdone.app.data.ProjectEntity
import com.albumdone.app.data.ProjectStatus
import com.albumdone.app.data.ProjectSummary
import com.albumdone.app.people.PeopleRepository
import com.albumdone.app.plan.AlbumAnalysis
import com.albumdone.app.plan.AlbumPlanner
import com.albumdone.app.plan.Coverage
import com.albumdone.app.plan.PlanPhoto
import com.albumdone.app.scan.FolderScanner
import com.albumdone.app.scan.FoundPhoto
import com.albumdone.app.work.AnalyzeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data class NewAlbum(val treeUri: Uri, val name: String) : Screen
    data class Progress(val projectId: Long) : Screen
    data class TagFamily(val projectId: Long) : Screen
    data class Results(val projectId: Long) : Screen
    data class Shortlist(val projectId: Long) : Screen
}

/** What the folder scan found, before analysis starts. */
data class ScanState(
    val scanning: Boolean = true,
    val found: Int = 0,
    val photos: List<FoundPhoto> = emptyList(),
    val events: Int = 0,
    val error: String? = null,
)

data class ResultsState(
    val project: ProjectEntity,
    val analysis: AlbumAnalysis,
    val shortlistCount: Int,
    /** Coverage of each Must / Important person: in the shortlist if there is one, else none yet. */
    val coverage: List<Coverage>,
)

data class ShortlistPhoto(val photo: PhotoEntity, val event: String, val reasons: List<String>, val cover: Boolean)

data class ShortlistState(
    val project: ProjectEntity?,
    /** Selected photos, in story order, grouped by event (in event order). */
    val byEvent: List<Pair<String, List<ShortlistPhoto>>>,
    val selectedCount: Int,
    /** There are photos left that could replace removed ones. */
    val canReplace: Boolean,
)

/** One row of the "Tag your family" screen. */
data class PersonRow(
    val person: PersonEntity,
    val thumbPath: String?,
    val faceLeft: Float, val faceTop: Float, val faceRight: Float, val faceBottom: Float,
    val role: String?,
)

data class FamilyState(
    val project: ProjectEntity?,
    val bride: PersonRow?,
    val groom: PersonRow?,
    /** The most photographed people after the couple. */
    val others: List<PersonRow>,
)

class AlbumViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).dao()
    private val people = PeopleRepository(dao)

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    var scan by mutableStateOf(ScanState())
        private set

    var results by mutableStateOf<ResultsState?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    /** A short message to show once (e.g. "Found 4 more photos of Aai"). */
    var message by mutableStateOf<String?>(null)

    val projects: StateFlow<List<ProjectSummary>> = dao.projectSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun projectSummary(id: Long) = dao.projectSummary(id)

    // The planner's view of a wedding. Worked out once and reused until the family tags change.
    private val analysisLock = Mutex()
    @Volatile private var cachedAnalysis: Pair<Long, AlbumAnalysis>? = null

    private suspend fun analysisFor(projectId: Long): AlbumAnalysis = analysisLock.withLock {
        cachedAnalysis?.let { (id, a) -> if (id == projectId) return@withLock a }
        val project = dao.project(projectId) ?: error("No such album")
        val (photos, tags) = people.planInput(project)
        val analysis = withContext(Dispatchers.Default) { AlbumPlanner.analyze(photos, tags) }
        cachedAnalysis = projectId to analysis
        analysis
    }

    private fun forgetAnalysis() { cachedAnalysis = null }

    // ---- Navigation ----

    fun back() {
        when (val s = screen) {
            is Screen.Shortlist -> showResults(s.projectId)
            is Screen.TagFamily -> showResults(s.projectId)
            else -> screen = Screen.Home
        }
    }

    fun openProject(id: Long) {
        viewModelScope.launch {
            val project = dao.project(id) ?: return@launch
            when {
                project.status != ProjectStatus.DONE -> {
                    // Starts the work again if Android stopped it; does nothing if it is running.
                    AnalyzeWorker.start(getApplication(), id)
                    screen = Screen.Progress(id)
                }
                !project.familyTagged -> screen = Screen.TagFamily(id)
                else -> showResults(id)
            }
        }
    }

    /** Analysis finished: next comes tagging the family (once), then the results. */
    fun onAnalysisDone(projectId: Long) {
        forgetAnalysis()
        openProject(projectId)
    }

    fun showResults(projectId: Long) {
        screen = Screen.Results(projectId)
        if (results?.project?.id != projectId) results = null
        viewModelScope.launch {
            val project = dao.project(projectId) ?: return@launch
            val analysis = analysisFor(projectId)
            val selected = withContext(Dispatchers.IO) { dao.photos(projectId).filter { it.selected }.map { it.id } }
            results = ResultsState(project, analysis, selected.size, AlbumPlanner.coverage(analysis, selected))
        }
    }

    fun openTagFamily(projectId: Long) {
        screen = Screen.TagFamily(projectId)
    }

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
                    val events = AlbumPlanner.assignEvents(
                        photos.mapIndexed { i, p -> PlanPhoto(id = i.toLong(), name = p.name, folder = p.folder) }
                    ).values.distinct().size
                    ScanState(scanning = false, found = photos.size, photos = photos, events = events)
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
                    ProjectEntity(name = name.trim().ifEmpty { s.name }, treeUri = s.treeUri.toString(), createdAt = System.currentTimeMillis())
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

    // ---- Tag your family ----

    fun family(projectId: Long): Flow<FamilyState> =
        combine(dao.peopleWithFaces(projectId), dao.projectSummary(projectId)) { list, summary ->
            val project = summary?.project
            fun row(p: PersonWithFace) = PersonRow(
                p.person, p.thumbPath, p.faceLeft ?: 0f, p.faceTop ?: 0f, p.faceRight ?: 1f, p.faceBottom ?: 1f,
                when (p.person.id) { project?.brideId -> "Bride"; project?.groomId -> "Groom"; else -> null },
            )
            val rows = list.map { row(it) }
            FamilyState(
                project = project,
                bride = rows.firstOrNull { it.person.id == project?.brideId },
                groom = rows.firstOrNull { it.person.id == project?.groomId },
                others = rows.filter { it.role == null }.take(TOP_PEOPLE),
            )
        }.flowOn(Dispatchers.Default)

    private fun editPeople(projectId: Long, block: suspend () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            forgetAnalysis()
            if (cachedAnalysis == null && screen is Screen.Results) showResults(projectId)
        }
    }

    fun updatePerson(person: PersonEntity) = editPeople(person.projectId) { dao.updatePerson(person) }

    fun setBride(projectId: Long, personId: Long) = editPeople(projectId) {
        val p = dao.project(projectId) ?: return@editPeople
        dao.setCouple(projectId, personId, if (p.groomId == personId) p.brideId else p.groomId)
    }

    fun setGroom(projectId: Long, personId: Long) = editPeople(projectId) {
        val p = dao.project(projectId) ?: return@editPeople
        dao.setCouple(projectId, if (p.brideId == personId) p.groomId else p.brideId, personId)
    }

    fun swapCouple(projectId: Long) = editPeople(projectId) {
        val p = dao.project(projectId) ?: return@editPeople
        dao.setCouple(projectId, p.groomId, p.brideId)
    }

    fun merge(projectId: Long, keep: Long, gone: Long) = editPeople(projectId) { people.merge(projectId, keep, gone) }

    fun split(projectId: Long, personId: Long) = editPeople(projectId) {
        val parts = people.split(projectId, personId)
        message = if (parts > 1) "Separated into $parts people" else "These faces look like one person"
    }

    fun findMore(projectId: Long, personId: Long) = editPeople(projectId) {
        val n = people.findMore(projectId, personId)
        message = if (n > 0) "Found $n more photo${if (n == 1) "" else "s"}" else "No more photos found"
    }

    fun finishTagging(projectId: Long) {
        viewModelScope.launch {
            dao.setFamilyTagged(projectId)
            forgetAnalysis()
            showResults(projectId)
        }
    }

    // ---- Shortlist ----

    fun makeShortlist(projectId: Long, size: Int) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val analysis = analysisFor(projectId)
            withContext(Dispatchers.Default) {
                val photos = dao.photos(projectId)
                val removed = photos.filter { it.removed }.map { it.id }.toSet()
                val list = AlbumPlanner.shortlist(analysis, size, removed)
                dao.setAlbumSize(projectId, size)
                dao.replaceSelection(projectId, list.ids)
                dao.setPickReasons(list.ids.associateWith { list.reasons[it].orEmpty().joinToString("\n") })
                dao.setCover(projectId, list.coverId)
            }
            busy = false
            screen = Screen.Shortlist(projectId)
        }
    }

    fun openShortlist(projectId: Long) {
        screen = Screen.Shortlist(projectId)
    }

    fun shortlist(projectId: Long): Flow<ShortlistState> =
        dao.photosFlow(projectId).map { photos ->
            val project = dao.project(projectId)
            val analysis = analysisFor(projectId)
            val selected = photos.filter { it.selected }.associateBy { it.id }
            val ordered = analysis.photos.filter { it.id in selected }
            val byEvent = analysis.events.mapNotNull { event ->
                val items = ordered.filter { it.event == event }.map {
                    val p = selected.getValue(it.id)
                    ShortlistPhoto(p, event, p.pickReason?.split('\n')?.filter(String::isNotBlank).orEmpty(), p.id == project?.coverPhotoId)
                }
                if (items.isEmpty()) null else event to items
            }
            ShortlistState(
                project = project,
                byEvent = byEvent,
                selectedCount = selected.size,
                canReplace = photos.any { !it.selected && !it.removed && !it.failed },
            )
        }.flowOn(Dispatchers.Default)

    fun removeFromShortlist(photoId: Long) {
        viewModelScope.launch { dao.removeFromShortlist(photoId) }
    }

    fun undoRemove(photoId: Long) {
        viewModelScope.launch { dao.restoreToShortlist(photoId) }
    }

    fun replaceRemoved(projectId: Long) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val analysis = analysisFor(projectId)
            withContext(Dispatchers.Default) {
                val project = dao.project(projectId) ?: return@withContext
                val photos = dao.photos(projectId)
                val keep = photos.filter { it.selected }.map { it.id }.toSet()
                val list = AlbumPlanner.shortlist(
                    analysis, project.albumSize,
                    removed = photos.filter { it.removed }.map { it.id }.toSet(),
                    keep = keep,
                )
                dao.replaceSelection(projectId, list.ids)
                // Kept photos keep their original reasons; new ones get theirs.
                dao.setPickReasons(list.ids.filter { it !in keep }.associateWith { list.reasons[it].orEmpty().joinToString("\n") })
                if (project.coverPhotoId !in keep) dao.setCover(projectId, list.coverId)
            }
            busy = false
        }
    }

    /** Plain text for the photographer: event headings, then file names. */
    fun shareText(state: ShortlistState): String = buildString {
        append("Album selection: ").append(state.project?.name ?: "").append('\n')
        append(state.selectedCount).append(" photos\n")
        for ((event, items) in state.byEvent) {
            append('\n').append(event).append(" (").append(items.size).append(")\n")
            for (item in items) {
                val p = item.photo
                if (p.folder.isEmpty()) append(p.name) else append(p.folder).append('/').append(p.name)
                if (item.cover) append("  (cover)")
                append('\n')
            }
        }
    }

    companion object {
        /** People shown on the tagging screen besides the couple. */
        const val TOP_PEOPLE = 40
    }
}
