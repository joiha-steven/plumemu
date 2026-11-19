package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings › About › Reset… › Clear Recent…: it asks first with Cancel focused, says play time and statistics stay, then reports the count or the failure on its item.
 */
internal class SettingsRecentTest : SettingsModelHarness() {
    private val library = SettingsSection.Library.rowsIn(SettingsContent())

    private fun openList() {
        model.content = SettingsContent()
        openSection(SettingsSection.About)
        focusOption(SettingsOption.Reset)
        press(Action.Enter)
        assertEquals(PanelPage.Reset, model.focus.page)
    }

    private fun openConfirm(again: Boolean = false) {
        if (!again) openList()
        // Clear Recent… is the list's third item; after a confirm the cursor is already on it.
        while (model.focus.panelIndex != ResetPanelItem.Recent.ordinal) move(Direction.Down)
        press(Action.Enter)
        assertEquals(PanelPage.ResetRecentConfirm, model.focus.page)
        assertEquals(SettingsOption.ClearRecent, model.panelOption)
    }

    @Test
    fun libraryNoLongerListsClearRecent() {
        assertTrue(SettingsOption.ClearRecent !in SettingsSection.Library.rowsIn(SettingsContent()))
        val everyRow = SettingsContent(hiddenGames = listOf(SettingsGame(1, "x", "y")), unidentifiedGames = listOf(SettingsGame(2, "x", "y")))
        val rows = SettingsSection.Library.rowsIn(everyRow)
        val after = rows.indexOf(SettingsOption.FilesNotAdded) + 1
        assertEquals(listOf(SettingsOption.UnidentifiedGames, SettingsOption.HiddenGames), rows.subList(after, after + 2))
        assertEquals(ResetPanelItem.Recent.option, SettingsOption.ClearRecent)
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
        assertEquals("Cancel goes back on its item", PanelPage.Reset, model.focus.page)
        assertEquals(ResetPanelItem.Recent.ordinal, model.focus.panelIndex)
        openConfirm(again = true)
        press(Action.Back)
        assertEquals(PanelPage.Reset, model.focus.page)
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
        assertEquals("the result shows on its item", PanelPage.Reset, model.focus.page)
        assertEquals(SettingsOption.ClearRecent, model.doneOption)
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
    fun clearByTouchNeedsTheSecondTapOnTheItemAndOnTheButton() {
        openSection(SettingsSection.About)
        focusOption(SettingsOption.Reset)
        model.onTapPanelItem(ResetPanelItem.Recent.ordinal, actions.settings, wide)
        assertEquals(PanelPage.Reset, model.focus.page)
        model.onTapPanelItem(ResetPanelItem.Recent.ordinal, actions.settings, wide)
        assertEquals(PanelPage.ResetRecentConfirm, model.focus.page)
        model.onTapPanelItem(SettingsModel.CONFIRM_RESET, actions.settings, wide)
        assertEquals("the first tap only focuses Clear", 0, actions.recentClears)
        model.onTapPanelItem(SettingsModel.CONFIRM_RESET, actions.settings, wide)
        assertEquals(1, actions.recentClears)
    }

    @Test
    fun threeQuickAsOnTheItemClearNothing() {
        openList()
        repeat(2) { move(Direction.Down) }
        repeat(3) { press(Action.Enter) }
        assertEquals(0, actions.recentClears)
    }
}
