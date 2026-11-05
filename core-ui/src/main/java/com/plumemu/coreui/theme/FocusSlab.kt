package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The focused row's slab (docs/design/rules.md > The focus slab): the kit boards' GLASS (the GLASS look and its shared building blocks),
 * layer by layer (1 board px = 1/1.5 dp):
 *  - dark: `linear-gradient(180deg, white 0.17 0%, 0.07 46%, 0.045 100%)` over the page, a 1 px border of white 0.14 with a white 0.42 top, inset
 *    `0 1px 1px` white 0.30 and `0 -10px 18px` black 0.20, a `0 0 0 1px` black 0.40 ring, drops `0 3px 6px` black 0.35 and `0 16px 32px` black 0.45;
 *  - light: a gradient from white moved [LIGHT_TOP_TINT] toward the page to white moved [LIGHT_BOTTOM_TINT] toward it (Paper #FCFBF9 to #F4F2ED;
 *    the board's #FFFFFF to #FAF8F3 read as plain white, so the slab follows the page more), a 1 px border of brown 0.06 with a white top,
 *    inset `0 -4px 8px` brown 0.04, drops `0 3px 6px` brown 0.158 and `0 16px 32px` brown 0.203.
 * The glow `0 0 22px` accent 0.3 is `PlumComposeColors.slabGlow`. Derived from the background only (white overlays on dark, the brown
 * [shadowTint] on light), so every colour set and custom colour has one; the accent paints the bar.
 */
object FocusSlab {
    /** The gradient's three stops over [under] (the page, or the card under it): top, [MID_AT], bottom. Light themes tint toward [bg]. */
    fun stops(
        under: Rgb,
        bg: Rgb,
        dark: Boolean,
    ): List<Rgb> =
        if (dark) {
            listOf(DARK_TOP, DARK_MID, DARK_BOTTOM).map { ColorMath.mix(under, WHITE, it) }
        } else {
            val top = ColorMath.mix(WHITE, bg, LIGHT_TOP_TINT)
            val bottom = ColorMath.mix(WHITE, bg, LIGHT_BOTTOM_TINT)
            listOf(top, ColorMath.mix(top, bottom, MID_AT.toDouble()), bottom)
        }

    /** The gradient's mean over its height (for contrast checks and flat samples): white at [DARK_MEAN] over the page, or white [LIGHT_MEAN_TINT] toward it. */
    fun fill(
        bg: Rgb,
        text: Rgb,
        dark: Boolean,
    ): Rgb = if (dark) ColorMath.mix(bg, WHITE, DARK_MEAN) else ColorMath.mix(WHITE, bg, LIGHT_MEAN_TINT)

    /** [fill] where the slab sits on a raised card: the same white overlays over the card's own fill on dark; the light gradient is opaque, so the page's. */
    fun fillOnCard(
        card: Rgb,
        bg: Rgb,
        dark: Boolean,
    ): Rgb = if (dark) ColorMath.mix(card, WHITE, DARK_MEAN) else fill(bg, bg, false)

    /** The border's top colour as an overlay: white at [TOP_DARK] on dark, white on light. */
    fun topAlpha(dark: Boolean) = if (dark) TOP_DARK else 1.0

    /** The top border's colour: white on every theme (the CSS `border-top-color`), whatever the text; laid at [topAlpha]. */
    @Suppress("UNUSED_PARAMETER")
    fun topInk(text: Rgb): Rgb = WHITE

    fun edgeAlpha(dark: Boolean) = if (dark) EDGE_DARK else EDGE_LIGHT

    /** The border's colour (sides and bottom): white on a dark theme, the brown [shadowTint] on a light one. */
    @Suppress("UNUSED_PARAMETER")
    fun edgeInk(
        text: Rgb,
        dark: Boolean,
    ): Rgb = if (dark) WHITE else SHADOW_TINT_LIGHT

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

    /** The border as it reads over the slab's fill. */
    fun edgeOver(
        fill: Rgb,
        text: Rgb,
        dark: Boolean,
    ): Rgb = ColorMath.mix(fill, edgeInk(text, dark), edgeAlpha(dark))

    /** The CSS gradient: white at these alphas over the page (dark), at 0 %, [MID_AT] and 100 %; the light gradient runs from white [LIGHT_TOP_TINT] to [LIGHT_BOTTOM_TINT] of the way toward the page (a straight line, mid stop at [MID_AT]). */
    const val DARK_TOP = 0.17
    const val DARK_MID = 0.07
    const val DARK_BOTTOM = 0.045
    const val MID_AT = 0.46f
    const val DARK_MEAN = MID_AT * (DARK_TOP + DARK_MID) / 2 + (1 - MID_AT) * (DARK_MID + DARK_BOTTOM) / 2
    const val LIGHT_TOP_TINT = 0.18
    const val LIGHT_BOTTOM_TINT = 0.55

    /** The light gradient is a straight line, so its mean is the average of its two ends. */
    const val LIGHT_MEAN_TINT = (LIGHT_TOP_TINT + LIGHT_BOTTOM_TINT) / 2

    /** The border: white 0.14 (dark) or brown 0.06 (light) with a white 0.42 / white top. */
    const val TOP_DARK = 0.42
    const val EDGE_DARK = 0.14
    const val EDGE_LIGHT = 0.06

    /** Inset shadows: dark `0 1px 1px` white 0.30 and `0 -10px 18px` black 0.20; light `0 -4px 8px` brown 0.04. */
    const val INSET_TOP_DARK = 0.30f
    const val INSET_BOTTOM_DARK = 0.20f
    const val INSET_BOTTOM_LIGHT = 0.04f

    /** Drops `0 3px 6px` and `0 16px 32px`: black 0.35 / 0.45 on dark, brown 0.158 / 0.203 on light. */
    const val NEAR_DARK = 0.35f
    const val FAR_DARK = 0.45f
    const val NEAR_LIGHT = 0.158f
    const val FAR_LIGHT = 0.203f

    /**
     * The dark far drop as Chrome renders the board's `0 16px 32px` black 0.45 (the reference where the two differ): wider and darker than its
     * Gaussian, measured beside the Graphite slab (PartsDark, Settings): black 0.52, 9 dp down, sigma 13 dp. With it (and the glow's sigma 7) the glow fades to 2 of 255
     * where the board's does at the sides (10.7 dp) and 0.7 dp later above (12 against 11.3). Light themes keep the CSS values (no light board draws this slab with a glow).
     */
    const val FAR_DARK_RENDERED = 0.52f
    const val FAR_DARK_RENDERED_DROP_DP = 9f
    const val FAR_DARK_RENDERED_SIGMA_DP = 13f

    /** The dark `0 0 0 1px` ring (black at 0.4; a light theme draws none), and its width in dp (1 board px). */
    const val RING_DARK = 0.4
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
 * the inset shadows [insetTop] / [insetBottom] (null where the board has none), the dark [ring], the drops [near] / [far].
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
            fun shade(alpha: Float, drop: Float, blurPx: Float) = SlabShadow(tint.copy(alpha = alpha), (drop / BOARD_PX).dp, (blurPx / 2f / BOARD_PX).dp)
            return SlabLook(
                fill = fill.toComposeColor(),
                edge = FocusSlab.edgeInk(text, dark).toComposeColor().copy(alpha = FocusSlab.edgeAlpha(dark).toFloat()),
                ring = if (dark) Color.Black.copy(alpha = FocusSlab.RING_DARK.toFloat()) else Color.Transparent,
                onCard = FocusSlab.fillOnCard(card, bg, dark).toComposeColor(),
                top = Color.White.copy(alpha = FocusSlab.topAlpha(dark).toFloat()),
                year = FocusSlab.yearInk(fill, text, floor, dark).toComposeColor(),
                region = FocusSlab.regionInk(fill, text, floor, dark).toComposeColor(),
                regionWeight = if (dark) FocusSlab.REGION_WEIGHT_DARK else FocusSlab.REGION_WEIGHT_LIGHT,
                stops = FocusSlab.stops(bg, bg, dark).map { it.toComposeColor() },
                stopsOnCard = FocusSlab.stops(if (dark) card else bg, bg, dark).map { it.toComposeColor() },
                insetTop = if (dark) SlabShadow(Color.White.copy(alpha = FocusSlab.INSET_TOP_DARK), (1f / BOARD_PX).dp, (0.5f / BOARD_PX).dp) else null,
                insetBottom = if (dark) shade(FocusSlab.INSET_BOTTOM_DARK, -10f, 18f) else shade(FocusSlab.INSET_BOTTOM_LIGHT, -4f, 8f),
                near = shade(if (dark) FocusSlab.NEAR_DARK else FocusSlab.NEAR_LIGHT, 3f, 6f),
                shadow = tint.copy(alpha = FocusSlab.shadowAlpha(dark)),
                far =
                    if (dark) {
                        SlabShadow(tint.copy(alpha = FocusSlab.FAR_DARK_RENDERED), FocusSlab.FAR_DARK_RENDERED_DROP_DP.dp, FocusSlab.FAR_DARK_RENDERED_SIGMA_DP.dp)
                    } else {
                        shade(FocusSlab.FAR_LIGHT, 16f, 32f)
                    },
            )
        }

        /** Board px per dp. */
        private const val BOARD_PX = 1.5f
    }
}
