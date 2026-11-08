package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.OwnColors
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.settings.colorSet
import com.plumemu.settings.colorsCycle
import com.plumemu.settings.toSetting
import com.plumemu.settings.toThemeSettings
import com.plumemu.settings.withColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Appearance › Colors: ready-made sets over Background, Text color and Accent color. */
internal class SettingsColorsTest : SettingsModelHarness() {
    /** The value the row shows for what is stored now. */
    private fun shown(): Int = choiceName(SettingsOption.Colors, SettingsOption.Colors.choiceIndex(actions.settings, TextSizeSetting.Medium))

    private fun onColors(start: AppearanceSettings) {
        actions.settings = PlumSettings(appearance = start, flags = SetupFlags(setupCompleted = true))
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Colors)
    }

    private val graphite = AppearanceSettings(background = ColorSetting.Preset("Graphite"))

    @Test
    fun colorsIsAChoiceAtTheTopOfAppearanceAboveAccentColor() {
        assertEquals(
            listOf(SettingsOption.Language, SettingsOption.Colors, SettingsOption.Accent),
            SettingsSection.Appearance.options.take(3),
        )
        assertEquals(OptionKind.Choice, SettingsOption.Colors.kind)
    }

    /** Pocket and Banana moved their background with the dark text #1C1C1E: the old stored one is still the set. */
    @Test
    fun anOldStoredBackgroundOfAMovedSetStillShowsItsNameAndDrawsTheNewOne() {
        val moved = listOf(
            Triple(ColorSet.Pocket, 0xCDCCC4, R.string.settings_colors_pocket),
            Triple(ColorSet.Banana, 0xF2CD3C, R.string.settings_colors_banana),
        )
        for ((set, old, name) in moved) {
            val stored = AppearanceSettings(background = ColorSetting.Custom(old), textColor = TextColorSetting.Dark, accent = ColorSetting.Custom(set.accentColor.value))
            actions.settings = PlumSettings(appearance = stored)
            assertEquals("$set reads as itself", set, stored.colorSet())
            assertEquals("$set name in Settings", name, shown())
            assertEquals("$set draws the new background", set.backgroundColor, stored.toThemeSettings(animationsOn = true).backgroundColor)
            assertEquals("stored value untouched", ColorSetting.Custom(old), stored.background)
            // A near-but-different colour is Custom.
            val near = stored.copy(background = ColorSetting.Custom(old + 1))
            assertNull("$set near", near.colorSet())
            actions.settings = PlumSettings(appearance = near)
            assertEquals("$set near shows Custom", R.string.settings_color_custom, shown())
        }
    }

    /** The stored dark text is a name (Dark), so every dark-text set, Daylight included, still reads as itself. */
    @Test
    fun everyDarkTextSetReadsAsItselfFromStoredDarkText() {
        for (set in ColorSet.entries.filter { it.textMode == com.plumemu.coreui.theme.TextMode.Dark }) {
            val stored = AppearanceSettings(background = set.background.toSetting(), textColor = TextColorSetting.Dark, accent = set.accent.toSetting())
            assertEquals("$set", set, stored.colorSet())
        }
    }

    @Test
    fun aFreshInstallShowsPlumemuAndNoCustom() {
        actions.settings = PlumSettings.Defaults
        assertEquals(ColorSet.Plumemu, PlumSettings.Defaults.appearance.colorSet())
        assertEquals(R.string.settings_colors_plumemu, shown())
        assertEquals("17 sets, no Custom without own colours", 17, SettingsOption.Colors.choiceCount(actions.settings))
    }

    @Test
    fun pickingASetMovesAllThreeRowsToItsValues() {
        onColors(PlumSettings.Defaults.appearance)
        move(Direction.Right)
        assertEquals("a set is stored as own-colour hexes", ColorSetting.Custom(0xECEBE5), appearance.background)
        assertEquals(TextColorSetting.Dark, appearance.textColor)
        assertEquals(ColorSetting.Custom(0x2A64D8), appearance.accent)
        assertEquals(R.string.settings_colors_daylight, shown())
        move(Direction.Right)
        assertEquals(ColorSetting.Custom(0x0F2142), appearance.background)
        assertEquals(TextColorSetting.Light, appearance.textColor)
        assertEquals(ColorSetting.Custom(0xF4B740), appearance.accent)
        assertEquals(R.string.settings_colors_ultramarine, shown())
        // Every set, stepped to in order, is the one the row then shows, with its three values.
        for (set in ColorSet.entries.drop(3)) {
            move(Direction.Right)
            assertEquals(set, appearance.colorSet())
            assertEquals(set.textMode.name, appearance.textColor.stored)
        }
        assertEquals(R.string.settings_colors_banana, shown())
        assertNull("sets picked from sets keep no own colours", appearance.ownColors)
        move(Direction.Right)
        assertEquals("right stops at the last set", ColorSet.Banana, appearance.colorSet())
        press(Action.Enter)
        assertEquals("A opens the panel instead of stepping", PanelPage.ColorSets, model.focus.page)
        assertEquals(ColorSet.Banana, appearance.colorSet())
        press(Action.Back)
        pickInColorsPanel(ColorSet.Plumemu)
        assertEquals(ColorSet.Plumemu, appearance.colorSet())
        move(Direction.Left)
        assertEquals("left stops at the first", ColorSet.Plumemu, appearance.colorSet())
        // Back on Plumemu nothing else has moved: the font, size and the rest are not colours.
        assertEquals(PlumSettings.Defaults.appearance, appearance.copy(buttonSet = null))
    }

    @Test
    fun everySetIsStoredAsItsOwnHexColoursNeverAPresetName() {
        onColors(PlumSettings.Defaults.appearance)
        repeat(ColorSet.ColdGrey.ordinal) { move(Direction.Right) }
        assertEquals(ColorSet.ColdGrey, appearance.colorSet())
        assertEquals(ColorSetting.Custom(0x8EC3FF), appearance.accent)
        assertEquals(ColorSetting.Custom(0x272B32), appearance.background)
        for (set in ColorSet.entries) {
            val stored = AppearanceSettings().withColors(ColorSet.Banana).withColors(set)
            assertTrue("$set background", stored.background is ColorSetting.Custom)
            assertTrue("$set accent", stored.accent is ColorSetting.Custom)
        }
    }

    @Test
    fun changingAnyOfTheThreeRowsByHandMakesItCustom() {
        val pocket = PlumSettings.Defaults.appearance.copy(background = ColorSetting.Custom(0xD2D1CA), textColor = TextColorSetting.Dark, accent = ColorSetting.Custom(0x8C1D4E))
        assertEquals(ColorSet.Pocket, pocket.colorSet())
        for (edited in listOf(
            pocket.copy(background = ColorSetting.Custom(0xCDCCC5)),
            pocket.copy(textColor = TextColorSetting.Custom(0x000000)),
            pocket.copy(accent = ColorSetting.Preset("Blue")),
        )) {
            actions.settings = PlumSettings(appearance = edited)
            assertNull("$edited", edited.colorSet())
            assertEquals(R.string.settings_color_custom, shown())
            assertEquals("Custom is the value it is on", 18, SettingsOption.Colors.choiceCount(actions.settings))
        }
        // Through the model: Text color › Light on Pocket is refused (unreadable), so a hand change that
        // reads is needed: a different accent from the Accent row's panel is covered above.
        onColors(pocket)
        focusOption(SettingsOption.TextColor)
        move(Direction.Left)
        assertEquals("Light cannot be read on Pocket: refused", TextColorSetting.Dark, appearance.textColor)
        assertEquals("so Pocket is still the set", ColorSet.Pocket, appearance.colorSet())
        actions.settings = PlumSettings(appearance = pocket.copy(background = ColorSetting.Custom(0xCDCCC5)))
        assertNull(appearance.colorSet())
        assertEquals(R.string.settings_color_custom, shown())
    }

    @Test
    fun customToASetAndBackRestoresTheOwnColoursExactly() {
        val own = AppearanceSettings(background = ColorSetting.Custom(0x2C1111), textColor = TextColorSetting.Light, accent = ColorSetting.Custom(0x123456))
        onColors(own)
        assertEquals(R.string.settings_color_custom, shown())
        assertEquals("Custom sits after the sets", ColorSet.entries.size, SettingsOption.Colors.choiceIndex(actions.settings, TextSizeSetting.Medium))
        move(Direction.Right)
        assertEquals("right stops at Custom, the last value", own, appearance)
        pickInColorsPanel(ColorSet.Plumemu) // Custom → Plumemu
        assertEquals(ColorSet.Plumemu, appearance.colorSet())
        assertEquals(OwnColors(own.background, own.textColor, own.accent, buttonSet = "None"), appearance.ownColors)
        move(Direction.Right)
        assertEquals(ColorSet.Daylight, appearance.colorSet())
        assertEquals("a set picked from a set keeps them", OwnColors(own.background, own.textColor, own.accent, buttonSet = "None"), appearance.ownColors)
        assertEquals("Custom stays in the cycle", 18, SettingsOption.Colors.choiceCount(actions.settings))
        move(Direction.Left)
        move(Direction.Left)
        assertEquals("left stops at the first set", ColorSet.Plumemu, appearance.colorSet())
        repeat(ColorSet.entries.size) { move(Direction.Right) }
        assertEquals("Custom puts back all three exactly", own, appearance.copy(ownColors = null, buttonSet = null))
        assertEquals(R.string.settings_color_custom, shown())
        // And again: to a set and back is the same three.
        pickInColorsPanel(ColorSet.Plumemu)
        repeat(ColorSet.entries.size) { move(Direction.Right) }
        assertEquals(own, appearance.copy(ownColors = null, buttonSet = null))
    }

    @Test
    fun ownColoursEditedOnASetAreKeptInsteadOfTheOldOnesWhenTheNextSetIsPicked() {
        onColors(graphite)
        pickInColorsPanel(ColorSet.Plumemu) // Graphite kept, Plumemu on
        assertEquals(ColorSetting.Preset("Graphite"), appearance.ownColors?.background)
        // Background by hand on Plumemu: Custom with a new look, which is what the next pick keeps.
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = ColorSetting.Preset("Plum")))
        assertNull(appearance.colorSet())
        move(Direction.Left)
        assertEquals(ColorSet.Banana, appearance.colorSet())
        assertEquals(ColorSetting.Preset("Plum"), appearance.ownColors?.background)
    }

    @Test
    fun withoutOwnColoursTheCycleHasNoCustom() {
        // The removed Super set's stored look (own colours) reads as Ivory.
        val onSuper = PlumSettings.Defaults.appearance.copy(background = ColorSetting.Custom(0xD9D8D3), textColor = TextColorSetting.Dark, accent = ColorSetting.Custom(0xAA2924))
        assertEquals(ColorSet.entries.toList(), onSuper.colorsCycle())
        onColors(onSuper)
        repeat(ColorSet.entries.size) { move(Direction.Right) }
        assertEquals("right stops at the last set: no Custom after it", ColorSet.Banana, appearance.colorSet())
        assertNull(appearance.ownColors)
        // Own colours that are a set's (a hand-made file) bring nothing a set does not: no Custom either.
        val keptAsSet = onSuper.copy(ownColors = OwnColors(ColorSetting.Preset("Black"), TextColorSetting.Light, ColorSetting.Custom(0x3E7BE0)))
        assertFalse(null in keptAsSet.colorsCycle())
    }

    /** Presses faster than the store answers each count, from what is stored (like every choice). */
    @Test
    fun quickPressesEachStepFromTheStoredValue() {
        onColors(graphite)
        move(Direction.Left)
        move(Direction.Left)
        move(Direction.Left)
        assertEquals("Custom, then Banana, Cold grey, Clear grape", ColorSet.ClearGrape, appearance.colorSet())
        assertEquals("the own colours are the ones before the first press", ColorSetting.Preset("Graphite"), appearance.ownColors?.background)
    }

    @Test
    fun resetAllSettingsForgetsTheOwnColours() {
        actions.settings = PlumSettings(appearance = PlumSettings.Defaults.appearance.copy(ownColors = OwnColors(ColorSetting.Preset("Plum"), TextColorSetting.Light, ColorSetting.Preset("Amber"))))
        assertTrue(null in appearance.colorsCycle())
        assertNull(actions.settings.resetKeepingFlags().appearance.ownColors)
        assertEquals("Reset all writes no buttons, not undecided", AppearanceSettings.BUTTONS_NONE, actions.settings.resetKeepingFlags().appearance.buttonSet)
    }
}
