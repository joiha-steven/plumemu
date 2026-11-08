package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** [Rgb] as an opaque Compose [Color]. */
fun Rgb.toComposeColor(): Color = Color(toArgb())

/**
 * The tokens of [PlumColors] as Compose colours, read in composables via `PlumTheme.colors`.
 *
 * Every colour is read from a [State] at the moment it is asked for, so one object serves a colour
 * that moves: a finger on a colour slider changes the State and only what reads a colour re-runs
 * (read it in a draw or layout lambda and that is a redraw, not a recomposition). A fixed colour set
 * ([from] with [PlumColors]) wraps constants. Two States, so that a move of one does not wake the
 * readers of the other: the tokens, and the accent (By system moves only the accent).
 */
@Stable
class PlumComposeColors private constructor(
    private val tokens: State<Tokens>,
    private val accentLook: State<AccentLook>,
) {
    val bg: Color get() = tokens.value.bg
    val text: Color get() = tokens.value.text
    val strong: Color get() = tokens.value.strong
    val sel: Color get() = tokens.value.sel
    val sel2: Color get() = tokens.value.sel2
    val text2: Color get() = tokens.value.text2

    /** The info line's subtitle under the covers ([PlumColors.infoMeta]). */
    val infoMeta: Color get() = tokens.value.infoMeta
    val meta: Color get() = tokens.value.meta
    val muted: Color get() = tokens.value.muted
    val chip: Color get() = tokens.value.chip

    /** Body text on a plain surface (the empty state's lines): the board's softer ink, between [text] and [text2] in strength ([Tokens.bodyInk]). */
    val body: Color get() = tokens.value.body

    /** The hint bar's labels: the board's cool grey on a dark theme (#9699A0 on Graphite), [body] on a light one (#4C4942 on Paper). */
    val hintLabel: Color get() = tokens.value.hintLabel

    /** The on-screen keyboard: tray, keys with a word or icon, top hairline (PlumColors). */
    val tray: Color get() = tokens.value.tray
    val trayKey: Color get() = tokens.value.trayKey
    val trayEdge: Color get() = tokens.value.trayEdge

    /** The flat face of a key, hint cap or dialog button ([FlatFace.key]). */
    val keyFace: FlatFace get() = tokens.value.keyFace

    /** The flat face of a disabled key, button or knob ([FlatFace.off]); its text is [muted]. */
    val keyOff: FlatFace get() = tokens.value.keyOff

    /** An error's colour (a field's groove, a Remove button's text): the board's coral on a dark theme, its brick red on a light one. */
    val danger: Color get() = tokens.value.danger

    /** The flat face of a part inside a collar ([FlatFace.collarCap]); `keyFace` stays for parts not yet in one. */
    val collarCap: FlatFace get() = tokens.value.collarCap

    /** The collar every touchable part sits in ([CollarLook]). */
    val collar: CollarLook get() = tokens.value.collar

    /** The flat face of a toggle or slider knob. */
    val knobFace: FlatFace get() = tokens.value.knobFace

    /** Black at the inset shadow's alpha: the 1 dp shadow of a sunken track or field. */
    val inset: Color get() = tokens.value.inset

    /** A panel's hairline edge (the text colour, faint) and soft shadow (black, lighter on a light theme): [PanelLook]. */
    val panelEdge: Color get() = tokens.value.panelEdge
    val panelShadow: Color get() = tokens.value.panelShadow

    /** The focused row's slab: every CSS layer ([FocusSlab]); the accent bar and the glow are drawn from [accent] and [slabGlow]. */
    val slab: SlabLook get() = tokens.value.slab

    /** The raised card of the game panel and its questions: fill, edge and shadow ([RaisedCard]). */
    val card: CardLook get() = tokens.value.card

    /** The glass tile of the second screen (gradient, edge, foot shade; [GlassTile]). */
    val glass: GlassLook get() = tokens.value.glass

    /** The rule between a card's parts and the stub of an empty column ([ToneLadder.line]): [ToneLadder.LINE] of L* off the page toward the text. */
    val line: Color get() = tokens.value.line

    /** The fill of a meter's track under its inset shadow ([MeterTrackToken]). */
    val meterTrack: Color get() = tokens.value.meterTrack

    /** A played column of the week chart ([PlayedBarToken], alpha included). */
    val playedBar: Color get() = tokens.value.playedBar

    private var accentMemo: FlatFace? = null
    private var knobOnMemo: FlatFace? = null

    /** The main button's face, in the [accentFill] now drawn (kept until the accent moves, so drawing allocates nothing). */
    fun accentFace(): FlatFace {
        val now = accentFill
        accentMemo?.let { if (it.face == now && it.downBottom == accent) return it }
        return FlatFace.accent(now, down = accent).also { accentMemo = it }
    }

    /** The knob of a switch that is on: `onAccent` (kept like [accentFace]). */
    fun knobOnFace(): FlatFace {
        val now = onAccent
        knobOnMemo?.let { if (it.face == now) return it }
        return FlatFace.knobOn(now).also { knobOnMemo = it }
    }

    /** The shadow the tray casts upwards: black at [PlumColors.trayShadowAlpha]. */
    val trayShadow: Color get() = tokens.value.trayShadow

    /** The grid cover's hairline ([PlumColors.coverEdge]). */
    val coverEdge: Color get() = tokens.value.coverEdge

    /** The accent. While By system it follows the system in view; while a slider moves it follows the preview: read it where it is drawn. */
    val accent: Color get() = accentLook.value.accent

    /**
     * The one flat colour of every accent face at rest (switch track on, primary button, slider fill, choice disc, accent keys, empty-state badge): the
     * mean of the board's accent-to-accentLight fade (accentLight is a quarter of the way to white, so the mean is [ACCENT_FILL_LIGHTEN] of the way;
     * the default blue gives the board's #568BE4). Text on it stays [onAccent]. Follows [accent]: read it where it is drawn.
     */
    val accentFill: Color
        get() {
            val a = accent
            // Per channel like the board; `lerp` blends in Oklab and lands a shade lighter (#578CE5).
            return Color(a.red + (1f - a.red) * ACCENT_FILL_LIGHTEN, a.green + (1f - a.green) * ACCENT_FILL_LIGHTEN, a.blue + (1f - a.blue) * ACCENT_FILL_LIGHTEN, a.alpha)
        }

    /** The kit boards' `accentLight`: each channel of [accent] [ACCENT_LIGHT_SHARE] of the way to white. Read it where it is drawn. */
    val accentLight: Color
        get() {
            val a = accent
            val t = ACCENT_LIGHT_SHARE
            return Color(a.red + (1f - a.red) * t, a.green + (1f - a.green) * t, a.blue + (1f - a.blue) * t, a.alpha)
        }

    /** True on a theme with light text on a dark page. */
    val isDark: Boolean get() = tokens.value.dark

    /** The accent glow in a corner of the second screen's page, alpha = its peak ([BackdropGlow]); follows [accent]. */
    val backdropGlow: Color get() = accentLook.value.backdropGlow

    /** The focused row's pill ([PlumColors.pill]); follows [accent]. */
    val pill: Color get() = accentLook.value.pill

    /** Text on an accent fill; follows [accent]. */
    val onAccent: Color get() = accentLook.value.onAccent

    /** A label on an accent cap ([PlumColors.onAccentFace]: white unless under 3:1); follows [accent]. */
    val onAccentFace: Color get() = accentLook.value.onAccentFace

    /** The focus halo outside a focus ring (see [FocusHalo]): the accent with its lightness clamped, alpha = its peak; transparent on a light theme. Follows [accent]. */
    val focusGlow: Color get() = accentLook.value.focusGlow

    /** The glow of a focused collar part (button, switch, slider, segment, swatch, choice, field, key): [focusGlow] on a dark theme, the accent at 30 % on a light one ([FocusHalo.partLook]). Follows [accent]. */
    val partGlow: Color get() = accentLook.value.partGlow

    /** The floor blob under a focused row's slab: transparent on every theme now (the slab's own glow, [slabGlow], is all there is). */
    val slabBlob: Color get() = accentLook.value.slabBlob

    /** The focused slab's glow (the boards' `0 0 glowBlur glowColor`): the accent at [FocusHalo.SLAB_GLOW] on every theme. */
    val slabGlow: Color get() = accentLook.value.slabGlow

    /** The colours of one [PlumColors], as Compose colours (the accent is in [AccentLook]). */
    @Immutable
    internal class Tokens(
        val bg: Color,
        val text: Color,
        val strong: Color,
        val sel: Color,
        val sel2: Color,
        val text2: Color,
        val infoMeta: Color,
        val meta: Color,
        val muted: Color,
        val chip: Color,
        val body: Color,
        val hintLabel: Color,
        val tray: Color,
        val trayKey: Color,
        val trayEdge: Color,
        val keyFace: FlatFace,
        val knobFace: FlatFace,
        val keyOff: FlatFace,
        val collar: CollarLook,
        val collarCap: FlatFace,
        val danger: Color,
        val inset: Color,
        val trayShadow: Color,
        val coverEdge: Color,
        val panelEdge: Color,
        val panelShadow: Color,
        val slab: SlabLook,
        val card: CardLook,
        val dark: Boolean = true,
        val glass: GlassLook,
        val line: Color,
        val meterTrack: Color,
        val playedBar: Color,
    ) {
        companion object {
            private val DANGER_ON_DARK = Color(0xFFFF7A6E)
            private val DANGER_ON_LIGHT = Color(0xFFC8372B)

            private fun darkTheme(tokens: PlumColors) = TextMode.of(tokens.text, tokens.bg) == TextMode.Light

            private fun keyColour(tokens: PlumColors) = ToneLadder.level(tokens.bg, ToneLadder.TOP).toComposeColor()

            /**
             * The board's body ink (Graphite #C6C2B9, Paper #4C4942): the text [BODY_DARK] / [BODY_LIGHT] of the way from the background, kept only
             * while it holds [BODY_MIN_CONTRAST] on the background; otherwise `text2` (which holds 11:1 wherever the text allows).
             */
            private fun bodyInk(
                tokens: PlumColors,
                dark: Boolean,
            ): Rgb {
                // A light theme's ink is warmed toward the board's brown, as its body text (#4C4942) is.
                val ink = if (dark) tokens.text else ColorMath.mix(tokens.text, CollarLook.SHADE_BROWN, BODY_WARM)
                val soft = ColorMath.mix(tokens.bg, ink, if (dark) BODY_DARK else BODY_LIGHT)
                return if (ColorMath.contrast(soft, tokens.bg) >= BODY_MIN_CONTRAST) soft else tokens.text2
            }

            /** The hint label: on a dark theme the text moved toward blue, [HINT_LABEL_DARK] of the way from the background (Graphite #9699A0), while it holds [BODY_MIN_CONTRAST]. */
            private fun hintLabelInk(
                tokens: PlumColors,
                dark: Boolean,
            ): Rgb {
                val body = bodyInk(tokens, dark)
                if (!dark) return body
                val ink = Rgb.fromChannels(tokens.text.red + HINT_SHIFT_RED, tokens.text.green + HINT_SHIFT_GREEN, tokens.text.blue + HINT_SHIFT_BLUE)
                val soft = ColorMath.mix(tokens.bg, ink, HINT_LABEL_DARK)
                return if (ColorMath.contrast(soft, tokens.bg) >= HINT_LABEL_MIN_CONTRAST) soft else body
            }

            private const val HINT_LABEL_DARK = 0.62
            private const val HINT_SHIFT_RED = -3.5
            private const val HINT_SHIFT_GREEN = 4.7
            private const val HINT_SHIFT_BLUE = 24.8
            private const val HINT_LABEL_MIN_CONTRAST = 4.6
            private const val BODY_DARK = 0.825
            private const val BODY_LIGHT = 0.80
            private const val BODY_WARM = 0.30
            private const val BODY_MIN_CONTRAST = 6.0

            fun of(tokens: PlumColors): Tokens {
                val dark = darkTheme(tokens)
                val keyFace = FlatFace.key(keyColour(tokens), dark)
                val collar = CollarLook.of(tokens.bg, tokens.text, dark)
                return Tokens(
                    bg = tokens.bg.toComposeColor(),
                    text = tokens.text.toComposeColor(),
                    strong = tokens.strong.toComposeColor(),
                    sel = tokens.sel.toComposeColor(),
                    sel2 = tokens.sel2.toComposeColor(),
                    text2 = tokens.text2.toComposeColor(),
                    infoMeta = tokens.infoMeta.toComposeColor(),
                    meta = tokens.meta.toComposeColor(),
                    muted = tokens.muted.toComposeColor(),
                    chip = tokens.chip.toComposeColor(),
                    body = bodyInk(tokens, dark).toComposeColor(),
                    hintLabel = hintLabelInk(tokens, dark).toComposeColor(),
                    tray = tokens.tray.toComposeColor(),
                    trayKey = tokens.trayKey.toComposeColor(),
                    trayEdge = tokens.trayEdge.toComposeColor(),
                    keyFace = keyFace,
                    knobFace = FlatFace.knob(),
                    collar = collar,
                    collarCap = FlatFace.collarCap(collar, dark),
                    danger = if (dark) DANGER_ON_DARK else DANGER_ON_LIGHT,
                    keyOff = darkTheme(tokens).let { dark -> FlatFace.off(collar.cap, collar.groove, dark) },
                    inset = Color.Black.copy(alpha = if (darkTheme(tokens)) INSET_ALPHA_DARK else INSET_ALPHA_LIGHT),
                    trayShadow = Color.Black.copy(alpha = tokens.trayShadowAlpha.toFloat()),
                    coverEdge = tokens.coverEdge.toComposeColor(),
                    panelEdge = tokens.text.toComposeColor().copy(alpha = PanelLook.edgeAlpha(darkTheme(tokens))),
                    panelShadow = Color.Black.copy(alpha = PanelLook.shadowAlpha(dark)),
                    slab = SlabLook.of(tokens.bg, tokens.text, dark),
                    card = CardLook.of(tokens.bg, tokens.text, dark),
                    dark = dark,
                    glass = GlassLook.of(tokens.bg, dark),
                    line = ToneLadder.line(tokens.bg, tokens.text).toComposeColor(),
                    meterTrack = MeterTrackToken.of(tokens.bg, dark),
                    playedBar = PlayedBarToken.of(tokens.bg, dark),
                )
            }
        }
    }

    private class Constant<T>(
        override val value: T,
    ) : State<T>

    companion object {
        /**
         * A fixed colour set. [liveAccent], when given, is the accent of the system in view (By
         * system): a [State] read by the accent getters, so a system move re-runs only what reads the
         * accent, not everything under the theme as a new colour set would.
         */
        fun from(
            tokens: PlumColors,
            liveAccent: State<AccentLook>? = null,
        ): PlumComposeColors = PlumComposeColors(Constant(Tokens.of(tokens)), liveAccent ?: Constant(AccentLook.of(tokens)))

        /**
         * A colour set that follows [tokens] and [accent] as they change (the colour preview of a
         * slider drag). The two are separate States so that a move of the accent alone (By system)
         * does not wake a reader of the other colours.
         */
        fun following(
            tokens: State<PlumColors>,
            accent: State<AccentLook>,
        ): PlumComposeColors = PlumComposeColors(derivedStateOf { Tokens.of(tokens.value) }, accent)
    }
}

/** The colours that follow the accent: the accent, the text on it, the focus halo and the focused row's pill. */
@Immutable
data class AccentLook(
    val accent: Color,
    val onAccent: Color,
    val focusGlow: Color,
    val pill: Color,
    val partGlow: Color,
    val slabBlob: Color = Color.Transparent,
    val slabGlow: Color = focusGlow,
    val onAccentFace: Color = onAccent,
    val backdropGlow: Color = Color.Transparent,
) {
    companion object {
        fun of(tokens: PlumColors): AccentLook {
            val accent = tokens.accent.toComposeColor()
            val halo = FocusHalo.look(tokens.accent, tokens.bg)?.let { it.colour.toComposeColor().copy(alpha = it.peak.toFloat()) } ?: Color.Transparent
            val part = FocusHalo.partLook(tokens.accent, tokens.bg).let { it.colour.toComposeColor().copy(alpha = it.peak.toFloat()) }
            // No floor blob on any theme: the slab's own glow only, a halo of a few dp as on a dark theme.
            val blob = Color.Transparent
            // The CSS glow is the accent itself at 0.3 on every theme (the boards' `0 0 22px rgba(accent, 0.3)`), not the halo's clamped colour.
            val slabGlow = accent.copy(alpha = FocusHalo.SLAB_GLOW.toFloat())
            val dark = TextMode.of(tokens.text, tokens.bg) == TextMode.Light
            val backdrop = accent.copy(alpha = if (dark) BackdropGlow.PEAK_DARK else BackdropGlow.PEAK_LIGHT)
            return AccentLook(accent, tokens.onAccent.toComposeColor(), halo, tokens.pill.toComposeColor(), part, blob, slabGlow, tokens.onAccentFace.toComposeColor(), backdrop)
        }
    }
}

/** What an alpha mask (`BlendMode.DstIn`) keeps whole; only its alpha counts, so it holds on every colour set. */
val MaskOpaque: Color = Color.Black

private const val ACCENT_FILL_LIGHTEN = 0.125f

/** How far `accentLight` is from the accent toward white (the kit boards' `lift`). */
internal const val ACCENT_LIGHT_SHARE = 0.3f
