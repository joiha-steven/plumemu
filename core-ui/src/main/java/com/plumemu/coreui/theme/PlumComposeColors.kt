package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color

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
    val meta: Color get() = tokens.value.meta
    val muted: Color get() = tokens.value.muted
    val chip: Color get() = tokens.value.chip

    /** The on-screen keyboard: tray, keys with a word or icon, top hairline (PlumColors). */
    val tray: Color get() = tokens.value.tray
    val trayKey: Color get() = tokens.value.trayKey
    val trayEdge: Color get() = tokens.value.trayEdge

    /** The shadow the tray casts upwards: black at [PlumColors.trayShadowAlpha]. */
    val trayShadow: Color get() = tokens.value.trayShadow

    /** The grid cover's hairline ([PlumColors.coverEdge]). */
    val coverEdge: Color get() = tokens.value.coverEdge

    /** The accent. While By system it follows the system in view; while a slider moves it follows the preview: read it where it is drawn. */
    val accent: Color get() = accentLook.value.accent

    /** The focused row's pill ([PlumColors.pill]); follows [accent]. */
    val pill: Color get() = accentLook.value.pill

    /** Text on an accent fill; follows [accent]. */
    val onAccent: Color get() = accentLook.value.onAccent

    /** The soft glow around a focus ring: accent at 25 % (the board's `accent + '40'`); follows [accent]. */
    val focusGlow: Color get() = accentLook.value.focusGlow

    /** The colours of one [PlumColors], as Compose colours (the accent is in [AccentLook]). */
    @Immutable
    internal class Tokens(
        val bg: Color,
        val text: Color,
        val strong: Color,
        val sel: Color,
        val sel2: Color,
        val text2: Color,
        val meta: Color,
        val muted: Color,
        val chip: Color,
        val tray: Color,
        val trayKey: Color,
        val trayEdge: Color,
        val trayShadow: Color,
        val coverEdge: Color,
    ) {
        companion object {
            fun of(tokens: PlumColors) =
                Tokens(
                    bg = tokens.bg.toComposeColor(),
                    text = tokens.text.toComposeColor(),
                    strong = tokens.strong.toComposeColor(),
                    sel = tokens.sel.toComposeColor(),
                    sel2 = tokens.sel2.toComposeColor(),
                    text2 = tokens.text2.toComposeColor(),
                    meta = tokens.meta.toComposeColor(),
                    muted = tokens.muted.toComposeColor(),
                    chip = tokens.chip.toComposeColor(),
                    tray = tokens.tray.toComposeColor(),
                    trayKey = tokens.trayKey.toComposeColor(),
                    trayEdge = tokens.trayEdge.toComposeColor(),
                    trayShadow = Color.Black.copy(alpha = tokens.trayShadowAlpha.toFloat()),
                    coverEdge = tokens.coverEdge.toComposeColor(),
                )
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

/** The colours that follow the accent: the accent, the text on it, the focus glow and the focused row's pill. */
@Immutable
data class AccentLook(
    val accent: Color,
    val onAccent: Color,
    val focusGlow: Color,
    val pill: Color,
) {
    companion object {
        // 0x40 / 0xFF, the alpha byte the board appends to the accent hex.
        private const val GLOW_ALPHA = 0x40 / 255f

        fun of(tokens: PlumColors): AccentLook {
            val accent = tokens.accent.toComposeColor()
            return AccentLook(accent, tokens.onAccent.toComposeColor(), accent.copy(alpha = GLOW_ALPHA), tokens.pill.toComposeColor())
        }
    }
}
