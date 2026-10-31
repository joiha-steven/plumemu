package com.plumemu.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which lines the statistics card keeps: a line that does not fit is skipped, the lower ones are still tried. */
class SecondScreenStatisticsKeptPartsTest {
    private val top = StatisticsPart.Top
    private val total = StatisticsPart.Total
    private val game1 = StatisticsPart.Game(1)
    private val game2 = StatisticsPart.Game(2)
    private val game3 = StatisticsPart.Game(3)
    private val system = StatisticsPart.System
    private val played = StatisticsPart.Played
    private val boxArt = StatisticsPart.BoxArt

    /** A gap of 10 between groups, 2 inside one; heights as given. */
    private fun kept(
        maxHeight: Int?,
        vararg parts: Pair<StatisticsPart, Int>,
    ) = keptParts(parts.toList(), maxHeight) { before, part -> if (before.group == part.group) 2 else 10 }

    @Test
    fun everythingShowsWhenItFits() {
        val all = arrayOf(top to 10, total to 20, game1 to 10, game2 to 10, game3 to 10, system to 10, played to 10, boxArt to 10)
        assertEquals(all.map { it.first }, kept(1_000, *all))
        assertEquals(all.map { it.first }, kept(null, *all))
    }

    @Test
    fun aTwoLineSystemThatDoesNotFitIsSkippedAndGamesPlayedStillShows() {
        // top 10 + total 20 + gap 10 + game1 10 = 50; the system (40, gap 10) would make 100; played (10, gap 10) makes 70.
        val result = kept(80, top to 10, total to 20, game1 to 10, system to 40, played to 10)
        assertEquals(listOf(top, total, game1, played), result)
    }

    @Test
    fun aLowerLineNeverTakesTheRoomOfAHigherOne() {
        // 52 with game1; game2 makes 64, box art alone would make 72, both 84: with 74 the higher one wins.
        val result = kept(74, top to 10, total to 20, game1 to 10, game2 to 10, boxArt to 10)
        assertEquals(listOf(top, total, game1, game2), result)
    }

    @Test
    fun theTopLineAndTheTotalStayEvenWhenNothingFits() {
        assertEquals(listOf(top, total), kept(5, top to 10, total to 20, game1 to 10, played to 10))
    }

    @Test
    fun shownLinesKeepTheirPlaceOnTheCard() {
        assertEquals(listOf(top, total, game1, game3, boxArt), kept(1_000, top to 1, total to 1, game1 to 1, game3 to 1, boxArt to 1))
        // game2 skipped because tall; game3 and box art still shown, in card order.
        assertEquals(listOf(top, total, game1, game3, boxArt), kept(80, top to 10, total to 10, game1 to 10, game2 to 100, game3 to 10, boxArt to 10))
    }
}
