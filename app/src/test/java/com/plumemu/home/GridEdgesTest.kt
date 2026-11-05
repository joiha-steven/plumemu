package com.plumemu.home

import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packed grids at the reference screens: with mixed shapes every full row (all but the list's last) meets both content edges with
 * the board's gap between boxes and every picture whole; with one shape every row has the same box width and the same column x,
 * the last row included. Plain JVM.
 */
class GridEdgesTest {
    // 853x480: Pocket 6, 1920x1080 at density 2.25.
    private val screens = listOf(768 to 432, 576 to 432, 640 to 480, 832 to 468, 853 to 480)
    private val mixes =
        listOf(
            List(40) { if (it % 3 == 0) 1.4f else 0.71f },
            List(40) { listOf(0.715f, 1f, 1.2f, 0.85f, 1.4f)[it % 5] },
            List(40) { 1f },
            List(40) { 0.71f },
            List(40) { 1.4f },
        )

    private fun gridOf(
        w: Int,
        h: Int,
        size: GridSize,
        aspects: List<Float>,
    ): Triple<PackedGrid, Float, Float> {
        val layout = PlumLayout(w.dp, h.dp, peek = PlumDimens.peekWidth, textScale = 1f, gridSize = size)
        val shape = CoverShape(1f)
        val width = layout.gridCoversWidth.value
        val gap = layout.gridGap(shape).value
        val chromeX = (CollarSize.Cover.sidePadding * 2).value
        val chromeY = (CollarSize.Cover.topPadding + CollarSize.Cover.bottomPadding).value
        return Triple(packRows(aspects, layout.gridColumns(shape), layout.gridRowHeight(shape).value, gap, width, chromeX, chromeY), width, gap)
    }

    @Test
    fun oneShapeHasIdenticalColumnsOnEveryRow() {
        val failures = ArrayList<String>()
        for ((w, h) in screens) for (size in GridSize.entries) for (aspect in listOf(0.71f, 1f, 1.4f)) {
            val (grid, width, gapOf) = gridOf(w, h, size, List(40) { aspect })
            val columns = grid.rows[0].boxes
            val end = columns.last().let { it.x + it.width }
            // Columns start at one edge and end at the other with the board gap between.
            if (abs(columns[0].x) > 0.01f) failures += "$w x $h $size $aspect: starts at ${columns[0].x}"
            if (abs(end - width) > 0.01f) failures += "$w x $h $size $aspect: ends at $end of $width"
            if (columns.zipWithNext { a, b -> b.x - (a.x + a.width) }.any { abs(it - gapOf) > 0.01f }) failures += "$w x $h $size $aspect: gaps differ from the board gap"
            grid.rows.forEachIndexed { r, row ->
                row.boxes.forEachIndexed { c, b ->
                    if (abs(b.x - columns[c].x) > 0.01f || abs(b.width - columns[0].width) > 0.01f) failures += "$w x $h $size $aspect row $r box $c: x ${b.x} width ${b.width}"
                }
            }
        }
        assertTrue(failures.take(10).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun everyFullRowOfMixedShapesFillsBothEdgesWithBoardGapsAndKeepsEachPictureWhole() {
        // The requirement changed: a mixed row is no longer left at natural widths (capped) when justifying would gain much; every
        // full row meets both edges, so only the very last row of the list may end short.
        val failures = ArrayList<String>()
        val chromeX = (CollarSize.Cover.sidePadding * 2).value
        val chromeY = (CollarSize.Cover.topPadding + CollarSize.Cover.bottomPadding).value
        for ((w, h) in screens) for (size in GridSize.entries) for (aspects in mixes.take(2)) {
            val (grid, width, gap) = gridOf(w, h, size, aspects)
            grid.rows.dropLast(1).forEachIndexed { r, row ->
                val right = row.boxes.last().let { it.x + it.width }
                val gaps = row.boxes.zipWithNext { a, b -> b.x - (a.x + a.width) }
                if (abs(row.boxes.first().x) > 0.01f || abs(right - width) > 0.01f || gaps.any { abs(it - gap) > 0.01f }) {
                    failures += "$w x $h $size row $r: left ${row.boxes.first().x} right $right of $width gaps $gaps"
                }
                row.boxes.forEachIndexed { k, box ->
                    val shape = (box.width - chromeX) / (row.height - chromeY)
                    if (abs(shape - aspects[row.start + k]) > 0.001f) failures += "$w x $h $size row $r box $k picture is $shape, not ${aspects[row.start + k]}"
                }
            }
        }
        assertTrue(failures.take(10).joinToString("\n"), failures.isEmpty())
    }

    /** A widened cell leaves its extra room outside the collar: the collar (and so the lit groove) hugs the picture, no accent fill beside it. */
    @Test
    fun aWidenedCellsCollarHugsThePicture() {
        val density = androidx.compose.ui.unit.Density(1f)
        val side = CollarSize.Cover.sidePadding.value
        val cell = androidx.compose.ui.geometry.Rect(0f, 0f, 200f, 100f)
        val inner = androidx.compose.ui.geometry.Rect(side, 0f, 200f - side, 100f)
        val collar = with(density) { collarRect(1f, cell) }
        val picture = shelfRect(1f, inner.copy(top = CollarSize.Cover.topPadding.value, bottom = 100f - CollarSize.Cover.bottomPadding.value))
        assertTrue("collar ${collar.width} is the picture ${picture.width} plus its two sides", abs(collar.width - (picture.width + 2 * side)) < 0.01f)
        assertTrue("the collar is centred: left ${collar.left}, right ${200f - collar.right}", abs(collar.left - (200f - collar.right)) < 0.01f)
    }
}
