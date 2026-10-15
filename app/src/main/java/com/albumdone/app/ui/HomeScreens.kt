package com.albumdone.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import java.io.File

/** Deep plum to a softer plum: the brand, the same in light and dark mode. */
private val BrandGradient = Brush.linearGradient(listOf(Color(0xFF3B1F45), Color(0xFF6A3B70)))
private val Gold = Color(0xFFE9B730)

@Composable
fun HomeScreen(vm: AlbumViewModel) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }
    var toDelete by remember { mutableStateOf<ProjectSummary?>(null) }

    Scaffold(
        bottomBar = {
            BottomBar {
                BigButton(
                    if (projects.isEmpty()) "Choose photos folder" else "Start a new album",
                    onClick = { pickFolder.launch(null) }, icon = Icons.Default.Add,
                )
            }
        },
    ) { padding ->
        if (projects.isEmpty()) {
            Welcome(Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Brand() }
                item { Text("Your albums", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 4.dp)) }
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
private fun Welcome(modifier: Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Brand()
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(BrandGradient).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Gold), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF3B1F45), modifier = Modifier.size(28.dp))
            }
            Text("Your wedding album, picked the way you would.", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text(
                "Choose the folder your photographer gave you. We pick the best photos; you make the final calls.",
                style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Step(Icons.Default.Check, "We check every photo", "Blur, closed eyes, repeats, bad light. Even with the phone locked.")
            Step(Icons.Default.Face, "Your album, with your family in it", "The best of the two of you, and the people who matter.")
            Step(Icons.Default.Share, "Swap, then send", "Change any photo you don't love, then send the list to your photographer.")
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(
                "Your photos never leave this phone. The app has no internet access. One folder of up to ${"%,d".format(AlbumSize.MAX_PHOTOS)} photos at a time.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(BrandGradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
        }
        Text("Album Done", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
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
    val status = when (p.status) {
        ProjectStatus.DONE -> "Album ready · ${p.albumSize} photos"
        ProjectStatus.PEOPLE -> "Finding the people in your photos…"
        else -> "Checking photos: ${"%,d".format(summary.done)} of ${"%,d".format(summary.total)}"
    }
    Surface(
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpen),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(BrandGradient)) {
                summary.coverThumb?.let {
                    AsyncImage(model = File(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xD9120B15))))
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(p.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Delete album", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            if (!ready) {
                val fraction = if (summary.total == 0) 0f else summary.done.toFloat() / summary.total
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(4.dp))
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
                    Text("Pick the folder with the photographer's photos (JPG, PNG or HEIC).")
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
                            "Choose one function's folder (like Wedding or Reception), or the photographer's selected photos.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    BigButton("Choose another folder", onClick = { pickFolder.launch(null) })
                }
                else -> {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp),
                    ) {
                        Text("%,d".format(scan.found), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                        Text(if (scan.events.size > 1) "photos in ${scan.events.size} folders" else "photos found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (scan.events.size > 1) {
                            Spacer(Modifier.height(12.dp))
                            scan.events.forEachIndexed { i, (event, count) ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                    Text(Display.eventName(event), Modifier.weight(1f))
                                    Text("%,d".format(count), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Album name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("How many photos in your album?", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (n in AlbumSize.choices(scan.found)) {
                                FilterChip(selected = vm.newAlbumSize == n && other.isEmpty(), onClick = { other = ""; vm.newAlbumSize = n }, label = { Text("$n") })
                            }
                        }
                        OutlinedTextField(
                            value = other,
                            onValueChange = { v -> other = v.filter(Char::isDigit).take(4); other.toIntOrNull()?.takeIf { it > 0 }?.let { vm.newAlbumSize = it } },
                            label = { Text("Or type a number") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(200.dp), shape = RoundedCornerShape(16.dp),
                        )
                        Text("About 15 of every 100 photos make a good album. Ask your photographer if unsure; you can change it later.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
