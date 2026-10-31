package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.backup.BackupProgress
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.AccountInFile
import com.plumemu.graph.RestoreProblem
import com.plumemu.graph.RestoreState
import com.plumemu.graph.RestoreSummary
import com.plumemu.graph.WayBackSet
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Advanced › Restore… and Undo last restore:
 * the row, the panel, the reading, the confirm of each kind, the swap, a refusal, how it ended,
 * and the Undo row with its panel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsRestoreScreenshotTest {
    private val signedIn = SettingsContent(account = AccountStatus.SignedIn("plumtester"))
    private val everything = RestoreSummary(BackupKind.Everything, MADE_AT, games = 2_054, pictures = 1_234, account = AccountInFile.SignsIn)
    private val wayBack = WayBackSet(BackupKind.Everything, RESTORED_AT, 412_000_000)

    private fun shot(
        name: String,
        focus: SettingsFocus,
        content: SettingsContent,
    ) = ReferenceShots.capture(name) { SettingsAt(focus, settingsForShot(), content) }

    private val inPanel = panelAt(SettingsOption.Restore, PanelPage.Restore, 0)

    /** The Undo row is listed only with a set, so its place comes from the rows shown. */
    private fun undoAt(content: SettingsContent): SettingsFocus {
        val index = SettingsSection.Advanced.rowsIn(content).indexOf(SettingsOption.UndoRestore)
        return SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Advanced, rememberedOption = mapOf(SettingsSection.Advanced to index))
    }

    @Test fun row() = shot("settings_restore_row", optionsAt(SettingsOption.Restore), signedIn)

    @Test fun panel() = shot("settings_restore_panel", inPanel, signedIn)

    @Test
    fun reading() = shot("settings_restore_reading", inPanel, signedIn.copy(restore = RestoreState.Reading(BackupProgress(1_234, 5_000))))

    @Test fun confirmEverything() = shot("settings_restore_confirm_everything", inPanel.copy(panelIndex = 1), signedIn.copy(restore = RestoreState.Confirm(everything)))

    @Test
    fun confirmSettings() =
        shot(
            "settings_restore_confirm_settings",
            inPanel.copy(panelIndex = 1),
            signedIn.copy(restore = RestoreState.Confirm(RestoreSummary(BackupKind.Settings, MADE_AT, games = 0, pictures = 0, account = AccountInFile.SignsIn))),
        )

    @Test fun replacing() = shot("settings_restore_replacing", inPanel, signedIn.copy(restore = RestoreState.Replacing(isUndo = false, withLibrary = true)))

    @Test fun refusedNewer() = shot("settings_restore_refused_newer", inPanel, signedIn.copy(restore = RestoreState.Refused(RestoreProblem.NewerFormat)))

    @Test fun refusedTorn() = shot("settings_restore_refused_torn", inPanel, signedIn.copy(restore = RestoreState.Refused(RestoreProblem.Torn)))

    @Test fun refusedScan() = shot("settings_restore_refused_scan", inPanel, signedIn.copy(restore = RestoreState.Refused(RestoreProblem.ScanRunning)))

    /** After the restart: the row's word and its panel's line, with Undo offered below. */
    @Test
    fun doneEverything() = shot("settings_restore_done", inPanel, signedIn.copy(restore = RestoreState.Done(isUndo = false, BackupKind.Everything, MADE_AT), wayBack = wayBack))

    @Test fun failed() = shot("settings_restore_failed", inPanel, signedIn.copy(restore = RestoreState.Failed(isUndo = false, isPutBack = true)))

    @Test fun interrupted() = shot("settings_restore_interrupted", inPanel, signedIn.copy(restore = RestoreState.Interrupted(isFinished = false)))

    @Test
    fun undoRow() {
        val content = signedIn.copy(wayBack = wayBack)
        shot("settings_restore_undo_row", undoAt(content), content)
    }

    @Test
    fun undoPanel() {
        val content = signedIn.copy(wayBack = wayBack)
        shot("settings_restore_undo_panel", undoAt(content).copy(column = SettingsColumn.Panel, page = PanelPage.UndoRestore, panelIndex = 0), content)
    }

    @Test
    fun undoAsk() {
        val content = signedIn.copy(wayBack = wayBack)
        shot("settings_restore_undo_ask", undoAt(content).copy(column = SettingsColumn.Panel, page = PanelPage.UndoRestore, panelIndex = 1, undoAsk = UndoPanelItem.Undo), content)
    }

    @Test
    fun forgetAsk() {
        val content = signedIn.copy(wayBack = wayBack)
        shot("settings_restore_forget_ask", undoAt(content).copy(column = SettingsColumn.Panel, page = PanelPage.UndoRestore, panelIndex = 1, undoAsk = UndoPanelItem.Forget), content)
    }

    /** A second Everything restore: the confirm says the kept copy goes. */
    @Test
    fun confirmReplacingTheCopy() =
        shot("settings_restore_confirm_replaces_copy", inPanel.copy(panelIndex = 1), signedIn.copy(restore = RestoreState.Confirm(everything), wayBack = wayBack))

    private companion object {
        const val MADE_AT = 1_792_756_800_000L
        const val RESTORED_AT = 1_792_843_200_000L
    }
}
