package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CustomPalette
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.settings.toThemeSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Appearance › Text color: the presets panel, its own-colour
 * page on a dark and a light background, a hex code refused with the nearest readable colour, and
 * the line saying a background choice moved the text. Graphite is the dark and Paper the light shot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsTextColorScreenshotTest {
    /** The shot's settings with Android's keyboard (no tray) and the text colour [text] on the preset's own background. */
    @Composable
    private fun settingsWith(text: TextColorSetting?): PlumSettings {
        val base = settingsForShot()
        val shown = text ?: base.appearance.textColor
        return base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android), appearance = base.appearance.copy(textColor = shown))
    }

    @Composable
    private fun Shown(
        settings: PlumSettings,
        model: SettingsModel,
    ) {
        CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
            SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
        }
    }

    @Composable
    private fun TextPanel(
        page: PanelPage,
        steps: SettingsModel.(PlumSettings, SettingsGeometry) -> Unit = { _, _ -> },
    ) {
        val settings = settingsWith(null)
        val geometry = settingsGeometry(PlumTheme.layout)
        val model =
            remember(page) {
                SettingsModel(NoActions, optionsAt(SettingsOption.TextColor)).also { it.steps(settings, geometry) }
            }
        Shown(settings, model)
    }

    /** Opens the page through the panel itself (the shot's model must know the colours it opened on), at the hex row when [toHex]. */
    private fun SettingsModel.openCustomPage(
        settings: PlumSettings,
        geometry: SettingsGeometry,
        toHex: Boolean,
    ) {
        // Taps reach "+" and the hex row whatever the colours: the cursor never walks onto a preset that does not read.
        onAction(Action.Enter, settings, geometry)
        onTapPanelItem(plusIndex(settings), settings, geometry)
        // A first tap on a tile may only focus it.
        if (focus.page == PanelPage.TextPresets) onTapPanelItem(plusIndex(settings), settings, geometry)
        if (toHex) onTapPanelItem(paletteSize(settings), settings, geometry)
    }

    private fun plusIndex(settings: PlumSettings): Int = ColorPages.presetCount(PanelPage.TextPresets, settings.toThemeSettings().backgroundColor) - 1

    private fun paletteSize(settings: PlumSettings): Int = PaletteCuration.curated(CustomPalette.textSwatchesOn(settings.toThemeSettings().backgroundColor)).size

    @Test
    fun presets() =
        ReferenceShots.capture("settings_text_color_panel") {
            TextPanel(PanelPage.TextPresets) { settings, geometry -> onAction(Action.Enter, settings, geometry) }
        }

    @Test
    fun ownColourPage() =
        ReferenceShots.capture("settings_text_color_custom") {
            TextPanel(PanelPage.TextCustom) { settings, geometry -> openCustomPage(settings, geometry, toHex = false) }
        }

    @Test
    fun hexRefused() =
        ReferenceShots.capture("settings_text_color_refused") {
            TextPanel(PanelPage.TextCustom) { settings, geometry ->
                openCustomPage(settings, geometry, toHex = true)
                // Close to the background of either preset, so it cannot be read there.
                val dark = settings.appearance.background != ColorSetting.Preset("Paper")
                onHexTextChange(if (dark) "222222" else "EEEEEE")
                onHexEntered(hexText)
            }
        }

    @Test
    fun backgroundMovedTheText() =
        ReferenceShots.capture("settings_text_color_adjusted") {
            val geometry = settingsGeometry(PlumTheme.layout)
            val base = settingsWith(TextColorSetting.Custom(0xC8E6C9)).let { it.copy(appearance = it.appearance.copy(background = ColorSetting.Preset("Graphite"))) }
            val applied = remember { mutableStateOf(base) }
            val actions =
                remember {
                    object : SettingsActions by NoActions {
                        override fun update(transform: (PlumSettings) -> PlumSettings) {
                            applied.value = transform(applied.value)
                        }
                    }
                }
            val model =
                remember {
                    SettingsModel(actions, panelAt(SettingsOption.Background, PanelPage.BackgroundPresets, 0)).also {
                        // The pale-green text is fine on Graphite and cannot be read on Paper (second preset).
                        it.onAction(Action.Move(Direction.Down), applied.value, geometry)
                    }
                }
            Shown(applied.value, model)
        }
}
