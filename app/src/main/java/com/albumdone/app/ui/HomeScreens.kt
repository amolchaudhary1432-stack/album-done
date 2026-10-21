package com.albumdone.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.albumdone.app.data.ProjectStatus
import com.albumdone.app.data.ProjectSummary
import com.albumdone.app.plan.AlbumSize
import com.albumdone.app.plan.Display
import com.albumdone.app.plan.Occasion
import java.io.File

@Composable
fun HomeScreen(vm: AlbumViewModel) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }
    var toDelete by remember { mutableStateOf<ProjectSummary?>(null) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (projects.isEmpty()) {
            Welcome(Modifier.fillMaxSize())
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Brand(Modifier.statusBarsPadding().padding(top = 14.dp)) }
                item {
                    Column(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                        Text("Your albums", style = MaterialTheme.typography.displaySmall)
                        Text("Picked on your phone. Nothing is uploaded.", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(projects, key = { it.project.id }) { summary ->
                    AlbumCard(summary, onOpen = { vm.openProject(summary.project.id) }, onDelete = { toDelete = summary })
                }
            }
        }
        // The one action, floating at the bottom.
        Button(
            onClick = { pickFolder.launch(null) },
            shape = RoundedCornerShape(30.dp),
            contentPadding = PaddingValues(horizontal = 28.dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp).height(60.dp)
                .then(if (projects.isEmpty()) Modifier.fillMaxWidth().padding(horizontal = 20.dp) else Modifier),
        ) {
            Icon(LineIcons.Plus, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (projects.isEmpty()) "Choose photos folder" else "New album", style = MaterialTheme.typography.titleMedium)
        }
    }

    toDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete \"${summary.project.name}\"?") },
            text = { Text("This removes the album and its choices from this app. Your photos are not touched.") },
            confirmButton = { TextButton(onClick = { vm.deleteProject(summary.project.id); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Welcome(modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Brand()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your album, picked the way you would.", style = MaterialTheme.typography.displayMedium)
            Text(
                "Wedding, birthday, family function or trip: choose the folder of photos. We pick the album-worthy ones; you make the final calls.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Step(LineIcons.Tick, "We check every photo", "Blur, closed eyes, repeats, bad light. Even with the phone locked.")
            Step(LineIcons.Person, "Your people in it", "The best of whoever the photos are about, and the family who matter.")
            Step(LineIcons.Send, "Swap, then send", "Change any photo you don't love, then send the list to your photographer or print shop.")
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(LineIcons.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                "Your photos never leave this phone. The app has no internet access. One folder of up to ${"%,d".format(AlbumSize.MAX_PHOTOS)} photos at a time.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Saffron))
        Text("Album Done", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(SaffronSoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Saffron, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlbumCard(summary: ProjectSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    val p = summary.project
    val ready = p.status == ProjectStatus.DONE
    val occasion = Occasion.of(p.occasion)
    val fraction = if (summary.total == 0) 0f else summary.done.toFloat() / summary.total
    val status = when (p.status) {
        ProjectStatus.DONE -> "${occasion.label} · ${p.albumSize} picked from ${"%,d".format(summary.total)}"
        ProjectStatus.PEOPLE -> "Finding the people in your photos…"
        else -> "Checking ${"%,d".format(summary.done)} of ${"%,d".format(summary.total)}"
    }
    Box(
        Modifier.fillMaxWidth().height(236.dp).clip(RoundedCornerShape(26.dp)).background(Ink).clickable(onClick = onOpen),
    ) {
        summary.coverThumb?.let {
            AsyncImage(model = File(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xB8000000))))
        Box(
            Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(50)).background(Color(0xEBFFFFFF))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(if (ready) "Ready to send" else "Checking · ${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Ink)
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(10.dp).size(40.dp).clip(CircleShape).background(Color(0x47000000)).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete album", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
            Text(p.name, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f))
            if (!ready) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x59FFFFFF))) {
                    Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewAlbumScreen(vm: AlbumViewModel, screen: Screen.NewAlbum) {
    val scan = vm.scan
    var name by rememberSaveable(screen.treeUri) { mutableStateOf(screen.name) }
    var other by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }
    // Ask for notifications first (Android 13+) so "your album is ready" can be shown.
    // Checking starts whatever the answer is.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startAnalysis(name) }
    val start = {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.startAnalysis(name)
    }

    Scaffold(
        topBar = { AppBar("New album", onBack = vm::back) },
        bottomBar = {
            if (!scan.scanning && scan.found > 0 && !scan.tooMany) {
                BottomBar {
                    BigButton("Start checking photos", onClick = start, enabled = !vm.busy)
                    Text("Keep the phone on charge. You can lock it; we keep going.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when {
                scan.error != null -> {
                    Text(scan.error, style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = vm::back) { Text("Go back") }
                }
                scan.scanning -> {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text("Looking for photos… ${"%,d".format(scan.found)} found", style = MaterialTheme.typography.titleMedium)
                    }
                }
                scan.found == 0 -> {
                    Text("No photos in this folder.", style = MaterialTheme.typography.headlineSmall)
                    Text("Pick the folder with the photos (JPG, PNG or HEIC).")
                    BigButton("Choose another folder", onClick = { pickFolder.launch(null) })
                }
                scan.tooMany -> {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("${"%,d".format(scan.found)} photos", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "That is more than one album works from. Album Done picks from one folder of up to ${"%,d".format(AlbumSize.MAX_PHOTOS)} photos.",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            "Choose a smaller folder (one function, like Wedding or Reception, or one day of a trip), or the photographer's selected photos.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    BigButton("Choose another folder", onClick = { pickFolder.launch(null) })
                }
                else -> {
                    // The folder, in one line: its name, how many photos, and its sub-folders.
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(LineIcons.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("%,d photos".format(scan.found) + if (scan.events.size > 1) " · ${scan.events.size} folders" else " found",
                            style = MaterialTheme.typography.labelLarge)
                    }
                    if (scan.events.size > 1) {
                        Text(scan.events.joinToString("  ·  ") { (event, count) -> "${Display.eventName(event)} ${"%,d".format(count)}" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("What are these photos of?", style = MaterialTheme.typography.headlineLarge)
                        Text("We guessed from the folder name. Tap to change.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // One card per kind; a birthday or a family function then asks which.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (pair in Occasion.values().distinctBy { it.label }.chunked(2)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (o in pair) {
                                    OccasionCard(o, selected = vm.newOccasion.label == o.label, modifier = Modifier.weight(1f).testTag("occasion-${o.name}")) {
                                        if (vm.newOccasion.label != o.label) vm.newOccasion = o
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    val subs = Occasion.values().filter { it.label == vm.newOccasion.label && it.sub != null }
                    if (subs.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (vm.newOccasion.label == "Birthday") "Whose birthday?" else "Which function?", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (o in subs) {
                                    Pill(selected = vm.newOccasion == o, onClick = { vm.newOccasion = o },
                                        label = { Text(if (o == Occasion.KIDS_BIRTHDAY) "A child's (with mummy & papa)" else o.sub!!) },
                                        modifier = Modifier.testTag("occasion-sub-${o.name}"))
                                }
                            }
                        }
                    }
                    Text(
                        when {
                            vm.newOccasion.couple -> "We find the couple and both families by ourselves."
                            vm.newOccasion.child -> "We find the child, mummy and papa by ourselves."
                            vm.newOccasion == Occasion.ANNIVERSARY -> "We find the couple and their family by ourselves."
                            else -> "We find who the photos are about by ourselves."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Album name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("How many photos in your album?", style = MaterialTheme.typography.titleLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (n in AlbumSize.choices(scan.found)) {
                                Pill(selected = vm.newAlbumSize == n && other.isEmpty(), onClick = { other = ""; vm.newAlbumSize = n }, label = { Text("$n") })
                            }
                        }
                        OutlinedTextField(
                            value = other,
                            onValueChange = { v -> other = v.filter(Char::isDigit).take(4); other.toIntOrNull()?.takeIf { it > 0 }?.let { vm.newAlbumSize = it } },
                            label = { Text("Or type a number") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(200.dp), shape = RoundedCornerShape(18.dp),
                        )
                        Text("About 15 of every 100 photos make a good album. You can change it later.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One kind of occasion on the New album screen: an icon, a name and a hint, black-outlined when picked. */
@Composable
private fun OccasionCard(o: Occasion, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (icon, hint) = when (o.label) {
        "Wedding" -> LineIcons.Heart to "Couple, both families"
        "Engagement" -> LineIcons.Ring to "Or a pre-wedding shoot"
        "Birthday" -> LineIcons.Cake to "A child's or a grown-up's"
        "Family function" -> LineIcons.Home to "Naming, baby shower…"
        "Trip" -> LineIcons.Mountain to "People and places"
        else -> LineIcons.Lines to "Any folder of photos"
    }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.heightIn(min = 120.dp).clip(RoundedCornerShape(22.dp))
            .background(if (selected) colors.surface else colors.surfaceContainer)
            .border(2.dp, if (selected) colors.primary else Color.Transparent, RoundedCornerShape(22.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Saffron else colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else colors.onSurface, modifier = Modifier.size(20.dp))
        }
        Text(o.label, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
