package com.plumemu.coreui.theme

/**
 * Hue, saturation and lightness of a colour, for the colour sliders of Settings (the same
 * conversion [Readability] moves lightness with). Hue is in turns (0..1), saturation and
 * lightness in 0..1.
 */
object HslColors {
    fun toHsl(colour: Rgb): Triple<Double, Double, Double> = ColorMath.toHsl(colour)

    fun fromHsl(
        hue: Double,
        saturation: Double,
        lightness: Double,
    ): Rgb = ColorMath.fromHsl(hue, saturation, lightness)
}
