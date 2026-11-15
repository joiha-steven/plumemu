package com.plumemu.coreui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PageGrainLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** [drawPageFade]: the plain gradient with grain off, the page's own grain through the same ramp with grain on. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PageGrainFadeTest {
    private val page = Color(0xFF161A24)
    private val width = 300
    private val height = 400

    private fun draw(block: DrawScope.() -> Unit) =
        ImageBitmap(width, height).also { image ->
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat()), block)
        }.toPixelMap()

    /** The gradient `ListEdgeFade` painted before the grain existed. */
    private fun DrawScope.todaysGradient(
        topLeft: Offset,
        size: Size,
        atBottom: Boolean,
    ) {
        val clear = page.copy(alpha = 0f)
        drawRect(
            Brush.verticalGradient(if (atBottom) listOf(clear, page) else listOf(page, clear), startY = topLeft.y, endY = topLeft.y + size.height),
            topLeft = topLeft,
            size = size,
        )
    }

    @Test
    fun withGrainOffItIsTodaysGradientPixelForPixel() {
        for (atBottom in listOf(true, false)) {
            val topLeft = Offset(0f, 120f)
            val size = Size(width.toFloat(), 90f)
            val before = draw { todaysGradient(topLeft, size, atBottom) }
            val now = draw { drawPageFade(page, PageGrain.Off, topLeft, size, atBottom) }
            for (y in 0 until height) for (x in 0 until width) assertEquals("($x, $y) atBottom=$atBottom", before[x, y], now[x, y])
        }
    }

    private fun tilePixel(
        amplitude: Int,
        x: Int,
        y: Int,
    ): Color {
        val side = PageGrainLook.TILE_SIDE
        return Color(pageGrainPixels(page.toArgb(), amplitude)[Math.floorMod(y, side) * side + Math.floorMod(x, side)])
    }

    private fun assertNear(
        message: String,
        want: Color,
        got: Color,
    ) {
        for ((w, g) in listOf(want.red to got.red, want.green to got.green, want.blue to got.blue)) assertTrue("$message: $want vs $got", abs(w - g) <= 1.5f / 255f)
    }

    private fun assertSolidEndIsTheTileAtBandCoordinates(
        grain: PageGrain,
        atBottom: Boolean,
        topLeft: Offset,
    ) {
        val size = Size(120f, 60f)
        val amplitude = grain.amplitudeFor(page)
        val shot = draw { drawPageFade(page, grain, topLeft, size, atBottom) }
        val solidRow = (if (atBottom) topLeft.y + size.height - 1 else topLeft.y).toInt()
        val clearRow = (if (atBottom) topLeft.y else topLeft.y + size.height - 1).toInt()
        for (x in topLeft.x.toInt() until (topLeft.x + size.width).toInt() step 7) {
            val got = shot[x, solidRow]
            assertTrue("alpha at the solid end of $grain: ${got.alpha}", got.alpha >= 0.99f)
            // The tile starts at the band's own top-left.
            assertNear("$grain at ($x, $solidRow)", tilePixel(amplitude, x - topLeft.x.toInt(), solidRow - topLeft.y.toInt()), got)
            assertTrue("clear end of $grain: ${shot[x, clearRow].alpha}", shot[x, clearRow].alpha <= 0.02f)
        }
        assertEquals("nothing outside the band", 0f, shot[topLeft.x.toInt() - 1, solidRow].alpha, 0f)
        assertEquals("nothing past the band", 0f, shot[topLeft.x.toInt(), (topLeft.y + size.height).toInt()].alpha, 0f)
    }

    @Test
    fun withGrainOnTheSolidEndIsTheTileAtBandLocalPixels() {
        for (grain in listOf(PageGrain.Light, PageGrain.Strong)) {
            for (atBottom in listOf(true, false)) {
                assertSolidEndIsTheTileAtBandCoordinates(grain, atBottom, Offset(13f, 70f))
            }
        }
    }

    @Test
    fun theRampIsTheSameAlphaRampAsTheGradientsAndFramesBuildNoShader() {
        val topLeft = Offset(0f, 100f)
        val size = Size(width.toFloat(), 80f)
        val plain = draw { todaysGradient(topLeft, size, atBottom = true) }
        val grained = draw { drawPageFade(page, PageGrain.Light, topLeft, size, true) }
        for (y in 100 until 180 step 9) assertEquals("alpha row $y", plain[10, y].alpha, grained[10, y].alpha, 2f / 255f)
        val built = PageGrainTile.fadesBuilt
        val tiles = PageGrainTile.built
        repeat(5) { draw { drawPageFade(page, PageGrain.Light, topLeft, size, true) } }
        assertEquals("a second draw reuses the shader", built, PageGrainTile.fadesBuilt)
        draw { drawPageFade(page, PageGrain.Light, Offset(37f, 215f), size, true) }
        assertEquals("a moved band reuses it", built, PageGrainTile.fadesBuilt)
        val short = Size(width.toFloat(), 50f)
        repeat(3) {
            draw { drawPageFade(page, PageGrain.Light, topLeft, short, true) }
            draw { drawPageFade(page, PageGrain.Light, topLeft, size, true) }
        }
        assertEquals("a reach going back and forth builds one more shader, once", built + 1, PageGrainTile.fadesBuilt)
        assertEquals("and no tile", tiles, PageGrainTile.built)
    }

    // A scrolling list's fade takes every height from nothing to its whole band, both ways, in both directions: after one pass no frame builds.
    @Test
    fun aScrollThroughEveryFadeHeightBuildsNothingOnItsSecondPass() {
        val image = ImageBitmap(8, 200)
        val canvas = Canvas(image)
        fun pass() {
            for (atBottom in listOf(true, false)) {
                for (h in (1..120) + (120 downTo 1)) {
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(8f, 200f)) {
                        drawPageFade(page, PageGrain.Strong, Offset(0f, 40f), Size(8f, h.toFloat()), atBottom)
                    }
                }
            }
        }
        pass()
        val built = PageGrainTile.fadesBuilt
        pass()
        assertEquals("the second pass builds no shader", built, PageGrainTile.fadesBuilt)
    }
}
