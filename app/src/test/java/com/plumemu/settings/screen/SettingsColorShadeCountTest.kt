package com.plumemu.settings.screen

import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.ColorShades
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.ShadeRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shade count changes with the stop on some backgrounds, so the Color code row's index moves under the cursor. */
internal class SettingsColorShadeCountTest : SettingsModelHarness() {
    /** A vivid background where Accent's shade count differs between stops, and a code on a stop with another count than [from]. */
    private fun backgroundWithVaryingCounts(): Rgb =
        ColorPages.vividBackgrounds.map { it.color }.first { bg ->
            (0 until ColorShades.stopCount(ShadeRole.Accent)).map { ColorShades.shades(ShadeRole.Accent, it, bg).size }.toSet().size > 1
        }

    private fun openAccentOn(bg: Rgb) {
        useAndroidKeyboard()
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = ColorSetting.Custom(bg.value), accent = ColorSetting.Preset("Red")))
        model.forgetColorWrites()
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        toHexRow()
    }

    @Test
    fun aTypedCodeOnAStopWithAnotherShadeCountKeepsTheCursorOnTheColorCodeRow() {
        val bg = backgroundWithVaryingCounts()
        openAccentOn(bg)
        val here = ColorShades.shades(ShadeRole.Accent, model.colorStop, bg).size
        val other = (0 until ColorShades.stopCount(ShadeRole.Accent)).first { ColorShades.shades(ShadeRole.Accent, it, bg).size.let { n -> n != here && n > 0 } }
        val code = ColorShades.shades(ShadeRole.Accent, other, bg).first()
        press(Action.Enter)
        model.onHexTextChange("%06X".format(code.value and 0xFFFFFF))
        model.onHexEntered(model.hexText)
        assertEquals("the typed code moved the strip to a stop with another count", other, model.colorStop)
        assertEquals("the cursor is on the Color code row", ColorPart.Hex, onPart)
        assertEquals(model.hexIndex(colourPage), model.focus.panelIndex)
        press(Action.Enter)
        assertTrue("A types again", model.isEditingHex)
    }

    @Test
    fun aTouchOnTheStripFromAnyPartBringsTheCursorToTheColorRowWhateverTheShadeCount() {
        val bg = backgroundWithVaryingCounts()
        openAccentOn(bg)
        repeat(ColorShades.stopCount(ShadeRole.Accent)) { stop ->
            model.onTouchStop(stop, actions.shown, wide, drag = false)
            assertEquals("stop $stop: the cursor is on Color", ColorPart.Color, onPart)
            move(Direction.Down)
            move(Direction.Down)
            assertTrue("stop $stop: Down twice is on the Color code row or a shade", onPart == ColorPart.Hex || onPart == ColorPart.Shade)
        }
    }
}
