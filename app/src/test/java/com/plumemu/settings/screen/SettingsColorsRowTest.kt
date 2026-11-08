package com.plumemu.settings.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Appearance › Colors on screen: the row's value, its explanation, and set names on the rows below. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorsRowTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun text(id: Int) = context.getString(id)

    /** The words of a row's value: text inside a `settings_value_*` node (the label and the panel say the same words). */
    private fun valueText(id: Int) = valueText(text(id))

    /** The Colors row's own value (the rows below it may say the same set's name). */
    private fun colorsValue(id: Int) = hasText(text(id)) and hasAnyAncestor(hasTestTag(SettingsTags.value(SettingsOption.Colors)))

    private fun valueText(name: String) =
        hasText(name) and
            SemanticsMatcher("inside a settings row's value") { node ->
                generateSequence(node) { it.parent }.any { (it.config.getOrNull(SemanticsProperties.TestTag) ?: "").startsWith("settings_value_") }
            }

    private fun show(appearance: AppearanceSettings) {
        val stored = PlumSettings(appearance = appearance, flags = SetupFlags(setupCompleted = true))
        compose.setContent { PlumTheme { SettingsAt(optionsAt(SettingsOption.TextColor), stored) } }
    }

    @Test
    fun aFreshInstallShowsPlumemuAndExplainsTheRow() {
        show(AppearanceSettings())
        compose.onAllNodesWithText(text(R.string.settings_colors)).assertCountEquals(2) // the row and its panel's title
        compose.onNode(colorsValue(R.string.settings_colors_plumemu), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_colors_panel_help)).assertExists() // above the row the panel scrolled to
        // A fresh install stores Plumemu's own #161A24 and #3A7CEB: the Colors, Background and Accent rows all name the set.
        compose.onAllNodes(valueText(R.string.settings_colors_plumemu), useUnmergedTree = true).assertCountEquals(3)
    }

    @Test
    fun anOldStoredBlackWithTheOldBlueReadsPlumemuOnTheColorsAndBackgroundRows() {
        show(AppearanceSettings(background = ColorSetting.Preset("Black"), textColor = TextColorSetting.Light, accent = ColorSetting.Custom(0x3E7BE0)))
        compose.onNode(colorsValue(R.string.settings_colors_plumemu), useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(valueText(R.string.settings_colors_plumemu), useUnmergedTree = true).assertCountEquals(3)
        compose.onNode(valueText(R.string.settings_bg_black), useUnmergedTree = true).assertDoesNotExist()
    }

    /** What setup stores on an OLED handheld: true Black under the Plumemu accent is still the Plumemu set. */
    @Test
    fun theOledBlackPageWithThePlumemuAccentReadsPlumemuOnTheColorsAndBackgroundRows() {
        show(AppearanceSettings(background = ColorSetting.Preset("Black"), textColor = TextColorSetting.Light, accent = ColorSetting.Custom(0x3A7CEB)))
        compose.onNode(colorsValue(R.string.settings_colors_plumemu), useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(valueText(R.string.settings_colors_plumemu), useUnmergedTree = true).assertCountEquals(3)
        compose.onNode(valueText(R.string.settings_bg_black), useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(valueText(R.string.settings_color_custom), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun blackWithAnotherAccentIsBlackAndCustom() {
        show(AppearanceSettings(background = ColorSetting.Preset("Black"), textColor = TextColorSetting.Light, accent = ColorSetting.Preset("Green")))
        compose.onNode(colorsValue(R.string.settings_color_custom), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(valueText(R.string.settings_bg_black), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aSetsOwnHexColoursShowTheSetsNameNotCustom() {
        show(AppearanceSettings(background = ColorSetting.Custom(0xF3EAD0), textColor = TextColorSetting.Dark, accent = ColorSetting.Custom(0x127087)))
        compose.onNode(colorsValue(R.string.settings_colors_ivory), useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(valueText(R.string.settings_colors_ivory), useUnmergedTree = true).assertCountEquals(3) // Colors, Background and Accent color
        compose.onNode(valueText(R.string.settings_color_custom), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aSetsPreviousAccentStillShowsTheSetsNameNotCustom() {
        // Stored by Ivory before the accent changed (#B22B26); the accent row is named by the set it still belongs to.
        show(AppearanceSettings(background = ColorSetting.Custom(0xF3EAD0), textColor = TextColorSetting.Dark, accent = ColorSetting.Custom(0x1792B0)))
        compose.onNode(colorsValue(R.string.settings_colors_ivory), useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(valueText(R.string.settings_colors_ivory), useUnmergedTree = true).assertCountEquals(3)
        compose.onNode(valueText(R.string.settings_color_custom), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theColorsRowReadsCustomButEachRowIsNamedByItsOwnColour() {
        // Ivory's background with Blue: no set matches, so Colors says Custom; the background is still Ivory's colour.
        show(AppearanceSettings(background = ColorSetting.Custom(0xF3EAD0), textColor = TextColorSetting.Dark, accent = ColorSetting.Preset("Blue")))
        compose.onNode(colorsValue(R.string.settings_color_custom), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(valueText(R.string.settings_colors_ivory), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(valueText(R.string.settings_accent_blue), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun changingOnlyTheTextOfIvoryLeavesBackgroundAndAccentNamedIvory() {
        show(AppearanceSettings(background = ColorSetting.Custom(0xF3EAD0), textColor = TextColorSetting.Custom(0x14213D), accent = ColorSetting.Custom(0x127087)))
        compose.onNode(colorsValue(R.string.settings_color_custom), useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(valueText(R.string.settings_colors_ivory), useUnmergedTree = true).assertCountEquals(2) // Background and Accent color
    }

    @Test
    fun ivorysPreviousAccentWithItsOwnTextIsStillNamedIvory() {
        // The text moved away from Ivory before its accent changed: the stored old accent is not settled, but it is still Ivory's.
        show(AppearanceSettings(background = ColorSetting.Custom(0xF3EAD0), textColor = TextColorSetting.Custom(0x14213D), accent = ColorSetting.Custom(0x1792B0)))
        compose.onAllNodes(valueText(R.string.settings_colors_ivory), useUnmergedTree = true).assertCountEquals(2) // Background and Accent color
    }

    @Test
    fun aVividPresetBackgroundIsNamedByItsPresetName() {
        show(AppearanceSettings(background = ColorSetting.Custom(ColorPages.vividBackgrounds.first().color.value), textColor = TextColorSetting.Dark))
        compose.onNode(valueText(ColorPages.vividBackgrounds.first().name), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theAccentRowShowsTheBySystemGlyphNotTheFallbackColourDot() {
        show(AppearanceSettings(accent = ColorSetting.BySystem(ColorSetting.Preset("Blue"))))
        compose.onNodeWithTag(SettingsTags.VALUE_BY_SYSTEM).assertIsDisplayed()
    }
}
