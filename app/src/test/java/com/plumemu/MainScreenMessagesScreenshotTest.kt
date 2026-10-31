package com.plumemu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.settings.ButtonLetters
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The main screen's own messages: "could not open its library" with Try again and Settings
 * (A1), and "Opening your library…" while the first answer is pending (A3). Drawn on the screen's background,
 * as `MainContent` does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class MainScreenMessagesScreenshotTest {
    @Composable
    private fun OnBackground(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg)) { content() }

    @Test
    fun libraryError() =
        ReferenceShots.capture("home_library_error") {
            val scope = rememberCoroutineScope()
            val router = remember { InputRouter(scope, onDebug = {}) }
            OnBackground { LibraryError(router, ButtonLetters.Default, onRetry = {}, onSettings = {}) }
        }

    @Test
    fun openingLibrary() = ReferenceShots.capture("home_opening_library") { OnBackground { OpeningLibrary(isShown = { true }) } }
}
