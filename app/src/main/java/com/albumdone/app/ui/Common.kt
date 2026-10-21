package com.albumdone.app.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** The top of a screen: a round back button and a bold title. */
@Composable
fun AppBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

/** A 44 dp round button with one icon, on a soft grey (or see-through over a photo). */
@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, overPhoto: Boolean = false) {
    Box(
        modifier.size(44.dp).clip(CircleShape)
            .background(if (overPhoto) Color(0x38FFFFFF) else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (overPhoto) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

/** The one main action of a screen: a big black pill. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(29.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** A choice among a few (album size, whose side): black when picked, outlined otherwise. */
@Composable
fun Pill(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (selected) colors.primary else colors.surface)
            .border(1.5.dp, if (selected) colors.primary else colors.outline, RoundedCornerShape(22.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) colors.onPrimary else colors.onSurface) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { label() }
        }
    }
}

/** A light card: soft grey, round corners, no lines. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainer, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(color).padding(16.dp), content = content)
}

/** Simple line icons drawn from SVG path data (24 x 24), so no icon library is needed. */
fun lineIcon(name: String, vararg paths: String): ImageVector = ImageVector.Builder(
    name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    for (d in paths) addPath(
        pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    )
}.build()

object LineIcons {
    val Heart = lineIcon("heart", "M12 21s-7-4.4-9-9a5 5 0 0 1 9-3 5 5 0 0 1 9 3c-2 4.6-9 9-9 9z")
    val Ring = lineIcon("ring", "M12 8a6 6 0 1 0 0 12 6 6 0 0 0 0-12z", "M9 2h6l-3 6z")
    val Cake = lineIcon("cake", "M4 21h16M5 21v-7h14v7M12 14V9", "M12 6a1.5 1.5 0 0 0 1.5-1.5C13.5 3 12 2 12 2s-1.5 1-1.5 2.5A1.5 1.5 0 0 0 12 6z")
    val Home = lineIcon("home", "M3 11l9-7 9 7M5 10v10h14V10M10 20v-5h4v5")
    val Mountain = lineIcon("mountain", "M2 17l7-11 4 6 3-4 6 9z", "M2 21h20")
    val Lines = lineIcon("lines", "M4 6h16M4 12h16M4 18h10")
    val Folder = lineIcon("folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val Lock = lineIcon("lock", "M4 13a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z", "M8 11V7a4 4 0 0 1 8 0v4")
    val Person = lineIcon("person", "M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8z", "M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6")
    val Tick = lineIcon("tick", "M5 12l5 5 9-10")
    val Send = lineIcon("send", "M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M16 6l-4-4-4 4M12 2v14")
    val Plus = lineIcon("plus", "M12 5v14M5 12h14")
    val Minus = lineIcon("minus", "M5 12h14")
    val Next = lineIcon("next", "M9 5l7 7-7 7")
}

/** The bottom of a screen holding its main button, clear of the phone's navigation bar. */
@Composable
fun BottomBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), content = content)
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

/**
 * A round picture of a person's face: the sharp face picture when there is one, otherwise cut
 * from that photo's thumbnail.
 */
@Composable
fun FaceAvatar(face: FaceCrop, size: Dp, modifier: Modifier = Modifier, unknown: Boolean = false, ring: Boolean = false) {
    var pictureFailed by remember(face.facePath) { mutableStateOf(false) }
    val picture = face.facePath?.takeIf { !pictureFailed }
    // The thumbnail is only needed when there is no face picture to show.
    val thumbPath = if (picture == null) face.thumbPath else null
    val thumb by produceState<ImageBitmap?>(null, thumbPath) {
        val loaded = withContext(Dispatchers.IO) { thumbPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
        if (loaded == null && thumbPath != null) Log.w("AlbumDone", "face thumbnail could not be read: $thumbPath")
        value = loaded
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
        val img = thumb
        val box = face.box
        when {
            picture != null -> AsyncImage(
                model = File(picture), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size),
                onError = {
                    Log.w("AlbumDone", "face picture could not be read: $picture (${it.result.throwable.message})")
                    pictureFailed = true
                },
            )
            img != null && box != null -> Canvas(Modifier.size(size)) {
                val cx = (box.left + box.right) / 2f * img.width
                val cy = (box.top + box.bottom) / 2f * img.height
                val half = max((box.right - box.left) * img.width, (box.bottom - box.top) * img.height) * 0.75f
                val side = (2 * half).toInt().coerceIn(4, minOf(img.width, img.height))
                val x0 = (cx - side / 2f).toInt().coerceIn(0, img.width - side)
                val y0 = (cy - side / 2f).toInt().coerceIn(0, img.height - side)
                drawImage(image = img, srcOffset = IntOffset(x0, y0), srcSize = IntSize(side, side),
                    dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()))
            }
            unknown || thumbPath == null || img == null ->
                Text("?", style = MaterialTheme.typography.headlineSmall, color = colors.onSurfaceVariant)
        }
    }
}
