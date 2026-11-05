package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The visible edges of a packed grid, measured in the real screen: in every full row the first collar starts on the content's left
 * margin (where the list starts) and the last collar ends on the right margin the clock and the hint bar end on, with equal gaps
 * between. At every Size step, in both themes, for one-shape and mixed box art, on the Thor's 837 dp, the 853 dp reference, the
 * Pocket 6's 832, the RP5's 768, the Nova's 640x480 and 576x432.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GridRightEdgeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** Boxes are as wide as their collars, so a cell is its collar's outer edge. */
    private fun collarOf(index: Int): DpRect = bounds(HomeTags.cover(index))

    private fun assertEdges(
        what: String,
        board: @Composable () -> Unit,
    ) {
        var theme by mutableStateOf(TableText.Graphite)
        compose.setContent { PlumTheme(theme) { key(theme) { board() } } }
        for (name in listOf("Graphite", "Paper")) for (size in GridSize.entries) {
            theme = (if (name == "Paper") TableText.Paper else TableText.Graphite).copy(gridSize = size)
            compose.waitForIdle()
            assertEdgesNow("$what $name $size")
        }
    }

    private fun assertEdgesNow(what: String) {
        // The content's right edge is where the clock and the hint bar end, the left edge where the list starts.
        val right = compose.onRoot().getUnclippedBoundsInRoot().right.value - PlumDimens.stripMarginSide.value
        val left = (PlumDimens.stripMarginSide + PlumDimens.peekWidth).value
        val shown = (0 until 60).filter { compose.onAllNodesWithTag(HomeTags.cover(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val rows = shown.groupBy { Math.round(bounds(HomeTags.cover(it)).top.value) }.values.toList()
        assertTrue("$what: rows shown", rows.size >= 2)
        // Every row but the last shown one is a full row.
        rows.dropLast(1).forEach { row ->
            val cells = row.map { collarOf(it) }
            val gaps = cells.zipWithNext { a, b -> (b.left - a.right).value }
            assertEquals("$what: a full row starts on the left edge", left, cells.first().left.value, EDGE)
            assertEquals("$what: a full row ends on the right edge", right, cells.last().right.value, EDGE)
            gaps.forEach { assertEquals("$what: equal gaps", gaps.first(), it, EDGE) }
        }
    }

    @Composable
    private fun single() = HomeAt(FakeHomeContent.withArtwork().let { it.withEntries(it.entries.map { e -> if (e.id == "nds") e.copy(coverShape = NDS_SHAPE) else e }) }, gridActions(NDS_INDEX, SQUARE_GRID_FOCUS), gridIds = setOf("nds"))

    @Composable
    private fun mixed() = HomeAt(FakeHomeContent.withArtwork(), gridActions(FakeHomeContent.BOARD_SNES_INDEX, MEGA_MAN_X), gridIds = setOf("snes"))

    @Test
    @Config(qualifiers = THOR)
    fun thorSingleShape() = assertEdges("thor single") { single() }

    @Test
    @Config(qualifiers = THOR)
    fun thorMixed() = assertEdges("thor mixed") { mixed() }

    @Test
    @Config(qualifiers = FULL)
    fun fullSingleShape() = assertEdges("853 single") { single() }

    @Test
    @Config(qualifiers = FULL)
    fun fullMixed() = assertEdges("853 mixed") { mixed() }

    @Test
    @Config(qualifiers = POCKET6)
    fun pocket6SingleShape() = assertEdges("832 single") { single() }

    @Test
    @Config(qualifiers = POCKET6)
    fun pocket6Mixed() = assertEdges("832 mixed") { mixed() }

    @Test
    @Config(qualifiers = WIDE)
    fun wideSingleShape() = assertEdges("768 single") { single() }

    @Test
    @Config(qualifiers = WIDE)
    fun wideMixed() = assertEdges("768 mixed") { mixed() }

    @Test
    @Config(qualifiers = NOVA)
    fun novaSingleShape() = assertEdges("640 single") { single() }

    @Test
    @Config(qualifiers = NOVA)
    fun novaMixed() = assertEdges("640 mixed") { mixed() }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSingleShape() = assertEdges("576 single") { single() }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowMixed() = assertEdges("576 mixed") { mixed() }

    private companion object {
        const val THOR = "w837dp-h471dp-land-xhdpi"
        const val FULL = "w853dp-h480dp-land-xhdpi"
        const val POCKET6 = "w832dp-h468dp-land-xhdpi"
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NOVA = "w640dp-h480dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val EDGE = 0.5f
    }
}
