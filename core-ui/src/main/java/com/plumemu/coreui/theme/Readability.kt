package com.plumemu.coreui.theme

/** What a colour is for: text needs the targets below, an accent only 3:1 on the background. */
enum class ColorRole { Text, Accent }

/**
 * The one readability check. Nothing unreadable can be picked,
 * stored-and-drawn, or left behind by a background change:
 *
 * - **Text passes** (a person's own colour; presets and ready-made sets keep the full targets of
 *   `ColorSetsTest`) when the text is at least 7:1 on the background, and every
 *   text token derived from it still meets: meta 6:1 and muted 4.6:1 on the focused row's fill,
 *   key-cap borders 3:1, text and strong text on the focused row 4.5:1, and on the keyboard tray
 *   4.5:1. Secondary text reaches 11:1 where the text allows it; where not, it is the text itself
 *   ([ColorMath.reach] stops at the text), so it is not a target of its own.
 * - **An accent passes** at 3:1 on the background.
 *
 * [nearestPassing] moves a failing colour in HSL lightness only (hue and saturation stay) the
 * shortest way. Where no lightness of that hue passes, or no text colour at all can (a background
 * of middle lightness: pure black needs a background luminance of at least 0.35 for 7:1, pure white
 * at most 0.0833), it falls back to the better of Light and Dark, which is the best available and
 * what a background change did before colours were free.
 */
object Readability {
    /** Accent on the background. */
    const val ACCENT_MIN_CONTRAST = 3.0

    /** A person's own text colour on the background (bolder colours than the sets' 11:1). */
    const val TEXT_MIN_CONTRAST = 7.0

    private const val LIGHTNESS_STEPS = 200

    /** True when [colour], used for [role] on [background], meets the targets. */
    fun passes(
        colour: Rgb,
        background: Rgb,
        role: ColorRole,
    ): Boolean =
        when (role) {
            ColorRole.Accent -> ColorMath.contrast(colour, background) >= ACCENT_MIN_CONTRAST
            ColorRole.Text -> textChecks(colour, background).none { it.fails }
        }

    private class Check(
        val what: String,
        val ratio: Double,
        val target: Double,
    ) {
        val fails: Boolean get() = ratio < target
    }

    private fun textChecks(
        text: Rgb,
        background: Rgb,
    ): List<Check> {
        val c = PlumColors.derive(background, text, accent = text)
        return listOf(
            Check("text on bg", ColorMath.contrast(c.text, c.bg), TEXT_MIN_CONTRAST),
            Check("meta on sel", ColorMath.contrast(c.meta, c.sel), 6.0),
            Check("muted on sel", ColorMath.contrast(c.muted, c.sel), 4.6),
            Check("chip on bg", ColorMath.contrast(c.chip, c.bg), 3.0),
            Check("text on sel", ColorMath.contrast(c.text, c.sel), 4.5),
            Check("strong on sel", ColorMath.contrast(c.strong, c.sel), 4.5),
            Check("text on tray", ColorMath.contrast(c.text, c.tray), 4.5),
            Check("text2 on trayKey", ColorMath.contrast(c.text2, c.trayKey), 4.5),
            Check("strong on trayKey", ColorMath.contrast(c.strong, c.trayKey), 4.5),
        )
    }

    /** The targets a text colour misses on [background], by name with the ratio reached (for tests and logs: it formats strings, [passes] does not); empty when it passes. */
    fun textFailures(
        text: Rgb,
        background: Rgb,
    ): List<String> = textChecks(text, background).filter { it.fails }.map { "%s %.2f < %.1f".format(java.util.Locale.ROOT, it.what, it.ratio, it.target) }

    /**
     * [colour] itself when it passes on [against]; else the colour of the same hue and saturation
     * whose HSL lightness is the shortest way off and passes (the higher contrast when both ways
     * tie); else the better of Light and Dark ([TextMode.bestOn]).
     */
    fun nearestPassing(
        colour: Rgb,
        against: Rgb,
        role: ColorRole,
    ): Rgb {
        if (passes(colour, against, role)) return colour
        val (hue, saturation, lightness) = ColorMath.toHsl(colour)
        for (step in 1..LIGHTNESS_STEPS) {
            val shift = step / LIGHTNESS_STEPS.toDouble()
            val found =
                listOf(lightness + shift, lightness - shift)
                    .filter { it in 0.0..1.0 }
                    .map { ColorMath.fromHsl(hue, saturation, it) }
                    .filter { passes(it, against, role) }
            if (found.isNotEmpty()) return found.maxBy { ColorMath.contrast(it, against) }
        }
        return TextMode.bestOn(against).color
    }

    /**
     * True when some text colour can read on [background]: Light or Dark passes. Middle backgrounds
     * (relative luminance about 0.083 to 0.35 at 7:1, e.g. #808080) allow none, so they are never offered or
     * accepted as a background; backgrounds already stored there
     * keep working through the best-available fallback of [nearestPassing].
     */
    fun backgroundAllowsText(background: Rgb): Boolean =
        TextMode.entries.any { passes(it.color, background, ColorRole.Text) }

    /**
     * [background] itself when text can read on it; else the colour of the same hue and saturation
     * whose HSL lightness is the shortest way off and allows text (the better contrast when both
     * ways tie). Lightness only, like every adjustment here.
     */
    fun nearestUsableBackground(background: Rgb): Rgb {
        if (backgroundAllowsText(background)) return background
        val (hue, saturation, lightness) = ColorMath.toHsl(background)
        for (step in 1..LIGHTNESS_STEPS) {
            val shift = step / LIGHTNESS_STEPS.toDouble()
            val found =
                listOf(lightness + shift, lightness - shift)
                    .filter { it in 0.0..1.0 }
                    .map { ColorMath.fromHsl(hue, saturation, it) }
                    .filter(::backgroundAllowsText)
            if (found.isNotEmpty()) return found.maxBy { bg -> TextMode.entries.maxOf { ColorMath.contrast(it.color, bg) } }
        }
        return Rgb(0x000000)
    }

    /**
     * The text to draw on [background] for stored [text]: kept if it passes; Light or Dark that
     * fails becomes the better of the two (as before colours were free); any other colour moves in
     * lightness ([nearestPassing]).
     */
    fun settleText(
        text: Rgb,
        background: Rgb,
    ): Rgb =
        when {
            passes(text, background, ColorRole.Text) -> text
            text == TextMode.Light.color || text == TextMode.Dark.color -> TextMode.bestOn(background).color
            else -> nearestPassing(text, background, ColorRole.Text)
        }

    /**
     * [text] and [accent] settled on [background]: what is drawn, and what a background change
     * writes. A preset accent follows the settled text's mode and is kept while its colour passes;
     * a failing one becomes the person's-own colour nearest to it ([AccentChoice.Custom]).
     */
    fun settle(
        background: Rgb,
        text: Rgb,
        accent: AccentChoice,
    ): Settled {
        val settledText = settleText(text, background)
        val mode = TextMode.of(settledText, background)
        val resolved = accent.resolve(mode)
        val settledAccent =
            when {
                passes(resolved, background, ColorRole.Accent) -> accent
                // By system stays: only the colour it falls back to moves (systems settle one by one, SystemAccents).
                accent is AccentChoice.BySystem -> AccentChoice.BySystem(AccentChoice.Custom(nearestPassing(resolved, background, ColorRole.Accent)))
                else -> AccentChoice.Custom(nearestPassing(resolved, background, ColorRole.Accent))
            }
        return Settled(settledText, settledAccent, textAdjusted = settledText != text, accentAdjusted = settledAccent !== accent)
    }

    /** The result of [settle]; the flags tell the screen to say that something moved. */
    data class Settled(
        val text: Rgb,
        val accent: AccentChoice,
        val textAdjusted: Boolean,
        val accentAdjusted: Boolean,
    ) {
        val adjusted: Boolean get() = textAdjusted || accentAdjusted
    }
}
