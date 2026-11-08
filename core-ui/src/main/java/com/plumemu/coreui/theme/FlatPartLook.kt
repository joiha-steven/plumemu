package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

/**
 * The flat look of a button and a switch (docs/design/rules.md > Flat parts): solid fills from the tone ladder ([ToneLadder]), a hairline
 * edge, no gradient, no highlight, no thickness, no lip. Every value derives from the page and the text, so each colour set and custom
 * colour gets its own; the accent parts ([primary]) derive from the accent. Read while drawing.
 *
 * Secondary cap: [ToneLadder.TOP] with a [ToneLadder.line] hairline. Pressed: [PRESS_DELTA] L* further toward the page's contrast side
 * (lighter on a dark page, darker on a light one, then at least [PRESS_BELOW_PAGE] below the page). Off track: [ToneLadder.SUNKEN] with an
 * outline, and a thumb of the outline's colour: that colour is the page's tone stepped toward the text until it is [OFF_OUTLINE_CONTRAST]:1
 * against the page (WCAG 1.4.11), so an off switch shows on every set.
 */
class FlatPartLook(
    val dark: Boolean,
    val secondary: Color,
    val secondaryPressed: Color,
    val secondaryEdge: Color,
    /** A disabled cap: flat, no edge. */
    val off: Color,
    /** The off switch's track and its outline; [trackEdge] also fills the off thumb ([thumbOff]). */
    val track: Color,
    val trackEdge: Color,
    /** The on switch's thumb: a light solid. */
    val thumb: Color,
    /** The tint of every drop shadow: black on a dark page, the board's brown on a light one. */
    val shade: Color,
    /** The focus rim: [FocusLift.rimColor] of the page (near-white on a dark page, the text ink on a light one). */
    val rim: Color,
) {
    /** An accent part's fill, hairline and pressed fill. */
    class Accent(
        val fill: Color,
        val edge: Color,
        val pressed: Color,
    )

    /** The off switch's thumb: the outline's colour. */
    val thumbOff: Color get() = trackEdge

    private var accentMemo: Pair<Color, Accent>? = null

    /** The accent fill, its hairline (the accent [ACCENT_EDGE] toward black) and its pressed fill ([PRESS_DELTA] L* or more darker, so the white label keeps its contrast). */
    fun primary(accent: Color): Accent = accentMemo?.takeIf { it.first == accent }?.second ?: accentOf(accent).also { accentMemo = accent to it }

    companion object {
        /** Pressed: this many L* from the rest fill toward the page's contrast side. */
        const val PRESS_DELTA = 6.0

        /** A pressed cap on a light page is also at least this far below the page. */
        const val PRESS_BELOW_PAGE = 6.0

        /** The off switch's outline (and so its thumb) reaches this contrast against the page: WCAG 1.4.11. */
        const val OFF_OUTLINE_CONTRAST = 3.0

        /** The accent's hairline: this share of the way to black. */
        const val ACCENT_EDGE = 0.28

        /** A primary's pressed fill is darker than the rest by at least this many L*. */
        private const val ACCENT_PRESS_L = PRESS_DELTA + 0.5

        /** The drop shadow of a button on a light page (a dark page has none), and of a thumb on either. */
        const val LIGHT_BUTTON_SHADOW_ALPHA = 0.22f
        const val THUMB_SHADOW_ALPHA_DARK = 0.45f
        const val THUMB_SHADOW_ALPHA_LIGHT = 0.30f

        private const val THUMB_WHITE_DARK = 0.90
        private const val THUMB_WHITE_LIGHT = 0.96
        private const val SEARCH_STEP = 0.5
        private const val SEARCH_LIMIT = 70.0

        internal fun of(
            bg: Rgb,
            text: Rgb,
            dark: Boolean,
        ): FlatPartLook {
            val white = Rgb(0xFFFFFF)
            val toward = if (ColorMath.lightness(text) > ColorMath.lightness(bg)) 1.0 else -1.0
            val rest = ToneLadder.level(bg, ToneLadder.TOP)
            // Pressed: toward the page's contrast side until it is PRESS_DELTA L* from the rest fill (measured, as a step of the ladder is not exactly a step of L* where a colour is clamped).
            var step = if (dark) ToneLadder.TOP + PRESS_DELTA else minOf(ToneLadder.TOP - PRESS_DELTA, -PRESS_BELOW_PAGE)
            var pressed = ToneLadder.level(bg, step)
            while (step in -99.0..99.0 && abs(ColorMath.lightness(pressed) - ColorMath.lightness(rest)) < PRESS_DELTA + 0.2) {
                step += if (dark) 0.25 else -0.25
                pressed = ToneLadder.level(bg, step)
            }
            return FlatPartLook(
                dark = dark,
                secondary = rest.toComposeColor(),
                secondaryPressed = pressed.toComposeColor(),
                secondaryEdge = ToneLadder.line(bg, text).toComposeColor(),
                off = ToneLadder.level(bg, ToneLadder.SURFACE).toComposeColor(),
                track = ToneLadder.level(bg, ToneLadder.SUNKEN).toComposeColor(),
                trackEdge = offTrackEdge(bg, toward).toComposeColor(),
                thumb = ColorMath.mix(bg, white, if (dark) THUMB_WHITE_DARK else THUMB_WHITE_LIGHT).toComposeColor(),
                shade = if (dark) Color.Black else CollarLook.SHADE_BROWN.toComposeColor(),
                rim = FocusLift.rimColor(dark, text.toComposeColor()),
            )
        }

        /** The first tone from [ToneLadder.LINE] toward the text that is [OFF_OUTLINE_CONTRAST]:1 against [bg] (never past the text's own tone, so never past `muted`). */
        private fun offTrackEdge(
            bg: Rgb,
            toward: Double,
        ): Rgb {
            var step = ToneLadder.LINE
            while (step < SEARCH_LIMIT) {
                val c = ToneLadder.level(bg, toward * step)
                if (ColorMath.contrast(c, bg) >= OFF_OUTLINE_CONTRAST) return c
                step += SEARCH_STEP
            }
            return ToneLadder.level(bg, toward * step)
        }

        private fun accentOf(accent: Color): Accent {
            val a = Rgb(accent.toArgb() and 0xFFFFFF)
            val black = Rgb(0x000000)
            val start = ColorMath.lightness(a)
            var share = 0.0
            var pressed = a
            while (share < 1.0 && start - ColorMath.lightness(pressed) < ACCENT_PRESS_L) {
                share += 0.01
                pressed = ColorMath.mix(a, black, share)
            }
            return Accent(accent, lerp(accent, Color.Black, ACCENT_EDGE.toFloat()), pressed.toComposeColor())
        }

        /** Graphite, for a [CollarLook] built without a theme. */
        internal val GRAPHITE: FlatPartLook = of(Rgb(0x111214), Rgb(0xEEEAE2), true)
    }
}
