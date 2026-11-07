package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * The glass tile of the second screen (the V2 boards' `glass(t, glow=False)`): a vertical gradient over the page, a hairline edge with a
 * brighter top, a lit line under it (dark themes) and an inset shade rising from the foot. On dark themes the glass is white at the board's alphas over whatever the page is; on light
 * themes it runs from white to the background moved [FOOT_LIGHT] of the way toward [FOOT_TOWARD], a warm white (the board's #FFFFFF to #FAF8F3 over #ECE8DF), and its
 * side edge is the warm shadow colour at [EDGE_LIGHT] whatever the background (the board's rgba(60,40,20,0.06)).
 * Only the tile uses it (a tile is no control, decision 0011); the game panel's card stays flat ([RaisedCard]).
 */
object GlassTile {
    /** Light themes: how far toward [FOOT_TOWARD] the glass is at its foot (the top is white). */
    const val FOOT_LIGHT = 0.75
    internal val FOOT_TOWARD = Rgb(0xFFFDFA)

    const val TOP_DARK = 0.17f
    const val MID_DARK = 0.07f
    const val FOOT_DARK = 0.045f

    /** Where the middle stop sits down the tile. */
    const val MID_AT = 0.46f

    const val EDGE_DARK = 0.14f
    const val EDGE_TOP_DARK = 0.42f
    const val EDGE_LIGHT = 0.06f
    /** The board's inset shades: `inset 0 -10px 18px` black on dark themes, `inset 0 -4px 8px` warm brown on light ones. */
    const val SHADE_DARK = 0.20f
    const val SHADE_LIGHT = 0.04f

    /** The board's `inset 0 1px 1px` white under the top edge (dark themes only). */
    const val HIGHLIGHT_DARK = 0.30f
    const val RING_DARK = 0.40f

    /** The board's two tile shadows (`0 3px 6px` and `0 16px 32px`) at these shares of the card's shadow strength. */
    const val SHADOW_NEAR = 0.35f * Depth.SHADOW
    const val SHADOW_FAR = 0.45f * Depth.SHADOW

    fun footLight(bg: Rgb): Rgb = ColorMath.mix(bg, FOOT_TOWARD, FOOT_LIGHT)
}

/**
 * [GlassTile] as Compose colours: the gradient stops, the [edge] and its brighter [edgeTop], the inset [shade] (alpha = its strength) and the
 * inset [highlight] under the top edge; [dark] picks the shade's size in [GlassDimens].
 */
@Immutable
class GlassLook(
    val top: Color,
    val mid: Color,
    val foot: Color,
    val edge: Color,
    val edgeTop: Color,
    val shade: Color,
    val dark: Boolean,
    val highlight: Color = Color.Transparent,
    /** The 1 px ring just outside the tile (dark themes only; transparent on light). */
    val ring: Color = Color.Transparent,
) {
    internal companion object {
        fun of(
            bg: Rgb,
            dark: Boolean,
        ): GlassLook =
            if (dark) {
                GlassLook(
                    top = Color.White.copy(alpha = GlassTile.TOP_DARK),
                    mid = Color.White.copy(alpha = GlassTile.MID_DARK),
                    foot = Color.White.copy(alpha = GlassTile.FOOT_DARK),
                    edge = Color.White.copy(alpha = GlassTile.EDGE_DARK),
                    edgeTop = Color.White.copy(alpha = GlassTile.EDGE_TOP_DARK),
                    shade = Color.Black.copy(alpha = GlassTile.SHADE_DARK),
                    dark = true,
                    highlight = Color.White.copy(alpha = GlassTile.HIGHLIGHT_DARK),
                    ring = Color.Black.copy(alpha = GlassTile.RING_DARK),
                )
            } else {
                val foot = GlassTile.footLight(bg).toComposeColor()
                GlassLook(
                    top = Color.White,
                    mid = lerp(Color.White, foot, GlassTile.MID_AT),
                    foot = foot,
                    edge = MeterTrackToken.SHADOW.toComposeColor().copy(alpha = GlassTile.EDGE_LIGHT),
                    edgeTop = Color.White,
                    shade = MeterTrackToken.SHADOW.toComposeColor().copy(alpha = GlassTile.SHADE_LIGHT),
                    dark = false,
                )
            }
    }
}

/** Sizes of the glass tile: the board's px / 1.5. */
object GlassDimens {
    /** The inset shade, raised and blurred (a CSS blur of B is a sigma of B / 2): dark `0 -10px 18px`, light `0 -4px 8px`. */
    val shadeRiseDark = 6.667.dp
    val shadeSigmaDark = 6.dp
    val shadeRiseLight = 2.667.dp
    val shadeSigmaLight = 2.667.dp

    /** The lit line under the top edge, `inset 0 1px 1px`: dropped 1 px and blurred by half a px. */
    val highlightDrop = 0.667.dp
    val highlightSigma = 0.333.dp

    /** The hairline edge: the board's 1 px. */
    val edge = 0.67.dp

    /** The two shadows as drop and Gaussian sigma (board px / 1.5; a CSS blur of B is a sigma of B / 2). */
    val shadowNearDrop = 2.dp * Depth.SHADOW_REACH
    val shadowNearSigma = 2.dp * Depth.SHADOW_REACH
    val shadowFarDrop = 10.667.dp * Depth.SHADOW_REACH
    val shadowFarSigma = 10.667.dp * Depth.SHADOW_REACH
}

/**
 * The well of a meter's track and its shadows (the boards' `sunk`): on dark themes the background [TOWARD_BLACK_DARK] of the way toward
 * black; on light ones [LIGHT] toward the boards' warm shadow colour (`60,40,20`: #ECE8DF gives #E0DBD0). Over it the boards'
 * `inset 0 3px 6px` [shade], `inset 0 -1px 0` white at [BOTTOM_LINE], and under it the `0 1px 0` [lip]. A thinner track is darker all
 * through, as the shadow is the same size.
 */
object MeterTrackToken {
    const val TOWARD_BLACK_DARK = 0.40
    const val LIGHT = 0.07
    const val SHADE_DARK = 0.70f
    const val SHADE_LIGHT = 0.315f
    const val BOTTOM_LINE = 0.05f
    const val LIP_DARK = 0.08f
    internal val SHADOW = Rgb(0x3C2814)

    /** The inset shadow: black on dark themes, the warm shadow colour on light ones, at [SHADE_DARK] / [SHADE_LIGHT]. */
    fun shade(dark: Boolean): Color = if (dark) Color.Black.copy(alpha = SHADE_DARK) else SHADOW.toComposeColor().copy(alpha = SHADE_LIGHT)

    /** The lit line along the track's foot, inside it: white at [BOTTOM_LINE]. */
    val bottomLine: Color get() = Color.White.copy(alpha = BOTTOM_LINE)

    /** The line under the track: white at [LIP_DARK] on dark themes, white on light ones. */
    fun lip(dark: Boolean): Color = Color.White.copy(alpha = if (dark) LIP_DARK else 1f)

    fun of(
        bg: Rgb,
        dark: Boolean,
    ): Color = if (dark) ColorMath.mix(bg, Rgb(0x000000), TOWARD_BLACK_DARK).toComposeColor() else ColorMath.mix(bg, SHADOW, LIGHT).toComposeColor()
}

/**
 * A played column of the week chart (the boards' `muted` at 0.55, over the tile): on dark themes the background moved [DARK] of the way
 * toward a cool light grey, on light ones [LIGHT] toward a warm dark brown (the boards' `muted` are tinted like their pages),
 * at [ALPHA]. Not text: it is no `muted` (that one keeps 4.6:1); a column is a bar of a chart whose value is also written, and an
 * empty day stays fainter still ([LineToken]).
 */
object PlayedBarToken {
    const val ALPHA = 0.55f
    const val DARK = 0.50
    const val LIGHT = 0.50

    /** Half way from Graphite's #111214 to this is the boards' dark `muted` #6F737B. */
    private val COOL = Rgb(0xCDD4E2)

    /** Half way from Paper's #ECE8DF to this is the boards' light `muted` #9A958B. */
    private val WARM = Rgb(0x484237)

    fun of(
        bg: Rgb,
        dark: Boolean,
    ): Color = ColorMath.mix(bg, if (dark) COOL else WARM, if (dark) DARK else LIGHT).toComposeColor().copy(alpha = ALPHA)
}

/** The rule between a card's parts and the stub of an empty column: the text at 10 % on dark, 12 % on light (the board's `line`). */
object LineToken {
    const val ALPHA_DARK = 0.10f
    const val ALPHA_LIGHT = 0.12f

    fun of(
        text: Rgb,
        dark: Boolean,
    ): Color = text.toComposeColor().copy(alpha = if (dark) ALPHA_DARK else ALPHA_LIGHT)
}

/** The accent glow in a corner of the second screen's page. */
object BackdropGlow {
    /** The glow's peak alpha, measured on the boards after the blur has thinned it (opacity 0.22 dark, 0.16 light before the blur). */
    const val PEAK_DARK = 0.15f
    const val PEAK_LIGHT = 0.115f
}

/** Where the corner glow sits and how far it spreads (the boards' px / 1.5, fitted to the renders), and the glow past a meter's fill. */
object BackdropGlowDimens {
    /** The glow's middle: in from the page's right edge and down from its top. */
    val fromRight = 60.dp
    val fromTop = 13.dp

    /** Its Gaussian spread across and down. */
    val spreadAcross = 114.dp
    val spreadDown = 103.dp

    /** The glow past a meter's fill: the boards' 8 px blur. */
    val meterGlow = 5.33.dp

    /**
     * A track's inset shadow, `inset 0 3px 6px`: dropped 3 px. The browser blurs it wider than a sigma of half the blur: fitted to the
     * boards' rendered rows (the 6 and 8 px tracks, both looks) the sigma is 3.3 px (near the blur over the square root of 3), not 3.
     */
    val meterShadeDrop = 2.dp
    val meterShadeSigma = 2.2.dp

    /** The track's bottom line and the lip under it: the boards' 1 px. */
    val meterLine = 0.667.dp
}

/** The badge frame's strengths, of the slab's shadow ([SlabLook.shadow]): the boards' ring at 0.25 and drop at 0.4; the lit top line, white. */
object BadgeFrame {
    const val RING = 0.25f
    const val DROP = 0.4f
    const val TOP_LINE = 0.35f

    /** The lit top line: white at [TOP_LINE]. */
    val topLine: Color get() = Color.White.copy(alpha = TOP_LINE)
}

/** The badge frame's sizes: one board px for the ring and the top line, the drop's 2 px down with its 4 px blur (sigma 2 px). */
object BadgeFrameDimens {
    val ring = 0.667.dp
    val topLine = 0.667.dp
    val drop = 1.333.dp * Depth.SHADOW_REACH
    val sigma = 1.333.dp * Depth.SHADOW_REACH
}
