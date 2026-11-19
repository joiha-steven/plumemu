package com.plumemu.home

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import com.plumemu.coreui.testing.imageBuildsWaiting
import com.plumemu.coreui.components.WARM_DELAY_MS
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsColumn
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * A screen at rest asks for no frame and recomposes nothing. Once settled, with motion on:
 *  - the test clock gets idle without advancing (an infinite animation or a poll keeps a frame awaited, so the rule never reports idle
 *    and, under auto-advance, would run the clock on),
 *  - 125 more frames (2 s) run no composable (composer tracer) and apply no state change (an animation's value, a polled field: what
 *    any redraw starts from).
 * Home's list, home's grids and Settings. The control, a poll that writes state every frame, shows the counters do see a frame when one is asked.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class IdleFramesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var compositions = 0
    private var stateChanges = 0
    private var changes: ObserverHandle? = null

    @Before
    fun startCounting() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    compositions++
                }

                override fun traceEventEnd() = Unit
            },
        )
        changes = Snapshot.registerApplyObserver { changed, _ -> stateChanges += changed.size }
    }

    @After
    fun stopCounting() {
        Composer.setTracer(null)
        changes?.dispose()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) { content() } }
    }

    /** Settles [what], then checks the clock goes idle on its own and 2 s of frames compose and change nothing. */
    private fun assertIdleAtRest(
        what: String,
        content: @Composable () -> Unit,
    ) {
        show(content)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        finishOneOffWork()
        val before = compose.mainClock.currentTime
        compose.waitForIdle()
        assertEquals("$what: the clock ran on to get idle: a frame is awaited at rest", before, compose.mainClock.currentTime)
        compositions = 0
        stateChanges = 0
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        // The looper clock passing the rest delay again adds nothing: the one-off work does not repeat.
        passMainLooper(WARM_DELAY_MS + LOOPER_SLACK_MS)
        compose.waitForIdle()
        assertEquals("$what: composable calls in 2 s at rest", 0, compositions)
        assertEquals("$what: state changes (animation or polling) in 2 s at rest", 0, stateChanges)
    }

    private fun passMainLooper(ms: Long) {
        compose.runOnUiThread { ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS) }
        compose.waitForIdle()
    }

    /**
     * Counting starts after the work a screen does once on its own: the rest timer on the main looper (WARM_DELAY_MS, which Compose's clock
     * does not drive) and the background image builds, whose landing is posted to the main looper from a build thread at a time the test does not
     * choose (a lone class run, with a cold JVM, landed one in the counted window).
     */
    private fun finishOneOffWork() {
        // A warm-up queues its next build only once the previous one's coroutine has resumed in a frame, so "no build waits" counts only after quiet rounds.
        var quiet = 0
        var rounds = 0
        while (quiet < QUIET_ROUNDS) {
            assertTrue("image builds did not finish", rounds++ < MAX_ROUNDS)
            if (imageBuildsWaiting() > 0) {
                Thread.sleep(BUILD_POLL_MS)
                quiet = 0
                continue
            }
            passMainLooper(WARM_DELAY_MS + LOOPER_SLACK_MS)
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (imageBuildsWaiting() == 0) quiet++ else quiet = 0
        }
    }

    @Test
    fun theCountersSeeAPollThatWritesEveryFrame() {
        compose.mainClock.autoAdvance = false
        show {
            var ticks by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) { while (true) withFrameNanos { ticks++ } }
            BasicText("ticks $ticks")
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compositions = 0
        stateChanges = 0
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        assertTrue("the control composed $compositions times and changed state $stateChanges times: the counters are blind", compositions > 0 && stateChanges > 0)
    }

    @Test
    fun homeListIsIdleAtRest() = assertIdleAtRest("home list") { PlumTheme(TableText.Default) { HomeAt(FakeHomeContent.board(), isMotionOn = true) } }

    @Test
    fun homeCoverGridIsIdleAtRest() =
        assertIdleAtRest("home cover grid") {
            PlumTheme(TableText.Default) {
                HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes"))
            }
        }

    @Test
    fun homeAppsGridIsIdleAtRest() = assertIdleAtRest("apps grid") { PlumTheme(TableText.Default) { AppsGridBoard(focused = 0) } }

    @Test
    fun settingsIsIdleAtRest() =
        assertIdleAtRest("settings") { PlumTheme(TableText.Default) { SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Statistics)) } }

    private companion object {
        const val FRAMES = 125
        const val SETTLE_MS = 1000L
        const val LOOPER_SLACK_MS = 100L
        const val MAX_ROUNDS = 1000
        const val QUIET_ROUNDS = 3
        const val BUILD_POLL_MS = 20L
    }
}
