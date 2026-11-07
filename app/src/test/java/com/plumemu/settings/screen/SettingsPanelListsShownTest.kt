package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.graph.BackupState
import com.plumemu.graph.LibraryResetState
import com.plumemu.graph.RestoreState
import com.plumemu.graph.ScrapeCounts
import com.plumemu.graph.ScrapeStatus
import com.plumemu.graph.WayBackSet
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.UpdateFailure
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateStep
import com.plumemu.update.UpdateView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the Updates, Back up and restore, Reset… and Box art panels show, state by state: the words a person reads and the row's value. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsPanelListsShownTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val release = UpdateManifest(41, "0.1.3", "https://plumemu-app.stevensdesk.com/update/p.apk", 9_800_000, "ab".repeat(32), 23, "2026-10-23", listOf("One."), null)
    private val found = UpdateView(lastCheck = LastCheck(1, CheckOutcome.Found), found = release)
    private val copy = WayBackSet(BackupKind.Everything, restoredAtMillis = 0L, bytes = 120_000_000L)

    private fun show(
        focus: SettingsFocus,
        content: SettingsContent,
        status: SettingsStatus = SettingsStatus(),
        pressAfter: Action? = null,
    ) {
        val model = SettingsModel(NoActions, focus)
        model.content = content.withGamesListed()
        lateinit var shown: PlumSettings
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                shown = settingsForShot()
                SettingsLayout(shown, model, NoTouch, TEST_VERSION, { BOARD_CLOCK }, { BOARD_BATTERY }, status = status)
            }
        }
        compose.waitForIdle()
        pressAfter?.let { action -> compose.runOnIdle { model.onAction(action, PlumSettings(), SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)) } }
        compose.waitForIdle()
    }

    private fun updatesAt(index: Int, view: UpdateView) =
        show(optionsAt(SettingsOption.Updates).copy(column = SettingsColumn.Panel, page = PanelPage.Update, panelIndex = index), SettingsContent(update = view, hasUpdates = true))

    private fun inUpdates(text: String) = compose.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(SettingsTags.UPDATE_PANEL)))

    private fun noUpdateButton() = compose.onNode(hasText(context.getString(R.string.settings_update_button)) and hasAnyAncestor(hasTestTag(SettingsTags.UPDATE_PANEL))).assertDoesNotExist()

    private fun status() = compose.onNodeWithTag(SettingsPanelTags.STATUS)

    @Test
    fun withNothingFoundTheStatusCheckNowAndTheSwitchShowAndNoUpdateButton() {
        updatesAt(0, UpdateView(lastCheck = LastCheck(1, CheckOutcome.UpToDate)))
        status().assertIsDisplayed()
        inUpdates(context.getString(R.string.settings_updates_check_now)).assertIsDisplayed()
        inUpdates(context.getString(R.string.settings_check_updates_daily)).assertExists()
        noUpdateButton()
    }

    @Test
    fun theDailyExplanationNamesWhoSeesTheAddressAndIsOnScreenOnTheSwitchWithAnUpdateFound() {
        updatesAt(2, found)
        val body = context.getString(R.string.settings_check_updates_daily_body)
        assertTrue(body.contains("Cloudflare"))
        compose.onNodeWithText(body).assertIsDisplayed()
    }

    @Test
    fun everyStepOfAnUpdateStillSaysItsLineAndOffersItsButtons() {
        val steps =
            listOf(
                UpdateStep.Downloading(1_000, 9_800_000) to null,
                UpdateStep.AskMetered to null,
                UpdateStep.NeedsPermission to R.string.settings_update_needs_permission,
                UpdateStep.NoPermissionScreen to R.string.settings_update_no_permission_screen,
                UpdateStep.Failed(UpdateFailure.WrongSigner) to R.string.settings_update_failed_signer,
                UpdateStep.Failed(UpdateFailure.HashMismatch) to R.string.settings_update_failed_hash,
                UpdateStep.Failed(UpdateFailure.Offline) to R.string.settings_update_failed_offline,
                UpdateStep.Verifying to R.string.settings_update_verifying,
                UpdateStep.ReadyToInstall to R.string.settings_update_ready,
                UpdateStep.Installing() to R.string.settings_update_installing,
                UpdateStep.Installing(askedPerson = true) to R.string.settings_update_confirm,
                UpdateStep.Cancelled to R.string.settings_update_cancelled,
                UpdateStep.InstallAborted to R.string.settings_update_aborted,
            )
        val model = SettingsModel(NoActions, optionsAt(SettingsOption.Updates).copy(column = SettingsColumn.Panel, page = PanelPage.Update))
        model.content = SettingsContent(update = found, hasUpdates = true).withGamesListed()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                SettingsLayout(settingsForShot(), model, NoTouch, TEST_VERSION, { BOARD_CLOCK }, { BOARD_BATTERY })
            }
        }
        for ((step, line) in steps) {
            val view = found.copy(step = step)
            compose.runOnIdle { model.content = SettingsContent(update = view, hasUpdates = true).withGamesListed() }
            compose.waitForIdle()
            if (line != null) status().assertTextContains(context.getString(line).take(20), substring = true)
            updateStepItems(view).forEachIndexed { index, _ -> compose.onNodeWithTag(SettingsTags.panelItem(index), useUnmergedTree = true).assertExists() }
            inUpdates(context.getString(R.string.settings_updates_check_now)).assertExists()
        }
    }

    @Test
    fun aReleaseForANewerAndroidSaysSoWithNoUpdateButton() {
        updatesAt(0, UpdateView(lastCheck = LastCheck(1, CheckOutcome.NeedsNewerAndroid)))
        status().assertTextContains(context.getString(R.string.settings_check_updates_needs_android), substring = true)
        noUpdateButton()
    }

    @Test
    fun checkNowDuringADownloadSaysWhyItChecksNothing() {
        val view = found.copy(step = UpdateStep.Downloading(1_000, 9_800_000))
        updatesAt(updatesPanelItems(view).indexOf(UpdatePanelItem.CheckNow), view)
        status().assertTextContains(context.getString(R.string.settings_check_updates_busy))
    }

    @Test
    fun theRowsValueIsTheVersionTheLastResultOrAWordWhileItRuns() {
        var values = emptyList<OptionValue>()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                values =
                    listOf(
                        updatesRowValue(found),
                        updatesRowValue(found.copy(step = UpdateStep.Downloading(1, 2))),
                        updatesRowValue(UpdateView(isChecking = true)),
                        updatesRowValue(UpdateView()),
                    )
            }
        }
        compose.waitForIdle()
        assertEquals(OptionValue.Opens(versionOf(found)), values[0])
        assertEquals(OptionValue.Text(context.getString(R.string.settings_update_value_downloading)), values[1])
        assertEquals(OptionValue.Opens(context.getString(R.string.settings_check_updates_checking)), values[2])
        assertEquals(OptionValue.Opens(""), values[3])
    }

    private fun versionOf(view: UpdateView) = "${checkNotNull(view.found).versionName} available"

    @Test
    fun backupAndRestoresValueTakesTheMostUrgentFirst() {
        var values = emptyList<OptionValue>()
        val saved = BackupState.Done(BackupKind.Settings, "a.zip", 1)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                values =
                    listOf(
                        backupAndRestoreValue(SettingsContent(backup = BackupState.Running(BackupKind.Settings), restore = RestoreState.Interrupted(isFinished = true), wayBack = copy)),
                        backupAndRestoreValue(SettingsContent(restore = RestoreState.Reading(), backup = saved, wayBack = copy)),
                        backupAndRestoreValue(SettingsContent(backup = saved, wayBack = copy)),
                        backupAndRestoreValue(SettingsContent(backup = BackupState.Interrupted(deleted = true, fileName = null))),
                        backupAndRestoreValue(SettingsContent(restore = RestoreState.Interrupted(isFinished = false), backup = saved)),
                        backupAndRestoreValue(SettingsContent(wayBack = copy)),
                        backupAndRestoreValue(SettingsContent()),
                    )
            }
        }
        compose.waitForIdle()
        assertEquals(OptionValue.Text(context.getString(R.string.settings_backup_running)), values[0])
        assertEquals(OptionValue.Text(context.getString(R.string.settings_restore_reading)), values[1])
        assertEquals(OptionValue.Opens(context.getString(R.string.settings_backup_value_saved)), values[2])
        assertEquals(OptionValue.Opens(context.getString(R.string.settings_backup_value_interrupted)), values[3])
        assertEquals(OptionValue.Opens(context.getString(R.string.settings_restore_value_interrupted)), values[4])
        assertTrue("the copy's space, no word for it alone", (values[5] as OptionValue.Opens).text.contains("120"))
        assertEquals(OptionValue.Opens(""), values[6])
    }

    @Test
    fun theListShowsEachItemsWord() {
        show(
            optionsAt(SettingsOption.BackupAndRestore).copy(column = SettingsColumn.Panel, page = PanelPage.BackupRestore),
            SettingsContent(backup = BackupState.Interrupted(deleted = true, fileName = null), restore = RestoreState.Interrupted(isFinished = true), wayBack = copy),
        )
        compose.onNodeWithTag(SettingsTags.panelItem(0), useUnmergedTree = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_backup)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_restore)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_undo_restore)).assertIsDisplayed()
    }

    @Test
    fun anInterruptedBackupsWholeLineIsOnItsPage() {
        show(panelAt(SettingsOption.Backup, PanelPage.Backup, 0), SettingsContent(backup = BackupState.Interrupted(deleted = true, fileName = null)))
        compose.onNodeWithText(context.getString(R.string.settings_backup_interrupted)).assertExists()
    }

    @Test
    fun resetListsBothWithWhatEachDoes() {
        show(optionsAt(SettingsOption.Reset), SettingsContent())
        compose.onNodeWithText(context.getString(R.string.settings_reset_all)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_reset_all_body)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_reset_library)).assertExists()
    }

    @Test
    fun aFailedLibraryResetIsSaidOnTheList() {
        show(panelAt(SettingsOption.ResetLibrary, PanelPage.ResetLibraryConfirm, SettingsModel.CONFIRM_RESET), SettingsContent(), SettingsStatus(libraryReset = LibraryResetState.Failed), Action.Enter)
        compose.onNodeWithTag(SettingsPanelTags.DONE).assertTextContains(context.getString(R.string.settings_reset_library_failed))
    }

    @Test
    fun boxArtsPanelDrawsReDownloadsConfirm() {
        val games = SettingsContent(account = AccountStatus.SignedOut, chosenBoxArtSource = BoxArtSourceSetting.LibretroOnly, boxArt = ScrapeStatus.Idle(ScrapeCounts(300, 2_054, 0)))
        show(SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Accounts, page = PanelPage.ScraperConfirm, panelIndex = BoxArtConfirmItem.Cancel.ordinal), games)
        compose.onNodeWithText(context.getString(R.string.settings_box_art_confirm_title)).assertIsDisplayed()
    }
}
