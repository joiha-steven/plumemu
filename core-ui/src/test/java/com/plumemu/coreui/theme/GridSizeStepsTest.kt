package com.plumemu.coreui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Each grid size is a step a person can see: on the Nova at 640 x 480, Medium and Large square covers (DS, PlayStation,
 * Game Boy, Dreamcast) both drew 3 columns, so Large changed nothing. And at every width, size and text size the covers
 * fill the grid's span to the right margin (no gap at the right).
 */
class GridSizeStepsTest {
    private val widths = listOf(576.dp to 432.dp, 640.dp to 480.dp, 768.dp to 432.dp, 832.dp to 468.dp, 853.dp to 480.dp)

    private fun layout(
        width: Dp,
        height: Dp,
        text: TextSize,
        size: GridSize,
    ) = PlumLayout(width, height, peek = PlumDimens.peekWidth * text.heightScale, textScale = text.heightScale, gridSize = size)

    private val shapes = mapOf(CoverClass.Landscape to CoverShape(1.4f), CoverClass.Square to CoverShape(1f), CoverClass.Tall to CoverShape(0.7f))

    /**
     * From Medium text up. At Small text (area 544) square Medium and Large stay 3 and 3: Large would need a cell over
     * 173.3, and the Thor keeps 4 square Large columns at its default Large text (area 731.4) only up to 173.8.
     */
    @Test
    fun eachSizeHasFewerColumnsThanTheOneBeforeAt640() {
        for (text in TextSize.entries.filter { it >= TextSize.Medium }) {
            for ((coverClass, _) in shapes) {
                val columns = GridSize.entries.map { layout(640.dp, 480.dp, text, it).gridColumns(coverClass) }
                assertTrue("$coverClass at 640, $text text: $columns", columns.zipWithNext().all { (a, b) -> a > b })
            }
        }
    }

    @Test
    fun coversReachTheRightMarginAtEveryWidth() {
        for ((width, height) in widths) {
            for (text in TextSize.entries) {
                for (size in GridSize.entries) {
                    val grid = layout(width, height, text, size)
                    for ((coverClass, shape) in shapes) {
                        val columns = grid.gridColumns(shape)
                        val end = grid.gridCoverWidth(shape) * columns + grid.gridGap(shape) * (columns - 1)
                        val label = "$coverClass $size at $width, $text text"
                        assertTrue("$label: covers end ${grid.gridCoversWidth - end} short", abs((grid.gridCoversWidth - end).value) < 0.01f)
                        // The span starts after the peek and its gap and ends on the right margin.
                        assertEquals("$label: the span ends on the right margin", width - PlumDimens.stripMarginSide, grid.peek + PlumDimens.columnGap + grid.gridCoversWidth)
                    }
                }
            }
        }
    }
}
