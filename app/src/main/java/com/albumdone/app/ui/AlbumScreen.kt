package com.albumdone.app.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albumdone.app.data.PersonEntity
import com.albumdone.app.data.PersonSide

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.album(projectId) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    val host = remember { SnackbarHostState() }
    MessageHost(vm, host)
    var personSheet by remember { mutableStateOf<Long?>(null) }
    var moreSheet by remember { mutableStateOf(false) }
    var settingsSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppBar(state?.project?.name ?: "", onBack = vm::back) {
                IconButton(onClick = { settingsSheet = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Album size and more") }
            }
        },
        snackbarHost = { SnackbarHost(host) },
        bottomBar = {
            if (state != null) {
                Surface(tonalElevation = 2.dp) {
                    Button(
                        onClick = { vm.openSend(projectId) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Send list to photographer", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Making your album…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding).testTag("album-grid"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }

            s.cover?.let { cover ->
                item(key = "cover", span = full) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(18.dp))
                            .clickable { vm.openPhoto(projectId, cover.photo.id) }.testTag("cover"),
                    ) {
                        PhotoThumb(cover.photo, cover.crop, Modifier.fillMaxSize())
                        Surface(color = Color(0xB31B1220), shape = RoundedCornerShape(50), modifier = Modifier.padding(12.dp)) {
                            Text("COVER", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }
                }
            }
            item(key = "summary", span = full) {
                Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
                    Text("${s.total} photos", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text("${s.events.size} event${if (s.events.size == 1) "" else "s"} · ready to send",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "couple", span = full) { CoupleCard(s, onTap = { personSheet = it }, onSwap = { vm.swapCouple(projectId) }) }
            item(key = "family", span = full) {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("YOUR FAMILY IN THE ALBUM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(s.family, key = { it.personId }) { c -> PersonChipView(c) { personSheet = c.personId } }
                        item {
                            Column(
                                Modifier.width(72.dp).clickable { moreSheet = true }.testTag("more-people"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(Modifier.size(62.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                                Text("Add someone", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Text(
                        if (s.family.isEmpty()) "Tap + to add family members you want in the album."
                        else "We guessed your family from who is with you at every function. Tap a face to name or fix.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    for (w in s.warnings) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                            Text(w, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            for (e in s.events) {
                item(key = "event-${e.name}", span = full) {
                    Column(Modifier.padding(top = 22.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(e.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            Text("${e.photos.size} photos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (e.keyMoments.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (m in e.keyMoments) {
                                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                                        Text(m, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                items(e.photos, key = { it.photo.id }) { p ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).clickable { vm.openPhoto(projectId, p.photo.id) }.testTag("album-photo")) {
                        PhotoThumb(p.photo, p.crop, Modifier.fillMaxSize())
                    }
                }
            }
        }

        personSheet?.let { id -> PersonSheet(vm, projectId, id, s, onClose = { personSheet = null }) }
        if (moreSheet) MorePeopleSheet(s, onPick = { moreSheet = false; personSheet = it }, onClose = { moreSheet = false })
        if (settingsSheet) SettingsSheet(vm, projectId, s, onClose = { settingsSheet = false })
    }
}

@Composable
private fun CoupleCard(s: AlbumState, onTap: (Long) -> Unit, onSwap: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.bride == null || s.groom == null) {
                Text("We could not tell who the bride and groom are. Tap + below and choose them.", modifier = Modifier.weight(1f).padding(end = 10.dp))
                return@Row
            }
            // Two overlapping faces; each opens that person.
            Box(Modifier.width(78.dp)) {
                Box(Modifier.clip(CircleShape).clickable { onTap(s.bride.personId) }.testTag("couple-bride")) { FaceAvatar(s.bride.face, 44.dp) }
                Box(Modifier.padding(start = 34.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .clickable { onTap(s.groom.personId) }.testTag("couple-groom")) { FaceAvatar(s.groom.face, 44.dp) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                val named = s.bride.label != "Bride" || s.groom.label != "Groom"
                Text(if (named) "${s.bride.label} & ${s.groom.label}" else "Bride & groom", style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Found automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            TextButton(onClick = onSwap) { Text("Swap") }
        }
    }
}

@Composable
fun PersonChipView(c: PersonChip, onClick: () -> Unit) {
    val low = c.warning != null && c.inAlbum < c.target
    Column(Modifier.width(72.dp).clickable(onClick = onClick).testTag("person-chip"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            FaceAvatar(c.face, 62.dp, unknown = !c.known)
            if (low) {
                Surface(color = MaterialTheme.colorScheme.error, shape = CircleShape, modifier = Modifier.size(20.dp).align(Alignment.BottomEnd)) {
                    Box(contentAlignment = Alignment.Center) { Text("!", color = Color.White, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        Text(c.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${c.inAlbum} photo${if (c.inAlbum == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium,
            color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val FAMILY = listOf("Mother", "Father", "Grandmother", "Grandfather", "Sister", "Brother", "Aunt", "Uncle", "Cousin", "Other relative")
private val FRIENDS = listOf("Best friend", "Friend")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PersonSheet(vm: AlbumViewModel, projectId: Long, personId: Long, album: AlbumState, onClose: () -> Unit) {
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
                    Text(chip?.label ?: "", style = MaterialTheme.typography.headlineSmall)
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
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)))
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
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                Text(rule, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { vm.setRelation(projectId, person, chosenSide, relation, name); onClose() },
                enabled = relation.isNotEmpty() || name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
            ) { Text("Save") }
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
private fun MorePeopleSheet(s: AlbumState, onPick: (Long) -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add someone", style = MaterialTheme.typography.headlineSmall)
            Text("Other people in your photos, most photographed first. Tap someone to add them.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.others.isEmpty()) Text("Nobody else appears often enough.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (c in s.others) PersonChipView(c.copy(label = c.label.takeIf { c.known } ?: "Unnamed")) { onPick(c.personId) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(vm: AlbumViewModel, projectId: Long, s: AlbumState, onClose: () -> Unit) {
    var other by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Album size", style = MaterialTheme.typography.headlineSmall)
            Text("Your album has ${s.total} photos now. Choose another size and it is remade at once.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in listOf(150, 250, 350, 500)) {
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
