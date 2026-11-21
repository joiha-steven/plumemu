package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Sizes of the second screen's tiles, drawn as the focused row's plate ([SlabLook], `glassTile`): its rim inside the box. */
object GlassDimens {
    /** The rim: the slab's own edge. */
    val edge = SlabDimens.edge
}

/**
 * The well of a meter's track and its shadows (the boards' `sunk`): the tone ladder's sunken step ([ToneLadder.SUNKEN]), the page's own
 * hue on dark and light alike. Over it the boards'
 * `inset 0 3px 6px` [shade], `inset 0 -1px 0` white at [BOTTOM_LINE], and under it the `0 1px 0` [lip]. A thinner track is darker all
 * through, as the shadow is the same size.
 */
object MeterTrackToken {
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
    ): Color = ToneLadder.level(bg, ToneLadder.SUNKEN).toComposeColor()
}

/**
 * A played column of the week chart (the boards' `muted` at 0.55, over the tile): on dark themes the background moved [DARK] of the way
 * toward a cool light grey, on light ones [LIGHT] toward a warm dark brown (the boards' `muted` are tinted like their pages),
 * at [ALPHA]. Not text: it is no `muted` (that one keeps 4.6:1); a column is a bar of a chart whose value is also written, and an
 * empty day stays fainter still ([ToneLadder.line]).
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
