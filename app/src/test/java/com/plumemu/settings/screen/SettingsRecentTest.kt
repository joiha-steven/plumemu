package com.plumemu.settings.screen

import com.plumemu.coredata.settings.RecentSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings › Library › Recent size and Clear Recent…: the size steps 10 / 20 / 50 from 20, and
 * Clear asks first with Cancel focused, says play time and statistics stay, then reports the count or the failure.
 */
internal class SettingsRecentTest : SettingsModelHarness() {
    private val library = SettingsSection.Library.rowsIn(SettingsContent())

    private fun openConfirm(again: Boolean = false) {
        if (!again) openSection(SettingsSection.Library)
        focusOption(SettingsOption.ClearRecent)
        press(Action.Enter)
        assertEquals(PanelPage.Confirm, model.focus.page)
    }

    @Test
    fun theTwoRowsAreLastInThatOrder() {
        assertEquals(listOf(SettingsOption.RecentSize, SettingsOption.ClearRecent), library.takeLast(2))
        val everyRow = SettingsContent(hiddenGames = listOf(SettingsGame(1, "x", "y")), unidentifiedGames = listOf(SettingsGame(2, "x", "y")))
        assertEquals(listOf(SettingsOption.HiddenGames, SettingsOption.RecentSize, SettingsOption.ClearRecent), SettingsSection.Library.rowsIn(everyRow).takeLast(3))
        assertEquals(OptionKind.Choice, SettingsOption.RecentSize.kind)
    }

    @Test
    fun theSizeStartsAtTwentyAndStepsThroughTenTwentyFifty() {
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.RecentSize)
        assertEquals(RecentSizeSetting.Twenty, actions.settings.library.recentSize)
        move(Direction.Right)
        assertEquals(RecentSizeSetting.Fifty, actions.settings.library.recentSize)
        move(Direction.Left)
        move(Direction.Left)
        assertEquals(RecentSizeSetting.Ten, actions.settings.library.recentSize)
    }

    @Test
    fun clearAsksFirstWithCancelFocusedAndClearsNothingYet() {
        openConfirm()
        assertEquals("Cancel has the focus", SettingsModel.CONFIRM_CANCEL, model.focus.panelIndex)
        assertEquals(0, actions.recentClears)
    }

    @Test
    fun cancelAndBackDoNothing() {
        openConfirm()
        press(Action.Enter)
        assertNull(model.focus.page)
        openConfirm(again = true)
        press(Action.Back)
        assertEquals(0, actions.recentClears)
        assertNull(model.work.notice)
    }

    @Test
    fun clearRunsOnceAndSaysHowManyLeft() {
        openConfirm()
        move(Direction.Right)
        press(Action.Enter)
        assertEquals(1, actions.recentClears)
        assertEquals(SettingsOption.ClearRecent to SettingsNotice.RecentCleared, model.work.notice)
        assertEquals(3, model.work.noticeCount)
    }

    @Test
    fun anEmptyRecentSaysSo() {
        actions.recentCleared = 0
        openConfirm()
        move(Direction.Right)
        press(Action.Enter)
        assertEquals(SettingsOption.ClearRecent to SettingsNotice.RecentAlreadyEmpty, model.work.notice)
    }

    @Test
    fun aFailedClearIsSaidAndNoCountShows() {
        actions.recentCleared = null
        openConfirm()
        move(Direction.Right)
        press(Action.Enter)
        assertEquals(SettingsOption.ClearRecent to SettingsNotice.RecentClearFailed, model.work.notice)
    }

    @Test
    fun reopeningTheConfirmForgetsTheLastCount() {
        openConfirm()
        move(Direction.Right)
        press(Action.Enter)
        assertTrue(model.work.notice != null)
        openConfirm(again = true)
        assertNull("the old count is not the answer to this ask", model.work.notice)
    }

    @Test
    fun clearByTouchNeedsTheSecondTapOnTheButton() {
        openSection(SettingsSection.Library)
        val row = library.indexOf(SettingsOption.ClearRecent)
        model.onTapOption(row, actions.settings, wide)
        model.onTapOption(row, actions.settings, wide)
        assertEquals(PanelPage.Confirm, model.focus.page)
        model.onTapPanelItem(SettingsModel.CONFIRM_RESET, actions.settings, wide)
        assertEquals("the first tap only focuses Clear", 0, actions.recentClears)
        model.onTapPanelItem(SettingsModel.CONFIRM_RESET, actions.settings, wide)
        assertEquals(1, actions.recentClears)
    }
}
