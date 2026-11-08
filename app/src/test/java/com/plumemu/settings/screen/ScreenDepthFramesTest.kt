package com.plumemu.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
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

/**
 * A screen arriving with depth ([FadeIn] with a depth: Settings opening from smaller, the home coming back from larger),
 * frame by frame: where it starts, that it only moves toward rest and ends exactly whole, that its content composes
 * once (a frame of motion recomposes nothing), and that with Animations off it is whole at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class ScreenDepthFramesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var compositions = 0

    /** The page colour's red, as the screens stand on it (MainScreen's background). */
    private var page = 0f

    private fun show(
        animationsOn: Boolean,
        depth: Float,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(animationsOn = animationsOn)) {
                page = PlumTheme.colors.bg.red
                Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg).testTag(ROOT)) {
                    FadeIn(Unit, depth = depth) {
                        SideEffect { compositions++ }
                        Box(Modifier.fillMaxSize().background(Color.White))
                    }
                }
            }
        }
    }

    private fun frame(): PixelMap = compose.onNodeWithTag(ROOT).captureToImage().toPixelMap()

    /** The first column (on the middle row) where the white screen shows over the page, -1 while it is still clear. */
    private fun PixelMap.leftEdge(): Int = (0 until width).firstOrNull { this[it, height / 2].red > page + 0.05f } ?: -1

    @Test
    fun goingInTheScreenSettlesFromSmallerToItsPlaceAndEndsWhole() {
        show(animationsOn = true, depth = -PlumMotion.SCREEN_DEPTH)
        compose.mainClock.advanceTimeByFrame()
        val edges = mutableListOf<Int>()
        val light = mutableListOf<Float>()
        repeat(PlumMotion.SCREEN_MS / 16 + 2) {
            val shot = frame()
            edges += shot.leftEdge()
            light += shot[shot.width / 2, shot.height / 2].red
            compose.mainClock.advanceTimeByFrame()
        }
        val seen = edges.filter { it >= 0 }
        // 4 % smaller about the centre: the edge starts about 2 % of 1536 px (30 px) in.
        assertTrue("starts inset: $edges", seen.first() in 15..31)
        assertTrue("only moves out: $edges", seen.zipWithNext().all { (a, b) -> b <= a })
        assertTrue("only brightens: $light", light.zipWithNext().all { (a, b) -> b >= a - 0.01f })
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS.toLong())
        assertEquals(0, frame().leftEdge())
        assertEquals(1f, frame()[10, 10].red, 0.02f)
        assertEquals("a frame of motion recomposes nothing", 1, compositions)
    }

    @Test
    fun comingBackTheScreenSettlesFromLargerAndItsFadeEndsOpaque() {
        show(animationsOn = true, depth = PlumMotion.SCREEN_DEPTH)
        compose.mainClock.advanceTimeByFrame()
        val first = frame()[10, 10].red
        assertTrue("starts faded: $first", first < page + 0.6f * (1f - page))
        // Larger than its box: it covers the edge as soon as it shows.
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(0, frame().leftEdge())
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS + 50L)
        assertEquals(1f, frame()[10, 10].red, 0.02f)
        assertEquals(1, compositions)
    }

    @Test
    fun withAnimationsOffAScreenIsWholeAtOnce() {
        show(animationsOn = false, depth = -PlumMotion.SCREEN_DEPTH)
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0, frame().leftEdge())
        assertEquals(1f, frame()[10, 10].red, 0.02f)
    }

    private companion object {
        const val ROOT = "root"
    }
}
