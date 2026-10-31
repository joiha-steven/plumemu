package com.plumemu.coreui.theme

/**
 * The two text presets, Light and Dark. Text is any colour;
 * these stay as its presets, and a [TextMode] is also what the rest of the theme branches on:
 * [of] derives it from the text colour actually drawn. When a background change leaves a preset
 * unreadable it becomes the [bestOn] one.
 */
enum class TextMode(
    val color: Rgb,
) {
    Light(Rgb(0xEDEDED)),
    Dark(Rgb(0x17181A)),
    ;

    companion object {
        /** Light when [text] is lighter than [background], else Dark: what branches on the mode (strong text, accent presets, system bars) follows the colour drawn. */
        fun of(
            text: Rgb,
            background: Rgb,
        ): TextMode = if (ColorMath.luminance(text) > ColorMath.luminance(background)) Light else Dark

        /** The mode whose text colour contrasts more with [background]. */
        fun bestOn(background: Rgb): TextMode =
            if (ColorMath.contrast(Dark.color, background) > ColorMath.contrast(Light.color, background)) Dark else Light
    }
}

/** The eight background presets of Settings › Appearance › Background (design/rules.md › Colour). */
enum class BackgroundPreset(
    val color: Rgb,
) {
    Graphite(Rgb(0x0E0F11)),
    Black(Rgb(0x000000)),
    Midnight(Rgb(0x0B1220)),
    Plum(Rgb(0x1A1020)),
    Forest(Rgb(0x0E1A14)),
    Paper(Rgb(0xF4F3EF)),
    Sand(Rgb(0xEEE6D6)),
    Mist(Rgb(0xE8ECF0)),
}

/**
 * Accent presets. Each has one colour for light text and a deeper one for dark text; [Mono] is
 * the text colour itself. Red is the default.
 */
enum class AccentPreset(
    private val withLightText: Rgb?,
    private val withDarkText: Rgb?,
) {
    Red(Rgb(0xDA4655), Rgb(0x9E2A3A)),
    Amber(Rgb(0xE8A13A), Rgb(0x8F5500)),
    Lilac(Rgb(0xB583E8), Rgb(0x7A3FC0)),
    Blue(Rgb(0x6FA8FF), Rgb(0x1F5FBF)),
    Green(Rgb(0x5FD38D), Rgb(0x1A6B3B)),
    Mono(null, null),
    ;

    fun colorFor(textMode: TextMode): Rgb =
        when (textMode) {
            TextMode.Light -> withLightText
            TextMode.Dark -> withDarkText
        } ?: textMode.color

    companion object {
        val Default: AccentPreset = Red
    }
}
