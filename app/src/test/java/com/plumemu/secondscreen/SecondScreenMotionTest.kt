package com.plumemu.secondscreen

import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreui.theme.SwapStep
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** How each second-screen card comes in: from the side the top screen's cursor went, with depth for Settings and games. */
class SecondScreenMotionTest {
    @Before
    fun forget() = SecondScreenMotion.reset()

    private fun card(n: Int) = SecondScreenContent.Game("Game $n", "Super Nintendo", gameId = n.toLong())

    private fun at(
        card: SecondScreenContent,
        column: Column,
        system: Int,
        row: Int = 0,
    ) = SecondScreenMotion.noteOffered(card, StripFocus(column, system, "s$system", mapOf("s$system" to row)), 12)

    @Test
    fun theCursorDownAGameListBringsTheNextCardFromBelowAndUpFromAbove() {
        at(card(1), Column.Games, 3, 4)
        at(card(2), Column.Games, 3, 5)
        assertEquals(SwapStep(y = 1), SecondScreenMotion.stepBetween(card(1), card(2)))
        assertEquals(SwapStep(y = -1), SecondScreenMotion.stepBetween(card(2), card(1)))
    }

    @Test
    fun intoTheGamesTheCardComesFromTheRightAndTheSystemsWrapTheShortWay() {
        val system = card(10)
        at(system, Column.Systems, 11)
        at(card(11), Column.Games, 11)
        at(card(12), Column.Systems, 0)
        assertEquals(SwapStep(x = 1), SecondScreenMotion.stepBetween(system, card(11)))
        assertEquals("the last system's next is the first, from below", SwapStep(y = 1), SecondScreenMotion.stepBetween(system, card(12)))
    }

    @Test
    fun settingsAndGamesComeWithDepth() {
        val statistics = SecondScreenContent.Statistics(com.plumemu.settings.screen.PlayStatistics())
        assertEquals(SwapStep(depth = -1), SecondScreenMotion.stepBetween(card(1), statistics))
        assertEquals(SwapStep(depth = 1), SecondScreenMotion.stepBetween(statistics, card(1)))
        assertEquals(SwapStep(depth = -1), SecondScreenMotion.stepBetween(WindowMode.Picking, WindowMode.InGameClock))
        assertEquals(SwapStep(depth = 1), SecondScreenMotion.stepBetween(WindowMode.InGameBlack, WindowMode.Picking))
        assertEquals(null, SecondScreenMotion.stepBetween(WindowMode.Picking, WindowMode.Picking))
    }

    @Test
    fun aCardNotOfferedByTheCursorJustFades() {
        assertEquals(null, SecondScreenMotion.stepBetween(SecondScreenContent.Calm, card(99)))
    }

    @Test
    fun aHeldKeyThatOffersManyCardsStillKnowsWhereTheShownOneWas() {
        at(card(1), Column.Games, 3, 4)
        SecondScreenMotion.showing(card(1))
        for (n in 2..40) at(card(n), Column.Games, 3, 3 + n)
        assertEquals("from below, after 39 cards offered on the way", SwapStep(y = 1), SecondScreenMotion.stepBetween(card(1), card(40)))
    }

    @Test
    fun onTheTopScreenAStepRightComesFromTheRightAndAStepDownFromBelow() {
        val cell = Rect(100f, 100f, 300f, 400f)
        assertEquals("a grid's next cell", SwapStep(x = 1), SecondScreenMotion.stepOnScreen(cell, cell.translate(220f, 0f)))
        assertEquals("a grid's cell before", SwapStep(x = -1), SecondScreenMotion.stepOnScreen(cell, cell.translate(-220f, 0f)))
        assertEquals("the row below", SwapStep(y = 1), SecondScreenMotion.stepOnScreen(cell, cell.translate(0f, 320f)))
        assertEquals("the list scrolled under a cursor that stayed: the rows decide", null, SecondScreenMotion.stepOnScreen(cell, cell.translate(0f, 2f)))
        assertEquals("nothing drawn: the rows decide", null, SecondScreenMotion.stepOnScreen(null, cell))
    }
}
