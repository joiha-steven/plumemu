package com.plumemu.textsize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsColumn
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsPanelTags
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.setup.SetupFolder
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › ROM folders › a folder and Skip a sub-folder… at every text size, as
 * `SettingsUnknownFolderFitTest` checks the unknown folders: no text ellipsized, clipped, too short or
 * on top of another, and the focused item fully on screen, with long sub-folder names.
 */
abstract class SettingsFolderPageFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val sd = SetupFolder(2, "content://com.android.externalstorage.documents/tree/1234-ABCD%3AGames", "Games", 253, 4, isCounted = true)
    private val skipped = listOf("Hacks", "Game Boy Advance/Translations", "_unsorted")
    private val listed = listOf("Hacks", "Homebrew", "Romhacks and translations from the old drive", "Translations", "gba")
    private val content = SettingsContent(folders = listOf(sd), skippedSubfolders = mapOf(sd.id to skipped))

    private fun focus(page: PanelPage, index: Int): SettingsFocus =
        SettingsFocus(
            column = SettingsColumn.Panel,
            section = SettingsSection.Library,
            rememberedOption = mapOf(SettingsSection.Library to SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.RomFolders)),
            page = page,
            panelIndex = index,
            folderId = sd.id,
        )

    private fun check(
        name: String,
        focused: SettingsFocus,
        tagOfFocused: String,
        subfolders: Result<List<String>?>?,
    ) {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { Screen(focused, subfolders) } }
        val combinations =
            TextSize.entries.map { PlumFont.Sans to it } +
                listOf(PlumFont.Serif, PlumFont.Mono).flatMap { listOf(it to TextSize.Large, it to TextSize.Largest) }
        val failures = mutableListOf<String>()
        for ((eachFont, each) in combinations) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val label = "$name at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            val allowance =
                FitAllowance(
                    setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, SettingsTags.PANEL_LIST, SettingsPanelTags.FOLDER_PAGE, SettingsPanelTags.SKIP_SUBFOLDER_PAGE),
                    isLargest = each == TextSize.Largest,
                )
            textProblems(root, allowance).mapTo(failures) { "$label: $it" }
            val item = findTagged(root, tagOfFocused)
            if (item == null) failures += "$label: the focused item is not drawn" else notFullyShown(item, root)?.let { failures += "$label: the focused item $it" }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    @Composable
    private fun Screen(
        focused: SettingsFocus,
        subfolders: Result<List<String>?>?,
    ) = SettingsAt(focused, content = content, subfolders = subfolders)

    @Test
    fun theFolderPageOnItsLastItem() = check("folder_page", focus(PanelPage.Folder, 4), SettingsTags.panelItem(4), null)

    @Test
    fun theFolderPageOnALongSkippedPath() = check("folder_page_skipped", focus(PanelPage.Folder, 2), SettingsTags.panelItem(2), null)

    @Test
    fun skipASubfolderOnALongName() = check("skip_subfolder", focus(PanelPage.SkipSubfolder, 1), SettingsTags.panelItem(1), Result.success(listed))

    @Test
    fun skipASubfolderUnreadable() = check("skip_subfolder_unreadable", focus(PanelPage.SkipSubfolder, 0), SettingsPanelTags.SKIP_SUBFOLDER_PAGE, Result.success(null))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsFolderPageFit768Test : SettingsFolderPageFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsFolderPageFit576Test : SettingsFolderPageFitBase(576, 432)
