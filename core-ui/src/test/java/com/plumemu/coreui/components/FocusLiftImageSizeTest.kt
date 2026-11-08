package com.plumemu.coreui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The lift's light and shadow image does not depend on the cell's size: after the image of one cover exists, a cover of another height (a
 * grid size step) builds no new bitmap on the main thread, and what is drawn is what an image built for that very height would draw.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class FocusLiftImageSizeTest {
    private val ink = Color(0xFF1C1C1E)

    private fun draw(
        frame: Rect,
        corner: Float,
        dark: Boolean,
        shadow: Boolean = true,
    ): ImageBitmap =
        ImageBitmap(frame.right.toInt() + 40, frame.bottom.toInt() + 40).also { image ->
            val size = Size(image.width.toFloat(), image.height.toFloat())
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), size) {
                drawFocusLiftUnder(frame, corner, 1f, dark, ink, shadow)
            }
        }

    private fun frameOf(
        width: Float,
        height: Float,
    ) = Rect(Offset(30f, 30f), Size(width, height))

    @Test
    fun aSizeStepBuildsNoNewBitmap() {
        draw(frameOf(180f, 120f), 10f, dark = true)
        val before = SoftImages.dropsBuilds.get()
        for (height in listOf(100f, 150f, 200f, 260f)) draw(frameOf(180f, height), 10f, dark = true)
        for (width in listOf(150f, 240f)) draw(frameOf(width, 120f), 10f, dark = true)
        assertEquals("builds after the first cover's image", before, SoftImages.dropsBuilds.get())
    }

    @Test
    fun aTallerCoverIsDrawnAsAnImageBuiltForItsOwnHeightWould() {
        val failures = mutableListOf<String>()
        for (dark in listOf(true, false)) {
            for (corner in listOf(10f, 12f)) {
                for (height in listOf(70f, 100f, 137f, 200f, 261f)) {
                    val frame = frameOf(190f, height)
                    val sliced = draw(frame, corner, dark).toPixelMap()
                    val exact = exactDraw(frame, corner, dark).toPixelMap()
                    var worst = 0f; var at = ""
                    for (y in 0 until exact.height) {
                        for (x in 0 until exact.width) {
                            val a = sliced[x, y]
                            val b = exact[x, y]
                            val d = maxOf(abs(a.alpha - b.alpha), abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)); if (d > worst) { worst = d; at = "$x,$y a=${a.alpha} b=${b.alpha}" }
                        }
                    }
                    if (worst * 255f > 2.01f) failures += "dark=$dark corner=$corner height=$height: worst channel delta ${worst * 255f} at $at"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The same under-layer from one image built for exactly this box (no slicing of either axis). */
    private fun exactDraw(
        frame: Rect,
        corner: Float,
        dark: Boolean,
    ): ImageBitmap {
        val rim = com.plumemu.coreui.theme.FocusLift.rimWidth.value
        val w = Math.round(frame.width + 2 * rim)
        val h = Math.round(frame.height + 2 * rim)
        val depth = com.plumemu.coreui.theme.FocusLift.shadowColor(dark)
        val placed =
            checkNotNull(
                SoftImages.dropsBuild(
                    w,
                    h,
                    corner + rim,
                    SoftImages.Inset(depth, com.plumemu.coreui.theme.FocusLift.shadowDrop.value, com.plumemu.coreui.theme.FocusLift.shadowSigma.value),
                    SoftImages.Inset(depth.copy(alpha = 0f), 0f, com.plumemu.coreui.theme.FocusLift.shadowSigma.value),
                    SoftImages.Inset(com.plumemu.coreui.theme.FocusLift.glowColor(dark, ink), 0f, com.plumemu.coreui.theme.FocusLift.glowSigma.value),
                    Color.Transparent,
                    sliced = false,
                ),
            )
        return ImageBitmap(frame.right.toInt() + 40, frame.bottom.toInt() + 40).also { image ->
            val size = Size(image.width.toFloat(), image.height.toFloat())
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), size, drawExact(placed, frame, rim))
        }
    }

    private fun drawExact(
        placed: SoftImages.Placed,
        frame: Rect,
        rim: Float,
    ): DrawScope.() -> Unit = {
        drawImage(placed.image, dstOffset = androidx.compose.ui.unit.IntOffset(Math.round(frame.left - rim) + placed.dx, Math.round(frame.top - rim) + placed.dy))
    }
}
