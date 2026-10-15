package com.albumdone.app.ui

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.albumdone.app.data.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** The one main action of a screen: a big, easy-to-hit button. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** The strip at the bottom of a screen that holds its main button, clear of the phone's own buttons. */
@Composable
fun BottomBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), content = content)
        }
    }
}

/** A small heading above a group of things. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** White status bar icons while a dark photo is behind them. */
@Composable
fun LightStatusBarIcons() {
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    DisposableEffect(view, dark) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) controller?.isAppearanceLightStatusBars = before }
    }
}

/** A round badge with a number or sign, e.g. on a face that needs more photos. */
@Composable
fun DotBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color, shape = CircleShape, modifier = modifier.size(22.dp), border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.background)) {
        Box(contentAlignment = Alignment.Center) { Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall) }
    }
}

/** A square photo tile, centred on the faces. */
@Composable
fun PhotoThumb(photo: PhotoEntity, crop: Pair<Float, Float>, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    AsyncImage(
        model = photo.thumbPath?.let { File(it) } ?: photo.docUri,
        contentDescription = photo.name,
        contentScale = contentScale,
        alignment = if (contentScale == ContentScale.Crop) BiasAlignment(crop.first, crop.second) else Alignment.Center,
        modifier = modifier,
    )
}

/** A round picture of a person's face, cut from that photo's thumbnail. */
@Composable
fun FaceAvatar(face: FaceCrop, size: Dp, modifier: Modifier = Modifier, unknown: Boolean = false, ring: Boolean = false) {
    val path = face.facePath ?: face.thumbPath
    val image by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surfaceVariant)
            .then(if (ring) Modifier.border(2.dp, colors.secondary, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        val box = face.box
        if (img != null && face.facePath != null) {
            // Already a square picture of just the face.
            androidx.compose.foundation.Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else if (img != null && box != null) {
            Canvas(Modifier.size(size)) {
                val cx = (box.left + box.right) / 2f * img.width
                val cy = (box.top + box.bottom) / 2f * img.height
                val half = max((box.right - box.left) * img.width, (box.bottom - box.top) * img.height) * 0.75f
                val side = (2 * half).toInt().coerceIn(4, minOf(img.width, img.height))
                val x0 = (cx - side / 2f).toInt().coerceIn(0, img.width - side)
                val y0 = (cy - side / 2f).toInt().coerceIn(0, img.height - side)
                drawImage(image = img, srcOffset = IntOffset(x0, y0), srcSize = IntSize(side, side),
                    dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()))
            }
        } else if (unknown || img == null) {
            Text("?", style = MaterialTheme.typography.headlineSmall, color = colors.onSurfaceVariant)
        }
    }
}
