package com.plumemu.settings.screen

import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.AccentPreset
import com.plumemu.settings.colorSet
import com.plumemu.settings.toThemeSettings
import com.plumemu.usage.settingChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Accent color › By system in the Settings panel: the tile after the presets,
 * reachable with the D-pad and a tap, keeping the accent the person had as its fallback, left by
 * picking any other accent, counted as "by_system" and matching no ready-made set.
 */
internal class SettingsAccentBySystemTest : SettingsModelHarness() {
    private val bySystemIndex = ColorPages.bySystemIndex

    private fun useAccent(accent: ColorSetting) {
        actions.settings = actions.settings.copy(appearance = appearance.copy(accent = accent))
        model.forgetColorWrites()
    }

    private fun openAccentPanel(expect: PanelPage = PanelPage.AccentPresets) {
        if (model.focus.column == SettingsColumn.Sections) openSection(SettingsSection.Appearance)
        repeat(12) { move(Direction.Up) }
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        assertEquals(expect, model.focus.page)
    }

    /** Walks to [index] of the presets page: Right along a row, Down when Right stops. Bounded. */
    private fun walkTo(index: Int) {
        repeat(20) {
            val at = model.focus.panelIndex
            if (at == index) return
            val row = at / wide.presetColumns
            val target = index / wide.presetColumns
            move(if (row < target) Direction.Down else if (row > target) Direction.Up else if (at < index) Direction.Right else Direction.Left)
        }
        assertEquals("reached the tile", index, model.focus.panelIndex)
    }

    @Test
    fun theTileComesAfterThePresetsAndBeforePlus() {
        assertEquals(AccentPreset.entries.size, bySystemIndex)
        val background = actions.settings.toThemeSettings().backgroundColor
        assertEquals("presets, By system, +", AccentPreset.entries.size + 2, ColorPages.presetCount(PanelPage.AccentPresets, background))
        assertEquals("the other panels are unchanged", BACKGROUND_COUNT + 1, ColorPages.presetCount(PanelPage.BackgroundPresets, background))
    }

    @Test
    fun theDpadReachesTheTilePreviewsItAndAKeepsTheAccentAsItsFallback() {
        useAccent(ColorSetting.Preset("Blue"))
        openAccentPanel()
        assertEquals("opens on the current preset", AccentPreset.Blue.ordinal, model.focus.panelIndex)
        walkTo(bySystemIndex)
        assertEquals(ColorSetting.BySystem(ColorSetting.Preset("Blue")), appearance.accent)
        press(Action.Enter)
        assertNull("the panel closed", model.focus.page)
        assertEquals(ColorSetting.BySystem(ColorSetting.Preset("Blue")), appearance.accent)
        assertEquals("what shows with no system in view is the fallback", AccentPreset.Blue.colorFor(actions.settings.toThemeSettings().textMode), actions.settings.toThemeSettings().accentColor)
    }

    @Test
    fun theTileIsReachedFromAnOwnColourAndKeepsItAsTheFallback() {
        useAccent(ColorSetting.Custom(0x336699))
        // An own colour opens the palette page; B goes back to the presets page, on "+".
        openAccentPanel(expect = PanelPage.AccentCustom)
        press(Action.Back)
        assertEquals(PanelPage.AccentPresets, model.focus.page)
        walkTo(bySystemIndex)
        assertEquals(ColorSetting.BySystem(ColorSetting.Custom(0x336699)), appearance.accent)
        press(Action.Enter)
        assertEquals(ColorSetting.BySystem(ColorSetting.Custom(0x336699)), appearance.accent)
    }

    @Test
    fun aTapOnTheTileFocusesThenChoosesIt() {
        useAccent(ColorSetting.Preset("Green"))
        model.onTapSection(SettingsSection.Appearance)
        model.onTapSection(SettingsSection.Appearance)
        val row = SettingsSection.Appearance.options.indexOf(SettingsOption.Accent)
        model.onTapValue(row, actions.shown, wide)
        assertEquals(PanelPage.AccentPresets, model.focus.page)
        model.onTapPanelItem(bySystemIndex, actions.shown, wide)
        assertEquals(bySystemIndex, model.focus.panelIndex)
        assertEquals(ColorSetting.BySystem(ColorSetting.Preset("Green")), appearance.accent)
        model.onTapPanelItem(bySystemIndex, actions.shown, wide)
        assertNull("the second tap enters it and the panel closes", model.focus.page)
        assertEquals(ColorSetting.BySystem(ColorSetting.Preset("Green")), appearance.accent)
    }

    @Test
    fun reopeningWhileBySystemLandsOnTheTileAndPickingAPresetLeavesBySystem() {
        useAccent(ColorSetting.BySystem(ColorSetting.Preset("Amber")))
        openAccentPanel()
        assertEquals(bySystemIndex, model.focus.panelIndex)
        walkTo(AccentPreset.Lilac.ordinal)
        press(Action.Enter)
        assertEquals(ColorSetting.Preset("Lilac"), appearance.accent)
    }

    @Test
    fun movingOffTheTileAndBackKeepsTheFirstFallback() {
        useAccent(ColorSetting.Preset("Amber"))
        openAccentPanel()
        walkTo(bySystemIndex)
        walkTo(AccentPreset.Green.ordinal)
        assertEquals(ColorSetting.Preset("Green"), appearance.accent)
        walkTo(bySystemIndex)
        assertEquals("the accent the panel opened with", ColorSetting.BySystem(ColorSetting.Preset("Amber")), appearance.accent)
        press(Action.Back)
        assertEquals("B keeps what the cursor is on, as on any preset", ColorSetting.BySystem(ColorSetting.Preset("Amber")), appearance.accent)
    }

    @Test
    fun plusStillOpensTheOwnColourPageAfterTheTile() {
        openAccentPanel()
        walkTo(bySystemIndex + 1)
        press(Action.Enter)
        assertEquals(PanelPage.AccentCustom, model.focus.page)
    }

    @Test
    fun bySystemIsCountedAsByUnderscoreSystemNeverAColour() {
        val before = PlumSettings()
        val after = before.copy(appearance = before.appearance.copy(accent = ColorSetting.BySystem(ColorSetting.Custom(0x336699))))
        fun accentEvents(from: PlumSettings, to: PlumSettings) = settingChanges(from, to).filter { it.key == "accent" }
        assertEquals("by_system", accentEvents(before, after).single().value)
        // Changing only the fallback behind it is not a change of the choice.
        assertTrue(accentEvents(after, after.copy(appearance = after.appearance.copy(accent = ColorSetting.BySystem(ColorSetting.Preset("Blue"))))).isEmpty())
        // Back to a preset is counted as that preset.
        assertEquals("red", accentEvents(after, before).single().value)
    }

    @Test
    fun bySystemMatchesNoReadyMadeSetSoTheColorsRowReadsCustom() {
        val plumemu = AppearanceSettings()
        assertEquals(com.plumemu.coreui.theme.ColorSet.Plumemu, plumemu.colorSet())
        val bySystem = plumemu.copy(accent = ColorSetting.BySystem(ColorSetting.Preset(AppearanceSettings.DEFAULT_ACCENT)))
        assertNull(bySystem.colorSet())
        assertTrue(bySystem.toThemeSettings(animationsOn = true).accent is AccentChoice.BySystem)
    }

    private companion object {
        val BACKGROUND_COUNT = ColorPages.backgroundSwatchCount
    }
}
