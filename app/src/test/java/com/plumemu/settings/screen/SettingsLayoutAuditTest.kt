package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings layout items measured by bounds at 768x432 and
 * 576x432, Graphite and Paper: section rows at least 40 and equal, a two-line option row's text
 * evenly inside its pill, the colour presets on the panel's text column, the first rows of both
 * columns on one line.
 */
abstract class SettingsLayoutAuditBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private var theme by mutableStateOf(MediumText.Graphite)

    /** Shows the page once; [each] then runs in Graphite and in Paper (one setContent per test). */
    private fun inBothThemes(
        focus: SettingsFocus,
        content: SettingsContent = ShotContent.library,
        each: (String) -> Unit,
    ) {
        compose.setContent { PlumTheme(theme) { SettingsAt(focus, content = content) } }
        for ((name, shown) in themes) {
            theme = shown
            compose.waitForIdle()
            each(name)
        }
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    private fun textBounds(matcher: SemanticsMatcher): DpRect = compose.onNode(matcher, useUnmergedTree = true).getBoundsInRoot()

    protected abstract val narrow: Boolean

    private val themes = listOf("Graphite" to MediumText.Graphite, "Paper" to MediumText.Paper)

    @Test
    fun sectionRowsAreAtLeast40AndEqualAndTheLastEndsAboveTheHintBar() =
        inBothThemes(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Library)) { name ->
            val rows = SettingsSection.entries.map { bounds(SettingsTags.section(it)) }
            val heights = rows.map { it.bottom.value - it.top.value }
            assertTrue("$name: every section row at least 40, got $heights", heights.all { it >= PlumDimens.rowHeight.value - 0.01f })
            assertTrue("$name: equal heights, got $heights", heights.all { kotlin.math.abs(it - heights[0]) < 0.01f })
            val hint = bounds(SettingsTags.HINT_CONFIRM)
            assertTrue("$name: the last row (${rows.last().bottom}) ends above the hint bar (${hint.top})", rows.last().bottom <= hint.top)
        }

    @Test
    fun aFocusedTwoLineOptionRowHasEvenRoomAroundItsText() =
        inBothThemes(optionsAt(SettingsOption.ConfirmBottom), SettingsContent()) { name ->
            val tag = SettingsTags.option(SettingsOption.ConfirmBottom)
            val row = bounds(tag)
            val label = textBounds(hasText(context.getString(SettingsOption.ConfirmBottom.label)) and hasAnyAncestor(hasTestTag(tag)))
            val detail = textBounds(hasText(context.getString(com.plumemu.R.string.settings_confirm_bottom_detail)))
            val above = label.top.value - row.top.value
            val below = row.bottom.value - detail.bottom.value
            assertEquals("$name: room above ($above) and below ($below) the text", above, below, 0.51f)
            assertTrue("$name: at least ${PlumDimens.settingsTwoLineRowPadding} (got $above)", above >= PlumDimens.settingsTwoLineRowPadding.value - 0.01f)
        }

    @Test
    fun theColourPresetsStartOnThePanelsTextColumn() {
        // Narrow screens show the panel as a further stop, opened.
        val (page, startIndex) = ColorPages.openingFor(SettingsOption.Background, PlumSettings(appearance = AppearanceSettings(background = ColorSetting.Preset("Graphite"))))
        inBothThemes(if (narrow) panelAt(SettingsOption.Background, page, startIndex) else optionsAt(SettingsOption.Background)) { name ->
            val panel = bounds(SettingsTags.PANEL)
            val first = bounds(SettingsTags.panelItem(0))
            val title = textBounds(hasText(context.getString(SettingsOption.Background.label)) and hasAnyAncestor(hasTestTag(SettingsTags.PANEL)))
            assertEquals("$name: grid starts where the title does", title.left.value, first.left.value, 0.51f)
            val textRight = panel.right.value - PlumDimens.gameRowPadding.value
            var index = 0
            var rightmost = first.right.value
            while (compose.onAllNodes(hasTestTag(SettingsTags.panelItem(index))).fetchSemanticsNodes().isNotEmpty()) {
                rightmost = maxOf(rightmost, bounds(SettingsTags.panelItem(index)).right.value)
                index++
            }
            assertTrue("$name: grid right $rightmost within the text column's $textRight", rightmost <= textRight + 0.01f)
        }
    }

    /** Sample rows shown under the Background panel's colour name at each text size; printed for the report. */
    @Test
    fun theBackgroundPanelShowsSampleRowsOneByOneAsTheTextGrows() {
        val (page, startIndex) = ColorPages.openingFor(SettingsOption.Background, PlumSettings(appearance = AppearanceSettings(background = ColorSetting.Preset("Graphite"))))
        var size by mutableStateOf(TextSize.Medium)
        compose.setContent { PlumTheme(MediumText.Graphite.copy(textSize = size)) { SettingsAt(panelAt(SettingsOption.Background, page, startIndex)) } }
        val counts = mutableMapOf<TextSize, Int>()
        for (each in listOf(TextSize.Medium, TextSize.Large, TextSize.Largest)) {
            size = each
            compose.waitForIdle()
            // At Largest on the wide panel the four swatch rows leave no room even for the name: it is left out whole (keeping it clipped and overlapped "+", TextSizeScreens768Test).
            val nameShown = compose.onAllNodes(hasText("#0E0F11", substring = true), useUnmergedTree = true).fetchSemanticsNodes().any { n -> n.layoutInfo.isPlaced }
            if (narrow || each != TextSize.Largest) assertTrue("the colour name shows at $each", nameShown)
            counts[each] = listOf("Mega Man X", "Super Metroid", "Yoshi's Island").count { compose.onAllNodes(hasText(it), useUnmergedTree = true).fetchSemanticsNodes().any { n -> n.layoutInfo.isPlaced } }
        }
        println("SAMPLE_ROWS narrow=$narrow $counts")
        // 768 wide: four rows of swatches leave about 25 below them, room for the name and no sample row; 576 wide: three rows of swatches leave room for rows.
        if (narrow) assertTrue("at least one sample row shows at Medium when there is room: $counts", counts.getValue(TextSize.Medium) >= 1)
        assertTrue("rows only drop as text grows: $counts", counts.getValue(TextSize.Medium) >= counts.getValue(TextSize.Large) && counts.getValue(TextSize.Large) >= counts.getValue(TextSize.Largest))
    }

    @Test
    fun theFirstRowsOfBothColumnsShareOneLine() =
        inBothThemes(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Library)) { name ->
            val section = textBounds(hasTestTag(SystemRowTags.LABEL) and hasAnyAncestor(hasTestTag(SettingsTags.section(SettingsSection.Library))))
            val option = textBounds(hasText(context.getString(SettingsOption.RomFolders.label)) and hasAnyAncestor(hasTestTag(SettingsTags.option(SettingsOption.RomFolders))))
            val sectionMid = (section.top.value + section.bottom.value) / 2
            val optionMid = (option.top.value + option.bottom.value) / 2
            assertEquals("$name: first labels' centres (section $sectionMid, option $optionMid)", sectionMid, optionMid, 0.51f)
        }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsLayoutAudit768Test : SettingsLayoutAuditBase() {
    override val narrow = false
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsLayoutAudit576Test : SettingsLayoutAuditBase() {
    override val narrow = true
}
