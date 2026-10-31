package com.plumemu.game

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * A game panel with a field: its [header] (title, body lines) above its [body]
 * (the field and what is under it). While Plumemu's tray is up over the field ([TypingState.isTrayUp])
 * the panel's space ends at the tray's top ([keyboardCovers] less at the bottom): the panel is laid
 * out once in that height, not animated (the tray slides by itself, by translation). The
 * field stays whole and what is under it gets the space left, where its own list scrolls; so that
 * at least one line of it shows, the header gives way first, from its top (clipped by the panel's
 * box). With the tray down the panel has its whole height again. The tray's state is read while
 * measuring: it lays the panel out again, it recomposes nothing.
 */
@Composable
internal fun TypingPanelLayout(
    typing: TypingState,
    keyboardCovers: Dp,
    modifier: Modifier,
    header: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    // The field and one row under it, with their gap: what must always show.
    val keep = PlumTheme.layout.rowHeight * 2 + PlumDimens.listRowGap
    Layout(contents = listOf({ Column { header() } }, { Column { body() } }), modifier) { (headers, bodies), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val available = if (typing.isTrayUp) (height - keyboardCovers.roundToPx()).coerceAtLeast(0) else height
        val loose = Constraints(minWidth = width, maxWidth = width)
        val top = headers.single().measure(loose)
        val headerShown = top.height.coerceAtMost((available - keep.roundToPx()).coerceAtLeast(0))
        val under = bodies.single().measure(loose.copy(maxHeight = (available - headerShown).coerceAtLeast(0)))
        layout(width, height) {
            top.place(0, headerShown - top.height)
            under.place(0, headerShown)
        }
    }
}
