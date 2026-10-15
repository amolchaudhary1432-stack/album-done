package com.albumdone.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.albumdone.app.data.PersonSide
import java.io.File

private val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/** Everyone in the photos, most photographed first, with filters for a big family. */
@Composable
fun PeopleScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.album(projectId) }
    val state by flow.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageHost(vm, host)
    var filter by rememberSaveable { mutableStateOf("all") }
    var personSheet by remember { mutableStateOf<Long?>(null) }

    Scaffold(topBar = { AppBar("Family & friends", onBack = vm::back) }, snackbarHost = { SnackbarHost(host) }) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val couple = listOfNotNull(s.bride, s.groom)
        val rest = s.family + s.others
        val everyone = couple + rest
        val filters = listOf(
            "all" to "All ${everyone.size}",
            PersonSide.BRIDE to "Bride's side ${rest.count { it.side == PersonSide.BRIDE }}",
            PersonSide.GROOM to "Groom's side ${rest.count { it.side == PersonSide.GROOM }}",
            "unnamed" to "Not named ${rest.count { !it.known }}",
        )
        val shown = when (filter) {
            "all" -> everyone
            "unnamed" -> rest.filter { !it.known }
            else -> rest.filter { it.side == filter }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.fillMaxSize().padding(padding).testTag("people-grid"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "intro", span = fullSpan) {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Most photographed first. Tap a face to say who they are, join two that are the same person, or leave someone out.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                    if (s.toName.isNotEmpty()) QuickTagCard(s.toName, { vm.openQuickTag(projectId) })
                }
            }
            item(key = "filters", span = fullSpan) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    items(filters, key = { it.first }) { (value, label) ->
                        FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) }, modifier = Modifier.testTag("filter-$value"))
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty", span = fullSpan) {
                    Text("Nobody here yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
            items(shown, key = { it.personId }) { c -> PersonTile(c) { personSheet = c.personId } }
        }
        personSheet?.let { id -> PersonSheet(vm, projectId, id, s, onClose = { personSheet = null }) }
    }
}

@Composable
private fun PersonTile(c: PersonChip, onClick: () -> Unit) {
    val low = c.warning != null && c.inAlbum < c.target
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp).testTag("person-tile"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            FaceAvatar(c.face, 84.dp)
            if (low) DotBadge("!", MaterialTheme.colorScheme.error, Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(6.dp))
        Text(c.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (c.known) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            if (c.inAlbum > 0) "${c.inAlbum} in album" else "${c.photoCount} photos",
            style = MaterialTheme.typography.labelMedium,
            color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val QUICK_RELATIONS = listOf(
    "Mother", "Father", "Grandmother", "Grandfather", "Sister", "Brother",
    "Aunt", "Uncle", "Cousin", "Other relative", "Friend",
)

/**
 * "Who's who": one face at a time, one tap each. A big Indian family can be 40 faces; this takes
 * about a minute and nobody has to fill in a form.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun QuickTagScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.album(projectId) }
    val state by flow.collectAsStateWithLifecycle()
    // The faces are fixed when the screen opens, so naming someone does not reshuffle the rest.
    var queue by remember { mutableStateOf<List<PersonChip>?>(null) }
    LaunchedEffect(state) { if (queue == null) state?.let { queue = it.toName } }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var named by rememberSaveable { mutableIntStateOf(0) }
    val q = queue
    val current = q?.getOrNull(index)

    Scaffold(
        topBar = { AppBar("Who's who", onBack = vm::back) },
        bottomBar = {
            if (current != null) {
                BottomBar {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { vm.quickNotFamily(current.personId); named++; index++ },
                            modifier = Modifier.weight(1f).height(52.dp).testTag("quick-not-family"), shape = RoundedCornerShape(26.dp),
                        ) { Text("Not family") }
                        TextButton(
                            onClick = { index++ },
                            modifier = Modifier.weight(1f).height(52.dp).testTag("quick-skip"),
                        ) { Text("Don't know, skip") }
                    }
                }
            }
        },
    ) { padding ->
        if (q == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (current == null) {
            QuickTagDone(named, Modifier.padding(padding)) { vm.finishQuickTag(projectId) }
            return@Scaffold
        }
        AnimatedContent(
            targetState = index,
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
            label = "face",
            modifier = Modifier.padding(padding),
        ) { i ->
            val c = q.getOrNull(i)
            if (c != null) {
                FaceQuestion(vm, c, i, q.size) { side, relation ->
                    vm.quickTag(c.personId, side, relation)
                    named++
                    index++
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FaceQuestion(vm: AlbumViewModel, c: PersonChip, i: Int, total: Int, onAnswer: (side: String, relation: String) -> Unit) {
    var side by remember(c.personId) { mutableStateOf(c.side) }
    val samples by produceState(emptyList<String>(), c.personId) { value = vm.samples(c.personId) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(
                progress = { i.toFloat() / total },
                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Text("${i + 1} of $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FaceAvatar(c.face, 120.dp, ring = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Who is this?", style = MaterialTheme.typography.headlineMedium)
                Text("In ${c.photoCount} photos", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (samples.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (path in samples) {
                    AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
                }
                repeat(3 - samples.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        SectionLabel("Whose side")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, label) in listOf(PersonSide.BRIDE to "Bride's side", PersonSide.GROOM to "Groom's side")) {
                FilterChip(selected = side == value, onClick = { side = if (side == value) PersonSide.UNKNOWN else value }, label = { Text(label) })
            }
        }
        SectionLabel("Tap one")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in QUICK_RELATIONS) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable { onAnswer(side, r) }.testTag("quick-$r"),
                ) {
                    Text(r, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QuickTagDone(named: Int, modifier: Modifier, onFinish: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(48.dp))
        }
        Text("All done", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (named > 0) "Thank you. Your album is remade with the right people in it." else "Nothing changed. You can name people any time from Family & friends.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        BigButton("See your album", onClick = onFinish)
    }
}
