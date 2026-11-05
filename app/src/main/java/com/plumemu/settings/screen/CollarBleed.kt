package com.plumemu.settings.screen

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.offset
import com.plumemu.coreui.components.CollarSize

/**
 * Lets the collar added by `Modifier.collar(size)` further in the chain reach into the space around the part instead of
 * taking it: the part keeps the size and place it had without a collar (so a grid's columns and gaps stay), the lip and
 * groove fall in the gap beside it. Put it before `collar`; the gap must be at least the collar's padding.
 */
internal fun Modifier.collarBleed(size: CollarSize): Modifier =
    layout { measurable, constraints ->
        val start = size.sidePadding.roundToPx()
        val top = size.topPadding.roundToPx()
        val bottom = size.bottomPadding.roundToPx()
        val wider = constraints.offset(horizontal = 2 * start, vertical = top + bottom)
        val placeable = measurable.measure(Constraints(wider.minWidth, wider.maxWidth, wider.minHeight, wider.maxHeight))
        val width = (placeable.width - 2 * start).coerceAtLeast(0)
        val height = (placeable.height - top - bottom).coerceAtLeast(0)
        layout(width, height) { placeable.place(-start, -top) }
    }

/**
 * For a square grid cell that holds a collared cap: the cell stays [width] x [width] and the cap, square, fills what the
 * collar's lip and groove leave at the sides; the collar's extra bottom depth (a dp or so) spills past the cell. Put it
 * before `collar`, on a modifier whose width is fixed.
 */
internal fun Modifier.collarCell(size: CollarSize): Modifier =
    layout { measurable, constraints ->
        val width = constraints.maxWidth
        val start = size.sidePadding.roundToPx()
        val side = (width - 2 * start).coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(width, side + size.topPadding.roundToPx() + size.bottomPadding.roundToPx()))
        layout(width, width) { placeable.place(0, 0) }
    }
