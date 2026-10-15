package com.albumdone.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {

    private val viewModel: AlbumViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            AlbumDoneTheme { AppContent(viewModel) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Opening the app from a notification goes straight to that album. */
    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_PROJECT_ID, -1L) ?: -1L
        if (id > 0) viewModel.openProject(id)
    }

    companion object {
        const val EXTRA_PROJECT_ID = "projectId"
    }
}

@Composable
private fun AppContent(vm: AlbumViewModel) {
    val screen = vm.screen
    BackHandler(enabled = screen != Screen.Home) { vm.back() }
    when (screen) {
        Screen.Home -> HomeScreen(vm)
        is Screen.NewAlbum -> NewAlbumScreen(vm, screen)
        is Screen.Progress -> ProgressScreen(vm, screen.projectId)
        is Screen.Album -> AlbumScreen(vm, screen.projectId)
        is Screen.Viewer -> ViewerScreen(vm, screen.projectId, screen.photoId)
        is Screen.Send -> SendScreen(vm, screen.projectId)
    }
}
