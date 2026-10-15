package com.albumdone.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albumdone.app.data.ProjectStatus
import com.albumdone.app.data.ProjectSummary
import com.albumdone.app.plan.Display

@Composable
fun HomeScreen(vm: AlbumViewModel) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }
    var toDelete by remember { mutableStateOf<ProjectSummary?>(null) }

    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Button(
                    onClick = { pickFolder.launch(null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(if (projects.isEmpty()) "Choose photos folder" else "Start a new album", style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { padding ->
        if (projects.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Brand()
                Text("Your wedding album, picked the way you would.", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Choose the folder your photographer gave you. We do the first round of choosing; you make the final calls.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Step("1", "We check every photo, even with the phone locked")
                Step("2", "Your album is ready, with your family in it")
                Step("3", "Swap any photo you don't love, then send the list")
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "Your photos never leave this phone. The app has no internet access.",
                        modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Brand(); Spacer(Modifier.height(8.dp)) }
                item { Text("Your albums", style = MaterialTheme.typography.headlineMedium) }
                items(projects, key = { it.project.id }) { summary ->
                    AlbumCard(summary, onOpen = { vm.openProject(summary.project.id) }, onDelete = { toDelete = summary })
                }
            }
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
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize()) {}
            Text("✓", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium)
        }
        Text("Album Done", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxSize()) {}
            Text(n, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AlbumCard(summary: ProjectSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    val p = summary.project
    val status = when (p.status) {
        ProjectStatus.DONE -> "Album ready · ${p.albumSize} photos"
        ProjectStatus.PEOPLE -> "Finding the people in your photos…"
        else -> "Checking photos: ${summary.done} of ${summary.total}"
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete album") }
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
            if (!scan.scanning && scan.found > 0) {
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = start, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                            Text("Start checking photos", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("Big folders take a while. Keep the phone on charge.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                scan.error != null -> {
                    Text(scan.error, style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = vm::back) { Text("Go back") }
                }
                scan.scanning -> {
                    CircularProgressIndicator()
                    Text("Looking for photos… ${scan.found} found", style = MaterialTheme.typography.titleMedium)
                }
                scan.found == 0 -> {
                    Text("No photos in this folder.", style = MaterialTheme.typography.headlineSmall)
                    Text("Pick the folder with the photographer's photos (JPG, PNG or HEIC).")
                    Button(onClick = vm::back) { Text("Go back") }
                }
                else -> {
                    Column {
                        Text("%,d".format(scan.found), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                        Text(if (scan.events.size > 1) "photos found in ${scan.events.size} events" else "photos found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (scan.events.size > 1) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column {
                                scan.events.forEachIndexed { i, (event, count) ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                        Text(Display.eventName(event), Modifier.weight(1f))
                                        Text("%,d photos".format(count), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Album name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Text("How many photos will your album have?", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (n in listOf(150, 250, 350, 500)) {
                            FilterChip(selected = vm.newAlbumSize == n && other.isEmpty(), onClick = { other = ""; vm.newAlbumSize = n }, label = { Text("$n") })
                        }
                    }
                    OutlinedTextField(
                        value = other,
                        onValueChange = { v -> other = v.filter(Char::isDigit).take(4); other.toIntOrNull()?.takeIf { it > 0 }?.let { vm.newAlbumSize = it } },
                        label = { Text("Or type a number") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(200.dp),
                    )
                    Text("Not sure? Ask your photographer. You can change it later.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
