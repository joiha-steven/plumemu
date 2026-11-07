package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.backup.BackupProgress
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.BackupState
import com.plumemu.graph.WayBackSet
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Advanced › Back up and restore and Reset…: the rows with their lists, Back up…'s
 * choice with the password warning, the progress with Cancel, and how it ended.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsBackupScreenshotTest {
    private val signedIn = SettingsContent(account = AccountStatus.SignedIn("plumtester"))

    private fun shot(
        name: String,
        focus: SettingsFocus,
        content: SettingsContent,
    ) = ReferenceShots.capture(name) { SettingsAt(focus, settingsForShot(), content) }

    private val inPanel = panelAt(SettingsOption.Backup, PanelPage.Backup, 0)

    private val copy = WayBackSet(BackupKind.Everything, restoredAtMillis = 0L, bytes = 412_000_000L)

    @Test fun row() = shot("settings_backup_row", optionsAt(SettingsOption.BackupAndRestore), signedIn)

    /** Back up and restore's list with the copy kept, a backup's word waiting. */
    @Test
    fun list() =
        shot(
            "settings_backup_restore_list",
            optionsAt(SettingsOption.BackupAndRestore).copy(column = SettingsColumn.Panel, page = PanelPage.BackupRestore, panelIndex = 1),
            signedIn.copy(backup = BackupState.Done(BackupKind.Everything, "Plumemu backup 2026-10-23.zip", 412_000_000), wayBack = copy),
        )

    /** Reset…'s list beside the row. */
    @Test fun resetRow() = shot("settings_reset_row", optionsAt(SettingsOption.Reset), signedIn)

    @Test fun resetList() = shot("settings_reset_list", optionsAt(SettingsOption.Reset).copy(column = SettingsColumn.Panel, page = PanelPage.Reset, panelIndex = 1), signedIn)

    @Test fun resetConfirm() = shot("settings_reset_library_confirm", optionsAt(SettingsOption.ResetLibrary), signedIn)

    @Test fun choice() = shot("settings_backup_choice", inPanel, signedIn)

    @Test fun choiceSignedOut() = shot("settings_backup_choice_signed_out", inPanel.copy(panelIndex = 1), SettingsContent())

    @Test
    fun running() = shot("settings_backup_running", inPanel, signedIn.copy(backup = BackupState.Running(BackupKind.Everything, BackupProgress(1_234, 5_000))))

    @Test
    fun done() = shot("settings_backup_done", inPanel, signedIn.copy(backup = BackupState.Done(BackupKind.Everything, "Plumemu backup 2026-10-23.zip", 412_000_000)))

    @Test fun failed() = shot("settings_backup_failed", inPanel, signedIn.copy(backup = BackupState.Failed(deleted = true)))

    /** A backup the process did not finish, found at the next start; its file could not be deleted. */
    @Test
    fun interrupted() =
        shot("settings_backup_interrupted", inPanel, signedIn.copy(backup = BackupState.Interrupted(deleted = false, fileName = "Plumemu backup 2026-10-23.zip")))

    /** The row after the person left Settings during a backup that failed: the word stays until its panel is seen. */
    @Test fun rowFailed() = shot("settings_backup_row_failed", optionsAt(SettingsOption.Animations), signedIn.copy(backup = BackupState.Failed(deleted = false)))
}
