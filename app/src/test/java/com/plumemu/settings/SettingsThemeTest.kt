package com.plumemu.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A stored change reaches the theme the screen draws with, without restarting anything. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsThemeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun aStoredChangeRedrawsWithTheNewLook() {
        val stored = mutableStateOf(PlumSettings.Defaults)
        var background = Color.Unspecified
        var motionOn = false
        var fontName = ""
        compose.setContent {
            SettingsTheme(stored.value) {
                background = PlumTheme.colors.bg
                motionOn = PlumTheme.motion.isOn
                fontName = PlumTheme.type.font.name
            }
        }
        compose.waitForIdle()
        assertEquals(BackgroundPreset.Black.color.toComposeColor(), background)
        assertEquals(true, motionOn)
        assertEquals("Sans", fontName)

        stored.value = PlumSettings(
            appearance = AppearanceSettings(ColorSetting.Preset("Paper"), TextColorSetting.Dark, font = FontSetting.Mono),
            advanced = AdvancedSettings(animations = false),
        )
        compose.waitForIdle()
        assertEquals(BackgroundPreset.Paper.color.toComposeColor(), background)
        assertEquals("Animations off stops motion", false, motionOn)
        assertEquals("Mono", fontName)
    }
}
