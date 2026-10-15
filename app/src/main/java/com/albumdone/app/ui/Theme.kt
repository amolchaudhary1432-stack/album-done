package com.albumdone.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Plum = Color(0xFF3B1F45)
private val PlumLight = Color(0xFF6B4A78)
private val Gold = Color(0xFFF6C247)
private val Cream = Color(0xFFFBE3A0)

private val Light = lightColorScheme(
    primary = Plum,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1E4F5),
    onPrimaryContainer = Plum,
    secondary = Color(0xFF8A6A00),
    onSecondary = Color.White,
    secondaryContainer = Cream,
    onSecondaryContainer = Color(0xFF3A2C00),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE3C2EE),
    onPrimary = Plum,
    primaryContainer = PlumLight,
    onPrimaryContainer = Color.White,
    secondary = Gold,
    onSecondary = Color(0xFF3A2C00),
    secondaryContainer = Color(0xFF5A4700),
    onSecondaryContainer = Cream,
)

@Composable
fun AlbumDoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
