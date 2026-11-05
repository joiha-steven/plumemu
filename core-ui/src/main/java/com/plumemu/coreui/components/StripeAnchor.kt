package com.plumemu.coreui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult

/**
 * Where the focused slab's accent stripe stands on a card with a title and a line or more under it: beside the title's first line,
 * not in the middle of the whole card (which, with a long description, is beside the description). A one-line row needs none: the stripe
 * is in the middle of the row, which is its line. The card calls [card] on the box the slab is drawn on, the title [title] and
 * hands its text layout to [measured]; [shiftY] (px, from the card's middle, up is negative) is read while drawing, so the card recomposes
 * nothing when the layout settles.
 */
@Stable
class StripeAnchor {
    /** The stripe's offset from the slab's middle: 0 until the card and its title are placed. */
    var shiftY by mutableFloatStateOf(0f)
        private set

    private var cardBox: LayoutCoordinates? = null
    private var titleBox: LayoutCoordinates? = null
    private var firstLineMiddle = Float.NaN

    /** Marks the box the slab is drawn on. Put it where [focusPill] is, before the padding. */
    fun card(): Modifier =
        Modifier.onGloballyPositioned {
            cardBox = it
            update()
        }

    /** Marks the title's text. */
    fun title(): Modifier =
        Modifier.onGloballyPositioned {
            titleBox = it
            update()
        }

    /** The title's text layout, from `onTextLayout`: its first line's middle is where the stripe stands. */
    fun measured(result: TextLayoutResult) {
        firstLineMiddle = (result.getLineTop(0) + result.getLineBottom(0)) / 2f
        update()
    }

    private fun update() {
        val card = cardBox?.takeIf { it.isAttached } ?: return
        val title = titleBox?.takeIf { it.isAttached } ?: return
        if (firstLineMiddle.isNaN()) return
        shiftY = card.localPositionOf(title, Offset.Zero).y + firstLineMiddle - card.size.height / 2f
    }
}
