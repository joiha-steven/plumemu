package com.plumemu.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.game.FakeGameActions
import com.plumemu.game.sampleFacts
import com.plumemu.graph.AchievementCount
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Achievements row of the game menu shows a count that is read once, with the other facts, when the menu opens: moving the cursor
 * on and off it costs no more recomposition than a step between two plain rows, and reads nothing again.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GameMenuAchievementsRecompositionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val calls = mutableListOf<String>()
    private lateinit var router: InputRouter
    private var factReads = 0

    @After
    fun stopTracing() = Composer.setTracer(null)

    private fun send(action: Action) {
        compose.runOnIdle { router.send(action) }
        compose.waitForIdle()
    }

    @Test
    fun movingAcrossTheAchievementsRowCostsNoMoreThanAPlainStep() {
        val games = FakeGameActions()
        games.facts = {
            factReads++
            sampleFacts(canFixBoxArt = true, achievementCount = AchievementCount(12, 40))
        }
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme(PlumThemeSettings.Graphite) { HomeScreen(FakeHomeContent.board(), router, HomeCallbacks(games = games)) }
        }
        compose.waitForIdle()
        repeat(FakeHomeContent.BOARD_SNES_INDEX) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        send(Action.Menu)
        // Rows: Play, Favorite, Sort by, Name, Box art, Achievements, ...; three steps put the cursor on Name.
        repeat(3) { send(Action.Move(Direction.Down)) }
        val readsAfterOpen = factReads
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
                    calls += info
                }

                override fun traceEventEnd() = Unit
            },
        )
        // The cursor moves already re-run the screen's follower composables: what must not change is that stepping on and off Achievements runs no more of them than a step between two plain rows.
        fun homeCallsOfOneMove(): List<String> {
            calls.clear()
            send(Action.Move(Direction.Down))
            return calls.filter { it.startsWith("com.plumemu.home.") }
        }
        val plainMove = homeCallsOfOneMove() // Name to Box art
        val ontoAchievements = homeCallsOfOneMove()
        val offAchievements = homeCallsOfOneMove()
        assertTrue("onto Achievements: $ontoAchievements runs more than a plain step: $plainMove", ontoAchievements.size <= plainMove.size && plainMove.containsAll(ontoAchievements))
        assertTrue("off Achievements: $offAchievements runs more than a plain step: $plainMove", offAchievements.size <= plainMove.size && plainMove.containsAll(offAchievements))
        assertTrue("the tracer sees menu rows: ${calls.size}", calls.any { it.startsWith("com.plumemu.game.") || it.startsWith("com.plumemu.coreui.") })
        assertEquals("the count was not read again while the cursor moved", readsAfterOpen, factReads)
    }
}
