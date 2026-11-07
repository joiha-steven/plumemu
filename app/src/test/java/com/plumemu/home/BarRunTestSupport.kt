package com.plumemu.home

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.theme.PlumDimens

/**
 * A column's node now runs from bar to bar (BarRun.kt); its slot is the strip the layout gave before: [PlumDimens.barGap] under the top
 * bar to [PlumDimens.footerGap] over the hint bar. What does not scroll stands in the slot; rows may run through the gaps, never into a bar.
 */
internal fun DpRect.slot(
    top: androidx.compose.ui.unit.Dp = PlumDimens.barGap,
    bottom: androidx.compose.ui.unit.Dp = PlumDimens.footerGap,
): DpRect = DpRect(left, this.top + top, right, this.bottom - bottom)

/** The bounds in root px of everything the two home bars draw themselves (their texts and keys), 4 dp round each: what a row under a bar may share a pixel with. */
internal fun androidx.compose.ui.test.junit4.ComposeTestRule.barsOwnBounds(): List<androidx.compose.ui.geometry.Rect> {
    val own = mutableListOf<androidx.compose.ui.geometry.Rect>()
    val grow = 4f * density.density

    fun collect(node: androidx.compose.ui.semantics.SemanticsNode) {
        node.children.forEach {
            own += it.boundsInRoot.inflate(grow)
            collect(it)
        }
    }
    for (tag in listOf(HomeTags.TOP_BAR, HomeTags.HINT_BAR)) collect(onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())
    return own
}
