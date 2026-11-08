package com.plumemu.settings

import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.theme.ColorSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Plumemu on true Black (setup's look on an OLED screen) is the Plumemu set, and leaving it never loses the black page. */
class PageVariantTest {
    private val oled = AppearanceSettings(background = ColorSetting.Preset("Black"), textColor = TextColorSetting.Light, accent = ColorSetting.Custom(0x3A7CEB))

    @Test
    fun trueBlackUnderThePlumemuAccentIsThePlumemuSet() {
        assertEquals(ColorSet.Plumemu, oled.colorSet())
        assertNull("no Custom entry while on the set", oled.customColors())
    }

    @Test
    fun goingToAnotherSetAndBackBringsTheBlackPageBack() {
        val daylight = oled.withColors(ColorSet.Daylight)
        assertEquals(ColorSet.Daylight, daylight.colorSet())
        val back = daylight.withColors(ColorSet.Plumemu)
        assertEquals(ColorSet.Plumemu, back.colorSet())
        assertEquals(ColorSetting.Preset("Black"), back.background)
        assertEquals(ColorSetting.Custom(0x3A7CEB), back.accent)
    }

    @Test
    fun plumemuOnItsInkPageStaysOnTheInk() {
        val ink = AppearanceSettings().withColors(ColorSet.Daylight).withColors(ColorSet.Plumemu)
        assertEquals(ColorSet.Plumemu.background.color.value, (ink.background as ColorSetting.Custom).rgb and 0xFFFFFF)
    }
}
