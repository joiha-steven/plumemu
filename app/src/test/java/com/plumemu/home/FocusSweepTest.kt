package com.plumemu.home

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The light band across the focused cover (docs/design/rules.md › Focus and current): it starts only
 * after focus has rested [PlumMotion.FOCUS_SWEEP_REST_MS], is gone when focus leaves before that,
 * plays once, and never plays with animations off. Driven by the compose test clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class FocusSweepTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun play(isMotionOn: Boolean): FocusSweep {
        val sweep = FocusSweep()
        compose.mainClock.autoAdvance = false
        compose.setContent { LaunchedEffect(Unit) { sweep.play(isMotionOn) } }
        return sweep
    }

    @Test
    fun doesNotStartBeforeTheRestDelay() {
        val sweep = play(isMotionOn = true)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS - 20L)
        assertFalse("still resting", sweep.progress.isRunning)
        compose.mainClock.advanceTimeBy(60L)
        assertTrue("rest is over, the band is on the cover", sweep.progress.isRunning)
    }

    @Test
    fun playsOnceAndThenIsGone() {
        val sweep = play(isMotionOn = true)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS / 2L)
        assertTrue(sweep.progress.isRunning)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_MS.toLong())
        assertFalse("the band has crossed", sweep.progress.isRunning)
        assertEquals(1f, sweep.progress.value, 0f)
    }

    @Test
    fun neverStartsWithAnimationsOff() {
        val sweep = play(isMotionOn = false)
        compose.mainClock.advanceTimeBy((PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS) * 2L)
        assertFalse(sweep.progress.isRunning)
        assertEquals(0f, sweep.progress.value, 0f)
    }

    @Test
    fun focusLeavingBeforeTheRestDelayCancelsIt() {
        val sweep = FocusSweep()
        var focused by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LaunchedEffect(focused) {
                if (focused == NO_COVER) sweep.stop() else sweep.play(isMotionOn = true)
            }
        }
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS - 40L)
        focused = NO_COVER
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + 200L)
        assertFalse("focus left during the rest: nothing plays", sweep.progress.isRunning)
        assertEquals(0f, sweep.progress.value, 0f)
    }
}
