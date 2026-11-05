package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** The whole-rows rule of [cutRowsToHide]: a cut row is hidden, but the list never goes blank. */
class EdgeFadeWholeRowsRuleTest {
    private class Rows(
        vararg val spans: Pair<Float, Float>,
    )

    private fun hide(
        rows: Rows,
        height: Float,
        focused: Int = -1,
        back: Boolean = true,
        forward: Boolean = true,
    ): Pair<Int, Int> {
        val packed = cutRowsToHide(rows.spans.size, height, back, forward, { rows.spans[it].first }, { rows.spans[it].second }, { it == focused })
        return hiddenTop(packed) to hiddenBottom(packed)
    }

    @Test
    fun aRowCutByTheTopOrBottomEdgeIsHidden() {
        assertEquals(0 to 2, hide(Rows(-30f to 70f, 80f to 180f, 190f to 290f), 250f))
    }

    @Test
    fun theFocusedRowIsNeverHidden() {
        assertEquals(-1 to 2, hide(Rows(-30f to 70f, 80f to 180f, 190f to 290f), 250f, focused = 0))
        assertEquals(0 to -1, hide(Rows(-30f to 70f, 80f to 180f, 190f to 290f), 250f, focused = 2))
    }

    @Test
    fun anEdgeTheListCannotScrollPastCutsNothing() {
        assertEquals(-1 to 2, hide(Rows(-30f to 70f, 80f to 180f, 190f to 290f), 250f, back = false))
        assertEquals(0 to -1, hide(Rows(-30f to 70f, 80f to 180f, 190f to 290f), 250f, forward = false))
    }

    @Test
    fun aRowTallerThanTheWindowIsNeverHidden() {
        assertEquals(-1 to -1, hide(Rows(-20f to 400f), 250f))
        assertEquals(-1 to -1, hide(Rows(-20f to 400f, 410f to 500f), 250f))
    }

    @Test
    fun twoCutRowsAndNothingElseKeepTheOneWithMostOnScreen() {
        // Window 100 tall: the top row shows 70 px and the bottom row 20; then 40 and 50.
        assertEquals(-1 to 1, hide(Rows(-30f to 70f, 80f to 180f), 100f))
        assertEquals(0 to -1, hide(Rows(-60f to 40f, 50f to 150f), 100f))
    }

    @Test
    fun aSingleCutRowStays() {
        assertEquals(-1 to -1, hide(Rows(-30f to 70f), 100f))
        assertEquals(-1 to -1, hide(Rows(60f to 160f), 100f))
    }
}
