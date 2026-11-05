package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Writes the buttons at the board's 1.5x scale to build/v2compare for a side-by-side with the drawing (not a regression shot); only with V2COMPARE=1, so a verify run without build/ does not look for them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w400dp-h300dp-land-hdpi")
class PlumButtonCompareTest {
    private fun shot(name: String, light: Boolean) {
        assumeTrue(System.getenv("V2COMPARE") != null)
        val settings = if (light) PlumThemeSettings.Paper else PlumThemeSettings.Graphite
        captureRoboImage("../build/v2compare/${name}_ours.png") {
            PlumTheme(settings) {
                Column(Modifier.background(PlumTheme.colors.bg).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PlumButton("Play", ButtonKind.Primary, focused = false)
                    PlumButton("Cancel", ButtonKind.Secondary, focused = false)
                    PlumButton("Cancel", ButtonKind.Secondary, focused = true)
                }
            }
        }
    }

    @Test fun dark() = shot("buttons_dark", false)

    @Test fun light() = shot("buttons_light", true)
}
