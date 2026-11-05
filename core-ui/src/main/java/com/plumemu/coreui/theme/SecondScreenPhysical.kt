package com.plumemu.coreui.theme

import kotlin.math.ceil
import kotlin.math.hypot

/**
 * What a display is, physically: its pixels, the density Android gives it and its diagonal. [inchesPerDp] is how big one dp is on
 * the glass, which is what a person reads.
 */
data class PanelFacts(
    val widthPx: Int,
    val heightPx: Int,
    /** Pixels per dp (`DisplayMetrics.density`). */
    val density: Float,
    val diagonalInches: Double,
) {
    val ppi: Double get() = hypot(widthPx.toDouble(), heightPx.toDouble()) / diagonalInches

    val inchesPerDp: Double get() = density / ppi

    /** The height of [sp] (at text size factor 1 and font scale 1) in millimetres. */
    fun millimetres(sp: Float): Double = sp * inchesPerDp * MM_PER_INCH

    private companion object {
        const val MM_PER_INCH = 25.4
    }
}

/**
 * The AYN Thor's two panels, from the device's recorded display dump (top 1920x1080 at 367 dpi, bottom 1240x1080 at 369 dpi) and the
 * panel sizes of its data sheet (6.0 and 3.92 inches). Android's own xdpi is not read: the bottom screen's density is set as if it were
 * a 4.5 inch panel, so a dp is about 12 % smaller there than on the top screen.
 */
object ThorPanels {
    val top = PanelFacts(1920, 1080, 367f / 160f, 6.0)
    val bottom = PanelFacts(1240, 1080, 369f / 160f, 3.92)

    /** The diagonal of a Thor panel by its pixel size, null for any other. */
    fun diagonalOf(
        widthPx: Int,
        heightPx: Int,
    ): Double? = listOf(top, bottom).firstOrNull { it.widthPx == widthPx && it.heightPx == heightPx }?.diagonalInches
}

/**
 * The second screen's text is drawn larger by [scale] so that it is at least as big to the eye as the same style on the main screen:
 * a dp is smaller on a denser panel. Every second-screen size is authored in dp at that screen's own density; the window multiplies
 * its font scale by this, so every sp there (and the Text size setting on top of it) comes out that much bigger.
 */
object SecondScreenPhysical {
    /** Never shrinks the text, and never more than this (a wrong dpi must not blow the cards up). */
    const val MAX_SCALE = 1.5f

    fun scale(
        second: PanelFacts,
        main: PanelFacts,
    ): Float = (ceil(main.inchesPerDp / second.inchesPerDp * ROUND) / ROUND).toFloat().coerceIn(1f, MAX_SCALE)

    /** Rounded up to the thousandth, so a style is never a hair under the main screen's. */
    private const val ROUND = 1000.0

    /** The scale for the Thor, where Android's densities are known to read the panels wrongly. */
    val thor: Float = scale(ThorPanels.bottom, ThorPanels.top)
}
