package com.plumemu.coreinput.strip

import android.os.Bundle
import android.os.Parcel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.stripDrag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumMotionState
import com.plumemu.coreui.theme.WidthClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class StripStateTest {
    @get:Rule
    val compose = createComposeRule()

    private val content =
        StripContent(
            listOf(SystemEntry("recent", 0), SystemEntry("favorites", 3), SystemEntry("snes", 248)),
            hasBoxArt = true,
        )

    private fun geometryOf(layout: PlumLayout) =
        StripGeometry(
            isNarrow = layout.widthClass == WidthClass.Narrow,
            leadingMargin = PlumDimens.stripMarginSide,
            systemsWidth = PlumDimens.systemsColumnWidth,
            gamesListWidth = layout.gamesListWidth,
            columnGap = PlumDimens.columnGap,
            peek = layout.peek,
            detailsStopListPeek = PlumDimens.detailsStopListPeek,
        )

    private val rp5 = geometryOf(PlumLayout(768.dp, 432.dp))
    private val spec = PlumMotionState(isOn = true).slideSpec<Float>()

    private fun px(dp: Float): Float = with(compose.density) { dp.dp.toPx() }

    private fun showStrip(
        isMotionOn: Boolean = true,
        geometry: () -> StripGeometry = { rp5 },
    ): StripState {
        lateinit var state: StripState
        compose.setContent {
            state = rememberStripState(content, geometry(), isMotionOn, spec)
            Box(Modifier.size(768.dp, 432.dp).testTag("strip").stripDrag(state))
        }
        compose.waitForIdle()
        return state
    }

    private fun StripState.toSnesGames() {
        dispatch(Action.Move(Direction.Down))
        dispatch(Action.Move(Direction.Down))
        dispatch(Action.Enter)
    }

    @Test
    fun focusSurvivesAConfigurationChange() {
        val tester = StateRestorationTester(compose)
        lateinit var state: StripState
        tester.setContent { state = rememberStripState(content, rp5, isMotionOn = true, animationSpec = spec) }
        compose.runOnIdle {
            state.toSnesGames()
            repeat(5) { state.dispatch(Action.Move(Direction.Down)) }
        }
        compose.waitForIdle()
        val before = state.focus
        tester.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(before, state.focus)
            assertEquals(Column.Games to 5, state.focus.column to state.focus.row)
            assertEquals("restored at its stop, not sliding", px(-176f), state.translationX)
            assertFalse(state.isMoving)
        }
    }

    @Test
    fun focusSurvivesProcessDeathThroughAParcel() {
        val focus = StripFocus(
            Column.Details,
            systemIndex = 2,
            systemId = "snes",
            rows = mapOf("snes" to 41, "favorites" to 2),
            rowIds = mapOf("snes" to "1041", "favorites" to "77"),
        )
        val parcel = Parcel.obtain()
        try {
            StripFocusBundle.save(focus).writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = Bundle.CREATOR.createFromParcel(parcel)
            assertEquals(focus, StripFocusBundle.restore(restored))
        } finally {
            parcel.recycle()
        }
        assertEquals("an empty bundle restores the default", StripFocus(), StripFocusBundle.restore(Bundle()))
    }

    @Test
    fun withMotionOffAFocusChangeJumps() {
        val state = showStrip(isMotionOn = false)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.toSnesGames() }
        assertEquals(px(-176f), state.translationX)
        assertFalse(state.isMoving)
    }

    @Test
    fun withMotionOnAFocusChangeSlidesIn180Ms() {
        val state = showStrip(isMotionOn = true)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.toSnesGames() }
        compose.mainClock.advanceTimeBy(PlumMotion.DURATION_MS / 2L)
        assertTrue(state.isMoving)
        assertTrue("half way: ${state.translationX}", state.translationX < 0f && state.translationX > px(-176f))
        compose.mainClock.advanceTimeBy(PlumMotion.DURATION_MS.toLong())
        assertEquals(px(-176f), state.translationX)
        assertFalse("at rest: nothing runs", state.isMoving)
    }

    @Test
    fun aRowMoveDuringTheSlideLetsItRunOnInsteadOfStartingOver() {
        val state = showStrip(isMotionOn = true)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.toSnesGames() }
        compose.mainClock.advanceTimeBy(PlumMotion.DURATION_MS / 2L)
        val halfWay = state.translationX
        // Down in the games list while the strip still slides there: same stop, no fresh 180 ms.
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
        assertEquals("the move itself does not jump the strip", halfWay, state.translationX)
        compose.mainClock.advanceTimeBy(PlumMotion.DURATION_MS / 2L + FRAME_MS)
        assertEquals("arrives when the first slide ends", px(-176f), state.translationX)
        assertFalse(state.isMoving)
    }

    @Test
    fun aMoveBackDuringTheSlideTurnsItAround() {
        val state = showStrip(isMotionOn = true)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.toSnesGames() }
        compose.mainClock.advanceTimeBy(PlumMotion.DURATION_MS / 2L)
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Left)) }
        compose.mainClock.advanceTimeBy(PlumMotion.DURATION_MS.toLong() + FRAME_MS)
        assertEquals("back at the systems stop", 0f, state.translationX)
        assertFalse(state.isMoving)
    }

    @Test
    fun systemNamesFadeWithTheStripsPosition() {
        val state = showStrip(isMotionOn = true)
        assertEquals("systems stop: names shown", 1f, state.systemNamesAlpha)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.toSnesGames() }
        assertEquals("the slide has not moved yet: still shown", 1f, state.systemNamesAlpha)
        compose.mainClock.advanceTimeBy(PlumMotion.DURATION_MS / 2L)
        val x = state.translationX
        assertEquals("half way, as far as the strip is", x / px(-176f), 1f - state.systemNamesAlpha, 0.001f)
        assertTrue(state.systemNamesAlpha > 0f && state.systemNamesAlpha < 1f)
        compose.mainClock.advanceTimeBy(PlumMotion.DURATION_MS.toLong())
        assertEquals("games stop: hidden", 0f, state.systemNamesAlpha)
    }

    @Test
    fun namesAlphaIsLinearBetweenTheStopsAndClampedPastThem() {
        assertEquals(1f, namesAlphaAt(0f, systemsStop = 0f, gamesStop = -176f, isSystemsFocused = false))
        assertEquals(0.5f, namesAlphaAt(-88f, systemsStop = 0f, gamesStop = -176f, isSystemsFocused = false))
        assertEquals(0f, namesAlphaAt(-176f, systemsStop = 0f, gamesStop = -176f, isSystemsFocused = true))
        assertEquals("the narrow details stop lies past the games stop", 0f, namesAlphaAt(-600f, 0f, -176f, false))
        assertEquals("a drag past the systems stop", 1f, namesAlphaAt(20f, 0f, -176f, false))
        assertEquals(1f, namesAlphaAt(0f, 0f, 0f, isSystemsFocused = true))
        assertEquals(0f, namesAlphaAt(0f, 0f, 0f, isSystemsFocused = false))
    }

    @Test
    fun aDragFollowsTheFingerAndAShortSlowOneSettlesBack() {
        val state = showStrip()
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) } // favorites: games exist
        compose.onNodeWithTag("strip").performTouchInput {
            down(center)
            moveBy(Offset(-px(40f), 0f))
            moveBy(Offset(-px(10f), 0f))
        }
        compose.runOnIdle {
            assertTrue("follows the finger: ${state.translationX}", state.translationX < 0f && state.translationX >= px(-50f))
        }
        compose.onNodeWithTag("strip").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(Column.Systems, state.focus.column)
        assertEquals(0f, state.translationX)
    }

    @Test
    fun aLongSlowDragSettlesOnTheNextStopAndFocusesIt() {
        val state = showStrip()
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
        compose.onNodeWithTag("strip").performTouchInput {
            swipe(center, center - Offset(px(120f), 0f), durationMillis = 2_000)
        }
        compose.waitForIdle()
        assertEquals(Column.Games, state.focus.column)
        assertEquals(px(-176f), state.translationX)
    }

    @Test
    fun aShortFlingGoesOnToTheNextStop() {
        val state = showStrip()
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
        compose.onNodeWithTag("strip").performTouchInput {
            swipe(center, center - Offset(px(50f), 0f), durationMillis = 40)
        }
        compose.waitForIdle()
        assertEquals(Column.Games, state.focus.column)
    }

    @Test
    fun theStripCannotBeDraggedPastItsStopsOrOntoAnEmptySystem() {
        val state = showStrip()
        // Recent is empty: Systems is the only stop.
        compose.onNodeWithTag("strip").performTouchInput {
            down(center)
            moveBy(Offset(-px(300f), 0f))
        }
        compose.runOnIdle { assertEquals(0f, state.translationX) }
        compose.onNodeWithTag("strip").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(Column.Systems, state.focus.column)
    }

    @Test
    fun aNewPeekMovesTheRestingStripWithoutAnimation() {
        var geometry by mutableStateOf(rp5)
        val state = showStrip { geometry }
        compose.runOnIdle { state.toSnesGames() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        geometry = geometryOf(PlumLayout(768.dp, 432.dp, peek = PlumDimens.peekWidthMax))
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("peek 120: 120 − 224", px(-104f), state.translationX)
        assertFalse(state.isMoving)
    }

    @Test
    fun aRescanThatRemovesSystemsIsReconciledInTheSameComposition() {
        var systems by mutableStateOf(List(10) { SystemEntry("system$it", itemCount = 4) })
        lateinit var state: StripState
        val shown = mutableListOf<String>()
        compose.setContent {
            val current = StripContent(systems, hasBoxArt = true)
            state = rememberStripState(current, rp5, isMotionOn = false, animationSpec = spec)
            // What a screen does with the focus in the same composition.
            shown += current.systems[state.focus.systemIndex].id
        }
        compose.runOnIdle { repeat(8) { state.dispatch(Action.Move(Direction.Down)) } }
        compose.waitForIdle()
        assertEquals("system8", shown.last())
        systems = systems.take(5)
        compose.waitForIdle()
        assertEquals("system4", shown.last())
        assertEquals(4, state.focus.systemIndex)
    }

    @Test
    fun aFocusRestoredWhileTheLibraryIsStillEmptySurvivesUntilItArrives() {
        val tester = StateRestorationTester(compose)
        var current by mutableStateOf(content)
        lateinit var state: StripState
        tester.setContent { state = rememberStripState(current, rp5, isMotionOn = true, animationSpec = spec) }
        compose.runOnIdle {
            state.toSnesGames()
            repeat(5) { state.dispatch(Action.Move(Direction.Down)) }
        }
        compose.waitForIdle()
        // After process death the library loads after the first frame.
        current = StripContent(emptyList(), hasBoxArt = true)
        compose.waitForIdle()
        tester.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) } // pressed before the systems arrive
        compose.waitForIdle()
        current = content
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("snes", state.focus.systemId)
            assertEquals(Column.Games to 5, state.focus.column to state.focus.row)
        }
    }

    /**
     * The app's second screen takes the details (layout C): the entry loses its details stop while
     * the cursor stands on it. The cursor is on the list in that same composition, on the same row,
     * the strip resting at the games stop; when the stop comes back the cursor stays on the list.
     */
    @Test
    fun aDetailsStopThatGoesAwayPutsTheCursorOnTheListForGood() {
        val nova = geometryOf(PlumLayout(576.dp, 432.dp))
        val withDetails = content
        val listOnly = content.copy(systems = content.systems.map { it.copy(hasDetails = false) })
        var current by mutableStateOf(withDetails)
        lateinit var state: StripState
        val seen = mutableListOf<Pair<Column, Float>>()
        compose.setContent {
            state = rememberStripState(current, nova, isMotionOn = false, animationSpec = spec)
            seen += state.focus.column to state.translationX
        }
        compose.runOnIdle {
            state.toSnesGames()
            repeat(3) { state.dispatch(Action.Move(Direction.Down)) }
            state.dispatch(Action.Move(Direction.Right))
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Column.Details, state.focus.column) }
        seen.clear()
        current = listOnly
        compose.waitForIdle()
        val gamesStop = px(nova.offsetOf(Column.Games).value)
        assertEquals("the first composition with the new content already rests on the list", Column.Games to gamesStop, seen.first())
        compose.runOnIdle {
            assertEquals(3, state.focus.row)
            assertFalse(state.isMoving)
        }
        // Nothing pressed meanwhile: the stop comes back and the cursor must not go back to it.
        current = withDetails
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("back with details: still on the list", Column.Games to 3, state.focus.column to state.focus.row)
            assertEquals(gamesStop, state.translationX)
        }
        current = listOnly
        compose.waitForIdle()
        compose.runOnIdle {
            state.dispatch(Action.Move(Direction.Right))
            assertEquals("no details stop to move to", Column.Games, state.focus.column)
        }
    }

    @Test
    fun aSlideToADetailsStopThatGoesAwayEndsAtTheList() {
        val nova = geometryOf(PlumLayout(576.dp, 432.dp))
        var current by mutableStateOf(content)
        lateinit var state: StripState
        compose.setContent { state = rememberStripState(current, nova, isMotionOn = true, animationSpec = spec) }
        compose.runOnIdle { state.toSnesGames() }
        compose.mainClock.advanceTimeBy(PlumMotion.DURATION_MS.toLong() * 2)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Right)) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertTrue("sliding to the details stop", state.isMoving)
        current = content.copy(systems = content.systems.map { it.copy(hasDetails = false) })
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        assertFalse("the slide ended", state.isMoving)
        assertEquals(Column.Games, state.focus.column)
        assertEquals(px(nova.offsetOf(Column.Games).value), state.translationX)
        compose.mainClock.advanceTimeBy(PlumMotion.DURATION_MS.toLong() * 2)
        assertEquals("and stays there", px(nova.offsetOf(Column.Games).value), state.translationX)
    }

    private companion object {
        const val FRAME_MS = 16L
    }

    // Push right to play through the state: the arming, its timeout and what disarms it.
    private fun armedState(clock: LongArray): StripState {
        val state = showStrip()
        compose.runOnIdle {
            state.pushRightPlays = true
            state.pushRightArm = PushRightArm { clock[0] }
            state.toSnesGames()
        }
        return state
    }

    private val rightKey = Action.Move(Direction.Right)

    @Test
    fun rightThenRightPlaysAndOneRightOnlyArms() {
        val clock = longArrayOf(0L)
        val state = armedState(clock)
        compose.runOnIdle {
            assertEquals(emptyList<StripIntent>(), state.dispatch(rightKey))
            assertTrue(state.isPushRightArmed)
            assertEquals(listOf(StripIntent.Play("snes", 0)), state.dispatch(rightKey))
            assertFalse("playing puts the arming away", state.isPushRightArmed)
        }
    }

    @Test
    fun rightDownRightDoesNotPlay() {
        val state = armedState(longArrayOf(0L))
        compose.runOnIdle {
            state.dispatch(rightKey)
            state.dispatch(Action.Move(Direction.Down))
            assertFalse(state.isPushRightArmed)
            assertEquals(emptyList<StripIntent>(), state.dispatch(rightKey))
            assertTrue("it arms the new row instead", state.isPushRightArmed)
        }
    }

    @Test
    fun rightWaitPastTheTimeoutRightDoesNotPlay() {
        val clock = longArrayOf(0L)
        val state = armedState(clock)
        compose.runOnIdle {
            state.dispatch(rightKey)
            clock[0] = PushRightArm.TIMEOUT_MS + 1
            assertEquals(emptyList<StripIntent>(), state.dispatch(rightKey))
            // Just inside the timeout it still plays.
            clock[0] += PushRightArm.TIMEOUT_MS
            assertEquals(listOf(StripIntent.Play("snes", 0)), state.dispatch(rightKey))
        }
    }

    @Test
    fun aHeldRightNeverPlaysAndDoesNotDisarm() {
        val state = armedState(longArrayOf(0L))
        compose.runOnIdle {
            state.dispatch(rightKey)
            repeat(5) { assertEquals(emptyList<StripIntent>(), state.dispatch(Action.Move(Direction.Right, isRepeat = true))) }
            assertTrue(state.isPushRightArmed)
        }
    }

    @Test
    fun backAndMenuDisarm() {
        val state = armedState(longArrayOf(0L))
        compose.runOnIdle {
            state.dispatch(rightKey)
            state.dispatch(Action.Menu)
            assertFalse(state.isPushRightArmed)
            state.dispatch(rightKey)
            state.dispatch(Action.Back)
            assertFalse(state.isPushRightArmed)
        }
    }

    @Test
    fun withTheOptionOffRightNeverArms() {
        val state = showStrip()
        compose.runOnIdle {
            state.toSnesGames()
            assertEquals(emptyList<StripIntent>(), state.dispatch(rightKey))
            assertFalse(state.isPushRightArmed)
            assertEquals(emptyList<StripIntent>(), state.dispatch(rightKey))
        }
    }

    @Test
    fun theArmingFollowsTheGameNotTheRowWhenARescanRemovesIt() {
        val ids = listOf("a", "b", "c")
        val withIds = StripContent(listOf(SystemEntry("recent", 0), SystemEntry("favorites", 3), SystemEntry("snes", 3, itemIds = ids)), hasBoxArt = true)
        lateinit var state: StripState
        compose.setContent { state = rememberStripState(withIds, rp5, true, spec) }
        compose.runOnIdle {
            state.pushRightPlays = true
            state.pushRightArm = PushRightArm { 0L }
            state.toSnesGames()
            state.dispatch(Action.Move(Direction.Down))
            assertEquals(emptyList<StripIntent>(), state.dispatch(rightKey))
            assertTrue(state.isPushRightArmed)
            // The armed game "b" leaves: the row stays 1 and now holds "c".
            state.content = StripContent(listOf(SystemEntry("recent", 0), SystemEntry("favorites", 3), SystemEntry("snes", 2, itemIds = listOf("a", "c"))), hasBoxArt = true)
            assertFalse(state.isPushRightArmed)
            assertEquals("the second Right must not play the game that moved up", emptyList<StripIntent>(), state.dispatch(rightKey))
        }
    }
}
