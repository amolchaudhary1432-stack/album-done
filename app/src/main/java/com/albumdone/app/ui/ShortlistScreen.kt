package com.albumdone.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ShortlistScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.shortlist(projectId) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val s = state
    val albumSize = s?.project?.albumSize ?: 0
    val missing = (albumSize - (s?.selectedCount ?: 0)).coerceAtLeast(0)

    Scaffold(
        topBar = { AppBar(s?.project?.name ?: "Shortlist", onBack = vm::back) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (s != null) {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (missing > 0 && s.canReplace) {
                            OutlinedButton(
                                onClick = { vm.replaceRemoved(projectId) },
                                enabled = !vm.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Text("  Replace removed ($missing)")
                            }
                        }
                        Button(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, "Album selection: ${s.project?.name ?: ""}")
                                    .putExtra(Intent.EXTRA_TEXT, vm.shareText(s))
                                context.startActivity(Intent.createChooser(send, "Send list to photographer"))
                            },
                            enabled = s.selectedCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text("  Send list to photographer")
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (s == null) return@Scaffold
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        "${s.selectedCount} of $albumSize photos picked",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("Tap a photo to take it out.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            for ((event, photos) in s.byEvent) {
                item(key = "event-$event", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)) {
                        Text(event, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${photos.size}", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(photos, key = { it.photo.id }) { item ->
                    val photo = item.photo
                    AsyncImage(
                        model = photo.thumbPath?.let { File(it) } ?: photo.docUri,
                        contentDescription = photo.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable {
                                vm.removeFromShortlist(photo.id)
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val result = snackbar.showSnackbar(
                                        "Took out ${photo.name}",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) vm.undoRemove(photo.id)
                                }
                            },
                    )
                }
            }
        }
    }
}
