package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LocalGlideClock
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
 * A focus move or a finger's press redraws only the cells whose look changes: a cell's draw block must read state that changes for that
 * cell alone (its own derived lift and dip), not the shared focus index, the lifts' version or the shared press progress. Counts the draw
 * blocks that ran ([GridDrawProbe]) between a move and rest. The baseline test shows what a capture alone forces, so a count above it is
 * a real invalidation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GridDrawCountTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val drawn = HashMap<Pair<String, Int>, Int>()
    private var state: StripState? = null

    private fun open(apps: Boolean) {
        val probe = GridDrawProbe { kind, index -> drawn.merge(kind to index, 1, Int::plus) }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme {
                CompositionLocalProvider(
                    LocalGridDrawProbe provides probe,
                    LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L },
                ) {
                    if (apps) {
                        AppsGridBoard(focused = 5, onState = { state = it })
                    } else {
                        HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 5), gridIds = setOf("snes"), onState = { state = it })
                    }
                }
            }
        }
        frames(80)
        shownBefore = drawn.keys.map { it.second }.toSet()
    }

    /** The cells drawn before the test's action: a cell scrolled into the window by it is drawn for the first time, which is not a redraw. */
    private var shownBefore = emptySet<Int>()

    /** Frame by frame, each one captured (a Robolectric window only draws when asked), [count] frames. */
    private fun frames(count: Int) {
        repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.onRoot().captureToImage()
        }
    }

    private fun cells(kind: String) = drawn.filter { it.key.first == kind && it.value > 0 }.keys.map { it.second }.sorted()

    private fun report(what: String) {
        println("DRAWCOUNT $what: " + drawn.keys.map { it.first }.distinct().sorted().joinToString("; ") { k -> "$k ${cells(k).size} cells ${cells(k)}, ${drawn.filterKeys { it.first == k }.values.sum()} draws" })
    }

    /** Presses a direction once in a settled grid and captures the frames of the lift; returns the focused item before and after. */
    private fun moveOnce(apps: Boolean, direction: Direction): Pair<Int, Int> {
        open(apps)
        val before = compose.runOnIdle { checkNotNull(state).focus.row }
        drawn.clear()
        compose.runOnUiThread { checkNotNull(state).dispatch(Action.Move(direction)) }
        frames(60)
        val after = compose.runOnIdle { checkNotNull(state).focus.row }
        assertTrue("the focus moved ($before to $after)", after != before)
        return before to after
    }

    @Test
    fun aSettledGridCapturedAgainDrawsNothing() {
        open(apps = false)
        drawn.clear()
        frames(20)
        report("covers, nothing changes")
        assertEquals("a capture alone must not run a cell's draw block: $drawn", 0, drawn.size)
    }

    @Test
    fun aRightPressRedrawsOnlyTheTwoCoversThatChange() {
        val (before, after) = moveOnce(apps = false, Direction.Right)
        report("covers Right")
        val others = cells(GridDrawProbe.COVER).filter { it != before && it != after && it in shownBefore }
        assertTrue("covers other than $before and $after redrew: $others", others.isEmpty())
        assertTrue("the two covers redrew", cells(GridDrawProbe.COVER).containsAll(listOf(before, after)))
    }

    @Test
    fun aDownPressRedrawsOnlyTheTwoAppTilesThatChange() {
        val (before, after) = moveOnce(apps = true, Direction.Down)
        report("apps Down")
        for (kind in listOf(GridDrawProbe.TILE, GridDrawProbe.NAME)) {
            val others = cells(kind).filter { it != before && it != after && it in shownBefore }
            assertTrue("$kind other than $before and $after redrew: $others", others.isEmpty())
            assertTrue("$kind: the two redrew", cells(kind).containsAll(listOf(before, after)))
        }
    }

    @Test
    fun aFingerDownOnOneCoverDipsThatCoverAndOnlyIt() {
        open(apps = false)
        drawn.clear()
        compose.onNodeWithTag(HomeTags.cover(2), useUnmergedTree = true).performTouchInput { down(center) }
        frames(20)
        compose.onNodeWithTag(HomeTags.cover(2), useUnmergedTree = true).performTouchInput { up() }
        frames(30)
        report("covers finger")
        // The focused cover (5) may redraw for its own dip; no other cover moves.
        val others = cells(GridDrawProbe.COVER).filter { it != 2 && it != 5 && it in shownBefore }
        assertTrue("covers other than the touched one redrew: $others", others.isEmpty())
        assertTrue("the touched cover drew through the dip: ${drawn[GridDrawProbe.COVER to 2]}", (drawn[GridDrawProbe.COVER to 2] ?: 0) >= 5)
    }
}
