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
) {
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

        /** The main button's face: the accent, a darker edge, a lighter rim. */
        fun accent(accent: Color) = of(accent, 0.50f, 0.30f, 0.28f)

        /** The toggle and slider knob: a pale face on any theme. */
        fun knob() = of(KNOB_FACE, 0.18f, 0f, 0.35f)

        /** The knob of a switch that is on: [onAccent], so it reads on the accent track whatever the accent is. */
        fun knobOn(onAccent: Color) = of(onAccent, 0.18f, 0f, 0.35f)

        /** The key colour is this share of the way from the background to the text (dark theme, light theme). */
        const val KEY_DARK = 0.17
        const val KEY_LIGHT = 0.11
        const val RAISE_LIGHT = 0.06f
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
