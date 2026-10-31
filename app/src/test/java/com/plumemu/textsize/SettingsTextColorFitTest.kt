package com.plumemu.textsize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.CustomPalette
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.settings.screen.ColorPages
import com.plumemu.settings.screen.ColorSlider
import com.plumemu.settings.screen.NoActions
import com.plumemu.settings.screen.PaletteCuration
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.NoTouch
import com.plumemu.settings.screen.SettingsGeometry
import com.plumemu.settings.screen.SettingsLayout
import com.plumemu.settings.screen.SettingsModel
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.TEST_VERSION
import com.plumemu.settings.screen.optionsAt
import com.plumemu.settings.screen.settingsForShot
import com.plumemu.settings.screen.settingsGeometry
import com.plumemu.settings.toThemeSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Appearance › Text color at every text size, the way
 * `SettingsStatisticsFitTest` checks Statistics: no text is ellipsized, clipped, too short or on top
 * of another, and what the cursor is on is fully on screen: the presets panel, the own-colour page,
 * a refused hex code with its "Use" row, and the notice that a background moved the text.
 */
abstract class SettingsTextColorFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Composable
    private fun Panel(steps: SettingsModel.(PlumSettings, SettingsGeometry) -> Unit) {
        val base = settingsForShot()
        val settings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android), appearance = base.appearance.copy(textColor = TextColorSetting.Light))
        val geometry = settingsGeometry(PlumTheme.layout)
        val model = remember { SettingsModel(NoActions, optionsAt(SettingsOption.TextColor)).also { it.steps(settings, geometry) } }
        CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
            SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
        }
    }

    private fun check(
        name: String,
        focusedTag: String,
        steps: SettingsModel.(PlumSettings, SettingsGeometry) -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { Panel(steps) } }
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
            textProblems(root, FitAllowance(setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, SettingsTags.COLOR_SCROLL), isLargest = each == TextSize.Largest)).mapTo(failures) { "$label: $it" }
            val focused = findTagged(root, focusedTag)
            if (focused == null) failures += "$label: the focused item is not drawn" else notFullyShown(focused, root)?.let { failures += "$label: the focused item $it" }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    private fun plusIndex(settings: PlumSettings): Int = ColorPages.presetCount(PanelPage.TextPresets, settings.toThemeSettings().backgroundColor) - 1

    private fun SettingsModel.openOwnColourPage(
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        onAction(Action.Enter, settings, geometry)
        onTapPanelItem(plusIndex(settings), settings, geometry)
        // A first tap on a tile may only focus it.
        if (focus.page == PanelPage.TextPresets) onTapPanelItem(plusIndex(settings), settings, geometry)
    }

    @Test
    fun presets() = check("text_color_presets", SettingsTags.panelItem(0)) { settings, geometry -> onAction(Action.Enter, settings, geometry) }

    /** The page opens on the swatch nearest the current Light; the first cell is tapped. */
    @Test
    fun ownColourPageOnTheFirstCell() =
        check("text_color_custom", SettingsTags.panelItem(0)) { settings, geometry ->
            openOwnColourPage(settings, geometry)
            // The page may already open on the first cell (the lightest tile is nearest the default Light): a second tap would choose it and leave the page.
            if (focus.panelIndex != 0) onTapPanelItem(0, settings, geometry)
        }

    @Test
    fun ownColourPageOnTheHexRow() = check("text_color_custom_hex", SettingsTags.HEX_ROW) { settings, geometry ->
            openOwnColourPage(settings, geometry)
            // Down to the hex row, the last one: the cursor is on it without the field open.
            repeat(CustomPalette.ROWS + ColorSlider.entries.size + 1) { onAction(Action.Move(Direction.Down), settings, geometry) }
        }

    /** A tap on a slider's track focuses its row and moves the thumb: the focused row must be whole at every size (Hue, then Lightness, the one nearest the Color code row). */
    @Test
    fun hueSliderFocused() = check("text_color_slider_hue", SettingsTags.panelItem(textCells())) { settings, geometry -> sliderTapped(ColorSlider.Hue, settings, geometry) }

    @Test
    fun lightnessSliderFocused() = check("text_color_slider_lightness", SettingsTags.panelItem(textCells() + 2)) { settings, geometry -> sliderTapped(ColorSlider.Lightness, settings, geometry) }

    private fun textCells(): Int = PaletteCuration.curated(CustomPalette.textSwatchesOn(BackgroundPreset.Graphite.color)).size

    private fun SettingsModel.sliderTapped(
        slider: ColorSlider,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        openOwnColourPage(settings, geometry)
        onTouchSlider(PaletteCuration.curated(CustomPalette.textSwatchesOn(settings.toThemeSettings().backgroundColor)).size + slider.ordinal, 0.5f, settings, geometry)
    }

    /** A code too close to the background: the two-line message and the "Use #…" row the cursor lands on. */
    @Test
    fun refusedHex() =
        check("text_color_refused", SettingsTags.HEX_USE) { settings, geometry ->
            onAction(Action.Enter, settings, geometry)
            onTapPanelItem(plusIndex(settings), settings, geometry)
            // A first tap on a tile may only focus it.
            if (focus.page == PanelPage.TextPresets) onTapPanelItem(plusIndex(settings), settings, geometry)
            val hexRow = PaletteCuration.curated(CustomPalette.textSwatchesOn(settings.toThemeSettings().backgroundColor)).size + ColorSlider.entries.size
            onTapPanelItem(hexRow, settings, geometry)
            onHexTextChange("222222")
            onHexEntered(hexText)
        }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsTextColorFit768Test : SettingsTextColorFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class SettingsTextColorFit837Test : SettingsTextColorFitBase(837, 471)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SettingsTextColorFit667Test : SettingsTextColorFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsTextColorFit576Test : SettingsTextColorFitBase(576, 432)
