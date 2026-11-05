package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** The slab's CSS values and the raised card's measured V2 board values (Parts sheets, 1 board px = 1/1.5 dp), so a change to one is a decision. */
class SlabBoardValuesTest {
    private fun within(
        expected: Int,
        actual: Rgb,
        channel: (Rgb) -> Int,
    ) = assertEquals(expected.toDouble(), channel(actual).toDouble(), 2.0)

    @Test
    fun theAccentBarIsTheBoards4By17dp() {
        assertEquals(4f, SlabDimens.barWidth.value, 0.01f)
        assertEquals(17.33f, SlabDimens.barHeight.value, 0.01f)
    }

    /**
     * The CSS gradient over the page. Graphite (page #111214): white 0.17 / 0.07 / 0.045 gives #393A3C at the top, #222324 at 46 %, #1C1D1F at the
     * bottom. Paper: white moved 0.18 and 0.55 toward the page (`#FCFBF9` to `#F4F2ED`). Its mean is the flat [FocusSlab.fill].
     */
    @Test
    fun theSlabGradientIsTheCssOverThePage() {
        val g = FocusSlab.stops(Rgb(0x111214), Rgb(0x111214), dark = true)
        for ((stop, hex) in g.zip(listOf(0x393A3C, 0x222324, 0x1C1D1F))) {
            within(hex shr 16, stop) { it.red }
            within((hex shr 8) and 0xFF, stop) { it.green }
            within(hex and 0xFF, stop) { it.blue }
        }
        assertEquals(0.46f, FocusSlab.MID_AT, 0f)
        val p = FocusSlab.stops(Rgb(0xECE8DF), Rgb(0xECE8DF), dark = false)
        for ((stop, hex) in listOf(p[0], p[2]).zip(listOf(0xFCFBF9, 0xF4F2ED))) {
            within(hex shr 16, stop) { it.red }
            within((hex shr 8) and 0xFF, stop) { it.green }
            within(hex and 0xFF, stop) { it.blue }
        }
        // The mean of 0.17 to 0.07 over 46 % and 0.07 to 0.045 over the rest.
        assertEquals(0.0862, FocusSlab.DARK_MEAN, 0.0005)
    }

    /** A light page's slab: neither end is white, both stay lighter than the page, the bottom is the page-ward end, and the flat fill is the true mean. */
    @Test
    fun aLightSlabFollowsThePageAndStaysRaised() {
        for (bg in listOf(Rgb(0xECE8DF), Rgb(0xE3EBF5))) {
            val g = FocusSlab.stops(bg, bg, dark = false)
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
        // Dark is unchanged: white 0.17 over the page at the top.
        val d = FocusSlab.stops(Rgb(0x111214), Rgb(0x111214), dark = true)
        assertEquals(0x39, d[0].red)
    }

    /** The CSS border: 1 px of white 0.14 (dark) or rgba(60,40,20,0.06) (light), its top white 0.42 / #FFFFFF. */
    @Test
    fun theSlabBorderIsTheCss() {
        val g = SlabLook.of(Rgb(0x111214), Rgb(0xEBE7DD), dark = true)
        assertEquals(Color.White.copy(alpha = 0.14f), g.edge)
        assertEquals(Color.White.copy(alpha = 0.42f), g.top)
        val p = SlabLook.of(Rgb(0xECE8DF), Rgb(0x1D1D1F), dark = false)
        assertEquals(Color(0x3C, 0x28, 0x14).copy(alpha = 0.06f), p.edge)
        assertEquals(Color.White, p.top)
        assertEquals(0.667f, SlabDimens.edge.value, 0.001f)
    }

    /** The focused row's year and region: Main board #D6D2C8 and #C9C5BB on Graphite; Paper board #4A4740 for both; weights 500 / 600 (dark) / 700 (light). */
    @Test
    fun theFocusedTrailingTextIsTheBoards() {
        val text = Rgb(0xEBE7DD)
        val fill = FocusSlab.fill(Rgb(0x111214), text, dark = true)
        val floor = ColorMath.reach(Rgb(0x111214), text, fill, 6.0)
        val year = FocusSlab.yearInk(fill, text, floor, dark = true)
        within(0xD6, year) { it.red }
        within(0xD2, year) { it.green }
        within(0xC8, year) { it.blue }
        val region = FocusSlab.regionInk(fill, text, floor, dark = true)
        within(0xC9, region) { it.red }
        within(0xC5, region) { it.green }
        within(0xBB, region) { it.blue }
        val ptext = Rgb(0x1C1C1E)
        val pbg = Rgb(0xECE8DF)
        val pfill = FocusSlab.fill(pbg, ptext, dark = false)
        val pfloor = ColorMath.reach(pbg, ptext, pfill, 6.0)
        for (ink in listOf(FocusSlab.yearInk(pfill, ptext, pfloor, dark = false), FocusSlab.regionInk(pfill, ptext, pfloor, dark = false))) {
            within(0x4A, ink) { it.red }
            within(0x47, ink) { it.green }
            // The board's #4A4740 is warmer than the neutral Paper text mixed toward the fill: blue is 9 above it (a warm tint is not derived from the text).
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
        assertEquals(0.4, RaisedCard.SHADOW_NEAR, 0.0)
        assertEquals(0.6, RaisedCard.SHADOW_FAR, 0.0)
        assertEquals(20f, CardDimens.shadowFarDrop.value, 0.01f)
    }

    @Test
    fun theSlabRingIsBlackAt40PercentOnDarkOnly() {
        assertEquals(0.4, FocusSlab.RING_DARK, 0.0)
        assertEquals(0f, SlabLook.of(Rgb(0xECE8DF), Rgb(0x1D1D1F), dark = false).ring.alpha, 0f)
        assertEquals(0.4f, SlabLook.of(Rgb(0x0E0F11), Rgb(0xF2F2F2), dark = true).ring.alpha, 0.001f)
    }

    @Test
    fun theSlabIs46Point7DpTallWithTheBarInsetAs8Point7() {
        assertEquals(46.67f, SlabDimens.height.value, 0.01f)
        assertEquals(8.67f, SlabDimens.barInset.value, 0.01f)
        assertEquals(23.33f, PlumDimens.gameRowPadding.value, 0.01f)
    }

    /** On a raised card the dark gradient is the same white overlays over the card (the menu board's card), the light one the page's, opaque. */
    @Test
    fun theSlabOnACardIsTheCssOverTheCard() {
        val text = Rgb(0xF2F2F2)
        val bg = Rgb(0x0E0F11)
        val card = RaisedCard.fill(bg, text, dark = true)
        val look = SlabLook.of(bg, text, dark = true)
        val top = look.stopsOnCard[0]
        assertEquals(card.red + (255 - card.red) * 0.17, top.red * 255.0, 1.0)
        assertEquals(card.blue + (255 - card.blue) * 0.045, look.stopsOnCard[2].blue * 255.0, 1.0)
        val paper = SlabLook.of(Rgb(0xECE8DF), Rgb(0x1D1D1F), dark = false)
        assertEquals(paper.stops, paper.stopsOnCard)
    }

    /**
     * The GLASS box-shadows of the kit boards, per layer (dp = px / 1.5, sigma = blur / 2 / 1.5). Graphite: inset `0 1px 1px` white 0.30 and
     * `0 -10px 18px` black 0.20, drops `0 3px 6px` black 0.35 and `0 16px 32px` black 0.45. Paper: inset `0 -4px 8px` brown 0.04, drops `0 3px 6px` 0.158
     * and `0 16px 32px` 0.203.
     */
    @Test
    fun theSlabShadowsAreTheCssLayers() {
        fun check(
            name: String,
            s: SlabShadow?,
            color: Color,
            drop: Float,
            sigma: Float,
        ) {
            requireNotNull(s) { "$name missing" }
            assertEquals("$name colour", color, s.color)
            assertEquals("$name drop", drop, s.drop.value, 0.01f)
            assertEquals("$name sigma", sigma, s.sigma.value, 0.01f)
        }
        val brown = Color(0x3C, 0x28, 0x14)
        val g = SlabLook.of(Rgb(0x111214), Rgb(0xEBE7DD), dark = true)
        check("graphite inset top", g.insetTop, Color.White.copy(alpha = 0.30f), 0.667f, 0.333f)
        check("graphite inset bottom", g.insetBottom, Color.Black.copy(alpha = 0.20f), -6.667f, 6f)
        check("graphite near", g.near, Color.Black.copy(alpha = 0.35f), 2f, 2f)
        // The CSS `0 16px 32px` black 0.45 (10.67 dp down, sigma 10.67) as Chrome renders it beside the board's slab: 0.52, 9 dp, sigma 13 (SlabListHaloTest).
        check("graphite far", g.far, Color.Black.copy(alpha = 0.52f), 9f, 13f)
        val p = SlabLook.of(Rgb(0xECE8DF), Rgb(0x1D1D1F), dark = false)
        assertEquals(null, p.insetTop)
        check("paper inset bottom", p.insetBottom, brown.copy(alpha = 0.04f), -2.667f, 2.667f)
        check("paper near", p.near, brown.copy(alpha = 0.158f), 2f, 2f)
        check("paper far", p.far, brown.copy(alpha = 0.203f), 10.667f, 10.667f)
        // `0 0 22px` (CSS sigma 7.33) as Chrome renders it, fitted with the far drop to the board's pixels.
        assertEquals(7f, SlabDimens.glowSigma.value, 0.01f)
        assertEquals(4f, SlabDimens.barGlowSigma.value, 0.01f)
    }

    /** A custom dark purple page (the Nova's kind) derives the same recipe: white overlays over its own colour, black shadows, the ring. */
    @Test
    fun aCustomDarkPurpleThemeDerivesTheRecipe() {
        val bg = Rgb(0x1E1030)
        val look = SlabLook.of(bg, Rgb(0xEDE6F5), dark = true)
        val top = look.stops[0]
        assertEquals((0x1E + (255 - 0x1E) * 0.17) / 255.0, top.red.toDouble(), 0.005)
        assertEquals((0x10 + (255 - 0x10) * 0.17) / 255.0, top.green.toDouble(), 0.005)
        assertEquals((0x30 + (255 - 0x30) * 0.17) / 255.0, top.blue.toDouble(), 0.005)
        assertEquals("still purple", true, top.blue > top.green)
        assertEquals(Color.White.copy(alpha = 0.14f), look.edge)
        assertEquals(Color.Black.copy(alpha = 0.52f), look.far?.color)
        assertEquals(0.4f, look.ring.alpha, 0.001f)
    }
}
