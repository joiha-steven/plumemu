package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.rememberHomeStripState
import com.plumemu.home.search.rememberSearchState
import com.plumemu.game.rememberGameMenuController
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The second screen's follow builds its game index once, however often the top screen switches between list and grid. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SecondScreenGameIndexTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var feed: SecondScreenFeed
    private val content = FakeHomeContent.withArtwork()

    /** SELECT switches list and grid without rebuilding the game index (one entry per game). */
    @Test
    fun selectNeverRebuildsTheGameIndex() {
        // Counted from Dispatchers.Default, which waitForIdle does not wait for.
        val builds = AtomicInteger()
        var gridIds by mutableStateOf(emptySet<String>())
        compose.setContent {
            val scope = rememberCoroutineScope()
            feed = remember { SecondScreenFeed(scope).also { it.setFollowing(true) } }
            PlumTheme {
                val state = rememberHomeStripState(content, gridIds, isMotionOn = false)
                val search = rememberSearchState()
                val menu = rememberGameMenuController(HomeCallbacks().games)
                FollowHomeOnSecondScreen(feed, content, state, search, menu, buildSystems = { builds.incrementAndGet(); GameSystems.of(it) })
            }
        }
        compose.waitUntil(WAIT_MS) { builds.get() == 1 }
        repeat(SWITCHES) {
            compose.runOnIdle { gridIds = if (gridIds.isEmpty()) setOf("snes") else emptySet() }
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(SecondScreenFeed.SETTLE_MS)
        compose.waitForIdle()
        val rebuilt = runCatching { compose.waitUntil(LATE_BUILD_MS) { builds.get() > 1 } }.isSuccess
        assertEquals("one game index for $SWITCHES switches", 1, if (rebuilt) builds.get() else 1)
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val LATE_BUILD_MS = 1_000L
        const val SWITCHES = 4
    }
}
