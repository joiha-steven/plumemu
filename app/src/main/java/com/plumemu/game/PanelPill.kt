package com.plumemu.game

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import com.plumemu.coreui.components.GlidingPillHost
import com.plumemu.coreui.components.rememberGlidingPill
import com.plumemu.coreui.theme.ListGlide
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * The gliding focus pill of a panel's option list (the game menu, Fix name, Fix box art): the list draws the
 * pill so it travels from row to row with the list's own glide. [focusedItem] is the lazy item that wears it, or -1 for none.
 * Panel rows differ in height (a badge or note under a label, a label on three lines), so the focused row reports its own box.
 * The index lives in a state read only while drawing, so a move recomposes nothing extra.
 */
@Composable
internal fun rememberPanelPill(
    listState: LazyListState,
    glide: ListGlide,
    focusedItem: Int,
): GlidingPillHost {
    val index = remember { mutableIntStateOf(-1) }
    index.intValue = focusedItem
    val layout = PlumTheme.layout
    return rememberGlidingPill(listState, glide, rowHeight = layout.rowHeight, stride = layout.rowHeight + PlumDimens.listRowGap, extraGap = layout.rowHeight * TALL_STEP_ROWS, rowsVary = true) { index.intValue }
}

/** Panel rows carry notes, badges and thumbnails, so one step can be several plain rows long; the pill's lag may span it. */
private const val TALL_STEP_ROWS = 6
