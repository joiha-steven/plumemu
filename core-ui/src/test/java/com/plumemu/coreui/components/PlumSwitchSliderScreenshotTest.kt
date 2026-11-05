package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The switch (off, on, focused, locked) and the slider (rest, focused with its bubble, hue, locked). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PlumSwitchSliderScreenshotTest {
    @Composable
    private fun Switches() {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            PlumSwitch(on = false, focused = false)
            PlumSwitch(on = true, focused = false)
            PlumSwitch(on = true, focused = true)
            PlumSwitch(on = false, focused = false, enabled = false)
        }
    }

    @Composable
    private fun Sliders() {
        val w = Modifier.width(200.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                PlumSlider(0.3f, focused = false, modifier = w)
                Column {
                    Row(Modifier.padding(top = 28.dp)) { PlumSlider(0.3f, focused = true, valueText = "30", modifier = w) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                PlumSlider(0.62f, focused = false, hue = true, modifier = w)
                PlumSlider(0.3f, focused = false, enabled = false, modifier = w)
            }
        }
    }

    @Test
    fun states() =
        ReferenceShots.capture("switch_slider") {
            Column(Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(32.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Switches()
                Sliders()
            }
        }

    /** One focused slider and switch with 40 dp of page all round (the shot to measure the glow's reach in). */
    @Test
    fun focusGlowWithRoom() =
        ReferenceShots.capture("slider_glow", sizes = listOf(ReferenceShots.Size.Wide)) {
            Column(Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(40.dp), verticalArrangement = Arrangement.spacedBy(80.dp)) {
                PlumSlider(0.3f, focused = true, modifier = Modifier.width(200.dp))
                PlumSwitch(on = true, focused = true)
            }
        }

    /** The same parts at the board's scale (1.5x dp) for the side-by-side with the board; only with V2COMPARE=1. */
    @Test
    fun atBoardScale() {
        assumeTrue(System.getenv("V2COMPARE") != null)
        RuntimeEnvironment.setQualifiers("w512dp-h288dp-land-hdpi")
        for (preset in ReferenceShots.Preset.entries) {
            captureRoboImage("build/v2compare/switch_slider_${preset.label}_ours.png") {
                PlumTheme(preset.settings) {
                    Column(Modifier.background(PlumTheme.colors.bg).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Switches()
                        Sliders()
                    }
                }
            }
        }
    }
}
