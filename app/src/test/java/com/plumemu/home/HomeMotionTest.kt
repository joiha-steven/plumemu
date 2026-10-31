package com.plumemu.home

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The main screen's motion on the real layout: the system column's scroll and when lists fade in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeMotionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var state: StripState
    private lateinit var scope: CoroutineScope

    private val downToSystem15 = List(FOCUSED_SYSTEM) { Action.Move(Direction.Down) }

    private fun show(
        actions: List<Action>,
        animations: Boolean = true,
    ) {
        compose.setContent {
            scope = rememberCoroutineScope()
            PlumTheme(PlumThemeSettings(animationsOn = animations)) {
                HomeAt(FakeHomeContent.board(), actions, onState = { state = it })
            }
        }
        compose.waitForIdle()
    }

    /** One action inside a frame, as input arrives: that frame composes it. */
    private fun dispatchInAFrame(action: Action) {
        compose.runOnIdle { scope.launch { withFrameNanos { state.dispatch(action) } } }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun aSystemColumnRebuiltWithTheFocusFarDownShowsIt() {
        // As after search closes: the strip is composed anew, its list state at the top.
        show(downToSystem15)
        assertEquals(FOCUSED_SYSTEM, state.focus.systemIndex)
        compose.onNodeWithTag(HomeTags.system(FOCUSED_SYSTEM), useUnmergedTree = true).assertIsDisplayed()
        assertRowWhole(HomeTags.system(FOCUSED_SYSTEM), HomeTags.SYSTEMS)
    }

    @Test
    fun steppingUpPastTheGroupGapKeepsEachSystemWhole() {
        show(downToSystem15, animations = false)
        repeat(FOCUSED_SYSTEM) {
            compose.runOnIdle { state.dispatch(Action.Move(Direction.Up)) }
            compose.waitForIdle()
            assertRowWhole(HomeTags.system(state.focus.systemIndex), HomeTags.SYSTEMS)
        }
        assertEquals(0, state.focus.systemIndex)
    }

    @Test
    fun aHeldMoveInTheSystemColumnSwapsThePreviewWithoutAFade() {
        show(listOf(Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Move(Direction.Down)))
        val atRest = inkOf(HomeTags.GAMES)
        compose.mainClock.autoAdvance = false
        repeat(4) {
            dispatchInAFrame(Action.Move(Direction.Down))
            val ink = inkOf(HomeTags.GAMES)
            assertTrue("the new preview is drawn at once ($ink of $atRest)", ink * 2 > atRest)
        }
    }

    @Test
    fun r1WithTheGamesFocusedFadesTheNextListIn() {
        show(listOf(Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter))
        val atRest = inkOf(HomeTags.GAMES)
        compose.mainClock.autoAdvance = false
        dispatchInAFrame(Action.NextSystem)
        val ink = inkOf(HomeTags.GAMES)
        assertTrue("the next list starts faded ($ink of $atRest)", ink * 2 < atRest)
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue("and is drawn in full after the fade", inkOf(HomeTags.GAMES) * 2 > atRest)
    }

    /** Pixels of [tag] that differ from its most common colour (the background). */
    private fun inkOf(tag: String): Int {
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        val colors = buildList { for (x in 0 until pixels.width) for (y in 0 until pixels.height) add(pixels[x, y]) }
        val background = colors.groupingBy { it }.eachCount().maxBy { it.value }.key
        return colors.count { it != background }
    }

    private fun assertRowWhole(
        row: String,
        list: String,
    ) {
        val r = compose.onNodeWithTag(row, useUnmergedTree = true).getBoundsInRoot()
        val l = compose.onNodeWithTag(list, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("$row at ${r.top}..${r.bottom} inside $list ${l.top}..${l.bottom}", r.top >= l.top && r.bottom <= l.bottom)
    }

    private companion object {
        const val FOCUSED_SYSTEM = 15
    }
}
