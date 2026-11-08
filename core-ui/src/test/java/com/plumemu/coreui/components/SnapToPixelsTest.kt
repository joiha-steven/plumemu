package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [snapToPixels] puts its content on whole pixels and keeps doing so when an ancestor's layer moves to another fractional rest (the strip slides, a list glides).
 * A black block on a white page is read back: a block on whole pixels has no grey pixel on its edges, one between pixels has.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SnapToPixelsTest {
    @get:Rule
    val compose = createComposeRule()

    private var shiftX by mutableFloatStateOf(0f)
    private var shiftY by mutableFloatStateOf(0f)
    private var snapOn by mutableFloatStateOf(1f)

    private fun setUp(snap: Boolean) {
        snapOn = if (snap) 1f else 0f
        compose.setContent {
            Box(Modifier.testTag("page").size(100.dp).background(Color.White)) {
                // The strip: its translation is animated state read in the layer block, so it moves without a layout pass.
                Box(Modifier.fillMaxSize().graphicsLayer { translationX = shiftX; translationY = shiftY }) {
                    Box(Modifier.size(100.dp).snapToPixels { snapOn > 0f }.drawBehind { drawRect(Color.Black, size = size.copy(width = 120f, height = 40f)) })
                }
            }
        }
        compose.waitForIdle()
    }

    /** Grey pixels (neither black nor white) on the block's edges, from the row and the column through its middle. */
    private fun greyPixels(): Int {
        val map = compose.onNodeWithTag("page").captureToImage().toPixelMap()
        fun grey(c: Color) = c.red > 0.02f && c.red < 0.98f
        var n = 0
        for (x in 0 until map.width) if (grey(map[x, 20])) n++
        for (y in 0 until map.height) if (grey(map[20, y])) n++
        return n
    }

    private val rests = listOf(0.3f to 0.6f, -412.8f to 0.45f, 0.5f to 0.25f, -0.9f to 0f, 37.7f to -0.35f, 0f to 0f)

    @Test fun contentStaysOnWholePixelsAtEveryRest() {
        setUp(snap = true)
        for ((x, y) in rests) {
            compose.runOnIdle { shiftX = x; shiftY = y }
            compose.waitForIdle()
            assertEquals("grey edge pixels after the ancestor moved to ($x, $y)", 0, greyPixels())
        }
    }

    // Guard: without the snap the same rests leave grey edges, so the check above can fail.
    @Test fun withoutSnapEdgesAreGrey() {
        setUp(snap = false)
        compose.runOnIdle { shiftX = 0.3f; shiftY = 0.6f }
        compose.waitForIdle()
        assertTrue("an unsnapped block at a fractional rest should show grey edges", greyPixels() > 0)
    }
}
