package com.plumemu.coreui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PlumSwitchSliderInputTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fractionMapsKnobCentreRange() {
        assertEquals(0f, sliderFraction(0f, 200f, 20f), 0f)
        assertEquals(0f, sliderFraction(10f, 200f, 20f), 0f)
        assertEquals(0.5f, sliderFraction(100f, 200f, 20f), 0.0001f)
        assertEquals(1f, sliderFraction(190f, 200f, 20f), 0f)
        assertEquals(1f, sliderFraction(500f, 200f, 20f), 0f)
    }

    private fun slider(enabled: Boolean, got: MutableList<Float>, done: IntArray) =
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default) {
                PlumSlider(0.5f, focused = false, enabled = enabled, modifier = Modifier.width(200.dp).testTag("s"), onValueChange = { got += it }, onValueChangeFinished = { done[0]++ })
            }
        }

    @Test
    fun tapSetsValueUnderTheFinger() {
        val got = mutableListOf<Float>()
        val done = IntArray(1)
        slider(true, got, done)
        // The knob is 23 dp with its ring; the centre of the slider is 0.5.
        compose.onNodeWithTag("s").performTouchInput { click(center) }
        assertEquals(0.5f, got.last(), 0.01f)
        assertEquals(1, done[0])
        compose.onNodeWithTag("s").performTouchInput { click(Offset(width.toFloat(), centerY)) }
        assertEquals(1f, got.last(), 0f)
    }

    @Test
    fun dragRunsFromStartToEnd() {
        val got = mutableListOf<Float>()
        val done = IntArray(1)
        slider(true, got, done)
        compose.onNodeWithTag("s").performTouchInput { swipeRight() }
        assertTrue(got.size > 2)
        assertTrue(got.first() < 0.2f)
        assertEquals(1f, got.last(), 0.01f)
        assertEquals(got.sorted(), got)
        assertEquals(1, done[0])
    }

    @Test
    fun lockedSliderIgnoresInput() {
        val got = mutableListOf<Float>()
        val done = IntArray(1)
        slider(false, got, done)
        compose.onNodeWithTag("s").performTouchInput { click(Offset(10f, centerY)) }
        compose.onNodeWithTag("s").performTouchInput { swipeRight() }
        assertTrue(got.isEmpty())
        assertEquals(0, done[0])
    }

    @Test
    fun switchIsASwitchAndLockedIsDisabled() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default) {
                PlumSwitch(on = true, focused = false, modifier = Modifier.testTag("on"))
                PlumSwitch(on = false, focused = false, enabled = false, modifier = Modifier.testTag("locked"))
            }
        }
        compose.onNodeWithTag("on").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        compose.onNodeWithTag("locked").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
    }
}
