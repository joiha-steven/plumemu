package com.plumemu.home

import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import org.junit.Assert.assertEquals
import kotlin.math.abs
import org.junit.Test

/**
 * A cover grid of one shape fills its width at every grid size and reference screen: the columns are the column table's and the
 * last box ends on the right margin. Plain JVM: the layout's numbers go through [packRows] as the drawn grid does.
 */
class GridFillsWidthTest {
    private data class Screen(val label: String, val width: Int, val height: Int, val textScale: Float = 1f)

    private val screens =
        ReferenceShots.Size.entries.map { Screen(it.label, it.widthDp, it.heightDp) } +
            Screen("832x468", 832, 468) + Screen("Thor 837x471", 837, 471) + Screen("Thor 837x471 Large text", 837, 471, 1.1f)

    // PS2 / Wii 0.715, Mega Drive 0.71, a square class box, a landscape one.
    private val shapes = listOf(0.715f, 0.71f, 0.85f, 1.0f, 1.12f, 1.2f, 1.4f)

    private fun layoutOf(screen: Screen, size: GridSize) =
        PlumLayout(screen.width.dp, screen.height.dp, peek = PlumDimens.peekWidth * screen.textScale, textScale = screen.textScale, gridSize = size)

    private data class Result(val columns: Int, val boxWidth: Float, val leftOver: Float, val gap: Float)

    private fun firstRow(layout: PlumLayout, aspect: Float): Result {
        val shape = CoverShape(aspect)
        val gap = layout.gridGap(shape).value
        val maxWidth = layout.gridCoversWidth.value
        val chromeX = (CollarSize.Cover.sidePadding * 2).value
        val chromeY = (CollarSize.Cover.topPadding + CollarSize.Cover.bottomPadding).value
        val grid = packRows(List(60) { aspect }, layout.gridColumns(shape), layout.gridRowHeight(shape).value, gap, maxWidth, chromeX, chromeY)
        val row = grid.rows.first().boxes
        return Result(row.size, row.last().width, maxWidth - (row.last().x + row.last().width), gap)
    }

    @Test
    fun everySizeFillsTheWidthExactlyWithTheTablesColumns() {
        // The requirement changed: no gap, however small, is left at the right, and the column count is the Size step's.
        val failures = ArrayList<String>()
        for (screen in screens) for (size in GridSize.entries) for (aspect in shapes) {
            val layout = layoutOf(screen, size)
            val r = firstRow(layout, aspect)
            val expected = layout.gridColumns(CoverShape(aspect))
            if (expected != r.columns || abs(r.leftOver) > 0.01f) failures += "${screen.label} $size shape $aspect: table $expected, drawn ${r.columns} columns, ${r.leftOver} dp left"
        }
        assertEquals(failures.take(10).joinToString("\n"), 0, failures.size)
    }

}
