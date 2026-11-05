package com.plumemu.coreui.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** An off button ignores a tap that its caller's `clickable` would take; an on one passes it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PlumButtonTest {
    @get:Rule
    val compose = createComposeRule()

    private fun clicks(enabled: Boolean): Int {
        var count = 0
        compose.setContent {
            PlumTheme {
                PlumButton("Play", ButtonKind.Primary, focused = false, enabled = enabled, modifier = Modifier.testTag("b").clickable { count++ })
            }
        }
        compose.onNodeWithTag("b").performClick()
        compose.waitForIdle()
        return count
    }

    @Test
    fun enabledButtonPassesTheTap() = assertEquals(1, clicks(enabled = true))

    @Test
    fun offButtonIgnoresTheTap() = assertEquals(0, clicks(enabled = false))

    @Test
    fun offButtonIsMarkedDisabled() {
        compose.setContent { PlumTheme { PlumButton("Play", ButtonKind.Secondary, focused = false, enabled = false, modifier = Modifier.testTag("b")) } }
        compose.onNodeWithTag("b").assertIsNotEnabled()
    }
}
