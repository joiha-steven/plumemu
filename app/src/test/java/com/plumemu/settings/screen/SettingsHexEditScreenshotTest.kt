package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.settings.toThemeSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CustomPalette
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The custom accent's hex editor while typing: the cursor on the field, on "Use this
 * colour" under it, and the wrong-code line. The model is driven by its own actions, as in use.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsHexEditScreenshotTest {
    @Composable
    private fun HexEditor(steps: SettingsModel.(settings: com.plumemu.coredata.settings.PlumSettings) -> Unit) {
        // Android's keyboard (Settings › Controls › Keyboard): no tray; Plumemu's tray: SettingsKeyboardScreenshotTest.
        val settings = settingsForShot().let { it.copy(controls = it.controls.copy(keyboard = KeyboardSetting.Android)) }
        val geometry = settingsGeometry(PlumTheme.layout)
        val model =
            remember(steps) {
                SettingsModel(NoActions, panelAt(SettingsOption.Accent, PanelPage.AccentCustom, PaletteCuration.curated(CustomPalette.accentSwatchesOn(settings.toThemeSettings().backgroundColor)).size)).also {
                    it.onAction(Action.Enter, settings, geometry)
                    it.onHexTextChange("1A2B3C")
                    it.steps(settings)
                }
            }
        CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
            SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
        }
    }

    @Test
    fun cursorOnTheField() = ReferenceShots.capture("settings_hex_typing_field") { HexEditor { } }

    @Test
    fun cursorOnUseThisColour() =
        ReferenceShots.capture("settings_hex_typing_use") {
            val geometry = settingsGeometry(PlumTheme.layout)
            HexEditor { settings -> onAction(Action.Move(Direction.Down), settings, geometry) }
        }

    @Test
    fun wrongCode() =
        ReferenceShots.capture("settings_hex_typing_wrong") {
            val geometry = settingsGeometry(PlumTheme.layout)
            HexEditor { settings ->
                onHexTextChange("zz")
                onAction(Action.Move(Direction.Down), settings, geometry)
                onAction(Action.Enter, settings, geometry)
            }
        }
}
