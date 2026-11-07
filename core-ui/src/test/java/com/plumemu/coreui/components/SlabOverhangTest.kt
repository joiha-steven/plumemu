package com.plumemu.coreui.components

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.rememberListGlide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The focused slab is 46.67 dp tall on a 38 or 40 dp row, drawn past the row above and below, and a list that clips to its bounds (`ListGlide`) does not
 * cut it at its first and last row. Its height is measured between its own edges, not by colour, so the lift around it does not count as slab.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SlabOverhangTest {
    @get:Rule
    val compose = createComposeRule()

    private val settings = PlumThemeSettings.Graphite
    private val colors = PlumComposeColors.from(PlumColors.derive(settings.backgroundColor, settings.textMode, settings.accentColor))

    private fun Color.near(other: Color) = abs(red - other.red) < 0.02f && abs(green - other.green) < 0.02f && abs(blue - other.blue) < 0.02f

    /** One focused row of [rowDp] in a 20 dp margin, optionally inside a list's clip; returns the rows of pixels down the middle. */
    private fun column(rowDp: Int, clipped: Boolean): List<Color> {
        // Motion off: no extra rows, so the list's own clip is the only one in the way.
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.setContent {
            PlumTheme(settings) {
                val glide = rememberListGlide(stride = rowDp.dp)
                Box(Modifier.testTag(BOX).background(colors.bg).padding(20.dp)) {
                    val rowClip = if (clipped) glide.modifier else Modifier
                    Box(Modifier.fillMaxWidth().height(rowDp.dp).then(rowClip)) {
                        // A list host pads its content by the glide's room, as its layout shifts the content up by it.
                        Box(Modifier.padding(vertical = if (clipped) glide.extra else 0.dp).fillMaxWidth().height(rowDp.dp).focusPill(true))
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = compose.onNodeWithTag(BOX).captureToImage().toPixelMap()
        return List(image.height) { image[image.width / 2, it] }
    }

    /**
     * The slab's first row and the first row below it, down the middle. The lift around it (light on Graphite) is as bright as its fill, so rows are not
     * told apart by colour: the slab is where the rim and fill start and end, the sharpest steps of the column in its upper and lower half.
     */
    private fun slabEdges(pixels: List<Color>): Pair<Int, Int> {
        val red = pixels.map { it.red }
        val mid = red.size / 2
        val top = (1 until mid).maxByOrNull { red[it] - red[it - 1] }!!
        val bottom = (mid until red.size).maxByOrNull { red[it - 1] - red[it] }!!
        return top to bottom
    }

    private fun slabRows(pixels: List<Color>): Int = slabEdges(pixels).let { it.second - it.first }

    @Test
    fun aSlabOnA38DpRowIs46Point7DpTall() = checkHeight(38)

    @Test
    fun aSlabOnA40DpRowIs46Point7DpTall() = checkHeight(40)

    private fun checkHeight(row: Int) {
        val density = compose.density.density
        val drawn = slabRows(column(row, clipped = false))
        assertEquals("row $row: the slab is ${SlabDimens.height.value} dp tall", SlabDimens.height.value * density, drawn.toFloat(), 3f * density)
    }

    @Test
    fun aClippedListDoesNotCutTheSlabAtItsFirstAndLastRow() {
        val density = compose.density.density
        val pixels = column(38, clipped = true)
        val drawn = slabRows(pixels)
        assertEquals("the whole slab shows through the list's clip", SlabDimens.height.value * density, drawn.toFloat(), 3f * density)
        // And the row layout did not move: the slab starts above the row's top (20 dp margin).
        val firstSlab = slabEdges(pixels).first
        assertTrue("the slab begins above the row", firstSlab < 20 * density)
    }

    private companion object {
        const val BOX = "box"
    }
}
