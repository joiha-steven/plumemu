package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlumThemeSettingsTest {
    @Test
    fun presetAccentFollowsTheTextMode() {
        assertEquals(Rgb(0x9E2A3A), PlumThemeSettings.Paper.accentColor)
        assertEquals(Rgb(0xDA4655), PlumThemeSettings.Graphite.accentColor)
    }

    @Test
    fun monoIsAlwaysTheCurrentTextColour() {
        val mono = PlumThemeSettings(accent = AccentChoice.Preset(AccentPreset.Mono))
        assertEquals(Rgb(0xEDEDED), mono.accentColor)
        assertEquals(Rgb(0x17181A), PlumThemeSettings.Paper.copy(accent = AccentChoice.Preset(AccentPreset.Mono)).accentColor)
    }

    @Test
    fun customAccentStaysAsPickedWhenTheTextModeChanges() {
        // 3:1 or more on both Graphite and Paper, so neither is adjusted.
        val custom = PlumThemeSettings.Graphite.copy(accent = AccentChoice.Custom(Rgb(0x2E8B57)))
        assertEquals(Rgb(0x2E8B57), custom.accentColor)
        assertEquals(Rgb(0x2E8B57), PlumThemeSettings.Paper.copy(accent = custom.accent).accentColor)
    }

    @Test
    fun backgroundIsAPresetOrThePersonsOwnColour() {
        assertEquals(Rgb(0x0E0F11), PlumThemeSettings.Graphite.backgroundColor)
        assertEquals(Rgb(0xF4F3EF), PlumThemeSettings.Paper.backgroundColor)
        val own = PlumThemeSettings(background = BackgroundChoice.Custom(CustomPalette.backgroundSwatches[9]))
        assertEquals(Rgb(0xF5A9A6), own.backgroundColor)
    }

    @Test
    fun legacyNavigationBarScrimKeepsWhiteButtonsReadable() {
        // Android 6.0–7.1 draws white navigation buttons on this fill when the text is dark.
        val scrim = PlumThemeSettings.Paper.legacyNavigationBarScrim
        assertTrue(ColorMath.contrast(Rgb(0xFFFFFF), scrim) >= 4.5)
        assertEquals(0xFF17181A.toInt(), scrim.toArgb())
    }

    @Test
    fun textModeFollowsTheContrastOfTheBackground() {
        for (p in BackgroundPreset.entries) {
            val expected = if (p == BackgroundPreset.Paper || p == BackgroundPreset.Sand || p == BackgroundPreset.Mist) TextMode.Dark else TextMode.Light
            assertEquals(p.name, expected, TextMode.bestOn(p.color))
        }
        assertEquals(TextMode.Dark, TextMode.bestOn(Rgb(0xFFFFFF)))
        assertEquals(TextMode.Light, TextMode.bestOn(Rgb(0x101010)))
    }

    @Test
    fun theDefaultLookIsBlackWithLightTextAndRed() {
        assertEquals(Rgb(0x000000), PlumThemeSettings.Default.backgroundColor)
        assertEquals(TextMode.Light, PlumThemeSettings.Default.textMode)
        assertEquals(Rgb(0xDA4655), PlumThemeSettings.Default.accentColor)
    }
}
