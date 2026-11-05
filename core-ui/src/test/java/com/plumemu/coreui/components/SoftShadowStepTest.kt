package com.plumemu.coreui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.RaisedCard
import org.junit.Assert.assertTrue
import org.junit.Test
import com.plumemu.coreui.theme.SlabLook
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A stacked shadow or glow must read smooth: along a vertical line through it, no two neighbouring pixels differ by more than
 * [MAX_JUMP] per channel, at 1x and 1.5x (the card's shadow showed steps at its bottom edge; the board's is smooth).
 * Each shadow is drawn over its theme's page, with no fill over it, so every pixel of the line is shadow.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SoftShadowStepTest {
    private var worstAt = 0

    private fun colours(s: PlumThemeSettings) = PlumComposeColors.from(PlumColors.derive(s.backgroundColor, s.textMode, s.accentColor))

    private fun worstJump(
        page: Color,
        density: Float,
        shape: IntRange,
        draw: DrawScope.() -> Unit,
    ): Int {
        val w = (420 * density).toInt()
        val h = (360 * density).toInt()
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawRect(page)
            draw()
        }
        val map = image.toPixelMap()
        val x = w / 2
        val from = ((shape.first - EDGE_DP) * density).toInt()
        val to = ((shape.last + EDGE_DP) * density).toInt()
        var worst = 0
        worstAt = 0
        for (shift in intArrayOf(0, 8, 16)) {
            val channel = IntArray(h) { (map[x, it].toArgb() shr shift) and 0xFF }
            // A true Gaussian is steep (a strong shadow falls 2 or 3 of 255 per pixel at 1x), so a step is a pixel whose change differs from
            // the mean change of its neighbours by more than [MAX_JUMP]; the shape's own edge region is skipped (a tight blur by design, hidden under the fill).
            for (y in 5 until h - 4) {
                if (y in from..to) continue
                val change = kotlin.math.abs(channel[y] - channel[y - 1])
                val around = (-3..3).filter { it != 0 }.map { kotlin.math.abs(channel[y + it] - channel[y + it - 1]) }.average()
                val jump = kotlin.math.abs(change - around).let { Math.ceil(it - 1e-9).toInt() }
                if (jump > worst) {
                    worst = jump
                    worstAt = y
                }
            }
        }
        return worst
    }

    private fun DrawScope.px(v: Int) = v.dp.toPx()

    private fun DrawScope.cardShadow(c: PlumComposeColors) {
        val card = c.card
        val box = Size(px(260), px(200))
        val topLeft = Offset(px(80), px(60))
        drawSoft(topLeft, box, CardDimens.corner.toPx(), CardDimens.shadowFarDrop.toPx(), CardDimens.shadowFarSigma.toPx(), card.shadow.copy(alpha = card.shadow.alpha * RaisedCard.SHADOW_FAR.toFloat()), true)
        drawSoft(topLeft, box, CardDimens.corner.toPx(), CardDimens.shadowNearDrop.toPx(), CardDimens.shadowNearSigma.toPx(), card.shadow.copy(alpha = card.shadow.alpha * RaisedCard.SHADOW_NEAR.toFloat()), true)
    }

    private fun DrawScope.slabShadow(c: PlumComposeColors) {
        val rect = RoundRect(px(60), px(150), px(360), px(197), px(13), px(13))
        drawSlabOutside(Outline.Rounded(rect), c.slab, c.slabGlow, under = c.bg)
    }

    private fun DrawScope.slabWithBlob(c: PlumComposeColors) {
        val rect = RoundRect(px(60), px(150), px(360), px(197), px(13), px(13))
        drawSlabOutside(Outline.Rounded(rect), c.slab, c.slabGlow, c.slabBlob, c.bg)
    }

    private fun DrawScope.slabBlobOnly(c: PlumComposeColors) {
        val rect = RoundRect(px(60), px(150), px(360), px(197), px(13), px(13))
        drawSlabOutside(Outline.Rounded(rect), SlabLook(c.slab.fill, c.slab.edge), Color.Transparent, blob = c.slabBlob)
    }

    private fun DrawScope.collarShadow(c: PlumComposeColors) {
        drawCollarShadow(c.collar, Offset(px(150), px(120)), Size(px(120), px(48)), px(24))
    }

    @Test
    fun noStackedShadowShowsAStepAt1xAnd15x() {
        val shadows: List<Pair<String, DrawScope.(PlumComposeColors) -> Unit>> =
            listOf("card" to { c -> cardShadow(c) }, "slab" to { c -> slabShadow(c) }, "slab with blob" to { c -> slabWithBlob(c) }, "slab blob" to { c -> slabBlobOnly(c) }, "collar" to { c -> collarShadow(c) })
        val rows = mapOf("card" to (60..260), "slab" to (150..197), "slab with blob" to (150..197), "slab blob" to (150..197), "collar" to (120..168))
        for (settings in listOf(PlumThemeSettings.Paper, PlumThemeSettings.Graphite)) {
            val c = colours(settings)
            for ((name, draw) in shadows) {
                for (density in floatArrayOf(1f, 1.5f)) {
                    val jump = worstJump(Color(settings.backgroundColor.toArgb() or 0xFF000000.toInt()), density, rows.getValue(name)) { draw(c) }
                    assertTrue("$name on ${settings.backgroundColor}: worst step $jump at row $worstAt (of ${(360 * density).toInt()}), ${density}x", jump <= MAX_JUMP)
                }
            }
        }
    }

    /** Alpha of the shade [d] dp outside a rounded rect's straight bottom edge and [d] dp outside its bottom-right arc along the diagonal, at [density]. */
    private fun edgeAndCornerShade(
        density: Float,
        corner: Float,
        d: Float,
        drawnCorner: Float = corner,
    ): Pair<Float, Float> {
        val w = (300 * density).toInt()
        val h = (240 * density).toInt()
        val image = ImageBitmap(w, h)
        val box = Size(120f * density, 80f * density)
        val topLeft = Offset(90f * density, 60f * density)
        val sigma = 6f * density
        val r = corner * density
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawSoft(topLeft, box, drawnCorner * density, 0f, sigma, Color.Black, true)
        }
        val map = image.toPixelMap()
        val alphaAt = { x: Float, y: Float -> map[Math.round(x), Math.round(y)].alpha }
        val bottom = topLeft.y + box.height
        val right = topLeft.x + box.width
        val straight = alphaAt(topLeft.x + box.width / 2, bottom + d * density)
        val arc = (r + d * density) / Math.sqrt(2.0).toFloat()
        val diagonal = alphaAt(right - r + arc, bottom - r + arc)
        return straight to diagonal
    }

    @Test
    fun shadowFollowsTheRoundedCornerNotASquare() {
        // A square corner shades the diagonal point well over the straight edge's value (it is inside the square); a true blur of the rounded shape is under it (down to about 0.6 at 8 dp).
        for (density in floatArrayOf(1f, 2f)) {
            for (d in floatArrayOf(2f, 4f, 8f)) {
                val (straight, diagonal) = edgeAndCornerShade(density, CardDimens.corner.value, d)
                val ratio = diagonal / straight
                assertTrue("diagonal/straight shade $ratio at $d dp, ${density}x (straight $straight, diagonal $diagonal)", ratio in 0.45f..1.05f)
                val (squareStraight, squareDiagonal) = edgeAndCornerShade(density, CardDimens.corner.value, d, 0f)
                assertTrue("a square corner must read as one: ${squareDiagonal / squareStraight}", squareDiagonal / squareStraight > 1.3f)
            }
        }
    }

    @Test
    fun coverHazeFollowsTheCollarsRoundedCorner() {
        // The haze round a focused cover on a light theme is the blur of the collar's rounded shape, not of its square box. With its wide blur
        // the two differ by a few per cent at the corner only, so the haze summed over the corner is compared with both.
        val density = 2f
        val box = Size(120f * density, 160f * density)
        val topLeft = Offset(90f * density, 70f * density)
        val corner = CollarDimens.coverBoxCorner.value * density
        val outer = corner + (CollarSize.Cover.groove.value + CollarSize.Cover.lip.value) * density
        val sigma = CollarDimens.coverLightGlowSigma.value * density
        val drawn = cornerHaze(box, topLeft, outer) { drawCoverLightGlow(CollarSize.Cover, corner, topLeft, box, 1f, Color.Black) }
        val rounded = cornerHaze(box, topLeft, outer) { drawSoft(topLeft, box, outer, 0f, sigma, Color.Black.copy(alpha = CollarDimens.COVER_LIGHT_GLOW_ALPHA), true) }
        val square = cornerHaze(box, topLeft, outer) { drawSoft(topLeft, box, 0f, 0f, sigma, Color.Black.copy(alpha = CollarDimens.COVER_LIGHT_GLOW_ALPHA), true) }
        assertTrue("square $square must differ from rounded $rounded", kotlin.math.abs(square - rounded) > 0.03f * rounded)
        assertTrue("drawn haze $drawn, rounded $rounded, square $square", kotlin.math.abs(drawn - rounded) < 0.015f * rounded)
    }

    /** The haze's alpha summed over the box's bottom-right corner, outside the collar's rounded shape of corner [outer]. */
    private fun cornerHaze(
        box: Size,
        topLeft: Offset,
        outer: Float,
        draw: DrawScope.() -> Unit,
    ): Float {
        val w = (box.width + 2 * topLeft.x).toInt()
        val h = (box.height + 2 * topLeft.y).toInt()
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) { draw() }
        val map = image.toPixelMap()
        val right = topLeft.x + box.width
        val bottom = topLeft.y + box.height
        val cx = right - outer
        val cy = bottom - outer
        var sum = 0f
        for (x in Math.round(cx) until Math.round(right + 3 * outer)) {
            for (y in Math.round(cy) until Math.round(bottom + 3 * outer)) {
                val px = x + 0.5f
                val py = y + 0.5f
                val inside = px < right && py < bottom && (px <= cx || py <= cy || (px - cx) * (px - cx) + (py - cy) * (py - cy) <= outer * outer)
                if (!inside) sum += map[x, y].alpha
            }
        }
        return sum
    }

    @Test
    fun cardCornerIsTheBoards() {
        // The board's card corner is 17.2 dp (25.8 px at 1.5x).
        assertTrue("card corner ${CardDimens.corner.value} dp", kotlin.math.abs(CardDimens.corner.value - 17.2f) <= 1f)
    }

    private companion object {
        const val MAX_JUMP = 2
        const val EDGE_DP = 8
    }
}
