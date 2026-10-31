package com.plumemu.settings.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Appearance › Accent color with the By system tile focused, the row reading "By system". */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsAccentBySystemScreenshotTest {
    @Test
    fun tileFocused() =
        ReferenceShots.capture("settings_accent_by_system") {
            val base = settingsForShot()
            val settings = base.copy(appearance = base.appearance.copy(accent = ColorSetting.BySystem(ColorSetting.Preset("Red"))))
            val geometry = settingsGeometry(PlumTheme.layout)
            val model = remember { SettingsModel(NoActions, panelAt(SettingsOption.Accent, PanelPage.AccentPresets, ColorPages.bySystemIndex)) }
            CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
                SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
            }
        }
}
