package com.plumemu.coreui.theme

/**
 * Every colour token, derived from the three colour settings by the rules in
 * `docs/design/rules.md` › Colour. Nothing is adjusted here; [Readability] settles a combination
 * that cannot be read before it gets this far.
 */
data class PlumColors(
    val bg: Rgb,
    val text: Rgb,
    /** Focused text: white with light text, the board's near-black #0F0F10 with dark text. */
    val strong: Rgb,
    /** Focused row fill. */
    val sel: Rgb,
    /** Small panels: the tone ladder's surface step ([ToneLadder.SURFACE]). */
    val sel2: Rgb,
    /** Secondary text, 11:1 on [bg]. */
    val text2: Rgb,
    /** The boards' `text2` (#C9C5BB on Graphite, #4A4740 on Paper): the info line's subtitle under the covers, kept readable on the page. */
    val infoMeta: Rgb,
    /** Facts and values on the focused row, 6:1 on [sel]. */
    val meta: Rgb,
    /** Small text, 4.6:1 on [sel] so it also holds on the focused row. */
    val muted: Rgb,
    /** Key-cap borders and the switch ring, 3:1 on [bg]. */
    val chip: Rgb,
    val accent: Rgb,
    /**
     * Text on an accent fill: [bg] or [text], whichever contrasts more with [accent]; when neither reaches 4.5:1 (the default
     * red on Midnight, Plum and Forest), pure black or white, whichever contrasts more. The accent itself is never moved.
     */
    val onAccent: Rgb,
    /**
     * A label on an accent cap (the primary button, the keyboard's action key, the confirm hint key, a selected choice's check): white, as on
     * the kit boards, while white reaches [ON_ACCENT_FACE_MIN] on the cap's middle (the accent [ACCENT_FACE_MIDDLE] toward white, between the
     * gradient's accentLight top and accent bottom, where the label sits); otherwise [onAccent]. The one exception to the 4.5:1 floor.
     */
    val onAccentFace: Rgb = onAccent,
    /**
     * The on-screen keyboard's tray: the tone ladder's surface step ([ToneLadder.SURFACE]), the page's own hue.
     */
    val tray: Rgb,
    /** Keyboard keys with a word or an icon on them (and the focused key): the tone ladder's top step ([ToneLadder.TOP]). */
    val trayKey: Rgb,
    /** The tray's 1 dp top hairline: 14 % towards the text. */
    val trayEdge: Rgb,
    /** Opacity of the black shadow the tray casts upwards: faint on light backgrounds, stronger on dark ones. */
    val trayShadowAlpha: Double,
    /**
     * The grid cover's 1 dp hairline, just inside its edge: the background moved 10 % towards the
     * text, so a few percent lighter than a dark background and darker than a light one. It keeps a
     * cover's black (or white) border from melting into the background and is barely seen
     * elsewhere (simple, minimal).
     */
    val coverEdge: Rgb,
    /**
     * The focused row's pill: [bg] moved [PILL_ALPHA] of the way to [accent], then eased back toward [bg] until [meta] and
     * [muted] still read on it (6:1 and 4.6:1), so the text on a focused row keeps its contrast whatever the accent is.
     * Pair it with a 2 dp [accent] ring (docs/design/rules.md > Focus and current).
     */
    val pill: Rgb,
) {
    companion object {
        /**
         * Derives the tokens. [accent] is returned unchanged, whatever it is; pass
         * [AccentPreset.colorFor] for a preset or the person's own colour.
         */
        fun derive(
            background: Rgb,
            textMode: TextMode,
            accent: Rgb = AccentPreset.Default.colorFor(textMode),
        ): PlumColors = derive(background, textMode.color, textMode, accent)

        /** Any text colour; the mode is [TextMode.of] it. */
        fun derive(
            background: Rgb,
            text: Rgb,
            accent: Rgb,
        ): PlumColors {
            val mode = TextMode.of(text, background)
            return derive(background, text, mode, accent)
        }

        private fun derive(
            background: Rgb,
            text: Rgb,
            textMode: TextMode,
            accent: Rgb,
        ): PlumColors {
            val isDarkText = textMode == TextMode.Dark
            // Small text also sits on the focused row's fill, so meta and muted are measured on sel.
            val sel = ColorMath.mix(background, text, if (isDarkText) 0.09 else 0.10)
            // Dark themes lift their keyboard surfaces toward the board's cooler ink, not the warm text (CollarLook.surfaceInk).
            val surface = if (isDarkText) text else CollarLook.surfaceInk(text)
            val meta = ColorMath.reach(background, text, sel, 6.0)
            val muted = ColorMath.reach(background, text, sel, 4.6)
            return PlumColors(
                bg = background,
                text = text,
                strong = if (isDarkText) STRONG_DARK else Rgb(0xFFFFFF),
                sel = sel,
                sel2 = ToneLadder.level(background, ToneLadder.SURFACE),
                text2 = ColorMath.reach(background, text, background, 11.0),
                infoMeta = infoMeta(background, text, muted, isDarkText),
                meta = meta,
                muted = muted,
                chip = ColorMath.reach(background, text, background, 3.0),
                accent = accent,
                onAccent = onAccent(background, text, accent),
                onAccentFace = onAccentFace(accent, onAccent(background, text, accent)),
                tray = ToneLadder.level(background, ToneLadder.SURFACE),
                trayKey = ToneLadder.level(background, ToneLadder.TOP),
                trayEdge = ColorMath.mix(background, surface, if (isDarkText) TRAY_EDGE else TRAY_EDGE_DARK_THEME),
                trayShadowAlpha = if (isDarkText) TRAY_SHADOW_DARK_TEXT else TRAY_SHADOW_LIGHT_TEXT,
                coverEdge = ColorMath.mix(background, text, COVER_EDGE),
                pill = focusPill(background, accent, meta, muted),
            )
        }

        private fun onAccent(
            background: Rgb,
            text: Rgb,
            accent: Rgb,
        ): Rgb {
            val usual = if (ColorMath.contrast(background, accent) >= ColorMath.contrast(text, accent)) background else text
            if (ColorMath.contrast(usual, accent) >= ON_ACCENT_MIN) return usual
            return if (ColorMath.contrast(BLACK, accent) >= ColorMath.contrast(WHITE, accent)) BLACK else WHITE
        }

        /** See [PlumColors.onAccentFace]. */
        fun onAccentFace(
            accent: Rgb,
            onAccent: Rgb,
        ): Rgb = if (ColorMath.contrast(WHITE, ColorMath.mix(accent, WHITE, ACCENT_FACE_MIDDLE)) >= ON_ACCENT_FACE_MIN) WHITE else onAccent

        /** The boards' `text2`: the page moved [INFO_META_DARK] / [INFO_META_LIGHT] toward the text; [muted] where a custom palette leaves it under 4.5:1. */
        fun infoMeta(
            background: Rgb,
            text: Rgb,
            muted: Rgb,
            isDarkText: Boolean,
        ): Rgb {
            val ink = ColorMath.mix(background, text, if (isDarkText) INFO_META_LIGHT else INFO_META_DARK)
            return if (ColorMath.contrast(ink, background) >= INFO_META_MIN) ink else muted
        }

        private fun focusPill(
            background: Rgb,
            accent: Rgb,
            meta: Rgb,
            muted: Rgb,
        ): Rgb {
            var alpha = PILL_ALPHA
            while (alpha > 0.0) {
                val pill = ColorMath.mix(background, accent, alpha)
                if (ColorMath.contrast(meta, pill) >= 6.0 && ColorMath.contrast(muted, pill) >= 4.6) return pill
                alpha -= PILL_STEP
            }
            return background
        }

        /** How far the focused row's pill is tinted toward the accent (about 16 %). */
        const val PILL_ALPHA = 0.16
        private const val PILL_STEP = 0.01
        private const val ON_ACCENT_MIN = 4.5

        /** The boards' `text2` as a share of the way from the page to the text: #C9C5BB on Graphite, #4A4740 on Paper. */
        const val INFO_META_DARK = 0.84
        const val INFO_META_LIGHT = 0.80
        const val INFO_META_MIN = 4.5

        /** White on an accent cap needs this much on the cap's middle (large, bold labels on a lit face). */
        const val ON_ACCENT_FACE_MIN = 3.0

        /** The accent cap's middle: halfway along its gradient from the accent to accentLight (30 % toward white). */
        const val ACCENT_FACE_MIDDLE = 0.15
        private val BLACK = Rgb(0x000000)
        private val STRONG_DARK = Rgb(0x0F0F10)
        private val WHITE = Rgb(0xFFFFFF)

        private const val TRAY_EDGE = 0.14

        /** On a dark theme the board's tray hairline is #333436 over #111214. */
        private const val TRAY_EDGE_DARK_THEME = 0.155
        private const val COVER_EDGE = 0.10

        /** The board's `rgba(0,0,0,0.07)` with dark text (light backgrounds), 0.45 with light text. */
        private const val TRAY_SHADOW_DARK_TEXT = 0.07 * Depth.SHADOW
        private const val TRAY_SHADOW_LIGHT_TEXT = 0.45 * Depth.SHADOW
    }
}
