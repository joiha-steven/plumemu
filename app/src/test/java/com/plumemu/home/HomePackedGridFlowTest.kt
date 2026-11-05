package com.plumemu.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How the packed rows and the focus rules stay in step while things change under them: art arriving, a new grid size, and
 * what recomposes when one picture's shape changes. Expectations come from [expectedPacking], the rule worked out by hand.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomePackedGridFlowTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val calls = mutableListOf<String>()

    @Before
    fun trace() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
                    calls += info
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stopTracing() = Composer.setTracer(null)

    private val snesRows get() = FakeHomeContent.withArtwork().entries[boardIndexOf("snes")].rows

    /** Every Super Nintendo box at 1.4 (four to a row at Medium, 768 wide); [wide] is a picture of another shape for game 0. */
    private fun landscapeSource(): FakeArtSource = realCoverSource(aspectOf = { _, _ -> LANDSCAPE })

    /** Game [index]'s picture replaced by one of [aspect] (a result landing late, or a better picture saved). */
    private fun FakeArtSource.reshape(index: Int, aspect: Float) {
        val key = artKey(snesRows[index].id)
        val art = results.value.getValue(key)
        val grid = checkNotNull(art.grid)
        results.value = results.value + (key to art.copy(grid = ArtPicture(grid.path, (400 * aspect).toInt(), 400)))
    }

    private fun aspects(changed: Map<Int, Float> = emptyMap()): List<Float> = snesRows.indices.map { changed[it] ?: LANDSCAPE }

    private var layout: PlumLayout? = null

    private fun showSnes(source: FakeArtSource, focused: Int, size: GridSize = GridSize.Medium): StripState {
        lateinit var state: StripState
        compose.setContent {
            PlumTheme {
                com.plumemu.coreui.theme.ProvideGridSize(size) {
                    layout = PlumTheme.layout
                    HomeWithArt(source, gridActions(boardIndexOf("snes"), focused), gridIds = setOf("snes"), onState = { state = it })
                }
            }
        }
        compose.waitForIdle()
        return state
    }

    private fun expected(changed: Map<Int, Float> = emptyMap()) = expectedPacking(aspects(changed), checkNotNull(layout), CoverShape.Landscape)

    private fun StripState.press(direction: Direction): Int {
        compose.runOnIdle { dispatch(Action.Move(direction)) }
        compose.waitForIdle()
        return compose.runOnIdle { focus.row }
    }

    /** Art lands for a game above the focused one, which wraps the focused game onto the next row: Up and Down follow the new rows. */
    @Test
    fun artArrivingThatMovesTheFocusedGameToAnotherRowRetargetsUpAndDown() {
        val source = landscapeSource()
        val state = showSnes(source, focused = 3)
        val before = expected()
        assertEquals("game 3 ends the first row of four", 0, before.rowOf(3))
        source.reshape(0, WIDE)
        compose.waitForIdle()
        val after = expected(mapOf(0 to WIDE))
        assertEquals("a wide first box pushes game 3 to the second row", 1, after.rowOf(3))
        assertEquals("the focused game stays", 3, compose.runOnIdle { state.focus.row })
        assertEquals("Up goes to the nearest box of the first row in the new rows", after.up(3), state.press(Direction.Up))
        assertNotEquals("(the old rows would have kept it on the first row)", before.up(3), after.up(3))
        compose.runOnIdle { state.focusRow(3) }
        assertEquals("Down goes to the nearest box of the third row in the new rows", after.down(3), state.press(Direction.Down))
    }

    /** L1 or R1 changes the grid size; the very next Down already follows the new layout (no frame without its table). */
    @Test
    fun downRightAfterAGridSizeChangeFollowsTheNewLayout() {
        val source = landscapeSource()
        var size by mutableStateOf(GridSize.Large)
        lateinit var state: StripState
        compose.setContent {
            PlumTheme {
                com.plumemu.coreui.theme.ProvideGridSize(size) {
                    layout = PlumTheme.layout
                    HomeWithArt(source, gridActions(boardIndexOf("snes"), 0), gridIds = setOf("snes"), onState = { state = it })
                }
            }
        }
        compose.waitForIdle()
        val small = expected() // the first size (Large)
        // A game whose Down lands differently at the two sizes, so a table from the wrong layout shows.
        size = GridSize.Medium
        compose.waitForIdle()
        val medium = expected()
        val probe = snesRows.indices.first { small.rows.size > small.rowOf(it) + 1 && medium.rows.size > medium.rowOf(it) + 1 && small.down(it) != medium.down(it) }
        size = GridSize.Large
        compose.waitForIdle()
        compose.runOnIdle { state.focusRow(probe) }
        compose.waitForIdle()

        compose.mainClock.autoAdvance = false
        size = GridSize.Medium
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { state.dispatch(Action.Move(Direction.Down)) }
        val landed = compose.runOnUiThread { state.focus.row }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("Down from $probe, one frame after the size changed, follows Medium's rows (Small's would give ${small.down(probe)})", medium.down(probe), landed)
    }

    /** One picture's shape changes: only the cover whose box changed runs again; the other covers of every shown row are skipped. */
    @Test
    fun aPictureChangingShapeRecomposesOnlyItsOwnCover() {
        val source = landscapeSource()
        showSnes(source, focused = 0)
        val shown = coverCellsShown()
        assertTrue("several rows are shown: $shown", shown.size >= 8)
        // The first odd shape turns the one-shape grid into a mixed one (every row packs by widths); the next change is the case under test.
        source.reshape(1, NARROWER)
        compose.waitForIdle()
        compose.runOnIdle { calls.clear() }
        source.reshape(1, SLIGHTLY_NARROWER)
        compose.waitForIdle()
        val covers = calls.filter { it.startsWith("com.plumemu.home.GridCoverAt ") }
        val inner = calls.filter { it.startsWith("com.plumemu.home.GridCover.Cover ") || it.startsWith("com.plumemu.home.GridCover ") }
        // The requirement changed: a row is justified by one factor, so a shape change may lay out the covers of its own row (never another row's).
        val row = expected(mapOf(1 to SLIGHTLY_NARROWER)).let { it.rows[it.rowOf(1)].size }
        assertTrue("GridCoverAt ran only for covers of the changed row ($row): $covers", covers.size in 1..row)
        assertTrue("GridCover ran only for them: $inner (shown ${shown.size})", inner.size <= row + 1)
    }

    private fun coverCellsShown(): Set<String> =
        (0 until snesRows.size).filter { compose.onAllNodesWithTagCount(HomeTags.cover(it)) > 0 }.map { HomeTags.cover(it) }.toSet()

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private companion object {
        const val LANDSCAPE = 1.4f
        const val WIDE = 3.6f
        const val NARROWER = 1.2f
        const val SLIGHTLY_NARROWER = 1.1f
    }
}
