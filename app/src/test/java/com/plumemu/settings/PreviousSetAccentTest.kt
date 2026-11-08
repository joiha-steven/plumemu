package com.plumemu.settings

import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.TextMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A set whose accent moved (a later theme balance) is still that set for people who stored the old accent: no "Custom" after the update. */
class PreviousSetAccentTest {
    private fun storedWith(
        set: ColorSet,
        accent: Int,
        background: Int = set.backgroundColor.value,
    ) = AppearanceSettings(
        background = ColorSetting.Custom(background),
        textColor = if (set.textMode == TextMode.Dark) TextColorSetting.Dark else TextColorSetting.Light,
        accent = ColorSetting.Custom(accent),
    )

    @Test
    fun theOldAccentOfIvoryReadsAsThatSetAndDrawsTheNewAccent() {
        val stored = storedWith(ColorSet.Ivory, 0x1792B0)
        assertEquals("reads as Ivory", ColorSet.Ivory, stored.colorSet())
        assertEquals("draws its new accent", ColorSet.Ivory.accentColor, stored.toThemeSettings(animationsOn = true).accentColor)
        assertEquals("stored value untouched", ColorSetting.Custom(0x1792B0), stored.accent)
    }

    @Test
    fun theOldLooksOfSuperAndDotMatrixReadAsIvoryAndPeaGreen() {
        val looks = listOf(Triple(ColorSet.Ivory, 0xD9D8D3, 0xB22B26), Triple(ColorSet.Ivory, 0xD9D8D3, 0xAA2924), Triple(ColorSet.PeaGreen, 0xB4DB11, 0x306130))
        for ((set, background, old) in looks) {
            val stored = storedWith(set, old, background)
            assertEquals("$set reads as itself", set, stored.colorSet())
            assertEquals("$set draws its background", set.backgroundColor, stored.toThemeSettings(animationsOn = true).backgroundColor)
            assertEquals("$set draws its new accent", set.accentColor, stored.toThemeSettings(animationsOn = true).accentColor)
            assertEquals("stored value untouched", ColorSetting.Custom(old), stored.accent)
        }
    }

    @Test
    fun aGenuinelyCustomAccentNearTheOldOneStaysCustom() {
        assertNull(storedWith(ColorSet.Ivory, 0xB32B26, 0xD9D8D3).colorSet())
        assertEquals(Rgb(0xB32B26), storedWith(ColorSet.Ivory, 0xB32B26, 0xD9D8D3).toThemeSettings(animationsOn = true).accentColor)
        // The old accent with another background is not Ivory either.
        assertNull(storedWith(ColorSet.Ivory, 0xB22B26, 0xD9D8D3).copy(background = ColorSetting.Preset("Graphite")).colorSet())
    }
}
