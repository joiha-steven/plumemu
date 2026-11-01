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

    /** Alpha of band [band] (0 = next to the ring) of [steps]: the stack fades as (1 - t)^2 from [peak] to zero. */
    fun bandAlpha(
        peak: Float,
        band: Int,
        steps: Int,
    ): Float = peak * (1 - (band + 0.5f) / steps).pow(2)

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
    const val PEAK = 0.32
    const val BRIGHT_FACTOR = 0.5
    const val BRIGHT_FROM = 0.45
    const val BRIGHT_TO = 0.80
}
