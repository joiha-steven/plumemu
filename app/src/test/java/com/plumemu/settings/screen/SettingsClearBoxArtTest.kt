package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.graph.ScrapeCounts
import com.plumemu.graph.ScrapeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Library › ScreenScraper › Clear box art… (audit item 21): it asks first with Cancel focused, Cancel
 * or Back keeps the pictures, the confirm clears them, a failure is said; gamepad and touch.
 */
internal class SettingsClearBoxArtTest : SettingsModelHarness() {
    private fun openPanel() {
        model.content =
            model.content.copy(
                account = AccountStatus.SignedIn("plumtester"),
                boxArt = ScrapeStatus.Idle(ScrapeCounts(300, 2_054, 0)),
                boxArtBytes = 312L * 1_000_000,
            )
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.ScraperAccount)
        press(Action.Enter)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
    }

    private fun index() = accountPageItems(model.content).indexOf(AccountPageItem.ClearBoxArt)

    private fun openConfirm() {
        openPanel()
        repeat(index()) { press(Action.Move(Direction.Down)) }
        press(Action.Enter)
        assertEquals(PanelPage.ScraperClearConfirm, model.focus.page)
    }

    @Test
    fun clearAsksFirstWithCancelFocusedAndClearsNothingYet() {
        openConfirm()
        assertEquals("Cancel has the focus", ClearConfirmItem.Cancel.ordinal, model.focus.panelIndex)
        assertTrue("nothing was cleared by opening it", actions.boxArtCalls.none { it == "clear" })
    }

    @Test
    fun cancelAndBackKeepTheImagesAndReturnToTheButton() {
        openConfirm()
        press(Action.Enter)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        assertEquals(index(), model.focus.panelIndex)
        press(Action.Enter)
        press(Action.Back)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        assertTrue(actions.boxArtCalls.none { it == "clear" })
    }

    @Test
    fun confirmClearsOnceAndReturnsToTheButton() {
        openConfirm()
        press(Action.Move(Direction.Up))
        assertEquals(ClearConfirmItem.Clear.ordinal, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals(listOf("clear"), actions.boxArtCalls)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        assertEquals(index(), model.focus.panelIndex)
    }

    @Test
    fun confirmByTouch() {
        openPanel()
        model.onTapPanelItem(index(), actions.shown, wide)
        model.onTapPanelItem(index(), actions.shown, wide)
        assertEquals(PanelPage.ScraperClearConfirm, model.focus.page)
        model.onTapPanelItem(ClearConfirmItem.Clear.ordinal, actions.shown, wide)
        model.onTapPanelItem(ClearConfirmItem.Clear.ordinal, actions.shown, wide)
        assertEquals(listOf("clear"), actions.boxArtCalls)
    }

    @Test
    fun aFailedClearIsSaid() {
        actions.boxArtWorks = false
        openConfirm()
        press(Action.Move(Direction.Up))
        press(Action.Enter)
        assertEquals(SettingsOption.ScraperAccount to SettingsNotice.BoxArtClearFailed, model.work.notice)
    }

    private fun refusedWith(result: com.plumemu.graph.BoxArtClearResult, notice: SettingsNotice) {
        actions.clearResult = result
        openConfirm()
        press(Action.Move(Direction.Up))
        press(Action.Enter)
        assertEquals(SettingsOption.ScraperAccount to notice, model.work.notice)
    }

    @Test
    fun aClearRefusedForABackupOrRestoreSaysSo() = refusedWith(com.plumemu.graph.BoxArtClearResult.InUse, SettingsNotice.BoxArtClearInUse)

    @Test
    fun aClearWhoseDownloadDidNotStopSaysSo() = refusedWith(com.plumemu.graph.BoxArtClearResult.RunStillGoing, SettingsNotice.BoxArtClearRunGoing)
}
