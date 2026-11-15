package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A grid kept built beside the shown system ([GridPages]) behaves as a grid made when its system is entered: it opens at its focus after a finger scrolled it away, packs at the stored shapes of the new visit, and its row neither
 * cuts the Apps preview nor swallows a tap on the systems column.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w1024dp-h576dp-land-xhdpi")
class GridPagesKeptTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** Nothing is known until a test lands a result: every slot stays Unknown, so the stored shapes decide. */
    private class Source : HomeArtSource {
        override fun observe(keys: List<String>): kotlinx.coroutines.flow.Flow<Map<String, GameArt>> = kotlinx.coroutines.flow.emptyFlow()

        override suspend fun extras(key: String) = DetailsExtras.None

        override val images: CoverImages = FakeCoverImages()
    }

    private fun rows(aspect: (Int) -> Float? = { null }) = (0 until COUNT).map { i -> GameRowModel(i.toString(), "Game $i", region = "", scrapeKey = artKey(i.toString()), systemId = "snes", artAspect = aspect(i)) }

    private fun library(snes: List<GameRowModel> = rows()): HomeContent {
        val other = (0 until 3).map { GameRowModel("n$it", "Other $it", region = "", systemId = "nes") }
        return HomeContent.of(
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(SystemGames("snes", "Super Nintendo", snes, CoverShape.Landscape), SystemGames("nes", "Nintendo", other, CoverShape.Landscape)),
            hasBoxArt = true,
        )
    }

    private var content by mutableStateOf(library())
    private lateinit var strip: StripState
    private var densityValue = 1f
    private var layoutAreaDp = 0f
    private val snesIndex get() = content.entries.indexOfFirst { it.id == "snes" }

    private fun open(
        actions: List<Action>,
        grids: Set<String> = setOf("snes"),
        shown: HomeContent = content,
        runners: BarRunners? = null,
    ) {
        content = shown
        compose.setContent {
            PlumTheme {
                val body = @androidx.compose.runtime.Composable { ProvideHomeArt(Source(), content.hasBoxArt) { HomeAt(content, actions, gridIds = grids, onAction = { strip.dispatch(it) }, onState = { strip = it }) } }
                if (runners == null) body() else CompositionLocalProvider(LocalBarRunners provides runners) { body() }
            }
        }
        compose.waitForIdle()
    }

    private fun act(action: Action) {
        compose.runOnIdle { strip.dispatch(action) }
        compose.waitForIdle()
    }

    private fun coverBounds(): Map<Int, DpRect> =
        (0 until COUNT)
            .filter { compose.onAllNodes(hasTestTag(HomeTags.cover(it)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            .associateWith { compose.onNodeWithTag(HomeTags.cover(it), useUnmergedTree = true).getUnclippedBoundsInRoot() }

    private fun widths(tiles: Map<Int, DpRect>) = tiles.values.map { Math.round((it.right - it.left).value) }.distinct()

    @Test
    fun aGridKeptBuiltOpensAtItsFocusAfterAFingerScrolledItAway() {
        open(listOf(Action.TapItem(Column.Systems, 0)))
        act(Action.TapItem(Column.Systems, snesIndex))
        act(Action.Enter)
        act(Action.TapItem(Column.Games, 0))
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertFalse("a finger scrolled the focused cover away", coverBounds().containsKey(0))
        act(Action.NextSystem)
        compose.mainClock.advanceTimeBy(1_000)
        act(Action.PreviousSystem)
        compose.mainClock.advanceTimeBy(1_000)
        val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val first = coverBounds()[0]
        assertTrue("the focused cover is on screen again: $first", first != null && first.top >= grid.top && first.bottom <= grid.bottom)
    }

    @Test
    fun rowsChangingWhileAGridIsHiddenPackAtTheStoredShapesOfTheNextVisit() {
        open(gridActionsFor(DEEP))
        val before = coverBounds()
        act(Action.NextSystem)
        compose.runOnIdle { content = library(rows(::mixed)) }
        compose.waitForIdle()
        act(Action.PreviousSystem)
        assertTrue("the visit packs at the stored shapes: ${widths(before)} then ${widths(coverBounds())}", coverBounds() != before)
    }

    @Test
    fun aTapOnTheRightOfASystemRowIsNotSwallowedByTheGridsRow() {
        open(listOf(Action.TapItem(Column.Systems, 0)))
        act(Action.TapItem(Column.Systems, snesIndex))
        compose.mainClock.advanceTimeBy(1_000)
        val next = snesIndex + 1
        compose.onNodeWithTag(HomeTags.system(next), useUnmergedTree = true).performTouchInput { click(Offset(40.dp.toPx(), height / 2f)) }
        compose.waitForIdle()
        assertEquals("a tap on the left of the next system's row reached it", next, strip.focus.systemIndex)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(HomeTags.system(snesIndex), useUnmergedTree = true).performTouchInput { click(Offset(width - 10.dp.toPx(), height / 2f)) }
        compose.waitForIdle()
        assertEquals("a tap on the right of a system's row reached it, the grid's row beside it", snesIndex, strip.focus.systemIndex)
    }

    @Test
    fun theAppsPreviewBesideTheSystemsReachesTheRightMarginWholeColumns() {
        val board = FakeHomeContent.withArtwork()
        val apps = board.entries.indexOfFirst { it.id == HomeContent.APPS_ID }
        compose.setContent {
            PlumTheme {
                densityValue = LocalDensity.current.density
                layoutAreaDp = PlumTheme.layout.gridAreaWidth.value
                HomeAt(board, listOf(Action.TapItem(Column.Systems, apps)), gridIds = setOf(HomeContent.APPS_ID), onState = { strip = it })
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().toPixelMap()
        val bg = image[image.width - 2, 2]
        val density = densityValue
        val cells = coverBoundsOf(board.entries[apps].rows.size)
        val firstRow = cells.entries.filter { Math.abs(it.value.top.value - cells.getValue(0).top.value) < 1f }.sortedBy { it.value.left }
        assertTrue("a row of the preview: ${firstRow.size} cells", firstRow.size >= 3)
        fun ink(box: DpRect): Int {
            var n = 0
            for (x in (box.left.value * density).toInt() until (box.right.value * density).toInt()) {
                for (y in (box.top.value * density).toInt() until (box.bottom.value * density).toInt()) {
                    if (x in 0 until image.width && y in 0 until image.height && differs(image[x, y], bg)) n++
                }
            }
            return n
        }
        // The preview shows whole columns up to the right margin; every cell that ends inside it is drawn whole, as the first.
        println("APPS_PREVIEW grid area ${layoutAreaDp} dp")
        val limit = image.width / density - com.plumemu.coreui.theme.PlumDimens.stripMarginSide.value
        val inside = firstRow.filter { it.value.right.value <= limit }
        val inks = inside.map { ink(it.value) }
        println("APPS_PREVIEW cells ${firstRow.map { it.value.right.value }} limit $limit inks $inks")
        assertTrue("whole columns reach the margin: ${inside.size} of ${firstRow.size}", inside.size >= 5)
        assertTrue("the columns up to the margin are drawn whole: $inks", inks.all { it * 10 >= inks.first() * 8 })
    }

    private fun differs(a: Color, b: Color) = Math.abs(a.red - b.red) + Math.abs(a.green - b.green) + Math.abs(a.blue - b.blue) > 0.02f

    private fun coverBoundsOf(count: Int): Map<Int, DpRect> =
        (0 until count)
            .filter { compose.onAllNodes(hasTestTag(HomeTags.cover(it)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            .associateWith { compose.onNodeWithTag(HomeTags.cover(it), useUnmergedTree = true).getUnclippedBoundsInRoot() }

    private fun gridActionsFor(index: Int) = listOf(Action.TapItem(Column.Systems, 3), Action.Enter, Action.TapItem(Column.Games, index))

    private fun mixed(i: Int): Float? = SHAPES[i % SHAPES.size]

    private companion object {
        const val COUNT = 120
        const val DEEP = 60
        const val BAND = 10f
        val SHAPES = listOf(1.4f, 0.7f, 1f, 1.4f, 0.7f)
    }
}
