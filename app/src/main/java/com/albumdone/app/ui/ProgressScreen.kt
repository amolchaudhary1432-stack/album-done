package com.albumdone.app.ui

import androidx.compose.foundation.Canvas
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

@Composable
fun ProgressScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.projectSummary(projectId) }
    val summary by flow.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(summary?.project?.status) {
        if (summary?.project?.status == ProjectStatus.DONE) vm.openAlbum(projectId)
    }

    Scaffold(topBar = { AppBar(summary?.project?.name ?: "", onBack = vm::back) }) { padding ->
        val s = summary
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (s == null) return@Column
            Spacer(Modifier.height(16.dp))
            if (s.project.status == ProjectStatus.PEOPLE || (s.project.status == ProjectStatus.DONE)) {
                Text("Finding the people in your photos", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("All ${"%,d".format(s.total)} photos are checked. Your album is almost ready.",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val fraction = if (s.total == 0) 0f else s.done.toFloat() / s.total
            Ring(fraction)
            Text("${"%,d".format(s.done)} of ${"%,d".format(s.total)} photos checked", style = MaterialTheme.typography.titleMedium)
            val seconds = s.project.activeMillis / 1000.0
            if (seconds >= 20 && s.done >= 5) {
                val left = ((s.total - s.done) / (s.done / seconds)).toLong()
                Text("About ${formatDuration(left * 1000)} left", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text("Working out how long this will take…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                Text(
                    "You can lock your phone or use other apps. We keep going and send a notification when your album is ready. Nothing is uploaded.",
                    modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Ring(fraction: Float) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val bar = MaterialTheme.colorScheme.primary
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 14.dp.toPx()
            val d = size.minDimension - w
            val topLeft = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(w))
            drawArc(bar, -90f, 360f * fraction, false, topLeft, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.headlineLarge, color = bar)
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
