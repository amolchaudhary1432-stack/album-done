package com.albumdone.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var open by remember { mutableStateOf<ShortlistPhoto?>(null) }

    val s = state
    val albumSize = s?.project?.albumSize ?: 0
    val missing = (albumSize - (s?.selectedCount ?: 0)).coerceAtLeast(0)

    fun takeOut(item: ShortlistPhoto) {
        val photo = item.photo
        vm.removeFromShortlist(photo.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Took out ${photo.name}", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.undoRemove(photo.id)
        }
    }

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
                            OutlinedButton(onClick = { vm.replaceRemoved(projectId) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
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
                    Text("${s.selectedCount} of $albumSize photos picked", style = MaterialTheme.typography.titleMedium)
                    Text("Tap a photo to see why it was picked, or to take it out.", style = MaterialTheme.typography.bodyMedium)
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
                    Box(Modifier.aspectRatio(1f).clickable { open = item }) {
                        AsyncImage(
                            model = item.photo.thumbPath?.let { File(it) } ?: item.photo.docUri,
                            contentDescription = item.photo.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (item.cover) {
                            Row(
                                Modifier.align(Alignment.TopStart).background(Color(0xCC3B1F45)).padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF6C247))
                                Text(" Cover", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    open?.let { item ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(if (item.cover) "Cover photo" else "Why this photo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(
                        model = item.photo.thumbPath?.let { File(it) } ?: item.photo.docUri,
                        contentDescription = item.photo.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    )
                    Text(item.photo.name, style = MaterialTheme.typography.labelMedium)
                    val reasons = item.reasons.ifEmpty { listOf("Picked as one of the best photos of ${item.event}") }
                    for (r in reasons) Text("• $r", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Keep") } },
            dismissButton = { TextButton(onClick = { takeOut(item); open = null }) { Text("Take out") } },
        )
    }
}
