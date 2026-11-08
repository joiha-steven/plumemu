package com.plumemu.coreui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.Depth
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.SeatLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The cover collar (SPEC 20) drawn as the grid draws it, at the boards' scale (1.5 px per dp, a pixel is a board px), read against the
 * PartsDark / PartsLight renders: the lip's 9 px corner, the lit groove's own glow over the lip and the kit's accent glow on Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class CoverCollarSpecTest {
    private fun DrawScope.cover(c: PlumComposeColors, art: ImageBitmap, x: Float, y: Float, focus: Float) {
        val cs = CollarSize.Cover
        val corner = CollarDimens.coverBoxCorner.toPx()
        val w = art.width + 2 * (cs.lip + cs.groove).toPx()
        val h = art.height + (cs.lip + cs.groove + cs.lipBottom + cs.grooveBottom).toPx()
        sized(x, y, Size(w, h)) {
            drawCollar(c.collar, cs, corner, focus, accent = c.accent)
            val at = Offset((cs.lip + cs.groove).toPx(), (cs.lip + cs.groove).toPx())
            val sz = Size(art.width.toFloat(), art.height.toFloat())
            translate(at.x, at.y) {
                drawSoft(Offset.Zero, sz, corner, CollarDimens.boxShadowSoftDrop.toPx(), CollarDimens.boxShadowSoftSigma.toPx(), c.collar.shadow.copy(alpha = 0.4f), true)
                drawRoundRect(c.collar.shadow.copy(alpha = 0.55f), Offset(0f, CollarDimens.boxShadowHardDrop.toPx()), sz, CornerRadius(corner))
                clipPath(Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, sz), CornerRadius(corner))) }) {
                    drawImage(art, IntOffset.Zero, IntSize(art.width, art.height), IntOffset.Zero, IntSize(art.width, art.height))
                }
            }
            drawCollarHalo(cs, corner, Offset.Zero, Size(w, h), focus, c.partGlow)
            drawCollarGrooveGlow(cs, corner, Offset.Zero, Size(w, h), focus, c.partGlow)
        }
    }

    private fun DrawScope.translate(x: Float, y: Float, block: DrawScope.() -> Unit) = drawContext.transform.let {
        it.translate(x, y)
        block()
        it.translate(-x, -y)
    }

    private fun DrawScope.sized(x: Float, y: Float, s: Size, block: DrawScope.() -> Unit) {
        val old = drawContext.size
        drawContext.transform.translate(x, y)
        drawContext.size = s
        block()
        drawContext.size = old
        drawContext.transform.translate(-x, -y)
    }

    private fun shot(set: PlumThemeSettings): (Int, Int) -> IntArray {
        val c = PlumComposeColors.from(PlumColors.derive(set.backgroundColor, set.textMode, set.accentColor))
        val image = ImageBitmap(398, 205)
        CanvasDrawScope().draw(Density(1.5f), LayoutDirection.Ltr, Canvas(image), Size(398f, 205f)) {
            drawRect(c.bg)
            // The board's EarthBound (112 x 156 px, at rest) and Contra III (214 x 156 px, focused) at its place, 1 px = 1 board px.
            cover(c, ImageBitmap(112, 156).also { solid(it, Color(0xFF1D1E22)) }, 18f, 18f, 0f)
            cover(c, ImageBitmap(214, 156).also { solid(it, Color(0xFF1F4FA8)) }, 154f, 18f, 1f)
        }
        val map = image.toPixelMap()
        return { x, y -> IntArray(3) { (map[x, y].toArgb() shr (16 - 8 * it)) and 0xFF } }
    }

    private fun solid(image: ImageBitmap, color: Color) = CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(image.width.toFloat(), image.height.toFloat())) { drawRect(color) }

    private fun near(what: String, board: IntArray, ours: IntArray, tolerance: Int) =
        assertTrue("$what: board ${board.toList()} ours ${ours.toList()}", board.indices.all { abs(board[it] - ours[it]) <= tolerance })

    /** A tone the board mixes from the page is drawn [Depth.RAISE] of the way from [page] to the board's [tone]. */
    private fun raised(page: IntArray, tone: IntArray) = IntArray(3) { (page[it] + (tone[it] - page[it]) * Depth.RAISE).roundToInt() }

    /** The share of a Gaussian glow of [sigma] dp left [distance] dp outside a straight edge (the tail beyond it). */
    private fun tail(distance: Double, sigma: Double): Double {
        val z = distance / sigma
        // Abramowitz and Stegun 26.2.17.
        val t = 1.0 / (1.0 + 0.2316419 * z)
        return exp(-z * z / 2) / sqrt(2 * Math.PI) * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
    }

    /**
     * What a board pixel [lit] that was [rest] before the glow reached it looks like drawn: the resting tone at Depth, plus the glow's change at
     * [Depth.GLOW] of its strength and the share of it a glow [distance] dp from the edge keeps once its sigma goes from [boardSigma] to
     * [boardSigma] x [Depth.GLOW_REACH].
     */
    private fun glowed(page: IntArray, rest: IntArray, lit: IntArray, distance: Double, boardSigma: Double): IntArray = glowedOver(raised(page, rest), rest, lit, distance, boardSigma)

    /** As [glowed] over a [base] the app draws itself: the cover's lip is glass (`SeatLook.coverGlass`), not the board's tone, and the glow's change on it is the board's. */
    private fun glowedOver(base: IntArray, rest: IntArray, lit: IntArray, distance: Double, boardSigma: Double): IntArray {
        val sigma = boardSigma * Depth.GLOW_REACH
        val share = Depth.GLOW * tail(distance, sigma) / tail(distance, boardSigma)
        return IntArray(3) { (base[it] + (lit[it] - rest[it]) * share).roundToInt() }
    }

    @Test
    fun theFrameCornerIsThePictureCornerPlusTheFrameWidth() {
        // Picture 6 dp, frame 10 dp: concentric (the frame's width is groove plus lip, 4 dp), at every density.
        assertEquals(6f, CollarDimens.coverBoxCorner.value, 0.001f)
        assertEquals(10f, CollarDimens.coverFrameCorner.value, 0.001f)
        assertEquals(CollarDimens.coverBoxCorner.value + CollarSize.Cover.sidePadding.value, CollarDimens.coverFrameCorner.value, 0.001f)
        for (d in listOf(1f, 1.5f, 2f, 2.625f)) {
            val density = Density(d)
            val outer = CollarSize.Cover.outerCorner(density, with(density) { CollarDimens.coverBoxCorner.toPx() }, Size(900f, 900f))
            assertEquals("density $d", 10f * d, outer, 0.01f)
        }
    }

    /**
     * PartsDark render: the lit groove's `0 0 12px` (sigma 4 dp) tints the lip above it from (62,65,70) at rest to (61,71,88); beside it (31,33,37) to (34,43,59),
     * both 1.17 dp from the groove; drawn at Depth.
     */
    @Test
    fun theLitGrooveGlowsOverTheLipOnGraphite() {
        val px = shot(PlumThemeSettings.Graphite)
        val page = intArrayOf(0x11, 0x12, 0x14)
        near("focused lip above the groove", glowedOver(px(80, 20), intArrayOf(62, 65, 70), intArrayOf(61, 71, 88), 1.17, 4.0), px(266, 20), 5)
        // The cover's lip is glass, its top the recipe's first step above the page (`SeatLook.coverGlass`), not the board's tone.
        val glassTop = SeatLook.of(Rgb(0x111214), Rgb(0xF2F0EA), true).coverGlass.lipStops[0]
        near("resting lip above the groove", intArrayOf((glassTop.red * 255).roundToInt(), (glassTop.green * 255).roundToInt(), (glassTop.blue * 255).roundToInt()), px(80, 20), 4)
        near("focused lip beside the groove", glowedOver(px(20, 100), intArrayOf(31, 33, 37), intArrayOf(34, 43, 59), 1.17, 4.0), px(156, 100), 4)
    }

    /**
     * PartsLight render: the lip above the lit groove (232,239,251; the lip's top is white at rest); the kit's accent glow (sigma 7.33 dp) 2.67 dp above the
     * collar (215,219,225) and 6.67 dp above it (224,224,221), over the page; drawn at Depth.
     */
    @Test
    fun paperGlowsWithTheKitsAccentGlowNotAHaze() {
        val px = shot(PlumThemeSettings.Paper)
        val page = intArrayOf(0xEC, 0xE8, 0xDF)
        near("focused lip above the groove", glowedOver(px(80, 20), intArrayOf(255, 255, 255), intArrayOf(232, 239, 251), 1.17, 4.0), px(266, 20), 5)
        near("4 px above the collar", glowed(page, page, intArrayOf(215, 219, 225), 2.67, 7.33), px(266, 14), 6)
        near("10 px above the collar", glowed(page, page, intArrayOf(224, 224, 221), 6.67, 7.33), px(266, 8), 4)
    }
}
