package com.plumemu.coreui.theme

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * A colour in OKLCH: perceptual lightness [l] (0..1), chroma [c] and hue [h] in radians. Moving [l] keeps the hue as the eye
 * sees it, which HSL lightness does not (dark yellow turns olive). [toRgb] clips chroma into sRGB, never the hue.
 */
data class Oklch(
    val l: Double,
    val c: Double,
    val h: Double,
) {
    /** The nearest sRGB colour: this lightness and hue, chroma cut down until it fits. */
    fun toRgb(): Rgb {
        val fitting = if (linear(l, c, h) != null) c else maxChroma(l, h, c)
        val v = linear(l, fitting, h) ?: doubleArrayOf(l, l, l)
        return Rgb.fromChannels(encode(v[0]) * 255, encode(v[1]) * 255, encode(v[2]) * 255)
    }

    companion object {
        fun of(colour: Rgb): Oklch {
            val r = decode(colour.red)
            val g = decode(colour.green)
            val b = decode(colour.blue)
            val lc = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val mc = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val sc = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            val l = 0.2104542553 * lc + 0.7936177850 * mc - 0.0040720468 * sc
            val a = 1.9779984951 * lc - 2.4285922050 * mc + 0.4505937099 * sc
            val bb = 0.0259040371 * lc + 0.7827717662 * mc - 0.8086757660 * sc
            return Oklch(l, hypot(a, bb), atan2(bb, a))
        }

        /** The most chroma, up to [upTo], that sRGB shows at lightness [l] and hue [h]. */
        fun maxChroma(
            l: Double,
            h: Double,
            upTo: Double,
        ): Double {
            var lo = 0.0
            var hi = upTo
            repeat(CHROMA_STEPS) {
                val m = (lo + hi) / 2
                if (linear(l, m, h) != null) lo = m else hi = m
            }
            return lo
        }

        /** Linear sRGB channels, or null when the colour lies outside sRGB. */
        private fun linear(
            l: Double,
            c: Double,
            h: Double,
        ): DoubleArray? {
            val a = c * cos(h)
            val b = c * sin(h)
            val lc = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
            val mc = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
            val sc = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
            val rgb =
                doubleArrayOf(
                    4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc,
                    -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc,
                    -0.0041960863 * lc - 0.7034186147 * mc + 1.7076127090 * sc,
                )
            return if (rgb.all { it in -GAMUT_SLACK..1 + GAMUT_SLACK }) rgb else null
        }

        private fun decode(v: Int): Double = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

        private fun encode(x: Double): Double = x.coerceIn(0.0, 1.0).let { if (it <= 0.0031308) 12.92 * it else 1.055 * it.pow(1 / 2.4) - 0.055 }

        private const val CHROMA_STEPS = 25
        private const val GAMUT_SLACK = 1e-6
    }
}
