package com.plumemu.textsize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.graph.ScrapeCounts
import com.plumemu.graph.ScrapeStatus
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.AccountShotContent
import com.plumemu.settings.screen.ClearConfirmItem
import com.plumemu.settings.screen.FolderAnswer
import com.plumemu.settings.screen.FolderKey
import com.plumemu.settings.screen.FolderSystemChoice
import com.plumemu.settings.screen.NotAddedItem
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsColumn
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsFolderRow
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.SystemsShots
import com.plumemu.settings.screen.boxArtPanelEntries
import com.plumemu.settings.screen.notAddedItems
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Library › Files not added and an unknown folder's page, System order on top of the Systems panel, and Box art's
 * panel and Clear confirm with no account, at every text size, as `SettingsBackupFitTest`
 * checks Backup: the six sizes in Plum Sans and Large and Largest in Serif and Mono, no text ellipsized (but a folder's own
 * very long name in the row label, which wraps to two lines and ends in an ellipsis by design), clipped, too short or on top
 * of another, and the focused item fully on screen.
 */
abstract class SettingsUnknownFolderFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val xbox = SettingsFolderRow(FolderKey(1, "Xbox 360"), 1234, FolderAnswer.Unanswered)
    private val halo = SettingsFolderRow(FolderKey(2, "Halo"), 0, FolderAnswer.OfSystem("dc", "Sega Dreamcast and Naomi"))
    private val long = SettingsFolderRow(FolderKey(1, "Games from my old drive"), 1, FolderAnswer.Unanswered)
    private val systems =
        listOf("3DO", "Atari 2600", "Nintendo Entertainment System", "Super Nintendo Entertainment System", "Sega Dreamcast and Naomi").map { FolderSystemChoice(it.lowercase(), it) }
    private val content = SettingsContent(unknownFolders = listOf(xbox, long, halo), folderSystemChoices = systems)

    private fun focus(row: SettingsFolderRow, index: Int): SettingsFocus =
        SettingsFocus(
            column = SettingsColumn.Panel,
            section = SettingsSection.Library,
            rememberedOption = mapOf(SettingsSection.Library to SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.FilesNotAdded)),
            page = PanelPage.UnknownFolder,
            panelIndex = index,
            folderKey = row.key,
        )

    private fun check(name: String, focused: SettingsFocus, tagOfFocused: String, content: SettingsContent = this.content) {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { SettingsAt(focused, content = content) } }
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
            val allowance = FitAllowance(setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, SettingsTags.PANEL_LIST, SettingsTags.UNKNOWN_FOLDER_PANEL, SettingsTags.SYSTEMS_LIST, SettingsTags.ACCOUNT_PANEL), isLargest = each == TextSize.Largest)
            textProblems(root, allowance).mapTo(failures) { "$label: $it" }
            val item = findTagged(root, tagOfFocused)
            if (item == null) failures += "$label: the focused item is not drawn" else notFullyShown(item, root)?.let { failures += "$label: the focused item $it" }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun thePanelOfAnUnansweredFolderWithManyFiles() = check("unknown_panel", focus(xbox, 2), SettingsTags.panelItem(2))

    @Test
    fun thePanelOfAnAnsweredFolderWithALongSystemName() = check("unknown_panel_answered", focus(halo, 5), SettingsTags.panelItem(5))

    @Test
    fun aVeryLongFolderNameInItsPanel() = check("unknown_long_name", focus(long, 0), SettingsTags.panelItem(0))

    private val lines = content.copy(notAdded = notAddedItems(emptyList(), content.unknownFolders))

    private fun filesNotAdded(column: SettingsColumn, page: PanelPage?, index: Int) =
        SettingsFocus(column = column, section = SettingsSection.Library, rememberedOption = mapOf(SettingsSection.Library to SettingsSection.Library.rowsIn(lines).indexOf(SettingsOption.FilesNotAdded)), page = page, panelIndex = index)

    @Test
    fun theRowSaysHowManyFoldersWait() = check("files_row_waiting", filesNotAdded(SettingsColumn.Options, null, 0), SettingsTags.value(SettingsOption.FilesNotAdded), lines)

    @Test
    fun anAnsweredFoldersLine() {
        val line = lines.notAdded.indexOfLast { it is NotAddedItem.Folder }
        check("files_answered_line", filesNotAdded(SettingsColumn.Panel, PanelPage.FilesNotAdded, line), SettingsTags.panelItem(line), lines)
    }

    @Test
    fun systemOrderOnTopOfTheSystemsPanel() {
        val systems = SettingsContent(librarySystems = SystemsShots.systems)
        val row = SettingsSection.Library.rowsIn(systems).indexOf(SettingsOption.Systems)
        val focus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Library, rememberedOption = mapOf(SettingsSection.Library to row), page = PanelPage.Systems)
        check("system_order_item", focus, SettingsTags.option(SettingsOption.SystemOrder), systems)
    }

    private fun boxArt(source: BoxArtSourceSetting, page: PanelPage, index: Int): Pair<SettingsContent, SettingsFocus> {
        val shown = AccountShotContent.of(AccountStatus.SignedOut).copy(chosenBoxArtSource = source, boxArt = ScrapeStatus.Idle(ScrapeCounts(300, 412, 0)))
        return shown to AccountShotContent.focusOn(shown, SettingsOption.BoxArtSource, page).copy(panelIndex = index)
    }

    @Test
    fun boxArtsLastItemWithNoAccount() {
        val last = boxArtPanelEntries(boxArt(BoxArtSourceSetting.LibretroOnly, PanelPage.BoxArtSource, 0).first).lastIndex
        val (shown, focus) = boxArt(BoxArtSourceSetting.LibretroOnly, PanelPage.BoxArtSource, last)
        check("box_art_last_item", focus, SettingsTags.panelItem(last), shown)
    }

    @Test
    fun clearBoxArtsConfirmUnderBoxArtOnly() {
        val (shown, focus) = boxArt(BoxArtSourceSetting.LibretroOnly, PanelPage.ScraperClearConfirm, ClearConfirmItem.Cancel.ordinal)
        check("box_art_clear_libretro", focus, SettingsTags.panelItem(ClearConfirmItem.Cancel.ordinal), shown)
    }

    @Test
    fun clearBoxArtsConfirmUnderOwnCovers() {
        val (shown, focus) = boxArt(BoxArtSourceSetting.OwnCovers, PanelPage.ScraperClearConfirm, ClearConfirmItem.Cancel.ordinal)
        check("box_art_clear_own", focus, SettingsTags.panelItem(ClearConfirmItem.Cancel.ordinal), shown)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsUnknownFolderFit768Test : SettingsUnknownFolderFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsUnknownFolderFit576Test : SettingsUnknownFolderFitBase(576, 432)
