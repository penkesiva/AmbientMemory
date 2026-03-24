package com.ambientmemory.timeline.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF3949AB)
private val Coral = Color(0xFFFF6F61)
private val SurfaceLight = Color(0xFFF7F8FC)

private val Colors =
    lightColorScheme(
        primary = Indigo,
        secondary = Coral,
        tertiary = Color(0xFF00897B),
        background = SurfaceLight,
        surface = Color.White,
    )

@Composable
fun AmbientMemoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        content = content,
    )
}
