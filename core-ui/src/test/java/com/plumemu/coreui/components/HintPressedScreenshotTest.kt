package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hint bar at rest and with A and L1 held (the pressed key caps, design/rules.md › Shapes and
 * motion › Pressed). A hint that appears while its button is down starts pressed, which is how the shot
 * gets the final look without waiting for the animation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HintPressedScreenshotTest {
    @Composable
    private fun Bar(held: Set<String>) {
        CompositionLocalProvider(LocalHeldKeys provides remember { mutableStateOf(held) }) {
            Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg), contentAlignment = Alignment.BottomStart) {
                HintBar {
                    Hint("A", "Select")
                    Hint("B", "Back")
                    Hint("L1 R1", "Size")
                    Hint("START", "Settings", hasDot = true)
                }
            }
        }
    }

    @Test
    fun released() = ReferenceShots.capture("hint_bar_released") { Bar(emptySet()) }

    @Test
    fun pressed() = ReferenceShots.capture("hint_bar_pressed") { Bar(setOf("A", "L1")) }
}
