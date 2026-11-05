package com.plumemu.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideGridSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A system switch (L1/R1 with the games focused) in a grid recomposes the new system's grid and what shows its name and facts,
 * and nothing else more than once: the top bar, the hint bar, the systems column and the strip are not rebuilt twice for one press.
 * Counted with Compose's composition tracer, which sees every composable body that actually runs.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeSwitchRecompositionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val calls = mutableListOf<String>()

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

    private fun switchAndCount(): Map<String, Int> {
        lateinit var state: StripState
        val content = FakeHomeContent.withArtwork()
        val gridIds = content.entries.map { it.id }.toSet()
        compose.setContent {
            PlumTheme { ProvideGridSize(GridSize.Large) { HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), content = content, gridIds = gridIds, onState = { state = it }) } }
        }
        compose.waitForIdle()
        compose.runOnIdle { calls.clear() }
        compose.runOnIdle { state.dispatch(Action.NextSystem) }
        compose.waitForIdle()
        return calls.map { it.substringBefore(' ') + " " + it.substringAfter('(', "").substringBefore(')') }.groupingBy { it }.eachCount()
    }

    @Test
    fun aSwitchRunsTheBarsOnceAndOnlyTwoSystemRows() {
        val counts = switchAndCount()
        fun ran(function: String): Int = counts.filterKeys { it.startsWith("com.plumemu.") && it.substringBefore(' ').endsWith(".$function") }.values.sum()
        for (bar in listOf("HomeTopBar", "TopBar", "HomeHints", "GridHintBar", "TitledHintBar")) {
            assertEquals("$bar runs once for one press: $counts", 1, ran(bar))
        }
        assertEquals("the strip is not rebuilt by a switch", 0, ran("HomeStrip"))
        assertEquals("only the row that loses and the row that gains the look run", 2, ran("SystemRow"))
    }

    @Test
    fun aSwitchComposesTheNewGridOnceAndAsksNoIconForACover() {
        val counts = switchAndCount()
        fun ran(function: String): Int = counts.filterKeys { it.startsWith("com.plumemu.") && it.substringBefore(' ').endsWith(".$function") }.values.sum()
        assertEquals("GamesGrid runs once for one press: $counts", 1, ran("GamesGrid"))
        assertEquals("the new grid's 15 covers are composed once: $counts", 15, ran("GridCoverAt"))
        assertEquals("one lazy grid scope for the new system, and one more for the leftover that lands once it is measured: $counts", 2, ran("GridRows"))
        assertEquals("no cover of a game asks for an app icon: $counts", 0, ran("rememberAppGameIcon"))
    }
}
