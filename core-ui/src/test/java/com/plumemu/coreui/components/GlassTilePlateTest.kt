package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A second-screen tile is the top screen's focused-row plate: one flat face, the slab's
 * fill, the same at its top and its foot (no fade, no foot shade), no line of any kind ("lifted card": a lighter flat face and one soft shadow) and a shadow under it, on a dark, a light and the yellow set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w400dp-h300dp-mdpi")
class GlassTilePlateTest {
    @get:Rule
    val compose = createComposeRule()

    private fun themeOf(set: ColorSet) = PlumThemeSettings(background = set.background, text = set.textMode.color, accent = set.accent)

    @Test
    fun theTileIsTheSlabPlateOnEverySet() {
        val failures = mutableListOf<String>()
        var settings by mutableStateOf(PlumThemeSettings.Graphite)
        var colors: PlumComposeColors? = null
        compose.setContent {
            PlumTheme(settings) {
                val c = PlumTheme.colors
                colors = c
                Box(Modifier.size(240.dp, 120.dp).drawBehind { drawRect(c.bg) }.testTag(ROOT)) {
                    Box(Modifier.padding(20.dp).size(200.dp, 60.dp).glassTile())
                }
            }
        }
        for ((name, set) in listOf("Graphite" to PlumThemeSettings.Graphite, "Paper" to PlumThemeSettings.Paper, "Banana" to themeOf(ColorSet.Banana))) {
            settings = set
            compose.waitForIdle()
            val map = compose.onNodeWithTag(ROOT).captureToImage().toPixelMap()
            val c = colors!!
            val fill = c.slab.fill
            // The very top and foot rows count too: no lit line, no foot band; the face is one colour from edge to edge.
            for (y in listOf(20, 21, 24, 50, 76, 78, 79)) {
                if (!near(map[120, y], fill)) failures += "$name row $y is ${hex(map[120, y])}, the slab's face ${hex(fill)}"
            }
            // The plate stands up: its foot casts a shadow on the page just under it.
            val under = map[120, 82]
            if (lum(under) >= lum(c.bg) - 0.002f) failures += "$name: no shadow under the tile (${hex(under)} on ${hex(c.bg)})"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun near(
        a: Color,
        b: Color,
    ) = abs(a.red - b.red) * 255 <= 2.5f && abs(a.green - b.green) * 255 <= 2.5f && abs(a.blue - b.blue) * 255 <= 2.5f

    private fun lum(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    private companion object {
        const val ROOT = "root"
    }
}
