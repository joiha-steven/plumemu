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
    fun sortGamesStepsThroughNameLastPlayedAndYearByEveryInput() {
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.SortGames)
        assertEquals(SortGamesSetting.Name, sort)
        press(Action.Enter)
        assertEquals("A goes to the next value", SortGamesSetting.LastPlayed, sort)
        press(Action.Enter)
        assertEquals(SortGamesSetting.Year, sort)
        press(Action.Enter)
        assertEquals("and wraps", SortGamesSetting.Name, sort)
        move(Direction.Right)
        move(Direction.Right)
        assertEquals(SortGamesSetting.Year, sort)
        move(Direction.Right)
        assertEquals("right stops at the end", SortGamesSetting.Year, sort)
        move(Direction.Left)
        move(Direction.Left)
        assertEquals(SortGamesSetting.Name, sort)
        val row = SettingsSection.Library.rowsIn(model.content).indexOf(SettingsOption.SortGames)
        model.onTapValue(row, actions.shown, wide)
        assertEquals("a tap on the value steps it", SortGamesSetting.LastPlayed, sort)
    }

    @Test
    fun hiddenGamesIsListedOnlyWhileSomeAreHidden() {
        assertFalse(SettingsOption.HiddenGames in SettingsSection.Library.rowsIn(SettingsContent()))
        assertEquals(SettingsOption.HiddenGames, SettingsSection.Library.rowsIn(SettingsContent(hiddenGames = hidden)).dropLast(2).last()) // the two Recent rows come after
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
