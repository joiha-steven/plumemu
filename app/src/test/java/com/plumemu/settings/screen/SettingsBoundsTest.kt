package com.plumemu.settings.screen

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import com.plumemu.coreui.theme.PlumDimens
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.theme.PlumTheme
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

    private fun show(focus: SettingsFocus) = compose.setContent { PlumTheme(MediumText.Graphite) { SettingsAt(focus) } }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun wideSectionsRestAtTheMarginWithTheOptionsAfterThem() {
        show(SettingsFocus(section = SettingsSection.Appearance))
        compose.onNodeWithTag(SettingsTags.SECTIONS).assertLeftPositionInRootIsEqualTo(0.dp).assertWidthIsEqualTo(224.dp)
        compose.onNodeWithTag(SettingsTags.section(SettingsSection.Library)).assertHeightIsEqualTo(40.dp)
        compose.onNodeWithTag(SettingsTags.OPTIONS).assertLeftPositionInRootIsEqualTo(248.dp)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun wideOptionsSlideTheSectionsAwayWithThePanelBeside() {
        show(optionsAt(SettingsOption.Background))
        compose.onNodeWithTag(SettingsTags.OPTIONS).assertLeftPositionInRootIsEqualTo(24.dp).assertWidthIsEqualTo(448.dp)
        compose.onNodeWithTag(SettingsTags.PANEL).assertLeftPositionInRootIsEqualTo(496.dp).assertWidthIsEqualTo(248.dp)
        compose.onNodeWithTag(SettingsTags.option(SettingsOption.Background)).assertHeightIsEqualTo(40.dp)
        // Colors is the first row, Background the second.
        val first = compose.onNodeWithTag(SettingsTags.option(SettingsOption.Colors)).getBoundsInRoot()
        val second = compose.onNodeWithTag(SettingsTags.option(SettingsOption.Background)).getBoundsInRoot()
        val third = compose.onNodeWithTag(SettingsTags.option(SettingsOption.TextColor)).getBoundsInRoot()
        assertEquals("rows 42 apart (40 + 2)", 42f, (second.top - first.top).value, 0.01f)
        assertEquals("rows 42 apart (40 + 2)", 42f, (third.top - second.top).value, 0.01f)
        // Top 14 + bar 18 + gap 10.
        assertEquals(42f, first.top.value, 0.01f)
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
        // Ten 40 dp presets a row, 10 apart, from the panel's text column (24 + the row padding 14; audit B6).
        compose.onNodeWithTag(SettingsTags.panelItem(0)).assertLeftPositionInRootIsEqualTo(38.dp).assertWidthIsEqualTo(40.dp)
        compose.onNodeWithTag(SettingsTags.panelItem(8)).assertLeftPositionInRootIsEqualTo(438.dp)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun widePresetsFourARowOnTheTextColumnAndThePaletteEightOfTwentyFour() {
        show(panelAt(SettingsOption.Background, PanelPage.BackgroundPresets, 0))
        // The panel's text column is 510..730 (496 + 14, 744 - 14): four 40 dp swatches 10 apart fit, a fifth would pass 730 (audit B6).
        compose.onNodeWithTag(SettingsTags.panelItem(3)).assertLeftPositionInRootIsEqualTo(660.dp)
        compose.onNodeWithTag(SettingsTags.panelItem(4)).assertLeftPositionInRootIsEqualTo(510.dp)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun wideCustomPaletteIsEightTilesAcrossThePanelWidth() {
        show(panelAt(SettingsOption.Background, PanelPage.BackgroundCustom, 0))
        // The panel spans 496..744; the grid's container is that wide and its tiles share what the padding leaves (edges: SettingsColorSliderAlign768Test).
        compose.onNodeWithTag(PALETTE_GRID_TAG).assertLeftPositionInRootIsEqualTo(496.dp)
        compose.onNodeWithTag(SettingsTags.panelItem(0)).assertLeftPositionInRootIsEqualTo(496.dp + PlumDimens.settingsPanelRowPadding)
    }
}
