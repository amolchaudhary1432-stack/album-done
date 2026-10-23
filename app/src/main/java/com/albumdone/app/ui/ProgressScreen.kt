package com.albumdone.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albumdone.app.data.ProjectStatus
import com.albumdone.app.plan.TimeLeft

@Composable
fun ProgressScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.projectSummary(projectId) }
    val summary by flow.collectAsStateWithLifecycle(initialValue = null)
    val recentFlow = remember(projectId) { vm.recentThumbs(projectId) }
    val recent by recentFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Time left from the recent pace, not the average since the start (the first photos are slow).
    val timeLeft = remember(projectId) { TimeLeft() }

    LaunchedEffect(summary?.project?.status) {
        if (summary?.project?.status == ProjectStatus.DONE) vm.openAlbum(projectId)
    }

    Scaffold(topBar = { AppBar(summary?.project?.name ?: "", onBack = vm::back) }) { padding ->
        val s = summary
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (s == null) return@Column
            Spacer(Modifier.height(12.dp))
            if (s.project.status == ProjectStatus.PEOPLE || (s.project.status == ProjectStatus.DONE)) {
                Text("Finding the people in your photos", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                Text("All ${"%,d".format(s.total)} photos are checked. Your album is almost ready.",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val fraction = if (s.total == 0) 0f else s.done.toFloat() / s.total
                Text("Making your album", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Ring(fraction)
                Text("${"%,d".format(s.done)} of ${"%,d".format(s.total)} photos checked", style = MaterialTheme.typography.titleMedium)
                timeLeft.add(s.project.activeMillis, s.done)
                val left = timeLeft.secondsLeft(s.total)
                if (left != null) {
                    Text("About ${formatDuration(left * 1000)} left", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Working out how long this will take…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SectionLabel("Just checked", Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (path in recent.take(5)) {
                        AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
                    }
                    repeat(5 - recent.take(5).size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Saffron), contentAlignment = Alignment.Center) {
                    Icon(LineIcons.Lock, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                }
                Text(
                    "You can lock your phone or use other apps. We keep going and send a notification when your album is ready. Nothing is uploaded.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Ring(fraction: Float) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val bar = Saffron
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 14.dp.toPx()
            val d = size.minDimension - w
            val topLeft = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(w))
            drawArc(bar, -90f, 360f * fraction, false, topLeft, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return when {
        h > 0 -> "$h hr $m min"
        m > 0 -> "$m min"
        else -> "1 min"
    }
}
