package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.setup.SetupFolder
import com.plumemu.textsize.FitAllowance
import com.plumemu.textsize.findTagged
import com.plumemu.textsize.notFullyShown
import com.plumemu.textsize.textProblems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Library › Folder could not be read on screen at 768x432 and 576x432: the row shows only while a folder has unreadable
 * sub-folders and goes when a scan read them; a finger reaches Skip this folder and Scan again; the panel measured in Graphite
 * and Paper (no text cut or overlapping, items on one left edge with even gaps, at least a row tall, the focused one whole).
 */
abstract class SettingsUnreadableFoldersScreenBase(
    private val isNarrow: Boolean,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val geometry = SettingsGeometry(isNarrow = isNarrow, presetColumns = if (isNarrow) 10 else 5, defaultTextSize = TextSizeSetting.Medium)

    private var theme by mutableStateOf(TableText.Graphite)

    private val roms = SetupFolder(1, "content://tree/1", "ROMs", 120, 3, isCounted = true, unreadablePaths = listOf("psx"))
    private val games =
        SetupFolder(2, "content://tree/2", "Games", 40, 2, isCounted = true, unreadablePaths = listOf("Game Boy Advance/Translations from the old drive", "snes"))

    private class Recording : SettingsActions by NoActions {
        val skips = mutableListOf<Triple<Long, String, Boolean>>()
        val answers = mutableListOf<(Boolean) -> Unit>()
        var rescans = 0

        override fun skipSubfolder(
            folderId: Long,
            relativePath: String,
            skip: Boolean,
            onDone: (Boolean) -> Unit,
        ) {
            skips += Triple(folderId, relativePath, skip)
            answers += onDone
        }

        override fun rescanLibrary(): Boolean {
            rescans++
            return true
        }
    }

    private val actions = Recording()
    private val settings = PlumSettings()

    private fun host(
        focus: SettingsFocus,
        content: SettingsContent,
    ): SettingsModel {
        val model = SettingsModel(actions, focus)
        model.content = content.withGamesListed()
        val touch =
            SettingsTouch(
                { model.onTapSection(it) },
                { model.onTapOption(it, settings, geometry) },
                { model.onTapValue(it, settings, geometry) },
                { model.onTapPanelItem(it, settings, geometry) },
                {},
                {},
                {},
            )
        compose.setContent { PlumTheme(theme) { SettingsLayout(settings, model, touch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" }) } }
        compose.waitForIdle()
        return model
    }

    private fun libraryOptions(content: SettingsContent, option: SettingsOption) =
        SettingsFocus(
            column = SettingsColumn.Options,
            section = SettingsSection.Library,
            rememberedOption = mapOf(SettingsSection.Library to SettingsSection.Library.rowsIn(content.withGamesListed()).indexOf(option)),
        )

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    @Test
    fun theRowShowsOnlyWhileAFolderCouldNotBeRead() {
        val content = SettingsContent(folders = listOf(roms))
        val model = host(libraryOptions(content, SettingsOption.Rescan), content)
        val row = SettingsTags.option(SettingsOption.UnreadableFolders)
        assertTrue("listed while psx could not be read", exists(row))
        val label = context.getString(R.string.settings_unreadable_row)
        assertTrue(compose.onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("its value names the folder", compose.onAllNodes(hasText("psx", substring = true) and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.runOnIdle { model.content = SettingsContent(folders = listOf(roms.copy(unreadablePaths = emptyList()))).withGamesListed() }
        compose.waitForIdle()
        assertTrue("gone once a scan read every sub-folder", !exists(row))
    }

    @Test
    fun aFingerReachesSkipThisFolderAndScanAgain() {
        val content = SettingsContent(folders = listOf(roms))
        val model = host(libraryOptions(content, SettingsOption.RomFolders), content)
        val row = SettingsTags.option(SettingsOption.UnreadableFolders)
        tap(row)
        // Wide: the panel shows beside the focused row; narrow: a second tap opens it as a further stop.
        if (isNarrow) tap(row)
        assertTrue("the panel is on screen", exists(SettingsPanelTags.UNREADABLE_FOLDERS))
        repeat(2) { if (actions.skips.isEmpty()) tap(SettingsTags.panelItem(1)) }
        assertEquals(listOf(Triple(1L, "psx", true)), actions.skips)
        repeat(2) { if (actions.rescans == 0) tap(SettingsTags.panelItem(0)) }
        assertEquals(1, actions.rescans)
        assertEquals("the cursor waits on Rescan library, where the scan shows", SettingsOption.Rescan, model.focusedOption)
        assertNull(model.focus.page)
    }

    /**
     * The last folder's skip that could not be saved: while it is saved the graph lists it as being skipped, then the answer
     * says no and it is listed again. The failure is said where the person acted, on screen.
     */
    @Test
    fun aSkipThatCouldNotBeSavedIsSaidOnScreen() {
        val content = SettingsContent(folders = listOf(roms))
        val focus = libraryOptions(content, SettingsOption.UnreadableFolders).copy(column = SettingsColumn.Panel, page = PanelPage.UnreadableFolders, panelIndex = 1)
        val model = host(focus, content)
        repeat(2) { if (actions.skips.isEmpty()) tap(SettingsTags.panelItem(1)) }
        assertEquals(listOf(Triple(1L, "psx", true)), actions.skips)
        compose.runOnIdle { model.content = content.copy(skippingFolders = setOf(FolderKey(1, "psx"))).withGamesListed() }
        compose.waitForIdle()
        compose.runOnIdle {
            actions.answers.single()(false)
            model.content = content.withGamesListed()
        }
        compose.waitForIdle()
        val failed = context.getString(R.string.settings_folder_skip_failed)
        assertTrue(
            "the failure is on screen",
            compose.onAllNodes(hasText(failed, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
        assertEquals("still on the panel where the person acted", PanelPage.UnreadableFolders, model.focus.page)
    }

    @Test
    fun thePanelMeasuresCleanInBothThemes() {
        val content = SettingsContent(folders = listOf(roms, games))
        val base = libraryOptions(content, SettingsOption.UnreadableFolders)
        host(base.copy(column = SettingsColumn.Panel, page = PanelPage.UnreadableFolders, panelIndex = 2), content)
        val title = context.getString(R.string.settings_unreadable_title_many)
        for ((name, shown) in listOf("Graphite" to TableText.Graphite, "Paper" to TableText.Paper)) {
            theme = shown
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val allowance = FitAllowance(setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, SettingsTags.PANEL_LIST, SettingsPanelTags.UNREADABLE_FOLDERS), isLargest = false)
            assertEquals("$name: text problems", emptyList<String>(), textProblems(root, allowance))
            val focused = checkNotNull(findTagged(root, SettingsTags.panelItem(2))) { "$name: the focused item is not drawn" }
            assertNull("$name: the focused item", notFullyShown(focused, root))
            val shownItems = (0..3).filter { exists(SettingsTags.panelItem(it)) }
            val items = shownItems.map { compose.onNodeWithTag(SettingsTags.panelItem(it), useUnmergedTree = true).getUnclippedBoundsInRoot() }
            assertTrue("$name: at least Scan again and two folders are laid out, got ${items.size}", items.size >= 3)
            val heading =
                compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag(SettingsPanelTags.UNREADABLE_FOLDERS)), useUnmergedTree = true).getBoundsInRoot()
            val itemTexts =
                listOf(context.getString(R.string.settings_unreadable_scan_again), "ROMs/psx", "Games/Game Boy Advance/Translations from the old drive").map { text ->
                    compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(SettingsPanelTags.UNREADABLE_FOLDERS)), useUnmergedTree = true).getUnclippedBoundsInRoot()
                }
            val skipLines =
                compose.onAllNodes(hasText(context.getString(R.string.settings_unreadable_skip)), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.left }
            println("UNREADABLE ${if (isNarrow) 576 else 768}x432 $name: title left ${heading.left}, item text lefts ${itemTexts.map { it.left }}, skip lines px ${skipLines}")
            items.forEach { assertEquals("$name: every item box starts on one edge", items[0].left.value, it.left.value, 0.51f) }
            assertEquals("$name: the two folders' names start on one edge", itemTexts[1].left.value, itemTexts[2].left.value, 0.51f)
            items.forEach { assertTrue("$name: every item is at least a row tall, got ${it.bottom - it.top}", (it.bottom - it.top).value >= PlumDimens.rowHeight.value - 0.01f) }
            val gaps = items.zipWithNext { a, b -> (b.top - a.bottom).value }
            assertTrue("$name: items do not overlap, gaps $gaps", gaps.all { it >= -0.01f })
            if (gaps.size >= 2) assertEquals("$name: the folders' items are evenly spaced, gaps $gaps", gaps[1], gaps.last(), 0.51f)
            val panel = bounds(SettingsPanelTags.UNREADABLE_FOLDERS)
            items.forEach { assertTrue("$name: item right ${it.right} inside the panel ${panel.right}", it.right.value <= panel.right.value + 0.51f) }
            println("UNREADABLE ${if (isNarrow) 576 else 768}x432 $name: title left ${heading.left}, items ${items.map { "${it.left}..${it.right} ${it.top}-${it.bottom}" }}, gaps $gaps, panel ${panel.left}..${panel.right}")
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsUnreadableFolders768Test : SettingsUnreadableFoldersScreenBase(isNarrow = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsUnreadableFolders576Test : SettingsUnreadableFoldersScreenBase(isNarrow = true)
