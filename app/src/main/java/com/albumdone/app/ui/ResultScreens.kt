package com.albumdone.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albumdone.app.data.ProjectStatus
import com.albumdone.app.plan.Display
import com.albumdone.app.plan.Issue
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(vm: AlbumViewModel, projectId: Long) {
    val flow = remember(projectId) { vm.projectSummary(projectId) }
    val summary by flow.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(summary?.project?.status) {
        if (summary?.project?.status == ProjectStatus.DONE) vm.onAnalysisDone(projectId)
    }

    Scaffold(topBar = { AppBar(summary?.project?.name ?: "Checking photos", onBack = vm::back) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val s = summary
            if (s == null) {
                CircularProgressIndicator()
                return@Column
            }
            if (s.project.status == ProjectStatus.PEOPLE) {
                Text("Finding the people in your photos", style = MaterialTheme.typography.headlineSmall)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("All ${s.total} photos are checked. Now grouping faces so you can tag your family. This takes a minute.")
                return@Column
            }
            Text("Checking your photos", style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(
                progress = { if (s.total == 0) 0f else s.done.toFloat() / s.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${s.done} of ${s.total} photos done", style = MaterialTheme.typography.titleMedium)

            val seconds = s.project.activeMillis / 1000.0
            if (seconds >= 5 && s.done > 0) {
                val perSecond = s.done / seconds
                Text("Speed: ${(perSecond * 60).roundToInt()} photos a minute")
                val left = ((s.total - s.done) / perSecond).toLong()
                Text("Time left: about ${formatDuration(left * 1000)}")
            } else {
                Text("Working out how long this will take…")
            }
            Spacer(Modifier.height(8.dp))
            Card {
                Text(
                    "You can lock your phone or use other apps. We keep going and tell you when it is done. " +
                        "Nothing is uploaded.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
fun ResultsScreen(vm: AlbumViewModel, projectId: Long) {
    val state = vm.results?.takeIf { it.project.id == projectId }

    Scaffold(topBar = { AppBar(state?.project?.name ?: "Results", onBack = vm::back) }) { padding ->
        if (state == null) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        val a = state.analysis
        var sizeText by rememberSaveable(projectId) { mutableStateOf(state.project.albumSize.toString()) }
        val size = sizeText.toIntOrNull()
        val sizeOk = size != null && size in 1..a.photos.size

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${a.photos.size} photos checked", style = MaterialTheme.typography.headlineSmall)
            Text("Time taken: ${formatDuration(state.project.activeMillis)}")

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CountRow("Good to use", a.usableCount, bold = true)
                    HorizontalDivider()
                    CountRow("Blurry", a.count(Issue.BLURRY))
                    CountRow("Too dark or too bright", a.darkOrBrightCount)
                    CountRow("Eyes closed", a.count(Issue.EYES_CLOSED))
                    CountRow("Repeats of the same shot or group", a.countAny(Issue.DUPLICATE, Issue.GROUP_REPEAT))
                    CountRow("Face cut off or blocked", a.countAny(Issue.FACE_CUT_OFF, Issue.FACE_BLOCKED))
                    CountRow("Guests' backs in front", a.count(Issue.BACK_OF_GUEST))
                    HorizontalDivider()
                    Text("Small flaws (used only if needed)", style = MaterialTheme.typography.labelLarge)
                    CountRow("Half-blink or mid-talk", a.countAny(Issue.HALF_BLINK, Issue.MID_TALK))
                    CountRow("Someone not looking", a.count(Issue.NOT_LOOKING))
                    CountRow("Face in shadow", a.count(Issue.FACE_DARK))
                    CountRow("Phone or stranger in the shot", a.countAny(Issue.PHONE_IN_SHOT, Issue.STRANGER))
                    CountRow("Slightly crooked", a.count(Issue.TILTED))
                    val unreadable = a.count(Issue.UNREADABLE)
                    if (unreadable > 0) CountRow("Could not open", unreadable)
                }
            }

            Text("By event", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (e in a.eventStats) {
                        Row {
                            Text(Display.eventName(e.name), Modifier.weight(1f))
                            Text("${e.usable} good of ${e.total}")
                        }
                    }
                }
            }

            FamilyCoverage(state)
            OutlinedButton(onClick = { vm.openTagFamily(projectId) }, modifier = Modifier.fillMaxWidth()) {
                Text("Tag your family")
            }

            Spacer(Modifier.height(8.dp))
            Text("How many photos in the album?", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = sizeText,
                onValueChange = { sizeText = it.filter(Char::isDigit).take(5) },
                singleLine = true,
                isError = !sizeOk,
                supportingText = { if (!sizeOk) Text("Enter a number from 1 to ${a.photos.size}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { if (size != null) vm.makeShortlist(projectId, size) },
                enabled = sizeOk && !vm.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.shortlistCount > 0) "Make a new shortlist" else "Make my shortlist") }
            if (state.shortlistCount > 0) {
                OutlinedButton(onClick = { vm.openShortlist(projectId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open my shortlist (${state.shortlistCount} photos)")
                }
            }
        }
    }
}

@Composable
private fun CountRow(label: String, count: Int, bold: Boolean = false) {
    val weight = if (bold) FontWeight.SemiBold else FontWeight.Normal
    Row {
        Text(label, Modifier.weight(1f), fontWeight = weight)
        Text(count.toString(), fontWeight = weight)
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "$h hr $m min"
        m > 0 -> "$m min $s sec"
        else -> "$s sec"
    }
}

/** "Aai: 12 photos" for every Must / Important person, with a warning for anyone short. */
@Composable
private fun FamilyCoverage(state: ResultsState) {
    if (state.coverage.isEmpty()) return
    val inAlbum = state.shortlistCount > 0
    Text(if (inAlbum) "Family in your shortlist" else "Family photos found", style = MaterialTheme.typography.titleMedium)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in state.coverage) {
                val count = if (inAlbum) c.picked else c.goodAvailable
                Row {
                    Text(c.name + if (c.elder) " (elder)" else "", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("$count photo${if (count == 1) "" else "s"}")
                }
                val warning = if (inAlbum) c.warning else c.warning?.takeIf { c.goodAvailable < c.target }
                if (warning != null) {
                    Text(
                        "⚠ $warning (wanted at least ${c.target})",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
