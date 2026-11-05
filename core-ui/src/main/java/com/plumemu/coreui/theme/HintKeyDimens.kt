package com.plumemu.coreui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/** A hint key in its collar: the room the collar takes beside the cap in the layout (it is drawn outside the cap's height). */
object HintKeyDimens {
    /** Between a wide cap's edge and its label, each side (board: `padding: 0 13px` at 1.5 px per dp; the type is ours, so the cap is wider only by its text). */
    val capPadding: Dp = (26f / 3f).dp

    val collarSide: Dp = CollarDimens.hintLip + CollarDimens.hintGroove

    /**
     * How far the key's collar reaches above a hint bar of [barHeight] whose cap is [capHeight] high (centred): a bar
     * under a grid moves down by this much so the collar stays out of the gap above it.
     */
    fun reachAboveBar(barHeight: Dp, capHeight: Dp): Dp = max(0.dp, CollarDimens.hintLip + CollarDimens.hintGroove - (barHeight - capHeight) / 2)
}
