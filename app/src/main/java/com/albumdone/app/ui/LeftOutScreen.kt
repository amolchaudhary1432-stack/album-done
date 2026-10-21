package com.albumdone.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albumdone.app.plan.Display

private val wide: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/**
 * "What we left out": every photo not in the album, in the photographer's order, each with the
 * main reason. Tap one to look at it and add it back.
 */
@Composable
fun LeftOutScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.leftOut(projectId) }
    val photos by flow.collectAsStateWithLifecycle(initialValue = null)
    val host = remember { SnackbarHostState() }
    MessageHost(vm, host)
    var filter by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(topBar = { AppBar("What we left out", onBack = vm::back) }, snackbarHost = { SnackbarHost(host) }) { padding ->
        val all = photos
        if (all == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val counts = all.groupingBy { it.reason }.eachCount()
        val shown = all.filter { filter == null || it.reason.name == filter }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding).testTag("left-out-grid"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "intro", span = wide) {
                Text(
                    if (all.isEmpty()) "Every photo is in your album." else
                        "${"%,d".format(all.size)} photos are not in the album. Nothing is deleted: tap one to look at it and add it back.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                )
            }
            if (counts.size > 1) {
                item(key = "filters", span = wide) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        item { Pill(selected = filter == null, onClick = { filter = null }, label = { Text("All ${all.size}") }) }
                        items(LeftOutReason.entries.filter { (counts[it] ?: 0) > 0 }) { r ->
                            Pill(selected = filter == r.name, onClick = { filter = r.name }, label = { Text("${r.label} ${counts[r]}") },
                                modifier = Modifier.testTag("left-out-${r.name}"))
                        }
                    }
                }
            }
            var lastEvent: String? = null
            for (p in shown) {
                if (p.event != lastEvent) {
                    lastEvent = p.event
                    item(key = "event-${p.event}", span = wide) {
                        Text(Display.eventName(p.event), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp))
                    }
                }
                item(key = p.photo.id) {
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                        .clickable { vm.openPhoto(projectId, p.photo.id, fromLeftOut = true) }.testTag("left-out-photo")) {
                        PhotoThumb(p.photo, p.crop, Modifier.fillMaxSize())
                        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0x99000000))
                            .padding(horizontal = 6.dp, vertical = 3.dp)) {
                            Text(p.reason.label, color = Color.White, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
