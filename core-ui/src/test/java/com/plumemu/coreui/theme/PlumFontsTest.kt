package com.plumemu.coreui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import com.plumemu.coreui.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins which face draws each design weight, so 500 can never silently fall back to the 600 face. */
class PlumFontsTest {
    private fun faceFor(
        family: FontFamily,
        weight: FontWeight,
    ): Int {
        val fonts = (family as androidx.compose.ui.text.font.FontListFontFamily).fonts
        val font: Font = fonts.single { it.weight == weight }
        return (font as ResourceFont).resId
    }

    @Test
    fun sansMapsEveryWeightToItsFace() {
        assertEquals(R.font.plum_sans_400, faceFor(PlumFonts.Sans, FontWeight.Normal))
        assertEquals(R.font.plum_sans_500, faceFor(PlumFonts.Sans, FontWeight.Medium))
        assertEquals(R.font.plum_sans_600, faceFor(PlumFonts.Sans, FontWeight.SemiBold))
        assertEquals(R.font.plum_sans_700, faceFor(PlumFonts.Sans, FontWeight.Bold))
        assertEquals(R.font.plum_sans_800, faceFor(PlumFonts.Sans, FontWeight.ExtraBold))
    }

    @Test
    fun serifMapsEveryWeightToItsFace() {
        assertEquals(R.font.plum_serif_400, faceFor(PlumFonts.Serif, FontWeight.Normal))
        assertEquals(R.font.plum_serif_500, faceFor(PlumFonts.Serif, FontWeight.Medium))
        assertEquals(R.font.plum_serif_600, faceFor(PlumFonts.Serif, FontWeight.SemiBold))
        assertEquals(R.font.plum_serif_700, faceFor(PlumFonts.Serif, FontWeight.Bold))
        assertEquals(R.font.plum_serif_800, faceFor(PlumFonts.Serif, FontWeight.ExtraBold))
    }

    @Test
    fun monoMapsEveryWeightToItsFace() {
        assertEquals(R.font.plum_mono_400, faceFor(PlumFonts.Mono, FontWeight.Normal))
        assertEquals(R.font.plum_mono_500, faceFor(PlumFonts.Mono, FontWeight.Medium))
        assertEquals(R.font.plum_mono_700, faceFor(PlumFonts.Mono, FontWeight.SemiBold))
        assertEquals(R.font.plum_mono_700, faceFor(PlumFonts.Mono, FontWeight.Bold))
        assertEquals(R.font.plum_mono_800, faceFor(PlumFonts.Mono, FontWeight.ExtraBold))
    }

    @Test
    fun focusedRowWeightReachesTheMediumFaceInEveryFont() {
        // 500 is what a focused row asks for at Thin; no dark-background adjustment may move it off the 500 face.
        for (font in PlumFont.entries) {
            for (mode in TextMode.entries) {
                assertEquals(500, TypeRole.GameTitleFocused.weightFor(font, mode, TextWeight.Thin))
            }
        }
    }

    @Test
    fun thinDrawsTheSameFaceAsBeforeTheSetting() {
        // The badge once asked 700 and Sans and Serif drew the 600 face; Thin must still draw it.
        for (font in PlumFont.entries) {
            val asked = TypeRole.Badge.weightFor(font, TextMode.Light, TextWeight.Thin)
            val face = faceFor(PlumFonts.familyOf(font), FontWeight(asked).let { w -> if (font == PlumFont.Mono && asked == 600) FontWeight.SemiBold else w })
            val expected = if (font == PlumFont.Mono) R.font.plum_mono_700 else if (font == PlumFont.Sans) R.font.plum_sans_600 else R.font.plum_serif_600
            assertEquals("$font", expected, face)
        }
    }
}
