package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Library, Emulators, Controls and Home screen against the board RP5-S1-settings
 * (pages library, emulators, controls, home) and Nova-6-settings (narrow). Wide: the option focused
 * with its panel beside; narrow: the panel as the further stop where it holds a list.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsSectionsScreenshotTest {
    private val isWide: Boolean
        @androidx.compose.runtime.Composable get() = PlumTheme.layout.widthClass == WidthClass.Wide

    @Test
    fun library() =
        ReferenceShots.capture("settings_library") {
            SettingsAt(if (isWide) optionsAt(SettingsOption.RomFolders) else panelAt(SettingsOption.RomFolders, PanelPage.Folders, 0), content = ShotContent.library)
        }

    /** The Rescan library panel while a scan runs: the breathing dot (pinned) before the progress line. */
    @Test
    fun rescanWhileScanning() =
        ReferenceShots.capture("settings_rescan_scanning") {
            // Narrow: the panel is a stop of its own.
            val focus = if (isWide) optionsAt(SettingsOption.Rescan) else optionsAt(SettingsOption.Rescan).copy(column = SettingsColumn.Panel)
            SettingsAt(focus, content = ShotContent.library.copy(isScanning = true))
        }

    @Test
    fun rescanWalking() =
        ReferenceShots.capture("settings_rescan_walking") {
            val focus = if (isWide) optionsAt(SettingsOption.Rescan) else optionsAt(SettingsOption.Rescan).copy(column = SettingsColumn.Panel)
            SettingsAt(focus, content = RescanShots.walking)
        }

    @Test
    fun rescanBusy() =
        ReferenceShots.capture("settings_rescan_busy") {
            val focus = if (isWide) optionsAt(SettingsOption.Rescan) else optionsAt(SettingsOption.Rescan).copy(column = SettingsColumn.Panel)
            SettingsAt(focus, content = RescanShots.busy, busyPress = true)
        }

    @Test
    fun rescanNaming() =
        ReferenceShots.capture("settings_rescan_naming") {
            val focus = if (isWide) optionsAt(SettingsOption.Rescan) else optionsAt(SettingsOption.Rescan).copy(column = SettingsColumn.Panel)
            SettingsAt(focus, content = RescanShots.naming)
        }

    @Test
    fun rescanDone() =
        ReferenceShots.capture("settings_rescan_done") {
            val focus = if (isWide) optionsAt(SettingsOption.Rescan) else optionsAt(SettingsOption.Rescan).copy(column = SettingsColumn.Panel)
            SettingsAt(focus, content = RescanShots.withChanges)
        }

    @Test
    fun rescanDoneNoChanges() =
        ReferenceShots.capture("settings_rescan_done_nochanges") {
            val focus = if (isWide) optionsAt(SettingsOption.Rescan) else optionsAt(SettingsOption.Rescan).copy(column = SettingsColumn.Panel)
            SettingsAt(focus, content = RescanShots.noChanges)
        }

    @Test
    fun rescanFolderUnavailable() =
        ReferenceShots.capture("settings_rescan_unavailable") {
            val focus = if (isWide) optionsAt(SettingsOption.Rescan) else optionsAt(SettingsOption.Rescan).copy(column = SettingsColumn.Panel)
            SettingsAt(focus, content = RescanShots.mixed)
        }

    @Test
    fun folderRemoveConfirm() =
        ReferenceShots.capture("settings_folder_remove") {
            val focus = panelAt(SettingsOption.RomFolders, PanelPage.RemoveFolder, SettingsModel.CONFIRM_CANCEL).copy(folderId = 2)
            SettingsAt(focus, content = ShotContent.library)
        }

    @Test
    fun emulators() =
        ReferenceShots.capture("settings_emulators") {
            val focus = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Emulators, rememberedOption = mapOf(SettingsSection.Emulators to ShotContent.PSX_ROW))
            SettingsAt(focus, content = ShotContent.library)
        }

    @Test
    fun emulatorsOfASystem() =
        ReferenceShots.capture("settings_emulators_system") {
            val focus =
                SettingsFocus(
                    column = SettingsColumn.Panel,
                    section = SettingsSection.Emulators,
                    rememberedOption = mapOf(SettingsSection.Emulators to ShotContent.PSX_ROW),
                    page = PanelPage.Emulators,
                    panelIndex = 2,
                    systemId = "psx",
                )
            SettingsAt(focus, content = ShotContent.library)
        }

    @Test
    fun controls() = ReferenceShots.capture("settings_controls") { SettingsAt(optionsAt(SettingsOption.ShowButtonHints), content = ShotContent.library) }

    /** Push right to play focused and on: its switch and the explanation beside it. */
    @Test
    fun controlsPushRightToPlay() =
        ReferenceShots.capture("settings_controls_push_right") {
            val on = settingsForShot().let { it.copy(controls = it.controls.copy(pushRightToPlay = true)) }
            SettingsAt(optionsAt(SettingsOption.PushRightToPlay), settings = on, content = ShotContent.library)
        }

    /** The long explanation at the largest text size: nothing cut (the panel scrolls). */
    @Test
    fun controlsPushRightLargest() =
        ReferenceShots.capture("settings_controls_push_right", textSize = com.plumemu.coreui.theme.TextSize.Largest) {
            val on = settingsForShot().let { it.copy(controls = it.controls.copy(pushRightToPlay = true)) }
            SettingsAt(optionsAt(SettingsOption.PushRightToPlay), settings = on, content = ShotContent.library)
        }

    /** Controls › Keyboard: ‹ Plumemu ›, its explanation beside. */
    @Test
    fun controlsKeyboard() = ReferenceShots.capture("settings_controls_keyboard") { SettingsAt(optionsAt(SettingsOption.Keyboard), content = ShotContent.library) }

    /** Controls › Switch system with: ‹ L2 / R2 ›, its explanation beside. */
    @Test
    fun controlsSwitchSystemWith() = ReferenceShots.capture("settings_controls_switch_system_with") { SettingsAt(optionsAt(SettingsOption.SwitchSystemWith), content = ShotContent.library) }

    /** The label and the long explanation at the largest text size: nothing cut. */
    @Test
    fun controlsSwitchSystemWithLargest() =
        ReferenceShots.capture("settings_controls_switch_system_with", textSize = com.plumemu.coreui.theme.TextSize.Largest) {
            SettingsAt(optionsAt(SettingsOption.SwitchSystemWith), content = ShotContent.library)
        }

    @Test
    fun homeScreen() = ReferenceShots.capture("settings_home") { SettingsAt(optionsAt(SettingsOption.UseAsHome), content = ShotContent.library) }
}
