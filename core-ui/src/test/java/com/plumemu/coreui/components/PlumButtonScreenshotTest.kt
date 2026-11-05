package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The four kinds at rest, focused, pressed and off, and a long label that wraps to two lines. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PlumButtonScreenshotTest {
    /** A key state held fully pressed. */
    @Composable
    private fun pressed(): KeyState {
        val state = rememberKeyState()
        LaunchedEffect(state) { state.dip.progress.snapTo(1f) }
        return state
    }

    @Test
    fun buttons() =
        ReferenceShots.capture("plum_button") {
            Column(
                Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                for ((kind, label) in listOf(ButtonKind.Primary to "Play", ButtonKind.Secondary to "Cancel", ButtonKind.Quiet to "Skip", ButtonKind.Danger to "Remove")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlumButton(label, kind, focused = false)
                        PlumButton(label, kind, focused = true)
                        PlumButton(label, kind, focused = false, state = pressed())
                        PlumButton(label, kind, focused = false, enabled = false)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlumButton("Choose another folder to scan", ButtonKind.Secondary, focused = false, modifier = Modifier.width(190.dp), maxLines = 2)
                    PlumButton("Choose another folder to scan", ButtonKind.Primary, focused = true, modifier = Modifier.width(190.dp), maxLines = 2)
                }
            }
        }

    /** Focused buttons alone with 40 dp of page all round, so no neighbour or edge cuts the glow (the shot to measure its reach in). */
    @Test
    fun focusGlowWithRoom() =
        ReferenceShots.capture("plum_button_glow", sizes = listOf(ReferenceShots.Size.Wide)) {
            Row(
                Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(40.dp),
                horizontalArrangement = Arrangement.spacedBy(80.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlumButton("Play", ButtonKind.Primary, focused = true)
                PlumButton("Cancel", ButtonKind.Secondary, focused = true)
            }
        }
}
