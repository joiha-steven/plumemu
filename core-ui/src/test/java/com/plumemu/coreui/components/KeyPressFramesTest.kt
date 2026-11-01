package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A flat-face key goes down into its edge while pressed, sits higher while focused, and returns to rest after a
 * cancelled press (design/rules.md > Controls). The drawn offset is the first row of the picture that is not the
 * background, read at the key's middle column; the key's layout never moves. xhdpi: 1 dp is 2 px.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class KeyPressFramesTest {
    @get:Rule
    val compose = createComposeRule()

    private val held = mutableStateOf<Set<String>>(emptySet())
    private val focused = mutableStateOf(true)
    private val settings = PlumThemeSettings.Graphite
    private val colors = PlumComposeColors.from(PlumColors.derive(settings.backgroundColor, settings.textMode, settings.accentColor))
    private val firstKeyComposes = mutableIntStateOf(0)
    private val secondKeyComposes = mutableIntStateOf(0)

    private fun show(themeSettings: PlumThemeSettings = settings) {
        compose.setContent {
            PlumTheme(themeSettings) {
                CompositionLocalProvider(LocalHeldKeys provides held) {
                    Column(Modifier.testTag(FRAME).background(PlumTheme.colors.bg).padding(16.dp)) {
                        Key(focused.value, FIRST, firstKeyComposes)
                        Key(false, SECOND, secondKeyComposes)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun Key(
        isFocused: Boolean,
        tag: String,
        composes: androidx.compose.runtime.MutableIntState,
    ) {
        SideEffect { composes.intValue++ }
        val state = rememberKeyState()
        Box(Modifier.padding(bottom = 16.dp).testTag(tag).size(80.dp, 24.dp).flatKey(state, isFocused, { it.keyFace }, 12.dp, PlumTheme.colors))
    }

    private fun near(a: Color, b: Color) = abs(a.red - b.red) < 0.02f && abs(a.green - b.green) < 0.02f && abs(a.blue - b.blue) < 0.02f

    /** The first picture row (px) of the first key's column that is not the background. */
    private fun faceTop(): Int {
        val image = compose.onNodeWithTag(FRAME).captureToImage().toPixelMap()
        val x = 16 * 2 + 80
        for (y in 0 until image.height) if (!near(image[x, y], colors.bg)) return y
        error("no key drawn")
    }

    private fun hold(vararg keys: String) {
        held.value = keys.toSet()
        Snapshot.sendApplyNotifications()
    }

    private fun settle() = compose.mainClock.advanceTimeBy(PlumMotion.PRESS_OUT_MS + PlumMotion.PRESS_MIN_VISIBLE_MS + 100)

    @Test
    fun restThenPressedThenBackAtRest() {
        show()
        compose.mainClock.autoAdvance = false
        settle()
        val raised = faceTop()
        hold("A")
        compose.mainClock.advanceTimeBy(PlumMotion.PRESS_IN_MS + 40L)
        // Down by the edge (2 dp) from where it rests raised (1 dp up): 3 dp, 6 px.
        assertEquals("pressed: the face is 3 dp lower", raised + 6, faceTop())
        hold()
        settle()
        assertEquals("released: back to rest", raised, faceTop())
    }

    @Test
    fun aFocusedKeySitsHigherThanAnUnfocusedOne() {
        focused.value = false
        show()
        compose.mainClock.autoAdvance = false
        settle()
        val flat = faceTop()
        focused.value = true
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(PlumMotion.RAISE_MS + 100L)
        assertEquals("focused: 1 dp (2 px) higher", flat - 2, faceTop())
        focused.value = false
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(PlumMotion.RAISE_MS + 100L)
        assertEquals("unfocused again: at rest height", flat, faceTop())
    }

    @Test
    fun aPressCancelledByLosingTheFocusReturnsToRest() {
        show()
        compose.mainClock.autoAdvance = false
        settle()
        hold("A")
        compose.mainClock.advanceTimeBy(PlumMotion.PRESS_IN_MS + 40L)
        focused.value = false
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(PlumMotion.RAISE_MS + 100L)
        val rest = faceTop()
        hold()
        settle()
        assertEquals("never stuck down: A is still held, yet the face is at rest height", rest, faceTop())
        focused.value = true
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(PlumMotion.RAISE_MS + 100L)
        assertEquals("focus back with the button already down: nothing pressed", rest - 2, faceTop())
    }

    @Test
    fun aFingerDraggedOffAfterPressingReturnsToRest() {
        focused.value = false
        show()
        compose.mainClock.autoAdvance = false
        settle()
        val rest = faceTop()
        compose.onNodeWithTag(FIRST).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(400)
        assertTrue("finger down: pressed", faceTop() > rest)
        compose.onNodeWithTag(FIRST).performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(400f, 0f)) }
        compose.mainClock.advanceTimeBy(PlumMotion.PRESS_OUT_MS + 300L)
        assertEquals("dragged off: back at rest", rest, faceTop())
        compose.onNodeWithTag(FIRST).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        assertEquals("lifted: still at rest", rest, faceTop())
    }

    @Test
    fun withAnimationsOffEveryChangeIsInstant() {
        show(settings.copy(animationsOn = false))
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(16)
        val raised = faceTop()
        hold("A")
        compose.mainClock.advanceTimeBy(16)
        assertEquals("pressed in one frame", raised + 6, faceTop())
        hold()
        compose.mainClock.advanceTimeBy(16)
        assertEquals("released in one frame, no minimum visible time", raised, faceTop())
        focused.value = false
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(16)
        assertEquals("unfocused in one frame", raised + 2, faceTop())
    }

    @Test
    fun aPressRecomposesNoKey() {
        show()
        compose.mainClock.autoAdvance = false
        val first = firstKeyComposes.intValue
        val second = secondKeyComposes.intValue
        hold("A")
        compose.mainClock.advanceTimeBy(300)
        hold()
        compose.mainClock.advanceTimeBy(500)
        assertEquals("the pressed key is not recomposed", first, firstKeyComposes.intValue)
        assertEquals("the other key is not recomposed", second, secondKeyComposes.intValue)
    }

    private companion object {
        const val FRAME = "frame"
        const val FIRST = "first"
        const val SECOND = "second"
    }

    @Test
    fun anUnfocusedKeyRunsNothingAndHoverRaisesIt() {
        var state: KeyState? = null
        compose.setContent {
            PlumTheme(settings) {
                val key = rememberKeyState()
                state = key
                Box(Modifier.testTag(FIRST).size(80.dp, 24.dp).flatKey(key, false, { it.keyFace }, 12.dp, PlumTheme.colors))
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(300)
        val key = state!!
        assertTrue("no lift animation running", !key.lift.isRunning)
        assertTrue("no press animation running", !key.dip.progress.isRunning)
        assertEquals(0f, key.raise, 0f)
        compose.onNodeWithTag(FIRST).performMouseInput { enter(center) }
        compose.mainClock.advanceTimeBy(PlumMotion.RAISE_MS + 100L)
        assertEquals("mouse over: raised", 1f, key.raise, 0.01f)
        compose.onNodeWithTag(FIRST).performMouseInput { exit(Offset(-50f, -50f)) }
        compose.mainClock.advanceTimeBy(PlumMotion.RAISE_MS + 100L)
        assertEquals("mouse gone: at rest", 0f, key.raise, 0.01f)
    }

    @Test
    fun aRowReleasesIn100MsAndAKeyIn140() {
        val row = PressDip()
        val key = PressDip(PlumMotion.KEY_PRESS_OUT_MS)
        compose.setContent {
            androidx.compose.runtime.LaunchedEffect(Unit) { row.press(true); row.release(true) }
            androidx.compose.runtime.LaunchedEffect(Unit) { key.press(true); key.release(true) }
        }
        compose.mainClock.autoAdvance = false
        // The dip is full by 60 ms; step the clock and note when each reaches rest. The two start together; a key (140 ms out) must reach rest after a row (100 ms out).
        var rowSeen = false
        var keySeen = false
        var rowBackAt = -1L
        var keyBackAt = -1L
        var t = 0L
        while (t < 600L && keyBackAt < 0) {
            compose.mainClock.advanceTimeBy(8)
            t += 8
            if (row.progress.value > 0f) rowSeen = true
            if (key.progress.value > 0f) keySeen = true
            run {
                if (rowSeen && rowBackAt < 0 && row.progress.value == 0f) rowBackAt = t
                if (keySeen && keyBackAt < 0 && key.progress.value == 0f) keyBackAt = t
            }
        }
        assertTrue("row came back", rowBackAt > 0)
        assertTrue("key came back", keyBackAt > 0)
        val gap = keyBackAt - rowBackAt
        assertTrue("a key comes back later than a row (gap $gap ms)", gap > 0)
    }
}
