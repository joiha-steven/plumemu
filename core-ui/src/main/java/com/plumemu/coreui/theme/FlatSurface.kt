package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The one depth recipe of every surface (docs/design/rules.md > Depth): no surface is painted with a gradient. A button, key cap, chip, row
 * slab, card, second-screen tile (the slab's plate), field or tray has one flat face; whether it stands up or sits down is told by its edges and shadows only:
 *  - raised: the flat face on a solid lower edge, a lit hairline along its top inside its edge, and a tight drop shadow under it
 *    (plus its own thickness, for a cap); the focused row's slab and the second-screen tile are the exception: a lighter flat face and a soft drop shadow, no line at all ([FocusSlab]);
 *  - sunken: a flat face a step darker than what is round it, and a dark inner edge along its top;
 *  - pressed: a raised part sinks (moves down by its travel, its drop gone, the dark inner top edge on).
 * No soft inner shade rising from a part's foot either: that is a gradient by another name, and it is what made a cap read as a dome.
 * Where the boards drew a gradient, the flat face is that gradient's [mean], so a part keeps its tone and its text its contrast.
 * Gradients stay only where they are not a surface: list edges that fade, scrims, shadows and glows, the page's grain and backdrop, charts,
 * a colour slider's spectrum, and the poster covers that stand in for box art. The `flat-surfaces` guard keeps them to those files.
 */
object FlatSurface {
    /**
     * The one scrim behind a raised overlay card (the game menu, its panels and questions): the page's own colour at this alpha, flat, over
     * the whole screen, faded in and out with the card. What lies behind recedes toward the page instead of fighting the card.
     */
    const val SCRIM_ALPHA = 0.6f

    /**
     * A raised card's solid lower edge: the whole card in its [base] colour and its
     * face over it this much short of its foot, so the card stands on a band of it, as a key cap on its thickness. The
     * game panel's card, which has room under it, draws its band below its box.
     */
    val raisedEdge = 2.dp

    /** How far below the face the solid edge is, in CIE L*: dark themes toward black, light ones toward the warm shadow. */
    const val BASE_DARK_L = 14.0
    const val BASE_LIGHT_L = 22.0

    /** A sunken part's crisp dark inner top edge (`inset 0 1px 0`), drawn at `Depth.RAISE`: black on dark themes, the warm shadow on light ones. */
    val sunkenEdge = 1.dp
    const val SUNKEN_EDGE_DARK = 0.75f
    const val SUNKEN_EDGE_LIGHT = 0.40f

    /** [face] moved [step] L* toward [toward]: a raised plate's solid edge. */
    fun base(
        face: Rgb,
        toward: Rgb,
        step: Double,
    ): Rgb = ColorMath.mix(face, toward, ColorMath.shareForLightness(face, toward, step).toDouble().coerceIn(0.0, 1.0))

    /**
     * The mean colour of a gradient through [stops] at [at] (shares of its length, 0..1, rising; evenly spaced when null), taken along its
     * length: the colour each stretch shows on average, the ends held past the first and last stop. Channels are averaged with their alpha
     * (premultiplied), so a translucent white fade stays white.
     */
    fun mean(
        stops: List<Color>,
        at: List<Float>? = null,
    ): Color {
        require(stops.isNotEmpty()) { "a gradient has at least one stop" }
        if (stops.size == 1) return stops[0]
        val places = at ?: List(stops.size) { it.toFloat() / (stops.size - 1) }
        require(places.size == stops.size) { "one place per stop" }
        var a = 0f
        var r = 0f
        var g = 0f
        var b = 0f
        fun add(
            c: Color,
            weight: Float,
        ) {
            a += c.alpha * weight
            r += c.red * c.alpha * weight
            g += c.green * c.alpha * weight
            b += c.blue * c.alpha * weight
        }
        add(stops.first(), places.first().coerceIn(0f, 1f))
        for (i in 0 until stops.lastIndex) {
            val length = (places[i + 1].coerceIn(0f, 1f) - places[i].coerceIn(0f, 1f)).coerceAtLeast(0f)
            add(stops[i], length / 2)
            add(stops[i + 1], length / 2)
        }
        add(stops.last(), 1f - places.last().coerceIn(0f, 1f))
        if (a <= 0f) return Color.Transparent
        return Color(r / a, g / a, b / a, a)
    }
}
