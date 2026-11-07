package com.plumemu.coreui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.Depth
import com.plumemu.coreui.theme.CollarLook
import com.plumemu.coreui.theme.ColorMath
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.TextMode
import com.plumemu.coreui.theme.toComposeColor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A rendered button seat (LIP, WELL, CAP) at the board's 1.5 px per dp, read back pixel by pixel: the cap's middle is its gradient's mean, the
 * thickness under it is the solid side colour, the groove is the well, nothing of an outer shadow shows inside a box, and the drop under the
 * lip is the board's soft dark (Graphite and Paper).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SeatPixelTest {
    private val density = 1.5f

    private fun render(
        page: Color,
        w: Int,
        h: Int,
        draw: DrawScope.() -> Unit,
    ): PixelMap {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawRect(page)
            draw()
        }
        return image.toPixelMap()
    }

    private fun near(what: String, want: Color, got: Color, tol: Int) {
        val a = want.toArgb()
        val b = got.toArgb()
        for (s in intArrayOf(16, 8, 0)) {
            val d = kotlin.math.abs(((a shr s) and 0xFF) - ((b shr s) and 0xFF))
            assertTrue("%s: want #%06X got #%06X".format(what, a and 0xFFFFFF, b and 0xFFFFFF), d <= tol)
        }
    }

    private fun check(bg: Int, mode: TextMode, theme: String) {
        val colors = PlumComposeColors.from(PlumColors.derive(Rgb(bg), mode))
        val seat = colors.collar.seat
        // A button: 120 x 52.67 dp at 1.5 = 180 x 79 px, placed 30 px in; the cap is 57 px high inside the lip (the board's 8 px, at Depth.LIP) + groove 2.
        val x0 = 30f
        val y0 = 20f
        val map =
            render(colors.bg, 240, 140) {
                translate(x0, y0) {
                    val s = Size(180f, 79f)
                    drawSeatInto(colors, s)
                }
            }
        val cx = (x0 + 90).toInt()
        val capTop = kotlin.math.round(8f * Depth.LIP + 2f) // the seat's rows are whole pixels
        near("$theme cap middle", lerp(seat.capStops[0], seat.capStops[1], 0.5f), map[cx, (y0 + capTop + 28).toInt()], 3)
        // The thickness (the board's 2 px at Depth.THICKNESS) is under a pixel here and lands on one whole row: the side's own colour.
        near("$theme thickness", seat.side, map[cx, (y0 + capTop + 57).toInt()], 3)
        // The groove ring beside a resting cap at mid height, darkest pixel (the cap's drop and the well's inset over it), at the board's own scale.
        if (theme == "Paper") {
            val row = (x0.toInt() + 5..x0.toInt() + 12).map { map[it, (y0 + 40).toInt()] }.minBy { it.red + it.green + it.blue }
            // The ring is the page 0.185 toward the brown at Depth.RAISE; what the board has darker than that is the cap's soft drop and the well's inset,
            // which are soft shadows here: SHADOW of their strength over SHADOW_REACH of their reach.
            val ringBoard = ColorMath.mix(Rgb(bg), CollarLook.SHADE_BROWN, 0.185).toComposeColor()
            val ring = ColorMath.mix(Rgb(bg), CollarLook.SHADE_BROWN, 0.185 * Depth.RAISE).toComposeColor()
            val soft = Depth.SHADOW * Depth.SHADOW_REACH
            val want = Color(ring.red + (0xC4 / 255f - ringBoard.red) * soft, ring.green + (0xBB / 255f - ringBoard.green) * soft, ring.blue + (0xAF / 255f - ringBoard.blue) * soft)
            near("Paper groove ring at rest (board #C4BBAF at Depth)", want, row, 2)
        }
        // A round hint key B (cap 34 x 31 px in lip 4 + groove 1, 5 + 2 below: 44 x 43 px), the darkest ring pixel at mid height. Its groove is
        // one pixel wide, so the browser's and Skia's edge coverage differ by up to 3 in blue; every layer is pinned exactly by SeatLookTest.
        if (theme == "Paper") {
            val hint = CollarSize.Hint
            val key =
                render(colors.bg, 120, 100) {
                    translate(x0, y0) {
                        inset(0f, 0f, size.width - 44f, size.height - 43f) {
                            drawCollar(colors.collar, hint, 31f / 2f)
                            val l = (hint.lip + hint.groove).toPx()
                            drawCssCap(seat.hintCap, Offset(l, l), Size(34f, 31f))
                        }
                    }
                }
            val ring = (x0.toInt() + 2..x0.toInt() + 7).map { key[it, (y0 + 20).toInt()] }.minBy { it.red + it.green + it.blue }
            // Its 1 px groove is mostly edge pixels, so the darkest one is the lip's and groove's own tones (RAISE), not the cap's soft drop.
            val hintRing = ColorMath.mix(Rgb(bg), CollarLook.SHADE_BROWN, 0.185 * Depth.RAISE).toComposeColor()
            val hintBoardRing = ColorMath.mix(Rgb(bg), CollarLook.SHADE_BROWN, 0.185).toComposeColor()
            val hintWant = Color(hintRing.red + (0xC4 / 255f - hintBoardRing.red) * Depth.RAISE, hintRing.green + (0xBA / 255f - hintBoardRing.green) * Depth.RAISE, hintRing.blue + (0xAD / 255f - hintBoardRing.blue) * Depth.RAISE)
            near("Paper hint ring at rest (board #C4BAAD at Depth)", hintWant, ring, 3)
        }
        // The groove alone (no cap in it): its middle is the well, its inset shadow only along the top.
        val bare = render(colors.bg, 240, 140) { translate(x0, y0) { inset(0f, 0f, size.width - 180f, size.height - 79f) { drawCollar(colors.collar, CollarSize.Control, 57f / 2f) } } }
        near("$theme groove", seat.well, bare[cx, (y0 + 45).toInt()], 2)
        // Under the lip: the soft dark of `0 3px 4px` and `0 10px 22px` over the page; darker than the page, never as dark as the shade's peak.
        val under = map[cx, (y0 + 79 + 2).toInt()]
        val pageLum = colors.bg.red + colors.bg.green + colors.bg.blue
        val underLum = under.red + under.green + under.blue
        assertTrue("$theme shadow under the lip ($underLum vs page $pageLum)", underLum < pageLum - 0.02f)
        val darkest = seat.shade.copy(alpha = 0.45f).compositeOver(colors.bg)
        assertTrue("$theme shadow lighter than its peak", underLum > darkest.red + darkest.green + darkest.blue)
    }

    private fun DrawScope.drawSeatInto(
        colors: PlumComposeColors,
        s: Size,
    ) {
        val pad = CollarSize.Control
        // drawCollar fills its DrawScope's size: draw it into a sub-scope of the seat's size.
        inset(0f, 0f, size.width - s.width, size.height - s.height) {
            drawCollar(colors.collar, pad, (57f) / 2f, accent = colors.accent, glow = colors.partGlow)
            drawCssCap(colors.collar.seat.buttonCap, Offset(pad.lip.toPx() + pad.groove.toPx(), pad.lip.toPx() + pad.groove.toPx()), Size(s.width - 2 * (pad.lip + pad.groove).toPx(), 57f))
        }
    }


    @Test
    fun graphiteSeat() = check(0x111214, TextMode.Light, "Graphite")

    @Test
    fun paperSeat() = check(0xECE8DF, TextMode.Dark, "Paper")
}
