package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The focused row's slab, "lifted card" (docs/design/rules.md > Focus and current, the focused row): one recipe for every colour set.
 *  - face: one flat colour ([FlatSurface]), clearly lighter than the page or card it lifts off. On a light theme the page under [LIGHT_WHITE_SHARE] of white
 *    (it keeps a hint of the page's hue; a yellow page reads cream); on a dark one the page lifted [DARK_LIFT_L] of CIE L*, in its own hue;
 *  - depth: one soft real drop shadow ([DROP_DP] down, Gaussian [SIGMA_DP]), black at [SHADOW_LIGHT_ALPHA] / [SHADOW_DARK_ALPHA]. No outline, no top line, no foot, no
 *    stroke of any kind: the face and the shadow are all the edge there is.
 * Derived from the background only, so every colour set and custom colour has one; the accent paints the bar (and its glow).
 */
object FocusSlab {
    /** The slab's one flat face over [bg] (see the class). */
    fun fill(
        bg: Rgb,
        text: Rgb,
        dark: Boolean,
    ): Rgb =
        if (dark) ColorMath.mix(bg, WHITE, ColorMath.shareForLightness(bg, WHITE, DARK_LIFT_L)) else ColorMath.mix(bg, WHITE, LIGHT_WHITE_SHARE)

    /** [fill] where the slab sits on a raised card. */
    fun fillOnCard(
        card: Rgb,
        bg: Rgb,
        dark: Boolean,
    ): Rgb = fill(card, bg, dark)

    /**
     * The year (and the single trailing text) on a focused row: [fill] moved [YEAR_DARK] / [YEAR_LIGHT] toward the text (the board's #D6D2C8 on Graphite,
     * #4A4740 on Paper); kept at [MIN_CONTRAST] against the fill, else [floor] (the `meta` token), so a custom palette still reads.
     */
    fun yearInk(
        fill: Rgb,
        text: Rgb,
        floor: Rgb,
        dark: Boolean,
    ): Rgb = readable(ColorMath.mix(fill, text, if (dark) YEAR_DARK else YEAR_LIGHT), fill, floor)

    /** The region on a focused row: [REGION_DARK] / [REGION_LIGHT] toward the text (board #C9C5BB on Graphite, #4A4740 on Paper), kept readable like [yearInk]. */
    fun regionInk(
        fill: Rgb,
        text: Rgb,
        floor: Rgb,
        dark: Boolean,
    ): Rgb = readable(ColorMath.mix(fill, text, if (dark) REGION_DARK else REGION_LIGHT), fill, floor)

    private fun readable(
        ink: Rgb,
        fill: Rgb,
        floor: Rgb,
    ) = if (ColorMath.contrast(ink, fill) >= MIN_CONTRAST) ink else floor

    fun shadowTint(dark: Boolean) = if (dark) Rgb(0x000000) else SHADOW_TINT_LIGHT

    /** The shadows' strength: 1 on a dark theme, the board's 0.45 on a light one (other parts' shadows, the badge frame's, scale by it). */
    fun shadowAlpha(dark: Boolean) = if (dark) SHADOW_DARK else SHADOW_LIGHT

    const val SHADOW_DARK = 1.0f
    const val SHADOW_LIGHT = 0.45f

    /** The face: a light page under this share of white; a dark page lifted this much L*. */
    const val LIGHT_WHITE_SHARE = 0.70
    const val DARK_LIFT_L = 15.0

    /** The drop shadow: black at this alpha, moved [DROP_DP] down, Gaussian sigma [SIGMA_DP] (3 dp down, sigma 6 dp). */
    const val SHADOW_LIGHT_ALPHA = 0.18f
    const val SHADOW_DARK_ALPHA = 0.45f
    const val DROP_DP = 3f
    const val SIGMA_DP = 6f

    /** Focused trailing text: shares of the way from the fill to the text, and weights (the board's year 500, region 600 dark / 700 light). */
    const val YEAR_DARK = 0.89
    const val REGION_DARK = 0.82
    const val YEAR_LIGHT = 0.80
    const val REGION_LIGHT = 0.80
    const val YEAR_WEIGHT = 500
    const val REGION_WEIGHT_DARK = 600
    const val REGION_WEIGHT_LIGHT = 700
    private const val MIN_CONTRAST = 6.0

    private val SHADOW_TINT_LIGHT = Rgb(0x3C2814)
    private val WHITE = Rgb(0xFFFFFF)
}

/** One CSS box-shadow of the slab: [color] (its alpha is the strength), moved [drop] down (negative: up), Gaussian [sigma] (the CSS blur / 2). */
@Immutable
class SlabShadow(
    val color: Color,
    val drop: Dp,
    val sigma: Dp,
)

/**
 * [FocusSlab] as Compose colours: the flat face [fill] (on a card [onCard]) and the one drop [near] ([far] is the same layer at no strength: the cached
 * image takes two). No ring, no top line, no foot: the face is the whole plate.
 */
@Immutable
class SlabLook(
    val fill: Color,
    val onCard: Color = fill,
    val year: Color = fill,
    val region: Color = fill,
    val regionWeight: Int = FocusSlab.REGION_WEIGHT_DARK,
    val near: SlabShadow? = null,
    val far: SlabShadow? = null,
    /** The shadows' tint, alpha = their strength ([FocusSlab.shadowAlpha]), for parts that take the slab's shadows (the badge frame). */
    val shadow: Color = Color.Black,
) {
    internal companion object {
        fun of(
            bg: Rgb,
            text: Rgb,
            dark: Boolean,
        ): SlabLook {
            val fill = FocusSlab.fill(bg, text, dark)
            val card = RaisedCard.fill(bg, text, dark)
            val floor = ColorMath.reach(bg, text, fill, 6.0)
            val tint = FocusSlab.shadowTint(dark).toComposeColor()
            val drop = SlabShadow(Color.Black.copy(alpha = if (dark) FocusSlab.SHADOW_DARK_ALPHA else FocusSlab.SHADOW_LIGHT_ALPHA), FocusSlab.DROP_DP.dp, FocusSlab.SIGMA_DP.dp)
            return SlabLook(
                fill = fill.toComposeColor(),
                onCard = FocusSlab.fillOnCard(card, bg, dark).toComposeColor(),
                year = FocusSlab.yearInk(fill, text, floor, dark).toComposeColor(),
                region = FocusSlab.regionInk(fill, text, floor, dark).toComposeColor(),
                regionWeight = if (dark) FocusSlab.REGION_WEIGHT_DARK else FocusSlab.REGION_WEIGHT_LIGHT,
                near = drop,
                far = SlabShadow(drop.color.copy(alpha = 0f), drop.drop, drop.sigma),
                shadow = tint.copy(alpha = FocusSlab.shadowAlpha(dark)),
            )
        }
    }
}
