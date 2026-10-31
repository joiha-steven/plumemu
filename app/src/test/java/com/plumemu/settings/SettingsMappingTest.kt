package com.plumemu.settings

import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.ControlsSettings
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.TextWeightSetting
import com.plumemu.coreinput.ButtonLayout
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.InputSettings
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.AccentPreset
import com.plumemu.coreui.theme.BackgroundChoice
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.TextMode
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.TextWeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsMappingTest {
    @Test
    fun defaultsAreTheThemeAndInputDefaults() {
        assertEquals(PlumThemeSettings.Default, PlumSettings.Defaults.toThemeSettings())
        assertEquals("BUTTON_A (printed A on the Thor) confirms by default", InputSettings(ButtonLayout.Xbox), PlumSettings.Defaults.controls.toInputSettings())
    }

    @Test
    fun everyPresetNameCoreUiHasMapsBack() {
        for (preset in BackgroundPreset.entries) {
            assertEquals(BackgroundChoice.Preset(preset), ColorSetting.Preset(preset.name).toBackgroundChoice())
        }
        for (preset in AccentPreset.entries) {
            assertEquals(AccentChoice.Preset(preset), ColorSetting.Preset(preset.name).toAccentChoice())
        }
    }

    @Test
    fun aPresetThisVersionDoesNotKnowFallsBackToTheDefault() {
        assertEquals(BackgroundChoice.Preset(BackgroundPreset.Black), ColorSetting.Preset("Aurora").toBackgroundChoice())
        assertEquals(AccentChoice.Preset(AccentPreset.Default), ColorSetting.Preset("Teal").toAccentChoice())
    }

    @Test
    fun everyAppearanceFieldReachesTheTheme() {
        val stored = PlumSettings(
            appearance = AppearanceSettings(
                background = ColorSetting.Custom(0x203040),
                textColor = TextColorSetting.Dark,
                accent = ColorSetting.Custom(0xFF8800),
                font = FontSetting.Serif,
                textSize = TextSizeSetting.Small,
            ),
            advanced = AdvancedSettings(animations = false),
        )

        assertEquals(
            PlumThemeSettings(
                background = BackgroundChoice.Custom(Rgb(0x203040)),
                text = TextMode.Dark.color,
                accent = AccentChoice.Custom(Rgb(0xFF8800)),
                font = PlumFont.Serif,
                textSize = TextSize.Small,
                animationsOn = false,
            ),
            stored.toThemeSettings(),
        )
        assertEquals(PlumThemeSettings.Paper, PlumSettings(appearance = AppearanceSettings(ColorSetting.Preset("Paper"), TextColorSetting.Dark)).toThemeSettings())
        assertEquals(PlumFont.Mono, AppearanceSettings(font = FontSetting.Mono).toThemeSettings(true).font)
        assertEquals(TextSize.Large, AppearanceSettings(textSize = TextSizeSetting.Large).toThemeSettings(true).textSize)
    }

    @Test
    fun everyStoredTextSizeNameReachesTheThemeWithItsOwnFactor() {
        assertEquals(
            mapOf("Small" to 0.9f, "Medium" to 1.0f, "Large" to 1.1f, "Larger" to 1.2f, "Huge" to 1.3f, "Largest" to 1.45f),
            TextSizeSetting.entries.associate { it.name to AppearanceSettings(textSize = it).toThemeSettings(true).textSize!!.factor },
        )
    }

    @Test
    fun theStoredTextWeightReachesTheThemeAndNotPickedIsMedium() {
        assertEquals(TextWeight.Medium, AppearanceSettings().toThemeSettings(true).textWeight)
        for (setting in TextWeightSetting.entries) {
            assertEquals(setting.name, AppearanceSettings(textWeight = setting).toThemeSettings(true).textWeight.name)
        }
    }

    @Test
    fun noPickedTextSizeStaysUnsetAndTheDefaultIsLarge() {
        assertEquals(TextSize.Default.name, TextSizeSetting.Default.name)
        assertEquals(TextSize.Large, TextSize.Default)
        assertNull(AppearanceSettings().toThemeSettings(true).textSize)
        assertNull(PlumSettings().toThemeSettings().textSize)
    }

    @Test
    fun theConfirmButtonSwapsOnlyAAndB() {
        // The Thor sends by letter, so the Xbox layout reads Android's key names as the printed
        // letters; swapping A and B never moves X and Y.
        assertEquals(
            InputSettings(ButtonLayout.Xbox, swapAB = false, holdToScrollFaster = false),
            ControlsSettings(ConfirmButtonSetting.Right, holdToScrollFaster = false).toInputSettings(),
        )
        assertEquals(
            InputSettings(ButtonLayout.Xbox, swapAB = true, holdToScrollFaster = true),
            ControlsSettings(ConfirmButtonSetting.Bottom).toInputSettings(),
        )
    }

    @Test
    fun theKeyboardSettingPicksTheKeyboardOfEveryField() {
        assertEquals("Plumemu's keyboard by default", KeyboardChoice.Plumemu, ControlsSettings().toKeyboardChoice())
        assertEquals(KeyboardChoice.Android, ControlsSettings(keyboard = KeyboardSetting.Android).toKeyboardChoice())
    }

    @Test
    fun theRouterFollowsAStoredChangeLive() = runTest {
        val stored = MutableStateFlow(PlumSettings.Defaults)
        val router = InputRouter(backgroundScope, onDebug = {}, onStalled = {})
        backgroundScope.launch { router.followSettings(stored) }
        runCurrent()
        assertEquals(InputSettings(ButtonLayout.Xbox), router.settings)

        stored.value = stored.value.copy(controls = ControlsSettings(ConfirmButtonSetting.Bottom))
        runCurrent()
        assertEquals(InputSettings(ButtonLayout.Xbox, swapAB = true), router.settings)

        stored.value = stored.value.copy(controls = ControlsSettings(holdToScrollFaster = false))
        runCurrent()
        assertEquals(InputSettings(ButtonLayout.Xbox, holdToScrollFaster = false), router.settings)
    }

    @Test
    fun selectStoresOneSystemsViewAndKeepsTheOthersEvenOnesNoLongerInTheLibrary() {
        val stored = PlumSettings(appearance = AppearanceSettings(gridSystems = setOf("gone-system", "psp")))
        val grid = stored.withGridView("snes", isGrid = true)
        assertEquals(setOf("gone-system", "psp", "snes"), grid.appearance.gridSystems)
        assertEquals("nothing else changes", stored.copy(appearance = stored.appearance.copy(gridSystems = grid.appearance.gridSystems)), grid)
        assertEquals(setOf("gone-system", "snes"), grid.withGridView("psp", isGrid = false).appearance.gridSystems)
        assertEquals("a repeated press's write is harmless", grid, grid.withGridView("snes", isGrid = true))
    }
}
