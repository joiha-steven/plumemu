package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/**
 * A key's flat face (decision 0011, point 2): one solid [face], a [rim] (the face with a hairline of light, drawn
 * 1 dp above it on the top edge), an [edge] 2 dp under it and a [shadow] (black, alpha = its peak). All opaque but the
 * shadow, so drawing is plain filled shapes.
 */
class FlatFace(
    val face: Color,
    val rim: Color,
    val edge: Color,
    val shadow: Color,
    /** [face] and [rim] a touch lighter, for a raised (focused or hovered) key. */
    val faceUp: Color = lerp(face, Color.White, RAISE_LIGHT),
    val rimUp: Color = lerp(rim, Color.White, RAISE_LIGHT),
    /** The face's top tone, when it fades from [faceTop] (top) to [face] (bottom) in two plain shapes; equal to [face] for a one-colour face. */
    val faceTop: Color = face,
    val faceTopUp: Color = lerp(faceTop, Color.White, RAISE_LIGHT),
    /** Pressed (collar parts): the top and bottom tones, the top highlight gone and a dark band of alpha [innerShade] along the top. 0 keeps the plain press. */
    val downTop: Color = faceTop,
    val downBottom: Color = face,
    val innerShade: Float = 0f,
) {
    /** [tone] with the inner top shadow at [amount] (0..1) of its alpha. */
    fun shaded(
        tone: Color,
        amount: Float,
    ): Color = Color.Black.copy(alpha = innerShade * amount).compositeOver(tone)

    companion object {
        /** [face] with an edge [edgeDark] of the way to black and a rim [rimLight] of the way to white. */
        fun of(
            face: Color,
            edgeDark: Float,
            rimLight: Float,
            shadowAlpha: Float,
        ) = FlatFace(
            face = face,
            rim = lerp(face, Color.White, rimLight),
            edge = lerp(face, Color.Black, edgeDark),
            shadow = Color.Black.copy(alpha = shadowAlpha),
        )

        /** The face of a key or button: [face] (the `key` colour) on a dark theme ([darkTheme]) or a light one. */
        fun key(
            face: Color,
            darkTheme: Boolean,
        ) = if (darkTheme) of(face, 0.85f, 0.16f, 0.30f) else of(face, 0.23f, 0.70f, 0.18f)

        /**
         * A disabled part: flat and sunken, no edge, rim or shadow, its [face] [OFF_DARK] / [OFF_LIGHT] of the way
         * from the key face to the collar's [groove] (board: Graphite #232529 to #18191C, Paper #EFEBE3 to #E6E1D7). Draw it
         * with `press = 1` so it sits down; its text is the `muted` colour, never an opacity.
         */
        fun off(
            keyFace: Color,
            groove: Color,
            darkTheme: Boolean,
        ): FlatFace {
            val face = lerp(keyFace, groove, if (darkTheme) OFF_DARK else OFF_LIGHT)
            return FlatFace(face = face, rim = face, edge = face, shadow = Color.Transparent, innerShade = OFF_SHADE)
        }

        /**
         * The face of a part inside a collar: [CollarLook.cap] (top) fading to [CollarLook.capBottom], a hairline of light on top and an
         * edge under it (board: Graphite #2A2C31 to #232529 over #121316; Paper #F7F4EE to #EFEBE3 over #D3CBBC).
         */
        /** The cap's shadow on a light theme: the board's brown, so light that it only tints the groove ring beside the cap (Paper #CCC4B8 to the board's #C4BBAF). */
        const val LIGHT_CAP_SHADOW = 0.058f

        fun collarCap(
            look: CollarLook,
            darkTheme: Boolean,
        ) = FlatFace(
            face = look.capBottom,
            rim = lerp(look.cap, Color.White, if (darkTheme) 0.16f else 1f),
            edge = lerp(look.capBottom, Color.Black, if (darkTheme) 0.50f else 0.12f),
            shadow = if (darkTheme) Color.Black.copy(alpha = 0.30f) else CollarLook.SHADE_BROWN.toComposeColor().copy(alpha = LIGHT_CAP_SHADOW),
            faceTop = look.cap,
            downTop = lerp(look.cap, Color.Black, if (darkTheme) 0.21f else 0.05f),
            downBottom = lerp(look.capBottom, Color.Black, if (darkTheme) 0.13f else 0f),
            innerShade = 0.22f,
        )

        /** The main button's face inside a collar: [accent] fading up to a lighter top ([ACCENT_TOP] of the way to white), an edge at 0.62 of the accent. */
        fun collarAccent(accent: Color): FlatFace {
            val top = lerp(accent, Color.White, ACCENT_TOP)
            return FlatFace(face = accent, rim = lerp(top, Color.White, 0.40f), edge = lerp(accent, Color.Black, 0.38f), shadow = Color.Black.copy(alpha = 0.30f), faceTop = top, downTop = accent, downBottom = accent, innerShade = 0.30f)
        }

        /** The main button's face: [accent] (the flat accent fill), a darker edge, a lighter rim; pressed it sinks to [down], the plain accent on the board. */
        fun accent(
            accent: Color,
            down: Color = accent,
        ) = of(accent, 0.50f, 0.30f, 0.28f).let {
            FlatFace(it.face, it.rim, it.edge, it.shadow, downTop = Color(down.red * ACCENT_DOWN_TOP, down.green * ACCENT_DOWN_TOP, down.blue * ACCENT_DOWN_TOP), downBottom = down, innerShade = ACCENT_SHADE)
        }

        /** The toggle and slider knob: a pale face on any theme. */
        fun knob() = of(KNOB_FACE, 0.18f, 0f, 0.35f)

        /** The knob of a switch that is on: [onAccent], so it reads on the accent track whatever the accent is. */
        fun knobOn(onAccent: Color) = of(onAccent, 0.18f, 0f, 0.35f)

        /** The key colour is this share of the way from the background to the text (dark theme, light theme). */
        const val KEY_DARK = 0.17
        const val KEY_LIGHT = 0.11
        const val RAISE_LIGHT = 0.06f
        const val ACCENT_TOP = 0.30f
        const val OFF_SHADE = 0.18f

        /** A pressed accent face: the top is the accent's channels times this (board #3A74D3 on #3E7BE0), under a faint inner top shade. */
        const val ACCENT_DOWN_TOP = 0.942f
        const val ACCENT_SHADE = 0.12f
        const val OFF_DARK = 0.55f
        const val OFF_LIGHT = 0.40f
        private val KNOB_FACE = Color(0xFFFAFAF8)
    }
}

/** A sunken track's top crescent: [inset] (black at its alpha) over [fill]. */
fun sunkenRim(
    fill: Color,
    inset: Color,
): Color = inset.compositeOver(fill)

/** The inset shadow's alpha on a dark theme and on a light one. */
const val INSET_ALPHA_DARK = 0.55f
const val INSET_ALPHA_LIGHT = 0.28f
