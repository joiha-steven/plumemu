package com.plumemu.home

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.slabOverhang

/**
 * How many px the focused slab is drawn above (and below) the row box tagged [tag]: the slab is taller than the row and its
 * layout does not change, so a test that finds the slab's drawn edge adds this to compare it with the row's bounds.
 */
internal fun ComposeContentTestRule.slabOverhangPx(
    tag: String,
    /** What the tagged box holds under the visible row (a game row's tap gap): the slab is as tall as the row without it. */
    gap: Dp = 0.dp,
): Float {
    val node = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first()
    val density = node.layoutInfo.density
    return slabOverhang(node.size.height.toFloat() - gap.value * density.density, density)
}

/**
 * The first pixel row from [from] to [until] where most of the columns [xs] are the focused slab's top border: on a dark theme its white 0.42 is far
 * lighter than both the page or glow 2 px above and the gradient fill 3 px below (the fill itself has no flat colour to search for); -1 when none.
 */
internal fun ImageBitmap.slabTopRow(
    xs: IntRange,
    from: Int,
    until: Int,
): Int {
    val pixels = toPixelMap()
    fun lum(
        x: Int,
        y: Int,
    ) = pixels[x, y.coerceIn(0, height - 1)].let { it.red + it.green + it.blue }
    for (y in from.coerceAtLeast(0) until until.coerceAtMost(height - 3)) {
        val hits = xs.count { x -> lum(x, y) - lum(x, y + 3) > BORDER_STEP && lum(x, y) - lum(x, y - 2) > BORDER_STEP }
        if (hits * 2 >= xs.count()) return y
    }
    return -1
}

/** How much lighter (summed channels, 0..3) the border row is than its neighbours at the least: half a px of white 0.42 over the fill (about 1.0 whole), so an anti-aliased first row covered less than half is passed over, as a rounded top would be. */
private const val BORDER_STEP = 0.45f
