package com.plumemu.coreui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Collections

/**
 * A slab of one look builds its shadow images once, whatever its width: a row in Settings, the system column and the games list differ only
 * in the straight run along x. A new image per width was a long main-thread frame the first time each screen opened.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SlabImagesShareTest {
    private val builds = Collections.synchronizedList(mutableListOf<String>())
    private val colors = PlumThemeSettings.Graphite.let { PlumComposeColors.from(PlumColors.derive(it.backgroundColor, it.textMode, it.accentColor)) }

    @Before
    fun watch() {
        ImageBuilding.clearCaches()
        ImageBuilding.watcher = { builds += it }
    }

    @After
    fun stop() {
        ImageBuilding.watcher = null
        ImageBuilding.clearCaches()
    }

    private fun drawSlab(width: Float) {
        val size = Size(width, HEIGHT)
        val outline = Outline.Rounded(RoundRect(0f, 0f, width, HEIGHT, CornerRadius(CORNER)))
        CanvasDrawScope().draw(Density(DENSITY), LayoutDirection.Ltr, Canvas(ImageBitmap(WIDE.toInt() + 400, 600)), size) {
            drawSlabOutside(outline, colors.slab, colors.slabGlow, under = colors.bg)
            drawSlabFill(outline, colors.slab, onCard = false)
        }
    }

    @Test
    fun threeWidthsOfOneLookBuildEachImageOnce() {
        drawSlab(WIDE)
        val first = builds.toList()
        drawSlab(WIDE - 120f)
        drawSlab(WIDE - 300f)
        assertEquals(first, builds.toList())
    }

    private companion object {
        const val DENSITY = 2.5f
        const val WIDE = 1000f
        const val HEIGHT = 117f
        const val CORNER = 33f
    }
}
