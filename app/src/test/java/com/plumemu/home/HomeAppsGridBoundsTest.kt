package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Apps' grid of icons measured at the RP5's 768x432, the RP4 Pro's 576x432
 * and the Thor's top screen, at every text size of the ramp in Sans, Serif and Mono: the columns from
 * the width ([appsGridColumns]), tiles of one size within a pixel and even gaps, every icon 56 dp and
 * centred, every name at most two lines inside its tile, and the focused tile's ring inside the screen
 * (between the bars) after scrolling to the last app. The grid size changes none of it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeAppsGridBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Graphite)
    private var layout: PlumLayout? = null
    private var nameLine: Dp = 0.dp
    private var state: StripState? = null

    private fun show() {
        compose.setContent {
            PlumTheme(settings) {
                layout = PlumTheme.layout
                nameLine = appsGridNameLine(appsGridNameStyle(), compose.density)
                key(settings) { AppsGridBoard(focused = 0, onState = { state = it }) }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun inTile(
        index: Int,
        tag: String,
    ): DpRect = compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(HomeTags.cover(index))), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun isShown(index: Int) = compose.onAllNodesWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Half a pixel at xhdpi is 0.25 dp; "within 1 px" is 0.5 dp. */
    private fun near(
        what: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(what, expected.value, actual.value, PIXEL)

    private fun assertEveryTextSizeAndFont(screen: String) {
        show()
        val report = StringBuilder()
        for (font in PlumFont.entries) {
            for (size in TextSize.entries) {
                settings = PlumThemeSettings.Graphite.copy(font = font, textSize = size)
                compose.waitForIdle()
                val label = "$screen $font $size"
                val columns = assertTheTiles(label)
                report.append("$label: $columns columns, row ${"%.1f".format(bounds(HomeTags.cover(0)).let { it.bottom - it.top }.value)} dp\n")
                assertTheRingAfterScrollingToTheEnd(label)
            }
        }
        println(report)
    }

    /** The first two rows: columns, sizes, gaps, icons and names. Returns the columns. */
    private fun assertTheTiles(label: String): Int {
        val sized = checkNotNull(layout)
        val columns = appsGridColumns(sized)
        val first = bounds(HomeTags.cover(0))
        val width = first.right - first.left
        val height = first.bottom - first.top
        val gap = PlumDimens.gridGapPortrait
        near("$label: the tile row is ${appsGridRowHeight(nameLine)}", appsGridRowHeight(nameLine), height)
        near("$label: $columns columns span the grid's width", first.left + sized.gridCoversWidth, bounds(HomeTags.cover(columns - 1)).right)
        assertTrue("$label: column ${columns + 1} starts a new row", bounds(HomeTags.cover(columns)).top > first.bottom)
        for (index in 0 until columns * 2) {
            val tile = bounds(HomeTags.cover(index))
            val at = "$label: tile $index"
            near("$at width", width, tile.right - tile.left)
            near("$at height", height, tile.bottom - tile.top)
            if (index % columns > 0) near("$at gap across", gap, tile.left - bounds(HomeTags.cover(index - 1)).right)
            if (index >= columns) near("$at gap down", gap, tile.top - bounds(HomeTags.cover(index - columns)).bottom)
            val iconTag = if (index == APP_WITHOUT_ICON) HomeTags.APP_ICON_STAND_IN else HomeTags.APP_ICON
            val icon = inTile(index, iconTag)
            near("$at icon width", PlumDimens.appsGridIcon, icon.right - icon.left)
            near("$at icon height", PlumDimens.appsGridIcon, icon.bottom - icon.top)
            near("$at icon centred", icon.left - tile.left, tile.right - icon.right)
            val name = inTile(index, HomeTags.APP_NAME)
            assertTrue("$at: the name is inside the tile across ($name in $tile)", name.left >= tile.left - PIXEL.dp && name.right <= tile.right + PIXEL.dp)
            assertTrue("$at: the name is under the icon", name.top >= icon.bottom)
            near("$at: the name is centred under the icon", icon.left + icon.right, name.left + name.right)
            near("$at: the name sits ${PlumDimens.appsGridNameGap} under the icon", PlumDimens.appsGridNameGap, name.top - icon.bottom)
            assertTrue("$at: the name ends inside the tile (${name.bottom} > ${tile.bottom})", name.bottom <= tile.bottom - PlumDimens.appsGridTilePadding + PIXEL.dp)
            val lines = (name.bottom - name.top) / nameLine
            assertTrue("$at: at most two lines ($lines)", lines <= 2f + PIXEL / nameLine.value)
            if (index == APP_LONG_NAME) near("$at: a long name takes exactly two lines", nameLine * 2, name.bottom - name.top)
        }
        return columns
    }

    /** Down until the last row: the focused tile is whole in the grid and its ring between the bars, inside the screen. */
    private fun assertTheRingAfterScrollingToTheEnd(label: String) {
        val strip = checkNotNull(state)
        val last = AppsGridApps.lastIndex
        repeat(AppsGridApps.size) { compose.runOnIdle { strip.dispatch(Action.Move(Direction.Down)) } }
        compose.runOnIdle { strip.dispatch(Action.TapItem(Column.Games, last)) }
        compose.waitForIdle()
        assertTrue("$label: the last app is shown", isShown(last))
        val tile = bounds(HomeTags.cover(last))
        val grid = bounds(HomeTags.GRID)
        assertTrue("$label: the last tile is whole in the grid ($tile in $grid)", tile.top >= grid.top - PIXEL.dp && tile.bottom <= grid.bottom + PIXEL.dp)
        val ring = PlumDimens.focusRingOutset
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val top = bounds(HomeTags.TOP_BAR).bottom
        val bottom = bounds(HomeTags.HINT_BAR).top
        assertTrue("$label: the ring is below the top bar", tile.top - ring >= top - PIXEL.dp)
        assertTrue("$label: the ring is above the hint bar", tile.bottom + ring <= bottom + PIXEL.dp)
        assertTrue("$label: the ring is inside the screen across", tile.left - ring >= root.left && tile.right + ring <= root.right)
        // Back to the top for the next size.
        compose.runOnIdle { strip.dispatch(Action.TapItem(Column.Games, 0)) }
        repeat(AppsGridApps.size) { compose.runOnIdle { strip.dispatch(Action.Move(Direction.Up)) } }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = WIDE)
    fun rp5EveryTextSizeAndFont() = assertEveryTextSizeAndFont("768x432")

    @Test
    @Config(qualifiers = NARROW)
    fun rp4ProEveryTextSizeAndFont() = assertEveryTextSizeAndFont("576x432")

    @Test
    @Config(qualifiers = THOR)
    fun thorTopScreenEveryTextSizeAndFont() = assertEveryTextSizeAndFont("837x471")

    /**
     * The columns the docs and PlumDimens name, with the peek growing with the text as the theme makes it:
     * 7 / 5 / 8 at Medium on 768 / 576 / 837, 7 on the Thor at its default Large, 5 / 3 / 5 at Largest; never by grid size.
     */
    @Test
    fun theColumnsFollowTheWidthAndTextSizeNeverTheGridSize() {
        val expected = mapOf(
            (768 to TextSize.Medium) to 7, (576 to TextSize.Medium) to 5, (837 to TextSize.Medium) to 8, (837 to TextSize.Large) to 7,
            (768 to TextSize.Largest) to 5, (576 to TextSize.Largest) to 3, (837 to TextSize.Largest) to 5,
        )
        for ((at, columns) in expected) {
            val (width, text) = at
            for (gridSize in GridSize.entries) {
                val scale = text.heightScale
                val sized = PlumLayout(width = width.dp, height = 432.dp, peek = PlumDimens.peekWidth * scale, textScale = scale, gridSize = gridSize)
                assertEquals("$width at $text, $gridSize", columns, appsGridColumns(sized))
            }
        }
    }

    private companion object {
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val THOR = "w837dp-h471dp-land-xhdpi"
        const val PIXEL = 0.5f
    }
}
