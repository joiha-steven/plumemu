package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [plainLayer] draws as `graphicsLayer` does (alpha, translation, scale, clip) but adds no semantics node, where `graphicsLayer`
 * adds one: the accessibility pass walks every node while a service is on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w320dp-h160dp-land-mdpi")
class PlainLayerTest {
    @get:Rule
    val compose = createComposeRule()

    private val alpha = mutableFloatStateOf(0.4f)

    private val look: GraphicsLayerScope.() -> Unit = {
        this.alpha = this@PlainLayerTest.alpha.floatValue
        compositingStrategy = CompositingStrategy.ModulateAlpha
        translationX = 7f
        translationY = 3f
        scaleX = 0.9f
        clip = true
        shape = RectangleShape
    }

    private fun count(node: SemanticsNode): Int = 1 + node.children.sumOf(::count)

    private fun showBoth() {
        compose.setContent {
            Row {
                Box(Modifier.testTag("layer").size(100.dp).drawBehind { drawRect(Color.White) }) {
                    Box(Modifier.size(80.dp).graphicsLayer(look).drawBehind { drawRect(Color.Blue) })
                }
                Box(Modifier.testTag("plain").size(100.dp).drawBehind { drawRect(Color.White) }) {
                    Box(Modifier.size(80.dp).plainLayer(look).drawBehind { drawRect(Color.Blue) })
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun drawsWhatGraphicsLayerDrawsAndFollowsItsState() {
        showBoth()
        for (value in listOf(0.4f, 0.9f)) {
            alpha.floatValue = value
            compose.waitForIdle()
            val layer = compose.onNodeWithTag("layer").captureToImage().toPixelMap()
            val plain = compose.onNodeWithTag("plain").captureToImage().toPixelMap()
            var differing = 0
            for (x in 0 until layer.width) for (y in 0 until layer.height) if (layer[x, y] != plain[x, y]) differing++
            assertEquals("alpha $value: pixels that differ from graphicsLayer's", 0, differing)
        }
        // Not vacuous: the blue box really is drawn, faded.
        val centre = compose.onNodeWithTag("plain").captureToImage().toPixelMap().let { it[50, 50] }
        assertEquals("the faded box shows", true, centre.blue > centre.red)
    }

    /** A new block (a recomposition with another shift, as the Apps grid's spread does) moves the layer as graphicsLayer's does. */
    @Test
    fun aNewBlockIsAppliedAsGraphicsLayerAppliesIt() {
        val shift = mutableFloatStateOf(4f)
        compose.setContent {
            val by = shift.floatValue
            Row {
                Box(Modifier.testTag("layer").size(100.dp).drawBehind { drawRect(Color.White) }) {
                    Box(Modifier.size(60.dp).graphicsLayer { translationX = by }.drawBehind { drawRect(Color.Blue) })
                }
                Box(Modifier.testTag("plain").size(100.dp).drawBehind { drawRect(Color.White) }) {
                    Box(Modifier.size(60.dp).plainLayer { translationX = by }.drawBehind { drawRect(Color.Blue) })
                }
            }
        }
        for (value in listOf(4f, 30f)) {
            shift.floatValue = value
            compose.waitForIdle()
            val layer = compose.onNodeWithTag("layer").captureToImage().toPixelMap()
            val plain = compose.onNodeWithTag("plain").captureToImage().toPixelMap()
            var differing = 0
            for (x in 0 until layer.width) for (y in 0 until layer.height) if (layer[x, y] != plain[x, y]) differing++
            assertEquals("shift $value: pixels that differ from graphicsLayer's", 0, differing)
            // Not vacuous: the box's left edge moved with the shift.
            assertEquals("shift $value: the box starts at its shift", Color.Blue, plain[value.toInt() + 1, 30])
        }
    }

    /** A tap lands where the layer moved its content, as with graphicsLayer (the Apps grid's cells carry their touch target). */
    @Test
    fun aTapFollowsTheLayersTranslation() {
        var tapped = 0
        compose.setContent {
            Box(Modifier.testTag("area").size(200.dp)) {
                Box(Modifier.size(40.dp).plainLayer { translationX = 120f }.clickable { tapped++ })
            }
        }
        compose.onNodeWithTag("area").performTouchInput { click(Offset(20f, 20f)) }
        assertEquals("a tap where the box was before its shift", 0, tapped)
        compose.onNodeWithTag("area").performTouchInput { click(Offset(140f, 20f)) }
        assertEquals("a tap where the box is drawn", 1, tapped)
    }

    @Test
    fun addsNoSemanticsNodeWhereGraphicsLayerAddsOne() {
        showBoth()
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val layer = compose.onNodeWithTag("layer", useUnmergedTree = true).fetchSemanticsNode()
        val plain = compose.onNodeWithTag("plain", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("graphicsLayer's box holds a node of its own", 1, layer.children.size)
        assertEquals("plainLayer's box holds none", 0, plain.children.size)
        assertEquals("the whole tree", 4, count(root))
    }
}
