package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The focused row's slab (docs/design/rules.md > Focus and current, the focused row): one glass recipe for every colour set, each layer a step of perceived lightness
 * (CIE L*) from what lies under it, so a dark and a light theme draw the same object at the same strength:
 *  - fill: the page or card lit toward white, keeping its hue. On a dark theme [FILL_TOP_L] at the top to [FILL_BOTTOM_L] at the foot above it, [DARK_GAIN] more on
 *    black (the eye takes a step on a dark page for less). On a light theme only [LIGHT_TOP_L] to [LIGHT_BOTTOM_L]: the pane stays below white, so the white rim shows;
 *  - rim: a white hairline, [RIM_SIDE_L] above the fill along the sides on a dark theme and white itself on a light one, and white at two opposite corners, [RIM_LIT_L]
 *    above the fill or as far as white goes;
 *  - lift: two soft shadows [LIFT_NEAR_L] and [LIFT_FAR_L] off the page, black on a dark theme and the brown [shadowTint] on a light one; never a glow.
 * Dark and light draw the same rimmed pane over the same shadow. A pure white page leaves the white rim nothing to show against: there only the shadow carries the slab
 * (the sets keep their light pages at most about L* 93). Derived from the background only, so every colour set and custom colour has one; the accent paints the bar.
 */
object FocusSlab {
    /** The gradient's three stops over [under] (the page, or the card under it): top, [MID_AT], bottom. */
    fun stops(
        under: Rgb,
        dark: Boolean,
    ): List<Rgb> =
        steps(dark).map { lit(under, it) }

    private fun steps(dark: Boolean) = if (dark) listOf(FILL_TOP_L, FILL_MID_L, FILL_BOTTOM_L) else listOf(LIGHT_TOP_L, LIGHT_MID_L, LIGHT_BOTTOM_L)

    /** The gradient's mean over its height (for contrast checks and flat samples). */
    fun fill(
        bg: Rgb,
        text: Rgb,
        dark: Boolean,
    ): Rgb {
        val (top, mid, bottom) = steps(dark)
        return lit(bg, MID_AT * (top + mid) / 2 + (1 - MID_AT) * (mid + bottom) / 2)
    }

    /** [fill] where the slab sits on a raised card. */
    fun fillOnCard(
        card: Rgb,
        bg: Rgb,
        dark: Boolean,
    ): Rgb = fill(card, bg, dark)

    /** [under] moved [step] of L* toward white. */
    private fun lit(
        under: Rgb,
        step: Double,
    ) = ColorMath.mix(under, WHITE, ColorMath.shareForLightness(under, WHITE, step * (1 + DARK_GAIN * (1 - ColorMath.lightness(under) / 100))))

    /** The rim's lit corners as a white overlay over [fill]. */
    fun topAlpha(fill: Rgb) = ColorMath.shareForLightness(fill, WHITE, RIM_LIT_L)

    /** The rim's strength along the sides, as a white overlay over [fill]: [RIM_SIDE_L] above it on a dark theme, white itself on a light one. */
    fun edgeAlpha(
        fill: Rgb,
        bg: Rgb,
        dark: Boolean,
    ): Double = if (dark) ColorMath.shareForLightness(fill, WHITE, RIM_SIDE_L) else 1.0

    /** The rim's colour along the sides: white on every theme. */
    fun edgeInk(dark: Boolean): Rgb = WHITE

    /** What lifts the slab off [bg]: a shadow on every theme ([shadowTint]), never a glow. */
    fun liftInk(dark: Boolean): Rgb = shadowTint(dark)

    /** A lift layer's strength over [bg], for a step of [step] L*. */
    fun liftAlpha(
        bg: Rgb,
        dark: Boolean,
        step: Double,
    ) = ColorMath.shareForLightness(bg, liftInk(dark), step).toFloat()

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

    /** The rim's side as it reads over the slab's fill. */
    fun edgeOver(
        fill: Rgb,
        bg: Rgb,
        dark: Boolean,
    ): Rgb = ColorMath.mix(fill, edgeInk(dark), edgeAlpha(fill, bg, dark))

    /** Steps of L*, from what lies under each layer. See the class. */
    const val FILL_TOP_L = 7.5
    const val FILL_MID_L = 5.5
    const val FILL_BOTTOM_L = 4.0
    const val DARK_GAIN = 0.33
    const val LIGHT_TOP_L = 3.5
    const val LIGHT_MID_L = 3.0
    const val LIGHT_BOTTOM_L = 2.5
    const val RIM_LIT_L = 30.0 * Depth.RAISE
    const val RIM_SIDE_L = RIM_LIT_L * 2 / 3
    const val LIFT_NEAR_L = 10.0 / 3.0 * Depth.SHADOW
    const val LIFT_FAR_L = 35.0 / 6.0 * Depth.SHADOW

    /** Where the gradient's middle stop sits. */
    const val MID_AT = 0.46f

    /** The lift layers in dp, at [Depth.SHADOW_REACH] of the board's `0 3px 6px` and `0 16px 32px`. */
    const val NEAR_DROP_DP = 2f * Depth.SHADOW_REACH
    const val NEAR_SIGMA_DP = 2f * Depth.SHADOW_REACH
    const val FAR_DROP_DP = 10.67f * Depth.SHADOW_REACH
    const val FAR_SIGMA_DP = 10.67f * Depth.SHADOW_REACH

    /** The dark `0 0 0 1px` ring (black at 0.4; a light theme draws none), and its width in dp (1 board px). */
    const val RING_DARK = 0.0
    const val RING_WIDTH_DP = 0.667f

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
 * [FocusSlab] as Compose colours, one field per CSS layer: the gradient [stops] (on a card [stopsOnCard]), the border [edge] with its [top],
 * the inset shadows [insetTop] / [insetBottom] (none on glass), the dark [ring], the drops [near] / [far].
 * [fill] / [onCard] are the gradients' means (contrast checks, flat samples).
 */
@Immutable
class SlabLook(
    val fill: Color,
    val edge: Color,
    val ring: Color = Color.Transparent,
    val onCard: Color = fill,
    val top: Color = edge,
    val year: Color = edge,
    val region: Color = edge,
    val regionWeight: Int = FocusSlab.REGION_WEIGHT_DARK,
    val stops: List<Color> = listOf(fill, fill, fill),
    val stopsOnCard: List<Color> = stops,
    val insetTop: SlabShadow? = null,
    val insetBottom: SlabShadow? = null,
    val near: SlabShadow? = null,
    val far: SlabShadow? = null,
    /** The shadows' tint, alpha = their strength ([FocusSlab.shadowAlpha]), for parts that take the slab's shadows (the badge frame). */
    val shadow: Color = Color.Black,
) {
    /** The glass rim from [start] to [end], a box's opposite corners: [top] at both and [edge] between them. */
    fun rim(
        start: Offset,
        end: Offset,
    ): Brush = Brush.linearGradient(0f to top, RIM_FADE to edge, 1f - RIM_FADE to edge, 1f to top, start = start, end = end)

    internal companion object {
        fun of(
            bg: Rgb,
            text: Rgb,
            dark: Boolean,
        ): SlabLook {
            val fill = FocusSlab.fill(bg, text, dark)
            val floor = ColorMath.reach(bg, text, fill, 6.0)
            val card = RaisedCard.fill(bg, text, dark)
            val tint = FocusSlab.shadowTint(dark).toComposeColor()
            val lift = FocusSlab.liftInk(dark).toComposeColor()
            return SlabLook(
                fill = fill.toComposeColor(),
                edge = FocusSlab.edgeInk(dark).toComposeColor().copy(alpha = FocusSlab.edgeAlpha(fill, bg, dark).toFloat()),
                ring = if (dark) Color.Black.copy(alpha = FocusSlab.RING_DARK.toFloat()) else Color.Transparent,
                onCard = FocusSlab.fillOnCard(card, bg, dark).toComposeColor(),
                top = Color.White.copy(alpha = FocusSlab.topAlpha(fill).toFloat()),
                year = FocusSlab.yearInk(fill, text, floor, dark).toComposeColor(),
                region = FocusSlab.regionInk(fill, text, floor, dark).toComposeColor(),
                regionWeight = if (dark) FocusSlab.REGION_WEIGHT_DARK else FocusSlab.REGION_WEIGHT_LIGHT,
                stops = FocusSlab.stops(bg, dark).map { it.toComposeColor() },
                stopsOnCard = FocusSlab.stops(card, dark).map { it.toComposeColor() },
                near = SlabShadow(lift.copy(alpha = FocusSlab.liftAlpha(bg, dark, FocusSlab.LIFT_NEAR_L)), FocusSlab.NEAR_DROP_DP.dp, FocusSlab.NEAR_SIGMA_DP.dp),
                far = SlabShadow(lift.copy(alpha = FocusSlab.liftAlpha(bg, dark, FocusSlab.LIFT_FAR_L)), FocusSlab.FAR_DROP_DP.dp, FocusSlab.FAR_SIGMA_DP.dp),
                shadow = tint.copy(alpha = FocusSlab.shadowAlpha(dark)),
            )
        }
    }
}

/** Share of the rim's diagonal, from each lit corner, over which its light fades out. */
private const val RIM_FADE = 0.16f
