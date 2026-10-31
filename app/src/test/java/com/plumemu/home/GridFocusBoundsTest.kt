package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The focused item of a grid (ring hugging the item, grown and raised) stays whole inside the screen's room for
 * it at the first and last column and the first and last row, in the cover grid and the Apps grid, at 768x432 and 576x432. The
 * lifted item and its ring (gap and accent band) must stay under the top bar, above the hint bar, between the screen's side
 * margin and the grid's right edge; and the accent ring is really drawn on the pixels just outside the lifted item (so it is
 * neither cut nor missing).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GridFocusBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var accent = Color.Unspecified
    private var state: StripState? = null
    private var columns = 0

    private var gridSize by mutableStateOf(GridSize.Default)

    private fun show(content: @Composable () -> Unit) =
        compose.setContent {
            PlumTheme(MediumText.Default.copy(gridSize = gridSize)) {
                accent = PlumTheme.colors.accent
                columns = PlumTheme.layout.gridColumns(com.plumemu.coreui.theme.CoverShape.Landscape)
                appsColumnCount = appsGridColumns(PlumTheme.layout)
                content()
            }
        }

    /** The item as laid out: an app's tile, a cover's picture (fitted in its cell), in px. */
    private fun resting(index: Int): Rect {
        val cell = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true)
        val picture = cell.fetchSemanticsNode().children.firstOrNull { child -> child.config.any { it.value == HomeTags.COVER_PICTURE } }
        if (picture != null) return Rect(picture.positionInRoot, androidx.compose.ui.geometry.Size(picture.size.width.toFloat(), picture.size.height.toFloat()))
        val b = cell.getUnclippedBoundsInRoot()
        val d = compose.density.density
        return Rect(b.left.value * d, b.top.value * d, b.right.value * d, b.bottom.value * d)
    }

    private fun dp(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** Focus [index], then check the lifted rect from [liftedOf] (px) and its ring against the room the screen leaves. */
    private fun assertRoomFor(
        label: String,
        index: Int,
        liftedOf: (Rect) -> Rect,
    ) {
        compose.runOnIdle { checkNotNull(state).dispatch(Action.TapItem(Column.Games, index)) }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val d = compose.density.density
        val lifted = liftedOf(resting(index))
        val outset = PlumDimens.focusRingOutset.value * d
        val grid = dp(HomeTags.GRID)
        val top = dp(HomeTags.TOP_BAR).bottom.value * d
        val hints = dp(HomeTags.HINT_BAR).top.value * d
        val minLeft = (grid.left.value - PlumDimens.stripMarginSide.value) * d
        // The covers end 8 dp before the grid's edge and the next column starts 24 dp after it: the ring may use the gap, keeping 8 dp clear of that column.
        val maxRight = (grid.right.value + PlumDimens.columnGap.value - 8f) * d
        assertTrue("$label: ring under the top bar (${lifted.top - outset} vs $top)", lifted.top - outset >= top - 0.5f)
        assertTrue("$label: ring above the hint bar (${lifted.bottom + outset} vs $hints)", lifted.bottom + outset <= hints + 0.5f)
        assertTrue("$label: ring inside the left margin (${lifted.left - outset} vs $minLeft)", lifted.left - outset >= minLeft - 0.5f)
        assertTrue("$label: ring clear of the next column (${lifted.right + outset} vs $maxRight)", lifted.right + outset <= maxRight + 0.5f)
        // The accent band is drawn, uncut, in the middle of each side just outside the gap.
        val band = (PlumDimens.focusRingGap.value + PlumDimens.focusRingWidth.value / 2f) * d
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val sides = mapOf(
            "top" to (lifted.center.x to lifted.top - band), "bottom" to (lifted.center.x to lifted.bottom + band),
            "left" to (lifted.left - band to lifted.center.y), "right" to (lifted.right + band to lifted.center.y),
        )
        for ((side, at) in sides) {
            val p = pixels[at.first.roundToInt(), at.second.roundToInt()]
            assertTrue("$label: the accent ring is drawn on the $side side", abs(p.red - accent.red) < 0.12f && abs(p.green - accent.green) < 0.12f && abs(p.blue - accent.blue) < 0.12f)
        }
    }

    private fun appsLifted(rest: Rect): Rect {
        val s = PlumDimens.FOCUSED_ICON_SCALE
        val lift = PlumDimens.focusedLift.value * compose.density.density
        return Rect(rest.center.x - rest.width * s / 2f, rest.center.y - rest.height * s / 2f - lift, rest.center.x + rest.width * s / 2f, rest.center.y + rest.height * s / 2f - lift)
    }

    private fun coverLifted(rest: Rect): Rect = focusedCoverRect(rest, null, PlumDimens.FOCUSED_COVER_SCALE, PlumDimens.focusedLift.value * compose.density.density)

    private fun assertApps(label: String) {
        show { AppsGridBoard(focused = 0, onState = { state = it }) }
        compose.waitForIdle()
        val last = AppsGridApps.lastIndex
        for (index in listOf(0, 6, 7, last)) assertRoomFor("$label apps $index", index, ::appsLifted)
        assertRoomFor("$label apps last column of the first row", appsColumnCount - 1, ::appsLifted)
        assertRoomFor("$label apps first column of the last row", last - last % appsColumnCount, ::appsLifted)
    }

    private var appsColumnCount = 0

    private fun assertCovers(label: String) {
        show { androidx.compose.runtime.key(gridSize) { HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes"), onState = { state = it }) } }
        val snes = FakeHomeContent.withArtwork().entries[FakeHomeContent.BOARD_SNES_INDEX].rows.lastIndex
        for (size in GridSize.entries) {
            gridSize = size
            compose.waitForIdle()
            // Columns by the cells' tops (a picture of another shape sits lower in its cell).
            fun cellTop(i: Int) = dp(HomeTags.cover(i)).top
            var real = 1
            while (real < snes && cellTop(real) == cellTop(0)) real++
            for (index in listOf(0, real - 1, real, snes)) assertRoomFor("$label covers $size $index", index, ::coverLifted)
        }
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideApps() = assertApps("768x432")

    @Test
    @Config(qualifiers = NARROW)
    fun narrowApps() = assertApps("576x432")

    @Test
    @Config(qualifiers = WIDE)
    fun wideCovers() = assertCovers("768x432")

    @Test
    @Config(qualifiers = NARROW)
    fun narrowCovers() = assertCovers("576x432")

    private companion object {
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
    }
}
