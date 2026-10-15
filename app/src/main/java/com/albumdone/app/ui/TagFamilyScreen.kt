package com.albumdone.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albumdone.app.data.PersonEntity
import com.albumdone.app.data.PersonPriority
import com.albumdone.app.data.PersonSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
fun TagFamilyScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.family(projectId) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<PersonEntity?>(null) }
    var merging by remember { mutableStateOf<PersonRow?>(null) }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    Scaffold(
        topBar = { AppBar("Tag your family", onBack = vm::back) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { vm.finishTagging(projectId) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Done") }
            }
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Mark who must be in the album. Must: at least 5 good photos with the couple. " +
                        "Important: 1 or 2. Elders get first place. Skip anyone you don't need.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item { CoupleCard(vm, projectId, s) }
            if (s.others.isEmpty()) {
                item { Text("No other people found yet.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(s.others, key = { it.person.id }) { row ->
                PersonCard(
                    row = row,
                    onChange = { vm.updatePerson(it) },
                    onRename = { renaming = row.person },
                    onMerge = { merging = row },
                    onSplit = { vm.split(projectId, row.person.id) },
                    onFindMore = { vm.findMore(projectId, row.person.id) },
                    onBride = { vm.setBride(projectId, row.person.id) },
                    onGroom = { vm.setGroom(projectId, row.person.id) },
                )
            }
        }

        renaming?.let { person ->
            var text by remember(person.id) { mutableStateOf(person.name) }
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("Name") },
                text = {
                    OutlinedTextField(value = text, onValueChange = { text = it.take(30) }, singleLine = true, label = { Text("e.g. Aai, Nani, Mama") })
                },
                confirmButton = { TextButton(onClick = { vm.updatePerson(person.copy(name = text.trim())); renaming = null }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
            )
        }

        merging?.let { row ->
            val choices = listOfNotNull(s.bride, s.groom) + s.others.filter { it.person.id != row.person.id }
            AlertDialog(
                onDismissRequest = { merging = null },
                title = { Text("Same person as…") },
                text = {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(choices, key = { it.person.id }) { other ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    vm.merge(projectId, keep = other.person.id, gone = row.person.id)
                                    merging = null
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FaceAvatar(other, 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(displayName(other))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { merging = null }) { Text("Cancel") } },
            )
        }
    }
}

private fun displayName(row: PersonRow): String =
    row.role ?: row.person.name.ifBlank { "Not named (${row.person.photoCount} photos)" }

@Composable
private fun CoupleCard(vm: AlbumViewModel, projectId: Long, s: FamilyState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("The couple", style = MaterialTheme.typography.titleMedium)
            if (s.bride == null && s.groom == null) {
                Text("We could not tell who the couple is. Use ⋮ next to a person below to mark the bride and groom.")
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                for ((label, row) in listOf("Bride" to s.bride, "Groom" to s.groom)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (row != null) FaceAvatar(row, 72.dp) else Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                        Text(label, fontWeight = FontWeight.SemiBold)
                        if (row != null) Text("${row.person.photoCount} photos", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedButton(onClick = { vm.swapCouple(projectId) }) { Text("Swap bride and groom") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonCard(
    row: PersonRow,
    onChange: (PersonEntity) -> Unit,
    onRename: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onFindMore: () -> Unit,
    onBride: () -> Unit,
    onGroom: () -> Unit,
) {
    val p = row.person
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FaceAvatar(row, 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).clickable(onClick = onRename)) {
                    Text(p.name.ifBlank { "Tap to add a name" }, fontWeight = FontWeight.SemiBold,
                        color = if (p.name.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                    Text("${p.photoCount} photos", style = MaterialTheme.typography.bodySmall)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Same person as…") }, onClick = { menu = false; onMerge() })
                        DropdownMenuItem(text = { Text("These are different people") }, onClick = { menu = false; onSplit() })
                        DropdownMenuItem(text = { Text("Find more of this person") }, onClick = { menu = false; onFindMore() })
                        DropdownMenuItem(text = { Text("This is the bride") }, onClick = { menu = false; onBride() })
                        DropdownMenuItem(text = { Text("This is the groom") }, onClick = { menu = false; onGroom() })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in listOf(PersonPriority.MUST to "Must", PersonPriority.IMPORTANT to "Important", PersonPriority.SKIP to "Skip")) {
                    FilterChip(
                        selected = p.priority == value,
                        onClick = { onChange(p.copy(priority = if (p.priority == value) PersonPriority.NORMAL else value)) },
                        label = { Text(label) },
                    )
                }
                FilterChip(selected = p.elder, onClick = { onChange(p.copy(elder = !p.elder)) }, label = { Text("Elder") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in listOf(PersonSide.BRIDE to "Bride's side", PersonSide.GROOM to "Groom's side")) {
                    FilterChip(
                        selected = p.side == value,
                        onClick = { onChange(p.copy(side = if (p.side == value) PersonSide.UNKNOWN else value)) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

/** A round picture of the person's clearest face, cut from that photo's thumbnail. */
@Composable
fun FaceAvatar(row: PersonRow, size: Dp) {
    val path = row.thumbPath
    val image by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    Canvas(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        val img = image ?: return@Canvas
        val cx = (row.faceLeft + row.faceRight) / 2f * img.width
        val cy = (row.faceTop + row.faceBottom) / 2f * img.height
        val half = max((row.faceRight - row.faceLeft) * img.width, (row.faceBottom - row.faceTop) * img.height) * 0.7f
        val side = (2 * half).toInt().coerceIn(4, minOf(img.width, img.height))
        val x0 = (cx - side / 2f).toInt().coerceIn(0, img.width - side)
        val y0 = (cy - side / 2f).toInt().coerceIn(0, img.height - side)
        drawImage(
            image = img,
            srcOffset = IntOffset(x0, y0),
            srcSize = IntSize(side, side),
            dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
        )
    }
}
