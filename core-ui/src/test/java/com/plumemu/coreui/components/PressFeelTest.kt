package com.plumemu.coreui.components

import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A focused row looks pressed while the confirm button is held, and any row while a finger is down on it
 * (design/rules.md › Shapes and motion › Pressed). Read from the drawn pixels, with the test clock.
 * The fill is sampled at the row's top edge, centre: inside the fill at rest, the first pixel outside it once
 * the row has shrunk.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PressFeelTest {
    @get:Rule
    val compose = createComposeRule()

    private val held = mutableStateOf<Set<String>>(emptySet())

    /** The held keys as the rows see them, counting every read of the value. */
    private var heldReads = 0
    private val countingHeld =
        object : State<Set<String>> {
            override val value: Set<String> get() = held.value.also { heldReads++ }
        }
    private var focusedLook by mutableStateOf(GameRowLook.Focused)
    private val settings = PlumThemeSettings.Graphite
    private val colors = PlumComposeColors.from(PlumColors.derive(settings.backgroundColor, settings.textMode, settings.accentColor))
    private val pressedFill = lerp(colors.pill, colors.text, PlumMotion.PRESS_FILL_MIX)

    private fun show(confirmCap: String = "A", themeSettings: PlumThemeSettings = settings) {
        compose.setContent {
            PlumTheme(themeSettings) {
                CompositionLocalProvider(LocalHeldKeys provides countingHeld, LocalConfirmCap provides confirmCap) {
                    Column {
                        GameRow(title = "Focused", trailing = "1990", look = focusedLook, modifier = Modifier.testTag(FOCUSED))
                        GameRow(title = "Other", trailing = "1991", look = GameRowLook.Normal, modifier = Modifier.testTag(OTHER))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun Color.near(other: Color) = abs(red - other.red) < 0.02f && abs(green - other.green) < 0.02f && abs(blue - other.blue) < 0.02f

    /** Below the ring (2 dp, drawn inside the pill's edge), inside the fill. */
    private val FILL_Y = 10

    private fun fillAt(tag: String, y: Int): Color {
        val image = compose.onNodeWithTag(tag).captureToImage()
        return image.toPixelMap()[image.width / 2, y]
    }

    private fun wholeRow(tag: String): List<Int> {
        val map = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return buildList { for (y in 0 until map.height step 2) for (x in 0 until map.width step 3) add(map[x, y].hashCode()) }
    }

    private fun hold(vararg keys: String) {
        held.value = keys.toSet()
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(300)
    }

    private fun release() {
        held.value = emptySet()
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(500)
    }

    @Test
    fun holdingConfirmOnTheFocusedRowPressesItAndLettingGoReturnsToRest() {
        show()
        val resting = wholeRow(FOCUSED)
        assertTrue("rest: the focus fill", fillAt(FOCUSED, FILL_Y).near(colors.pill))
        hold("A")
        assertTrue("pressed: the fill moved a step toward the text colour", fillAt(FOCUSED, FILL_Y).near(pressedFill))
        release()
        assertTrue("released: the fill is back", fillAt(FOCUSED, FILL_Y).near(colors.pill))
        assertEquals("and the whole row is as it was", resting, wholeRow(FOCUSED))
    }

    @Test
    fun withAnimationsOffAQuickTapReleasesInTheNextFrame() {
        show(themeSettings = settings.copy(animationsOn = false))
        val resting = wholeRow(FOCUSED)
        held.value = setOf("A")
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(20)
        assertTrue("pressed at once", fillAt(FOCUSED, FILL_Y).near(pressedFill))
        held.value = emptySet()
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(16) // one frame; with motion on the dip would stay until 100 ms
        assertEquals("animations off: back to rest, no minimum visible time", resting, wholeRow(FOCUSED))
    }

    @Test
    fun theRowDipsWhilePressed() {
        show()
        val resting = wholeRow(FOCUSED)
        hold("A")
        assertTrue("the row's pixels changed (fill and scale)", resting != wholeRow(FOCUSED))
        // The first pixel row lies outside the 0.97 row: the background, not the ring that sits on the resting row's edge.
        assertFalse("shrunk: the top edge pixel is no longer the ring", fillAt(FOCUSED, 0).near(colors.accent))
    }

    @Test
    fun anUnfocusedRowDoesNotReactToTheButton() {
        show()
        val resting = wholeRow(OTHER)
        hold("A")
        assertEquals(resting, wholeRow(OTHER))
    }

    @Test
    fun theBackButtonDoesNotPress() {
        show()
        val resting = wholeRow(FOCUSED)
        hold("B")
        assertEquals("B is not the confirm button", resting, wholeRow(FOCUSED))
    }

    @Test
    fun underConfirmWithBTheButtonPrintedBPresses() {
        show(confirmCap = "B")
        val resting = wholeRow(FOCUSED)
        hold("A")
        assertEquals("A does not press when B confirms", resting, wholeRow(FOCUSED))
        release()
        hold("B")
        assertTrue("B presses", fillAt(FOCUSED, FILL_Y).near(pressedFill))
    }

    @Test
    fun aFingerDownOnAnyRowPressesItUntilItLifts() {
        show()
        val resting = wholeRow(OTHER)
        compose.onNodeWithTag(OTHER).performTouchInput { down(center) }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(pressFeelTouchDelayMs() + 300)
        assertTrue("finger down: the unfocused row dips", resting != wholeRow(OTHER))
        compose.onNodeWithTag(OTHER).performTouchInput { up() }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(500)
        assertEquals("finger up: back at rest", resting, wholeRow(OTHER))
    }

    @Test
    fun aPressShorterThanTheMinimumStillShowsTheDip() {
        show()
        held.value = setOf("A")
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(70)
        held.value = emptySet()
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(30)
        assertTrue("still pressed 100 ms after the press began", fillAt(FOCUSED, FILL_Y).near(pressedFill))
        compose.mainClock.advanceTimeBy(500)
        assertTrue("then released", fillAt(FOCUSED, FILL_Y).near(colors.pill))
    }

    @Test
    fun withAnimationsOffOnlyTheFillChanges() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show()
        hold("A")
        assertTrue("the fill changed", fillAt(FOCUSED, FILL_Y).near(pressedFill))
        assertTrue("and the row did not shrink: the top edge pixel is still the ring", fillAt(FOCUSED, 0).near(colors.accent))
    }

    @Test
    fun theRowsBoundsAreTheSameAtRestAndAfterAPress() {
        show()
        val before = compose.onNodeWithTag(FOCUSED).fetchSemanticsNode().boundsInRoot
        hold("A")
        release()
        assertEquals(before, compose.onNodeWithTag(FOCUSED).fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun keyEventsDoNotWakeUnfocusedRows() {
        focusedLook = GameRowLook.Normal
        show()
        heldReads = 0
        hold("A")
        release()
        hold("DOWN")
        assertEquals("no row is focused: nobody reads the held keys", 0, heldReads)
    }

    @Test
    fun aFingerThatStartsAScrollNeverPressesTheRow() {
        show()
        val resting = wholeRow(OTHER)
        compose.onNodeWithTag(OTHER).performTouchInput { down(center) }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(20)
        compose.onNodeWithTag(OTHER).performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, 80f)) }
        for (i in 0..8) {
            compose.mainClock.advanceTimeBy(50)
            assertEquals("a scroll began: the row stays at rest", resting, wholeRow(OTHER))
        }
        compose.onNodeWithTag(OTHER).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        assertEquals(resting, wholeRow(OTHER))
    }

    @Test
    fun aQuickTapStillShowsTheWholeDip() {
        show()
        val resting = wholeRow(OTHER)
        compose.onNodeWithTag(OTHER).performTouchInput { down(center) }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(20)
        compose.onNodeWithTag(OTHER).performTouchInput { up() }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(80)
        assertTrue("dipped after a 20 ms tap", resting != wholeRow(OTHER))
        compose.mainClock.advanceTimeBy(500)
        assertEquals("then at rest", resting, wholeRow(OTHER))
    }

    @Test
    fun aRowThatTakesTheFocusWhileTheButtonIsHeldStaysAtRestUntilItIsPressedAgain() {
        focusedLook = GameRowLook.Normal
        show()
        hold("A")
        focusedLook = GameRowLook.Focused
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(300)
        assertTrue("A is still down from before the focus: the fill is the plain focus fill", fillAt(FOCUSED, FILL_Y).near(colors.pill))
        release()
        hold("A")
        assertTrue("a new press shows", fillAt(FOCUSED, FILL_Y).near(pressedFill))
    }

    @Test
    fun aMarkThatMovesToAnotherItemWhileTheButtonIsHeldStaysAtRestUntilItIsPressedAgain() {
        // The home grid draws its own focus mark and names the focused cell; the D-pad moves it while A is held.
        var cell by mutableStateOf<Int?>(1)
        var scale: () -> Float = { 1f }
        compose.setContent {
            PlumTheme(settings) {
                CompositionLocalProvider(LocalHeldKeys provides countingHeld, LocalConfirmCap provides "A") {
                    scale = rememberConfirmPressScale { cell }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        hold("A")
        assertEquals("A on cell 1 dips it", PlumMotion.PRESS_SCALE, scale(), 0.001f)
        cell = 2
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(500)
        assertEquals("cell 2 took the focus while A was held: at rest", 1f, scale(), 0.001f)
        release()
        hold("A")
        assertEquals("a new press on cell 2 dips it", PlumMotion.PRESS_SCALE, scale(), 0.001f)
    }

    @Test
    fun aQuickTapInterruptedByTheButtonDoesNotLeaveTheRowDipped() {
        show()
        compose.onNodeWithTag(FOCUSED).performTouchInput { down(center); up() }
        compose.mainClock.advanceTimeBy(20)
        hold("A") // the button's driver takes the dip over mid-tap
        release()
        assertTrue("back at rest after A is let go", fillAt(FOCUSED, FILL_Y).near(colors.pill))
    }

    private companion object {
        const val FOCUSED = "press_focused"
        const val OTHER = "press_other"
    }
}
