package com.plumemu.coreui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Colour arithmetic ported one-to-one from the design board's reference code
 * (`mix`, `lum`, `contrast`, `reach`, `fromHsl`),
 * so derived tokens match the board to the hex digit. Every intermediate colour is rounded to
 * `#RRGGBB` exactly where the board rounds it.
 */
internal object ColorMath {
    /** Straight RGB blend: `t = 0` gives [a], `t = 1` gives [b]. */
    fun mix(
        a: Rgb,
        b: Rgb,
        t: Double,
    ): Rgb =
        Rgb.fromChannels(
            a.red + (b.red - a.red) * t,
            a.green + (b.green - a.green) * t,
            a.blue + (b.blue - a.blue) * t,
        )

    /**
     * The linear value of each 8-bit channel, worked out once: the readability checks run `luminance`
     * hundreds of times per slider move (every value of a track), and the `pow` in it was most of the
     * UI thread's time on the Retroid Pocket Classic. Same arithmetic, so same results.
     */
    private val linearOfChannel =
        DoubleArray(256) { channel ->
            val v = channel / 255.0
            if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }

    /** WCAG relative luminance, with the board's 0.03928 threshold. */
    fun luminance(c: Rgb): Double = 0.2126 * linearOfChannel[c.red] + 0.7152 * linearOfChannel[c.green] + 0.0722 * linearOfChannel[c.blue]

    /** WCAG contrast ratio, 1.0 to 21.0. */
    fun contrast(
        a: Rgb,
        b: Rgb,
    ): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /**
     * The first colour on the way from [from] to [to], in steps of 0.02, whose contrast against
     * [on] reaches [target]; [to] itself if none does. The step is accumulated by repeated
     * addition, as in the board, so the floating-point path is the same.
     */
    fun reach(
        from: Rgb,
        to: Rgb,
        on: Rgb,
        target: Double,
    ): Rgb {
        var t = 0.0
        while (t < 1) {
            val m = mix(from, to, t)
            if (contrast(m, on) >= target) return m
            t += 0.02
        }
        return to
    }

    /** HSL to RGB; [hue] in turns (0..1), [saturation] and [lightness] in 0..1. */
    fun fromHsl(
        hue: Double,
        saturation: Double,
        lightness: Double,
    ): Rgb {
        fun f(n: Int): Double {
            val k = (n + hue * 12) % 12
            val clamp = max(-1.0, min(min(k - 3, 9 - k), 1.0))
            return lightness - saturation * min(lightness, 1 - lightness) * clamp
        }
        return Rgb.fromChannels(f(0) * 255, f(8) * 255, f(4) * 255)
    }

    /** RGB to HSL: hue in turns (0..1), saturation and lightness in 0..1. The inverse of [fromHsl] up to rounding. */
    fun toHsl(c: Rgb): Triple<Double, Double, Double> {
        val r = c.red / 255.0
        val g = c.green / 255.0
        val b = c.blue / 255.0
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val lightness = (hi + lo) / 2
        val delta = hi - lo
        if (delta == 0.0) return Triple(0.0, 0.0, lightness)
        val saturation = delta / (1 - kotlin.math.abs(2 * lightness - 1))
        val sector =
            when (hi) {
                r -> ((g - b) / delta + 6) % 6
                g -> (b - r) / delta + 2
                else -> (r - g) / delta + 4
            }
        return Triple(sector / 6, saturation, lightness)
    }
}
