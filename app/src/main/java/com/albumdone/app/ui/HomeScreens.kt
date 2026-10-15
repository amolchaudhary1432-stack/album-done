package com.albumdone.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albumdone.app.data.ProjectStatus
import com.albumdone.app.data.ProjectSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
    )
}

@Composable
fun HomeScreen(vm: AlbumViewModel) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }
    var toDelete by remember { mutableStateOf<ProjectSummary?>(null) }

    Scaffold(
        topBar = { AppBar("Album Done") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickFolder.launch(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Start new album") },
            )
        },
    ) { padding ->
        if (projects.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Your wedding album shortlist in a few taps", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Pick the folder of photos from your photographer. We check every photo on " +
                        "this phone and suggest the best ones. Not a single photo is uploaded.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { pickFolder.launch(null) }) { Text("Choose photo folder") }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(projects, key = { it.project.id }) { summary ->
                    ProjectCard(summary, onOpen = { vm.openProject(summary.project.id) }, onDelete = { toDelete = summary })
                }
            }
        }
    }

    toDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete \"${summary.project.name}\"?") },
            text = { Text("This removes the checks and shortlist from this app. Your photos are not touched.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteProject(summary.project.id); toDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectCard(summary: ProjectSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(summary.project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                val status = if (summary.project.status == ProjectStatus.DONE) {
                    "${summary.total} photos checked"
                } else {
                    "Checking photos: ${summary.done} of ${summary.total}"
                }
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
}

@Composable
fun NewAlbumScreen(vm: AlbumViewModel, screen: Screen.NewAlbum) {
    val scan = vm.scan
    var name by rememberSaveable(screen.treeUri) { mutableStateOf(screen.name) }
    val context = LocalContext.current
    // Ask for notifications first (Android 13+) so the progress and "done" messages can show.
    // Analysis starts whatever the answer is.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.startAnalysis(name)
    }
    val start = {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.startAnalysis(name)
    }

    Scaffold(topBar = { AppBar("New album", onBack = vm::back) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
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
                    Text("No photos found in this folder.", style = MaterialTheme.typography.titleMedium)
                    Text("Pick the folder that has the photographer's photos (JPG, PNG, HEIC).")
                    Button(onClick = vm::back) { Text("Go back") }
                }
                else -> {
                    Text("${scan.found} photos found", style = MaterialTheme.typography.headlineSmall)
                    if (scan.events > 1) {
                        Text("In ${scan.events} folders (like Haldi, Wedding, Reception)")
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Album name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Next we check every photo for blur, light, closed eyes and repeat shots. " +
                            "It runs in the background and you can lock your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = start, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Check my photos")
                    }
                }
            }
        }
    }
}
