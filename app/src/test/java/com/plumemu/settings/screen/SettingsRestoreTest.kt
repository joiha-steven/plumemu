package com.plumemu.settings.screen

import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.backup.BackupProgress
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.AccountInFile
import com.plumemu.graph.RestoreProblem
import com.plumemu.graph.RestoreState
import com.plumemu.graph.RestoreSummary
import com.plumemu.graph.WayBackSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Advanced › Restore… and Undo last restore by gamepad and touch: Choose file…, Cancel while reading, the confirm opening on Cancel, Undo listed only
 * while its set exists, and the result kept until the focus leaves the row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class SettingsRestoreTest : SettingsModelHarness() {
    private val summary = RestoreSummary(BackupKind.Everything, 1_792_756_800_000L, games = 2_054, pictures = 1_234, account = AccountInFile.SignsIn)
    private val wayBack = WayBackSet(BackupKind.Everything, 1_792_843_200_000L, 412_000_000)

    private fun openRestorePanel(content: SettingsContent = SettingsContent()) {
        model.content = content
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.Restore)
        press(Action.Enter)
        assertEquals(PanelPage.Restore, model.focus.page)
    }

    @Test
    fun theRowSitsRightAfterBackUpAndUndoAfterItOnlyWhileTheSetExists() {
        val advanced = SettingsSection.Advanced
        assertFalse(SettingsOption.UndoRestore in advanced.rowsIn(SettingsContent()))
        val rows = advanced.rowsIn(SettingsContent(wayBack = wayBack))
        assertEquals(rows.indexOf(SettingsOption.Backup) + 1, rows.indexOf(SettingsOption.Restore))
        assertEquals(rows.indexOf(SettingsOption.Restore) + 1, rows.indexOf(SettingsOption.UndoRestore))
        assertEquals(rows.indexOf(SettingsOption.ResetAll) - 1, rows.indexOf(SettingsOption.UndoRestore))
    }

    @Test
    fun aOnChooseFileOpensThePickerAndWithoutOneThePanelSaysSo() {
        openRestorePanel()
        assertEquals(listOf(RestorePanelItem.ChooseFile), restorePanelItems(model.content.restore))
        press(Action.Enter)
        assertEquals(listOf("forget result", "pick"), actions.restoreCalls)
        assertEquals("the panel stays open for the reading", PanelPage.Restore, model.focus.page)

        actions.hasOpenPicker = false
        press(Action.Enter)
        assertEquals(SettingsOption.Restore to SettingsNotice.NoOpenPicker, model.work.notice)
    }

    @Test
    fun whileReadingTheOnlyButtonIsCancel() {
        openRestorePanel()
        press(Action.Enter)
        model.content = model.content.copy(restore = RestoreState.Reading(BackupProgress(12, 5_000)))
        assertEquals(listOf(RestorePanelItem.Cancel), restorePanelItems(model.content.restore))
        actions.restoreCalls.clear()
        press(Action.Enter)
        assertEquals(listOf("cancel"), actions.restoreCalls)
    }

    @Test
    fun theConfirmOpensOnCancelSoADoubleADoesNotReplaceTheLibrary() {
        openRestorePanel()
        press(Action.Enter)
        model.content = model.content.copy(restore = RestoreState.Reading())
        model.content = model.content.copy(restore = RestoreState.Confirm(summary))
        assertEquals(listOf(RestorePanelItem.Restore, RestorePanelItem.Cancel), restorePanelItems(model.content.restore))
        assertEquals("the cursor is on Cancel", 1, model.focus.panelIndex)
        actions.restoreCalls.clear()
        press(Action.Enter)
        assertEquals(listOf("cancel"), actions.restoreCalls)

        model.content = model.content.copy(restore = RestoreState.Confirm(summary))
        move(Direction.Up)
        press(Action.Enter)
        assertEquals(listOf("cancel", "confirm"), actions.restoreCalls)
        // Replacing: no buttons, nothing to press.
        model.content = model.content.copy(restore = RestoreState.Replacing(isUndo = false, withLibrary = true))
        assertTrue(restorePanelItems(model.content.restore).isEmpty())
        press(Action.Enter)
        assertEquals(listOf("cancel", "confirm"), actions.restoreCalls)
    }

    @Test
    fun aTapOnRestoreAtTheConfirmReplaces() {
        model.content = SettingsContent(restore = RestoreState.Confirm(summary))
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.Restore)
        val settings = actions.shown
        model.onTapPanelItem(0, settings, wide)
        model.onTapPanelItem(0, settings, wide)
        assertTrue(actions.restoreCalls.contains("confirm"))
    }

    @Test
    fun theUndoPanelAsksAgainBeforeItUndoesWithTheCursorOnCancel() {
        model.content = SettingsContent(wayBack = wayBack)
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.UndoRestore)
        press(Action.Enter)
        assertEquals(PanelPage.UndoRestore, model.focus.page)
        assertEquals(listOf(UndoPanelItem.Undo, UndoPanelItem.Forget), undoPanelItems(model.content, model.focus.undoAsk))
        actions.restoreCalls.clear()
        press(Action.Enter)
        assertTrue("one press only asks", actions.restoreCalls.isEmpty())
        assertEquals(UndoPanelItem.Undo, model.focus.undoAsk)
        assertEquals(listOf(UndoPanelItem.Confirm, UndoPanelItem.Cancel), undoPanelItems(model.content, model.focus.undoAsk))
        assertEquals("the cursor waits on Cancel", 1, model.focus.panelIndex)
        press(Action.Enter)
        assertTrue("Cancel does nothing", actions.restoreCalls.isEmpty())
        assertEquals(null, model.focus.undoAsk)
        press(Action.Enter)
        move(Direction.Up)
        press(Action.Enter)
        assertEquals(listOf("undo"), actions.restoreCalls)
        assertTrue("no buttons while a restore replaces", undoPanelItems(SettingsContent(wayBack = wayBack, restore = RestoreState.Replacing(isUndo = true, withLibrary = true)), null).isEmpty())
    }

    @Test
    fun forgettingTheCopyAsksAgainWithTheCursorOnCancel() {
        model.content = SettingsContent(wayBack = wayBack)
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.UndoRestore)
        press(Action.Enter)
        actions.restoreCalls.clear()
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(UndoPanelItem.Forget, model.focus.undoAsk)
        assertEquals("the cursor waits on Cancel", 1, model.focus.panelIndex)
        press(Action.Back)
        assertEquals("Back steps out of the question", null, model.focus.undoAsk)
        assertEquals(PanelPage.UndoRestore, model.focus.page)
        assertTrue(actions.restoreCalls.isEmpty())
        move(Direction.Down)
        press(Action.Enter)
        move(Direction.Up)
        press(Action.Enter)
        assertEquals(listOf("forget copy"), actions.restoreCalls)
        assertEquals("Delete closes the panel", SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun theResultIsForgottenWhenTheFocusLeavesTheRow() {
        openRestorePanel(SettingsContent(restore = RestoreState.Refused(RestoreProblem.NewerFormat)))
        press(Action.Back)
        actions.restoreCalls.clear()
        move(Direction.Down)
        assertEquals(listOf("forget result"), actions.restoreCalls)
    }
}
