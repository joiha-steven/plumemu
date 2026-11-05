package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SwitchSliderDimens
import com.plumemu.coreui.theme.TextMode
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
 * The V2 switch measured against the Controls board (1 px = 1/1.5 dp): 40 x 22.7 dp track in a 4 dp lip, a 18.7 dp knob, the on track one flat
 * colour (the mean of the board's accent fade, no gradient), the knob the collar's cap face, and the on track's glow tinting the lip
 * (the board's `0 0 12px`), drawn with fading strokes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PlumSwitchLookTest {
    @get:Rule
    val compose = createComposeRule()

    private fun Color.sum(other: Color) = abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue)

    private fun check(settings: PlumThemeSettings) {
        val colors = PlumComposeColors.from(PlumColors.derive(settings.backgroundColor, TextMode.of(settings.textColor, settings.backgroundColor), settings.accentColor))
        compose.setContent {
            PlumTheme(settings) {
                Row(Modifier.background(PlumTheme.colors.bg)) {
                    Box(Modifier.padding(16.dp).testTag("off")) { PlumSwitch(on = false, focused = false) }
                    Box(Modifier.padding(16.dp).testTag("on")) { PlumSwitch(on = true, focused = false) }
                }
            }
        }
        compose.waitForIdle()
        val off = compose.onNodeWithTag("off").captureToImage().toPixelMap()
        val on = compose.onNodeWithTag("on").captureToImage().toPixelMap()
        val d = compose.density.density
        val lip = SwitchSliderDimens.switchLip.value * d
        val trackH = SwitchSliderDimens.switchTrackHeight.value * d
        val trackW = SwitchSliderDimens.switchTrackWidth.value * d
        val mid = (lip + trackH / 2).toInt()
        // Size: the node is the lip plus the track.
        assertEquals((trackW + 2 * lip).toInt(), on.width)
        // The on track: the board's `linear-gradient(accent, accentLight)`, top to bottom (its inset shadow fades out 4 dp in).
        val seat = colors.collar.seat
        val light = com.plumemu.coreui.theme.SeatLook.accentLight(colors.accent)
        // Sampled past the track's rounded end (where the inset shadow follows the curve) and left of the knob.
        val trackX = (lip + trackH / 2 + 2 * d).toInt()
        for (y in (lip + 4 * d).toInt()..(lip + trackH - 4 * d).toInt()) {
            val want = mix(colors.accent, light, ((y + 0.5f - lip) / trackH).coerceIn(0f, 1f))
            assertTrue("on track at row $y is ${on[trackX, y]}, wanted $want", on[trackX, y].sum(want) < 9f / 255)
        }
        // The off track is the WELL.
        assertTrue("off track ${off[(lip + trackW - 4 * d).toInt(), mid]}", off[(lip + trackW - 4 * d).toInt(), mid].sum(seat.well) < 3f / 255)
        // The knob is the CAP (its gradient's middle) on both (on: right end, off: left end).
        val cap = mix(seat.capStops[0], seat.capStops[1], 0.5f)
        val knobR = (lip + trackW - 2 * d - SwitchSliderDimens.switchKnob.value * d / 2).toInt()
        val knobL = (lip + 2 * d + SwitchSliderDimens.switchKnob.value * d / 2).toInt()
        assertTrue("on knob ${on[knobR, mid]}", on[knobR, mid].sum(cap) < 6f / 255)
        assertTrue("off knob ${off[knobL, mid]}", off[knobL, mid].sum(cap) < 6f / 255)
        // The glow: the lip beside an on track moves toward the accent by about the board's 0.12 (half of that at least), the off one is untouched.
        val atLip = (lip / 2).toInt() // the lip's middle, in the node's own coordinates
        val lipOff = off[atLip, mid]
        val lipOn = on[atLip, mid]
        val toAccent = lipOff.sum(colors.accent)
        assertTrue("the lip beside an on track moved ${lipOn.sum(lipOff)} of $toAccent", lipOn.sum(lipOff) >= 0.06f * toAccent)
        assertTrue("the lip must not be fully the accent", lipOn.sum(colors.accent) > 0.5f * toAccent)
    }

    @Test fun graphite() = check(PlumThemeSettings.Graphite)

    @Test fun paper() = check(PlumThemeSettings.Paper)

    private fun mix(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)
}
