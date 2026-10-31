package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.toThemeSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "+" page of Background and Text color with the Lightness slider focused:
 * the Background track shows the tones no text reads on dimmed, the Text track only what reads.
 * Taken after the panel scrolled the focused row into view, so the shot is what a person sees.
 * Medium and Largest, Graphite and Paper, at each of the two reference sizes
 * (`settings_color_sliders_<background|text>_<size>_<preset>[_largest].png`).
 */
abstract class SettingsColorSlidersShotBase(
    private val size: ReferenceShots.Size,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private data class Look(
        val screen: String,
        val preset: ReferenceShots.Preset,
        val textSize: TextSize,
    )

    @Composable
    private fun SlidersPage(screen: String) {
        val option = if (screen == "text") SettingsOption.TextColor else SettingsOption.Background
        val presets = if (screen == "text") PanelPage.TextPresets else PanelPage.BackgroundPresets
        val base = settingsForShot()
        val settings: PlumSettings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android))
        val geometry = settingsGeometry(PlumTheme.layout)
        val model =
            remember {
                SettingsModel(NoActions, optionsAt(option)).also { model ->
                    model.onAction(Action.Enter, settings, geometry)
                    val plus = ColorPages.presetCount(presets, settings.toThemeSettings().backgroundColor) - 1
                    model.onTapPanelItem(plus, settings, geometry)
                    // A first tap on a tile may only focus it.
                    if (model.focus.page == presets) model.onTapPanelItem(plus, settings, geometry)
                    // Down to Lightness, the slider nearest the Color code row.
                    val swatches = ColorPages.palette(model.focus.page!!, settings.toThemeSettings().backgroundColor).size
                    var guard = 0
                    while (model.focus.panelIndex < swatches + ColorSlider.Lightness.ordinal && guard++ < 20) {
                        model.onAction(Action.Move(Direction.Down), settings, geometry)
                    }
                }
            }
        CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
            SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
        }
    }

    @Test
    fun shots() {
        var look by mutableStateOf(Look("background", ReferenceShots.Preset.Graphite, TextSize.Medium))
        compose.setContent { PlumTheme(look.preset.settings.copy(textSize = look.textSize)) { key(look) { SlidersPage(look.screen) } } }
        for (screen in listOf("background", "text")) {
            for (textSize in listOf(TextSize.Medium, TextSize.Largest)) {
                for (preset in ReferenceShots.Preset.entries) {
                    look = Look(screen, preset, textSize)
                    compose.waitForIdle()
                    compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/${ReferenceShots.fileName("settings_color_sliders_$screen", size, preset, textSize = textSize)}")
                }
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorSliders768ShotTest : SettingsColorSlidersShotBase(ReferenceShots.Size.Wide)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorSliders576ShotTest : SettingsColorSlidersShotBase(ReferenceShots.Size.Narrow)
