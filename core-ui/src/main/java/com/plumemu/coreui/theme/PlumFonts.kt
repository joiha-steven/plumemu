package com.plumemu.coreui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.plumemu.coreui.R

/**
 * The typefaces: Jost (static faces built from the OFL variable font, `tools/fonts/build_jost.py`) and the three Plum ones,
 * built from `plum-fonts/dist` (Sans 0.0.4, Serif 0.0.3, Mono 0.0.3). Jost, Sans and Serif ship 400, 500, 600, 700
 * and 800; Mono 400, 500, 700 and 800. Jost is the default; per-font tuning is [PlumFont].
 *
 * Every weight a text role can ask for (500 to 800, [TypeRole.weight]) has a face of its own, so Compose never falls back by itself
 * (the CSS matching it follows would draw a missing weight with the wrong face and may synthesise bold). The one weight with no face
 * is Mono 600, declared on the 700 face. `PlumFontsTest` pins this table.
 */
object PlumFonts {
    val Jost: FontFamily =
        FontFamily(
            Font(R.font.jost_400, FontWeight.Normal),
            Font(R.font.jost_500, FontWeight.Medium),
            Font(R.font.jost_600, FontWeight.SemiBold),
            Font(R.font.jost_700, FontWeight.Bold),
            Font(R.font.jost_800, FontWeight.ExtraBold),
        )

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
            PlumFont.Jost -> Jost
            PlumFont.Sans -> Sans
            PlumFont.Serif -> Serif
            PlumFont.Mono -> Mono
        }
}
