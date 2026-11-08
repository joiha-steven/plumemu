package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

/** An app game card's colours: the gradient's [top] and [bottom], the system [band] and the [ink] of the title and band text. */
@Immutable
data class PosterLook(
    val top: Color,
    val bottom: Color,
    val band: Color,
    val ink: Color,
)

/** White ink of an app game card. */
val POSTER_INK_WHITE = Color.White

/** The inner frame of an app game's card. */
val POSTER_FRAME_COLOR = Color.White.copy(alpha = 0.35f)

/** The soft shadow under an app card's icon. */
val POSTER_SHADOW_COLOR = Color.Black.copy(alpha = 0.4f)

/** The smallest title size of a drawn box front, in sp: a cover too small for it shows no title. */
const val POSTER_TITLE_FLOOR_SP = 6f

/** The smallest size of the system name on an app game card's band, in sp. */
const val POSTER_BAND_FLOOR_SP = 6f

/** Letter spacing of the system name on an app game card's band, in em. */
const val POSTER_BAND_SPACING_EM = 0.12f

/** The most the title turns the system colour's hue, in degrees either way. */
const val BOX_HUE_SPREAD = 25

/** The big first letter: a lighter tone of the field at this strength. */
const val BOX_MARK_ALPHA = 0.12f

/** The grain over the field, the most one channel moves (of 255): fine monochrome noise like the cover pictures'. */
const val BOX_GRAIN = 12

/** The code (the system's short name) is the title's white at this strength. */
const val BOX_CODE_ALPHA = 0.8f

/** The field's base where no system is known: a grey, which the recipe leaves grey. */
val BOX_NEUTRAL_BASE = Color(0xFF8A8D96)

/** The year on the titled tile: the code's white at its strength. */
val BOX_YEAR_INK = Color.White.copy(alpha = BOX_CODE_ALPHA)

/** The contrast white keeps on the lightest part of the field with the big letter and the strongest grain on it; a little over 4.5:1. */
private const val BOX_TITLE_CONTRAST = 4.7

/** The field's colours: the lighter [top] (top left), the darker [bottom] (bottom right), the big letter's [mark], and the white [title] and [code] ink. */
@Immutable
class BoxFieldLook(
    val top: Color,
    val bottom: Color,
    val mark: Color,
) {
    val title: Color = Color.White
    val code: Color = Color.White.copy(alpha = BOX_CODE_ALPHA)
}

/** [c] with the strongest grain added: the lightest a pixel of that colour can be. */
fun boxLightestPixel(c: Rgb): Rgb = Rgb.fromChannels(minOf(255, c.red + BOX_GRAIN).toDouble(), minOf(255, c.green + BOX_GRAIN).toDouble(), minOf(255, c.blue + BOX_GRAIN).toDouble())

/** The turn of the hue for [title], in degrees from -[BOX_HUE_SPREAD] to +[BOX_HUE_SPREAD]; the same title always gets the same turn. */
fun boxHueTurn(title: String): Int {
    val h = title.lowercase(Locale.ROOT).fold(1125899906842597L) { a, c -> 31 * a + c.code }
    val mixed = (h xor (h ushr 29)) * -0x61c8864680b583ebL
    return ((mixed ushr 33) % (2 * BOX_HUE_SPREAD + 1)).toInt() - BOX_HUE_SPREAD
}

/**
 * The drawn box front's field from the system's colour [base]: its hue turned by [boxHueTurn] of [title], saturated and
 * mid-dark so white reads on it on every page (a card stands off Paper as a picture does), lighter at the top left and darker
 * at the bottom right. Near-grey colours stay grey. The lightest part keeps [BOX_TITLE_CONTRAST] against white.
 */
fun boxFieldFor(
    base: Color,
    title: String,
): BoxFieldLook {
    val (hue0, sat0, _) = ColorMath.toHsl(Rgb(base.toArgb() and 0xFFFFFF))
    val hue = ((hue0 + boxHueTurn(title) / 360.0) % 1.0 + 1.0) % 1.0
    val sat = if (sat0 < 0.12) sat0 else sat0.coerceIn(0.55, 0.85)
    val white = Rgb(0xFFFFFF)

    fun reads(lightness: Double): Boolean {
        val field = ColorMath.fromHsl(hue, sat, lightness)
        val mark = ColorMath.fromHsl(hue, sat * 0.9, (lightness + 0.3).coerceAtMost(0.9))
        return ColorMath.contrast(white, boxLightestPixel(ColorMath.mix(field, mark, BOX_MARK_ALPHA.toDouble()))) >= BOX_TITLE_CONTRAST
    }
    var lo = 0.05
    var hi = 0.5
    val top =
        if (reads(hi)) {
            hi
        } else {
            repeat(18) {
                val mid = (lo + hi) / 2
                if (reads(mid)) lo = mid else hi = mid
            }
            lo
        }
    return BoxFieldLook(
        top = ColorMath.fromHsl(hue, sat, top).toComposeColor(),
        bottom = ColorMath.fromHsl(hue, (sat + 0.05).coerceAtMost(1.0), top * 0.55).toComposeColor(),
        mark = ColorMath.fromHsl(hue, sat * 0.9, (top + 0.3).coerceAtMost(0.9)).toComposeColor(),
    )
}
