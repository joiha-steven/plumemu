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
     * The colour to offer when [colour] is refused for [role] on [page] (design/rules.md > Suggesting a colour); [colour] itself when it passes.
     * Unlike [nearestPassing], which settles a stored colour and keeps its HSL hue and saturation, a suggestion may change more to look right:
     * - it moves OKLCH lightness, keeping the hue the eye sees and as much chroma as sRGB shows there;
     * - an accent aims at [SUGGESTED_ACCENT_CONTRAST], where the sets live, not at the floor;
     * - an accent that would keep under [MUD_CHROMA] of its chroma (dark yellow turning olive) turns its hue up to [MUD_TURN_DEGREES] away
     *   from yellow ([YELLOW_HUE]), toward the neighbour that holds its colour: yellow to marigold, orange to rust, lime to green;
     * - on a coloured page (chroma at least [COLOURED_PAGE]) an accent within [CLASH_DEGREES] of the page's hue is offered at the opposite hue
     *   (180°, then 150° and 210°, then 120° and 240°), at least as colourful as the page;
     * - text keeps quiet: the page's own hue at chroma at most [TEXT_CHROMA].
     * Falls back to [nearestPassing] where none of that passes.
     */
    fun suggest(
        colour: Rgb,
        page: Rgb,
        role: ColorRole,
    ): Rgb {
        if (passes(colour, page, role)) return colour
        val own = Oklch.of(colour)
        val ground = Oklch.of(page)
        val found =
            when (role) {
                ColorRole.Text -> {
                    val hue = if (ground.c >= GREY_PAGE) ground.h else own.h
                    moveLightness(Oklch(own.l, minOf(own.c, TEXT_CHROMA), hue), page) { passes(it, page, ColorRole.Text) }
                }
                ColorRole.Accent -> {
                    val clashes = ground.c >= COLOURED_PAGE && hueDistance(own.h, ground.h) < Math.toRadians(CLASH_DEGREES)
                    val starts =
                        if (clashes) {
                            OPPOSITE_TURNS.map { Oklch(own.l, maxOf(own.c, ground.c), ground.h + Math.toRadians(it)) }
                        } else {
                            listOf(own)
                        }
                    starts.firstNotNullOfOrNull { accentWithoutMud(it, page) }
                }
            }
        return found ?: nearestPassing(colour, page, role)
    }

    /** [start] moved in OKLCH lightness to [SUGGESTED_ACCENT_CONTRAST] on [page], its hue turned when the move would muddy it; null when nothing passes. */
    private fun accentWithoutMud(
        start: Oklch,
        page: Rgb,
    ): Rgb? {
        val reaches = { c: Rgb -> ColorMath.contrast(c, page) >= SUGGESTED_ACCENT_CONTRAST }
        val straight = moveLightness(start, page, reaches) ?: return null
        if (start.c < GREY_PAGE || kept(straight, start) >= MUD_CHROMA) return straight
        var best = straight
        val away = if (((Math.toDegrees(start.h) % 360) + 360) % 360 < YELLOW_HUE) -1 else 1
        var turn = MUD_TURN_STEP
        while (turn <= MUD_TURN_DEGREES) {
            val turned = moveLightness(start.copy(h = start.h + away * Math.toRadians(turn)), page, reaches)
            if (turned != null) {
                if (kept(turned, start) >= MUD_CHROMA) return turned
                if (kept(turned, start) > kept(best, start)) best = turned
            }
            turn += MUD_TURN_STEP
        }
        return best
    }

    private fun kept(
        moved: Rgb,
        start: Oklch,
    ) = Oklch.of(moved).c / start.c

    /** The nearest OKLCH lightness of [start] (its hue, chroma clipped to sRGB) that [ok] accepts, the higher contrast when both ways tie. */
    private fun moveLightness(
        start: Oklch,
        page: Rgb,
        ok: (Rgb) -> Boolean,
    ): Rgb? {
        for (step in 0..LIGHTNESS_STEPS) {
            val shift = step / LIGHTNESS_STEPS.toDouble()
            val found = listOf(start.l + shift, start.l - shift).filter { it in 0.0..1.0 }.distinct().map { start.copy(l = it).toRgb() }.filter(ok)
            if (found.isNotEmpty()) return found.maxBy { ColorMath.contrast(it, page) }
        }
        return null
    }

    private fun hueDistance(
        a: Double,
        b: Double,
    ): Double {
        val d = ((Math.toDegrees(a - b) % 360) + 360) % 360
        return Math.toRadians(minOf(d, 360 - d))
    }

    /** Where suggested accents land: the sets' own margin over the 3:1 floor. */
    const val SUGGESTED_ACCENT_CONTRAST = 3.5
    const val MUD_CHROMA = 0.85
    const val MUD_TURN_DEGREES = 40.0
    private const val MUD_TURN_STEP = 5.0

    /** OKLCH hue of the purest yellow: a muddy hue below it turns toward red, one above it toward green. */
    const val YELLOW_HUE = 110.0
    const val COLOURED_PAGE = 0.06
    const val CLASH_DEGREES = 60.0
    const val TEXT_CHROMA = 0.04
    private const val GREY_PAGE = 0.02
    private val OPPOSITE_TURNS = listOf(180.0, 150.0, 210.0, 120.0, 240.0)

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
