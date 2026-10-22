package com.albumdone.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.albumdone.app.R
import kotlinx.coroutines.delay

/** Shown once each time the app starts, not again when coming back to it. */
private var introShown = false

/**
 * The name and the promise for a moment when the app opens, over the first screen, then it fades.
 * It never takes taps: the screen under it works from the start.
 */
@Composable
fun LaunchIntro(skip: Boolean) {
    var visible by remember { mutableStateOf(!introShown && !skip) }
    LaunchedEffect(Unit) {
        introShown = true
        delay(INTRO_MILLIS)
        visible = false
    }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("intro")) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher), contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Saffron))
                    Text("Album Done", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
                }
                Text(
                    "The album-worthy photos, picked on your phone.",
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                )
            }
            Text(
                "Nothing is uploaded",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
            )
        }
    }
}

private const val INTRO_MILLIS = 1_400L
