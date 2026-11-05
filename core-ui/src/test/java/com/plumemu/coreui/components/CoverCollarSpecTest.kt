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
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

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

    @Test
    fun theLipIsRoundedNinePxRoundASixPxGroove() {
        // SPEC: lip radius 9 px = 6 dp, groove 6 px = 4 dp, art 4 px; the lip is not concentric with the groove.
        val density = Density(1.5f)
        assertEquals(9f, CollarSize.Cover.outerCorner(density, with(density) { CollarDimens.coverBoxCorner.toPx() }, Size(300f, 300f)), 0.01f)
        assertEquals(6f, (CollarDimens.coverBoxCorner + CollarSize.Cover.groove + CollarSize.Cover.lipCornerAdd).value, 0.01f)
    }

    /** PartsDark render: the lit groove's `0 0 12px` tints the lip above it from (62,65,70) at rest to (61,71,88); beside it (31,33,37) to (34,43,59). */
    @Test
    fun theLitGrooveGlowsOverTheLipOnGraphite() {
        val px = shot(PlumThemeSettings.Graphite)
        near("focused lip above the groove", intArrayOf(61, 71, 88), px(266, 20), 5)
        near("resting lip above the groove", intArrayOf(62, 65, 70), px(80, 20), 4)
        near("focused lip beside the groove", intArrayOf(34, 43, 59), px(156, 100), 4)
    }

    /** PartsLight render: the lip above the lit groove (232,239,251); the kit's accent glow 2.67 dp above the collar (215,219,225). */
    @Test
    fun paperGlowsWithTheKitsAccentGlowNotAHaze() {
        val px = shot(PlumThemeSettings.Paper)
        near("focused lip above the groove", intArrayOf(232, 239, 251), px(266, 20), 5)
        near("4 px above the collar", intArrayOf(215, 219, 225), px(266, 14), 6)
        near("10 px above the collar", intArrayOf(224, 224, 221), px(266, 8), 4)
    }
}
