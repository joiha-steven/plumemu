package com.plumemu.coreui.theme

/**
 * Settings › Appearance › Colors: ready-made choices of the three colour settings,
 * inspired by classic handhelds and never named after one. A set is only a background, a text mode
 * and an accent (plus, for some, the colours of the A/B/X/Y hint glyphs, which `PlumTheme`
 * provides while the set is current); picking one moves the Background, Text color and Accent
 * color rows to its values.
 *
 * Which set is current is derived, never stored: the one whose resolved background colour, text
 * mode and resolved accent colour equal the current ones exactly ([matching]); none is "Custom".
 * Entry order is the cycle order of the row. Every set passes the contrast targets the presets
 * pass (`ColorSetsTest`); where the first value chosen did not, only its HSL lightness moved.
 */
enum class ColorSet(
    val background: BackgroundChoice,
    val textMode: TextMode,
    val accent: AccentChoice,
    /** Colours of the A/B/X/Y glyphs by printed letter; null keeps the usual look (`PlumTheme.buttons`). */
    val buttons: ButtonColors? = null,
    /**
     * Accents this set had before its current one (stored by people who picked it then, as an own hex colour). Settings that hold
     * one of these with the set's background and text still read as this set and draw its current accent ([settled]), so an update
     * that moved a set's accent does not turn it into Custom. Add the old value here whenever a set's accent changes.
     */
    val previousAccents: List<Rgb> = emptyList(),
) {
    /** Today's default look exactly (`PlumThemeSettings.Default`), so a fresh install shows it. */
    Plumemu(BackgroundChoice.Preset(BackgroundPreset.Black), TextMode.Light, AccentChoice.Preset(AccentPreset.Red)),
    Paper(BackgroundChoice.Preset(BackgroundPreset.Paper), TextMode.Dark, AccentChoice.Preset(AccentPreset.Red)),

    /** Background lightened from #C8C6BE so secondary text reaches 11:1. */
    Pocket(
        BackgroundChoice.Custom(Rgb(0xCDCCC4)),
        TextMode.Dark,
        AccentChoice.Custom(Rgb(0x8C1D4E)),
        ButtonColors(a = ButtonColor(Rgb(0x8C1D4E)), b = ButtonColor(Rgb(0x8C1D4E)), x = ButtonColor(Rgb(0x8C1D4E)), y = ButtonColor(Rgb(0x8C1D4E))),
    ),

    /** Background lightened from #9BBC0F (secondary text 11:1); accent darkened from #306230 to #2E5E2E (badge text 4.75:1, ring on the pill 3.98:1). */
    DotMatrix(
        BackgroundChoice.Custom(Rgb(0xB4DB11)),
        TextMode.Dark,
        AccentChoice.Custom(Rgb(0x2E5E2E)),
        previousAccents = listOf(Rgb(0x306130)), // earlier value, before the theme balance: ring on the pill and badge text
    ),

    /** Accent darkened from #C8302B to #AA2924 (badge text 4.82:1, ring on the pill 4.04:1); the A glyph keeps #C8302B. */
    Super(
        BackgroundChoice.Custom(Rgb(0xD9D8D3)),
        TextMode.Dark,
        AccentChoice.Custom(Rgb(0xAA2924)),
        ButtonColors(a = ButtonColor(Rgb(0xC8302B)), b = ButtonColor(Rgb(0xD9A400)), x = ButtonColor(Rgb(0x2B54B5)), y = ButtonColor(Rgb(0x2E8B4A))),
        previousAccents = listOf(Rgb(0xB22B26)), // earlier value, before the theme balance
    ),
    Indigo(BackgroundChoice.Custom(Rgb(0x262158)), TextMode.Light, AccentChoice.Custom(Rgb(0xA79CF5))),

    /** The accent #5FD38D is the Green preset's light-text colour, so it is stored as that preset. */
    Charcoal(
        BackgroundChoice.Custom(Rgb(0x2A2B2E)),
        TextMode.Light,
        AccentChoice.Preset(AccentPreset.Green),
        ButtonColors(a = ButtonColor(Rgb(0x3E6FD8)), b = ButtonColor(Rgb(0x2E9A47)), x = ButtonColor(Rgb(0xD9A400)), y = ButtonColor(Rgb(0xD9A400))),
    ),

    /**
     * Accent darkened from #1795B3 to #127087 (on the background 4.74:1, ring on the pill 3.88:1, badge text 4.74:1). Background warmed from #ECEAE4
     * and keys laid out as on the AYN Thor (A red, B yellow, X blue, Y green).
     */
    Ivory(
        BackgroundChoice.Custom(Rgb(0xF3EAD0)),
        TextMode.Dark,
        AccentChoice.Custom(Rgb(0x127087)),
        ButtonColors(a = ButtonColor(Rgb(0xCF3A3A)), b = ButtonColor(Rgb(0xD9A400)), x = ButtonColor(Rgb(0x2F6FDB)), y = ButtonColor(Rgb(0x2E9A47))),
        previousAccents = listOf(Rgb(0x1792B0)), // earlier value: its ring was 2.56:1 on the pill
    ),

    /** A red, B teal, X and Y a darker yellow than the background; every letter white. */
    Banana(
        BackgroundChoice.Custom(Rgb(0xF2CD3C)),
        TextMode.Dark,
        AccentChoice.Custom(Rgb(0x1F45A8)),
        ButtonColors(a = ButtonColor(Rgb(0xC8302B)), b = ButtonColor(Rgb(0x0E7C7C)), x = ButtonColor(Rgb(0x926B00)), y = ButtonColor(Rgb(0x926B00))),
    ),
    Cream(BackgroundChoice.Custom(Rgb(0xE9DFC8)), TextMode.Dark, AccentChoice.Custom(Rgb(0x9A1F2B))),
    ;

    val backgroundColor: Rgb get() = background.color

    val accentColor: Rgb get() = accent.resolve(textMode)

    /** True when [settings] draws exactly this set's three colours (font, size and the rest do not count). */
    fun isIn(settings: PlumThemeSettings): Boolean =
        // By system changes with the system in view, so it is no set's accent: the row reads Custom.
        settings.accent !is AccentChoice.BySystem && settings.backgroundColor == backgroundColor && settings.textColor == textMode.color && settings.accentColor == accentColor

    companion object {
        /** The set [settings] draws, or null ("Custom") when its colours match none. */
        fun matching(settings: PlumThemeSettings): ColorSet? = entries.firstOrNull { it.isIn(settings) }

        /**
         * [settings] with a set's previous accent ([ColorSet.previousAccents]) replaced by its current one, when the background and
         * text are that set's; anything else (a custom accent near the old one included) is returned as it is.
         */
        fun settled(settings: PlumThemeSettings): PlumThemeSettings {
            val accent = settings.accent as? AccentChoice.Custom ?: return settings
            val set =
                entries.firstOrNull {
                    accent.color in it.previousAccents && settings.backgroundColor == it.backgroundColor && settings.text == it.textMode.color
                } ?: return settings
            return settings.copy(accent = set.accent)
        }
    }
}

/** The four face buttons' glyph colours, by the letter printed on the button (not its position). */
data class ButtonColors(
    val a: ButtonColor,
    val b: ButtonColor,
    val x: ButtonColor,
    val y: ButtonColor,
) {
    /**
     * The colour of the key cap that reads [keyCap], or null for every other cap (START, SELECT,
     * L1 R1, arrows). By the printed letter: with "Confirm with B" the confirm hint reads "B" and
     * so takes B's colour.
     */
    fun forKeyCap(keyCap: String): ButtonColor? =
        when (keyCap) {
            "A" -> a
            "B" -> b
            "X" -> x
            "Y" -> y
            else -> null
        }
}

/**
 * A glyph's fill and the colour of the letter on it: white or the dark text colour, whichever
 * contrasts more with the fill (every fill in [ColorSet] gives at least 3:1, `ColorSetsTest`).
 */
data class ButtonColor(
    val fill: Rgb,
) {
    val letter: Rgb
        get() = if (ColorMath.contrast(WHITE, fill) >= ColorMath.contrast(TextMode.Dark.color, fill)) WHITE else TextMode.Dark.color

    private companion object {
        val WHITE = Rgb(0xFFFFFF)
    }
}
