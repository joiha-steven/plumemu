package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.game.GameRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Settings › Statistics: which rows show for which figures, and the page by gamepad and by touch. */
internal class SettingsStatisticsTest : SettingsModelHarness() {
    private fun rowsOf(figures: PlayStatistics) = statisticRowsOf(StatisticsState.Read(figures))

    @Test
    fun aPlayedLibraryShowsTheTotalTheTopGamesTheSystemTheLibraryLinesAndTheVersion() {
        val figures = StatisticsShots.played
        assertEquals(
            listOf(
                StatisticRow.Total(figures.totalSeconds),
                StatisticRow.ThisWeek(2 * 3_600L + 10 * 60),
                StatisticRow.Game(1, figures.topGames[0], showsCover = true),
                StatisticRow.Game(2, figures.topGames[1], showsCover = true),
                StatisticRow.Game(3, figures.topGames[2], showsCover = true),
                StatisticRow.System(figures.topSystem!!),
                StatisticRow.Played(38, 412),
                StatisticRow.BoxArt(301, 412),
                StatisticRow.Version,
            ),
            rowsOf(figures),
        )
    }

    @Test
    fun thisWeekIsHiddenUntilASessionIsRecordedAndSaysSoWhenTheWeekIsEmpty() {
        val none = rowsOf(StatisticsShots.played.copy(hasSessions = false, thisWeekSeconds = 0))
        assertEquals("updated, not played since: no 0 line", emptyList<StatisticRow>(), none.filterIsInstance<StatisticRow.ThisWeek>())
        assertEquals(listOf(StatisticRow.ThisWeek(0)), rowsOf(StatisticsShots.played.copy(thisWeekSeconds = 0)).filterIsInstance<StatisticRow.ThisWeek>())
        assertEquals("right after Total", StatisticRow.ThisWeek(5), rowsOf(StatisticsShots.played.copy(thisWeekSeconds = 5))[1])
    }

    @Test
    fun nothingPlayedIsOneCalmLineAndTheLibraryLinesStay() {
        assertEquals(
            listOf(StatisticRow.NothingPlayed, StatisticRow.Played(0, 412), StatisticRow.BoxArt(301, 412), StatisticRow.Version),
            rowsOf(StatisticsShots.nothingPlayed),
        )
    }

    @Test
    fun aLibraryWithNoBoxArtSaysZeroAndItsGamesHaveNoCoverSlot() {
        val setUpButEmpty = rowsOf(StatisticsShots.played.copy(gamesWithBoxArt = 0, topGames = StatisticsShots.played.topGames.map { it.copy(cover = null) }))
        assertEquals("signed in, nothing saved yet: 0 of 412", StatisticRow.BoxArt(0, 412), setUpButEmpty.single { it is StatisticRow.BoxArt })
        assertEquals("the slots stay, with stand-in tiles", listOf(true, true, true), setUpButEmpty.filterIsInstance<StatisticRow.Game>().map { it.showsCover })
        val notSetUp = rowsOf(StatisticsShots.played.copy(gamesWithBoxArt = 0, hasBoxArt = false, topGames = StatisticsShots.played.topGames.map { it.copy(cover = null) }))
        assertEquals("no box-art line without box art set up", 0, notSetUp.count { it is StatisticRow.BoxArt })
        assertEquals("and no cover slot", listOf(false, false, false), notSetUp.filterIsInstance<StatisticRow.Game>().map { it.showsCover })
        assertEquals(StatisticRow.Played(38, 412), notSetUp[notSetUp.size - 2])
    }

    @Test
    fun aLibraryOfOnlyShortcutGamesHasNoBoxArtLine() {
        // Pinned shortcuts are never asked for box art, so their library has nothing to cover: no "0 of 0".
        val shortcutsOnly = rowsOf(StatisticsShots.played.copy(gamesWithBoxArt = 0, boxArtGames = 0))
        assertEquals(0, shortcutsOnly.count { it is StatisticRow.BoxArt })
        assertEquals(StatisticRow.Played(38, 412), shortcutsOnly[shortcutsOnly.size - 2])
    }

    @Test
    fun fewerThanThreePlayedGamesShowOnlyThoseAndNoSystemWhenNoneCounts() {
        val one = StatisticsShots.played.topGames.take(1)
        val rows = rowsOf(StatisticsShots.played.copy(topGames = one, topSystem = null))
        assertEquals(1, rows.count { it is StatisticRow.Game })
        assertEquals(0, rows.count { it is StatisticRow.System })
    }

    @Test
    fun anEmptyLibraryIsOneLineAndAFailedReadSaysSo() {
        assertEquals(listOf(StatisticRow.NothingPlayed, StatisticRow.NoGames, StatisticRow.Version), rowsOf(PlayStatistics()))
        assertEquals(listOf(StatisticRow.Unreadable, StatisticRow.Version), statisticRowsOf(StatisticsState.Failed))
        assertEquals("nothing until the figures arrive: no row moves when they land", emptyList<StatisticRow>(), statisticRowsOf(StatisticsState.NotRead))
    }

    @Test
    fun theSectionSitsBetweenHomeScreenAndAdvancedAndHasNoRowsUntilTheFiguresArrive() {
        // Moved up from just before About.
        val sections = SettingsSection.entries
        assertEquals(sections.indexOf(SettingsSection.HomeScreen) + 1, sections.indexOf(SettingsSection.Statistics))
        assertEquals(sections.indexOf(SettingsSection.Advanced) - 1, sections.indexOf(SettingsSection.Statistics))
        assertEquals(emptyList<SettingsOption>(), SettingsSection.Statistics.rowsIn(SettingsContent()))
        assertEquals(List(9) { SettingsOption.Statistic }, SettingsSection.Statistics.rowsIn(StatisticsShots.contentOf(StatisticsShots.played)))
    }

    @Test
    fun theGamepadWalksTheRowsAndANeitherWritesNorOpensOnAWideScreenAndBGoesBack() {
        model.content = StatisticsShots.contentOf(StatisticsShots.played)
        openSection(SettingsSection.Statistics)
        assertEquals(SettingsOption.Statistic, model.focusedOption)
        repeat(10) { move(Direction.Down) }
        assertEquals("stops on the last row, the version", 8, model.optionIndex)
        move(Direction.Up)
        press(Action.Enter)
        assertEquals("A on a read-only row stays on it", SettingsColumn.Options, model.focus.column)
        move(Direction.Right)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals("nothing is written", 0, actions.writes)
        press(Action.Back)
        assertEquals(SettingsColumn.Sections, model.focus.column)
        assertEquals(SettingsSection.Statistics, model.focus.section)
        assertEquals(0, actions.closed)
    }

    @Test
    fun onANarrowScreenRightOpensTheExplanationToReadAndBComesBackToTheRow() {
        model.content = StatisticsShots.contentOf(StatisticsShots.played)
        openSection(SettingsSection.Statistics)
        move(Direction.Down, narrow)
        press(Action.Enter, narrow)
        assertEquals("A does nothing on a figure", SettingsColumn.Options, model.focus.column)
        move(Direction.Right, narrow)
        assertEquals(SettingsColumn.Panel, model.focus.column)
        assertEquals(PanelPage.ReadOnly, model.focus.page)
        press(Action.Back, narrow)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(1, model.optionIndex)
        assertEquals(0, actions.writes)
    }

    @Test
    fun l1AndR1ReachStatisticsOnceItsFiguresAreThereAndSkipItBefore() {
        openSection(SettingsSection.HomeScreen)
        press(Action.NextSystem)
        assertEquals("no rows yet: skipped", SettingsSection.Advanced, model.focus.section)
        model.content = StatisticsShots.contentOf(StatisticsShots.nothingPlayed)
        press(Action.PreviousSystem)
        assertEquals(SettingsSection.Statistics, model.focus.section)
        assertEquals(SettingsColumn.Options, model.focus.column)
        press(Action.PreviousSystem)
        assertEquals(SettingsSection.HomeScreen, model.focus.section)
    }

    @Test
    fun aTapFocusesARowAndASecondTapOrATapOnTheFigureDoesNothingMore() {
        model.content = StatisticsShots.contentOf(StatisticsShots.played)
        model.onTapSection(SettingsSection.Statistics)
        model.onTapSection(SettingsSection.Statistics)
        // Row 5 is the most-played system: a game row's second tap plays (below).
        model.onTapOption(5, actions.shown, wide)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(5, model.optionIndex)
        model.onTapOption(5, actions.shown, wide)
        model.onTapValue(5, actions.shown, wide)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
        assertEquals(0, actions.writes)
    }

    private val played = mutableListOf<GameRef>()

    private fun focusRow(index: Int) {
        model.content = StatisticsShots.contentOf(StatisticsShots.played)
        model.playGame = { played += it }
        openSection(SettingsSection.Statistics)
        repeat(index) { move(Direction.Down) }
    }

    // A most-played game row used to be read-only (A did nothing on a wide screen).
    @Test
    fun aOnAMostPlayedGameRowPlaysThatGameOnAWideScreen() {
        focusRow(3)
        assertEquals(StatisticsShots.played.topGames[1], model.focusedStatisticGame?.game)
        press(Action.Enter)
        assertEquals(listOf(GameRef("2", "The Legend of Zelda: A Link to the Past")), played)
        assertEquals("the page stays where it was", SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun aTapOnAFocusedGameRowOrItsFigurePlaysAndATapOnAnotherRowOnlyFocusesIt() {
        focusRow(0)
        model.onTapOption(2, actions.shown, wide)
        assertEquals("first tap focuses", emptyList<GameRef>(), played)
        model.onTapOption(2, actions.shown, wide)
        model.onTapValue(2, actions.shown, wide)
        assertEquals(listOf(GameRef("1", "Super Metroid"), GameRef("1", "Super Metroid")), played)
    }

    @Test
    fun everyOtherStatisticsRowStillPlaysNothing() {
        focusRow(0)
        repeat(9) {
            if (model.focusedStatisticGame == null) press(Action.Enter)
            move(Direction.Down)
        }
        assertEquals(emptyList<GameRef>(), played)
        assertEquals(0, actions.writes)
    }

    @Test
    fun onANarrowScreenAPlaysTheGameDirectlyAndOpensNoPanel() {
        focusRow(2)
        press(Action.Enter, narrow)
        assertEquals(listOf(GameRef("1", "Super Metroid")), played)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
    }

    @Test
    fun onANarrowScreenDpadRightOnAGameRowOpensNoPanel() {
        focusRow(2)
        move(Direction.Right, narrow)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
        assertEquals(emptyList<GameRef>(), played)
    }

    @Test
    fun aTapOnTheFigureOfAGameRowThatIsNotFocusedOnlyFocusesIt() {
        focusRow(0)
        model.onTapValue(2, actions.shown, wide)
        assertEquals(2, model.optionIndex)
        assertEquals("a first tap on the figure never launches", emptyList<GameRef>(), played)
        model.onTapValue(2, actions.shown, wide)
        assertEquals(listOf(GameRef("1", "Super Metroid")), played)
    }
}
