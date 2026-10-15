package com.albumdone.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    )
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
