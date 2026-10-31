package com.plumemu.coreinput

import android.view.KeyEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `InputRouter.held`: the key caps whose button is down, for the hints to look pressed. Names are the
 * letters printed on the buttons (after layout and swap), so a cap lettered A lights for the button lettered A.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeldKeysTest {
    private fun routerTest(
        settings: InputSettings = InputSettings(),
        body: (InputRouter) -> Unit,
    ) = runTest { body(InputRouter(backgroundScope, settings, clock = { 0L }, onDebug = {}) { }) }

    private fun InputRouter.down(key: Int, device: Int = 0) = onKey(key, isDown = true, repeatCount = 0, deviceId = device)

    private fun InputRouter.up(key: Int, device: Int = 0) = onKey(key, isDown = false, repeatCount = 0, deviceId = device)

    @Test
    fun aKeyIsHeldFromDownToUp() =
        routerTest { router ->
            assertEquals(emptySet<String>(), router.held.value)
            router.down(KeyEvent.KEYCODE_BUTTON_A)
            assertEquals(setOf("A"), router.held.value)
            router.up(KeyEvent.KEYCODE_BUTTON_A)
            assertEquals(emptySet<String>(), router.held.value)
        }

    @Test
    fun androidsOwnRepeatsChangeNothing() =
        routerTest { router ->
            router.down(KeyEvent.KEYCODE_BUTTON_START)
            router.onKey(KeyEvent.KEYCODE_BUTTON_START, isDown = true, repeatCount = 3)
            assertEquals(setOf("START"), router.held.value)
        }

    @Test
    fun severalButtonsAreHeldTogether() =
        routerTest { router ->
            router.down(KeyEvent.KEYCODE_BUTTON_L1)
            router.down(KeyEvent.KEYCODE_BUTTON_R1)
            router.down(KeyEvent.KEYCODE_BUTTON_SELECT)
            assertEquals(setOf("L1", "R1", "SELECT"), router.held.value)
            router.up(KeyEvent.KEYCODE_BUTTON_R1)
            assertEquals(setOf("L1", "SELECT"), router.held.value)
        }

    @Test
    fun theSwapDoesNotRenameTheButtonsBecauseHintsPrintThePhysicalLetter() =
        routerTest(InputSettings(swapAB = true)) { router ->
            router.down(KeyEvent.KEYCODE_BUTTON_B)
            assertEquals("the button printed B lights the cap that says B (confirm)", setOf("B"), router.held.value)
            router.up(KeyEvent.KEYCODE_BUTTON_B)
            router.down(KeyEvent.KEYCODE_BUTTON_A)
            assertEquals(setOf("A"), router.held.value)
            router.up(KeyEvent.KEYCODE_BUTTON_A)
            router.down(KeyEvent.KEYCODE_DPAD_CENTER) // confirms, so it lights the confirm hint, which says B
            assertEquals(setOf("B"), router.held.value)
            router.up(KeyEvent.KEYCODE_DPAD_CENTER)
            router.down(KeyEvent.KEYCODE_ESCAPE)
            assertEquals(setOf("A"), router.held.value)
        }

    @Test
    fun theNintendoLayoutNamesButtonsByTheirPrintedLetter() =
        routerTest(InputSettings(buttonLayout = ButtonLayout.Nintendo)) { router ->
            router.down(KeyEvent.KEYCODE_BUTTON_B) // east
            assertEquals(setOf("A"), router.held.value)
        }

    @Test
    fun enterAndDpadCentreLightAAndBackLightsB() =
        routerTest { router ->
            router.down(KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals(setOf("A"), router.held.value)
            router.up(KeyEvent.KEYCODE_DPAD_CENTER)
            router.down(KeyEvent.KEYCODE_ESCAPE)
            assertEquals(setOf("B"), router.held.value)
        }

    @Test
    fun aControlSentByTwoSourcesStaysHeldUntilBothLetGo() =
        routerTest { router ->
            router.down(KeyEvent.KEYCODE_BUTTON_L2) // as a key
            router.onAxes(JoystickAxes(leftTrigger = 1f)) // and as an axis
            router.up(KeyEvent.KEYCODE_BUTTON_L2)
            assertEquals("the axis still holds it", setOf("L2"), router.held.value)
            router.onAxes(JoystickAxes())
            assertEquals(emptySet<String>(), router.held.value)
        }

    @Test
    fun triggersAndTheHatAreHeldWhileTheyAreOverTheEdge() =
        routerTest { router ->
            router.onAxes(JoystickAxes(rightTrigger = 1f, hatX = 1f))
            assertEquals(setOf("R2", "→"), router.held.value)
            router.onAxes(JoystickAxes(rightTrigger = 1f))
            assertEquals(setOf("R2"), router.held.value)
            router.onAxes(JoystickAxes())
            assertEquals(emptySet<String>(), router.held.value)
        }

    @Test
    fun losingWindowFocusClearsEverythingHeld() =
        routerTest { router ->
            router.down(KeyEvent.KEYCODE_BUTTON_A)
            router.onAxes(JoystickAxes(leftTrigger = 1f))
            assertEquals(setOf("A", "L2"), router.held.value)
            router.releaseAll()
            assertEquals(emptySet<String>(), router.held.value)
        }

    @Test
    fun aKeyCoreInputDoesNotTakeIsNeverHeld() =
        routerTest { router ->
            router.down(KeyEvent.KEYCODE_VOLUME_UP)
            assertEquals(emptySet<String>(), router.held.value)
        }
}
