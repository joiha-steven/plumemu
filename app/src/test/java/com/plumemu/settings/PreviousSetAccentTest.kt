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
    ) = AppearanceSettings(
        background = ColorSetting.Custom(set.backgroundColor.value),
        textColor = if (set.textMode == TextMode.Dark) TextColorSetting.Dark else TextColorSetting.Light,
        accent = ColorSetting.Custom(accent),
    )

    @Test
    fun theOldAccentOfSuperDotMatrixAndIvoryReadsAsThatSetAndDrawsTheNewAccent() {
        for ((set, old) in listOf(ColorSet.Super to 0xB22B26, ColorSet.DotMatrix to 0x306130, ColorSet.Ivory to 0x1792B0)) {
            val stored = storedWith(set, old)
            assertEquals("$set reads as itself", set, stored.colorSet())
            assertEquals("$set draws its new accent", set.accentColor, stored.toThemeSettings(animationsOn = true).accentColor)
            assertEquals("stored value untouched", ColorSetting.Custom(old), stored.accent)
        }
    }

    @Test
    fun aGenuinelyCustomAccentNearTheOldOneStaysCustom() {
        assertNull(storedWith(ColorSet.Super, 0xB32B26).colorSet())
        assertEquals(Rgb(0xB32B26), storedWith(ColorSet.Super, 0xB32B26).toThemeSettings(animationsOn = true).accentColor)
        // The old accent with another background is not Super either.
        assertNull(storedWith(ColorSet.Super, 0xB22B26).copy(background = ColorSetting.Preset("Paper")).colorSet())
    }
}
