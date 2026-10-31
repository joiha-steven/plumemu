package com.plumemu.settings.screen

import android.content.Context
import android.text.format.Formatter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.backup.BackupProgress
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.graph.BackupFailure
import com.plumemu.graph.BackupState
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What Back up…'s panel says in each state: the password warning, the progress, the end (named file and size, cancel, failure). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsBackupPanelTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun show(
        backup: BackupState,
        account: AccountStatus = AccountStatus.SignedOut,
    ) {
        val model = SettingsModel(NoActions, panelAt(SettingsOption.Backup, PanelPage.Backup, 0))
        model.content = SettingsContent(account = account, backup = backup)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                SettingsLayout(settingsForShot(), model, NoTouch, TEST_VERSION, { BOARD_CLOCK }, { BOARD_BATTERY })
            }
        }
    }

    private fun text(id: Int) = compose.onNodeWithText(context.getString(id))

    @Test
    fun idleShowsBothChoicesAndThePasswordWarningOnlyWhileSignedIn() {
        show(BackupState.Idle, AccountStatus.SignedIn("plumtester"))
        text(R.string.settings_backup_settings).assertIsDisplayed()
        text(R.string.settings_backup_everything).assertIsDisplayed()
        compose.onNodeWithTag(SettingsPanelTags.BACKUP_PASSWORD).assertIsDisplayed()
    }

    @Test
    fun signedOutThereIsNoPasswordWarning() {
        show(BackupState.Idle)
        compose.onNodeWithTag(SettingsPanelTags.BACKUP_PASSWORD).assertDoesNotExist()
    }

    @Test
    fun runningShowsTheFilesWrittenOfTheTotalAndCancel() {
        show(BackupState.Running(BackupKind.Everything, BackupProgress(1_234, 5_000)))
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.settings_backup_progress, 5_000, "1,234", "5,000")).assertIsDisplayed()
        text(R.string.settings_cancel).assertIsDisplayed()
        text(R.string.settings_backup_everything).assertDoesNotExist()
    }

    @Test
    fun whileTheFileIsCheckedThePanelSaysSoAndCancelStays() {
        show(BackupState.Running(BackupKind.Everything, isChecking = true))
        text(R.string.settings_backup_checking).assertIsDisplayed()
        text(R.string.settings_cancel).assertIsDisplayed()
        // "Backing up…" stays only as the row's value, once: the panel's line is the check's.
        compose.onAllNodesWithText(context.getString(R.string.settings_backup_running)).assertCountEquals(1)
    }

    @Test
    fun aFileThatCouldNotBeReadBackAsksForAnotherPlace() {
        show(BackupState.Failed(deleted = true, reason = BackupFailure.NotReadBack))
        text(R.string.settings_backup_not_read_back).assertIsDisplayed()
    }

    @Test
    fun anUncheckedFileThatStayedAsksThePersonToDeleteIt() {
        show(BackupState.Failed(deleted = false, reason = BackupFailure.NotReadBack))
        text(R.string.settings_backup_not_read_back_kept).assertIsDisplayed()
    }

    @Test
    fun doneNamesTheFileAndItsSize() {
        show(BackupState.Done(BackupKind.Everything, "Plumemu backup 2026-10-23.zip", 412_000_000))
        val size = Formatter.formatShortFileSize(context, 412_000_000)
        compose.onNodeWithText(context.getString(R.string.settings_backup_done, "Plumemu backup 2026-10-23.zip", size)).assertIsDisplayed()
    }

    @Test
    fun aFailureSaysSoAndWhetherTheFileWasDeleted() {
        show(BackupState.Failed(deleted = true))
        text(R.string.settings_backup_failed).assertIsDisplayed()
    }

    @Test
    fun aFailureWhoseFileStayedAsksThePersonToDeleteIt() {
        show(BackupState.Failed(deleted = false))
        text(R.string.settings_backup_failed_kept).assertIsDisplayed()
    }

    @Test
    fun aBackupThatDidNotReadBackWholeSaysTheFileIsDamaged() {
        show(BackupState.Failed(deleted = true, reason = BackupFailure.Damaged))
        text(R.string.settings_backup_damaged).assertIsDisplayed()
    }

    @Test
    fun aDamagedFileThatStayedAsksThePersonToDeleteIt() {
        show(BackupState.Failed(deleted = false, reason = BackupFailure.Damaged))
        text(R.string.settings_backup_damaged_kept).assertIsDisplayed()
    }

    @Test
    fun anInterruptedBackupSaysItsFileWasDeleted() {
        show(BackupState.Interrupted(deleted = true, fileName = "Plumemu backup 2026-10-23.zip"))
        text(R.string.settings_backup_interrupted).assertIsDisplayed()
    }

    @Test
    fun anInterruptedBackupWhoseFileStayedNamesIt() {
        show(BackupState.Interrupted(deleted = false, fileName = "Plumemu backup 2026-10-23.zip"))
        compose.onNodeWithText(context.getString(R.string.settings_backup_interrupted_kept, "Plumemu backup 2026-10-23.zip")).assertIsDisplayed()
    }

    @Test
    fun theRowSaysHowTheLastBackupEndedUntilItIsSeen() {
        val opens = { word: Int -> context.getString(R.string.settings_value_opens, context.getString(word)) }
        show(BackupState.Failed(deleted = false))
        compose.onNodeWithText(opens(R.string.settings_backup_value_failed)).assertIsDisplayed()
    }

    @Test
    fun aCancelSaysTheFileWasDeleted() {
        show(BackupState.Cancelled(deleted = true))
        text(R.string.settings_backup_cancelled).assertIsDisplayed()
    }
}
