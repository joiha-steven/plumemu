package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The colours of the collar every touchable part sits in (docs/design/rules.md › Collar): the soft
 * rim of the surface's own colour ([lipTop] the hairline of light along its top edge, [lipMid] its body,
 * [lipBottom] the darker band under the part), the fine [groove] inside it, and the [shadow] (black, alpha =
 * its peak) it throws below. All opaque but the shadow, so drawing is plain filled shapes.
 */
class CollarLook(
    val lipTop: Color,
    val lipMid: Color,
    val lipBottom: Color,
    /** The sunken track tone (slider track, switch off track). */
    val groove: Color,
    /** The part's own face inside the collar: the key colour on a dark theme, a step toward white on a light one (lighter than the lip). */
    val cap: Color,
    /** The cap's bottom tone (the cap fades from [cap] at the top to this). */
    val capBottom: Color,
    val shadow: Color,
    /** The bright hairline at the very top of the lip (the board's inset white at .16 over the top tone); [lipTop] on a light theme, which has no room above white. */
    val lipCrest: Color = lipTop,
    /** Black at the alpha that darkens the groove along its top edge (its inset shadow); composited over the groove's colour. */
    val grooveShade: Color = Color.Black.copy(alpha = 0.3f),
    /** A shallow well, such as the empty-state disc: the surface moved a little toward black (dark theme) or the board's brown shade (light). */
    val well: Color = groove,
    /** The label of a disabled part (the board's #6F737B on Graphite, #9A958B on Paper): dimmer than `muted`, which stays for small text that must be read. */
    val offText: Color = groove,
    /** True: the drop shadow is the soft Gaussian one (a light theme); false: three stacked layers growing out. */
    val softShadow: Boolean = false,
    /** The groove ring between lip and part at rest (the board's #CCC4B8 on Paper, a step darker than the [groove] track); the same as [groove] on a dark theme. */
    val ring: Color = groove,
    /** The kit boards' seated parts layer by layer ([SeatLook]): lip, well, caps. */
    val seat: SeatLook = SeatLook.GRAPHITE,
) {
    companion object {
        /**
         * Derived from the background and the text, like [FlatFace.key], so every colour set and custom colour has one.
         * Each share is how far the lip colour is from [bg] toward the text, white or black; they were chosen to land
         * on the approved board's values:
         *  - Graphite (bg #111214, text light): top 0.21 to the text (board #3C3F45) with a crest of .16 white over it (board #5D5F63), mid 0.10 (#26282D), bottom 0.03 (#17181B), cap 0.12 / 0.096 to the text (#2A2C31 / the bottom #232529 and the mid #27292D meet at #25272B),
         *    groove 0.12 to black (#0F1012).
         *  - Paper (bg #ECE8DF, text dark): top all the way to white (#FFFFFF), mid 0.26 to white (#F1EDE6), bottom 0.04
         *    to the text (#E4DED3), groove 0.13 to the brown shade (#D7D0C3), ring 0.185 (#CCC4B8), cap 0.58 / 0.16 to white (#F7F4EE / #EFEBE3).
         * On a light theme a background that is already white has no room above it, so there the lip reads by its
         * darker bottom band and the groove (`CollarLookTest` holds the distance).
         */
        internal fun of(
            bg: Rgb,
            text: Rgb,
            dark: Boolean,
        ): CollarLook {
            val white = Rgb(0xFFFFFF)
            val black = Rgb(0x000000)
            val ink = surfaceInk(text)
            return if (dark) {
                CollarLook(
                    lipTop = ColorMath.mix(bg, ink, DARK_TOP).toComposeColor(),
                    lipMid = ColorMath.mix(bg, ink, DARK_MID).toComposeColor(),
                    lipBottom = ColorMath.mix(bg, ink, DARK_BOTTOM).toComposeColor(),
                    groove = ColorMath.mix(bg, black, DARK_GROOVE).toComposeColor(),
                    cap = ColorMath.mix(bg, ink, DARK_CAP).toComposeColor(),
                    capBottom = ColorMath.mix(bg, ink, DARK_CAP_BOTTOM).toComposeColor(),
                    shadow = Color.Black.copy(alpha = DARK_SHADOW),
                    lipCrest = lerp(ColorMath.mix(bg, ink, DARK_TOP).toComposeColor(), Color.White, DARK_CREST),
                    grooveShade = Color.Black.copy(alpha = DARK_GROOVE_INSET),
                    well = ColorMath.mix(bg, black, DARK_WELL).toComposeColor(),
                    offText = ColorMath.mix(bg, ink, DARK_OFF_TEXT).toComposeColor(),
                    seat = SeatLook.of(bg, text, true),
                )
            } else {
                CollarLook(
                    lipTop = ColorMath.mix(bg, white, LIGHT_TOP).toComposeColor(),
                    lipMid = ColorMath.mix(bg, white, LIGHT_MID).toComposeColor(),
                    lipBottom = ColorMath.mix(bg, text, LIGHT_BOTTOM).toComposeColor(),
                    groove = ColorMath.mix(bg, SHADE_BROWN, LIGHT_GROOVE).toComposeColor(),
                    ring = ColorMath.mix(bg, SHADE_BROWN, LIGHT_RING).toComposeColor(),
                    cap = ColorMath.mix(bg, white, LIGHT_CAP).toComposeColor(),
                    capBottom = ColorMath.mix(bg, white, LIGHT_CAP_BOTTOM).toComposeColor(),
                    shadow = SHADE_BROWN.toComposeColor().copy(alpha = LIGHT_SHADOW),
                    softShadow = true,
                    grooveShade = Color.Black.copy(alpha = LIGHT_GROOVE_INSET),
                    well = ColorMath.mix(bg, SHADE_BROWN, LIGHT_WELL).toComposeColor(),
                    offText = ColorMath.mix(bg, ColorMath.mix(text, SHADE_BROWN, 0.5), LIGHT_OFF_TEXT).toComposeColor(),
                    seat = SeatLook.of(bg, text, false),
                )
            }
        }

        /**
         * The colour a dark theme's surfaces (lips, caps, keys, tray) are mixed toward: the text moved toward blue, as the board's
         * surfaces are cooler than its warm text (Graphite caps #2A2C31 over #111214 where text-mixing gives #2B2C2C). Light themes mix toward white.
         */
        internal fun surfaceInk(text: Rgb): Rgb = Rgb.fromChannels(text.red + INK_SHIFT_RED, text.green + INK_SHIFT_GREEN, text.blue + INK_SHIFT_BLUE)

        private const val INK_SHIFT_RED = -9.0
        private const val INK_SHIFT_GREEN = 6.0
        private const val INK_SHIFT_BLUE = 34.0

        /** The colour the board's light-theme shadows and wells darken toward (its Paper shadow colour). */
        internal val SHADE_BROWN = Rgb(0x3C2814)

        const val DARK_OFF_TEXT = 0.443
        const val LIGHT_OFF_TEXT = 0.424
        const val DARK_WELL = 0.41
        const val LIGHT_WELL = 0.0715
        const val DARK_CREST = 0.16f * Depth.RAISE
        const val DARK_GROOVE_INSET = 0.45f * Depth.RAISE
        const val LIGHT_GROOVE_INSET = 0.13f * Depth.RAISE
        const val DARK_TOP = 0.21 * Depth.RAISE
        const val DARK_MID = 0.072 * Depth.RAISE
        const val DARK_BOTTOM = 0.03
        const val DARK_GROOVE = 0.30 * Depth.RAISE
        const val DARK_SHADOW = 0.45f * Depth.SHADOW
        const val LIGHT_CAP = 0.58
        const val LIGHT_CAP_BOTTOM = 0.16
        const val DARK_CAP = 0.12
        const val DARK_CAP_BOTTOM = 0.096
        const val LIGHT_TOP = 1.0 * Depth.RAISE
        const val LIGHT_MID = 0.26 * Depth.RAISE
        const val LIGHT_BOTTOM = 0.04
        const val LIGHT_GROOVE = 0.13 * Depth.RAISE
        const val LIGHT_RING = 0.185 * Depth.RAISE
        const val LIGHT_SHADOW = 0.10f * Depth.SHADOW
    }
}
