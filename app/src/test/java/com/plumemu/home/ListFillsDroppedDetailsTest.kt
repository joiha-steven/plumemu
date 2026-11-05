package com.plumemu.home

import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Column
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the details column is left out (under its 140 dp of room), the games list reaches the right margin: no blank gap. */
class ListFillsDroppedDetailsTest {
    private fun listRightAt(
        layout: PlumLayout,
        column: Column,
    ) = with(homeStripGeometry(layout, hasBoxArt = true)) {
        offsetOf(column) + leadingMargin + systemsWidth + columnGap + homeListWidth(layout, hasBoxArt = true)
    }

    @Test
    fun listEndsAtRightMarginWhenDetailsDropped() {
        val layout = PlumLayout(576.dp, 432.dp)
        assertEquals(0.dp, homeDetailsFit(layout, hasBoxArt = true, showsGrid = false, column = Column.Games))
        assertEquals((layout.width - PlumDimens.stripMarginSide).value, listRightAt(layout, Column.Games).value, 0.01f)
    }

    @Test
    fun listKeepsItsWidthWhereDetailsFit() {
        val wide = PlumLayout(768.dp, 432.dp)
        assertEquals(wide.gamesListWidth, homeListWidth(wide, hasBoxArt = true))
        val narrowWithRoom = PlumLayout(640.dp, 480.dp)
        assertEquals(narrowWithRoom.gamesListWidth, homeListWidth(narrowWithRoom, hasBoxArt = true))
    }
}
