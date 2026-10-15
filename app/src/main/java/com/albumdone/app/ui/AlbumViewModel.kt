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
import com.albumdone.app.data.PhotoEntity
import com.albumdone.app.data.ProjectEntity
import com.albumdone.app.data.ProjectStatus
import com.albumdone.app.data.ProjectSummary
import com.albumdone.app.plan.AlbumAnalysis
import com.albumdone.app.plan.AlbumPlanner
import com.albumdone.app.plan.PlanPhoto
import com.albumdone.app.scan.FolderScanner
import com.albumdone.app.scan.FoundPhoto
import com.albumdone.app.work.AnalyzeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data class NewAlbum(val treeUri: Uri, val name: String) : Screen
    data class Progress(val projectId: Long) : Screen
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
)

data class ShortlistPhoto(val photo: PhotoEntity, val event: String)

data class ShortlistState(
    val project: ProjectEntity?,
    /** Selected photos, in capture order, grouped by event (in event order). */
    val byEvent: List<Pair<String, List<ShortlistPhoto>>>,
    val selectedCount: Int,
    /** Clean or not, photos that are neither picked, removed, nor unreadable. */
    val canReplace: Boolean,
)

class AlbumViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).dao()

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    var scan by mutableStateOf(ScanState())
        private set

    var results by mutableStateOf<ResultsState?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    val projects: StateFlow<List<ProjectSummary>> = dao.projectSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun projectSummary(id: Long) = dao.projectSummary(id)

    // The planner's view of a finished wedding. Measurements never change after analysis,
    // so it is worked out once per project and reused.
    @Volatile private var cachedAnalysis: Pair<Long, AlbumAnalysis>? = null

    private fun analysisFor(projectId: Long, photos: List<PhotoEntity>): AlbumAnalysis {
        cachedAnalysis?.let { (id, a) -> if (id == projectId && a.photos.size == photos.size) return a }
        val analysis = AlbumPlanner.analyze(photos.map { it.toPlanPhoto() })
        cachedAnalysis = projectId to analysis
        return analysis
    }

    // ---- Navigation ----

    fun back() {
        screen = when (val s = screen) {
            is Screen.Shortlist -> Screen.Results(s.projectId)
            else -> Screen.Home
        }
        if (screen is Screen.Results) loadResults((screen as Screen.Results).projectId)
    }

    fun openProject(id: Long) {
        viewModelScope.launch {
            val project = dao.project(id) ?: return@launch
            if (project.status == ProjectStatus.DONE) {
                showResults(id)
            } else {
                // Starts the work again if Android stopped it; does nothing if it is running.
                AnalyzeWorker.start(getApplication(), id)
                screen = Screen.Progress(id)
            }
        }
    }

    fun showResults(projectId: Long) {
        screen = Screen.Results(projectId)
        loadResults(projectId)
    }

    private fun loadResults(projectId: Long) {
        if (results?.project?.id != projectId) results = null
        viewModelScope.launch {
            val project = dao.project(projectId) ?: return@launch
            val state = withContext(Dispatchers.Default) {
                val photos = dao.photos(projectId)
                ResultsState(project, analysisFor(projectId, photos), photos.count { it.selected })
            }
            results = state
        }
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
                        photos.mapIndexed { i, p -> blankPlanPhoto(i.toLong(), p.name, p.folder) }
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
                    ProjectEntity(
                        name = name.trim().ifEmpty { s.name },
                        treeUri = s.treeUri.toString(),
                        createdAt = System.currentTimeMillis(),
                    )
                )
                photos.chunked(1000).forEach { chunk ->
                    dao.insertPhotos(chunk.map {
                        PhotoEntity(
                            projectId = id,
                            docUri = it.docUri.toString(),
                            name = it.name,
                            folder = it.folder,
                            size = it.size,
                            lastModified = it.lastModified,
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
                        app.contentResolver.releasePersistableUriPermission(
                            Uri.parse(it.treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (_: SecurityException) {
                    }
                }
            }
            if (cachedAnalysis?.first == id) cachedAnalysis = null
        }
    }

    // ---- Shortlist ----

    fun makeShortlist(projectId: Long, size: Int) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val photos = dao.photos(projectId)
                val analysis = analysisFor(projectId, photos)
                val removed = photos.filter { it.removed }.map { it.id }.toSet()
                val ids = AlbumPlanner.shortlist(analysis, size, removed)
                dao.setAlbumSize(projectId, size)
                dao.replaceSelection(projectId, ids)
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
            val analysis = analysisFor(projectId, photos)
            val selected = photos.filter { it.selected }.associateBy { it.id }
            val ordered = analysis.photos.filter { it.id in selected }
            val byEvent = analysis.events.mapNotNull { event ->
                val items = ordered.filter { it.event == event }
                    .map { ShortlistPhoto(selected.getValue(it.id), event) }
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
            withContext(Dispatchers.Default) {
                val project = dao.project(projectId) ?: return@withContext
                val photos = dao.photos(projectId)
                val analysis = analysisFor(projectId, photos)
                val ids = AlbumPlanner.shortlist(
                    analysis,
                    project.albumSize,
                    removed = photos.filter { it.removed }.map { it.id }.toSet(),
                    keep = photos.filter { it.selected }.map { it.id }.toSet(),
                )
                dao.replaceSelection(projectId, ids)
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
                append('\n')
            }
        }
    }

    private fun PhotoEntity.toPlanPhoto() = PlanPhoto(
        id = id,
        name = name,
        folder = folder,
        takenAt = takenAt,
        sharpness = sharpness,
        brightness = brightness,
        darkFraction = darkFraction,
        brightFraction = brightFraction,
        hash = hash,
        faceCount = faceCount,
        smile = smile,
        eyesClosed = eyesClosed,
        failed = failed || !processed,
    )

    private fun blankPlanPhoto(id: Long, name: String, folder: String) = PlanPhoto(
        id = id, name = name, folder = folder, takenAt = 0, sharpness = 0.0, brightness = 0.0,
        darkFraction = 0.0, brightFraction = 0.0, hash = 0, faceCount = 0, smile = -1f, eyesClosed = false,
    )
}
