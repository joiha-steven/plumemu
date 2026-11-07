package com.plumemu.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout

/**
 * A games list with the systems focused and no room for the details column: it takes the width to the right margin, its right-hand text on that margin (design/rules.md › List and
 * details). The slot in the strip keeps the full list's width, so nothing beside it moves: the list overflows the slot by [overflow] and its rows change width once, when the column
 * changes, through [endInset]; [rowEndShift] then carries the right-hand text between the two places as the strip slides, in the draw pass, so no frame of the slide lays a row out.
 */
@Stable
internal class ListStretch(
    private val state: StripState,
    /** The list's width at the games stop. */
    private val fullWidth: Dp,
    /** Its width with the systems focused. */
    private val stretched: Dp,
    private val density: Float,
) {
    val overflow: Dp = (stretched - fullWidth).coerceAtLeast(0.dp)

    private val systemsFocused: Boolean
        get() = state.focus.column == Column.Systems

    /** Read while measuring: the room the rows leave at the end of the widest list. */
    fun endInset(): Dp = fullWidth + overflow - if (systemsFocused) stretched else fullWidth

    /** Read while drawing: where the right-hand text stands in the slide, from where the layout put it. */
    fun rowEndShift(): Float = (stretched - fullWidth).value * density * (state.systemNamesAlpha - if (systemsFocused) 1f else 0f)

    /** Read while drawing: what follows the list stays past it while the list is stretched, and comes in with the slide. */
    fun followerShift(): Float = overflow.value * density * state.systemNamesAlpha
}

/** The list's width to the right margin: its rows end [PlumDimens.gameRowEndPadding] past where the right-hand text ends. */
internal fun stretchedListWidth(layout: PlumLayout): Dp = layout.width - PlumDimens.stripMarginSide * 2 - layout.systemsColumnWidth - PlumDimens.columnGap + PlumDimens.gameRowEndPadding

internal val LocalListStretch = staticCompositionLocalOf<ListStretch?> { null }

@Composable
internal fun rememberListStretch(
    state: StripState,
    layout: PlumLayout,
    fullWidth: Dp,
    stretches: Boolean,
): ListStretch? {
    val density = LocalDensity.current.density
    return remember(state, layout, fullWidth, stretches, density) { if (stretches) ListStretch(state, fullWidth, stretchedListWidth(layout), density) else null }
}

/** The games list's content padding, with [ListStretch.endInset] read by the list's measuring: a change of column measures the rows again and recomposes none. */
internal fun ListStretch?.listPadding(
    top: Dp,
    bottom: Dp,
): PaddingValues {
    if (this == null) return PaddingValues(top = top, bottom = bottom)
    val stretch = this
    return object : PaddingValues {
        override fun calculateLeftPadding(layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Ltr) 0.dp else stretch.endInset()

        override fun calculateTopPadding() = top

        override fun calculateRightPadding(layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Ltr) stretch.endInset() else 0.dp

        override fun calculateBottomPadding() = bottom
    }
}

/** Lets the pages and the list run [ListStretch.overflow] past their slot. */
internal fun Modifier.overflowingSlot(stretch: ListStretch?): Modifier =
    if (stretch == null || stretch.overflow <= 0.dp) {
        this
    } else {
        layout { measurable, constraints ->
            val width = constraints.maxWidth + stretch.overflow.roundToPx()
            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
            layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
        }
    }

/** Inside the pages: what follows has the slot's width again, for what is drawn outside the rows (the pill) before [overflowingSlot] widens the list. */
internal fun Modifier.backToSlot(stretch: ListStretch?): Modifier =
    if (stretch == null || stretch.overflow <= 0.dp) {
        this
    } else {
        layout { measurable, constraints ->
            val width = constraints.maxWidth - stretch.overflow.roundToPx()
            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
            layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
        }
    }
