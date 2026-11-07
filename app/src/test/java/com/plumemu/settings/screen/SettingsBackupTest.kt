package com.plumemu.settings.screen

import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.backup.BackupProgress
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.BackupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Advanced › Back up… by gamepad and touch: the choice, the picker, Cancel, the line kept until the focus leaves. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class SettingsBackupTest : SettingsModelHarness() {
    private fun openBackupPanel() {
        // The screen hands the model its first content at once; later ones keep the cursor on its item.
        model.content = SettingsContent()
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.Backup)
        press(Action.Enter)
        assertEquals(PanelPage.Backup, model.focus.page)
    }

    @Test
    fun theRowSitsAfterAnimationsAndBeforeReset() {
        val advanced = SettingsSection.Advanced.options
        assertEquals(advanced.indexOf(SettingsOption.Animations) + 1, advanced.indexOf(SettingsOption.BackupAndRestore))
        assertEquals(advanced.indexOf(SettingsOption.BackupAndRestore) + 1, advanced.indexOf(SettingsOption.Reset))
    }

    @Test
    fun aOpensThePanelOnSettingsAndEachButtonAsksThePickerForItsKind() {
        openBackupPanel()
        assertEquals(0, model.focus.panelIndex)
        press(Action.Enter)
        move(Direction.Down)
        assertEquals(1, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals(listOf(BackupKind.Settings, BackupKind.Everything), actions.backupPicks)
        assertEquals("the panel stays open for the progress", PanelPage.Backup, model.focus.page)
    }

    @Test
    fun aTapOnEverythingInTheWidePanelAsksThePicker() {
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.Backup)
        press(Action.Enter)
        val settings = actions.shown
        model.onTapPanelItem(1, settings, wide)
        model.onTapPanelItem(1, settings, wide)
        assertEquals(listOf(BackupKind.Everything), actions.backupPicks)
    }

    @Test
    fun withoutASavePickerThePanelSaysSo() {
        actions.hasSavePicker = false
        openBackupPanel()
        press(Action.Enter)
        assertEquals(SettingsOption.Backup to SettingsNotice.NoSavePicker, model.work.notice)
    }

    @Test
    fun whileABackupRunsTheOnlyButtonIsCancelAndTheCursorStaysOnIt() {
        openBackupPanel()
        move(Direction.Down)
        press(Action.Enter)
        // The picker answered: the graph's backup runs.
        model.content = model.content.copy(backup = BackupState.Running(BackupKind.Everything, BackupProgress(12, 5_000)))
        assertEquals(0, model.focus.panelIndex)
        assertEquals(listOf(BackupPanelItem.Cancel), backupPanelItems(model.content.backup))
        move(Direction.Down)
        assertEquals(0, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals(1, actions.backupCancels)
    }

    @Test
    fun bLeavesThePanelAndTheResultIsForgottenWhenTheFocusLeavesTheRow() {
        openBackupPanel()
        model.content = model.content.copy(backup = BackupState.Done(BackupKind.Settings, "Plumemu settings 2026-10-23.zip", 4_096))
        press(Action.Back)
        press(Action.Back)
        assertEquals(SettingsColumn.Options, model.focus.column)
        val before = actions.backupForgets
        assertEquals("still on the row: the line stays", before, actions.backupForgets)
        move(Direction.Down)
        assertTrue(actions.backupForgets > before)
    }

    @Test
    fun onANarrowScreenTheResultStaysUntilThePanelWasOpened() {
        model.content = SettingsContent(backup = BackupState.Failed(deleted = false))
        openSection(SettingsSection.Advanced)
        // Bounded: a walk that never reaches the row fails here instead of spinning.
        repeat(SettingsSection.Advanced.rowsIn(model.content).size) {
            if (model.focusedOption != SettingsOption.BackupAndRestore) move(Direction.Down, narrow)
        }
        assertEquals("walked to the row (${model.focus})", SettingsOption.BackupAndRestore, model.focusedOption)
        val before = actions.backupForgets
        move(Direction.Down, narrow)
        assertEquals("passing over the row shows no result: it stays", before, actions.backupForgets)
        move(Direction.Up, narrow)
        press(Action.Enter, narrow)
        press(Action.Enter, narrow)
        assertEquals(PanelPage.Backup, model.focus.page)
        press(Action.Back, narrow)
        press(Action.Back, narrow)
        move(Direction.Down, narrow)
        assertTrue("seen in its panel, then left: forgotten", actions.backupForgets > before)
    }
}
