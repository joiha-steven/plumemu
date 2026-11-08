package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One CSS `box-shadow` layer in dp (the kit boards' CSS: blur B is a Gaussian of sigma B / 2; spread grows the shape, or shrinks an
 * inset's hole). [inset] layers are drawn inside the box over its fill; outer ones outside it only (CSS paints no outer shadow under the box).
 */
class CssShadow(
    color: Color,
    y: Dp,
    blur: Dp = 0.dp,
    val spread: Dp = 0.dp,
    val inset: Boolean = false,
    /** A focus glow: its colour and blur already are at their [Depth]. */
    glow: Boolean = false,
) {
    /** A soft outer shadow, or the highlights, insets and thicknesses that make a part look raised: each at its [Depth]. */
    private val isSoft = !glow && !inset && blur > 0.dp
    private val isThickness = !glow && !inset && blur == 0.dp && spread == 0.dp
    val color: Color = if (glow || isThickness) color else color.copy(alpha = color.alpha * if (isSoft) Depth.SHADOW else Depth.RAISE)
    val y: Dp = if (isSoft) y * Depth.SHADOW_REACH else if (isThickness) y * Depth.THICKNESS else y
    val blur: Dp = if (isSoft) blur * Depth.SHADOW_REACH else blur
}

/** A box's fill and shadows: [stops] top to bottom, evenly spaced (one stop is a solid fill), [shadows] in CSS order (the first is on top). */
class CssFace(
    val stops: List<Color>,
    val shadows: List<CssShadow>,
)

/** A raised cap: [rest], and [down] (pressed or off) drawn [travel] lower; the collar around it stays. */
class CssCap(
    val rest: CssFace,
    val down: CssFace,
    val travel: Dp,
) {
    companion object {
        /** A cap whose [travel] is the board's: glass is thinner, so it moves [Depth.THICKNESS] of it. A cap rebuilt from another's travel uses the constructor. */
        fun board(
            rest: CssFace,
            down: CssFace,
            travel: Dp,
        ) = CssCap(rest, down, travel * Depth.THICKNESS)
    }
}

/**
 * The seated parts of the kit boards (design/rules.md › Seated parts): the LIP, the WELL, the CAP and their states, every
 * colour derived from the theme's background and text (so Navy, OLED and a custom colour set get their own), read while drawing.
 * Graphite gives the board's #3C3F45 / #26282D / #17181B lip, #0F1012 well, #2A2C31 to #232529 cap over #121316; Paper the #FFFFFF / #F1EDE6 /
 * #E4DED3 lip, #D7D0C3 well, #F7F4EE to #EFEBE3 cap over #D3CBBC (`SeatLookTest`).
 */
class SeatLook(
    val dark: Boolean,
    /** The LIP's radial stops at 0 %, 45 % and 100 %, and the ellipse's height as a share of the box (Graphite 100 %, Paper 90 %; 120 % wide). */
    val lipStops: List<Color>,
    val lipEllipseHeight: Float,
    val lipShadows: List<CssShadow>,
    val well: Color,
    val wellShade: CssShadow,
    val capStops: List<Color>,
    val capDownStops: List<Color>,
    val capOff: Color,
    /** The cap's solid thickness under it (`0 3px 0 side`). */
    val side: Color,
    /** The tint of every drop shadow: black on a dark theme, the board's brown on a light one. */
    val shade: Color,
    /** The text field's well (`#0A0B0D` / `#E0DBD0`) and its inset (`inset 0 3px 6px`). */
    val fieldWell: Color,
    val fieldShade: CssShadow,
    /** The RAISED chip's radial stops at 0 % and 85 % (Graphite #34363C to #1D1E22, Paper #FFFFFF to #EFEBE3). */
    val chipStops: List<Color> = capStops,
    /** The page this look was made for. */
    val page: Rgb = Rgb(0x111214),
) {
    private fun s(a: Float) = shade.copy(alpha = a)

    /**
     * The cover's frame as glass: the lip is steps of lightness above the page ([Depth.COVER_FRAME_TOP_L] and on), as the slab's fill is, with a lit
     * crest along its top and a fine edge that parts it from the page. A near-white page has little room above it, so there the edge carries it.
     * Everything else is this look's. It goes through the collar's cached image, so a frame costs what it cost.
     */
    val coverGlass: SeatLook by lazy {
        fun up(l: Double) = ColorMath.mix(page, Rgb(0xFFFFFF), ColorMath.shareForLightness(page, Rgb(0xFFFFFF), l).toDouble()).toComposeColor()
        val edge = if (dark) Color.White.copy(alpha = Depth.COVER_FRAME_EDGE_DARK) else shade.copy(alpha = Depth.COVER_FRAME_EDGE_LIGHT)
        SeatLook(
            dark = dark,
            lipStops = listOf(up(Depth.COVER_FRAME_TOP_L), up(Depth.COVER_FRAME_MID_L), up(Depth.COVER_FRAME_BOTTOM_L)),
            lipEllipseHeight = lipEllipseHeight,
            lipShadows =
                listOf(
                    CssShadow(Color.White.copy(alpha = Depth.COVER_FRAME_CREST), (2f / 3f).dp, (1f / 3f).dp, inset = true),
                    CssShadow(edge, 0.dp, 0.dp, (1f / 3f).dp, inset = true),
                ) + lipShadows.filter { !it.inset },
            well = well,
            wellShade = wellShade,
            capStops = capStops,
            capDownStops = capDownStops,
            capOff = capOff,
            side = side,
            shade = shade,
            fieldWell = fieldWell,
            fieldShade = fieldShade,
            chipStops = chipStops,
            page = page,
        )
    }

    /**
     * The V2 board's RAISED surface (a chip, depth level 1): the radial [chipStops] under `inset 0 1px 0` (white .16, Paper #FFFFFF), `inset 0 -3px 4px`
     * (black .35, Paper brown .158), the ring `0 0 0 1px` (black .1, Paper brown .045) and the drop `0 2px 3px` (black .55, Paper brown .248).
     */
    val raisedChip: CssFace by lazy {
        CssFace(
            chipStops,
            listOf(
                CssShadow(if (dark) Color.White.copy(alpha = 0.16f) else Color.White, (2f / 3f).dp, inset = true),
                CssShadow(s(if (dark) 0.35f else 0.158f), (-2f).dp, (8f / 3f).dp, inset = true),
                CssShadow(s(if (dark) 0.10f else 0.045f), 0.dp, 0.dp, (2f / 3f).dp),
                CssShadow(s(if (dark) 0.55f else 0.248f), (4f / 3f).dp, (4f / 3f).dp),
            ),
        )
    }

    /** The button, knob and segment cap (CAP) and the off cap, made once. */
    val buttonCap: CssCap by lazy { cap() }
    val offCap: CssCap by lazy { off() }

    /** The hint key cap: thickness `0 2px 0`, drop `0 3px 4px` at .4 (Paper .18), pressed 2 px down with `inset 0 1px 2px .25`. */
    val hintCap: CssCap by lazy {
        cap(thick = (4f / 3f).dp, dropY = 2.dp, dropBlur = (8f / 3f).dp, dropAlpha = 0.40f, dropAlphaLight = 0.18f, travel = (4f / 3f).dp).let {
            CssCap(it.rest, CssFace(capDownStops, listOf(CssShadow(Color.Black.copy(alpha = 0.25f), (2f / 3f).dp, (4f / 3f).dp, inset = true))), it.travel)
        }
    }

    /** The keyboard's letter cap: `inset 0 1px 0 .14`, thickness `0 3px 0`, no drop. */
    val keyCap: CssCap by lazy { cap(dropAlpha = 0f, light = 0.14f) }

    /** The keyboard's word and icon cap: the letter cap a step lighter (the Search board's #33353B to #2B2D32), `inset 0 1px 0 .16`. */
    val wordCap: CssCap by lazy {
        fun lift(c: Color) = Color(c.red + (1f - c.red) * WORD_LIFT, c.green + (1f - c.green) * WORD_LIFT, c.blue + (1f - c.blue) * WORD_LIFT)
        val base = cap(dropAlpha = 0f)
        CssCap(CssFace(capStops.map(::lift), base.rest.shadows), base.down, base.travel)
    }

    private var swatchMemo: Pair<Color, CssCap>? = null
    private var dotMemo: Pair<Color, CssCap>? = null

    /**
     * A colour swatch's tile in [fill]: `inset 0 1px 0 rgba(255,255,255,.35), 0 3px 0 rgba(0,0,0,.30), 0 3px 0 side, 0 4px 5px .35` (Paper's drop
     * the brown at .158); its down face (locked) is the flat fill.
     */
    fun swatch(fill: Color): CssCap =
        swatchMemo?.takeIf { it.first == fill }?.second ?: CssCap.board(
            CssFace(
                listOf(fill),
                listOf(
                    CssShadow(Color.White.copy(alpha = 0.35f), (2f / 3f).dp, inset = true),
                    CssShadow(Color.Black.copy(alpha = 0.30f), 2.dp),
                    CssShadow(side, 2.dp),
                    CssShadow(s(if (dark) 0.35f else 0.158f), (8f / 3f).dp, (10f / 3f).dp),
                ),
            ),
            CssFace(listOf(fill), emptyList()),
            2.dp,
        ).also { swatchMemo = fill to it }

    /** The chosen dot of a one-of-several choice: accentLight to accent, `inset 0 1px 0 .4, 0 2px 0 accentDark`. */
    fun choiceDot(accent: Color): CssCap = dotMemo?.takeIf { it.first == accent }?.second ?: accentCap(accent, thick = (4f / 3f).dp, dropAlpha = 0f).also { dotMemo = accent to it }

    private var accentMemo: Pair<Color, CssCap>? = null
    private var hintAccentMemo: Pair<Color, CssCap>? = null
    private var keyAccentMemo: Pair<Color, CssCap>? = null

    /** [accentCap] for a button, kept until the accent moves (drawing allocates nothing while it stays). */
    fun buttonAccent(accent: Color): CssCap = accentMemo?.takeIf { it.first == accent }?.second ?: accentCap(accent).also { accentMemo = accent to it }

    /** The hint key's accent cap (A, confirm): thickness `0 2px 0`, drop `0 3px 4px .35`. */
    fun hintAccent(accent: Color): CssCap =
        hintAccentMemo?.takeIf { it.first == accent }?.second
            ?: accentCap(accent, thick = (4f / 3f).dp, dropY = 2.dp, dropBlur = (8f / 3f).dp, travel = (4f / 3f).dp).also { hintAccentMemo = accent to it }

    /** The keyboard's action cap (Search): `inset 0 1px 0 .4, 0 3px 0 accentDark`, no drop. */
    fun keyAccent(accent: Color): CssCap = keyAccentMemo?.takeIf { it.first == accent }?.second ?: accentCap(accent, dropAlpha = 0f).also { keyAccentMemo = accent to it }

    /** The cap highlight, `inset 0 1px 0 rgba(255,255,255,.16)` (Paper `#FFFFFF`). */
    private fun capLight(alpha: Float = CAP_LIGHT) = CssShadow(Color.White.copy(alpha = if (dark) alpha else 1f), (2f / 3f).dp, inset = true)

    /** The switch's on track `inset 0 2px 4px .40`, the locked knob's `inset 0 1px 2px .25`, the hue track's `inset 0 1px 3px .45` (all themes). */
    val switchOnShade = CssShadow(Color.Black.copy(alpha = 0.40f), (4f / 3f).dp, (8f / 3f).dp, inset = true)
    val lockedKnobShade = CssShadow(Color.Black.copy(alpha = 0.25f), (2f / 3f).dp, (4f / 3f).dp, inset = true)
    val hueShade = CssShadow(Color.Black.copy(alpha = 0.45f), (2f / 3f).dp, 2.dp, inset = true)

    /** The lit groove: `inset 0 1px 2px rgba(0,0,0,.35)` (all themes). */
    val focusShade = CssShadow(Color.Black.copy(alpha = 0.35f), (2f / 3f).dp, (4f / 3f).dp, inset = true)

    /**
     * A neutral cap: [thick] of solid side, a drop [dropY] lower blurred [dropBlur] at [dropAlpha] (Paper [dropAlphaLight]). Pressed: the
     * capDown gradient with `inset 0 2px 3px rgba(0,0,0,.22)`, [travel] lower. Buttons, knobs and segments use the defaults.
     */
    fun cap(
        thick: Dp = 2.dp,
        dropY: Dp = (8f / 3f).dp,
        dropBlur: Dp = (10f / 3f).dp,
        dropAlpha: Float = 0.45f,
        dropAlphaLight: Float = 0.20f,
        travel: Dp = 2.dp,
        light: Float = CAP_LIGHT,
    ): CssCap {
        val drop = if (dropAlpha > 0f) listOf(CssShadow(s(if (dark) dropAlpha else dropAlphaLight), dropY, dropBlur)) else emptyList()
        return CssCap.board(
            rest = CssFace(capStops, listOf(capLight(light), CssShadow(side, thick)) + drop),
            down = CssFace(capDownStops, listOf(CssShadow(Color.Black.copy(alpha = 0.22f), (4f / 3f).dp, 2.dp, inset = true))),
            travel = travel,
        )
    }

    /** A disabled cap: flat capOff, `inset 0 1px 2px rgba(0,0,0,.18)`, sat down. */
    fun off(travel: Dp = 2.dp): CssCap {
        val face = CssFace(listOf(capOff), listOf(CssShadow(Color.Black.copy(alpha = 0.18f), (2f / 3f).dp, (4f / 3f).dp, inset = true)))
        return CssCap.board(face, face, travel)
    }

    /** The ACCENT CAP: accentLight to accent, `inset 0 1px 0 rgba(255,255,255,.4), 0 3px 0 accentDark, 0 4px 5px rgba(0,0,0,.35)`; pressed flat accent with `inset 0 2px 4px .30`. */
    fun accentCap(
        accent: Color,
        thick: Dp = 2.dp,
        dropY: Dp = (8f / 3f).dp,
        dropBlur: Dp = (10f / 3f).dp,
        dropAlpha: Float = 0.35f,
        travel: Dp = 2.dp,
    ): CssCap {
        val drop = if (dropAlpha > 0f) listOf(CssShadow(Color.Black.copy(alpha = dropAlpha), dropY, dropBlur)) else emptyList()
        return CssCap.board(
            rest = CssFace(listOf(accentLight(accent), accent), listOf(CssShadow(Color.White.copy(alpha = 0.8f), (2f / 3f).dp, inset = true), CssShadow(accentDark(accent), thick)) + drop),
            down = CssFace(listOf(accent), listOf(CssShadow(Color.Black.copy(alpha = 0.30f), (4f / 3f).dp, (8f / 3f).dp, inset = true))),
            travel = travel,
        )
    }

    companion object {
        const val CAP_LIGHT = 0.5f
        const val WORD_LIFT = 0.04f

        /** Graphite's, for a [CollarLook] built by hand (tests). */
        val GRAPHITE: SeatLook by lazy { of(Rgb(0x111214), Rgb(0xECE8DE), true) }

        /** `accentLight`: each channel 30 % of the way to white (the kit boards). */
        fun accentLight(accent: Color) = Color(accent.red + (1f - accent.red) * 0.3f, accent.green + (1f - accent.green) * 0.3f, accent.blue + (1f - accent.blue) * 0.3f)

        /** `accentDark`: each channel times 0.62. */
        fun accentDark(accent: Color) = Color(accent.red * 0.62f, accent.green * 0.62f, accent.blue * 0.62f)

        /** The board's warm shade under Paper's lip and caps (its #E4DED3 lip foot and #D3CBBC cap side lie on the line from the page to it). */
        private val WARM_SHADE = Rgb(0x6F5730)

        /** The warm white Paper's cap top leans to (#F7F4EE over #ECE8DF). */
        private val WARM_WHITE = Rgb(0xFFFCF5)

        internal fun of(
            bg: Rgb,
            text: Rgb,
            dark: Boolean,
        ): SeatLook {
            fun c(rgb: Rgb) = rgb.toComposeColor()
            if (dark) {
                val ink = CollarLook.surfaceInk(text)
                fun up(t: Double) = c(ColorMath.mix(bg, ink, t))
                val black = Color.Black
                return SeatLook(
                    dark = true,
                    lipStops = listOf(up(0.21 * Depth.RAISE), up(0.10 * Depth.RAISE), up(0.03 * Depth.RAISE)),
                    lipEllipseHeight = 1f,
                    lipShadows =
                        listOf(
                            CssShadow(Color.White.copy(alpha = 0.16f), (4f / 3f).dp * Depth.LIP, (2f / 3f).dp, inset = true),
                            CssShadow(black.copy(alpha = 0.40f), (-2f).dp * Depth.LIP, (8f / 3f).dp * Depth.LIP, inset = true),
                            CssShadow(Color.White.copy(alpha = 0.03f), 0.dp, 0.dp, (2f / 3f).dp, inset = true),
                            CssShadow(Color.White.copy(alpha = 0.03f), (-2f / 3f).dp, 2.dp),
                            CssShadow(black.copy(alpha = 0.45f), 2.dp, (8f / 3f).dp),
                            CssShadow(black.copy(alpha = 0.45f), (20f / 3f).dp, (44f / 3f).dp),
                        ),
                    well = c(ColorMath.mix(bg, Rgb(0x000000), 0.12 * Depth.RAISE)),
                    wellShade = CssShadow(black.copy(alpha = 0.6f), (2f / 3f).dp, (4f / 3f).dp, inset = true),
                    capStops = listOf(up(0.10), up(0.085)),
                    capDownStops = listOf(up(0.075), up(0.062)),
                    capOff = up(0.033),
                    side = up(0.006),
                    shade = black,
                    fieldWell = c(ToneLadder.level(bg, ToneLadder.SUNKEN)),
                    fieldShade = CssShadow(black.copy(alpha = 0.55f), 2.dp, 4.dp, inset = true),
                    chipStops = listOf(up(0.165), up(0.058)),
                    page = bg,
                )
            }
            val brown = CollarLook.SHADE_BROWN.toComposeColor()
            fun warm(t: Double) = c(ColorMath.mix(bg, WARM_SHADE, t))
            fun br(t: Double) = c(ColorMath.mix(bg, CollarLook.SHADE_BROWN, t))
            return SeatLook(
                dark = false,
                lipStops = listOf(c(ColorMath.mix(bg, Rgb(0xFFFFFF), 1.0 * Depth.RAISE)), c(ColorMath.mix(bg, Rgb(0xFFFFFF), 0.26 * Depth.RAISE)), warm(0.068 * Depth.RAISE)),
                lipEllipseHeight = 0.9f,
                lipShadows =
                    listOf(
                        CssShadow(Color.White, (4f / 3f).dp * Depth.LIP, (2f / 3f).dp, inset = true),
                        CssShadow(brown.copy(alpha = 0.08f), (-2f).dp * Depth.LIP, 2.dp * Depth.LIP, inset = true),
                        CssShadow(Color.White.copy(alpha = 0.8f), (-4f / 3f).dp, (8f / 3f).dp),
                        CssShadow(brown.copy(alpha = 0.14f), 2.dp, (8f / 3f).dp),
                        CssShadow(brown.copy(alpha = 0.14f), (20f / 3f).dp, (44f / 3f).dp),
                    ),
                well = warm(0.165 * Depth.RAISE),
                wellShade = CssShadow(brown.copy(alpha = 0.25f), (2f / 3f).dp, (4f / 3f).dp, inset = true),
                capStops = listOf(c(ColorMath.mix(bg, WARM_WHITE, 0.5)), c(ColorMath.mix(bg, Rgb(0xFFFFFF), 0.42))),
                capDownStops = listOf(br(0.005), c(ColorMath.mix(bg, Rgb(0xFFFFFF), 0.16))),
                capOff = br(0.035),
                side = warm(0.2),
                shade = brown,
                fieldWell = c(ToneLadder.level(bg, ToneLadder.SUNKEN)),
                fieldShade = CssShadow(brown.copy(alpha = 0.248f), 2.dp, 4.dp, inset = true),
                chipStops = listOf(c(ColorMath.mix(bg, Rgb(0xFFFFFF), 1.0)), c(ColorMath.mix(bg, Rgb(0xFFFFFF), 0.16))),
                page = bg,
            )
        }
    }
}
