package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The slab's values and the raised card's measured V2 board values (Parts sheets, 1 board px = 1/1.5 dp), so a change to one is a decision. The slab
 * is the "lifted card" (docs/design/rules.md > Depth): a lighter flat face and one soft drop; the face's and the drop's values are in `FocusSlabSameObjectTest`.
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

    /** Requirement change ("lifted card"): the slab's fade and its stops are gone; the glow keeps its strength. */
    @Test
    fun theSlabGlowKeepsItsStrength() {
        assertEquals("the glow keeps its strength", 0.18, FocusHalo.SLAB_GLOW, 1e-6)
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

    /** The card's fill is the tone ladder's raised step: [ToneLadder.RAISED] L* above the page (the mean of its gradient, one L* either side). */
    @Test
    fun theCardFillIsTheRaisedStepOfThePage() {
        for (bg in listOf(Rgb(0xECE8DF), Rgb(0x111214), Rgb(0x1E1030))) {
            val card = RaisedCard.fill(bg, Rgb(0x1D1D1F), dark = ColorMath.lightness(bg) < 50)
            assertEquals("card on $bg", ColorMath.lightness(bg) + ToneLadder.RAISED, ColorMath.lightness(card), 0.5)
        }
        assertEquals(1.0, RaisedCard.shadowAlpha(dark = true), 0.0)
        assertEquals(0.4 * Depth.SHADOW, RaisedCard.SHADOW_NEAR, 1e-6)
        assertEquals(0.6 * Depth.SHADOW, RaisedCard.SHADOW_FAR, 1e-6)
        assertEquals(20f * Depth.SHADOW_REACH * 0.45f, CardDimens.shadowFarDrop.value, 0.01f)
    }

    @Test
    fun theSlabIs46Point7DpTallWithTheBarInsetAs8Point7() {
        assertEquals(46.67f, SlabDimens.height.value, 0.01f)
        assertEquals(8.67f, SlabDimens.barInset.value, 0.01f)
        assertEquals(23.33f, PlumDimens.gameRowPadding.value, 0.01f)
    }

    /** On a raised card the flat face is taken over the card, on every theme, the same recipe as on the page. */
    @Test
    fun theSlabOnACardIsTakenOverTheCard() {
        for ((bg, text, dark) in listOf(Triple(Rgb(0x0E0F11), Rgb(0xF2F2F2), true), Triple(Rgb(0xECE8DF), Rgb(0x1D1D1F), false))) {
            val card = RaisedCard.fill(bg, text, dark)
            val look = SlabLook.of(bg, text, dark)
            assertEquals(FocusSlab.fill(card, bg, dark).toComposeColor(), look.onCard)
        }
    }

    /** The glow's sigmas (`0 0 22px` as Chrome renders it); the drop is pinned in `FocusSlabSameObjectTest`. */
    @Test
    fun theGlowSigmasAreTheBoards() {
        assertEquals(7f * Depth.GLOW_REACH, SlabDimens.glowSigma.value, 0.01f)
        assertEquals(4f * Depth.GLOW_REACH, SlabDimens.barGlowSigma.value, 0.01f)
    }

    /** A custom dark purple page (the Nova's kind) derives the same recipe: its own colour, a shadow beneath. */
    @Test
    fun aCustomDarkPurpleThemeDerivesTheRecipe() {
        val bg = Rgb(0x1E1030)
        val look = SlabLook.of(bg, Rgb(0xEDE6F5), dark = true)
        val top = look.fill
        assertEquals("lighter than the page", true, top.red > 0x1E / 255f)
        assertEquals("still purple", true, top.blue > top.green)
        assertEquals("the lift is a shadow", Color.Black, look.near?.color?.copy(alpha = 1f))
    }
}
