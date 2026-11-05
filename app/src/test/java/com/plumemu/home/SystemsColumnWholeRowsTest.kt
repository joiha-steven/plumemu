package com.plumemu.home

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The systems column shows whole rows only: a row cut by its top or bottom edge leaves no sliver of a glyph (the part inside the column is plain background). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class SystemsColumnWholeRowsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** Rows of the column cut at an edge, and the ink rows found inside the column where such a row is. */
    private fun cutRowsAndInk(actions: List<Action>, settings: PlumThemeSettings = PlumThemeSettings.Graphite): Pair<Int, Int> {
        val content = FakeHomeContent.board()
        compose.setContent { PlumTheme(settings) { HomeAt(content, actions) } }
        compose.waitForIdle()
        val d = compose.density.density
        val window = compose.onNodeWithTag(HomeTags.SYSTEMS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val (wTop, wBottom, wLeft, wRight) = listOf(window.top.value * d, window.bottom.value * d, window.left.value * d, window.right.value * d)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val background = bitmap.getPixel(2, bitmap.height / 2)
        val rowPx = content.entries.indices.maxOf { compose.onAllNodesWithTag(HomeTags.system(it), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.height ?: 0f }
        var cut = 0
        var ink = 0
        for (index in content.entries.indices) {
            val node = compose.onAllNodesWithTag(HomeTags.system(index), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: continue
            val top = node.boundsInRoot.top
            val bottom = node.boundsInRoot.bottom
            if (bottom <= wTop || top >= wBottom) continue
            // The node's bounds are clipped by the column: a row shorter than its stride is cut.
            if (bottom - top >= rowPx - 1f) continue
            cut++
            val from = maxOf(top, wTop).toInt()
            val to = minOf(bottom, wBottom).toInt()
            for (y in from until to) for (x in maxOf(wLeft.toInt(), 0) until wRight.toInt()) if (bitmap.getPixel(x, y) != background) { ink++; break }
        }
        return cut to ink
    }

    @Test
    fun noRowCutAtEitherEdgeLeavesInk() {
        val last = FakeHomeContent.board().entries.lastIndex
        val atEnd = cutRowsAndInk(listOf(Action.TapItem(Column.Systems, last)))
        assertTrue("a row is cut at the top with the last system focused", atEnd.first > 0)
        assertTrue("ink of a cut row at the top: ${atEnd.second} rows", atEnd.second == 0)
    }

    @Test
    fun noRowCutAtTheBottomLeavesInk() {
        val atStart = cutRowsAndInk(listOf(Action.TapItem(Column.Systems, 0)))
        assertTrue("a row is cut at the bottom with the first system focused", atStart.first > 0)
        assertTrue("ink of a cut row at the bottom: ${atStart.second} rows", atStart.second == 0)
    }

    private val fadeOff = PlumThemeSettings.Graphite.copy(edgeFadeOn = false)

    @Test
    fun noRowCutAtTheTopLeavesInkWithTheFadeSettingOff() {
        val last = FakeHomeContent.board().entries.lastIndex
        val r = cutRowsAndInk(listOf(Action.TapItem(Column.Systems, last)), fadeOff)
        assertTrue("a row is cut at the top with the fade off", r.first > 0)
        assertTrue("ink of a cut row with the fade off: ${r.second} rows", r.second == 0)
    }

    @Test
    fun noRowCutAtTheBottomLeavesInkWithTheFadeSettingOff() {
        val r = cutRowsAndInk(listOf(Action.TapItem(Column.Systems, 0)), fadeOff)
        assertTrue("a row is cut at the bottom with the fade off", r.first > 0)
        assertTrue("ink of a cut row with the fade off: ${r.second} rows", r.second == 0)
    }
}
