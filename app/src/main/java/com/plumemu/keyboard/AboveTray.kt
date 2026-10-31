package com.plumemu.keyboard

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * A scrolling column under Plumemu's tray (the sign-in form in setup and in Settings, keyboard step
 * 2b-2): while [isUp] its height ends [covers] above its own bottom, where the tray's top is, so its
 * scroll can show the focused field above the tray; with the tray down it has its whole height
 * again. Laid out once, not animated (the tray slides by itself, by translation), and
 * [isUp] is read while measuring: the tray going up lays the column out again, it recomposes nothing.
 * The space it gives up stays its own (nothing else is placed there): the tray covers it.
 */
internal fun Modifier.aboveTray(
    isUp: () -> Boolean,
    covers: Dp,
): Modifier =
    layout { measurable, constraints ->
        if (!isUp() || !constraints.hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val height = (constraints.maxHeight - covers.roundToPx()).coerceAtLeast(0)
        val placeable = measurable.measure(constraints.copy(minHeight = minOf(constraints.minHeight, height), maxHeight = height))
        layout(placeable.width, constraints.maxHeight) { placeable.place(0, 0) }
    }
