package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Library › unknown folders: the rows (unanswered, answered, ignored, saving) and the side panel, at 768x432 and 576x432. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsUnknownFolderScreenshotTest {
    private val xbox = SettingsFolderRow(FolderKey(1, "Xbox 360"), 3, FolderAnswer.Unanswered)
    private val long = SettingsFolderRow(FolderKey(1, "Roms from the old external drive (backup 2019)"), 1, FolderAnswer.Unanswered)
    private val halo = SettingsFolderRow(FolderKey(2, "Halo"), 0, FolderAnswer.OfSystem("x360", "Xbox 360"))
    private val docs = SettingsFolderRow(FolderKey(2, "Docs"), 0, FolderAnswer.Ignored)
    private val saving = SettingsFolderRow(FolderKey(2, "Wii U"), 0, FolderAnswer.Ignored)
    private val systems =
        listOf("3DO", "Atari 2600", "Dreamcast", "GameCube", "Game Boy Advance", "Mega Drive", "Nintendo 64", "PlayStation", "Super Nintendo", "Xbox 360").map { FolderSystemChoice(it.lowercase(), it) }

    private val content =
        ShotContent.library.copy(
            unknownFolders = listOf(xbox, long, halo, docs, saving),
            folderSystemChoices = systems,
            answeringFolders = setOf(saving.key),
        )

    private fun focusOn(row: SettingsFolderRow, panel: Boolean, index: Int = 0): SettingsFocus {
        val at = SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.UnknownFolder) + content.unknownFolders.indexOf(row)
        return SettingsFocus(
            column = if (panel) SettingsColumn.Panel else SettingsColumn.Options,
            section = SettingsSection.Library,
            rememberedOption = mapOf(SettingsSection.Library to at),
            page = if (panel) PanelPage.UnknownFolder else null,
            panelIndex = index,
            folderKey = if (panel) row.key else null,
        )
    }

    @Test
    fun rowsWithTheUnansweredFolderFocused() = ReferenceShots.capture("settings_unknown_folder_rows") { SettingsAt(focusOn(xbox, panel = false), settingsForShot(), content) }

    @Test
    fun rowsAnsweredAndSaving() = ReferenceShots.capture("settings_unknown_folder_answered") { SettingsAt(focusOn(halo, panel = false), settingsForShot(), content) }

    @Test
    fun panelOfAnUnansweredFolder() = ReferenceShots.capture("settings_unknown_folder_panel") { SettingsAt(focusOn(xbox, panel = true, index = 3), settingsForShot(), content) }

    @Test
    fun panelOfAnAnsweredFolderMarksTheCurrentSystem() = ReferenceShots.capture("settings_unknown_folder_panel_answered") { SettingsAt(focusOn(halo, panel = true, index = 10), settingsForShot(), content) }

    @Test
    fun panelWithALongName() = ReferenceShots.capture("settings_unknown_folder_panel_long_name") { SettingsAt(focusOn(long, panel = true, index = 1), settingsForShot(), content) }
}
