package com.plumemu.coreinput.strip

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Column
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.WidthClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The strip's stops at the reference sizes, built from the real core-ui tokens and checked against
 * the board (RP5-2-games moves the strip by `peek − 224`) and docs/design/rules.md › The strip.
 * Positions are on screen: the strip starts at the screen edge with the 24 dp margin inside it.
 */
class StripGeometryTest {
    private fun geometryOf(layout: PlumLayout) =
        StripGeometry(
            isNarrow = layout.widthClass == WidthClass.Narrow,
            leadingMargin = PlumDimens.stripMarginSide,
            systemsWidth = PlumDimens.systemsColumnWidth,
            gamesListWidth = layout.gamesListWidth,
            columnGap = PlumDimens.columnGap,
            peek = layout.peek,
            detailsStopListPeek = PlumDimens.detailsStopListPeek,
        )

    /** Where each column lands on screen when the strip rests at [stop]. */
    private class Screen(
        geometry: StripGeometry,
        stop: Column,
    ) {
        private val offset = geometry.offsetOf(stop)
        private val margin = PlumDimens.stripMarginSide
        val systemsRight: Dp = margin + geometry.systemsWidth + offset
        val listLeft: Dp = margin + geometry.systemsWidth + geometry.columnGap + offset
        val listRight: Dp = listLeft + geometry.gamesListWidth
        val detailsLeft: Dp = listRight + geometry.columnGap
    }

    private val rp5 = PlumLayout(width = 768.dp, height = 432.dp)
    private val nova = PlumLayout(width = 576.dp, height = 432.dp)

    private val content = StripContent(listOf(SystemEntry("snes", 248)), hasBoxArt = true)
    private val onGames = StripFocus(column = Column.Games, systemId = "snes")

    @Test
    fun wideSystemsFocusedRestsAtTheStart() {
        val g = geometryOf(rp5)
        assertEquals(0.dp, g.offsetOf(Column.Systems))
        assertEquals(248.dp, Screen(g, Column.Systems).listLeft)
    }

    @Test
    fun wideGamesFocusedShowsThe48DpPeekTheListAndTheDetails() {
        val g = geometryOf(rp5)
        assertEquals("the board's peek − 224", (-176).dp, g.offsetOf(Column.Games))
        val screen = Screen(g, Column.Games)
        assertEquals("the system column's last 48 dp, from the screen edge", 48.dp, screen.systemsRight)
        assertEquals(72.dp, screen.listLeft)
        assertEquals(400.dp, g.gamesListWidth)
        assertEquals("details 248 end at the right margin", 768.dp - PlumDimens.stripMarginSide, screen.detailsLeft + PlumDimens.detailsColumnWidth)
        assertEquals(listOf(Column.Systems, Column.Games), g.stops(onGames, content))
    }

    @Test
    fun narrowGamesFocusedKeepsTheList400AndOnlyTheCoverPeeks() {
        val g = geometryOf(nova)
        assertEquals((-176).dp, g.offsetOf(Column.Games))
        val screen = Screen(g, Column.Games)
        assertEquals(48.dp, screen.systemsRight)
        assertEquals(72.dp to 472.dp, screen.listLeft to screen.listRight)
        assertEquals("the cover peeks in by 80 dp (Nova-2-games)", 80.dp, 576.dp - screen.detailsLeft)
    }

    @Test
    fun narrowDetailsStopShows56DpOfTheListAndGivesDetailsWidthMinus104() {
        val g = geometryOf(nova)
        assertEquals((-592).dp, g.offsetOf(Column.Details))
        val screen = Screen(g, Column.Details)
        assertEquals("56 dp of the list from the screen edge", 56.dp, screen.listRight)
        assertEquals(80.dp, screen.detailsLeft)
        assertEquals("details width − 104 up to the right margin", 472.dp, 576.dp - screen.detailsLeft - PlumDimens.stripMarginSide)
        assertEquals(nova.detailsStopWidth, 576.dp - screen.detailsLeft - PlumDimens.stripMarginSide)
        assertEquals(Column.entries, g.stops(onGames, content))
    }

    @Test
    fun thePeekSettingMovesTheGamesStop() {
        assertEquals((-104).dp, geometryOf(rp5.copy(peek = PlumDimens.peekWidthMax)).offsetOf(Column.Games))
        assertEquals((-224).dp, geometryOf(rp5.copy(peek = PlumDimens.peekWidthMin)).offsetOf(Column.Games))
        assertEquals(120.dp, Screen(geometryOf(rp5.copy(peek = PlumDimens.peekWidthMax)), Column.Games).systemsRight)
        assertEquals("peek 0 hides the column", 0.dp, Screen(geometryOf(rp5.copy(peek = PlumDimens.peekWidthMin)), Column.Games).systemsRight)
    }

    @Test
    fun stopsFollowContent() {
        val g = geometryOf(nova)
        assertEquals(listOf(Column.Systems, Column.Games), g.stops(onGames, content.copy(hasBoxArt = false)))
        val empty = StripContent(listOf(SystemEntry("recent", 0)), hasBoxArt = true)
        assertEquals(listOf(Column.Systems), g.stops(StripFocus(), empty))
    }

    @Test
    fun aSlowReleaseSettlesOnTheNearestStop() {
        val stops = listOf(Column.Systems to 0f, Column.Games to -176f, Column.Details to -592f)
        assertEquals(Column.Systems, settleStop(-70f, 0f, stops, flingVelocity = 400f))
        assertEquals(Column.Games, settleStop(-100f, 100f, stops, flingVelocity = 400f))
        assertEquals(Column.Details, settleStop(-400f, -100f, stops, flingVelocity = 400f))
    }

    @Test
    fun aFlingGoesToTheNextStopInItsDirection() {
        val stops = listOf(Column.Systems to 0f, Column.Games to -176f, Column.Details to -592f)
        assertEquals(Column.Games, settleStop(-20f, -900f, stops, flingVelocity = 400f))
        assertEquals(Column.Details, settleStop(-180f, -900f, stops, flingVelocity = 400f))
        assertEquals(Column.Games, settleStop(-500f, 900f, stops, flingVelocity = 400f))
        assertEquals(Column.Systems, settleStop(-140f, 900f, stops, flingVelocity = 400f))
        assertEquals("past the last stop stays there", Column.Details, settleStop(-592f, -900f, stops, flingVelocity = 400f))
        assertEquals(Column.Systems, settleStop(0f, 900f, stops, flingVelocity = 400f))
    }
}
