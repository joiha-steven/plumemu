package com.plumemu.settings.screen

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.SettingsThreeDimens
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings' positions against the board (RP5-S1-settings, Nova-6-settings) and design/rules.md › Settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(focus: SettingsFocus) = compose.setContent { PlumTheme(TableText.Graphite) { SettingsAt(focus) } }

    private val three = SettingsThreeDimens.sectionsWidth
    private val optionsLeft = three + SettingsThreeDimens.optionsGap
    private val panelLeft = 768.dp - PlumDimens.stripMarginSide - SettingsThreeDimens.panelWidth

    /** Three columns, the board's (design/rules.md › Settings): sections to x 184, options 194..520.7, right column 538.7..744. */
    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun threeColumnsSectionsStayWithTheOptionsAfterThem() {
        show(SettingsFocus(section = SettingsSection.Appearance))
        compose.onNodeWithTag(SettingsTags.SECTIONS).assertLeftPositionInRootIsEqualTo(0.dp).assertWidthIsEqualTo(three)
        compose.onNodeWithTag(SettingsTags.section(SettingsSection.Library)).assertHeightIsEqualTo(40.dp)
        compose.onNodeWithTag(SettingsTags.OPTIONS).assertLeftPositionInRootIsEqualTo(optionsLeft)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun threeColumnsKeepTheSectionsWithAnOptionFocused() {
        show(optionsAt(SettingsOption.Background))
        compose.onNodeWithTag(SettingsTags.SECTIONS).assertLeftPositionInRootIsEqualTo(0.dp).assertWidthIsEqualTo(three)
        compose.onNodeWithTag(SettingsTags.OPTIONS).assertLeftPositionInRootIsEqualTo(optionsLeft).assertWidthIsEqualTo(314.67.dp)
        compose.onNodeWithTag(SettingsTags.PANEL).assertLeftPositionInRootIsEqualTo(panelLeft).assertWidthIsEqualTo(SettingsThreeDimens.panelWidth)
        compose.onNodeWithTag(SettingsTags.option(SettingsOption.Background)).assertHeightIsEqualTo(40.dp)
        // Colors, Background and Text color under Language.
        val first = compose.onNodeWithTag(SettingsTags.option(SettingsOption.Colors)).getBoundsInRoot()
        val second = compose.onNodeWithTag(SettingsTags.option(SettingsOption.Background)).getBoundsInRoot()
        val third = compose.onNodeWithTag(SettingsTags.option(SettingsOption.TextColor)).getBoundsInRoot()
        assertEquals("rows 42 apart (40 + 2)", 42f, (second.top - first.top).value, 0.01f)
        assertEquals("rows 42 apart (40 + 2)", 42f, (third.top - second.top).value, 0.01f)
        // Language is the first row, above Colors: top 14 + bar 23 (grows with the place name line) + gap 10.
        val language = compose.onNodeWithTag(SettingsTags.option(SettingsOption.Language)).getBoundsInRoot()
        assertEquals(47f, language.top.value, 0.01f)
        assertEquals("rows 42 apart (40 + 2)", 42f, (first.top - language.top).value, 0.01f)
    }

    /** At 832x468 the sections and the right column keep their widths; the options take the rest. */
    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun threeColumnsAt832GiveTheRestToTheOptions() {
        show(optionsAt(SettingsOption.Background))
        compose.onNodeWithTag(SettingsTags.SECTIONS).assertWidthIsEqualTo(three)
        compose.onNodeWithTag(SettingsTags.OPTIONS).assertLeftPositionInRootIsEqualTo(optionsLeft).assertWidthIsEqualTo(378.67.dp)
        compose.onNodeWithTag(SettingsTags.PANEL).assertLeftPositionInRootIsEqualTo(832.dp - 24.dp - SettingsThreeDimens.panelWidth).assertWidthIsEqualTo(SettingsThreeDimens.panelWidth)
    }

    /** Where three columns do not fit (Largest text) the wide screen keeps two: sections 224 and slid away with an option focused. */
    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun twoColumnsAtLargestSectionsRestAtTheMarginWithTheOptionsAfterThem() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) { SettingsAt(SettingsFocus(section = SettingsSection.Appearance)) } }
        compose.onNodeWithTag(SettingsTags.SECTIONS).assertLeftPositionInRootIsEqualTo(0.dp).assertWidthIsEqualTo(224.dp)
        compose.onNodeWithTag(SettingsTags.OPTIONS).assertLeftPositionInRootIsEqualTo(248.dp)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun twoColumnsAtLargestOptionsSlideTheSectionsAwayWithThePanelBeside() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) { SettingsAt(optionsAt(SettingsOption.Background)) } }
        compose.onNodeWithTag(SettingsTags.OPTIONS).assertLeftPositionInRootIsEqualTo(24.dp).assertWidthIsEqualTo(448.dp)
        compose.onNodeWithTag(SettingsTags.PANEL).assertLeftPositionInRootIsEqualTo(496.dp).assertWidthIsEqualTo(248.dp)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrowOptionsTakeTheWidthLessBothMargins() {
        show(optionsAt(SettingsOption.Background))
        compose.onNodeWithTag(SettingsTags.OPTIONS).assertLeftPositionInRootIsEqualTo(24.dp).assertWidthIsEqualTo(528.dp)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrowPanelIsAFurtherStopAsWideAsTheOptions() {
        show(panelAt(SettingsOption.Background, PanelPage.BackgroundPresets, 0))
        compose.onNodeWithTag(SettingsTags.PANEL).assertLeftPositionInRootIsEqualTo(24.dp).assertWidthIsEqualTo(528.dp)
        // Ten 40 dp presets a row, 10 apart, from the panel's text column (24 + the row padding, `PlumDimens.gameRowPadding`; audit B6).
        val text = 24.dp + PlumDimens.gameRowPadding
        compose.onNodeWithTag(SettingsTags.panelItem(0)).assertLeftPositionInRootIsEqualTo(text).assertWidthIsEqualTo(40.dp)
        compose.onNodeWithTag(SettingsTags.panelItem(8)).assertLeftPositionInRootIsEqualTo(text + 400.dp)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun threeColumnsPresetsThreeARowOnTheTextColumn() {
        show(panelAt(SettingsOption.Background, PanelPage.BackgroundPresets, 0))
        // The right column's text column is 538.7 (the card's left edge) .. 744 - the row padding: three 40 dp swatches 10 apart fit (140), a fourth would pass its end.
        val text = panelLeft
        compose.onNodeWithTag(SettingsTags.panelItem(2)).assertLeftPositionInRootIsEqualTo(text + 100.dp)
        compose.onNodeWithTag(SettingsTags.panelItem(3)).assertLeftPositionInRootIsEqualTo(text)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun threeColumnsCustomPaletteIsEightTilesAcrossTheWidePage() {
        show(panelAt(SettingsOption.Background, PanelPage.BackgroundCustom, 0))
        // The page covers the options and the right column: 188.7..744; the grid's container is that wide and its tiles share what the padding leaves (edges: SettingsColorSliderAlign768Test).
        compose.onNodeWithTag(PALETTE_GRID_TAG).assertLeftPositionInRootIsEqualTo(optionsLeft)
        compose.onNodeWithTag(SettingsTags.panelItem(0)).assertLeftPositionInRootIsEqualTo(optionsLeft + PlumDimens.settingsPanelRowPadding)
    }
}
