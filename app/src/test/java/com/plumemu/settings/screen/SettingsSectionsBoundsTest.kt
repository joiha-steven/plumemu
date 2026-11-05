package com.plumemu.settings.screen

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.assertNoTextCut
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Library and Emulators' positions against the board (RP5-S1-settings pages library and emulators):
 * folder cards 2 apart filling the 248 panel, rows 42 apart like every option. Panel rows are 40
 * high, not the board's 34:.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsSectionsBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(focus: SettingsFocus) = compose.setContent { PlumTheme(TableText.Graphite) { SettingsAt(focus, content = ShotContent.library) } }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot()

    @Test
    fun folderCardsFillThePanelTwoApartWithAddFolderAfterThem() {
        show(optionsAt(SettingsOption.RomFolders))
        // Three columns: the right column is 538.7..744 (205.3 wide).
        compose.onNodeWithTag(SettingsTags.panelItem(0)).assertLeftPositionInRootIsEqualTo(538.67.dp).assertWidthIsEqualTo(205.33.dp)
        val first = bounds(SettingsTags.panelItem(0))
        val second = bounds(SettingsTags.panelItem(1))
        val add = bounds(SettingsTags.panelItem(2))
        assertEquals("cards 2 apart", 2f, (second.top - first.bottom).value, 0.01f)
        assertEquals(2f, (add.top - second.bottom).value, 0.01f)
        assertEquals("both cards one height", (first.bottom - first.top).value, (second.bottom - second.top).value, 0.01f)
        compose.onNodeWithTag(SettingsTags.panelItem(2)).assertLeftPositionInRootIsEqualTo(538.67.dp).assertHeightIsEqualTo(40.dp)
        // Top 14 + bar 23 (grows with the place name line) + gap 10, as every section.
        assertEquals(47f, bounds(SettingsTags.option(SettingsOption.RomFolders)).top.value, 0.01f)
    }

    @Test
    fun systemRowsAreOptionRowsAndTheirEmulatorsListRowsInThePanel() {
        show(SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Emulators, rememberedOption = mapOf(SettingsSection.Emulators to ShotContent.PSX_ROW)))
        compose.onNodeWithTag(SettingsTags.system(0)).assertLeftPositionInRootIsEqualTo(206.dp).assertWidthIsEqualTo(314.67.dp).assertHeightIsEqualTo(40.dp)
        assertEquals("rows 42 apart", 42f, (bounds(SettingsTags.system(1)).top - bounds(SettingsTags.system(0)).top).value, 0.01f)
        compose.onNodeWithTag(SettingsTags.panelItem(0)).assertLeftPositionInRootIsEqualTo(538.67.dp).assertWidthIsEqualTo(205.33.dp).assertHeightIsEqualTo(40.dp)
        assertEquals(42f, (bounds(SettingsTags.panelItem(1)).top - bounds(SettingsTags.panelItem(0)).top).value, 0.01f)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrowFoldersStopTakesTheOptionsWidth() {
        show(panelAt(SettingsOption.RomFolders, PanelPage.Folders, 0))
        compose.onNodeWithTag(SettingsTags.panelItem(0)).assertLeftPositionInRootIsEqualTo(24.dp).assertWidthIsEqualTo(528.dp)
    }

    /** The emulator panel of a system whose chosen emulator has the game panel's longest name: no text cut beside the badge. */
    private fun assertEmulatorPanelTextNotCut(what: String, wraps: Boolean = false) {
        val long = EmulatorEntry("retroarch-mednafen-psx", "RetroArch (Beetle PSX)", isLegalRisk = false)
        val duck = EmulatorEntry("duckstation", "DuckStation", isLegalRisk = false)
        val psx = SystemEmulators("psx", "PlayStation", automatic = duck, chosen = long, installed = listOf(duck, long))
        val content = ShotContent.library.copy(systems = listOf(psx))
        val focus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Emulators, rememberedOption = mapOf(SettingsSection.Emulators to 0), page = PanelPage.Emulators, panelIndex = 0, systemId = "psx")
        compose.setContent { PlumTheme(TableText.Graphite) { SettingsAt(focus, content = content) } }
        var seen = 0
        for (index in 0..2) {
            val root = compose.onNodeWithTag(SettingsTags.panelItem(index), useUnmergedTree = true).fetchSemanticsNode()
            // The 205 dp right column (768) wraps a long name over lines, never an ellipsis; the wider 576 panel keeps it on one line.
            seen += if (wraps) assertTextWhole(root, what) else assertNoTextCut(root, what)
        }
        assertTrue("$what: the items have text", seen > 0)
    }

    @Test
    fun emulatorPanelNamesAndTheCurrentBadgeAreNotCutAt768() = assertEmulatorPanelTextNotCut("768", wraps = true)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun emulatorPanelNamesAndTheCurrentBadgeAreNotCutAt576() = assertEmulatorPanelTextNotCut("576")

    /** Every text under [root] is drawn whole: not ellipsized, no overflow. Returns how many texts it saw. */
    private fun assertTextWhole(root: SemanticsNode, what: String): Int {
        var seen = 0
        fun visit(node: SemanticsNode) {
            if (node.layoutInfo.isPlaced && SemanticsProperties.Text in node.config) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val layout = layouts.first()
                val natural = layout.multiParagraph.intrinsics.maxIntrinsicWidth
                val whole = (0 until layout.lineCount).none { layout.isLineEllipsized(it) } && (layout.lineCount > 1 || natural <= node.boundsInRoot.width + 1f)
                assertTrue("$what: '${node.config[SemanticsProperties.Text].joinToString()}' is cut (${layout.lineCount} lines)", whole)
                seen++
            }
            node.children.forEach(::visit)
        }
        visit(root)
        return seen
    }
}
