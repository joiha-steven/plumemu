package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Settings › Statistics: which rows show for which figures, and the page by gamepad and by touch. */
internal class SettingsStatisticsTest : SettingsModelHarness() {
    private fun rowsOf(figures: PlayStatistics) = statisticRowsOf(StatisticsState.Read(figures))

    @Test
    fun aPlayedLibraryShowsTheTotalTheTopGamesTheSystemAndTheLibraryLines() {
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
            listOf(StatisticRow.NothingPlayed, StatisticRow.Played(0, 412), StatisticRow.BoxArt(301, 412)),
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
        assertEquals(StatisticRow.Played(38, 412), notSetUp[notSetUp.size - 1])
    }

    @Test
    fun aLibraryOfOnlyShortcutGamesHasNoBoxArtLine() {
        // Pinned shortcuts are never asked for box art, so their library has nothing to cover: no "0 of 0".
        val shortcutsOnly = rowsOf(StatisticsShots.played.copy(gamesWithBoxArt = 0, boxArtGames = 0))
        assertEquals(0, shortcutsOnly.count { it is StatisticRow.BoxArt })
        assertEquals(StatisticRow.Played(38, 412), shortcutsOnly[shortcutsOnly.size - 1])
    }

    @Test
    fun fewerThanThreePlayedGamesShowOnlyThoseAndNoSystemWhenNoneCounts() {
        val one = StatisticsShots.played.topGames.take(1)
        val rows = rowsOf(StatisticsShots.played.copy(topGames = one, topSystems = emptyList()))
        assertEquals(1, rows.count { it is StatisticRow.Game })
        assertEquals(0, rows.count { it is StatisticRow.System })
    }

    @Test
    fun anEmptyLibraryIsOneLineAndAFailedReadSaysSo() {
        assertEquals(listOf(StatisticRow.NothingPlayed, StatisticRow.NoGames), rowsOf(PlayStatistics()))
        assertEquals(listOf(StatisticRow.Unreadable), statisticRowsOf(StatisticsState.Failed))
        assertEquals("nothing until the figures arrive: no row moves when they land", emptyList<StatisticRow>(), statisticRowsOf(StatisticsState.NotRead))
    }

    @Test
    fun theSectionSitsBetweenControlsAndAccountsAndHasNoStopsUntilTheFiguresArrive() {
        val sections = SettingsSection.entries
        assertEquals(sections.indexOf(SettingsSection.Controls) + 1, sections.indexOf(SettingsSection.Statistics))
        assertEquals(sections.indexOf(SettingsSection.Accounts) - 1, sections.indexOf(SettingsSection.Statistics))
        assertEquals(emptyList<SettingsOption>(), SettingsSection.Statistics.rowsIn(SettingsContent()))
        assertEquals(
            "one stop per listed game, then the Exact play time switch",
            List(5) { SettingsOption.Statistic } + SettingsOption.ExactPlayTime,
            SettingsSection.Statistics.rowsIn(StatisticsShots.contentOf(StatisticsShots.played)),
        )
        assertEquals("the switch is the only stop of a page with no games", listOf(SettingsOption.ExactPlayTime), SettingsSection.Statistics.rowsIn(StatisticsShots.contentOf(StatisticsShots.nothingPlayed)))
        assertEquals("and of an unreadable page", listOf(SettingsOption.ExactPlayTime), SettingsSection.Statistics.rowsIn(SettingsContent(statistics = StatisticsState.Failed)))
    }

    @Test
    fun theGameStopsAreOnePerListedGame() {
        assertEquals(0, statisticStopsOf(StatisticsState.NotRead))
        assertEquals(0, statisticStopsOf(StatisticsState.Failed))
        assertEquals(0, statisticStopsOf(StatisticsState.Read(StatisticsShots.nothingPlayed)))
        assertEquals(0, statisticStopsOf(StatisticsState.Read(PlayStatistics())))
        assertEquals(5, statisticStopsOf(StatisticsState.Read(StatisticsShots.played)))
        assertEquals("games without a play are never listed", 0, statisticStopsOf(StatisticsState.Read(StatisticsShots.nothingPlayed.copy(topGames = StatisticsShots.played.topGames))))
    }

    @Test
    fun gameAtIsTheGameOfTheStopAndNullForThePageOrOutOfRange() {
        val read = StatisticsState.Read(StatisticsShots.played)
        assertEquals(StatisticsShots.played.topGames[0], read.gameAt(0))
        assertEquals(StatisticsShots.played.topGames[4], read.gameAt(4))
        assertNull(read.gameAt(5))
        assertNull(StatisticsState.Read(StatisticsShots.nothingPlayed).gameAt(0))
        assertNull(StatisticsState.Failed.gameAt(0))
        assertNull(StatisticsState.NotRead.gameAt(0))
    }

    private val shown = mutableListOf<Pair<String, Long>>()

    private fun enter(figures: PlayStatistics = StatisticsShots.played) {
        model.content = StatisticsShots.contentOf(figures)
        model.showOnHome = { system, id -> shown += system to id }
        openSection(SettingsSection.Statistics)
    }

    @Test
    fun theGamepadWalksTheGamesAndTheCursorStopsAtTheEndsWhileThePageScrolls() {
        enter()
        assertEquals(SettingsOption.Statistic, model.focusedOption)
        assertEquals(0, model.statisticsScroll.step.count)
        move(Direction.Up)
        assertEquals("Up on the first game scrolls the page up", StatisticsStep(1, down = false), model.statisticsScroll.step)
        assertEquals(0, model.optionIndex)
        repeat(4) { move(Direction.Down) }
        assertEquals(4, model.optionIndex)
        assertEquals("walking between games scrolls nothing here", 1, model.statisticsScroll.step.count)
        move(Direction.Down)
        assertEquals("Down on the last game reaches the Exact play time switch", 5, model.optionIndex)
        assertEquals(SettingsOption.ExactPlayTime, model.focusedOption)
        assertEquals("walking on scrolls nothing", 1, model.statisticsScroll.step.count)
        move(Direction.Down)
        assertEquals("Down on the switch scrolls the page down", StatisticsStep(2, down = true), model.statisticsScroll.step)
        assertEquals("and the cursor stays on the switch", 5, model.optionIndex)
        assertEquals(0, actions.writes)
    }

    @Test
    fun upFromTheSwitchGoesBackToTheLastGame() {
        enter()
        repeat(5) { move(Direction.Down) }
        move(Direction.Up)
        assertEquals(4, model.optionIndex)
        assertEquals(SettingsOption.Statistic, model.focusedOption)
    }

    @Test
    fun withNothingPlayedTheSwitchIsTheOneStopAndUpAndDownScrollThePage() {
        enter(StatisticsShots.nothingPlayed)
        assertEquals(SettingsOption.ExactPlayTime, model.focusedOption)
        move(Direction.Down)
        move(Direction.Up)
        assertEquals(StatisticsStep(2, down = false), model.statisticsScroll.step)
        assertEquals(0, model.optionIndex)
    }

    private fun enterOnTheSwitch(
        access: Boolean,
        figures: PlayStatistics = StatisticsShots.nothingPlayed,
    ) {
        enter(figures)
        model.content = model.content.copy(hasUsageAccess = access)
    }

    @Test
    fun aSwitchesExactPlayTimeOnWhenAccessIsGrantedAndOffAgain() {
        enterOnTheSwitch(access = true)
        press(Action.Enter)
        assertEquals(true, actions.settings.advanced.exactPlayTime)
        assertEquals("access is there: Android's screen stays shut", 0, actions.usageAccessOpened)
        press(Action.Enter)
        assertEquals(false, actions.settings.advanced.exactPlayTime)
    }

    @Test
    fun switchingOnWithoutAccessOpensAndroidsScreenAndKeepsItOn() {
        enterOnTheSwitch(access = false)
        press(Action.Enter)
        assertEquals(1, actions.usageAccessOpened)
        assertEquals(true, actions.settings.advanced.exactPlayTime)
    }

    @Test
    fun onWithoutAccessATurnsItOffAndOpensNothing() {
        enterOnTheSwitch(access = false)
        press(Action.Enter)
        assertEquals(true, actions.settings.advanced.exactPlayTime)
        press(Action.Enter)
        assertEquals(1, actions.usageAccessOpened)
        assertEquals(false, actions.settings.advanced.exactPlayTime)
    }

    @Test
    fun onWithoutAccessAndNoAndroidScreenATurnsItOff() {
        actions.canOpenUsageAccess = false
        actions.settings = actions.settings.copy(advanced = actions.settings.advanced.copy(exactPlayTime = true))
        enterOnTheSwitch(access = false)
        press(Action.Enter)
        assertEquals(false, actions.settings.advanced.exactPlayTime)
        assertEquals(0, actions.usageAccessOpened)
    }

    @Test
    fun withNoUsageAccessScreenTheSwitchStaysOffWithAMessage() {
        actions.canOpenUsageAccess = false
        enterOnTheSwitch(access = false)
        press(Action.Enter)
        assertEquals(1, actions.usageAccessOpened)
        assertEquals(false, actions.settings.advanced.exactPlayTime)
        assertEquals(0, actions.writes)
        assertEquals(SettingsOption.ExactPlayTime to SettingsNotice.UsageAccessUnavailable, model.work.notice)
    }

    @Test
    fun rightOnTheSwitchOpensNoPanelOnEitherWidth() {
        enterOnTheSwitch(access = true)
        move(Direction.Right)
        move(Direction.Right, narrow)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
    }

    @Test
    fun aOnAGameShowsItOnHomeOnWideAndNarrowAndStaysOnThePage() {
        enter()
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(listOf("snes" to 2L), shown)
        assertEquals(SettingsColumn.Options, model.focus.column)
        move(Direction.Down)
        press(Action.Enter, narrow)
        assertEquals(listOf("snes" to 2L, "gba" to 3L), shown)
        assertNull("no panel opens", model.focus.page)
        assertEquals(0, actions.writes)
    }

    @Test
    fun rightOpensNothingOnEitherWidthAndBGoesBackToTheSections() {
        enter()
        move(Direction.Right)
        move(Direction.Right, narrow)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
        press(Action.Back)
        assertEquals(SettingsColumn.Sections, model.focus.column)
        assertEquals(SettingsSection.Statistics, model.focus.section)
        assertEquals(0, actions.closed)
    }

    @Test
    fun l1AndR1ReachStatisticsOnceItsFiguresAreThereAndSkipItBefore() {
        openSection(SettingsSection.Controls)
        press(Action.NextSystem)
        assertEquals("no stops yet: skipped", SettingsSection.Accounts, model.focus.section)
        model.content = StatisticsShots.contentOf(StatisticsShots.nothingPlayed)
        press(Action.PreviousSystem)
        assertEquals(SettingsSection.Statistics, model.focus.section)
        assertEquals(SettingsColumn.Options, model.focus.column)
        press(Action.PreviousSystem)
        assertEquals(SettingsSection.Controls, model.focus.section)
    }

    @Test
    fun aSingleTapOnAGameShowsItOnHomeAndTheValueTapDoesToo() {
        enter()
        model.onTapSection(SettingsSection.Statistics)
        model.onTapOption(2, actions.shown, wide)
        assertEquals("one tap is enough", listOf("gba" to 3L), shown)
        assertEquals(2, model.optionIndex)
        model.onTapValue(0, actions.shown, wide)
        assertEquals(listOf("gba" to 3L, "snes" to 1L), shown)
        assertEquals(0, actions.writes)
    }

    @Test
    fun aTapOnTheSwitchTogglesItAndShowsNoGame() {
        enterOnTheSwitch(access = true, StatisticsShots.nothingPlayed)
        model.onTapOption(0, actions.shown, wide)
        assertEquals(emptyList<Pair<String, Long>>(), shown)
        assertEquals(true, actions.settings.advanced.exactPlayTime)
    }
}
