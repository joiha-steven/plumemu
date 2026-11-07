package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.components.FieldTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The panels at Largest on 768x432, both themes: a field keeps its width, a focused button and the text over it stand whole, a tall row is never one stray line under the top
 * fade, a wrapped line never starts with the dot, and a value that fits one line beside a two-line label does not wrap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsLargestRestsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = PlumSettings(flags = SetupFlags(setupCompleted = true))
    private var theme by mutableStateOf(PlumThemeSettings.Graphite)
    private var model by mutableStateOf<SettingsModel?>(null)
    private lateinit var geometry: SettingsGeometry

    private fun inBothThemes(check: () -> Unit) {
        compose.setContent {
            PlumTheme(theme.copy(textSize = TextSize.Largest)) {
                geometry = settingsGeometry(PlumTheme.layout)
                model?.let { key(it) { SettingsLayout(settings, it, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY }) } }
            }
        }
        for (each in listOf(PlumThemeSettings.Graphite, PlumThemeSettings.Paper)) {
            theme = each
            check()
        }
    }

    private fun onRow(
        option: SettingsOption,
        index: Int = -1,
    ) {
        val section = SettingsSection.shownIn(largestContent).first { option in it.rowsIn(largestContent) }
        val row = if (index >= 0) index else section.rowsIn(largestContent).indexOf(option)
        model = SettingsModel(NoActions, SettingsFocus(column = SettingsColumn.Options, section = section, rememberedOption = mapOf(section to row))).also { it.content = largestContent }
        compose.waitForIdle()
    }

    private fun press(action: Action) {
        compose.runOnIdle { model!!.onAction(action, settings, geometry) }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertWholeInPanel(tag: String) {
        val panel = bounds(SettingsTags.PANEL)
        val item = bounds(tag)
        assertTrue("$tag from ${item.top} to ${item.bottom} in ${panel.top}..${panel.bottom}", item.top >= panel.top && item.bottom <= panel.bottom)
    }

    private fun lines(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single()
    }

    private fun assertNoLineStartsWithTheDot(node: SemanticsNode) {
        val text = lines(node)
        for (line in 1 until text.lineCount) assertFalse("line $line of \"${text.layoutInput.text}\"", text.layoutInput.text.substring(text.getLineStart(line)).startsWith("·"))
    }

    @Test
    fun theReportFieldsKeepTheWidthOfThePanel() =
        inBothThemes {
            onRow(SettingsOption.ReportProblem)
            press(Action.Enter)
            val panel = bounds(SettingsTags.PANEL)
            val width = (panel.right - panel.left).value
            val fields = compose.onAllNodesWithTag(FieldTags.BOX, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(2, fields.size)
            for (field in fields) assertTrue("a field ${field.size.width} px wide in a $width dp panel", field.size.width / DENSITY >= width * 0.9f)
        }

    @Test
    fun clearRecentShowsItsTextAndBothButtonsWhole() =
        inBothThemes {
            onRow(SettingsOption.ClearRecent)
            val steps = listOf<Action?>(null, Action.Enter, Action.Move(Direction.Right))
            for (step in steps) {
                step?.let(::press)
                assertWholeInPanel(SettingsTags.panelItem(SettingsModel.CONFIRM_CANCEL))
                assertWholeInPanel(SettingsTags.panelItem(SettingsModel.CONFIRM_RESET))
                val body = compose.onNode(hasText("Recent is emptied", substring = true), useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertTrue("the text starts at ${body.top}, under the panel's top ${bounds(SettingsTags.PANEL).top}", body.top >= bounds(SettingsTags.PANEL).top)
                compose.onNodeWithTag(CONFIRM_PANEL + "_fade_top", useUnmergedTree = true).assertDoesNotExist()
                compose.onNodeWithTag(CONFIRM_PANEL + "_fade_bottom", useUnmergedTree = true).assertDoesNotExist()
            }
        }

    private fun assertNoStrayLine(what: String) {
        val list = bounds(SettingsTags.PANEL_LIST)
        val rows = (0 until MAX_ROWS).mapNotNull { i -> compose.onAllNodesWithTag(SettingsTags.panelItem(i), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() }
        for (row in rows) {
            val top = row.positionInRoot.y / DENSITY
            val bottom = top + row.size.height / DENSITY
            val isTall = bottom - top >= TALL_ROW_DP
            if (isTall && top < list.top.value && bottom > list.top.value) {
                // Under a third of a row is left out at rest, so that much does not show.
                val shown = bottom - list.top.value
                assertTrue("$what: $shown dp of a row ${bottom - top} dp tall shows under the top fade", shown < (bottom - top) / 3f || shown >= 2 * EdgePeek.value)
            }
        }
    }

    private fun walksDownWithNoStrayLine(what: String) {
        press(Action.Enter)
        assertNoStrayLine("$what, entered")
        var steps = 0
        while (steps++ < MAX_ROWS) {
            val before = model!!.focus.panelIndex
            press(Action.Move(Direction.Down))
            if (model!!.focus.panelIndex == before) break
            assertWholeInPanel(SettingsTags.panelItem(model!!.focus.panelIndex))
            assertNoStrayLine("$what, on row ${model!!.focus.panelIndex}")
        }
        assertTrue("$what walks", steps > 1)
    }

    @Test
    fun aTallRowIsNeverOneLineUnderTheTopFade() =
        inBothThemes {
            onRow(SettingsOption.FilesNotAdded)
            walksDownWithNoStrayLine("Files not added")
            onRow(SettingsOption.SystemEmulator, index = PLAYSTATION_ROW)
            walksDownWithNoStrayLine("PlayStation")
        }

    @Test
    fun anAccountWhoseOnlyButtonFitsDoesNotScroll() =
        inBothThemes {
            onRow(SettingsOption.RetroAchievementsAccount)
            press(Action.Enter)
            compose.onNodeWithTag(SettingsTags.ACCOUNT_PANEL + "_fade_top", useUnmergedTree = true).assertDoesNotExist()
            assertWholeInPanel(SettingsTags.panelItem(0))
            assertTrue("the title stands whole above the status line", bounds(SettingsPanelTags.STATUS).top - bounds(SettingsTags.PANEL).top >= TITLE_DP)
        }

    @Test
    fun aWrappedLineNeverStartsWithTheDot() =
        inBothThemes {
            onRow(SettingsOption.RetroAchievementsAccount)
            press(Action.Enter)
            assertNoLineStartsWithTheDot(compose.onNodeWithTag(SettingsPanelTags.STATUS, useUnmergedTree = true).fetchSemanticsNode())
            onRow(SettingsOption.Updates)
            press(Action.Enter)
            assertNoLineStartsWithTheDot(compose.onNode(hasText("Released", substring = true), useUnmergedTree = true).fetchSemanticsNode())
        }

    @Test
    fun noRowOfSystemsWrapsItsValue() =
        inBothThemes {
            onRow(SettingsOption.SystemEmulator, index = 1)
            val values = compose.onAllNodes(hasText("Ask on the first game"), useUnmergedTree = true).fetchSemanticsNodes().filter { it.positionInRoot.x / DENSITY < OPTIONS_END_DP }
            assertEquals(largestContent.systems.size, values.size)
            for (value in values) assertEquals("a value of one line", 1, lines(value).lineCount)
        }

    private companion object {
        const val CONFIRM_PANEL = "settings_confirm_panel"
        const val DENSITY = 2f
        val TITLE_DP = 30.dp
        const val MAX_ROWS = 8
        const val TALL_ROW_DP = 66f
        const val PLAYSTATION_ROW = 2
        const val OPTIONS_END_DP = 480f
    }
}
