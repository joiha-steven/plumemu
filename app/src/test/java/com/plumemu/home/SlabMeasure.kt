package com.plumemu.home

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertTrue
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

/** The columns (px) where a slab whose list starts at [left] draws its accent bar. */
internal fun ComposeContentTestRule.barColumns(left: Float): IntRange {
    val density = this.density.density
    return (left - BAR_BEFORE_LIST.value * density).toInt().coerceAtLeast(0)..(left + BAR_INTO_LIST.value * density).toInt()
}

/**
 * The middle (px) of the slab's accent bar in [from] to [until] over the columns [xs], or -1 when no slab is drawn. The bar is centred on the slab, solid and far from the tones
 * around it, so it places the slab to a fraction of a pixel on any theme, however faint the rim. Weighed by how many columns each row holds, so its anti-aliased ends count for what they show.
 */
internal fun ImageBitmap.slabMiddle(
    xs: IntRange,
    from: Int,
    until: Int,
): Float {
    val pixels = toPixelMap()
    val rows = from.coerceAtLeast(0) until until.coerceAtMost(height)
    val widths = rows.map { y -> xs.count { x -> x in 0 until width && pixels[x, y].let { maxOf(it.red, it.green, it.blue) - minOf(it.red, it.green, it.blue) } >= BAR_SATURATION } }
    val runs = mutableListOf<IntRange>()
    var start = -1
    for (i in 0..widths.size) {
        val on = i < widths.size && widths[i] > 0
        if (on && start < 0) start = i
        if (!on && start >= 0) {
            runs += start until i
            start = -1
        }
    }
    // Tall and narrow: a current dot is too short, a swatch too wide, and of what is left accent text has less ink.
    val bar = runs.filter { run -> run.count() >= BAR_ROWS_AT_LEAST && run.maxOf { widths[it] } <= BAR_COLUMNS_AT_MOST }.maxByOrNull { run -> run.sumOf { widths[it] } } ?: return -1f
    val ink = bar.sumOf { widths[it] }.toFloat()
    return rows.first + bar.sumOf { (it + 0.5) * widths[it] }.toFloat() / ink
}

/** The slab's top for a row [rowHeight] px tall overhung by [overhang], from [slabMiddle]; -1 when none. */
internal fun ImageBitmap.slabTopRow(
    xs: IntRange,
    from: Int,
    until: Int,
    rowHeight: Float,
    overhang: Float,
): Float = slabMiddle(xs, from, until).let { if (it < 0) -1f else it - rowHeight / 2 - overhang }

/** The height of the row tagged [tag] without what its box holds under the visible row ([slabOverhangPx]'s [gap]). */
internal fun ComposeContentTestRule.rowHeightPx(
    tag: String,
    gap: Dp = 0.dp,
): Float {
    val node = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first()
    return node.size.height - gap.value * node.layoutInfo.density.density
}

/** The slab rests centred on the row tagged [tag]: its bar's middle is the row's middle. [gap] as in [slabOverhangPx]. */
internal fun ComposeContentTestRule.assertSlabRestsOn(
    tag: String,
    xs: IntRange,
    from: Int,
    until: Int,
    gap: Dp = 0.dp,
) {
    val node = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first()
    val middle = node.boundsInRoot.top + rowHeightPx(tag, gap) / 2
    val drawn = onRoot().captureToImage().slabMiddle(xs, from, until)
    assertTrue("at rest a slab is drawn on $tag", drawn >= 0)
    assertTrue("at rest the slab is centred on $tag: row middle $middle, slab middle $drawn", abs(drawn - middle) <= SLAB_CENTRE_PX)
}

/** Half a pixel and the bar's own rounding. */
private const val SLAB_CENTRE_PX = 0.75f

private val BAR_BEFORE_LIST = 24.dp
private val BAR_INTO_LIST = 32.dp

/** How far apart (0..1) a bar pixel's strongest and weakest channels lie at the least: the accent, not its glow or a grey around it. */
private const val BAR_SATURATION = 0.3f

/** The fewest rows a bar is tall and the most columns it is wide, at the 2x the tests render at (17.33 by 4 dp, anti-aliased). */
private const val BAR_ROWS_AT_LEAST = 24
private const val BAR_COLUMNS_AT_MOST = 10
