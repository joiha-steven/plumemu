package com.plumemu.home

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.game.FakeGameActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L1 / R1 change the grid size while a grid has the games focus; in a list they jump a
 * letter; L2 / R2 (key or analog trigger) switch the system everywhere. The stored setting is stood in for by [written], which the theme
 * reads back the way the real settings flow does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeGridSizeKeysTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private val written = mutableListOf<GridSize>()
    private var stored by mutableStateOf(GridSize.Medium)
    private lateinit var router: InputRouter

    /** Failure callbacks of the writes, in order; a test calls one to say that write could not be saved. */
    private val failures = mutableListOf<() -> Unit>()

    private fun show(
        content: HomeContent = FakeHomeContent.withArtwork(),
        followsWrites: Boolean = true,
    ) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme(PlumThemeSettings.Default.copy(gridSize = stored)) {
                HomeScreen(
                    content,
                    router,
                    HomeCallbacks(
                        games = games,
                        onGridSize = { size, onFailed ->
                            written += size
                            failures += onFailed
                            if (followsWrites) stored = size
                        },
                    ),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hasText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Super Nintendo's grid with the second row's first game focused: game 4 at Medium (4 columns). */
    private fun openSnesGridOnSecondRow() {
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter, Action.ToggleView)
        send(Action.Move(Direction.Down))
    }

    private fun focusedGame(): String {
        games.launched.clear()
        send(Action.Play)
        return games.launched.single()
    }

    private fun firstCoverColumns(): Int {
        val first = compose.onNodeWithTag(HomeTags.cover(0), useUnmergedTree = true).getUnclippedBoundsInRoot()
        return (0..12).filter { count(HomeTags.cover(it)) == 1 }.count {
            compose.onNodeWithTag(HomeTags.cover(it), useUnmergedTree = true).getUnclippedBoundsInRoot().top == first.top
        }
    }

    @Test
    fun r1MakesTheGridLargerAndL1SmallerKeepingTheGame() {
        show()
        openSnesGridOnSecondRow()
        assertEquals("Medium has 4 landscape columns", 4, firstCoverColumns())
        assertEquals("snes-4", focusedGame())
        send(Action.NextSystem)
        assertEquals(listOf(GridSize.Large), written)
        assertEquals("Large has 3 columns", 3, firstCoverColumns())
        assertEquals("the same game, still in the grid", "snes-4", focusedGame())
        send(Action.PreviousSystem, Action.PreviousSystem)
        assertEquals(listOf(GridSize.Large, GridSize.Medium, GridSize.Small), written)
        assertEquals("Small has 5 columns", 5, firstCoverColumns())
        assertEquals("snes-4", focusedGame())
        assertEquals("still the same system's grid", 1, count(HomeTags.GRID))
    }

    @Test
    fun theEndsDoNothing() {
        stored = GridSize.Large
        show()
        openSnesGridOnSecondRow()
        send(Action.NextSystem)
        assertEquals("snes-3", focusedGame())
        assertEquals("R1 at Large writes nothing", emptyList<GridSize>(), written)
        assertEquals("and the system stays", 1, count(HomeTags.GRID))
        stored = GridSize.Small
        compose.waitForIdle()
        send(Action.PreviousSystem)
        assertEquals("L1 at Small writes nothing", emptyList<GridSize>(), written)
        assertEquals("the game is still the one under the first column of row 2", "snes-3", focusedGame())
    }

    @Test
    fun twoQuickPressesStepTwiceBeforeTheSettingIsBack() {
        show(followsWrites = false)
        openSnesGridOnSecondRow()
        send(Action.PreviousSystem, Action.PreviousSystem)
        assertEquals("Medium, L1 to Small, then nothing further", listOf(GridSize.Small), written)
    }

    @Test
    fun inAListR1JumpsALetterAndNeverSwitchesTheSystem() {
        show()
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)
        val first = focusedGame()
        send(Action.NextSystem)
        val jumped = focusedGame()
        assertTrue("R1 stayed in Super Nintendo: $jumped", jumped.startsWith("snes-"))
        assertTrue("and moved to another letter's first game: $first to $jumped", first != jumped)
        send(Action.PreviousSystem)
        assertEquals("L1 jumped back", first, focusedGame())
        assertEquals("no size asked in a list", emptyList<GridSize>(), written)
    }

    @Test
    fun l2AndR2SwitchTheSystemFromAListAndFromAGameGridByKeyAndByTrigger() {
        show()
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)
        pressKey(KeyEvent.KEYCODE_BUTTON_R2)
        assertTrue("R2 key in a list: the next system", !focusedGame().startsWith("snes-"))
        pressKey(KeyEvent.KEYCODE_BUTTON_L2)
        assertTrue("L2 key: back to Super Nintendo", focusedGame().startsWith("snes-"))
        send(Action.ToggleView)
        assertEquals("a grid", 1, count(HomeTags.GRID))
        pullTrigger(MotionEvent.AXIS_RTRIGGER)
        assertEquals("the right trigger in a game grid: the next system's list", 0, count(HomeTags.GRID))
        assertTrue(!focusedGame().startsWith("snes-"))
        pullTrigger(MotionEvent.AXIS_LTRIGGER)
        assertEquals("the left trigger: back to Super Nintendo's grid", 1, count(HomeTags.GRID))
        assertEquals("no size was asked on the way", emptyList<GridSize>(), written)
    }

    @Test
    fun withTheSystemsColumnFocusedL2AndR2SwitchSystemsAndL1R1DoNot() {
        show()
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter, Action.ToggleView)
        send(Action.Back)
        send(Action.NextSystem, Action.PreviousSystem)
        assertEquals("on the systems column R1 / L1 switch nothing", emptyList<GridSize>(), written)
        send(Action.Enter)
        assertEquals("still Super Nintendo's grid", 1, count(HomeTags.GRID))
        send(Action.Back, Action.JumpLetterNext, Action.Enter)
        assertTrue("R2 on the systems column is the next system: its games are focused", !focusedGame().startsWith("snes-"))
        assertEquals(emptyList<GridSize>(), written)
        send(Action.Back, Action.JumpLetterPrevious, Action.Enter)
        assertEquals("back on Super Nintendo, its grid is still a grid", 1, count(HomeTags.GRID))
    }

    @Test
    fun withTheSystemsColumnFocusedL1AndR1ChangeNothingVisible() {
        show()
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)
        send(Action.Back)
        assertTrue("the systems column has the focus", hasText("Select"))
        send(Action.NextSystem, Action.PreviousSystem, Action.NextSystem)
        assertTrue("still the systems column", hasText("Select"))
        send(Action.Enter)
        assertTrue("still Super Nintendo's list", focusedGame().startsWith("snes-"))
        assertEquals(emptyList<GridSize>(), written)
    }

    @Test
    fun fromTheGridBackThenR2SwitchesTheSystem() {
        show()
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter, Action.ToggleView)
        send(Action.Back, Action.JumpLetterNext, Action.Enter)
        assertEquals("the next system is a list", 0, count(HomeTags.GRID))
        assertEquals("nothing was written", emptyList<GridSize>(), written)
    }

    /** A game grid: the D-pad alone (no stick, no L1 / R1) goes left to the systems column, and Down there changes the system. */
    @Test
    fun dpadLeftFromAGameGridReachesTheSystemsColumnAndDownChangesTheSystem() {
        show()
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter, Action.ToggleView)
        assertTrue("a grid", count(HomeTags.GRID) == 1)
        dpad(KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue("the systems column has the focus: its own hint", hasText("Select"))
        dpad(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("the next system is a list", 0, count(HomeTags.GRID))
        assertEquals("nothing was written", emptyList<GridSize>(), written)
    }

    private fun dpad(code: Int) = pressKey(code)

    private fun pressKey(code: Int) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            compose.runOnIdle { router.onKeyEvent(KeyEvent(0L, 0L, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD)) }
            compose.waitForIdle()
        }
    }

    /** An analog trigger pulled all the way and let go (the Thor's L2 / R2 are analog), as `Activity.dispatchGenericMotionEvent` gets it. */
    private fun pullTrigger(axis: Int) {
        for (value in listOf(1f, 0f)) {
            val properties = MotionEvent.PointerProperties().apply { id = 0 }
            val coords = MotionEvent.PointerCoords().apply { setAxisValue(axis, value) }
            val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_JOYSTICK, 0)
            compose.runOnIdle { router.onGenericMotionEvent(event) }
            compose.waitForIdle()
        }
    }

    @Test
    fun theHintSaysSwitchSystemEverywhereAndSizeInTheGridOnly() {
        show()
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)
        assertTrue(hasText("Switch system") && !hasText("Size"))
        send(Action.ToggleView)
        assertTrue("a game grid has both", hasText("Size") && hasText("Switch system"))
        assertTrue("SELECT still offers the list", hasText("List view"))
        send(Action.ToggleView)
        assertTrue(hasText("Switch system") && !hasText("Size"))
    }

    @Test
    fun tappingTheSwitchSystemHintSwitchesTheSystem() {
        show()
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)
        compose.onNodeWithTag(HomeTags.hint(Action.JumpLetterNext), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTrue("the tap went to the next system, not to a letter", !focusedGame().startsWith("snes-"))
    }

    @Test
    fun searchDoesNotSeeTheSizeChange() {
        show()
        openSnesGridOnSecondRow()
        send(Action.Search, Action.NextSystem, Action.PreviousSystem)
        assertEquals("search is open: L1 R1 are not the grid's", emptyList<GridSize>(), written)
    }

    private fun tapSize() {
        compose.onNodeWithTag(HomeTags.hint(Action.NextSystem), useUnmergedTree = true).performClick() // the Size hint: L1 R1
        compose.waitForIdle()
    }

    @Test
    fun tappingTheSizeHintCyclesIncludingTheWrapFromLargeToSmall() {
        show()
        openSnesGridOnSecondRow()
        tapSize()
        tapSize()
        tapSize()
        tapSize()
        assertEquals(
            "Medium: Large, Small (the wrap), Medium, Large",
            listOf(GridSize.Large, GridSize.Small, GridSize.Medium, GridSize.Large),
            written,
        )
        assertEquals("the same game", "snes-4", focusedGame())
    }

    @Test
    fun aLateEchoOfAnEarlierStepDoesNotMakeTheNextPressStartOverFromIt() {
        stored = GridSize.Large
        show(followsWrites = false)
        openSnesGridOnSecondRow()
        send(Action.PreviousSystem, Action.PreviousSystem)
        assertEquals(listOf(GridSize.Medium, GridSize.Small), written)
        // The first write reaches the theme only now; the second is still on its way.
        stored = GridSize.Medium
        compose.waitForIdle()
        send(Action.NextSystem)
        assertEquals("counted from Small, the size asked for, not from the Medium shown", GridSize.Medium, written.last())
        assertEquals(3, written.size)
    }

    @Test
    fun aFailedWriteTellsThePersonAndLetsTheNextPressCountFromWhatIsShown() {
        show(followsWrites = false)
        openSnesGridOnSecondRow()
        send(Action.NextSystem)
        assertEquals(listOf(GridSize.Large), written)
        compose.runOnIdle { failures.single()() }
        compose.waitForIdle()
        assertTrue("the save message is shown", hasText("That change could not be saved"))
        send(Action.Enter)
        assertEquals("the message closed", false, hasText("That change could not be saved"))
        send(Action.NextSystem)
        assertEquals("Medium is still what is stored, so R1 asks for Large again", listOf(GridSize.Large, GridSize.Large), written)
    }
}
