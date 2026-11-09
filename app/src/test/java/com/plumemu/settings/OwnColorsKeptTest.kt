package com.plumemu.settings

import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.CustomPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A person's own colours survive a walk through the sets. The walk wears Plumemu on true Black (a set on one of its
 * other pages) on the way: leaving that page must not put it in place of the colours Custom still stands for.
 */
class OwnColorsKeptTest {
    private val own =
        AppearanceSettings(
            // Lemon, a tile of the Background page.
            background = ColorSetting.Custom(CustomPalette.backgroundAt(0, 3).value),
            textColor = TextColorSetting.Dark,
            accent = ColorSetting.Custom(0x0A7B26),
            buttonSet = "Ivory",
        )

    /** One press on the Colors row: the next (or previous) value of the cycle, as the row steps it. */
    private fun AppearanceSettings.step(by: Int): AppearanceSettings {
        val cycle = colorsCycle()
        return withColors(cycle[cycle.indexOf(colorSet()) + by])
    }

    private fun AppearanceSettings.assertCustomIsTheOwnColours(what: String) {
        val custom = customColors()
        assertTrue("$what: the Colors row still offers Custom", null in colorsCycle())
        assertEquals("$what: Custom's background", own.background, custom?.background)
        assertEquals("$what: Custom's text", own.textColor, custom?.textColor)
        assertEquals("$what: Custom's accent", own.accent, custom?.accent)
        assertEquals("$what: Custom's buttons", "Ivory", customButtonSet()?.name)
    }

    @Test
    fun steppingTheSetsFromPlumemuOnTrueBlackKeepsCustom() {
        assertNull("the own colours match no set", own.colorSet())
        val black = own.withColors(ColorSet.Plumemu).writeBackground(ColorSetting.Preset("Black")).appearance
        assertEquals("true Black under Plumemu's accent is the Plumemu set", ColorSet.Plumemu, black.colorSet())
        black.assertCustomIsTheOwnColours("on true Black")

        var walked = black
        repeat(3) { walked = walked.step(+1) }
        walked.assertCustomIsTheOwnColours("three sets on")
        repeat(3) { walked = walked.step(-1) }
        assertEquals(ColorSet.Plumemu, walked.colorSet())
        walked.assertCustomIsTheOwnColours("back on Plumemu")

        val custom = walked.withColors(null)
        assertEquals(own.background, custom.background)
        assertEquals(own.textColor, custom.textColor)
        assertEquals(own.accent, custom.accent)
    }
}
