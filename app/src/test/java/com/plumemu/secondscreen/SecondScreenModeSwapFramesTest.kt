package com.plumemu.secondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.ProvidePlumMotion
import com.plumemu.coreui.theme.fadingIn
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
 * The window's mode swap ([rememberModeSwap]), frame by frame: the in-game card settles in from smaller as a game starts
 * and the picking cards from larger as it ends, never further than [PlumMotion.SCREEN_DEPTH], ending exactly at rest; a
 * frame of motion recomposes nothing; with Animations off the swap is at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class SecondScreenModeSwapFramesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var mode by mutableStateOf(WindowMode.Picking)
    private var compositions = 0

    /** A window of black with a white square 200 dp wide in its middle, as the host draws a mode's content. */
    private fun show(animationsOn: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ProvidePlumMotion(animationsOn) {
                val swap = rememberModeSwap(mode)
                Box(Modifier.fillMaxSize().background(Color.Black).testTag(ROOT)) {
                    Box(Modifier.fillMaxSize().fadingIn(swap)) {
                        SideEffect { compositions++ }
                        Box(Modifier.offset(220.dp, 140.dp).size(200.dp).background(Color.White))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun frame(): PixelMap = compose.onNodeWithTag(ROOT).captureToImage().toPixelMap()

    /** The square's width on its middle row, in px, counting pixels at least half lit (so a faint one still counts). */
    private fun PixelMap.squareWidth(): Int {
        val y = height / 2
        val lit = (0 until width).map { this[it, y].red }
        val top = lit.maxOrNull() ?: 0f
        return if (top < 0.05f) 0 else lit.count { it > top / 2 }
    }

    private fun swapTo(next: WindowMode): Pair<Int, List<Int>> {
        val rest = frame().squareWidth()
        val before = compositions
        compose.runOnIdle { mode = next }
        val widths = mutableListOf<Int>()
        repeat(PlumMotion.SCREEN_MS / 16 + 2) {
            compose.mainClock.advanceTimeByFrame()
            widths += frame().squareWidth()
        }
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS.toLong())
        assertEquals("ends exactly at rest", rest, frame().squareWidth())
        assertTrue("the swap composes its content once: ${compositions - before}", compositions - before <= 1)
        return rest to widths.drop(1).filter { it > 0 }
    }

    @Test
    fun aGameStartingBringsTheInGameCardInFromSmaller() {
        show(animationsOn = true)
        val (rest, seen) = swapTo(WindowMode.InGameClock)
        assertTrue("starts smaller: $rest $seen", seen.first() < rest)
        // 4 % of 400 px: never smaller than that.
        assertTrue("never more than the depth: $rest $seen", seen.all { it >= rest - 17 })
        assertTrue("only grows: $seen", seen.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun aGameEndingBringsThePickingCardsBackFromLarger() {
        mode = WindowMode.InGameClock
        show(animationsOn = true)
        val (rest, seen) = swapTo(WindowMode.Picking)
        assertTrue("starts larger: $rest $seen", seen.first() > rest)
        assertTrue("only shrinks: $seen", seen.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun withAnimationsOffTheSwapIsAtOnce() {
        show(animationsOn = false)
        val rest = frame().squareWidth()
        val bright = frame()[320 * 2, 240 * 2].red
        compose.runOnIdle { mode = WindowMode.InGameBlack }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(rest, frame().squareWidth())
        assertEquals(bright, frame()[320 * 2, 240 * 2].red, 0.02f)
    }

    private companion object {
        const val ROOT = "root"
    }
}
