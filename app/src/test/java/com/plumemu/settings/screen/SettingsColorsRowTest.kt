package com.plumemu.settings.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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

    private fun choice(id: Int) = context.getString(R.string.settings_value_choice, text(id))

    private fun opens(id: Int) = context.getString(R.string.settings_value_opens, text(id))

    private fun show(appearance: AppearanceSettings) {
        val stored = PlumSettings(appearance = appearance, flags = SetupFlags(setupCompleted = true))
        compose.setContent { PlumTheme { SettingsAt(optionsAt(SettingsOption.Colors), stored) } }
    }

    @Test
    fun aFreshInstallShowsPlumemuAndExplainsTheRow() {
        show(AppearanceSettings())
        compose.onAllNodesWithText(text(R.string.settings_colors)).assertCountEquals(2) // the row and its panel's title
        compose.onNodeWithText(choice(R.string.settings_colors_plumemu)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_colors_panel_help)).assertIsDisplayed()
        // Plumemu's colours are presets: the rows below name them.
        compose.onNodeWithText(opens(R.string.settings_bg_black)).assertIsDisplayed()
        compose.onNodeWithText(opens(R.string.settings_accent_red)).assertIsDisplayed()
    }

    @Test
    fun aSetsOwnHexColoursShowTheSetsNameNotCustom() {
        show(AppearanceSettings(background = ColorSetting.Custom(0xD9D8D3), textColor = TextColorSetting.Dark, accent = ColorSetting.Custom(0xAA2924)))
        compose.onNodeWithText(choice(R.string.settings_colors_super)).assertIsDisplayed()
        compose.onAllNodesWithText(opens(R.string.settings_colors_super)).assertCountEquals(2) // Background and Accent color
        compose.onNodeWithText(opens(R.string.settings_color_custom)).assertDoesNotExist()
    }

    @Test
    fun aSetsPreviousAccentStillShowsTheSetsNameNotCustom() {
        // Stored by Super before the accent changed (#B22B26); the accent row is named by the set it still belongs to.
        show(AppearanceSettings(background = ColorSetting.Custom(0xD9D8D3), textColor = TextColorSetting.Dark, accent = ColorSetting.Custom(0xB22B26)))
        compose.onNodeWithText(choice(R.string.settings_colors_super)).assertIsDisplayed()
        compose.onAllNodesWithText(opens(R.string.settings_colors_super)).assertCountEquals(2)
        compose.onNodeWithText(opens(R.string.settings_color_custom)).assertDoesNotExist()
    }

    @Test
    fun theColorsRowReadsCustomButEachRowIsNamedByItsOwnColour() {
        // Super's background with Blue: no set matches, so Colors says Custom; the background is still Super's colour.
        show(AppearanceSettings(background = ColorSetting.Custom(0xD9D8D3), textColor = TextColorSetting.Dark, accent = ColorSetting.Preset("Blue")))
        compose.onNodeWithText(choice(R.string.settings_color_custom)).assertIsDisplayed()
        compose.onNodeWithText(opens(R.string.settings_colors_super)).assertIsDisplayed()
        compose.onNodeWithText(opens(R.string.settings_accent_blue)).assertIsDisplayed()
    }

    @Test
    fun changingOnlyTheTextOfIvoryLeavesBackgroundAndAccentNamedIvory() {
        show(AppearanceSettings(background = ColorSetting.Custom(0xF3EAD0), textColor = TextColorSetting.Custom(0x14213D), accent = ColorSetting.Custom(0x127087)))
        compose.onNodeWithText(choice(R.string.settings_color_custom)).assertIsDisplayed()
        compose.onAllNodesWithText(opens(R.string.settings_colors_ivory)).assertCountEquals(2) // Background and Accent color
    }

    @Test
    fun ivorysPreviousAccentWithItsOwnTextIsStillNamedIvory() {
        // The text moved away from Ivory before its accent changed: the stored old accent is not settled, but it is still Ivory's.
        show(AppearanceSettings(background = ColorSetting.Custom(0xF3EAD0), textColor = TextColorSetting.Custom(0x14213D), accent = ColorSetting.Custom(0x1792B0)))
        compose.onAllNodesWithText(opens(R.string.settings_colors_ivory)).assertCountEquals(2) // Background and Accent color
    }

    @Test
    fun aVividPresetBackgroundIsNamedByItsPresetName() {
        show(AppearanceSettings(background = ColorSetting.Custom(ColorPages.vividBackgrounds.first().color.value), textColor = TextColorSetting.Dark))
        compose.onNodeWithText(opens(ColorPages.vividBackgrounds.first().name)).assertIsDisplayed()
    }

    @Test
    fun theAccentRowShowsTheBySystemGlyphNotTheFallbackColourDot() {
        show(AppearanceSettings(accent = ColorSetting.BySystem(ColorSetting.Preset("Blue"))))
        compose.onNodeWithTag(SettingsTags.VALUE_BY_SYSTEM).assertIsDisplayed()
    }
}
