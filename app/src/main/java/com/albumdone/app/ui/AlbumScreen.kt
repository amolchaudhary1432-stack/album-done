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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albumdone.app.data.PersonEntity
import com.albumdone.app.data.PersonSide
import com.albumdone.app.plan.AlbumSize

/** Shows the message the view model has for the couple, with Undo when there is one. */
@Composable
fun MessageHost(vm: AlbumViewModel, host: SnackbarHostState) {
    LaunchedEffect(vm.messageId) {
        val text = vm.message ?: return@LaunchedEffect
        val undo = vm.undo
        host.currentSnackbarData?.dismiss()
        val r = host.showSnackbar(text, actionLabel = if (undo != null) "Undo" else null, duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) undo?.invoke()
    }
}

/** Faces shown in the album's family strip before "See all". */
private const val STRIP_SIZE = 10

@Composable
fun AlbumScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.album(projectId) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    val host = remember { SnackbarHostState() }
    MessageHost(vm, host)
    var personSheet by remember { mutableStateOf<Long?>(null) }
    var settingsSheet by remember { mutableStateOf(false) }
    var chooseGroom by remember { mutableStateOf(false) }
    val s = state
    if (s?.cover != null) LightStatusBarIcons()

    Scaffold(
        // The cover photo runs up behind the status bar; the rest keeps clear of the phone's bars itself.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(host, Modifier.navigationBarsPadding()) },
        floatingActionButton = {
            if (s != null) {
                ExtendedFloatingActionButton(
                    onClick = { vm.openSend(projectId) },
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                    text = { Text("Send list to photographer", style = MaterialTheme.typography.titleMedium) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        if (s == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Making your album…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding).testTag("album-grid"),
            contentPadding = PaddingValues(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "hero", span = full) {
                Hero(s, onBack = vm::back, onMenu = { settingsSheet = true }, onOpenCover = { s.cover?.let { vm.openPhoto(projectId, it.photo.id) } })
            }
            item(key = "couple", span = full) {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    CoupleCard(s, onTap = { personSheet = it }, onSwap = { vm.swapCouple(projectId) },
                        onConfirm = { vm.confirmCouple(projectId) }, onChoose = { chooseGroom = true })
                }
            }
            item(key = "family", span = full) {
                FamilySection(s, onPerson = { personSheet = it }, onSeeAll = { vm.openPeople(projectId) }, onQuickTag = { vm.openQuickTag(projectId) })
            }
            val titled = s.events.size > 1
            for (e in s.events) {
                item(key = "event-${e.name}", span = full) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                        Text(if (titled) e.title else "Your album", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        Text("${e.photos.size} photos", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(e.photos, key = { it.photo.id }) { p ->
                    Box(Modifier.aspectRatio(1f).clickable { vm.openPhoto(projectId, p.photo.id) }.testTag("album-photo")) {
                        PhotoThumb(p.photo, p.crop, Modifier.fillMaxSize())
                        if (p.cover) {
                            Icon(Icons.Default.Star, contentDescription = "Cover", tint = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
                        }
                    }
                }
            }
        }

        personSheet?.let { id -> PersonSheet(vm, projectId, id, s, onClose = { personSheet = null }) }
        if (settingsSheet) SettingsSheet(vm, projectId, s, onClose = { settingsSheet = false })
        if (chooseGroom) ChooseGroomSheet(s, onPick = { chooseGroom = false; vm.setGroom(projectId, it) }, onClose = { chooseGroom = false })
    }
}

private fun summaryLine(s: AlbumState): String =
    "${s.total} photos" + (if (s.events.size > 1) " · ${s.events.size} events" else "") + " · ready to send"

/** The cover, edge to edge, with the album's name on it. */
@Composable
private fun Hero(s: AlbumState, onBack: () -> Unit, onMenu: () -> Unit, onOpenCover: () -> Unit) {
    val cover = s.cover
    if (cover == null) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, contentDescription = "Album size and more") }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(s.project.name, style = MaterialTheme.typography.headlineLarge)
                Text(summaryLine(s), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    // The photo's own shape, within limits, so the cover is not cut badly and not too tall.
    val shape = if (cover.photo.width > 0 && cover.photo.height > 0) {
        (cover.photo.width.toFloat() / cover.photo.height).coerceIn(0.9f, 1.5f)
    } else 1.25f
    Box(Modifier.fillMaxWidth().aspectRatio(shape).clickable(onClick = onOpenCover).testTag("cover")) {
        PhotoThumb(cover.photo, cover.crop, Modifier.fillMaxSize())
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0x8C000000), Color.Transparent))))
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.55f).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9120B15)))),
        )
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, contentDescription = "Album size and more", tint = Color.White) }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
            Surface(color = Color(0x40FFFFFF), shape = RoundedCornerShape(50)) {
                Text("COVER", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(s.project.name, style = MaterialTheme.typography.headlineLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(summaryLine(s), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FamilySection(s: AlbumState, onPerson: (Long) -> Unit, onSeeAll: () -> Unit, onQuickTag: () -> Unit) {
    val everyone = s.family.size + s.others.size
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Family in your album", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (s.family.isEmpty()) "Tap See all to choose who matters" else "Picked from who is with you all day",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onSeeAll, modifier = Modifier.testTag("see-all-people")) {
                Text("See all $everyone")
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        val strip = s.family.take(STRIP_SIZE)
        if (strip.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(strip, key = { it.personId }) { c -> PersonChipView(c) { onPerson(c.personId) } }
                if (s.family.size > STRIP_SIZE) {
                    item {
                        Column(Modifier.width(72.dp).clickable(onClick = onSeeAll), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                Text("+${s.family.size - STRIP_SIZE}", style = MaterialTheme.typography.titleMedium)
                            }
                            Text("See all", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (s.toName.isNotEmpty()) QuickTagCard(s.toName, onQuickTag, Modifier.padding(horizontal = 16.dp))
        for (w in s.warnings) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Text(w, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

/** "Who's who?": invites the couple to name the faces quickly, one tap each. */
@Composable
fun QuickTagCard(toName: List<PersonChip>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val n = toName.size
    val bg = MaterialTheme.colorScheme.secondaryContainer
    Surface(color = bg, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).testTag("quick-tag")) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(80.dp)) {
                toName.take(3).forEachIndexed { i, c ->
                    Box(Modifier.padding(start = (i * 22).dp).border(2.dp, bg, CircleShape)) { FaceAvatar(c.face, 38.dp) }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Who's who?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Name $n ${if (n == 1) "face" else "faces"}, one tap each. Your album gets the right people.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun CoupleCard(s: AlbumState, onTap: (Long) -> Unit, onSwap: () -> Unit, onConfirm: () -> Unit, onChoose: () -> Unit) {
    val unsure = s.bride != null && s.groom != null && !s.project.coupleSure
    val bg = if (unsure) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
    Surface(color = bg, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("couple-card")) {
        Column(Modifier.padding(start = 14.dp, top = 12.dp, bottom = if (unsure) 6.dp else 12.dp, end = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.bride == null || s.groom == null) {
                    Text("We could not tell who the bride and groom are. Tap See all below and choose them.",
                        modifier = Modifier.weight(1f).padding(end = 10.dp))
                    return@Row
                }
                // Two overlapping faces; each opens that person.
                Box(Modifier.width(88.dp)) {
                    Box(Modifier.clip(CircleShape).clickable { onTap(s.bride.personId) }.testTag("couple-bride")) { FaceAvatar(s.bride.face, 52.dp) }
                    Box(Modifier.padding(start = 36.dp).border(3.dp, bg, CircleShape).clip(CircleShape)
                        .clickable { onTap(s.groom.personId) }.testTag("couple-groom")) { FaceAvatar(s.groom.face, 52.dp) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    if (unsure) {
                        Text("Is this the groom?", style = MaterialTheme.typography.titleMedium)
                        Text("We are not sure from the photos", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    } else {
                        val named = s.bride.label != "Bride" || s.groom.label != "Groom"
                        Text(if (named) "${s.bride.label} & ${s.groom.label}" else "Bride & groom", style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Tap a face to see their photos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
                if (!unsure) TextButton(onClick = onSwap) { Text("Swap") }
            }
            if (unsure) {
                Row(Modifier.fillMaxWidth().padding(end = 6.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onChoose, modifier = Modifier.testTag("choose-groom")) { Text("No, choose") }
                    TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-groom")) { Text("Yes") }
                }
            }
        }
    }
}

/** "Who is the groom?": the most photographed people, tap one. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChooseGroomSheet(s: AlbumState, onPick: (Long) -> Unit, onClose: () -> Unit) {
    val people = (s.family + s.others + listOfNotNull(s.groom))
        .filter { it.personId != s.bride?.personId }
        .distinctBy { it.personId }
        .sortedByDescending { it.photoCount }
        .take(24)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Who is the groom?", style = MaterialTheme.typography.headlineSmall)
            Text("Tap his face. The album is remade around the two of you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (c in people) {
                    Column(Modifier.width(72.dp).clickable { onPick(c.personId) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        FaceAvatar(c.face, 64.dp)
                        Text("${c.photoCount} photos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun PersonChipView(c: PersonChip, onClick: () -> Unit) {
    val low = c.warning != null && c.inAlbum < c.target
    Column(Modifier.width(72.dp).clickable(onClick = onClick).testTag("person-chip"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            FaceAvatar(c.face, 64.dp, unknown = false)
            if (low) DotBadge("!", MaterialTheme.colorScheme.error, Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(4.dp))
        Text(c.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (c.known) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${c.inAlbum} photo${if (c.inAlbum == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium,
            color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val FAMILY = listOf("Mother", "Father", "Grandmother", "Grandfather", "Sister", "Brother", "Aunt", "Uncle", "Cousin", "Other relative")
private val FRIENDS = listOf("Best friend", "Friend")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PersonSheet(vm: AlbumViewModel, projectId: Long, personId: Long, album: AlbumState, onClose: () -> Unit) {
    val chip = remember(album, personId) { (listOfNotNull(album.bride, album.groom) + album.family + album.others).firstOrNull { it.personId == personId } }
    val flow = remember(personId) { vm.personDetail(projectId, personId, chip?.side ?: PersonSide.UNKNOWN) }
    val detail by flow.collectAsStateWithLifecycle(initialValue = null)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var side by rememberSaveable(personId) { mutableStateOf<String?>(null) }
    var relation by rememberSaveable(personId) { mutableStateOf("") }
    var name by rememberSaveable(personId) { mutableStateOf("") }
    var merging by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
        val d = detail
        if (d == null) {
            // The face and name are already known from the album; the rest follows in a moment.
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    chip?.let { FaceAvatar(it.face, 72.dp) }
                    Column {
                        Text(chip?.label ?: "", style = MaterialTheme.typography.headlineSmall)
                        chip?.let { Text("In ${it.photoCount} photo${if (it.photoCount == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        } else {
            PersonSheetContent(vm, projectId, d, side, relation, name, { side = it }, { relation = it }, { name = it }, { merging = true }, onClose)
        }
    }

    if (merging) {
        val choices = listOfNotNull(album.bride, album.groom) + album.family + album.others
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { merging = false },
            title = { Text("Same person as…") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(choices.filter { it.personId != personId }, key = { it.personId }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.merge(projectId, keep = c.personId, gone = personId); merging = false; onClose() }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FaceAvatar(c.face, 44.dp)
                            Text(c.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { merging = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonSheetContent(
    vm: AlbumViewModel, projectId: Long, d: PersonDetail,
    side: String?, relation: String, name: String,
    setSide: (String) -> Unit, setRelation: (String) -> Unit, setName: (String) -> Unit,
    startMerge: () -> Unit, onClose: () -> Unit,
) {
    val person = d.person
    val chosenSide = side ?: person.side.takeIf { it != PersonSide.UNKNOWN } ?: d.guessedSide.takeIf { it != PersonSide.UNKNOWN } ?: PersonSide.BRIDE
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FaceAvatar(d.face, 72.dp)
            Column {
                Text(d.role ?: person.name.ifBlank { "Who is this?" }, style = MaterialTheme.typography.headlineSmall)
                Text("In ${person.photoCount} photo${if (person.photoCount == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (d.samples.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (path in d.samples) {
                    coil.compose.AsyncImage(model = java.io.File(path), contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
                }
            }
        }
        if (d.role == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in listOf(PersonSide.BRIDE to "Bride's family", PersonSide.GROOM to "Groom's family", PersonSide.UNKNOWN to "Friend")) {
                    FilterChip(selected = chosenSide == value, onClick = { setSide(value); setRelation("") }, label = { Text(label) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in if (chosenSide == PersonSide.UNKNOWN) FRIENDS else FAMILY) {
                    FilterChip(selected = relation == r, onClick = { setRelation(r) }, label = { Text(r) }, modifier = Modifier.testTag("relation-$r"))
                }
            }
            OutlinedTextField(value = name, onValueChange = { setName(it.take(30)) }, singleLine = true,
                label = { Text("Name (optional), e.g. Aai, Nani, Mama") }, modifier = Modifier.fillMaxWidth())
            val rule = when (relation) {
                "" -> "Who is this? That decides how many photos they get."
                "Mother", "Father", "Sister", "Brother", "Best friend" -> "At least 5 good photos with the two of you."
                "Grandmother", "Grandfather" -> "At least 5 good photos with the two of you, and elders come first."
                else -> "At least 2 good photos with the two of you."
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp)) {
                Text(rule, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            BigButton("Save", onClick = { vm.setRelation(projectId, person, chosenSide, relation, name); onClose() },
                enabled = relation.isNotEmpty() || name.isNotBlank())
        }
        HorizontalDivider()
        Column {
            if (d.role == null) {
                SheetAction("Not family. Show them less") { vm.notFamily(projectId, person); onClose() }
                SheetAction("This is the bride") { vm.setBride(projectId, person.id); onClose() }
                SheetAction("This is the groom") { vm.setGroom(projectId, person.id); onClose() }
            }
            SheetAction("These photos show two different people") { vm.split(projectId, person.id); onClose() }
            SheetAction("Same person as someone else…") { startMerge() }
            SheetAction("Find more photos of them") { vm.findMore(projectId, person.id); onClose() }
        }
    }
}

@Composable
private fun SheetAction(text: String, onClick: () -> Unit) {
    Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(vm: AlbumViewModel, projectId: Long, s: AlbumState, onClose: () -> Unit) {
    var other by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Album size", style = MaterialTheme.typography.headlineSmall)
            Text("${s.total} of your ${"%,d".format(s.folderPhotos)} photos are in the album. Choose another size and it is remade at once.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in (AlbumSize.choices(s.folderPhotos) + s.project.albumSize).distinct().sorted()) {
                    FilterChip(selected = s.project.albumSize == n, onClick = { vm.setAlbumSize(projectId, n); onClose() }, label = { Text("$n") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = other, onValueChange = { other = it.filter(Char::isDigit).take(4) }, singleLine = true,
                    label = { Text("Other number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(170.dp))
                OutlinedButton(onClick = { other.toIntOrNull()?.let { vm.setAlbumSize(projectId, it) }; onClose() }, enabled = (other.toIntOrNull() ?: 0) > 0) { Text("Use") }
            }
            Text("Your own swaps are always kept.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("What we left out", style = MaterialTheme.typography.headlineSmall)
            val l = s.leftOut
            for ((label, n) in listOf(
                "Repeat shots (kept the best)" to l.repeats, "Blurry" to l.blurry, "Eyes closed or half-blink" to l.eyes,
                "Too dark or too bright" to l.light, "Face cut off or blocked" to l.faces, "Phones, strangers or backs in the way" to l.distractions,
            )) {
                Row { Text(label, Modifier.weight(1f)); Text("%,d".format(n), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("These are only left out of the album. Nothing is deleted.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
