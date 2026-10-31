package com.plumemu.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.settings.ButtonLetters
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The confirm screen of "Add to home screen", not on the board: the
 * question with Add focused, a long name at the largest sizes, and the widget refusal. Drawn on
 * the plain background (on a device the app that asked shows through the scrim).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PinConfirmScreenshotTest {
    @Test
    fun asking() = ReferenceShots.capture("pin_confirm_ask") { PinConfirmAt(PinScreen.Asking("Broforce", "Steam")) }

    @Test
    fun askingWithALongNameAndCancelFocused() =
        ReferenceShots.capture("pin_confirm_long") {
            PinConfirmAt(PinScreen.Asking("The Elder Scrolls V: Skyrim Special Edition Anniversary Upgrade", "Shortcuts"), focus = 1)
        }

    @Test
    fun widgetRefused() = ReferenceShots.capture("pin_confirm_widget") { PinConfirmAt(PinScreen.Message(PinMessage.Widget)) }
}

@Composable
private fun PinConfirmAt(
    screen: PinScreen,
    focus: Int = 0,
) {
    Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg)) {
        PinConfirmLayout(screen, focus, ButtonLetters.Default, onAction = {})
    }
}
