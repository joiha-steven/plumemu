package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › ROM folders › a folder: its page with skipped sub-folders, and Skip a sub-folder… listed, reading and unreadable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsFolderPageScreenshotTest {
    private val content = ShotContent.library.copy(skippedSubfolders = mapOf(2L to listOf("Hacks", "gba/_unsorted")))

    private fun at(page: PanelPage, index: Int) = panelAt(SettingsOption.RomFolders, page, index).copy(folderId = 2)

    @Test
    fun theFolderPageWithTwoSkipped() = ReferenceShots.capture("settings_folder_page") { SettingsAt(at(PanelPage.Folder, 1), content = content) }

    @Test
    fun skipASubfolderListed() =
        ReferenceShots.capture("settings_skip_subfolder") {
            SettingsAt(at(PanelPage.SkipSubfolder, 1), content = content, subfolders = Result.success(listOf("Hacks", "Homebrew", "Translations", "gba")))
        }

    @Test
    fun skipASubfolderReading() = ReferenceShots.capture("settings_skip_subfolder_reading") { SettingsAt(at(PanelPage.SkipSubfolder, 0), content = content) }

    @Test
    fun skipASubfolderUnreadable() =
        ReferenceShots.capture("settings_skip_subfolder_unreadable") { SettingsAt(at(PanelPage.SkipSubfolder, 0), content = content, subfolders = Result.success(null)) }
}
