package com.plumemu.home

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The titled tile (a picture still loading, or neither picture nor poster) stands on the drawn box front's field: the game's
 * system colour, light at the top left and dark at the bottom right, with grain; drawn once per size and only that image per frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TitledTileFieldTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val snes = PosterSpec("Super Metroid", "Super Nintendo", "SNES")

    @After
    fun clearProbe() {
        boxFrontProbe = null
    }

    private var tintState = mutableStateOf(emptyMap<String, Color>())
    private var specState = mutableStateOf<PosterSpec?>(null)

    private fun show(
        spec: PosterSpec?,
        tints: Map<String, Color> = mapOf("SNES" to Color(0xFF7B5EA7)),
        frame: androidx.compose.runtime.MutableIntState? = null,
        size: DpSize = DpSize(120.dp, 160.dp),
    ) {
        tintState = mutableStateOf(tints)
        specState = mutableStateOf(spec)
        compose.setContent {
            CompositionLocalProvider(LocalSystemTints provides tintState) {
                PlumTheme(PlumThemeSettings.Graphite) {
                    CoverArt(
                        title = "Super Metroid",
                        picture = null,
                        size = size,
                        alignment = Alignment.Center,
                        corner = 3.dp,
                        titlePadding = 10.dp,
                        modifier = Modifier.testTag("tile").then(if (frame == null) Modifier else Modifier.drawBehind { frame.intValue }),
                        subtitle = "1994",
                        tileSystem = specState.value,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tile(): ImageBitmap = compose.onNodeWithTag("tile").captureToImage()

    /** The mean colour of the block at ([x0], [y0]) of [side] px, away from the text. */
    private fun mean(image: ImageBitmap, x0: Int, y0: Int, side: Int): Color {
        val map = image.toPixelMap()
        var r = 0.0
        var g = 0.0
        var b = 0.0
        for (x in x0 until x0 + side) for (y in y0 until y0 + side) {
            val c = map[x, y].toArgb()
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        val n = side * side
        return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /** Hue (0..1), saturation, value of [c]. */
    private fun hsv(c: Color): Triple<Float, Float, Float> {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), out)
        return Triple(out[0] / 360f, out[1], out[2])
    }

    private fun topLeft(image: ImageBitmap) = mean(image, 6, 6, 8)

    @Test
    fun aSecondFrameBuildsNothingAndDrawsOneImage() {
        val probe = BoxFrontProbe().also { boxFrontProbe = it }
        val frame = mutableIntStateOf(0)
        show(snes, frame = frame)
        tile()
        assertEquals("one field built", 1, probe.builds)
        assertEquals("one image drawn", 1, probe.imageDraws)
        for (n in 1..3) {
            frame.intValue = n
            compose.waitForIdle()
            tile()
            assertEquals("frame $n builds nothing", 1, probe.builds)
            // The tile's clip is its own layer, which keeps the drawn image: a redraw outside it draws at most the one image again.
            assertTrue("frame $n draws at most one image", probe.imageDraws in 1..1 + n)
        }
    }

    @Test
    fun theTileIsTheSystemsFieldNotFlatGrey() {
        show(snes)
        val image = tile()
        val top = hsv(topLeft(image))
        assertTrue("saturated: ${top.second}", top.second >= 0.4)
        // The purple tint's hue (about 0.74) turned at most 25 degrees either way.
        val hueGap = kotlin.math.abs(top.first - hsv(Color(0xFF7B5EA7)).first)
        assertTrue("hue stays with the system: $hueGap", hueGap <= 26f / 360 + 0.02f)
        val bottom = mean(image, image.width - 16, image.height - 16, 8)
        assertTrue("lighter at the top left: ${topLeft(image).luminance()} vs ${bottom.luminance()}", topLeft(image).luminance() > bottom.luminance() * 1.5)
    }

    @Test
    fun aDifferentSystemHasAnotherField() {
        show(snes)
        val purple = topLeft(tile())
        tintState.value = mapOf("GB" to Color(0xFF3D9B4A))
        specState.value = PosterSpec("Super Metroid", "Game Boy", "GB")
        compose.waitForIdle()
        assertNotEquals(purple, topLeft(tile()))
    }

    @Test
    fun noSystemTakesANeutralFieldOfTheSameRecipe() {
        show(null)
        val image = tile()
        assertTrue("grey stays grey: ${hsv(topLeft(image)).second}", hsv(topLeft(image)).second < 0.2)
        assertTrue("still light to dark", topLeft(image).luminance() > mean(image, image.width - 16, image.height - 16, 8).luminance())
    }

    @Test
    fun theTitleKeepsItsTagOnTheTile() {
        show(snes)
        compose.onNodeWithTag(HomeTags.PORTRAIT_TILE_TITLE).assertExists()
    }
}
