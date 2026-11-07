package com.plumemu.coreui.theme

import kotlin.math.pow

/**
 * The colour and strength of the focus halo (docs/design/rules.md > Focus and current; decision 0011): a soft accent glow
 * outside the focus ring on dark themes. The maths is the approved design board's `halo()`.
 *
 * The accent's lightness is clamped to [LIGHTNESS_MIN]..[LIGHTNESS_MAX], so a dark accent still shows and a bright one
 * does not glare; the peak alpha falls from [PEAK] to [PEAK] * [BRIGHT_FACTOR] as the clamped colour's luminance rises
 * from [BRIGHT_FROM] to [BRIGHT_TO] (smoothstep). Light themes get none.
 */
internal object FocusHalo {
    /** The halo's colour and its strongest alpha (next to the ring). */
    class Look(
        val colour: Rgb,
        val peak: Double,
    )

    /** Null on a light background (Paper keeps the ring alone). */
    fun look(
        accent: Rgb,
        background: Rgb,
    ): Look? {
        if (ColorMath.luminance(background) >= LIGHT_BACKGROUND) return null
        val (hue, saturation, lightness) = ColorMath.toHsl(accent)
        val colour = ColorMath.fromHsl(hue, saturation, lightness.coerceIn(LIGHTNESS_MIN, LIGHTNESS_MAX))
        val bright = smoothstep(BRIGHT_FROM, BRIGHT_TO, ColorMath.luminance(colour))
        return Look(colour, PEAK * (1 - (1 - BRIGHT_FACTOR) * bright))
    }

    /**
     * The glow of a focused collar part: the same as [look] on a dark background; on a light one the accent itself at [LIGHT_PART_PEAK]
     * (the light board's `glowColor`: the accent at 0.30), so a primary button, whose groove and cap are the accent, still stands off a resting one.
     * Never null.
     */
    fun partLook(
        accent: Rgb,
        background: Rgb,
    ): Look = look(accent, background) ?: Look(accent, LIGHT_PART_PEAK)

    /** Alpha of band [band] (0 = next to the ring) of [steps]: the stack fades as (1 - t)^2 from [peak] to zero. */
    fun bandAlpha(
        peak: Float,
        band: Int,
        steps: Int,
    ): Float = peak * (1 - (band + 0.5f) / steps).pow(2)

    /**
     * How much of a blurred shape's strength shows [distance] px outside its edge (negative: inside), for a Gaussian blur of [sigma] px:
     * 0.5 at the edge, 1 deep inside, 0 far outside. CSS `box-shadow` blurs this way, so the slab's bands follow it.
     */
    fun softEdge(
        distance: Float,
        sigma: Float,
    ): Float = (0.5 * erfc(distance / (sigma * SQRT2))).toFloat()

    /** Abramowitz and Stegun 7.1.26: error below 1.5e-7. */
    private fun erfc(x: Double): Double {
        val t = 1.0 / (1.0 + 0.3275911 * kotlin.math.abs(x))
        val poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
        val erf = 1.0 - poly * kotlin.math.exp(-x * x)
        return 1.0 - if (x >= 0) erf else -erf
    }

    private const val SQRT2 = 1.4142135623730951

    private fun smoothstep(
        from: Double,
        to: Double,
        x: Double,
    ): Double {
        val t = ((x - from) / (to - from)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** A background brighter than this (relative luminance) is a light theme: no halo. Same line as the cover shadow's. */
    const val LIGHT_BACKGROUND = 0.5
    const val LIGHTNESS_MIN = 0.50
    const val LIGHTNESS_MAX = 0.66
    const val PEAK = 0.32 * Depth.GLOW
    const val LIGHT_PART_PEAK = 0.24 * Depth.GLOW

    /** Peak alpha of the accent blob under a focused row's slab on a light theme (the Paper board's measured 0.5; dark themes use [look]). */
    const val LIGHT_SLAB_PEAK = 0.5

    /** The focused slab's glow, the boards' `0 0 22px rgba(accent, 0.3)`: the accent at 0.3 on every theme. */
    const val SLAB_GLOW = 0.3 * Depth.GLOW
    const val BRIGHT_FACTOR = 0.5
    const val BRIGHT_FROM = 0.45
    const val BRIGHT_TO = 0.80
}
