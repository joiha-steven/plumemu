package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.text.Normalizer
import java.util.Locale

/** One poster cover's colours: the gradient's [top] and [bottom], the system [band] and the [ink] of the title and band text. */
@Immutable
data class PosterLook(
    val top: Color,
    val bottom: Color,
    val band: Color,
    val ink: Color,
)

/** White title ink of the deep looks. */
val POSTER_INK_WHITE = Color.White
private val INK_WHITE = POSTER_INK_WHITE
private val INK_DARK = Color(0xFF14142B)

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

/**
 * The 23 looks (contrast measured, lowest 4.66:1): a drawn box front's field and the colours of its shapes.
 * Hand-tuned in OKLCH, from a bright hue to a deeper neighbour 10-25 degrees away; 12 deep ones with a
 * white title, 11 light ones with a near-black title, the ink passing 4.5:1 against both gradient ends
 * and the band. A title's hash ([PosterDice]) picks them, so one character apart gives an unrelated look.
 */
val POSTER_PALETTE: List<PosterLook> =
    listOf(
        PosterLook(Color(0xFFDF1E39), Color(0xFFAD0549), Color(0xFF870037), INK_WHITE), // ruby
        PosterLook(Color(0xFFD81E70), Color(0xFF9D1172), Color(0xFF7C0059), INK_WHITE), // rose
        PosterLook(Color(0xFFCD1AA5), Color(0xFF841495), Color(0xFF670075), INK_WHITE), // magenta
        PosterLook(Color(0xFFAA3BC9), Color(0xFF6724AD), Color(0xFF50008F), INK_WHITE), // orchid
        PosterLook(Color(0xFF8448D9), Color(0xFF452CB4), Color(0xFF340399), INK_WHITE), // violet
        PosterLook(Color(0xFF5C53DA), Color(0xFF2137A6), Color(0xFF121A8C), INK_WHITE), // indigo
        PosterLook(Color(0xFF306CE2), Color(0xFF004B96), Color(0xFF003670), INK_WHITE), // cobalt
        PosterLook(Color(0xFF047BB5), Color(0xFF035397), Color(0xFF003D73), INK_WHITE), // ocean
        PosterLook(Color(0xFF017F7F), Color(0xFF065D6D), Color(0xFF044551), INK_WHITE), // teal
        PosterLook(Color(0xFF018455), Color(0xFF066357), Color(0xFF034B41), INK_WHITE), // emerald
        PosterLook(Color(0xFF1E8515), Color(0xFF056641), Color(0xFF034D30), INK_WHITE), // forest
        PosterLook(Color(0xFF9C3B96), Color(0xFF5B2987), Color(0xFF450E6E), INK_WHITE), // plum
        PosterLook(Color(0xFFFDE836), Color(0xFFFFB334), Color(0xFFE39A01), INK_DARK), // lemon
        PosterLook(Color(0xFFFFBE3F), Color(0xFFFF8C08), Color(0xFFDC7702), INK_DARK), // marigold
        PosterLook(Color(0xFFFFAF7E), Color(0xFFFF7A66), Color(0xFFEC5542), INK_DARK), // peach
        PosterLook(Color(0xFFFEA6C4), Color(0xFFFE62B7), Color(0xFFE2459E), INK_DARK), // flamingo
        PosterLook(Color(0xFFC0F447), Color(0xFF61DC6B), Color(0xFF43C251), INK_DARK), // lime
        PosterLook(Color(0xFF6FF5C0), Color(0xFF3BD5BE), Color(0xFF07BBA4), INK_DARK), // mint
        PosterLook(Color(0xFF65F0F0), Color(0xFF43CAE7), Color(0xFF18B1CD), INK_DARK), // aqua
        PosterLook(Color(0xFF8ED5FE), Color(0xFF67AAFF), Color(0xFF4390EE), INK_DARK), // sky
        PosterLook(Color(0xFFCEB5FF), Color(0xFFA28DFE), Color(0xFF8A70EC), INK_DARK), // lavender
        PosterLook(Color(0xFFFFA194), Color(0xFFFD617A), Color(0xFFE44A65), INK_DARK), // coral
        PosterLook(Color(0xFFF5ADF9), Color(0xFFD180F4), Color(0xFFB767D9), INK_DARK), // lilac
    )

/** [title] without case, accents, punctuation or spaces: what the look is picked from. */
fun posterTitleKey(title: String): String {
    val decomposed = Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
    val key = StringBuilder()
    var i = 0
    while (i < decomposed.length) {
        val codePoint = decomposed.codePointAt(i)
        i += Character.charCount(codePoint)
        if (Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt()) continue
        if (Character.isLetterOrDigit(codePoint)) key.appendCodePoint(codePoint)
    }
    return key.toString()
}

/** The title's mixed 64-bit hash: FNV-1a over the code points of [posterTitleKey], so the same game gets the same value on every screen and device. */
fun posterHash(title: String): Long {
    val key = posterTitleKey(title)
    var h = -3750763034362895579L // FNV offset basis
    var i = 0
    while (i < key.length) {
        val codePoint = key.codePointAt(i)
        i += Character.charCount(codePoint)
        h = (h xor codePoint.toLong()) * 1099511628211L
    }
    return h
}

/**
 * A stream of choices that depends only on its seed (SplitMix64: every step mixes all bits, so titles one character apart get
 * unrelated looks). The drawn box front takes its field colour, its shapes and their places from one stream per title.
 */
class PosterDice(
    seed: Long,
) {
    private var state = seed

    /** The next choice in `0 until bound`. */
    fun next(bound: Int): Int {
        state += -7046029254386353131L
        var z = state
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return ((z ushr 1) % bound).toInt()
    }
}

/** Index into [POSTER_PALETTE] for [title]: the first choice of its [PosterDice], so the same game wears the same field everywhere. */
fun posterLookIndex(title: String): Int = PosterDice(posterHash(title)).next(POSTER_PALETTE.size)

/** The lit centre of a shape's soft shading: [colour] 40 % toward white (the board's sun goes from #F4BA34 to #FFE08A). */
fun boxHighlight(colour: Color): Color = androidx.compose.ui.graphics.lerp(colour, Color.White, 0.4f)

/** The board's drawn box front: band, dots and band text (the parts that never change), as colours. */
val BOX_BAND_TOP = Color(0xFFD9D9D6)
val BOX_BAND_BOTTOM = Color(0xFFBDBDB9)
val BOX_BAND_TEXT = Color(0xFF3A3A3A)
val BOX_DOTS = listOf(Color(0xFFD23A2C), Color(0xFFF2B630), Color(0xFF2F6BD0))

/**
 * Opacity of [look]'s small line under the band: the board's 0.8 where that still passes 4.5:1 on all three field colours, else
 * the first step above that does (the ruby's white line needs 1.0).
 */
fun boxCapsAlpha(look: PosterLook): Float {
    fun ratio(
        a: Color,
        b: Color,
    ) = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
    var alpha = 0.8f
    while (alpha < 1f) {
        val passes =
            listOf(look.top, look.bottom, look.band).all { field ->
                ratio(Color(look.ink.red * alpha + field.red * (1 - alpha), look.ink.green * alpha + field.green * (1 - alpha), look.ink.blue * alpha + field.blue * (1 - alpha)), field) >= 4.5f
            }
        if (passes) return alpha
        alpha += 0.05f
    }
    return 1f
}
