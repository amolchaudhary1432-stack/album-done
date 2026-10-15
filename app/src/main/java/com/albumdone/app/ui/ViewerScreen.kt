package com.albumdone.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albumdone.app.plan.Display
import com.albumdone.app.plan.Side

/** Behind the photo: near black, so the photo's own colours show true. */
private val Backdrop = Color(0xFF0F0B11)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(vm: AlbumViewModel, projectId: Long, photoId: Long) {
    val flow = remember(projectId, photoId) { vm.viewer(projectId, photoId) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    val host = remember { SnackbarHostState() }
    MessageHost(vm, host)
    LightStatusBarIcons()

    Scaffold(
        containerColor = Backdrop,
        topBar = {
            TopAppBar(
                title = { Text(state?.photo?.event?.let { Display.eventName(it) } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Backdrop, titleContentColor = Color.White, navigationIconContentColor = Color.White,
                ),
            )
        },
        snackbarHost = { SnackbarHost(host) },
        bottomBar = {
            val s = state
            if (s != null && s.inAlbum) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { vm.remove(projectId, photoId) },
                            modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(26.dp),
                        ) { Text("Remove") }
                        Button(onClick = vm::back, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(26.dp)) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Keep")
                        }
                    }
                }
            }
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // At most a little over half the screen, so the reasons below are visible without scrolling.
            val maxPhoto = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
            Box(Modifier.fillMaxWidth().background(Backdrop), contentAlignment = Alignment.Center) {
                PhotoThumb(s.photo.photo, 0f to 0f, Modifier.fillMaxWidth().heightIn(max = maxPhoto).aspectRatio(
                    if (s.photo.photo.width > 0 && s.photo.photo.height > 0) s.photo.photo.width.toFloat() / s.photo.photo.height else 1.5f,
                    matchHeightConstraintsFirst = true,
                ), contentScale = ContentScale.Fit)
            }
            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                // Reaches down to the buttons, so no dark strip shows under short reasons.
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = (LocalConfiguration.current.screenHeightDp * 0.5f).dp),
            ) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (s.photo.cover) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(6.dp))
                                Text("Your album cover", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    SectionLabel("Why it's in your album")
                    val reasons = s.photo.reasons.ifEmpty { listOf("One of the best photos of ${Display.eventName(s.photo.event)}") }
                    for (r in reasons) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Text(r, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(s.photo.photo.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.inAlbum && s.alternatives.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SectionLabel("Prefer another one? Tap to swap")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(s.alternatives, key = { it.photo.id }) { alt ->
                                Column(Modifier.width(108.dp).clickable { vm.swap(projectId, photoId, alt.photo.id) }.testTag("alternative")) {
                                    PhotoThumb(alt.photo, alt.crop, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
                                    Spacer(Modifier.height(4.dp))
                                    Text(alt.note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.album(projectId) }
    val state by flow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Whose album: the couple's own, or one family's copy (both families usually keep one).
    var forWhom by rememberSaveable { mutableStateOf<Side?>(null) }
    val familyText by produceState<String?>(null, forWhom, state?.total) {
        value = null
        value = forWhom?.let { vm.familyListText(projectId, it) }
    }
    val s = state
    val text = when {
        s == null -> null
        forWhom == null -> vm.shareText(s)
        else -> familyText
    }
    Scaffold(
        topBar = { AppBar("Send to photographer", onBack = vm::back) },
        bottomBar = {
            if (s != null && text != null) {
                BottomBar {
                    BigButton("Share list", icon = Icons.Default.Share, onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, text.lineSequence().first())
                            .putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, "Send list to photographer"))
                    })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (s != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        val count = text?.lineSequence()?.drop(1)?.firstOrNull()?.substringBefore(' ') ?: "${s.total}"
                        Text(count, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                        Text("photos" + if (s.events.size > 1) " from ${s.events.size} folders" else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    s.cover?.let { c -> PhotoThumb(c.photo, c.crop, Modifier.size(84.dp).clip(RoundedCornerShape(16.dp))) }
                }
                // Only a wedding has two families who each keep their own copy.
                if (s.occasion.couple) {
                    SectionLabel("Whose album")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((value, label) in listOf(null to "The two of you", Side.BRIDE to "Bride's family", Side.GROOM to "Groom's family")) {
                            FilterChip(selected = forWhom == value, onClick = { forWhom = value }, label = { Text(label) },
                                modifier = Modifier.testTag("album-for-${value ?: "couple"}"))
                        }
                    }
                }
                if (forWhom != null) {
                    Text("The same wedding, with ${if (forWhom == Side.BRIDE) "the bride's" else "the groom's"} relatives first. Your own swaps are kept.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Your photographer or print shop gets a simple list with the exact file names. It works in WhatsApp or email. Only the list is sent, never the photos.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s != null) {
                SectionLabel("Preview")
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f, fill = false)) {
                    if (text == null) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        SelectionContainer {
                            Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()))
                        }
                    }
                }
            }
        }
    }
}
