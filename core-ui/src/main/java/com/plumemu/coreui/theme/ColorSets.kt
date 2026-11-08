package com.plumemu.coreui.theme

/**
 * Settings › Appearance › Colors: ready-made choices of the three colour settings,
 * curated tones and colours of classic consoles and handhelds, never named after one. A set is only a background, a text mode
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
    /**
     * Backgrounds this set had before its current one, read like [previousAccents] (text stays the set's, and the stored text
     * presets are names, so a moved text colour needs no entry). Add the old value here whenever a set's background changes.
     */
    val previousBackgrounds: List<Rgb> = emptyList(),
    /**
     * Whole looks (background and accent) of sets that were taken out and now read as this one: settings holding exactly one, with this
     * set's text, are drawn as this set ([settled]); a removed set's name stored for its buttons resolves here too ([named]).
     */
    val formerLooks: List<FormerLook> = emptyList(),
    /** Other pages this set may stand on and still be this set (with its text and accent): drawn as stored, never moved. */
    val pageVariants: List<Rgb> = emptyList(),
) {
    /** The default look exactly (`PlumThemeSettings.Default`), so a fresh install shows it: a deep blue-black ink, not pure black. */
    Plumemu(
        BackgroundChoice.Custom(Rgb(0x161A24)),
        TextMode.Light,
        AccentChoice.Custom(Rgb(0x3A7CEB)),
        // The default on Black until the deep ink, and its own-colour red before the blue (a Red preset on Black stays as it is).
        formerLooks = listOf(FormerLook(Rgb(0x000000), Rgb(0x3E7BE0)), FormerLook(Rgb(0x000000), Rgb(0xDA4655), ownAccentOnly = true)),
        // True black: what setup gives an OLED handheld under this set's accent and tones.
        pageVariants = listOf(BackgroundPreset.Black.color),
    ),

    /** The light default: a clean grey-white, held below white so the focus slab's white rim still shows. */
    Daylight(
        BackgroundChoice.Custom(Rgb(0xECEBE5)),
        TextMode.Dark,
        AccentChoice.Custom(Rgb(0x2A64D8)),
        // The Paper set is not taken over: it was two presets (Paper, Red) anyone can pick by hand, and both stay.
    ),
    Ultramarine(BackgroundChoice.Custom(Rgb(0x0F2142)), TextMode.Light, AccentChoice.Custom(Rgb(0xF4B740))),
    Magenta(BackgroundChoice.Custom(Rgb(0x240F1C)), TextMode.Light, AccentChoice.Custom(Rgb(0xF0649D))),
    Peach(BackgroundChoice.Custom(Rgb(0xFDE3D3)), TextMode.Dark, AccentChoice.Custom(Rgb(0x00706B))),
    Matcha(BackgroundChoice.Custom(Rgb(0xE4ECD1)), TextMode.Dark, AccentChoice.Custom(Rgb(0x3D6E1C))),
    Mocha(BackgroundChoice.Custom(Rgb(0xF1E6DA)), TextMode.Dark, AccentChoice.Custom(Rgb(0xA4492A))),

    /** The grey 8-bit home console: its dark band and red letters. */
    FrontLoader(
        BackgroundChoice.Custom(Rgb(0x26272B)),
        TextMode.Light,
        AccentChoice.Custom(Rgb(0xF2545D)),
        ButtonColors(a = ButtonColor(Rgb(0xD22A35)), b = ButtonColor(Rgb(0xD22A35)), x = ButtonColor(Rgb(0x3A3B40)), y = ButtonColor(Rgb(0x3A3B40))),
    ),

    /** The indigo cube console: A green, B red, X and Y silver. */
    SpiceCube(
        BackgroundChoice.Custom(Rgb(0x231C50)),
        TextMode.Light,
        AccentChoice.Custom(Rgb(0xFF9F43)),
        ButtonColors(a = ButtonColor(Rgb(0x2EA557)), b = ButtonColor(Rgb(0xD93A3E)), x = ButtonColor(Rgb(0xBDBCC8)), y = ButtonColor(Rgb(0xBDBCC8))),
        formerLooks = listOf(FormerLook(Rgb(0x262158), Rgb(0xA79CF5))), // the Indigo set
    ),

    /** The first pocket handheld's screen: pea green with its darkest green. */
    PeaGreen(
        BackgroundChoice.Custom(Rgb(0xC6D88F)),
        TextMode.Dark,
        AccentChoice.Custom(Rgb(0x2F5F2F)),
        // The Dot matrix set, with the values it had before the theme balance and the darker text.
        formerLooks =
            listOf(Rgb(0xB8E011), Rgb(0xB4DB11)).flatMap { bg -> listOf(Rgb(0x2E5E2E), Rgb(0x306130)).map { FormerLook(bg, it) } },
    ),

    /** Background lightened from #C8C6BE so secondary text reaches 11:1 (the dark text is #1C1C1E). */
    Pocket(
        BackgroundChoice.Custom(Rgb(0xD2D1CA)),
        TextMode.Dark,
        AccentChoice.Custom(Rgb(0x8C1D4E)),
        ButtonColors(a = ButtonColor(Rgb(0x8C1D4E)), b = ButtonColor(Rgb(0x8C1D4E)), x = ButtonColor(Rgb(0x8C1D4E)), y = ButtonColor(Rgb(0x8C1D4E))),
        previousBackgrounds = listOf(Rgb(0xCDCCC4)), // before the dark text became #1C1C1E
    ),

    /** The 16-bit console's purple and lavender buttons. */
    Lavender(
        BackgroundChoice.Custom(Rgb(0xDEDCE6)),
        TextMode.Dark,
        AccentChoice.Custom(Rgb(0x5B3EA1)),
        ButtonColors(a = ButtonColor(Rgb(0x4E3591)), b = ButtonColor(Rgb(0x4E3591)), x = ButtonColor(Rgb(0xB2A5DB)), y = ButtonColor(Rgb(0xB2A5DB))),
    ),

    /** The Japanese 8-bit console: cream, deep red and gold. */
    RedAndGold(
        BackgroundChoice.Custom(Rgb(0xF3EBD8)),
        TextMode.Dark,
        AccentChoice.Custom(Rgb(0x9A1B2F)),
        ButtonColors(a = ButtonColor(Rgb(0x9A1B2F)), b = ButtonColor(Rgb(0x9A1B2F)), x = ButtonColor(Rgb(0xC69A1E)), y = ButtonColor(Rgb(0xC69A1E))),
        formerLooks = listOf(FormerLook(Rgb(0xE9DFC8), Rgb(0x9A1F2B))), // the Cream set
    ),

    /**
     * Accent darkened from #1795B3 to #127087 (on the background 4.74:1, ring on the pill 3.88:1, badge text 4.74:1). Background warmed from #ECEAE4
     * and keys laid out as on a dual-screen handheld (A red, B yellow, X blue, Y green).
     */
    Ivory(
        BackgroundChoice.Custom(Rgb(0xF3EAD0)),
        TextMode.Dark,
        AccentChoice.Custom(Rgb(0x127087)),
        ButtonColors(a = ButtonColor(Rgb(0xCF3A3A)), b = ButtonColor(Rgb(0xD9A400)), x = ButtonColor(Rgb(0x2F6FDB)), y = ButtonColor(Rgb(0x2E9A47))),
        previousAccents = listOf(Rgb(0x1792B0)), // earlier value: its ring was 2.56:1 on the pill
        formerLooks = listOf(FormerLook(Rgb(0xD9D8D3), Rgb(0xAA2924)), FormerLook(Rgb(0xD9D8D3), Rgb(0xB22B26))), // the Super set
    ),

    /** Clear purple shells. */
    ClearGrape(BackgroundChoice.Custom(Rgb(0x221838)), TextMode.Light, AccentChoice.Custom(Rgb(0xBE98FF))),

    /** The cold grey of a larger Android handheld, with ice blue. */
    ColdGrey(
        BackgroundChoice.Custom(Rgb(0x272B32)),
        TextMode.Light,
        AccentChoice.Custom(Rgb(0x8EC3FF)),
        ButtonColors(a = ButtonColor(Rgb(0x3E6FD8)), b = ButtonColor(Rgb(0x2E9A47)), x = ButtonColor(Rgb(0xD9A400)), y = ButtonColor(Rgb(0xD9A400))),
        formerLooks = listOf(FormerLook(Rgb(0x2A2B2E), AccentPreset.Green.colorFor(TextMode.Light))), // the Charcoal set
    ),

    /** A red, B teal, X and Y a darker yellow than the background; every letter white. */
    Banana(
        BackgroundChoice.Custom(Rgb(0xF2CF43)),
        TextMode.Dark,
        AccentChoice.Custom(Rgb(0x1F45A8)),
        ButtonColors(a = ButtonColor(Rgb(0xC8302B)), b = ButtonColor(Rgb(0x0E7C7C)), x = ButtonColor(Rgb(0x926B00)), y = ButtonColor(Rgb(0x926B00))),
        previousBackgrounds = listOf(Rgb(0xF2CD3C)), // before the dark text became #1C1C1E
    ),
    ;

    val backgroundColor: Rgb get() = background.color

    val accentColor: Rgb get() = accent.resolve(textMode)

    /** True when [settings] draws exactly this set's three colours, its page or one of its [pageVariants] (font, size and the rest do not count). */
    fun isIn(settings: PlumThemeSettings): Boolean =
        // By system changes with the system in view, so it is no set's accent: the row reads Custom.
        settings.accent !is AccentChoice.BySystem &&
            (settings.backgroundColor == backgroundColor || settings.backgroundColor in pageVariants) &&
            settings.textColor == textMode.color && settings.accentColor == accentColor

    companion object {
        /** The sets taken out, by their stored names, and the set each now reads as. */
        private val REMOVED =
            mapOf(
                "DotMatrix" to PeaGreen,
                "Super" to Ivory,
                "Indigo" to SpiceCube,
                "Charcoal" to ColdGrey,
                "Cream" to RedAndGold,
            )

        /** The set stored as [name] (a button set's name in settings or a backup), a removed set's name giving the set that replaced it; null for anything else. */
        fun named(name: String): ColorSet? = entries.firstOrNull { it.name == name } ?: REMOVED[name]

        /** The set [settings] draws, or null ("Custom") when its colours match none. */
        fun matching(settings: PlumThemeSettings): ColorSet? = entries.firstOrNull { it.isIn(settings) }

        /**
         * [settings] with a set's previous background or accent ([ColorSet.previousBackgrounds], [ColorSet.previousAccents]) replaced by
         * its current one, when the rest is that set's; anything else (a custom colour near an old one included) is returned as it is.
         */
        fun settled(settings: PlumThemeSettings): PlumThemeSettings {
            if (settings.accent is AccentChoice.BySystem) return settings
            for (set in entries) {
                if (settings.text != set.textMode.color) continue
                if (set.formerLooks.any { it.matches(settings, set.textMode) }) {
                    return settings.copy(background = set.background, accent = set.accent)
                }
                val oldBackground = settings.background is BackgroundChoice.Custom && settings.backgroundColor in set.previousBackgrounds
                val oldAccent = (settings.accent as? AccentChoice.Custom)?.color in set.previousAccents
                if (!oldBackground && !oldAccent) continue
                val backgroundOk = oldBackground || settings.backgroundColor == set.backgroundColor
                val accentOk = oldAccent || settings.accent.resolve(set.textMode) == set.accentColor
                if (!backgroundOk || !accentOk) continue
                return settings.copy(
                    background = if (oldBackground) set.background else settings.background,
                    accent = if (oldAccent) set.accent else settings.accent,
                )
            }
            return settings
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

/** A removed set's background and accent, exactly as they were drawn; [ownAccentOnly]: only an own-colour accent of that value, not a preset's. */
data class FormerLook(
    val background: Rgb,
    val accent: Rgb,
    val ownAccentOnly: Boolean = false,
) {
    fun matches(
        settings: PlumThemeSettings,
        textMode: TextMode,
    ): Boolean =
        settings.backgroundColor == background &&
            settings.accent.resolve(textMode) == accent &&
            (!ownAccentOnly || settings.accent is AccentChoice.Custom)
}
