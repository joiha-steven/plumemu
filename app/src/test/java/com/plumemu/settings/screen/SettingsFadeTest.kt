package com.plumemu.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Settings fade-in: 110 ms on the layer's alpha with motion on, drawn at once with Animations off. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsFadeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val key = mutableStateOf(0)

    private fun show(animationsOn: Boolean, isFading: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(animationsOn = animationsOn)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    FadeIn(key.value, Modifier.testTag("fade"), isFading) { Box(Modifier.size(40.dp).background(Color.White)) }
                }
            }
        }
    }

    private fun brightness(): Float {
        val pixel = compose.onNodeWithTag("fade").captureToImage().toPixelMap()[5, 5]
        return pixel.red
    }

    @Test
    fun withMotionOnTheContentStartsClearAndIsOpaqueAfterTheFade() {
        show(animationsOn = true)
        compose.mainClock.advanceTimeByFrame()
        assertTrue("starts nearly clear: ${brightness()}", brightness() < 0.5f)
        compose.mainClock.advanceTimeBy(PlumMotion.FADE_IN_MS + 100L)
        assertEquals(1f, brightness(), 0.02f)
    }

    @Test
    fun withoutFadingTheFirstFrameIsOpaqueEvenWithMotionOn() {
        show(animationsOn = true, isFading = false)
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1f, brightness(), 0.02f)
    }

    @Test
    fun anotherKeyFadesTheNewContentInAgain() {
        show(animationsOn = true)
        compose.mainClock.advanceTimeBy(PlumMotion.FADE_IN_MS + 100L)
        compose.runOnUiThread { key.value = 1 }
        val seen = StringBuilder()
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            seen.append(brightness()).append(' ')
        }
        assertTrue("clear again $seen after a change: ${brightness()}", brightness() < 0.5f)
        compose.mainClock.advanceTimeBy(PlumMotion.FADE_IN_MS + 100L)
        assertEquals(1f, brightness(), 0.02f)
    }

    @Test
    fun withAnimationsOffTheContentIsDrawnAtOnce() {
        show(animationsOn = false)
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1f, brightness(), 0.02f)
        compose.runOnUiThread { key.value = 1 }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1f, brightness(), 0.02f)
    }
}
