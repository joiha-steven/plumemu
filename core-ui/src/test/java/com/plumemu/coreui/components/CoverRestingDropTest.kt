package com.plumemu.coreui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
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

/** A resting cover stands raised off the page: its frame casts a drop under its foot, and none above its top. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class CoverRestingDropTest {
    private val density = 2f
    private val frameW = 160
    private val frameH = 200
    private val room = 40

    private fun lum(argb: Int): Int = ((argb shr 16 and 0xFF) + (argb shr 8 and 0xFF) + (argb and 0xFF))

    private fun check(set: PlumThemeSettings) {
        val c = PlumComposeColors.from(PlumColors.derive(set.backgroundColor, set.textMode, set.accentColor))
        val image = ImageBitmap(frameW + 2 * room, frameH + 2 * room)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(image.width.toFloat(), image.height.toFloat())) {
            drawRect(c.bg)
            val old = drawContext.size
            drawContext.transform.translate(room.toFloat(), room.toFloat())
            drawContext.size = Size(frameW.toFloat(), frameH.toFloat())
            drawCollar(c.collar, CollarSize.Cover, CollarDimens.coverBoxCorner.toPx())
            drawContext.size = old
        }
        val map = image.toPixelMap()
        val page = lum(c.bg.toArgb())
        fun at(x: Int, y: Int) = lum(map[room + x, room + y].toArgb())
        val midX = frameW / 2
        // Just under the frame's foot (1.5 dp, 3 dp): darker than the page, by more than a rounding step.
        for (dy in listOf(frameH + 3, frameH + 6)) {
            assertTrue("${set.background} under the foot at $dy: ${at(midX, dy)} vs page $page", page - at(midX, dy) >= 6)
        }
        // Beside the top, above the drop: the page itself.
        for (x in listOf(-4, frameW + 3, midX)) assertEquals("${set.background} above the top at $x", page, at(x, -12))
        // The drop is under the foot more than above the top (it falls, it does not glow round).
        assertTrue(page - at(midX, frameH + 4) > page - at(midX, -4))
    }

    @Test fun graphite() = check(PlumThemeSettings.Graphite)

    @Test fun paper() = check(PlumThemeSettings.Paper)
}
