package com.plumemu.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
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
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.fadingIn
import com.plumemu.coreui.theme.rememberSlidingSwap
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
 * Content swapping in from the side its step came from ([rememberSlidingSwap]: another system on L2/R2, a setup step),
 * frame by frame: it starts on the step's side, never further than [PlumMotion.SWAP_SHIFT_DP], only moves toward rest
 * and ends exactly there; a frame of motion recomposes nothing; with Animations off nothing moves.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SlidingSwapFramesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val key = mutableIntStateOf(0)
    private val order = mutableIntStateOf(0)
    private var compositions = 0

    private fun show(animationsOn: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(animationsOn = animationsOn)) {
                Box(Modifier.fillMaxSize().background(Color.Black).testTag(ROOT)) {
                    val swap = rememberSlidingSwap(key.intValue, { order.intValue }, wrapsAt = 12)
                    Box(Modifier.fillMaxSize().fadingIn(swap)) {
                        SideEffect { compositions++ }
                        Box(Modifier.align(Alignment.TopStart).offset(x = 100.dp, y = 100.dp).size(40.dp).background(Color.White))
                    }
                }
            }
        }
    }

    private fun frame(): PixelMap = compose.onNodeWithTag(ROOT).captureToImage().toPixelMap()

    /** The first row (on the square's middle column) where the square is drawn, -1 for none. */
    private fun PixelMap.squareTop(): Int = (0 until height).firstOrNull { this[240, it].red > 0.05f } ?: -1

    /** Steps to [to], then the square's top on each frame of the swap, from the first frame that shows it moved. */
    private fun stepTo(to: Int): Pair<Int, List<Int>> {
        compose.mainClock.advanceTimeByFrame()
        val rest = frame().squareTop()
        val before = compositions
        compose.runOnUiThread {
            order.intValue = to
            key.intValue += 1
        }
        val tops = mutableListOf<Int>()
        repeat(PlumMotion.GLIDE_MS / 16 + 2) {
            compose.mainClock.advanceTimeByFrame()
            tops += frame().squareTop()
        }
        compose.mainClock.advanceTimeBy(PlumMotion.GLIDE_MS.toLong())
        assertEquals("ends exactly at rest", rest, frame().squareTop())
        assertTrue("the swap composes its new content once: ${compositions - before}", compositions - before <= 1)
        // The frames before the new content shows (the old one, then one still clear) are left out.
        return rest to tops.dropWhile { it == rest }.filter { it >= 0 }
    }

    @Test
    fun aStepForwardSlidesTheNewContentUpFromBelowToRest() {
        show(animationsOn = true)
        val (rest, seen) = stepTo(1)
        assertTrue("starts below its place: $rest $seen", seen.first() > rest)
        // 16 dp at 2 px per dp: never further than that.
        assertTrue("never more than the shift: $rest $seen", seen.all { it - rest <= 32 })
        assertTrue("only moves up: $seen", seen.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun aStepBackSlidesTheNewContentDownFromAbove() {
        show(animationsOn = true)
        val (rest, seen) = stepTo(-1)
        assertTrue("starts above its place: $rest $seen", seen.first() < rest)
        assertTrue("only moves down: $seen", seen.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun fromTheLastSystemToTheFirstItStillComesFromBelow() {
        order.intValue = 11
        show(animationsOn = true)
        val (rest, seen) = stepTo(0)
        assertTrue("wrapped forward, from below: $rest $seen", seen.first() > rest)
    }

    @Test
    fun withAnimationsOffASwapDoesNotMove() {
        show(animationsOn = false)
        compose.mainClock.advanceTimeByFrame()
        val rest = frame().squareTop()
        compose.runOnUiThread {
            order.intValue = 1
            key.intValue = 1
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(rest, frame().squareTop())
        assertEquals(1f, frame()[240, rest + 10].red, 0.02f)
    }

    private companion object {
        const val ROOT = "root"
    }
}
