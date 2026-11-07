package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The slab's values and the raised card's measured V2 board values (Parts sheets, 1 board px = 1/1.5 dp), so a change to one is a decision. The slab
 * is one even glass pane (docs/design/rules.md > Depth): its gradient, rim and missing insets and ring are the design's, its shadows the boards'
 * at [Depth] (and a further 0.6 of strength on the near drop and the dark far drop, the glass design's).
 */
class SlabBoardValuesTest {
    private fun within(
        expected: Int,
        actual: Rgb,
        channel: (Rgb) -> Int,
    ) = assertEquals(expected.toDouble(), channel(actual).toDouble(), 2.0)

    /** [ink] is [fill] moved the board's share of the way to [text]: the share that took [boardFill] to [boardInk] (blue may differ by [blueSlack]: a warm tint is not derived). */
    private fun withinShare(
        boardInk: Int,
        boardFill: Rgb,
        text: Rgb,
        fill: Rgb,
        ink: Rgb,
        blueSlack: Double = 2.0,
    ) {
        fun want(
            boardInkChannel: Int,
            boardFillChannel: Int,
            textChannel: Int,
            fillChannel: Int,
        ) = fillChannel + (textChannel - fillChannel) * (boardInkChannel - boardFillChannel).toDouble() / (textChannel - boardFillChannel)
        assertEquals(want(boardInk shr 16, boardFill.red, text.red, fill.red), ink.red.toDouble(), 2.0)
        assertEquals(want((boardInk shr 8) and 0xFF, boardFill.green, text.green, fill.green), ink.green.toDouble(), 2.0)
        assertEquals(want(boardInk and 0xFF, boardFill.blue, text.blue, fill.blue), ink.blue.toDouble(), blueSlack)
    }

    @Test
    fun theAccentBarIsTheBoards4By17dp() {
        assertEquals(4f, SlabDimens.barWidth.value, 0.01f)
        assertEquals(17.33f, SlabDimens.barHeight.value, 0.01f)
    }

    /** The gradient over the page: a gentle fall from the top to the foot, Graphite #2B2C2E to #222325-ish and Paper near white to #F5F3EE-ish. `FocusSlabSameObjectTest` holds the steps. */
    @Test
    fun theSlabGradientFallsGentlyOverThePage() {
        for ((bg, dark) in listOf(Rgb(0x111214) to true, Rgb(0xECE8DF) to false)) {
            val g = FocusSlab.stops(bg, dark)
            assertEquals(true, ColorMath.luminance(g[0]) > ColorMath.luminance(g[1]))
            assertEquals(true, ColorMath.luminance(g[1]) > ColorMath.luminance(g[2]))
            assertEquals(true, ColorMath.luminance(g[2]) > ColorMath.luminance(bg))
        }
        assertEquals(0.46f, FocusSlab.MID_AT, 0f)
        assertEquals("the glow keeps its strength", 0.18, FocusHalo.SLAB_GLOW, 1e-6)
    }

    /** A light page's slab: neither end is white, both stay lighter than the page, the bottom is the page-ward end, and the flat fill is the true mean. */
    @Test
    fun aLightSlabFollowsThePageAndStaysRaised() {
        for (bg in listOf(Rgb(0xECE8DF), Rgb(0xE3EBF5))) {
            val g = FocusSlab.stops(bg, dark = false)
            assertEquals("top is not white", true, g[0] != Rgb(0xFFFFFF))
            assertEquals("bottom is not white", true, g[2] != Rgb(0xFFFFFF))
            for (stop in g) assertEquals("lighter than the page", true, ColorMath.luminance(stop) > ColorMath.luminance(bg))
            assertEquals("darker toward the bottom", true, ColorMath.luminance(g[2]) < ColorMath.luminance(g[0]))
            val fill = FocusSlab.fill(bg, Rgb(0x1D1D1F), dark = false)
            val mean = Rgb.fromChannels((g[0].red + g[2].red) / 2.0, (g[0].green + g[2].green) / 2.0, (g[0].blue + g[2].blue) / 2.0)
            assertEquals(mean.red.toDouble(), fill.red.toDouble(), 1.0)
            assertEquals(mean.green.toDouble(), fill.green.toDouble(), 1.0)
            assertEquals(mean.blue.toDouble(), fill.blue.toDouble(), 1.0)
        }
    }

    /** The rim: a thin hairline, white on dark and brown on light, lit white at two corners. */
    @Test
    fun theSlabRimIsThinAndLitAtItsCorners() {
        val g = SlabLook.of(Rgb(0x111214), Rgb(0xEBE7DD), dark = true)
        assertEquals(Color.White, g.edge.copy(alpha = 1f))
        assertEquals(Color.White, g.top.copy(alpha = 1f))
        assertEquals(true, g.top.alpha > g.edge.alpha)
        val p = SlabLook.of(Rgb(0xECE8DF), Rgb(0x1D1D1F), dark = false)
        assertEquals(Color(0x3C, 0x28, 0x14), p.edge.copy(alpha = 1f))
        assertEquals(Color.White, p.top.copy(alpha = 1f))
        assertEquals(0.667f, SlabDimens.edge.value, 0.001f)
    }

    /** The focused row's year and region: near the Main board's #D6D2C8 and #C9C5BB on Graphite and the Paper board's #4A4740 for both, as shares of the way from the fill to the text; weights 500 / 600 (dark) / 700 (light). */
    @Test
    fun theFocusedTrailingTextIsTheBoards() {
        val text = Rgb(0xEBE7DD)
        val fill = FocusSlab.fill(Rgb(0x111214), text, dark = true)
        val floor = ColorMath.reach(Rgb(0x111214), text, fill, 6.0)
        // The ink is the fill moved the board's share of the way to the text (board fill: the page with white 0.0862, the old gradient's mean).
        val boardFill = ColorMath.mix(Rgb(0x111214), Rgb(0xFFFFFF), 0.0862)
        val year = FocusSlab.yearInk(fill, text, floor, dark = true)
        withinShare(0xD6D2C8, boardFill, text, fill, year)
        val region = FocusSlab.regionInk(fill, text, floor, dark = true)
        withinShare(0xC9C5BB, boardFill, text, fill, region)
        val ptext = Rgb(0x1C1C1E)
        val pbg = Rgb(0xECE8DF)
        val pfill = FocusSlab.fill(pbg, ptext, dark = false)
        val pfloor = ColorMath.reach(pbg, ptext, pfill, 6.0)
        // On Paper the fill is whiter than the board's (#F7F5F1), so the ink is the recipe's share of the way from its own fill to the text, within 3 levels of the board's #4A4740.
        for ((ink, share) in listOf(FocusSlab.yearInk(pfill, ptext, pfloor, dark = false) to FocusSlab.YEAR_LIGHT, FocusSlab.regionInk(pfill, ptext, pfloor, dark = false) to FocusSlab.REGION_LIGHT)) {
            val want = ColorMath.mix(pfill, ptext, share)
            assertEquals(want.red.toDouble(), ink.red.toDouble(), 1.0)
            assertEquals(want.green.toDouble(), ink.green.toDouble(), 1.0)
            assertEquals(want.blue.toDouble(), ink.blue.toDouble(), 1.0)
            assertEquals(0x4A.toDouble(), ink.red.toDouble(), 3.0)
            assertEquals(0x47.toDouble(), ink.green.toDouble(), 3.0)
            assertEquals(0x40.toDouble(), ink.blue.toDouble(), 10.0)
        }
        assertEquals(listOf(500, 600, 700), listOf(FocusSlab.YEAR_WEIGHT, FocusSlab.REGION_WEIGHT_DARK, FocusSlab.REGION_WEIGHT_LIGHT))
        assertEquals(600, SlabLook.of(Rgb(0x111214), text, dark = true).regionWeight)
        assertEquals(700, SlabLook.of(pbg, ptext, dark = false).regionWeight)
    }

    /** The board's card gradient (#2C2E34 to #1A1B1F, Paper #FBFAF7 to #F3F0EA) averages #22 23 27 and #F7F5F1. */
    @Test
    fun theCardFillIsTheMeanOfTheBoardsGradient() {
        val paper = RaisedCard.fill(Rgb(0xECE8DF), Rgb(0x1D1D1F), dark = false)
        within(247, paper) { it.red }
        within(245, paper) { it.green }
        within(240, paper) { it.blue }
        assertEquals(1.0, RaisedCard.shadowAlpha(dark = true), 0.0)
        assertEquals(0.4 * Depth.SHADOW, RaisedCard.SHADOW_NEAR, 1e-6)
        assertEquals(0.6 * Depth.SHADOW, RaisedCard.SHADOW_FAR, 1e-6)
        assertEquals(20f * Depth.SHADOW_REACH * 0.45f, CardDimens.shadowFarDrop.value, 0.01f)
    }

    /** The glass pane has no dark ring (the board's was black at 40 % on dark themes). */
    @Test
    fun theSlabHasNoRingOnAnyTheme() {
        assertEquals(0.0, FocusSlab.RING_DARK, 0.0)
        assertEquals(0f, SlabLook.of(Rgb(0xECE8DF), Rgb(0x1D1D1F), dark = false).ring.alpha, 0f)
        assertEquals(0f, SlabLook.of(Rgb(0x0E0F11), Rgb(0xF2F2F2), dark = true).ring.alpha, 0f)
    }

    @Test
    fun theSlabIs46Point7DpTallWithTheBarInsetAs8Point7() {
        assertEquals(46.67f, SlabDimens.height.value, 0.01f)
        assertEquals(8.67f, SlabDimens.barInset.value, 0.01f)
        assertEquals(23.33f, PlumDimens.gameRowPadding.value, 0.01f)
    }

    /** On a raised card the gradient is taken over the card, on every theme. */
    @Test
    fun theSlabOnACardIsTakenOverTheCard() {
        for ((bg, text, dark) in listOf(Triple(Rgb(0x0E0F11), Rgb(0xF2F2F2), true), Triple(Rgb(0xECE8DF), Rgb(0x1D1D1F), false))) {
            val card = RaisedCard.fill(bg, text, dark)
            val look = SlabLook.of(bg, text, dark)
            assertEquals(FocusSlab.stops(card, dark).map { it.toComposeColor() }, look.stopsOnCard)
        }
    }

    /** The lift's two layers, the same on every theme: the board's `0 3px 6px` and `0 16px 32px` at [Depth.SHADOW_REACH] of their reach; the insets are gone (alpha 0). */
    @Test
    fun theSlabLiftLayersAreTheSameOnEveryTheme() {
        fun check(
            name: String,
            s: SlabShadow?,
            color: Color,
            drop: Float,
            sigma: Float,
        ) {
            requireNotNull(s) { "$name missing" }
            assertEquals("$name colour", color, s.color.copy(alpha = 1f))
            assertEquals("$name drop", drop, s.drop.value, 0.01f)
            assertEquals("$name sigma", sigma, s.sigma.value, 0.01f)
        }
        val brown = Color(0x3C, 0x28, 0x14)
        val g = SlabLook.of(Rgb(0x111214), Rgb(0xEBE7DD), dark = true)
        assertEquals("graphite inset top", 0f, g.insetTop?.color?.alpha ?: 0f, 0f)
        assertEquals("graphite inset bottom", 0f, g.insetBottom?.color?.alpha ?: 0f, 0f)
        check("graphite near", g.near, Color.White, 2f * Depth.SHADOW_REACH, 2f * Depth.SHADOW_REACH)
        check("graphite far", g.far, Color.White, 10.667f * Depth.SHADOW_REACH, 10.667f * Depth.SHADOW_REACH)
        val p = SlabLook.of(Rgb(0xECE8DF), Rgb(0x1D1D1F), dark = false)
        assertEquals(null, p.insetTop)
        assertEquals("paper inset bottom", 0f, p.insetBottom?.color?.alpha ?: 0f, 0f)
        check("paper near", p.near, brown, 2f * Depth.SHADOW_REACH, 2f * Depth.SHADOW_REACH)
        check("paper far", p.far, brown, 10.667f * Depth.SHADOW_REACH, 10.667f * Depth.SHADOW_REACH)
        // `0 0 22px` (CSS sigma 7.33) as Chrome renders it, fitted with the far drop to the board's pixels.
        assertEquals(7f * Depth.GLOW_REACH, SlabDimens.glowSigma.value, 0.01f)
        assertEquals(4f * Depth.GLOW_REACH, SlabDimens.barGlowSigma.value, 0.01f)
    }

    /** A custom dark purple page (the Nova's kind) derives the same recipe: white over its own colour, lit from beneath. */
    @Test
    fun aCustomDarkPurpleThemeDerivesTheRecipe() {
        val bg = Rgb(0x1E1030)
        val look = SlabLook.of(bg, Rgb(0xEDE6F5), dark = true)
        val top = look.stops[0]
        assertEquals("lighter than the page", true, top.red > 0x1E / 255f)
        assertEquals("still purple", true, top.blue > top.green)
        assertEquals(Color.White, look.edge.copy(alpha = 1f))
        assertEquals(Color.White, look.far?.color?.copy(alpha = 1f))
        assertEquals(0f, look.ring.alpha, 0f)
    }
}
