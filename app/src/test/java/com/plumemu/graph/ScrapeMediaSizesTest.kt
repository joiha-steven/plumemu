package com.plumemu.graph

import kotlin.math.ceil
import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The saved sizes hold what the screens draw, derived again from
 * docs/design/rules.md › Grid and the device table, so a change of the rules shows here.
 */
class ScrapeMediaSizesTest {
    // The Thor's upper screen: about 837 dp wide over 1920 px (docs/design/rules.md, device table).
    private val thorWidthDp = 837.0
    private val thorPxPerDp = 1920.0 / thorWidthDp

    // The Thor's bottom screen: 538 x 468 dp at 369 dpi.
    private val bottomPxPerDp = 369.0 / 160.0

    private fun px(dp: Double, perDp: Double) = ceil(dp * perDp).toInt()

    /** Width and height in px of one grid cell: area = width - 96, columns = floor((area + gap) / step). */
    private fun gridCell(step: Int, gap: Int, aspect: Double): Pair<Int, Int> {
        val area = thorWidthDp - 96
        val columns = floor((area + gap) / step)
        val width = (area - (columns - 1) * gap) / columns
        return px(width, thorPxPerDp) to px(width / aspect, thorPxPerDp)
    }

    @Test
    fun theGridSizeHoldsTheLargestCellOfBothCoverShapes() {
        val landscape = gridCell(step = 214, gap = 14, aspect = 1.4)
        val portrait = gridCell(step = 118, gap = 12, aspect = 0.78)
        val grid = ScrapeMediaSizes.Grid
        for ((w, h) in listOf(landscape, portrait)) {
            assertTrue("cell ${w}x$h fits in ${grid.maxWidth}x${grid.maxHeight}", w <= grid.maxWidth && h <= grid.maxHeight)
        }
        // Not much bigger than needed: within 10% of the largest cell on each side.
        assertTrue(grid.maxWidth <= maxOf(landscape.first, portrait.first) * 1.1)
        assertTrue(grid.maxHeight <= maxOf(landscape.second, portrait.second) * 1.1)
    }

    @Test
    fun theLargeSizeFillsTheThorsBottomScreenAndTheDetailsColumn() {
        val large = ScrapeMediaSizes.Large
        // The panel's pixels (1240 x 1080); its dp sizes are rounded, so within a pixel.
        assertEquals(538.0 * bottomPxPerDp, large.maxWidth.toDouble(), 1.0)
        assertEquals(468.0 * bottomPxPerDp, large.maxHeight.toDouble(), 1.0)
        assertTrue("details column, 248 dp", px(248.0, thorPxPerDp) <= large.maxWidth)
        assertTrue("narrow details cover, 228 x 164 dp", px(228.0, thorPxPerDp) <= large.maxWidth && px(164.0, thorPxPerDp) <= large.maxHeight)
    }

    @Test
    fun twoNamedSizesTheLargestLast() {
        assertEquals(listOf("grid", "large"), ScrapeMediaSizes.all.map { it.name })
        assertTrue(ScrapeMediaSizes.Grid.maxWidth < ScrapeMediaSizes.Large.maxWidth)
        assertTrue(ScrapeMediaSizes.Grid.maxHeight < ScrapeMediaSizes.Large.maxHeight)
    }
}
