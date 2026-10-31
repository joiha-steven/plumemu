package com.plumemu.settings

import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.theme.ColorSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Whose A/B/X/Y glyph colours are in use: the set last picked, not an exact colour match. */
class ButtonSetTest {
    private val fresh = AppearanceSettings()

    @Test
    fun aFreshInstallHasNoButtonColours() {
        assertNull(fresh.effectiveButtonSet())
        assertNull(fresh.toThemeSettings(animationsOn = true).buttonSet)
    }

    @Test
    fun pickingIvoryThenChangingTheBackgroundKeepsIvoryButtonsAndColorsReadsCustom() {
        val ivory = fresh.withColors(ColorSet.Ivory)
        assertEquals(ColorSet.Ivory, ivory.effectiveButtonSet())
        val tuned = ivory.copy(background = ColorSetting.Preset("Graphite"))
        assertNull("Colors reads Custom", tuned.colorSet())
        assertEquals("the buttons stay Ivory's", ColorSet.Ivory, tuned.effectiveButtonSet())
        assertEquals(ColorSet.Ivory, tuned.toThemeSettings(animationsOn = true).buttonSet)
        // Text color and Accent color by hand do not change it either.
        assertEquals(ColorSet.Ivory, tuned.copy(textColor = TextColorSetting.Light).effectiveButtonSet())
        assertEquals(ColorSet.Ivory, tuned.copy(accent = ColorSetting.Preset("Blue")).effectiveButtonSet())
    }

    @Test
    fun pickingASetWithoutButtonsBringsTheUsualGlyphsBack() {
        val ivory = fresh.withColors(ColorSet.Ivory)
        val paper = ivory.withColors(ColorSet.Paper)
        assertEquals(AppearanceSettings.BUTTONS_NONE, paper.buttonSet)
        assertNull(paper.effectiveButtonSet())
        // Super and Charcoal swap in their own colours.
        assertEquals(ColorSet.Super, paper.withColors(ColorSet.Super).effectiveButtonSet())
        assertEquals(ColorSet.Charcoal, paper.withColors(ColorSet.Charcoal).effectiveButtonSet())
    }

    /** An install from before the setting: colours exactly Ivory's and no stored value read as Ivory's buttons, and settling writes it. */
    @Test
    fun coloursThatAreExactlyIvoryWithNoStoredValueKeepIvoryButtonsAndSettleThem() {
        val legacy = fresh.copy(background = ColorSet.Ivory.background.toSetting(), textColor = ColorSet.Ivory.textMode.toSetting(), accent = ColorSet.Ivory.accent.toSetting())
        assertNull(legacy.buttonSet)
        assertEquals(ColorSet.Ivory, legacy.effectiveButtonSet())
        val settled = legacy.settleButtonSet()
        assertEquals("Ivory", settled.buttonSet)
        // Later hand changes can no longer lose it.
        assertEquals(ColorSet.Ivory, settled.copy(background = ColorSetting.Preset("Graphite")).effectiveButtonSet())
        // Colours that match no set, or a set without buttons, settle to none.
        assertEquals(AppearanceSettings.BUTTONS_NONE, fresh.copy(background = ColorSetting.Preset("Graphite")).settleButtonSet().buttonSet)
        assertEquals(AppearanceSettings.BUTTONS_NONE, fresh.settleButtonSet().buttonSet)
        // Settling twice changes nothing.
        assertEquals(settled, settled.settleButtonSet())
    }

    /** Custom brings the own colours back with the buttons that were in use when they were kept. */
    @Test
    fun customBringsBackTheButtonsThatWentWithTheOwnColours() {
        val tunedIvory = fresh.withColors(ColorSet.Ivory).copy(background = ColorSetting.Preset("Graphite"))
        val onPaper = tunedIvory.withColors(ColorSet.Paper)
        assertNull("Paper has no buttons", onPaper.effectiveButtonSet())
        assertEquals("Ivory", onPaper.ownColors?.buttonSet)
        val back = onPaper.withColors(null)
        assertNull(back.colorSet())
        assertEquals(ColorSetting.Preset("Graphite"), back.background)
        assertEquals(ColorSet.Ivory, back.effectiveButtonSet())
    }

    /** Ivory's three colours typed by hand on Plumemu, then Ivory picked: the pick still brings Ivory's buttons. */
    @Test
    fun pickingTheSetWhoseColoursAreAlreadyThereStillStoresItsButtons() {
        val typed = fresh.copy(background = ColorSet.Ivory.background.toSetting(), textColor = ColorSet.Ivory.textMode.toSetting(), accent = ColorSet.Ivory.accent.toSetting(), buttonSet = AppearanceSettings.BUTTONS_NONE)
        assertEquals(ColorSet.Ivory, typed.colorSet())
        assertNull(typed.effectiveButtonSet())
        assertEquals(ColorSet.Ivory, typed.withColors(ColorSet.Ivory).effectiveButtonSet())
        assertEquals("nothing changes when the buttons already match", fresh, fresh.withColors(ColorSet.Plumemu))
    }
}
