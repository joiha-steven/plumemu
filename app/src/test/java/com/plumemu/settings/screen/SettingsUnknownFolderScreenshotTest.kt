package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Library › Files not added › unknown folders: the lines (unanswered, answered, ignored, saving) and the folder's page, at 768x432 and 576x432. */
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
        ).let { it.copy(notAdded = notAddedItems(emptyList(), it.unknownFolders)) }

    private val filesNotAdded = SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.FilesNotAdded)

    private fun focusOnLine(row: SettingsFolderRow) =
        SettingsFocus(
            column = SettingsColumn.Panel,
            section = SettingsSection.Library,
            rememberedOption = mapOf(SettingsSection.Library to filesNotAdded),
            page = PanelPage.FilesNotAdded,
            panelIndex = content.notAdded.indexOfFirst { it is NotAddedItem.Folder && it.folder == row.key },
        )

    private fun focusOnPage(row: SettingsFolderRow, index: Int) = focusOnLine(row).copy(page = PanelPage.UnknownFolder, panelIndex = index, folderKey = row.key)

    @Test
    fun linesWithTheUnansweredFolderFocused() = ReferenceShots.capture("settings_unknown_folder_lines") { SettingsAt(focusOnLine(xbox), settingsForShot(), content) }

    @Test
    fun linesAnsweredAndSaving() = ReferenceShots.capture("settings_unknown_folder_lines_answered") { SettingsAt(focusOnLine(halo), settingsForShot(), content) }

    @Test
    fun panelOfAnUnansweredFolder() = ReferenceShots.capture("settings_unknown_folder_panel") { SettingsAt(focusOnPage(xbox, index = 3), settingsForShot(), content) }

    @Test
    fun panelOfAnAnsweredFolderMarksTheCurrentSystem() = ReferenceShots.capture("settings_unknown_folder_panel_answered") { SettingsAt(focusOnPage(halo, index = 10), settingsForShot(), content) }

    @Test
    fun panelWithALongName() = ReferenceShots.capture("settings_unknown_folder_panel_long_name") { SettingsAt(focusOnPage(long, index = 1), settingsForShot(), content) }
}
