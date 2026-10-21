package com.albumdone.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albumdone.app.R

/**
 * The look: photo first, mostly white, near-black buttons and one saffron accent.
 * Mockup: https://claude.ai/artifact/FhsiF3pfnqkssR1UJ5PdQz
 */
val Ink = Color(0xFF111114)
val Muted = Color(0xFF5B5B66)
val Saffron = Color(0xFFFF5A1F)
val SaffronSoft = Color(0xFFFFF1EA)
/** Warnings (someone with too few photos): darker than the accent so it reads as text. */
val Warn = Color(0xFFC2410C)

private val Light = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4F4F7),
    onPrimaryContainer = Ink,
    secondary = Saffron,
    onSecondary = Color.White,
    secondaryContainer = SaffronSoft,
    onSecondaryContainer = Ink,
    tertiary = Saffron,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF4F4F7),
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F8FA),
    surfaceContainer = Color(0xFFF4F4F7),
    surfaceContainerHigh = Color(0xFFEDEDF1),
    surfaceContainerHighest = Color(0xFFE6E6EB),
    outline = Color(0xFFD9D9E0),
    outlineVariant = Color(0xFFE9E9EE),
    error = Warn,
    errorContainer = SaffronSoft,
    onErrorContainer = Color(0xFF5C2A00),
)

private val Dark = darkColorScheme(
    primary = Color.White,
    onPrimary = Ink,
    primaryContainer = Color(0xFF26262C),
    onPrimaryContainer = Color.White,
    secondary = Saffron,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A2116),
    onSecondaryContainer = Color(0xFFFFE3D6),
    tertiary = Saffron,
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF0B0B0D),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF1C1C21),
    onSurfaceVariant = Color(0xFFA8A8B3),
    surfaceContainerLowest = Color(0xFF070708),
    surfaceContainerLow = Color(0xFF141418),
    surfaceContainer = Color(0xFF1C1C21),
    surfaceContainerHigh = Color(0xFF26262C),
    surfaceContainerHighest = Color(0xFF303037),
    outline = Color(0xFF45454E),
    outlineVariant = Color(0xFF2C2C33),
    error = Color(0xFFFF8A5C),
    errorContainer = Color(0xFF3A2116),
    onErrorContainer = Color(0xFFFFDDB0),
)

/** Plus Jakarta Sans for everything; Devanagari names fall back to the phone's own font. */
val AppFont = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) =
    TextStyle(fontFamily = AppFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)

private val Type = Typography(
    displayLarge = style(48, 54, FontWeight.ExtraBold, -1.5),
    displayMedium = style(40, 46, FontWeight.ExtraBold, -1.2),
    displaySmall = style(34, 40, FontWeight.ExtraBold, -1.0),
    headlineLarge = style(30, 36, FontWeight.ExtraBold, -0.8),
    headlineMedium = style(26, 32, FontWeight.ExtraBold, -0.6),
    headlineSmall = style(22, 28, FontWeight.Bold, -0.4),
    titleLarge = style(20, 26, FontWeight.Bold, -0.3),
    titleMedium = style(16, 22, FontWeight.Bold),
    titleSmall = style(14, 20, FontWeight.Bold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.SemiBold),
    labelSmall = style(11, 14, FontWeight.SemiBold, 0.4),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun AlbumDoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, shapes = AppShapes, content = content)
}
