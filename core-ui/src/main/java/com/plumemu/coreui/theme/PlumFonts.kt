package com.plumemu.coreui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.plumemu.coreui.R

/**
 * The three Plum typefaces, built from `plum-fonts/dist` (Sans 0.0.4, Serif 0.0.3, Mono
 * 0.0.3). Sans and Serif ship 400, 500, 600, 700 and 800; Mono 400, 500, 700 and 800. Plum Sans is the
 * default; per-font tuning is [PlumFont].
 *
 * Every weight a text role can ask for ([TextWeight] moves the design's 400 to 600 up to 800) has a face of
 * its own, so Compose never falls back by itself (the CSS matching it follows would draw a missing weight with
 * the wrong face and may synthesise bold). The one weight with no face is Mono 600, declared on the 700 face.
 * Sans and Serif have no 700 face of their own, so an asked-for 700 draws the 600 face; [TextWeight.Thin] still
 * asks 600 where it used to ask 700. `PlumFontsTest` pins this table.
 */
object PlumFonts {
    val Sans: FontFamily =
        FontFamily(
            Font(R.font.plum_sans_400, FontWeight.Normal),
            Font(R.font.plum_sans_500, FontWeight.Medium),
            Font(R.font.plum_sans_600, FontWeight.SemiBold),
            Font(R.font.plum_sans_700, FontWeight.Bold),
            Font(R.font.plum_sans_800, FontWeight.ExtraBold),
        )

    val Serif: FontFamily =
        FontFamily(
            Font(R.font.plum_serif_400, FontWeight.Normal),
            Font(R.font.plum_serif_500, FontWeight.Medium),
            Font(R.font.plum_serif_600, FontWeight.SemiBold),
            Font(R.font.plum_serif_700, FontWeight.Bold),
            Font(R.font.plum_serif_800, FontWeight.ExtraBold),
        )

    val Mono: FontFamily =
        FontFamily(
            Font(R.font.plum_mono_400, FontWeight.Normal),
            Font(R.font.plum_mono_500, FontWeight.Medium),
            Font(R.font.plum_mono_700, FontWeight.SemiBold),
            Font(R.font.plum_mono_700, FontWeight.Bold),
            Font(R.font.plum_mono_800, FontWeight.ExtraBold),
        )

    fun familyOf(font: PlumFont): FontFamily =
        when (font) {
            PlumFont.Sans -> Sans
            PlumFont.Serif -> Serif
            PlumFont.Mono -> Mono
        }
}
