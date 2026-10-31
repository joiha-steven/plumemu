package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The main screen with an update found: the accent dot on START Settings in the hint bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeUpdateDotScreenshotTest {
    @Test
    fun settingsHintWithTheUpdateDot() =
        ReferenceShots.capture("home_update_dot") {
            CompositionLocalProvider(LocalUpdateFound provides MutableStateFlow(true)) { HomeAt(FakeHomeContent.board(), BoardSystemsFocused) }
        }
}
