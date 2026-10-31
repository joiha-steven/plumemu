package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.CheckFailure
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.UpdateFailure
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateStep
import com.plumemu.update.UpdateView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › About with an update found: the row with
 * its panel beside it, the panel's steps, the check row and the daily switch. Each shot at 768×432
 * (the Thor's 1920×1080) and 576×432 (4:3), Graphite and Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsUpdatesScreenshotTest {
    /** 15 January 2026 UTC: a fixed past day, so the value shows a date whatever day the shots are taken. */
    private val checkedAt = 1_768_471_200_000L

    private val release =
        UpdateManifest(
            versionCode = 41,
            versionName = "0.0.3-alpha",
            apkUrl = "https://plumemu-app.stevensdesk.com/update/plumemu-0.0.3-alpha.apk",
            size = 9_800_000,
            sha256 = "ab".repeat(32),
            minSdk = 23,
            released = "2026-10-23",
            notes = listOf("Updates install from Settings › About.", "Folders are checked again when you come back to Plumemu.", "Fixes the grid's first row being cut."),
            notesUrl = null,
        )
    private val found = UpdateView(lastCheck = LastCheck(checkedAt, CheckOutcome.Found), found = release)
    private val withUpdate = SettingsContent(update = found)

    /** About › Update available, the second row while one is found. */
    private val onUpdateRow = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1))
    private val inPanel = onUpdateRow.copy(column = SettingsColumn.Panel, page = PanelPage.Update, panelIndex = 0)

    private fun shot(
        name: String,
        focus: SettingsFocus,
        content: SettingsContent,
    ) = ReferenceShots.capture(name) { SettingsAt(focus, settingsForShot(), content) }

    @Test fun updateRow() = shot("settings_update_row", onUpdateRow, withUpdate)

    @Test fun updatePanel() = shot("settings_update_panel", inPanel, withUpdate)

    @Test fun downloading() = shot("settings_update_downloading", inPanel, SettingsContent(update = found.copy(step = UpdateStep.Downloading(3_400_000, 9_800_000))))

    @Test fun metered() = shot("settings_update_metered", inPanel, SettingsContent(update = found.copy(step = UpdateStep.AskMetered)))

    @Test fun needsPermission() = shot("settings_update_permission", inPanel, SettingsContent(update = found.copy(step = UpdateStep.NeedsPermission)))

    @Test fun waitingForAndroid() = shot("settings_update_installing", inPanel, SettingsContent(update = found.copy(step = UpdateStep.Installing(askedPerson = true))))

    @Test fun tamperedRefused() = shot("settings_update_tampered", inPanel, SettingsContent(update = found.copy(step = UpdateStep.Failed(UpdateFailure.WrongSigner))))

    @Test
    fun checkRowAfterAFailure() =
        shot(
            "settings_update_check_failed",
            optionsAt(SettingsOption.CheckForUpdates),
            SettingsContent(update = UpdateView(lastCheck = LastCheck(checkedAt, CheckOutcome.Failed(CheckFailure.Offline)))),
        )

    @Test fun dailySwitch() = shot("settings_update_daily", optionsAt(SettingsOption.CheckUpdatesDaily), withUpdate)
}
