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

private val Plum = Color(0xFF3B1F45)
private val Gold = Color(0xFFE9B730)
private val Cream = Color(0xFFFBE3A0)

private val Light = lightColorScheme(
    primary = Plum,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1E4F5),
    onPrimaryContainer = Plum,
    secondary = Color(0xFF8A6A00),
    onSecondary = Color.White,
    secondaryContainer = Cream,
    onSecondaryContainer = Plum,
    tertiary = Gold,
    background = Color(0xFFFCF9F7),
    onBackground = Color(0xFF231A26),
    surface = Color(0xFFFCF9F7),
    onSurface = Color(0xFF231A26),
    surfaceVariant = Color(0xFFF3ECF1),
    onSurfaceVariant = Color(0xFF6E5F73),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F4F7),
    surfaceContainer = Color(0xFFF5EFF3),
    surfaceContainerHigh = Color(0xFFF0E9EE),
    surfaceContainerHighest = Color(0xFFEAE2E8),
    outline = Color(0xFFB9A9BE),
    outlineVariant = Color(0xFFE8DEEA),
    error = Color(0xFFB45309),
    errorContainer = Color(0xFFFFF1DC),
    onErrorContainer = Color(0xFF5C2A00),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD9B8E6),
    onPrimary = Color(0xFF1E1423),
    primaryContainer = Color(0xFF4A3352),
    onPrimaryContainer = Color.White,
    secondary = Gold,
    onSecondary = Color(0xFF3A2C00),
    secondaryContainer = Color(0xFF4A3A12),
    onSecondaryContainer = Cream,
    tertiary = Gold,
    background = Color(0xFF120B15),
    surface = Color(0xFF120B15),
    surfaceVariant = Color(0xFF2A1D30),
    onSurfaceVariant = Color(0xFFB8A7BE),
    surfaceContainerLowest = Color(0xFF0D080F),
    surfaceContainerLow = Color(0xFF1A111D),
    surfaceContainer = Color(0xFF1E1423),
    surfaceContainerHigh = Color(0xFF281C2D),
    surfaceContainerHighest = Color(0xFF332638),
    outline = Color(0xFF6E5F73),
    outlineVariant = Color(0xFF3A2B40),
    error = Color(0xFFF4B35E),
    errorContainer = Color(0xFF3A2A12),
    onErrorContainer = Color(0xFFFFDDB0),
)

/** Mukta reads well and also covers Devanagari (आई, बाबा). Marcellus for a few headings. */
val BodyFont = FontFamily(
    Font(R.font.mukta_regular, FontWeight.Normal),
    Font(R.font.mukta_medium, FontWeight.Medium),
    Font(R.font.mukta_semibold, FontWeight.SemiBold),
)
val DisplayFont = FontFamily(Font(R.font.marcellus_regular, FontWeight.Normal))

private val Type = Typography().let { t ->
    Typography(
        displayMedium = TextStyle(fontFamily = DisplayFont, fontSize = 44.sp, lineHeight = 50.sp),
        displaySmall = TextStyle(fontFamily = DisplayFont, fontSize = 34.sp, lineHeight = 40.sp),
        headlineLarge = TextStyle(fontFamily = DisplayFont, fontSize = 30.sp, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontFamily = DisplayFont, fontSize = 26.sp, lineHeight = 32.sp),
        headlineSmall = TextStyle(fontFamily = DisplayFont, fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = t.titleLarge.copy(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontFamily = BodyFont),
        bodyMedium = t.bodyMedium.copy(fontFamily = BodyFont),
        bodySmall = t.bodySmall.copy(fontFamily = BodyFont),
        labelLarge = t.labelLarge.copy(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontFamily = BodyFont),
        labelSmall = t.labelSmall.copy(fontFamily = BodyFont),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AlbumDoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, shapes = AppShapes, content = content)
}
