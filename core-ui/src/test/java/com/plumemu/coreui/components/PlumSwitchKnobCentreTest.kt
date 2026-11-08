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
 * The flat thumb sits on the track's centre line: measured in pixels on the Thor's 3x density and on 2x, both themes, on and off, focused and not.
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
        val thumb = if (on) colors.collar.flat.thumb else colors.collar.flat.thumbOff
        val x = (if (on) lip + trackW - trackH / 2 else lip + trackH / 2).toInt()
        val tol = 10f / 255
        // The flat thumb is one solid colour: the rows of its colour down the thumb's own column are its body (its 1 dp shadow and the track differ from it).
        // The off thumb has the off outline's colour, which also paints the track's top and bottom rows: take only the run through the centre.
        val centreRow = (lip + trackH / 2).toInt()
        var first = centreRow
        var last = centreRow
        while (px[x, first - 1].dist(thumb) < tol) first--
        while (px[x, last + 1].dist(thumb) < tol) last++
        val rows = if (px[x, centreRow].dist(thumb) < tol) (first..last).toList() else emptyList()
        assertTrue("no thumb found at x=$x", rows.isNotEmpty())
        val top = rows.first()
        val bottom = rows.last() + 1
        assertTrue("the thumb's rows are not one run: $top..$bottom (${rows.size} rows)", rows.size == bottom - top)
        val thumbCentre = (top + bottom) / 2f
        val trackCentre = lip + trackH / 2f
        val off = (thumbCentre - trackCentre) / d
        assertTrue("thumb rows $top..$bottom centre $thumbCentre, track centre $trackCentre: off by $off dp", abs(off) <= 0.5f)
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
