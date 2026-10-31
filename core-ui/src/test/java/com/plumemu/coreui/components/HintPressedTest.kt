package com.plumemu.coreui.components

import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
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
 * A hint's key cap looks pressed while its button is held and while a finger is down on it
 * (design/rules.md › Shapes and motion › Pressed): the cap fills with the text colour, its letter takes
 * the background colour, and the cap dips to 0.92. Read from the drawn pixels, with the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HintPressedTest {
    @get:Rule
    val compose = createComposeRule()

    private val held = mutableStateOf<Set<String>>(emptySet())
    private val settings = PlumThemeSettings.Graphite
    private val colors = PlumComposeColors.from(PlumColors.derive(settings.backgroundColor, settings.textMode, settings.accentColor))

    private fun show(keyCap: String = "A", themeSettings: PlumThemeSettings = settings) {
        compose.setContent {
            PlumTheme(themeSettings) {
                CompositionLocalProvider(LocalHeldKeys provides held) { Hint(keyCap, "Select", Modifier.testTag(HINT)) }
            }
        }
        compose.waitForIdle()
    }

    private fun Color.near(other: Color) = abs(red - other.red) < 0.03f && abs(green - other.green) < 0.03f && abs(blue - other.blue) < 0.03f

    /** The cap's pixel at [fx] of its width, mid-height. */
    private fun pixel(fx: Float): Color {
        val image = compose.onNodeWithTag(HINT_KEY_CAP_TAG, useUnmergedTree = true).captureToImage()
        return image.toPixelMap()[(image.width * fx).toInt(), image.height / 2]
    }

    private fun hold(vararg keys: String) {
        held.value = keys.toSet()
        Snapshot.sendApplyNotifications()
    }

    private fun isFilled() = pixel(0.16f).near(colors.text)

    @Test
    fun capNamesReadTheCapText() {
        assertEquals(setOf("A"), keyCapNames("A"))
        assertEquals(setOf("L1", "R1"), keyCapNames("L1 R1"))
        assertEquals(setOf("↑", "↓"), keyCapNames("↑↓"))
        assertEquals(setOf("→"), keyCapNames("›"))
        assertEquals(setOf("START"), keyCapNames("START"))
    }

    @Test
    fun theCapFillsWithTheTextColourWhileItsButtonIsHeldAndLetsGo() {
        show("A")
        assertFalse("at rest the cap is a ring", isFilled())
        compose.mainClock.autoAdvance = false
        hold("A")
        compose.mainClock.advanceTimeBy(PlumMotion.PRESS_IN_MS + 40L)
        assertTrue("pressed: filled with the text colour", isFilled())
        // The cap is smaller than its box: its edge column is no longer ring.
        assertFalse("pressed: dipped, the box edge is empty", pixel(0f).near(colors.text))
        hold()
        compose.mainClock.advanceTimeBy(PlumMotion.PRESS_OUT_MS + 200L)
        assertFalse("released: back to the ring", isFilled())
    }

    @Test
    fun anotherButtonDoesNotLightTheCap() {
        show("A")
        compose.mainClock.autoAdvance = false
        hold("B", "START")
        compose.mainClock.advanceTimeBy(300)
        assertFalse(isFilled())
    }

    @Test
    fun aHintForTwoButtonsLightsForEither() {
        show("L1 R1")
        compose.mainClock.autoAdvance = false
        hold("R1")
        compose.mainClock.advanceTimeBy(300)
        assertTrue(isFilled())
    }

    @Test
    fun aPressShorterThanTheDipStillShowsTheFullDip() {
        show("A")
        compose.mainClock.autoAdvance = false
        hold("A")
        compose.mainClock.advanceTimeBy(20)
        hold() // released after 20 ms
        compose.mainClock.advanceTimeBy(PlumMotion.PRESS_IN_MS + 20L)
        assertTrue("80 ms after the press, still pressed", isFilled())
        compose.mainClock.advanceTimeBy(PlumMotion.PRESS_OUT_MS + 300L)
        assertFalse("then it lets go", isFilled())
    }

    @Test
    fun withAnimationsOffTheReleaseIsImmediateEvenForAQuickTap() {
        show("A", settings.copy(animationsOn = false))
        compose.mainClock.autoAdvance = false
        hold("A")
        compose.mainClock.advanceTimeBy(20)
        assertTrue("pressed at once", isFilled())
        hold() // released after 20 ms: with motion on the dip would stay until 100 ms
        compose.mainClock.advanceTimeBy(16) // one frame
        assertFalse("animations off: let go in the next frame, no minimum visible time", isFilled())
    }

    @Test
    fun aFingerDownOnTheHintPressesItAndLiftingReleasesIt() {
        show("A")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(HINT).performTouchInput { down(center) }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(300)
        assertTrue("finger down: pressed", isFilled())
        compose.onNodeWithTag(HINT).performTouchInput { up() }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(500)
        assertFalse("finger up: released", isFilled())
    }

    @Test
    fun withAnimationsOffTheFillChangesButTheCapDoesNotShrink() {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show("A")
        compose.mainClock.autoAdvance = false
        hold("A")
        compose.mainClock.advanceTimeBy(100)
        assertTrue("filled", isFilled())
        assertTrue("not dipped: the box edge is the cap's ring", pixel(0f).near(colors.text) || pixel(0.02f).near(colors.text))
    }

    private companion object {
        const val HINT = "hint_under_test"
    }
}
