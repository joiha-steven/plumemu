package com.plumemu.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coreui.components.HINT_KEY_CAP_TAG
import com.plumemu.coreui.components.LocalHeldKeys
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pressing and releasing A on the home screen redraws the hint's key cap and recomposes nothing:
 * the held keys are read in a coroutine and in draw and layer blocks only. Counted with Compose's
 * composition tracer, as in [HomeRecompositionTest].
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HintPressRecompositionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val calls = mutableListOf<String>()
    private val held = mutableStateOf<Set<String>>(emptySet())

    @Before
    fun traceCompositions() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    calls += info
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stopTracing() = Composer.setTracer(null)

    private fun firstCapPixel(): Color {
        val image = compose.onAllNodesWithTag(HINT_KEY_CAP_TAG, useUnmergedTree = true)[0].captureToImage()
        return image.toPixelMap()[(image.width * 0.16f).toInt(), image.height / 2]
    }

    @Test
    fun pressingAndReleasingARecomposesNothingAndRedrawsTheCap() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                CompositionLocalProvider(LocalHeldKeys provides held) { HomeAt(FakeHomeContent.board()) }
            }
        }
        compose.waitForIdle()
        val resting = firstCapPixel()
        compose.runOnIdle { calls.clear() }

        compose.runOnIdle {
            held.value = setOf("A")
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        val pressed = firstCapPixel()
        compose.runOnIdle {
            held.value = emptySet()
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500) // the way out starts after the dip's minimum time

        assertEquals("a press and release recompose nothing: $calls", emptyList<String>(), calls)
        assertNotEquals("the first hint's cap is drawn pressed", resting, pressed)
        assertEquals("and back to rest", resting, firstCapPixel())
    }
}
