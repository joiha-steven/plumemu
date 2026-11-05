package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The packing of a cover grid's rows; plain JVM, units are dp. */
class GridPackingTest {
    private companion object {
        const val SCALE_LOW = 0.80f
        const val SCALE_HIGH = 1.25f
    }

    private fun pack(
        aspects: List<Float>,
        width: Float = 400f,
    ) = packRows(aspects, columns = 3, rowHeight = 112f, gap = 20f, maxWidth = width, chromeX = 8f, chromeY = 12f)

    private fun widthOf(aspect: Float) = 100f * aspect + 8f

    @Test
    fun everyFullMixedRowSpansTheWidthExactlyAtOneScaleWithTheBoardGaps() {
        // The requirement changed: mixed rows no longer keep natural widths under a cap with a gap left on the right;
        // each full row is scaled by one factor to both edges, and its height follows.
        val aspects = listOf(1.4f, 0.71f, 0.71f, 1.4f, 0.71f, 1.4f, 1f, 0.71f, 1.4f)
        val grid = pack(aspects, width = 360f)
        assertTrue("several rows", grid.rows.size >= 3)
        grid.rows.dropLast(1).forEach { row ->
            assertEquals("starts at the left edge", 0f, row.boxes.first().x, 0.001f)
            assertEquals("ends on the right edge", 360f, row.boxes.last().let { it.x + it.width }, 0.001f)
            row.boxes.zipWithNext { a, b -> assertEquals("board gap", 20f, b.x - (a.x + a.width), 0.001f) }
            row.boxes.forEachIndexed { k, box -> assertEquals("the picture keeps its shape", aspects[row.start + k], (box.width - 8f) / (row.height - 12f), 0.001f) }
        }
    }

    @Test
    fun aRowIsWidenedToFillWithNoCapSoItEndsTallerThanTheTarget() {
        // Two landscape boxes at the 100 picture height take 2 x 148 + 20 = 316 of 400, and a third wide one would shrink the row to
        // 0.62 of the target: the cheaper row is the two, grown to the width (no cap), taller than the target.
        val grid = pack(listOf(1.4f, 1.4f, 3f, 0.71f, 0.71f, 0.71f, 0.71f, 0.71f), width = 400f)
        val first = grid.rows[0]
        assertEquals(2, first.boxes.size)
        assertEquals(400f, first.boxes.last().let { it.x + it.width }, 0.001f)
        assertTrue("taller than the target (${first.height})", first.height > 112f)
    }

    @Test
    fun theLastRowIsLeftAlignedAtTheTargetHeightAndUnscaled() {
        val aspects = listOf(1.4f, 0.71f, 0.71f, 1.4f, 0.71f, 1f, 1f, 1.4f, 0.71f)
        val grid = pack(aspects)
        val last = grid.rows.last()
        assertEquals("the target height", 112f, last.height, 0.001f)
        assertEquals("left aligned", 0f, last.boxes.first().x, 0.001f)
        last.boxes.forEachIndexed { k, box -> assertEquals("natural width", 100f * aspects[last.start + k] + 8f, box.width, 0.001f) }
        assertTrue("short of the right edge", last.boxes.last().let { it.x + it.width } < 400f - 1f)
    }

    @Test
    fun theLastRowThatCanNotBeFilledAtItsHeightIsJustifiedLikeTheOthers() {
        // Boxes left that overshoot the width at the target height are shrunk to fit, not left over the edge.
        val grid = pack(listOf(1.4f, 1.4f, 1.4f, 1.4f, 1.4f, 0.71f), width = 330f)
        grid.rows.forEach { row -> assertTrue(row.boxes.last().let { it.x + it.width } <= 330f + 0.001f) }
    }

    @Test
    fun theRowBreaksKeepEveryFullRowsScaleNearOneForARealisticMix() {
        // DS (0.9), GBA landscape (1.5), PS1 square (1.0) and a poster (0.7) at the Thor's width and three targets.
        val shapes = listOf(0.9f, 1.5f, 1f, 0.7f)
        var worst = 1f
        var best = 1f
        for (seed in 1..40) {
            val random = java.util.Random(seed.toLong())
            val aspects = List(200) { shapes[random.nextInt(shapes.size)] }
            for (target in listOf(110f, 135f, 165f)) {
                val grid = packRows(aspects, columns = 4, rowHeight = target, gap = 6.667f, maxWidth = 741f, chromeX = 8f, chromeY = 12f)
                grid.rows.dropLast(1).forEach { row ->
                    worst = maxOf(worst, row.height / target)
                    best = minOf(best, row.height / target)
                }
            }
        }
        assertTrue("every full row's height stays within [$SCALE_LOW, $SCALE_HIGH] of the target, got [$best, $worst]", best >= SCALE_LOW && worst <= SCALE_HIGH)
    }

    @Test
    fun aVeryWideBoxIsNeverPastTheGridAndAloneItTakesTheWholeWidthAtItsOwnHeight() {
        // The requirement changed: a wide box is no longer forced onto a row of its own and cut to the width; rows are justified,
        // so it shares a row when that keeps the height near the target, and alone it spans the width with the height its shape gives.
        val grid = pack(listOf(6f, 6f, 1f, 6f), width = 300f)
        grid.rows.forEach { row -> row.boxes.forEach { assertTrue(it.x + it.width <= 300f + 0.001f) } }
        grid.rows.filter { it.boxes.size == 1 }.forEach { assertEquals(300f, it.boxes.single().width, 0.001f) }
    }

    @Test
    fun emptyListHasNoRowsAndOneGameHasOne() {
        val none = pack(emptyList())
        assertEquals(0, none.rows.size)
        assertEquals(0, none.toRows().rowCount)
        val one = pack(listOf(1f))
        assertEquals(1, one.rows.size)
        assertEquals(listOf(0), one.toRows().rowStarts)
        assertEquals(one.rows[0].boxes[0].width / 2f, one.toRows().centres.single(), 0.001f)
    }

    @Test
    fun aDamagedShapeCountsAsSquare() {
        val grid = pack(listOf(0f, Float.NaN, -2f), width = 3 * widthOf(1f) + 2 * 20f)
        assertTrue(grid.rows.flatMap { it.boxes }.all { it.width == widthOf(1f) })
    }

    @Test
    fun evenShapesPackLikeEvenColumns() {
        val grid = pack(List(7) { 1f }, width = 3 * widthOf(1f) + 2 * 20f)
        assertEquals(listOf(0, 3, 6), grid.rows.map { it.start })
    }

    @Test
    fun theTableTellsRowsAndCentres() {
        val grid = pack(listOf(1.4f, 0.71f, 0.71f, 1.4f))
        assertEquals(grid.rowOf(0), 0)
        assertEquals(1, grid.rowOf(3))
        val rows = grid.toRows()
        assertEquals(4, rows.centres.size)
        assertEquals(grid.rows[0].boxes[1].let { it.x + it.width / 2f }, rows.centres[1], 0.001f)
    }

    @Test
    fun everyRowStartsAtTheLeftEdgeWithTheBoardGapAndOnlyTheLastMayEndShortOfIt() {
        val grid = pack(listOf(1.4f, 0.71f, 0.71f, 1.4f, 0.71f, 1f, 1f, 1.4f, 0.71f))
        grid.rows.dropLast(1).forEach { row ->
            assertEquals(0f, row.boxes.first().x, 0.001f)
            row.boxes.zipWithNext { a, b -> assertEquals(20f, b.x - (a.x + a.width), 0.001f) }
        }
        assertTrue("the last row may end short", grid.rows.last().boxes.last().let { it.x + it.width } <= 400f + 0.001f)
    }

    @Test
    fun evenShapesFillTheWidthWithOneBoxSizeAndTheLastRowKeepsTheColumns() {
        val grid = pack(List(7) { 1f }, width = 3 * widthOf(1f) + 2 * 20f + 10f)
        val columns = grid.rows[0].boxes
        assertEquals(3, columns.size)
        assertEquals(3 * widthOf(1f) + 2 * 20f + 10f, columns.last().let { it.x + it.width }, 0.001f)
        grid.rows.forEach { row -> row.boxes.forEachIndexed { i, b -> assertEquals(columns[i].x, b.x, 0.001f); assertEquals(columns[0].width, b.width, 0.001f) } }
        assertEquals(1, grid.rows[2].boxes.size)
    }

    @Test
    fun evenShapesFillTheWidthWhateverItIsAndTheBoardGapsStay() {
        // The requirement changed: a one-shape grid no longer keeps natural widths and a gap on the right when filling would gain
        // much; the columns always span the width exactly and the height follows the width.
        val width = 3 * widthOf(1f) + 2 * 20f + 60f
        val grid = pack(List(7) { 1f }, width = width)
        val columns = grid.rows[0].boxes
        assertEquals(0f, columns[0].x, 0.001f)
        assertEquals(width, columns.last().let { it.x + it.width }, 0.001f)
        columns.zipWithNext { a, b -> assertEquals("board gap", 20f, b.x - (a.x + a.width), 0.001f) }
        val box = (width - 2 * 20f) / 3
        assertEquals(box, columns[0].width, 0.001f)
        grid.rows.forEach { assertEquals("one height from the shape", (box - 8f) / 1f + 12f, it.height, 0.001f) }
    }

    @Test
    fun theColumnCountIsTheStepsWhateverTheShape() {
        val tall = packRows(List(9) { 0.7f }, columns = 5, rowHeight = 100f, gap = 6f, maxWidth = 480f, chromeX = 8f, chromeY = 12f)
        assertEquals(listOf(0, 5), tall.rows.map { it.start })
        assertEquals(480f, tall.rows[0].boxes.last().let { it.x + it.width }, 0.001f)
        assertEquals((480f - 4 * 6f) / 5, tall.rows[1].boxes[0].width, 0.001f)
    }
}
