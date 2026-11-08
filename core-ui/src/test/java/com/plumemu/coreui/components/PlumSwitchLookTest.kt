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
 * The flat switch measured in pixels (docs/design/rules.md > Flat parts): a 40 x 22.7 dp track, the on track one solid accent (every row of a
 * column the same, no gradient), the off track one solid sunken tone with its hairline, a solid light thumb of 18.7 dp, and no glow or lip
 * round it (the page's own colour right outside the track).
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
        val flat = colors.collar.flat
        // Size: the node is the lip plus the track.
        assertEquals((trackW + 2 * lip).toInt(), on.width)
        // The on track: one solid accent down a column left of the knob, past the rounded end (the hairline is the first pixel row).
        val trackX = (lip + trackH / 2 + 2 * d).toInt()
        for (y in (lip + 3 * d).toInt()..(lip + trackH - 3 * d).toInt()) {
            assertTrue("on track at row $y is ${on[trackX, y]}, wanted ${colors.accent}", on[trackX, y].sum(colors.accent) < 4f / 255)
        }
        // The off track: the sunken ladder tone, with its 1.5 dp outline inside the top edge.
        val offX = (lip + trackW - 4 * d).toInt()
        val hairX = (lip + trackW * 0.6f).toInt()
        assertTrue("off track ${off[offX, mid]}", off[offX, mid].sum(flat.track) < 4f / 255)
        val edgeRow = (lip + 0.75f * d).toInt()
        assertTrue("off track outline ${off[hairX, edgeRow]}", off[hairX, edgeRow].sum(flat.trackEdge) < 12f / 255)
        // The thumb: the solid light tone when on (right end), the outline's colour when off (left end).
        val knobR = (lip + trackW - trackH / 2).toInt()
        val knobL = (lip + trackH / 2).toInt()
        assertTrue("on thumb ${on[knobR, mid]}", on[knobR, mid].sum(flat.thumb) < 6f / 255)
        assertTrue("off thumb ${off[knobL, mid]}", off[knobL, mid].sum(flat.thumbOff) < 6f / 255)
        // No glow and no lip: just outside the track both are the page's own colour.
        val atLip = lip.toInt() - 2
        assertTrue("beside the on track ${on[atLip, mid]}", on[atLip, mid].sum(colors.bg) < 3f / 255)
        assertTrue("beside the off track ${off[atLip, mid]}", off[atLip, mid].sum(colors.bg) < 3f / 255)
    }

    @Test fun graphite() = check(PlumThemeSettings.Graphite)

    @Test fun paper() = check(PlumThemeSettings.Paper)
}
