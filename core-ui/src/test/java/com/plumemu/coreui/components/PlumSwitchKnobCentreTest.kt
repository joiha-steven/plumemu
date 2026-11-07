package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.plumemu.coreui.theme.Depth
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SwitchSliderDimens
import com.plumemu.coreui.theme.TextMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The knob's body plus its solid edge (not the drop shadow) sits on the track's centre line (a deliberate change from the Controls board, whose body
 * alone is centred): measured in pixels on the Thor's 3x density and on 2x, both themes, on and off, focused and not.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h360dp-land-xxhdpi")
open class PlumSwitchKnobCentreTest {
    @get:Rule
    val compose = createComposeRule()

    private fun Color.dist(o: Color) = abs(red - o.red) + abs(green - o.green) + abs(blue - o.blue)

    private fun check(settings: PlumThemeSettings, on: Boolean, focused: Boolean) {
        val colors = PlumComposeColors.from(PlumColors.derive(settings.backgroundColor, TextMode.of(settings.textColor, settings.backgroundColor), settings.accentColor))
        compose.setContent {
            PlumTheme(settings) {
                Box(Modifier.background(PlumTheme.colors.bg).testTag("sw").padding(16.dp)) { PlumSwitch(on = on, focused = focused) }
            }
        }
        compose.waitForIdle()
        val px = compose.onNodeWithTag("sw").captureToImage().toPixelMap()
        val d = compose.density.density
        val lip = SwitchSliderDimens.switchLip.value * d + 16 * d // the 16 dp of padding around the switch is in the capture
        val trackH = SwitchSliderDimens.switchTrackHeight.value * d
        val trackW = SwitchSliderDimens.switchTrackWidth.value * d
        val knob = SwitchSliderDimens.switchKnob.value * d
        val seat = colors.collar.seat
        val x = (if (on) lip + trackW - 2 * d - knob / 2 else lip + 2 * d + knob / 2).toInt()
        val tol = 10f / 255
        // From the row after the track's first (at a fractional density that row is half lip, half track: a step that is not the knob).
        val rows = (lip.toInt() + 1 until (lip + trackH).toInt() + (6 * d).toInt())
        // The body's top is the first hard step down the column, its highlight row included (the track's own inset shadow fades gently).
        val step = rows.firstOrNull { it > rows.first() && px[x, it].dist(px[x, it - 1]) > 0.25f }
        assertTrue("no knob body found at x=$x", step != null)
        val top = step!!
        // The body ends where its solid side starts (the off track is nearly the side's colour, so look only below the knob's middle).
        val side = rows.filter { it > top + knob / 2 && px[x, it].dist(seat.side) < tol }
        assertTrue("no knob side found at x=$x", side.isNotEmpty())
        // The thick edge is the run of side-coloured rows under the body; the knob (body plus edge) is what must sit centred.
        // The shadow under it is nearly the same colour on a dark theme, so the run stops at the edge's thickness (`SeatLook.cap` thick, 2 dp, at `Depth.THICKNESS`).
        val edge = 2 * d * Depth.THICKNESS
        var end = side.first()
        while (end + 1 < rows.last && end + 1 - side.first() < edge - 0.5f && px[x, end + 1].dist(seat.side) < tol) end++
        val bottom = end + 1
        val knobCentre = (top + bottom) / 2f
        val trackCentre = lip + trackH / 2f
        val off = (knobCentre - trackCentre) / d
        assertTrue("knob body plus edge rows $top..$bottom centre $knobCentre, track centre $trackCentre: off by $off dp", abs(off) <= 0.5f)
    }

    @Test fun graphiteOn() = check(PlumThemeSettings.Graphite, on = true, focused = false)

    @Test fun graphiteOff() = check(PlumThemeSettings.Graphite, on = false, focused = false)

    @Test fun graphiteOnFocused() = check(PlumThemeSettings.Graphite, on = true, focused = true)

    @Test fun graphiteOffFocused() = check(PlumThemeSettings.Graphite, on = false, focused = true)

    @Test fun paperOn() = check(PlumThemeSettings.Paper, on = true, focused = false)

    @Test fun paperOff() = check(PlumThemeSettings.Paper, on = false, focused = false)

    @Test fun paperOnFocused() = check(PlumThemeSettings.Paper, on = true, focused = true)

    @Test fun paperOffFocused() = check(PlumThemeSettings.Paper, on = false, focused = true)
}

/** The same at 2x, where the switch's fractional dp sizes round differently. */
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PlumSwitchKnobCentreXhdpiTest : PlumSwitchKnobCentreTest()

/** The Thor's density is not a whole number; its fractional sizes must round the same way on both sides of the knob. */
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w731dp-h411dp-land-420dpi")
class PlumSwitchKnobCentre420Test : PlumSwitchKnobCentreTest()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w698dp-h393dp-land-440dpi")
class PlumSwitchKnobCentre440Test : PlumSwitchKnobCentreTest()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-400dpi")
class PlumSwitchKnobCentre400Test : PlumSwitchKnobCentreTest()
