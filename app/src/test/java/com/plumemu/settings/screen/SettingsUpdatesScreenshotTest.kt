package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.CheckFailure
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.NoteSection
import com.plumemu.update.NoticeLine
import com.plumemu.update.SectionKind
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
    private val grouped =
        release.copy(
            sections =
                listOf(
                    NoteSection(SectionKind.New, listOf("Updates install from Settings › About.", "A short tour of the home screen on first start.")),
                    NoteSection(SectionKind.Improved, listOf("Folders are checked again when you come back to Plumemu.")),
                    NoteSection(SectionKind.Fixed, listOf("The grid's first row is no longer cut.", "Scanning a folder with a very long name no longer stops.")),
                    NoteSection(SectionKind.Known, listOf("Some DAT files need a rescan after the update.")),
                ),
        )
    private val longItem = "A very long note that goes on and on, to show that the text wraps under itself and never under the dot, with words that make the lines longer still, and that it never touches the edge of the panel."
    private val long = release.copy(sections = listOf(NoteSection(SectionKind.New, listOf(longItem, "Short one.")), NoteSection(SectionKind.Fixed, listOf(longItem))))
    private val found = UpdateView(lastCheck = LastCheck(checkedAt, CheckOutcome.Found), found = release)
    private fun foundWith(manifest: UpdateManifest, step: UpdateStep = UpdateStep.Idle) = SettingsContent(update = found.copy(found = manifest, step = step))
    private val withUpdate = SettingsContent(update = found)

    /** About › Updates, the second row. */
    private val onUpdateRow = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1))
    private val inPanel = onUpdateRow.copy(column = SettingsColumn.Panel, page = PanelPage.Update, panelIndex = 0)

    private fun shot(
        name: String,
        focus: SettingsFocus,
        content: SettingsContent,
    ) = ReferenceShots.capture(name) { SettingsAt(focus, settingsForShot(), content) }

    @Test fun updateRow() = shot("settings_update_row", onUpdateRow, withUpdate)

    @Test fun updatePanel() = shot("settings_update_panel", inPanel, withUpdate)

    @Test fun updatePanelWithGroups() = shot("settings_update_groups", inPanel, foundWith(grouped))

    @Test fun updatePanelWithAVeryLongItem() = shot("settings_update_long_item", inPanel, foundWith(long))

    @Test fun downloadingWithGroups() = shot("settings_update_groups_downloading", inPanel, foundWith(grouped, UpdateStep.Downloading(3_400_000, 9_800_000)))

    @Test fun downloading() = shot("settings_update_downloading", inPanel, SettingsContent(update = found.copy(step = UpdateStep.Downloading(3_400_000, 9_800_000))))

    private val downloadingNote = UpdateStep.Downloading(3_400_000, 9_800_000)

    @Test fun noticeReason() = shot("settings_update_notice_reason", inPanel, SettingsContent(update = found.copy(step = downloadingNote, notice = NoticeLine.Reason)))

    @Test fun noticeOff() = shot("settings_update_notice_off", inPanel, SettingsContent(isHomeApp = true, update = found.copy(step = downloadingNote, notice = NoticeLine.NotificationsOff)))

    @Test fun noticeOffNotHome() = shot("settings_update_notice_off_not_home", inPanel, SettingsContent(update = found.copy(step = downloadingNote, notice = NoticeLine.NotificationsOff)))

    @Test fun noticeOffWithButton() = shot("settings_update_notice_button", inPanel, SettingsContent(isHomeApp = true, update = found.copy(notice = NoticeLine.NotificationsOff)))

    @Test fun noticeOffNotHomeWithButton() = shot("settings_update_notice_button_not_home", inPanel, SettingsContent(update = found.copy(notice = NoticeLine.NotificationsOff)))

    @Test fun noticeOffWithGroups() = shot("settings_update_notice_off_groups", inPanel, SettingsContent(isHomeApp = true, update = found.copy(found = grouped, step = downloadingNote, notice = NoticeLine.NotificationsOff)))

    @Test fun metered() = shot("settings_update_metered", inPanel, SettingsContent(update = found.copy(step = UpdateStep.AskMetered)))

    @Test fun needsPermission() = shot("settings_update_permission", inPanel, SettingsContent(update = found.copy(step = UpdateStep.NeedsPermission)))

    @Test fun waitingForAndroid() = shot("settings_update_installing", inPanel, SettingsContent(update = found.copy(step = UpdateStep.Installing(askedPerson = true))))

    @Test fun tamperedRefused() = shot("settings_update_tampered", inPanel, SettingsContent(update = found.copy(step = UpdateStep.Failed(UpdateFailure.WrongSigner))))

    @Test
    fun checkRowAfterAFailure() =
        shot(
            "settings_update_check_failed",
            optionsAt(SettingsOption.Updates),
            SettingsContent(update = UpdateView(lastCheck = LastCheck(checkedAt, CheckOutcome.Failed(CheckFailure.Offline)))),
        )

    /** The panel with nothing found, and the daily switch with what it sends brought into view. */
    @Test fun nothingFound() = shot("settings_update_nothing_found", inPanel, SettingsContent(update = UpdateView(lastCheck = LastCheck(checkedAt, CheckOutcome.UpToDate))))

    @Test fun dailySwitch() = shot("settings_update_daily", inPanel.copy(panelIndex = updatesPanelItems(found).indexOf(UpdatePanelItem.CheckDaily)), withUpdate)
}
