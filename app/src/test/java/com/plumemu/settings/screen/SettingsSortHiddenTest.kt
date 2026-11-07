package com.plumemu.settings.screen

import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Library › Sort games and Hidden games, by gamepad and by touch. */
internal class SettingsSortHiddenTest : SettingsModelHarness() {
    private val hidden =
        listOf(
            SettingsGame(11, "Chrono Trigger", "Super Nintendo"),
            SettingsGame(12, "Metroid Fusion", "Game Boy Advance"),
            SettingsGame(13, "Zelda", "Super Nintendo"),
        )

    private val sort: SortGamesSetting get() = actions.settings.library.sortGames

    @Test
    fun sortGamesStepsThroughTheChoicesOnOfferByEveryInput() {
        // No game has a year in this library (SettingsContent.hasYear is false): Year is not a choice.
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.SortGames)
        assertEquals(SortGamesSetting.Name, sort)
        press(Action.Enter)
        assertEquals("A goes to the next value", SortGamesSetting.LastPlayed, sort)
        press(Action.Enter)
        assertEquals(SortGamesSetting.MostPlayed, sort)
        press(Action.Enter)
        assertEquals(SortGamesSetting.RecentlyAdded, sort)
        press(Action.Enter)
        assertEquals("and wraps past the end, Year being hidden", SortGamesSetting.Name, sort)
        repeat(5) { move(Direction.Right) }
        assertEquals("right stops at the end", SortGamesSetting.RecentlyAdded, sort)
        repeat(5) { move(Direction.Left) }
        assertEquals(SortGamesSetting.Name, sort)
        val row = SettingsSection.Library.rowsIn(model.content).indexOf(SettingsOption.SortGames)
        model.onTapValue(row, actions.shown, wide)
        assertEquals("a tap on the value steps it", SortGamesSetting.LastPlayed, sort)
    }

    @Test
    fun sortGamesStepsOntoYearWhileSomeGameHasOneAndOffItAfterTheYearsAreGone() {
        model.content = SettingsContent(hasYear = true)
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.SortGames)
        repeat(4) { move(Direction.Right) }
        assertEquals("with years Year is the last choice", SortGamesSetting.Year, sort)
        move(Direction.Right)
        assertEquals("right stops on it", SortGamesSetting.Year, sort)
        model.content = SettingsContent(hasYear = false)
        assertEquals("the sort in use stays listed", SortGamesSetting.Year, sort)
        move(Direction.Left)
        assertEquals(SortGamesSetting.RecentlyAdded, sort)
        move(Direction.Right)
        assertEquals("Year is gone once it is not in use", SortGamesSetting.RecentlyAdded, sort)
        press(Action.Enter)
        assertEquals("A wraps to Name", SortGamesSetting.Name, sort)
    }

    @Test
    fun sortGamesStepsOntoRatingWhileSomeGameHasOneAndOffItAfterTheRatingsAreGone() {
        // Ratings but no years: Rating follows Recently added directly.
        model.content = SettingsContent(hasRating = true)
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.SortGames)
        repeat(4) { move(Direction.Right) }
        assertEquals("with ratings Rating is the last choice", SortGamesSetting.Rating, sort)
        move(Direction.Right)
        assertEquals("right stops on it", SortGamesSetting.Rating, sort)
        model.content = SettingsContent(hasRating = false)
        assertEquals("the sort in use stays listed", SortGamesSetting.Rating, sort)
        move(Direction.Left)
        assertEquals(SortGamesSetting.RecentlyAdded, sort)
        move(Direction.Right)
        assertEquals("Rating is gone once it is not in use", SortGamesSetting.RecentlyAdded, sort)
        press(Action.Enter)
        assertEquals("A wraps to Name", SortGamesSetting.Name, sort)
    }

    @Test
    fun sortGamesListsYearThenRatingWhenGamesHaveBoth() {
        model.content = SettingsContent(hasYear = true, hasRating = true)
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.SortGames)
        repeat(4) { move(Direction.Right) }
        assertEquals(SortGamesSetting.Year, sort)
        move(Direction.Right)
        assertEquals(SortGamesSetting.Rating, sort)
        press(Action.Enter)
        assertEquals("A wraps from Rating to Name", SortGamesSetting.Name, sort)
        val row = SettingsSection.Library.rowsIn(model.content).indexOf(SettingsOption.SortGames)
        repeat(5) { model.onTapValue(row, actions.shown, wide) }
        assertEquals("five taps step to Rating", SortGamesSetting.Rating, sort)
    }

    @Test
    fun hiddenGamesIsListedOnlyWhileSomeAreHidden() {
        assertFalse(SettingsOption.HiddenGames in SettingsSection.Library.rowsIn(SettingsContent()))
        assertEquals(SettingsOption.HiddenGames, SettingsSection.Library.rowsIn(SettingsContent(hiddenGames = hidden)).dropLast(1).last()) // Clear Recent… comes after
    }

    @Test
    fun aOnAHiddenGameShowsItAgainAndTheFocusStaysInPlace() {
        model.content = SettingsContent(hiddenGames = hidden)
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.HiddenGames)
        press(Action.Enter)
        assertEquals(PanelPage.HiddenGames, model.focus.page)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(listOf(12L), actions.unhidden)
        actions.answer(true)
        model.content = SettingsContent(hiddenGames = hidden - hidden[1])
        assertEquals("the next one moved up into the place", 1, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals(listOf(12L, 13L), actions.unhidden)
        assertEquals("at the end the cursor stays on the last", 0, model.focus.panelIndex)
        actions.answer(true)
        model.content = SettingsContent(hiddenGames = listOf(hidden[0]))
        press(Action.Enter)
        assertEquals(listOf(12L, 13L, 11L), actions.unhidden)
        assertNull("the last one closes the list", model.focus.page)
        assertEquals(SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun aTapFocusesAHiddenGameAndASecondTapShowsItAgain() {
        model.content = SettingsContent(hiddenGames = hidden)
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.HiddenGames)
        press(Action.Enter)
        model.onTapPanelItem(2, actions.shown, wide)
        assertTrue(actions.unhidden.isEmpty())
        model.onTapPanelItem(2, actions.shown, wide)
        assertEquals(listOf(13L), actions.unhidden)
    }

    @Test
    fun aFailedUnhideSaysSo() {
        model.content = SettingsContent(hiddenGames = hidden)
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.HiddenGames)
        press(Action.Enter)
        press(Action.Enter)
        actions.answer(false)
        assertEquals(SettingsOption.HiddenGames to SettingsNotice.UnhideFailed, model.work.notice)
    }
}
