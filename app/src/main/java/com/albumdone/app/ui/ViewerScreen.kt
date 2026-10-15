package com.albumdone.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albumdone.app.plan.Display

@Composable
fun ViewerScreen(vm: AlbumViewModel, projectId: Long, photoId: Long) {
    val flow = remember(projectId, photoId) { vm.viewer(projectId, photoId) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    val host = remember { SnackbarHostState() }
    MessageHost(vm, host)

    Scaffold(
        topBar = { AppBar(state?.photo?.event?.let { Display.eventName(it) } ?: "", onBack = vm::back) },
        snackbarHost = { SnackbarHost(host) },
        bottomBar = {
            val s = state
            if (s != null && s.inAlbum) {
                Surface(tonalElevation = 2.dp) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { vm.remove(projectId, photoId) },
                            modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp),
                        ) { Text("Remove") }
                        Button(onClick = vm::back, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Keep") }
                    }
                }
            }
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            PhotoThumb(s.photo.photo, 0f to 0f, Modifier.fillMaxWidth().heightIn(max = 420.dp).aspectRatio(
                if (s.photo.photo.width > 0 && s.photo.photo.height > 0) s.photo.photo.width.toFloat() / s.photo.photo.height else 1.5f,
                matchHeightConstraintsFirst = true,
            ).align(Alignment.CenterHorizontally), contentScale = ContentScale.Fit)
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s.photo.cover) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                        Text("Your album cover", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                }
                Text("WHY IT'S IN YOUR ALBUM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val reasons = s.photo.reasons.ifEmpty { listOf("One of the best photos of ${Display.eventName(s.photo.event)}") }
                for (r in reasons) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("✓", color = MaterialTheme.colorScheme.secondary)
                        Text(r, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(s.photo.photo.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.inAlbum && s.alternatives.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("PREFER ANOTHER ONE? TAP TO SWAP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(s.alternatives, key = { it.photo.id }) { alt ->
                            Column(Modifier.width(104.dp).clickable { vm.swap(projectId, photoId, alt.photo.id) }.testTag("alternative")) {
                                PhotoThumb(alt.photo, alt.crop, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                                Text(alt.note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SendScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.album(projectId) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    Scaffold(
        topBar = { AppBar("Send to photographer", onBack = vm::back) },
        bottomBar = {
            val s = state
            if (s != null) {
                Surface(tonalElevation = 2.dp) {
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "Album selection: ${s.project.name}")
                                .putExtra(Intent.EXTRA_TEXT, vm.shareText(s))
                            context.startActivity(Intent.createChooser(send, "Send list to photographer"))
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share list", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        val s = state
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your photographer gets a simple list, event by event, with the exact file names. It works in WhatsApp or email.",
                style = MaterialTheme.typography.bodyLarge)
            if (s != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f, fill = false)) {
                    SelectionContainer {
                        Text(vm.shareText(s), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }
        }
    }
}
