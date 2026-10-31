package com.plumemu.coreui.components.keyboard

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Touch and cost of [PlumKeyboard]: one tap types, the whole cell takes the tap, and a focus
 * move recomposes only the two keys involved while nothing runs at rest. The
 * recompositions are counted with Compose's composition tracer, as in the app's HomeRecompositionTest.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class PlumKeyboardTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val events = mutableListOf<KeyboardEvent>()
    private val spec = KeyboardSpec(actionLabel = "Done", hasShift = true)
    private val state = KeyboardState(spec.startModel())

    @Before
    fun traceCompositions() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    calls += info
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stopTracing() = Composer.setTracer(null)

    /** [trayToBottom] false: the keyboard's own height, so its last row's cells reach 2 dp past it. */
    private fun show(trayToBottom: Boolean = true) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) { KeyboardOnScreen(state, spec, trayToBottom, onEvent = { events += it }) }
        }
        compose.waitForIdle()
    }

    private fun tap(label: String) {
        compose.onNodeWithTag(keyboardKeyTag(state.model.idOf(label))).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun keyCells(): Int = calls.count { it.startsWith("com.plumemu.coreui.components.keyboard.KeyCell ") }

    private fun keyboards(): Int = calls.count { it.startsWith("com.plumemu.coreui.components.keyboard.PlumKeyboard ") }

    @Test
    fun aTapOnEveryKindOfKeyDoesItsJob() {
        show()
        tap("q")
        tap("SHIFT")
        assertEquals(ShiftMode.Once, state.shift)
        tap("w")
        assertEquals(ShiftMode.Off, state.shift)
        tap("DEL")
        tap("SPACE")
        tap("ACTION")
        tap("HIDE")
        tap("123")
        assertEquals(KeyboardPage.Symbols, state.page)
        tap("1")
        tap("=\\<")
        assertEquals(KeyboardPage.Symbols2, state.page)
        tap("~")
        tap("#+=")
        assertEquals(KeyboardPage.Symbols, state.page)
        tap("ABC")
        assertEquals(KeyboardPage.Letters, state.page)
        assertEquals(
            listOf(
                KeyboardEvent.Type('q'), KeyboardEvent.Type('W'), KeyboardEvent.Delete, KeyboardEvent.Space,
                KeyboardEvent.Action, KeyboardEvent.Hide, KeyboardEvent.Type('1'), KeyboardEvent.Type('~'),
            ),
            events,
        )
        // The tapped key keeps the focus, ready for the gamepad.
        assertEquals(state.model.idOf("123"), state.focus)
    }

    @Test
    fun theIconKeysAreNamedForScreenReaders() {
        show()
        compose.onNodeWithTag(keyboardKeyTag(state.model.idOf("SHIFT"))).assertContentDescriptionEquals("Shift")
        compose.onNodeWithTag(keyboardKeyTag(state.model.idOf("DEL"))).assertContentDescriptionEquals("Delete")
        compose.onNodeWithTag(keyboardKeyTag(state.model.idOf("HIDE"))).assertContentDescriptionEquals("Hide keyboard")
    }

    /** The gaps belong to the keys: a tap there types the key whose half of the gap it is. */
    @Test
    fun theWholeCellTakesTheTapGapsIncluded() {
        show(trayToBottom = false)
        val px = compose.density.density
        // In the gap just left of w (its half): w, not q.
        compose.onNodeWithTag(keyboardKeyTag(state.model.idOf("w"))).performTouchInput { click(Offset(0.5f * px, centerY)) }
        // In the tray's padding just above q.
        compose.onNodeWithTag(keyboardKeyTag(state.model.idOf("q"))).performTouchInput { click(Offset(centerX, 0.5f * px)) }
        // Under the space bar, in the last 2 dp of its cell, which reach past the keyboard's own height.
        compose.onNodeWithTag(keyboardKeyTag(state.model.idOf("SPACE"))).performTouchInput { click(Offset(centerX, height - 0.5f * px)) }
        compose.waitForIdle()
        assertEquals(listOf(KeyboardEvent.Type('w'), KeyboardEvent.Type('q'), KeyboardEvent.Space), events)
    }

    @Test
    fun aFocusMoveRecomposesOnlyTheTwoKeys() {
        show()
        compose.runOnIdle { calls.clear() }
        compose.runOnIdle { state.move(KeyboardDirection.Right) }
        compose.waitForIdle()
        assertEquals("keys recomposed: $calls", 2, keyCells())
        assertEquals("the keyboard itself does not recompose", 0, keyboards())

        compose.runOnIdle { calls.clear() }
        compose.runOnIdle { state.move(KeyboardDirection.Down) }
        compose.waitForIdle()
        assertEquals("keys recomposed on Down: $calls", 2, keyCells())
        assertEquals(0, keyboards())
    }

    @Test
    fun shiftRecomposesOnlyTheLettersAndTheShiftKey() {
        show()
        compose.runOnIdle { calls.clear() }
        compose.runOnIdle { state.toggleShift() }
        compose.waitForIdle()
        assertEquals("26 letters and shift: $calls", 27, keyCells())
        assertEquals(0, keyboards())
    }

    @Test
    fun nothingRunsWhileIdle() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { calls.clear() }
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertTrue("composed at rest: $calls", calls.isEmpty())
        // No animation or frame callback is waiting either.
        compose.mainClock.advanceTimeByFrame()
        assertTrue(calls.isEmpty())
    }
}
