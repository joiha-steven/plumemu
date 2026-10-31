package com.plumemu.coreui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * [content] with the grid at [size] instead of the theme's (a screenshot or a test of one size;
 * the app sets it through [PlumThemeSettings.gridSize]).
 */
@Composable
fun ProvideGridSize(
    size: GridSize,
    content: @Composable () -> Unit,
) {
    val layout = LocalPlumLayout.current
    val sized = remember(layout, size) { layout.copy(gridSize = size) }
    CompositionLocalProvider(LocalPlumLayout provides sized, content = content)
}
