package com.plumemu.home

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.plumemu.R
import com.plumemu.coredata.settings.SwitchSystemWithSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.game.FakeGameActions
import com.plumemu.settings.LocalShoulderKeys
import com.plumemu.settings.ShoulderKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Controls › Switch system with: L2 / R2 (the default) or L1 / R1 switch
 * system on the main screen, and the other pair does what the first did: a letter jump in a list, Size in a grid.
 * Keys and analog triggers go through the router as a handheld sends them; the hint text and taps follow the setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeSwitchSystemWithTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private val sizes = mutableListOf<GridSize>()
    private lateinit var router: InputRouter

    private fun show(setting: SwitchSystemWithSetting) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme(PlumThemeSettings.Default.copy(gridSize = GridSize.Medium)) {
                CompositionLocalProvider(LocalShoulderKeys provides ShoulderKeys.of(setting)) {
                    HomeScreen(FakeHomeContent.withArtwork(), router, HomeCallbacks(games = games, onGridSize = { size, _ -> sizes += size }))
                }
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

    private fun key(code: Int) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            compose.runOnIdle { router.onKeyEvent(KeyEvent(0L, 0L, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD)) }
            compose.waitForIdle()
        }
    }

    private fun trigger(axis: Int) {
        for (value in listOf(1f, 0f)) {
            val properties = MotionEvent.PointerProperties().apply { id = 0 }
            val coords = MotionEvent.PointerCoords().apply { setAxisValue(axis, value) }
            val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_JOYSTICK, 0)
            compose.runOnIdle { router.onGenericMotionEvent(event) }
            compose.waitForIdle()
        }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun focusedGame(): String {
        games.launched.clear()
        send(Action.Play)
        return games.launched.single()
    }

    private fun openSnesList() = send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)

    private fun inSnes() = focusedGame().startsWith("snes-")

    @Test
    fun withL1R1ChosenKeysAndTriggersOfL1R1SwitchSystemAndL2R2JumpALetter() {
        show(SwitchSystemWithSetting.Bumpers)
        openSnesList()
        val first = focusedGame()
        key(KeyEvent.KEYCODE_BUTTON_R1)
        assertTrue("R1 switched to the next system", !inSnes())
        key(KeyEvent.KEYCODE_BUTTON_L1)
        assertEquals("L1 switched back", first, focusedGame())
        key(KeyEvent.KEYCODE_BUTTON_R2)
        val jumped = focusedGame()
        assertTrue("R2 stayed in the system: $jumped", inSnes())
        assertTrue("and jumped to another letter: $first to $jumped", first != jumped)
        trigger(MotionEvent.AXIS_LTRIGGER)
        assertEquals("the left trigger jumped back a letter", first, focusedGame())
        trigger(MotionEvent.AXIS_RTRIGGER)
        assertTrue("the right trigger jumped a letter, not a system", inSnes() && first != focusedGame())
    }

    @Test
    fun withL1R1ChosenL2R2ChangeSizeInAGridAndL1R1SwitchTheSystemFromIt() {
        show(SwitchSystemWithSetting.Bumpers)
        openSnesList()
        send(Action.ToggleView)
        assertEquals("a grid", 1, count(HomeTags.GRID))
        key(KeyEvent.KEYCODE_BUTTON_R2)
        trigger(MotionEvent.AXIS_RTRIGGER)
        assertEquals("R2 as key and as trigger asked for Large (the second is already there: nothing more)", listOf(GridSize.Large), sizes)
        assertEquals("still the grid", 1, count(HomeTags.GRID))
        key(KeyEvent.KEYCODE_BUTTON_L2)
        assertEquals("L2 asks for the size below the one asked for", GridSize.Medium, sizes.last())
        key(KeyEvent.KEYCODE_BUTTON_R1)
        assertEquals("R1 switched to the next system's list", 0, count(HomeTags.GRID))
    }

    @Test
    fun theDefaultKeepsTodaysRoles() {
        show(SwitchSystemWithSetting.Triggers)
        openSnesList()
        send(Action.ToggleView)
        key(KeyEvent.KEYCODE_BUTTON_R1)
        assertEquals("R1 asks for Large in a grid", listOf(GridSize.Large), sizes)
        key(KeyEvent.KEYCODE_BUTTON_R2)
        assertEquals("R2 switched system", 0, count(HomeTags.GRID))
        trigger(MotionEvent.AXIS_LTRIGGER)
        assertEquals("the left trigger came back", 1, count(HomeTags.GRID))
    }

    private fun tapSwitchSystemHint(setting: SwitchSystemWithSetting) {
        show(setting)
        openSnesList()
        compose.onNodeWithTag(HomeTags.hint(Action.JumpLetterNext), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTrue("a tap on Switch system went to the next system under $setting", !inSnes())
    }

    @Test
    fun tappingTheSwitchSystemHintSwitchesTheSystemWithL2R2() = tapSwitchSystemHint(SwitchSystemWithSetting.Triggers)

    @Test
    fun tappingTheSwitchSystemHintSwitchesTheSystemWithL1R1() = tapSwitchSystemHint(SwitchSystemWithSetting.Bumpers)
}
