package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.AppsGridDimens
import com.plumemu.coreui.theme.FocusLift
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
 * The focused item of a grid stays whole inside the screen's room for it at the first and last column and the first and last row,
 * at 768x432 and 576x432 (`FocusLift`). Apps grid: the focused icon grown 3.5 % about its centre and its rim and glow stay under the top bar,
 * above the hint bar, between the screen's side margin and the grid's right edge, and the rim is really drawn on the pixels just outside it.
 * Cover grid (the collar look): the box stays in its cell (layout), is drawn larger, its groove stays unlit, and the larger collar with its
 * rim stays inside the same room (measured with the growth about the centre, which is more than any pivot grows outward).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GridFocusBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var accent = Color.Unspecified
    private var dark = true
    private var ink = Color.Unspecified
    private var background = Color.Unspecified
    private var state: StripState? = null
    private var columns = 0

    private var gridSize by mutableStateOf(GridSize.Default)

    private fun show(content: @Composable () -> Unit) =
        compose.setContent {
            PlumTheme(TableText.Default.copy(gridSize = gridSize)) {
                accent = PlumTheme.colors.accent
                dark = PlumTheme.colors.isDark
                ink = PlumTheme.colors.text
                background = PlumTheme.colors.bg
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
        val outset = FocusLift.reach.value * d
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
        // The rim is drawn, uncut, in the middle of each side, its middle half a rim-width outside the grown icon's edge.
        val band = FocusLift.rimWidth.value * d / 2f
        val rim = FocusLift.rimColor(dark, ink).compositeOver(background)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val sides = mapOf(
            "top" to (lifted.center.x to lifted.top - band), "bottom" to (lifted.center.x to lifted.bottom + band),
            "left" to (lifted.left - band to lifted.center.y), "right" to (lifted.right + band to lifted.center.y),
        )
        for ((side, at) in sides) {
            val p = pixels[at.first.roundToInt(), at.second.roundToInt()]
            assertTrue("$label: the rim is drawn on the $side side", abs(p.red - rim.red) < 0.12f && abs(p.green - rim.green) < 0.12f && abs(p.blue - rim.blue) < 0.12f)
        }
    }

    /** The focused tile's icon grown about its centre, in px: the rim stands on it. */
    private fun appsIcon(rest: Rect): Rect {
        val d = compose.density.density
        val side = AppsGridDimens.appsGridTileIcon.value * d * FocusLift.SCALE
        val centre = rest.top + (AppsGridDimens.appsGridTilePadding.value + AppsGridDimens.appsGridTileIcon.value / 2f) * d
        return Rect(rest.center.x - side / 2f, centre - side / 2f, rest.center.x + side / 2f, centre + side / 2f)
    }

    private fun assertApps(label: String) {
        show { AppsGridBoard(focused = 0, onState = { state = it }) }
        compose.waitForIdle()
        val last = AppsGridApps.lastIndex
        for (index in listOf(0, 6, 7, last)) assertRoomFor("$label apps $index", index, ::appsIcon)
        assertRoomFor("$label apps last column of the first row", appsColumnCount - 1, ::appsIcon)
        assertRoomFor("$label apps first column of the last row", last - last % appsColumnCount, ::appsIcon)
    }

    private var appsColumnCount = 0

    /** A cover box's place inside its cell (px): moves only if the box moves within its cell. */
    private fun boxInCell(index: Int): Rect {
        val box = resting(index)
        val d = compose.density.density
        val cell = dp(HomeTags.cover(index))
        return box.translate(-cell.left.value * d, -cell.top.value * d)
    }

    /** The collar (px) round the box as laid out, drawn larger about its centre by the lift. */
    private fun liftedCollar(box: Rect): Rect {
        val d = compose.density.density
        val collar = Rect(
            box.left - CollarSize.Cover.sidePadding.value * d, box.top - CollarSize.Cover.topPadding.value * d,
            box.right + CollarSize.Cover.sidePadding.value * d, box.bottom + CollarSize.Cover.bottomPadding.value * d,
        )
        val s = FocusLift.SCALE
        return Rect(collar.center.x - collar.width * s / 2f, collar.center.y - collar.height * s / 2f, collar.center.x + collar.width * s / 2f, collar.center.y + collar.height * s / 2f)
    }

    /** The box (px) as laid out, drawn larger about its collar's centre. */
    /** The longest side of the collar round [box], px: what the scale is worked out from. */
    private fun liftBase(box: Rect): Float {
        val d = compose.density.density
        return maxOf(box.width + 2 * CollarSize.Cover.sidePadding.value * d, box.height + (CollarSize.Cover.topPadding + CollarSize.Cover.bottomPadding).value * d)
    }

    private fun liftedAboutCollar(box: Rect): Rect {
        val d = compose.density.density
        val c = Rect(box.left, box.top - CollarSize.Cover.topPadding.value * d, box.right, box.bottom + CollarSize.Cover.bottomPadding.value * d).center
        val s = FocusLift.SCALE
        return Rect(c.x + (box.left - c.x) * s, c.y + (box.top - c.y) * s, c.x + (box.right - c.x) * s, c.y + (box.bottom - c.y) * s)
    }

    private fun focusOn(index: Int) {
        compose.runOnIdle { checkNotNull(state).dispatch(Action.TapItem(Column.Games, index)) }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        settleLiftRecording()
    }

    /**
     * The grid's overlay draws a lifted cover from the layer its own cell records while drawing; the cell's write that says so is a draw-phase write,
     * which the test clock applies and redraws only when asked (a device does it on the next frame): apply it and draw the frame.
     */
    private fun settleLiftRecording() {
        // A capture draws the screen once: the cell that has just gained the lift records itself then, and the frames below draw it lifted.
        compose.onRoot().captureToImage()
        repeat(2) {
            compose.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun near(
        a: Color,
        b: Color,
    ) = abs(a.red - b.red) < 0.12f && abs(a.green - b.green) < 0.12f && abs(a.blue - b.blue) < 0.12f

    /**
     * A focused cover's box stays in its cell (it is drawn larger, not laid out larger); its collar's groove is lit in the accent (and was not before); the larger collar and the ring around
     * it stay inside the room the screen leaves: under the top bar, above the hint bar, inside the side margin and clear of the next column.
     */
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
            for (index in listOf(0, real - 1, real, snes)) {
                val col = index % real
                val sameRow = index - col
                val other = listOf(col + 2, col - 2).firstOrNull { it in 0 until real && sameRow + it <= snes }?.let { sameRow + it }
                // A lifted cover's ring lies over the covers beside it: read the unlit groove with the focus two columns away (none in a 2-column grid).
                focusOn(other ?: if (index == 0) 1 else index - 1)
                val before = boxInCell(index)
                val d = compose.density.density
                val groove = CollarDimens.coverGroove.value * d
                fun grooveAt(box: Rect, pixels: androidx.compose.ui.graphics.PixelMap, scale: Float = 1f): Color = (1..8).map { pixels[(box.left - groove * scale * it / 4f).roundToInt(), box.center.y.roundToInt()] }.minBy { abs(it.red - litGroove(accent).red) + abs(it.green - litGroove(accent).green) + abs(it.blue - litGroove(accent).blue) }
                val restBox = resting(index)
                // A packed grid can leave the box beside the focused one on the next, cut row: only a box on screen has a groove to read.
                val restPixels = compose.onRoot().captureToImage().toPixelMap()
                if (other != null && restBox.bottom < restPixels.height && restBox.top >= 0) assertTrue("$label $size $index: the groove is not lit before focus", !near(grooveAt(restBox, restPixels), litGroove(accent)))
                focusOn(index)
                val box = resting(index)
                val after = boxInCell(index)
                assertTrue("$label $size $index: the box stays in its cell ($before vs $after)", abs(before.left - after.left) < 0.5f && abs(before.top - after.top) < 0.5f && abs(before.width - after.width) < 0.5f && abs(before.height - after.height) < 0.5f)
                assertTrue("$label $size $index: the groove stays unlit", !near(grooveAt(liftedAboutCollar(box), compose.onRoot().captureToImage().toPixelMap(), 1f), litGroove(accent)))
                val side = CollarSize.Cover.sidePadding.value * d
                // The rim and glow (reach) round the larger collar.
                val reach = FocusLift.reach.value * d
                val collar = liftedCollar(box)
                val grid = dp(HomeTags.GRID)
                val top = dp(HomeTags.TOP_BAR).bottom.value * d
                val hints = dp(HomeTags.HINT_BAR).top.value * d
                val minLeft = (grid.left.value - PlumDimens.stripMarginSide.value) * d
                val maxRight = (grid.right.value + PlumDimens.columnGap.value - 8f) * d
                val at = "$label $size $index"
                assertTrue("$at: ring under the top bar (${collar.top - reach} vs $top)", collar.top - reach >= top - 0.5f)
                assertTrue("$at: ring above the hint bar (${collar.bottom + reach} vs $hints)", collar.bottom + reach <= hints + 0.5f)
                assertTrue("$at: ring inside the left margin (${collar.left - reach} vs $minLeft)", collar.left - reach >= minLeft - 0.5f)
                assertTrue("$at: ring clear of the next column (${collar.right + reach} vs $maxRight)", collar.right + reach <= maxRight + 0.5f)
            }
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
