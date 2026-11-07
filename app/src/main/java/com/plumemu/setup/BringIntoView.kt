package com.plumemu.setup

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.PlumTheme

/**
 * Asks the enclosing scroll to show this item while [isFocused] (setup's controls, the home screen question's buttons).
 * [above] is what sits above it and may change its place (a block that appears, a line that wraps): a new value asks again.
 * [ring] is how far the focus ring reaches past the item: that much more is brought into view, so the ring is not cut at the scroll's edge.
 * [below] is how much under the item must show too (a field over Plumemu's tray: the row or line under it); a new value asks again.
 * [thenShows] is what must stay whole when the scroll is too short for both: it is asked for last.
 */
@Composable
internal fun Modifier.bringIntoViewWhen(isFocused: Boolean, above: Any? = null, ring: Dp = 0.dp, below: Dp = 0.dp, thenShows: BringIntoViewRequester? = null): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val size = remember { arrayOf(IntSize.Zero) }
    val outset = with(LocalDensity.current) { maxOf(ring, LocalBringRoom.current).toPx() }
    val under = with(LocalDensity.current) { below.toPx() }
    val stops = LocalBringStops.current
    val stop = remember { BringStop() }
    stop.belowPx = under.toInt()
    if (stops != null) {
        DisposableEffect(stops, isFocused) {
            if (isFocused) stops.focusedOn(stop)
            onDispose { if (isFocused) stops.focusLeft(stop) }
        }
    }
    // Again when the type changes (size or font): the item grows and may no longer be in view.
    val type = PlumTheme.type
    LaunchedEffect(isFocused, type, above, under) {
        if (!isFocused) return@LaunchedEffect
        if ((outset > 0f || under > 0f) && size[0] != IntSize.Zero) {
            requester.bringIntoView(Rect(-outset, -outset, size[0].width + outset, size[0].height + outset + under))
            // When the scroll is too short for both, the item itself wins: the request above may have cut its top.
            if (under > 0f) requester.bringIntoView(Rect(-outset, -outset, size[0].width + outset, size[0].height + outset))
        } else {
            requester.bringIntoView()
        }
        thenShows?.bringIntoView()
    }
    val placed = if (stops == null) this else onPlaced { stop.coordinates = it; stops.place(stop) }
    return placed.onSizeChanged { size[0] = it }.bringIntoViewRequester(requester)
}

/** One item [bringIntoViewWhen] keeps in view, where it was last placed. */
internal class BringStop {
    var coordinates: LayoutCoordinates? = null

    /** What must show under it too, px. */
    var belowPx = 0
}

/** A scrolling panel that wants to know its focus stops (`PanelBlocks`): a rest must not lose the focused one. */
internal interface BringStops {
    fun place(stop: BringStop)

    fun focusedOn(stop: BringStop)

    fun focusLeft(stop: BringStop)
}

internal val LocalBringStops = compositionLocalOf<BringStops?> { null }

/** The least room [bringIntoViewWhen] keeps around an item: a scrolling panel's edge fade must not lie over what has the focus. */
internal val LocalBringRoom = compositionLocalOf { 0.dp }
