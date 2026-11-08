package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Library › Folder could not be read: the row with its panel beside it, and the panel on Scan again and on a long folder, at 768x432 and 576x432. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsUnreadableFoldersScreenshotTest {
    private val content =
        ShotContent.library.let { library ->
            library.copy(
                folders =
                    listOf(
                        library.folders[0].copy(unreadablePaths = listOf("psx")),
                        library.folders[1].copy(unreadablePaths = listOf("Game Boy Advance/Translations from the old drive")),
                    ),
            )
        }

    private val row = SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.UnreadableFolders)

    private val onRow = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Library, rememberedOption = mapOf(SettingsSection.Library to row))

    private fun inPanel(index: Int) = onRow.copy(column = SettingsColumn.Panel, page = PanelPage.UnreadableFolders, panelIndex = index)

    @Test
    fun rowWithItsPanel() = ReferenceShots.capture("settings_unreadable_folders_row") { SettingsAt(onRow, settingsForShot(), content) }

    @Test
    fun panelOnScanAgain() = ReferenceShots.capture("settings_unreadable_folders_panel") { SettingsAt(inPanel(0), settingsForShot(), content) }

    @Test
    fun panelOnALongFolder() = ReferenceShots.capture("settings_unreadable_folders_panel_long") { SettingsAt(inPanel(2), settingsForShot(), content) }
}
