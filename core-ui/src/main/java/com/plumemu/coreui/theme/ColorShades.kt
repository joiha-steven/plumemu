package com.plumemu.coreui.theme

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin

/** What an own colour is for in the hue-then-shade picker. */
enum class ShadeRole(
    /** Background and Text offer a grey stop before the hues; an accent has none. */
    val hasGrey: Boolean,
) {
    Background(true),
    Text(true),
    Accent(false),
}

/**
 * The colour maths of the hue-then-shade picker:
 * a strip of [HUE_COUNT] hues (grey first for Background and Text), then up to [SHADE_COUNT] shades
 * of the chosen hue that Plumemu works out so each one passes [Readability] on the page.
 *
 * A stop is an index into the strip. For a role with a grey ([ShadeRole.hasGrey]) stop 0 is grey and
 * stop `i + 1` is hue `i`; for Accent stop `i` is hue `i`. Pure and deterministic; a few dozen
 * contrast checks per call (bisection on lightness, no search loops).
 */
object ColorShades {
    const val HUE_COUNT = 24
    const val SHADE_COUNT = 5

    /** Chroma under which an own colour counts as grey when it is matched to a stop. */
    const val GREY_CHROMA = 0.02

    /** Two shades closer than this in OKLab are one shade (a narrow passing range gives fewer). */
    const val MIN_DISTANCE = 0.03

    // Accents stay vivid but not neon: the sets' own accents sit at chroma 0.15 to 0.19 (Red DA4655, Blue 6FA8FF).
    private const val ACCENT_CHROMA = 0.19

    // Text and background tints stay quiet: text is Readability.suggest's own cap; a background tint
    // stops at the chroma of the vivid light presets (about 0.12 for the amber ones).
    private const val BACKGROUND_CHROMA = 0.10
    private const val ACCENT_NEAR_CONTRAST = Readability.SUGGESTED_ACCENT_CONTRAST
    private const val LIGHT_FAR = 0.92
    private const val DARK_FAR = 0.30
    private const val TEXT_LIGHT_FAR = 0.97
    private const val TEXT_DARK_FAR = 0.18
    private const val BISECT_STEPS = 12
    private val BACKGROUND_LIGHTNESS = doubleArrayOf(0.95, 0.85, 0.36, 0.27, 0.18)
    private const val STRIP_GREY_LIGHTNESS = 0.68

    // The strip's hues are drawn at their chroma peak, but never neon: the sets' own vivid accents sit at 0.15 to 0.19.
    private const val STRIP_CHROMA_CAP = 0.19
    private const val CUSP_LIGHTNESS_STEPS = 90
    private const val CUSP_LIGHTNESS_FROM = 0.20
    private const val CUSP_LIGHTNESS_TO = 0.99
    private const val CUSP_MAX_CHROMA = 0.40

    /** No strip stop is darker than this (OKLCH lightness): the darkest cusp is blue's, near 0.45. Yellow's is near 0.97, so it reads as yellow, never olive. */
    const val STRIP_LIGHTNESS_FLOOR = 0.40
    private const val NEIGHBOURS = 3
    private const val TWO_PI = 2 * PI

    /** OKLCH hue of sRGB red (#FF0000), the first stop: a clean red. */
    private const val RED_HUE_DEGREES = 29.0

    // Equal steps of OKLCH hue, so neighbouring stops differ by about the same to the eye (HSL degrees bunched the greens).
    private val hues: List<Double> = List(HUE_COUNT) { i -> Math.toRadians((RED_HUE_DEGREES + 360.0 * i / HUE_COUNT) % 360.0) }

    /** OKLCH hue (radians, 0 until 2 pi) of stop i: [RED_HUE_DEGREES] plus i equal steps of 15 degrees, red first. */
    fun hueStops(): List<Double> = hues

    /** How many stops [role] has: 25 with grey, 24 without. */
    fun stopCount(role: ShadeRole): Int = HUE_COUNT + if (role.hasGrey) 1 else 0

    /** True when [stop] is the grey one of [role]. */
    fun isGrey(
        role: ShadeRole,
        stop: Int,
    ): Boolean = role.hasGrey && stop == 0

    /** The index into [hueStops] of [stop] (not grey). */
    fun hueIndex(
        role: ShadeRole,
        stop: Int,
    ): Int = if (role.hasGrey) stop - 1 else stop

    // Each hue at the lightness where sRGB shows it most vivid (the gamut's cusp), chroma capped.
    private val stripHues: List<Rgb> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        hues.map { h ->
            var bestL = CUSP_LIGHTNESS_FROM
            var bestC = -1.0
            for (i in 0..CUSP_LIGHTNESS_STEPS) {
                val l = CUSP_LIGHTNESS_FROM + (CUSP_LIGHTNESS_TO - CUSP_LIGHTNESS_FROM) * i / CUSP_LIGHTNESS_STEPS
                val c = Oklch.maxChroma(l, h, CUSP_MAX_CHROMA)
                if (c > bestC) {
                    bestC = c
                    bestL = l
                }
            }
            Oklch(bestL, minOf(bestC, STRIP_CHROMA_CAP), h).toRgb()
        }
    }

    /** The colour that draws [stop] on the strip: the hue at its chroma peak (its cusp lightness, chroma capped); a neutral mid grey. Drawing only. */
    fun stripColor(
        role: ShadeRole,
        stop: Int,
    ): Rgb = if (isGrey(role, stop)) Oklch(STRIP_GREY_LIGHTNESS, 0.0, 0.0).toRgb() else stripHues[hueIndex(role, stop)]

    /**
     * Up to [SHADE_COUNT] shades of [stop], lightest first, each passing the check of [role] on [page]
     * (Accent: [Readability.passes]; Text: [Readability.passes] for text; Background: [Readability.backgroundAllowsText],
     * and [page] is ignored since the page is what is being chosen). Fewer when the passing range is narrow, none
     * when nothing passes (a middle-grey page has no text colour).
     */
    fun shades(
        role: ShadeRole,
        stop: Int,
        page: Rgb,
    ): List<Rgb> {
        val hue = if (isGrey(role, stop)) 0.0 else hues[hueIndex(role, stop)]
        val grey = isGrey(role, stop)
        val raw =
            when (role) {
                ShadeRole.Background -> BACKGROUND_LIGHTNESS.map { colour(it, if (grey) 0.0 else BACKGROUND_CHROMA, hue) }.filter(Readability::backgroundAllowsText)
                ShadeRole.Text -> spread(page, hue, if (grey) 0.0 else Readability.TEXT_CHROMA, TEXT_LIGHT_FAR, TEXT_DARK_FAR) { Readability.passes(it, page, ColorRole.Text) }
                ShadeRole.Accent ->
                    spread(page, hue, ACCENT_CHROMA, LIGHT_FAR, DARK_FAR) {
                        ColorMath.contrast(it, page) >= ACCENT_NEAR_CONTRAST && Readability.passes(it, page, ColorRole.Accent)
                    }
            }
        return dedupe(raw)
    }

    /** The stop whose hue is nearest [colour]'s (grey when it has almost no chroma and [role] has one) and the index of its nearest shade. */
    fun nearestStopAndShade(
        role: ShadeRole,
        colour: Rgb,
        page: Rgb,
    ): Pair<Int, Int> {
        val own = Oklch.of(colour)
        // Pale and dark tints carry so little chroma that 8-bit rounding moves their hue by a stop, or makes them
        // grey: the grey and the nearest hues compete by their nearest shade, a tie going to the nearer hue
        // (to grey first when the colour has almost no chroma).
        val hueStops = hues.indices.sortedBy { hueDistance(hues[it], own.h) }.take(NEIGHBOURS).map { if (role.hasGrey) it + 1 else it }
        val near = if (!role.hasGrey) hueStops else if (own.c < GREY_CHROMA) listOf(0) + hueStops else hueStops + 0
        var best = Triple(near[0], 0, Double.MAX_VALUE)
        for (stop in near) {
            val list = shades(role, stop, page)
            val i = nearestIndex(list, colour)
            val d = if (list.isEmpty()) Double.MAX_VALUE else distance(list[i], colour)
            if (d < best.third) best = Triple(stop, i, d)
        }
        return best.first to best.second
    }

    private fun nearestIndex(
        list: List<Rgb>,
        colour: Rgb,
    ): Int = list.indices.minByOrNull { distance(list[it], colour) } ?: 0

    /** The shades of one side of [page]: the lightness of the nearest passing one found by bisection, then [SHADE_COUNT] even steps to the far end. */
    private fun spread(
        page: Rgb,
        hue: Double,
        chroma: Double,
        lightFar: Double,
        darkFar: Double,
        ok: (Rgb) -> Boolean,
    ): List<Rgb> {
        val lightFirst = TextMode.bestOn(page) == TextMode.Light
        for (light in listOf(lightFirst, !lightFirst)) {
            val far = if (light) lightFar else darkFar
            if (!ok(colour(far, chroma, hue))) continue
            var pass = far
            var fail = Oklch.of(page).l
            if (ok(colour(fail, chroma, hue))) {
                pass = fail
            } else {
                repeat(BISECT_STEPS) {
                    val mid = (pass + fail) / 2
                    if (ok(colour(mid, chroma, hue))) pass = mid else fail = mid
                }
            }
            val out = (0 until SHADE_COUNT).map { colour(pass + (far - pass) * it / (SHADE_COUNT - 1), chroma, hue) }.filter(ok)
            return if (light) out.reversed() else out
        }
        return emptyList()
    }

    private fun colour(
        l: Double,
        chroma: Double,
        hue: Double,
    ): Rgb = Oklch(l, chroma, hue).toRgb()

    /** Keeps the first of any two shades closer than [MIN_DISTANCE]. */
    private fun dedupe(list: List<Rgb>): List<Rgb> {
        val kept = ArrayList<Rgb>(list.size)
        for (c in list) if (kept.none { distance(it, c) < MIN_DISTANCE }) kept.add(c)
        return kept
    }

    private fun distance(
        a: Rgb,
        b: Rgb,
    ): Double {
        val x = Oklch.of(a)
        val y = Oklch.of(b)
        val da = x.c * cos(x.h) - y.c * cos(y.h)
        val db = x.c * sin(x.h) - y.c * sin(y.h)
        return hypot(x.l - y.l, hypot(da, db))
    }

    private fun hueDistance(
        a: Double,
        b: Double,
    ): Double {
        val d = abs(a - b) % TWO_PI
        return minOf(d, TWO_PI - d)
    }
}
