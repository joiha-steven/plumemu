package com.plumemu.coreui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CssShadow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A focus glow is the accent's colour, which a colour page changes at every strip step: its image is one white image tinted when drawn,
 * so a new accent builds nothing on the main thread (before, every colour built its own, 10 to 20 ms each on the emulator).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GlowTintImageTest {
    private val builds = mutableListOf<String>()

    @Before
    fun watch() {
        ImageBuilding.clearCaches()
        ImageBuilding.watcher = { builds += it }
    }

    @After
    fun stopWatching() {
        ImageBuilding.watcher = null
    }

    private fun drawGlow(color: Color): ImageBitmap =
        ImageBitmap(160, 160).also { image ->
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(160f, 160f)) {
                drawCssShadows(listOf(CssShadow(color, 0.dp, 12.dp, glow = true)), Offset(40f, 40f), Size(80f, 80f), 40f)
            }
        }

    @Test
    fun aNewGlowColourBuildsNoNewImage() {
        drawGlow(Color(0x40FF0000))
        val first = builds.size
        assertTrue("the first glow builds its image", first > 0)
        for (c in listOf(0x40FFA500, 0x40224488, 0x40FF0000 + 0x1234)) drawGlow(Color(c))
        assertEquals("images built after the first colour: $builds", first, builds.size)
    }

    @Test
    fun theGlowIsDrawnInItsOwnColourAndStrength() {
        for (color in listOf(Color(0xFFE53935), Color(0xFF1E88E5)).map { it.copy(alpha = 0.5f) }) {
            val pixels = drawGlow(color).toPixelMap()
            // Just outside the box's left edge: the glow is strongest there, its colour the glow's, its alpha at most the glow's.
            val p = pixels[38, 80]
            assertTrue("some glow shows: alpha ${p.alpha}", p.alpha > 0.1f && p.alpha <= color.alpha + 0.01f)
            val delta = maxOf(abs(p.red - color.red), abs(p.green - color.green), abs(p.blue - color.blue))
            assertTrue("colour ${p.red},${p.green},${p.blue} is the glow's ${color.red},${color.green},${color.blue}", delta < 0.03f)
        }
    }
}
