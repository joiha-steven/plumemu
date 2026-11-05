package com.plumemu.coreui.components

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The games list's focused slab on Graphite is the one slab (the Settings board's, page #111214, glow `0 0 22px` accent 0.3, accent #3E7BE0), pixel
 * for pixel against Chrome's render of the board at 1.5 px per dp, per channel over the page, within [TOLERANCE]: Chrome draws the far drop wider and
 * darker than its CSS Gaussian (`FocusSlab.FAR_DARK_RENDERED`), so the glow fades to 2 of 255 at top 11.3, sides 10.7 dp, and below the drops
 * darken the page for 8 dp and on. Ours reaches top 12, sides 10.7 dp; the CSS Gaussians alone were 3 lighter there and reached top 15, sides 13 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SlabListHaloTest {
    private val density = 1.5f

    private fun deltas(): Map<String, Pair<IntArray, List<IntArray>>> {
        val s = PlumThemeSettings.Graphite
        val c = PlumComposeColors.from(PlumColors.derive(s.backgroundColor, s.textMode, s.accentColor))
        val page = Color(s.backgroundColor.toArgb() or 0xFF000000.toInt())
        val w = (520 * density).toInt()
        val h = (260 * density).toInt()
        val image = ImageBitmap(w, h)
        val left = 56f
        val top = 100f
        val right = left + 408f
        val bottom = top + 46.67f
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawRect(page)
            val rect = RoundRect(left.dp.toPx(), top.dp.toPx(), right.dp.toPx(), bottom.dp.toPx(), 13.33f.dp.toPx(), 13.33f.dp.toPx())
            drawSlabOutside(Outline.Rounded(rect), c.slab, c.slabGlow, under = c.bg)
        }
        val map = image.toPixelMap()
        val bg = IntArray(3) { (page.toArgb() shr (16 - 8 * it)) and 0xFF }
        fun at(
            x: Int,
            y: Int,
        ) = IntArray(3) { ((map[x, y].toArgb() shr (16 - 8 * it)) and 0xFF) - bg[it] }
        val cx = ((left + right) / 2 * density).toInt()
        val cy = ((top + bottom) / 2 * density).toInt()
        val t = (top * density).toInt()
        val b = (bottom * density).toInt()
        val l = (left * density).toInt()
        val r = (right * density).toInt()
        val steps = (24 * density).toInt()
        return mapOf(
            "top" to (at(cx, t - 1) to List(steps) { at(cx, t - 1 - it) }),
            "bottom" to (at(cx, b) to List(steps) { at(cx, b + it) }),
            "left" to (at(l - 1, cy) to List(steps) { at(l - 1 - it, cy) }),
            "right" to (at(r, cy) to List(steps) { at(r + it, cy) }),
        )
    }

    private fun near(
        side: String,
        line: List<IntArray>,
        board: List<IntArray>,
    ) {
        for ((i, expected) in board.withIndex()) {
            val px = line[i + 1]
            for (ch in 0..2) assertEquals("$side px ${i + 1} channel $ch: ${px.toList()} vs ${expected.toList()}", expected[ch].toDouble(), px[ch].toDouble(), TOLERANCE)
        }
    }

    @Test
    fun theGlowMatchesTheBoardAboveAndAtTheSides() {
        val d = deltas()
        near("top", d.getValue("top").second, BOARD_TOP)
        near("left", d.getValue("left").second, BOARD_SIDE)
        near("right", d.getValue("right").second, BOARD_SIDE)
    }

    @Test
    fun belowTheSlabTheDropsDarkenThePage() {
        val (_, line) = deltas().getValue("bottom")
        near("bottom", line, BOARD_BOTTOM)
        for (i in 1 until (16 * density).toInt()) assertTrue("darker than the page ${i / density} dp below: ${line[i].toList()}", line[i][0] < 0)
    }

    @Test
    fun theGlowEndsWhereTheBoardsDoes() {
        val d = deltas()
        // The last pixel whose blue is at least 2 over the page, against the board's (top 17 px = 11.3 dp, sides 16 px = 10.7 dp). Above, ours
        // ends 2 px (1.3 dp) later: Chrome's glow tail there is shorter than any one Gaussian that also keeps the first 10 dp within 2 levels.
        fun reach(line: List<IntArray>) = line.indexOfLast { it[2] >= 2 }
        assertEquals("top", reach(listOf(IntArray(3)) + BOARD_TOP).toDouble(), reach(d.getValue("top").second).toDouble(), 2.0)
        assertEquals("left", reach(listOf(IntArray(3)) + BOARD_SIDE).toDouble(), reach(d.getValue("left").second).toDouble(), 1.0)
        assertEquals("right", reach(listOf(IntArray(3)) + BOARD_SIDE).toDouble(), reach(d.getValue("right").second).toDouble(), 1.0)
    }

    @Test
    fun theGlowIsClippedNoCloserThanItsReach() {
        assertTrue(SlabDimens.glowReach.value >= 11.3f)
    }

    private companion object {
        /** Levels of 255 per channel; Chrome's own rounding leaves a few pixels 2 off any smooth profile. */
        const val TOLERANCE = 2.0

        // PartsDark (the same slab as the Settings kit board) at 1.5x, over its page (17, 18, 20): from the slab's edge outwards, its first pixel
        // (the ring) left out; out to where the glow's blue falls under 2 (top, sides) and 8 dp below.
        val BOARD_TOP =
            listOf(
                intArrayOf(2, 10, 22), intArrayOf(1, 9, 21), intArrayOf(2, 8, 19), intArrayOf(3, 8, 18), intArrayOf(2, 8, 17), intArrayOf(2, 7, 15), intArrayOf(1, 6, 14),
                intArrayOf(1, 5, 12), intArrayOf(1, 4, 10), intArrayOf(1, 4, 9), intArrayOf(0, 3, 8), intArrayOf(0, 2, 6), intArrayOf(0, 2, 5), intArrayOf(-1, 1, 4),
                intArrayOf(-1, 0, 3), intArrayOf(-1, 0, 2), intArrayOf(-1, 0, 1),
            )
        val BOARD_SIDE =
            listOf(
                intArrayOf(-2, 4, 13), intArrayOf(-2, 5, 13), intArrayOf(0, 4, 13), intArrayOf(0, 5, 13), intArrayOf(-1, 4, 11), intArrayOf(-1, 3, 10), intArrayOf(-1, 4, 10),
                intArrayOf(-1, 3, 8), intArrayOf(0, 3, 7), intArrayOf(0, 2, 7), intArrayOf(-1, 2, 5), intArrayOf(-1, 1, 4), intArrayOf(-1, 0, 4), intArrayOf(-2, 0, 3),
                intArrayOf(-1, -1, 2), intArrayOf(-1, 0, 1),
            )
        val BOARD_BOTTOM =
            listOf(
                intArrayOf(-8, -4, 2), intArrayOf(-7, -3, 2), intArrayOf(-6, -3, 2), intArrayOf(-6, -3, 3), intArrayOf(-6, -2, 3), intArrayOf(-5, -2, 3), intArrayOf(-5, -2, 3),
                intArrayOf(-5, -3, 1), intArrayOf(-5, -3, 0), intArrayOf(-4, -2, 1), intArrayOf(-4, -3, 1), intArrayOf(-4, -3, -1),
            )
    }
}
