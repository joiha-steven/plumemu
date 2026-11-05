package com.plumemu.settings.screen

import com.plumemu.coredata.db.LeftOutFileEntity
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Library › Files not added: the row, the filled page, a long name and path, and the empty page, at 768x432 and 576x432. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsFilesNotAddedScreenshotTest {
    private val xbox = SettingsFolderRow(FolderKey(1, "Xbox 360"), 3, FolderAnswer.Unanswered)

    private fun row(
        path: String?,
        reason: String,
        system: String? = null,
        directory: String = path?.substringBeforeLast('/', "") ?: "",
        more: Int = 0,
    ) = LeftOutFileEntity(folderId = 1, directoryPath = directory, relativePath = path, reason = reason, systemId = system, more = more)

    private val rows =
        listOf(
            row("Xbox 360/Halo 3.iso", "no_system"),
            row("Xbox 360/Gears.iso", "no_system"),
            row("Xbox 360/Fable.iso", "no_system"),
            row("Mystery.iso", "no_system"),
            row("PSX/setup.exe", "unmatched", "psx"),
            row("PSX/scph1001.bin", "bios", "psx"),
            row("SNES/Super Mario World (USA).srm", "save", "snes"),
            row("SNES/Super Mario World (USA).state1", "save", "snes"),
            row("readme.txt", "junk"),
            row(null, "junk", directory = "", more = 340),
        )
    private val longRows =
        listOf(
            row(
                "Roms from the old external drive (backup 2019)/PlayStation/Disc images/Final Fantasy VII (USA) (Disc 1) (Rev 1) (Greatest Hits Edition).sav",
                "save",
                "psx",
            ),
            row("PSX/scph1001.bin", "bios", "psx"),
        )

    private fun content(rows: List<LeftOutFileEntity>) =
        ShotContent.library.copy(
            unknownFolders = listOf(xbox),
            notAdded = notAddedItems(rows, listOf(xbox)),
            folderSystemChoices = listOf(FolderSystemChoice("psx", "PlayStation"), FolderSystemChoice("snes", "Super Nintendo")),
        )

    private fun focus(
        content: SettingsContent,
        panel: Boolean,
        index: Int = 0,
    ): SettingsFocus {
        val at = SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.FilesNotAdded)
        return SettingsFocus(
            column = if (panel) SettingsColumn.Panel else SettingsColumn.Options,
            section = SettingsSection.Library,
            rememberedOption = mapOf(SettingsSection.Library to at),
            page = if (panel) PanelPage.FilesNotAdded else null,
            panelIndex = index,
        )
    }

    @Test
    fun rowWithItsCount() = content(rows).let { c -> ReferenceShots.capture("settings_files_not_added_row") { SettingsAt(focus(c, panel = false), settingsForShot(), c) } }

    @Test
    fun pageFilledOnTheFolderLine() = content(rows).let { c -> ReferenceShots.capture("settings_files_not_added_page") { SettingsAt(focus(c, panel = true, index = 1), settingsForShot(), c) } }

    @Test
    fun pageWithALongNameAndPath() = content(longRows).let { c -> ReferenceShots.capture("settings_files_not_added_long_name") { SettingsAt(focus(c, panel = true, index = 1), settingsForShot(), c) } }

    @Test
    fun pageEmpty() = content(emptyList()).let { c -> ReferenceShots.capture("settings_files_not_added_empty") { SettingsAt(focus(c, panel = true), settingsForShot(), c) } }
}
