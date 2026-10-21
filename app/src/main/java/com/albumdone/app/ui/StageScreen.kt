package com.albumdone.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.albumdone.app.data.PersonSide

private val wholeRow: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/** On the album: how many family groups were photographed with the couple, and how many are in. */
@Composable
fun StageCard(s: AlbumState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val inAlbum = s.stage.count { it.inAlbum }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onOpen).testTag("stage-card"),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(80.dp)) {
                s.stage.take(3).forEachIndexed { i, item ->
                    PhotoThumb(item.photo.photo, item.photo.crop,
                        Modifier.padding(start = (i * 20).dp).size(44.dp).clip(RoundedCornerShape(10.dp))
                            .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp)))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Family groups", style = MaterialTheme.typography.titleSmall)
                Text("${s.stage.size} groups photographed with ${if (s.occasion.couple) "you" else s.mainWords} · $inAlbum in your album",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(LineIcons.Next, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Every family group photographed with the couple (on stage, at the mandap), one best photo each:
 * tick who goes in the album, add every family at once, or name a whole family in one go.
 */
@Composable
fun StageScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.album(projectId) }
    val state by flow.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageHost(vm, host)
    var naming by remember { mutableStateOf<StageItem?>(null) }

    Scaffold(topBar = { AppBar("Family groups", onBack = vm::back) }, snackbarHost = { SnackbarHost(host) }) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val items = s.stage
        val inAlbum = items.count { it.inAlbum }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).testTag("stage-grid"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "intro", span = wholeRow) {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${items.size} groups were photographed with ${s.mainWords}. $inAlbum are in your album.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap a photo to put it in or take it out. Name a family once and everyone in it is named.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (inAlbum < items.size) {
                        Button(onClick = { vm.addAllGroups(projectId, items) }, shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp).testTag("add-all-groups")) {
                            Icon(LineIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add every family (${items.size - inAlbum} more)")
                        }
                    }
                }
            }
            items(items, key = { it.frames.first() }) { item ->
                StageTile(item, onToggle = { vm.setGroupInAlbum(projectId, item, !item.inAlbum) }, onName = { naming = item })
            }
        }
        naming?.let { item ->
            NameGroupSheet(
                item,
                couple = s.occasion.couple,
                onSave = { side, name -> vm.tagGroup(projectId, item.guests.map { it.personId }, side, name, notFamily = false); naming = null },
                onNotFamily = { vm.tagGroup(projectId, item.guests.map { it.personId }, PersonSide.UNKNOWN, "", notFamily = true); naming = null },
                onClose = { naming = null },
            )
        }
    }
}

@Composable
private fun StageTile(item: StageItem, onToggle: () -> Unit, onName: () -> Unit) {
    val named = item.guests.filter { it.known }.map { it.label }.distinct()
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onToggle).testTag("stage-tile")) {
            PhotoThumb(item.photo.photo, item.photo.crop, Modifier.fillMaxSize())
            val tick = Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).clip(CircleShape)
            if (item.inAlbum) {
                Box(tick.background(Saffron), contentAlignment = Alignment.Center) {
                    Icon(LineIcons.Tick, contentDescription = "In your album", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            } else {
                Box(tick.background(Color(0x55000000)).border(2.dp, Color.White, CircleShape))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width((22 + 16 * (minOf(item.guests.size, 4) - 1).coerceAtLeast(0)).dp)) {
                item.guests.take(4).forEachIndexed { i, c ->
                    Box(Modifier.padding(start = (i * 16).dp).border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)) {
                        FaceAvatar(c.face, 22.dp)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    named.size == 1 -> named.first()
                    named.isNotEmpty() -> named.joinToString(", ")
                    else -> "${item.guests.size} ${if (item.guests.size == 1) "person" else "people"}"
                },
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
        }
        TextButton(onClick = onName, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.testTag("name-group")) {
            Text(if (named.isEmpty()) "Who are they?" else "Change", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Names everyone in one family group at once. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NameGroupSheet(
    item: StageItem,
    couple: Boolean,
    onSave: (side: String, name: String) -> Unit,
    onNotFamily: () -> Unit,
    onClose: () -> Unit,
) {
    val guessed = item.guests.map { it.side }.filter { it != PersonSide.UNKNOWN }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    // A wedding has two families; a birthday or a trip just has family and friends.
    val choices = if (couple) listOf(PersonSide.BRIDE to "Bride's family", PersonSide.GROOM to "Groom's family", PersonSide.UNKNOWN to "Friends")
        else listOf(FAMILY to "Family", PersonSide.UNKNOWN to "Friends")
    var side by rememberSaveable(item.photo.photo.id) { mutableStateOf(guessed ?: choices.first().first) }
    var name by rememberSaveable(item.photo.photo.id) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Who are they?", style = MaterialTheme.typography.headlineSmall)
            PhotoThumb(item.photo.photo, item.photo.crop, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in item.guests) FaceAvatar(c.face, 44.dp)
            }
            Text("All ${item.guests.size} ${if (item.guests.size == 1) "person is" else "people are"} named at once.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in choices) {
                    Pill(selected = side == value, onClick = { side = value }, label = { Text(label) }, modifier = Modifier.testTag("group-side-$value"))
                }
            }
            OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                label = { Text("Family name (optional), e.g. Mama's family") }, modifier = Modifier.fillMaxWidth())
            BigButton("Save", onClick = {
                if (side == FAMILY) onSave(PersonSide.UNKNOWN, name.ifBlank { "Family" }) else onSave(side, name)
            }, modifier = Modifier.testTag("save-group"))
            TextButton(onClick = onNotFamily, modifier = Modifier.fillMaxWidth()) { Text("Not family. Show them less") }
        }
    }
}

/** "Family" when a folder has no bride's and groom's sides (saved as no side, named Family). */
private const val FAMILY = "FAMILY"
