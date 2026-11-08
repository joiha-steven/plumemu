package com.plumemu.coreui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates

/**
 * Where the focused game or app is drawn, in window px: the place a started game grows from (design/rules.md › Shapes
 * and motion, the launch). The games list's slab, the focused cover and the focused Apps tile note themselves here while
 * they are drawn, and a list with nothing focused clears it. Plain fields, never state: a frame of motion writes it and
 * recomposes nothing. Read on the main thread.
 */
class LaunchOrigin {
    private var last: Rect? = null

    /** The rectangle drawn last, in window px; null when nothing focused is drawn. */
    val window: Rect? get() = last

    /** [local] (px in [coordinates]' node) was drawn this frame. Call it while drawing. */
    fun note(
        coordinates: LayoutCoordinates?,
        local: Rect,
    ) {
        if (coordinates == null || !coordinates.isAttached || local.isEmpty) return
        last = Rect(coordinates.localToWindow(local.topLeft), local.size)
    }

    /** Nothing focused is drawn any more (a list lost its focused row). */
    fun clear() {
        last = null
    }
}

/** The home's [LaunchOrigin]; null where nothing can be started (Settings, setup, previews). */
val LocalLaunchOrigin = staticCompositionLocalOf<LaunchOrigin?> { null }
