package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.settings.toThemeSettings
import com.plumemu.report.ProblemReportForm
import com.plumemu.report.ReportItem
import com.plumemu.report.ReportTags
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The faults the last review of the Settings shots found, each pinned by numbers. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsLastReviewTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val stored = PlumSettings(flags = SetupFlags(setupCompleted = true))
    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry
    private var density = Density(1f)

    private class Scene(val focus: SettingsFocus, val content: SettingsContent, val size: TextSize, val settings: PlumSettings, val form: ProblemReportForm?)

    private var scene by mutableStateOf<Scene?>(null)

    private fun open(
        focus: SettingsFocus,
        content: SettingsContent = SettingsContent(),
        size: TextSize = TextSize.Large,
        settings: PlumSettings = stored,
        form: ProblemReportForm? = null,
    ) {
        val isFirst = scene == null
        scene = Scene(focus, content, size, settings, form)
        if (isFirst) {
            compose.setContent {
                val now = scene ?: return@setContent
                PlumTheme(now.settings.toThemeSettings().copy(textSize = now.size)) {
                    key(now) {
                        geometry = settingsGeometry(PlumTheme.layout)
                        density = LocalDensity.current
                        model = remember { SettingsModel(NoActions, now.focus).also { it.content = now.content; now.form?.let(it.report::openWith) } }
                        SettingsLayout(now.settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun press(action: Action) {
        compose.runOnIdle { model.onAction(action, scene!!.settings, geometry) }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun px(dp: Float) = dp * density.density

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun textNode(text: String): SemanticsNode = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().first()

    private fun SemanticsNode.lines(): TextLayoutResult = mutableListOf<TextLayoutResult>().also { config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.first()

    private fun panelRow(
        option: SettingsOption,
        content: SettingsContent = SettingsContent(),
    ): SettingsFocus {
        val section = SettingsSection.entries.first { option in it.rowsIn(content) }
        return SettingsFocus(column = SettingsColumn.Options, section = section, rememberedOption = mapOf(section to section.rowsIn(content).indexOf(option)))
    }

    private fun theLastTextEndsWholeWithNoFade() {
        for (size in listOf(TextSize.Large, TextSize.Largest)) {
            val form = ProblemReportForm(KeyboardChoice.Plumemu)
            var walk = 0
            while (form.focus != ReportItem.ShareLog && walk++ < MAX_WALK) form.onAction(Action.Move(Direction.Down))
            open(panelRow(SettingsOption.ReportProblem).copy(column = SettingsColumn.Panel, page = PanelPage.ProblemReport), size = size, form = form)
            val line = bounds(ReportTags.SHARE_LOG_LINE)
            val panel = bounds(SettingsTags.PANEL)
            assertTrue("$size: Share log's text $line is whole in $panel", line.bottom <= panel.bottom)
            assertTrue(
                "$size: no fade over the form's end",
                compose.onAllNodesWithTag(SettingsTags.ACCOUNT_PANEL + FADE_BOTTOM_SUFFIX, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    @Test
    fun shareLogsTextEndsTheFormWholeAt768() = theLastTextEndsWholeWithNoFade()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun shareLogsTextEndsTheFormWholeAt576() = theLastTextEndsWholeWithNoFade()

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun shareLogsTextEndsTheFormWholeAt832() = theLastTextEndsWholeWithNoFade()

    private fun licenceNamesAreWhole() {
        for (size in listOf(TextSize.Large, TextSize.Largest)) {
            open(panelRow(SettingsOption.Licenses).copy(column = SettingsColumn.Panel, page = PanelPage.Licenses, panelIndex = 0), size = size)
            val top = bounds(SettingsTags.panelItem(0)).top
            val name = textNode(string(LicenseNotices.all.first().name))
            assertTrue("$size: the focused card keeps a row's padding: ${name.boundsInRoot.top} under $top", name.boundsInRoot.top - top >= px(PlumDimens.settingsTwoLineRowPadding.value) - 1f)
            for (i in LicenseNotices.all.indices) {
                val name = compose.onAllNodes(hasText(string(LicenseNotices.all[i].name)) and hasAnyAncestor(hasTestTag(SettingsTags.LICENSE_LIST)), useUnmergedTree = true).fetchSemanticsNodes().first()
                val lines = name.lines()
                assertEquals("$size: ${name.config[SemanticsProperties.Text]} is whole", lines.layoutInput.text.length, lines.getLineEnd(lines.lineCount - 1))
                press(Action.Move(Direction.Down))
            }
        }
    }

    @Test
    fun licenceNamesAreWholeAt768() = licenceNamesAreWhole()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun licenceNamesAreWholeAt576() = licenceNamesAreWhole()

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun aTallCardsFootIsNotCutByTheList() {
        val at = panelRow(SettingsOption.Reset).copy(column = SettingsColumn.Panel, page = PanelPage.Reset, panelIndex = 0)
        open(at, size = TextSize.Largest)
        press(Action.Move(Direction.Down))
        val card = bounds(SettingsTags.panelItem(1))
        val list = bounds(SettingsTags.PANEL_LIST)
        assertTrue("the card $card is whole in the list's window $list", card.top >= list.top && card.bottom <= list.bottom + 1f)
    }

    @Test
    fun aLabelTakesWhatItNeedsAt768() {
        val content = SettingsContent(secondScreen = SecondScreen(2, "Screen-2", ScreenKind.BuiltIn), isSecondScreenOn = true)
        val shown = stored.copy(secondScreen = SecondScreenSettings(enabled = true))
        open(SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.SecondScreen), content, settings = shown)
        assertEquals(1, textNode(string(R.string.settings_second_screen_in_game)).lines().lineCount)
    }

    @Test
    fun theColourCodeLabelStaysOnOneLine() {
        val content = SettingsContent()
        for (size in listOf(TextSize.Large, TextSize.Largest)) {
            run {
                open(colorPageAt(SettingsOption.Accent, stored), content, size)
                assertEquals(size.name, 1, textNode(string(R.string.settings_hex_label)).lines().lineCount)
            }
        }
    }

    @Test
    fun aRowOfTwoLinesKeepsARowsPadding() {
        val content = ShotContent.library
        open(SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Emulators), content)
        val pad = px(PlumDimens.settingsTwoLineRowPadding.value)
        val tall = SettingsSection.Emulators.rowsIn(content).indices.mapNotNull { index ->
            val tag = SettingsTags.system(index)
            val row = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot ?: return@mapNotNull null
            val texts = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
            val top = texts.minOf { it.top }
            val bottom = texts.maxOf { it.bottom }
            if (bottom - top > px(ONE_LINE_DP)) Triple(row, top, bottom) else null
        }
        assertTrue("this case has a row of two lines", tall.isNotEmpty())
        tall.forEach { (row, top, bottom) -> assertTrue("$top..$bottom keeps $pad px from $row", top - row.top >= pad - 1f && row.bottom - bottom >= pad - 1f) }
    }

    private fun stackedButtonsAreOneWidth() {
        run {
            open(panelRow(SettingsOption.ClearRecent).copy(column = SettingsColumn.Panel, page = PanelPage.Confirm, panelIndex = 0), size = TextSize.Largest)
            val cancel = bounds(SettingsTags.panelItem(SettingsModel.CONFIRM_CANCEL))
            val clear = bounds(SettingsTags.panelItem(SettingsModel.CONFIRM_RESET))
            if (cancel.top != clear.top) assertEquals("stacked $cancel and $clear", cancel.width, clear.width, 1f)
        }
    }

    @Test
    fun stackedButtonsAreOneWidthAt768() = stackedButtonsAreOneWidth()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun stackedButtonsAreOneWidthAt576() = stackedButtonsAreOneWidth()

    @Test
    fun titlesAndCrumbsHaveNoDots() {
        open(panelRow(SettingsOption.ClearRecent).copy(column = SettingsColumn.Panel, page = PanelPage.Confirm, panelIndex = 0))
        val shown = compose.onAllNodes(hasAnyAncestor(hasTestTag(SettingsTags.PANEL)) or hasAnyAncestor(hasTestTag(SettingsTags.TOP_BAR)) or hasTestTag(SettingsTags.TOP_BAR), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
        val name = string(R.string.settings_clear_recent)
        assertTrue("the row's name ends in dots here: $name", name.endsWith("…"))
        assertFalse("no title says $name: $shown", shown.any { it.contains(name) })
        assertTrue("the title is there: $shown", shown.any { it == name.trimEnd('…') })
        open(panelRow(SettingsOption.Reset).copy(column = SettingsColumn.Panel, page = PanelPage.Reset, panelIndex = 0))
        val bar = compose.onAllNodes(hasTestTag(SettingsTags.TOP_BAR) or hasAnyAncestor(hasTestTag(SettingsTags.TOP_BAR)), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.joinToString { it.text }
        assertFalse("the crumb has no dots: $bar", bar.contains("…"))
    }

    private fun systemsOrderShares() {
        val systems = listOf("Super Nintendo", "Game Boy Advance").map { LibrarySystem(it, it, 10) }
        val content = SettingsContent(librarySystems = systems)
        for (size in listOf(TextSize.Large, TextSize.Largest)) {
            open(panelRow(SettingsOption.Systems, content).copy(column = SettingsColumn.Panel, page = PanelPage.Systems, panelIndex = FIRST_SYSTEM_ITEM + 1), content, size)
            val label = textNode(string(SettingsOption.SystemOrder.label))
            val value = compose.onNodeWithTag(SettingsTags.valueText(SettingsTags.value(SettingsOption.SystemOrder)), useUnmergedTree = true).fetchSemanticsNode()
            val name = textNode("Super Nintendo")
            assertEquals("$size: label and names, left edge", name.boundsInRoot.left, label.boundsInRoot.left, 1f)
            assertEquals("$size: label and names, one size", name.lines().layoutInput.style.fontSize, label.lines().layoutInput.style.fontSize)
            if (value.boundsInRoot.top >= label.boundsInRoot.bottom - 1f) assertEquals("$size: the value under the label starts where it does", label.boundsInRoot.left, value.boundsInRoot.left, 1f)
        }
    }

    @Test
    fun systemOrderSharesTheNamesEdgeAndSizeAt768() = systemsOrderShares()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun systemOrderSharesTheNamesEdgeAndSizeAt576() = systemsOrderShares()

    private fun sectionsKeepTheirRhythm() {
        run {
            open(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Library), ShotContent.library, TextSize.Largest)
            val labels =
                compose.onAllNodesWithTag(SystemRowTags.LABEL, useUnmergedTree = true).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced && it.boundsInRoot.height > 0f }.map { it.boundsInRoot }.sortedBy { it.top }
            val gaps = labels.zipWithNext { a, b -> b.top - a.bottom }
            assertTrue("labels $labels", gaps.isNotEmpty())
            assertTrue("label gaps $gaps", gaps.max() - gaps.min() <= px(2f))
        }
    }

    @Test
    fun sectionsKeepTheirRhythmAt768() = sectionsKeepTheirRhythm()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun sectionsKeepTheirRhythmAt576() = sectionsKeepTheirRhythm()

    @Test
    @Config(qualifiers = "vi-w576dp-h432dp-land-xhdpi")
    fun sectionsKeepTheirRhythmInVietnamese() = sectionsKeepTheirRhythm()

    @Test
    @Config(qualifiers = "ja-w576dp-h432dp-land-xhdpi")
    fun sectionsKeepTheirRhythmInJapanese() = sectionsKeepTheirRhythm()

    private companion object {
        const val MAX_WALK = 12
        const val ONE_LINE_DP = 30f
    }
}
