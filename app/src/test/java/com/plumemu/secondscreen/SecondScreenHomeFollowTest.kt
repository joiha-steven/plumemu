package com.plumemu.secondscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.EntryKind
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.HomeScreen
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The main screen tells the second screen what is under the cursor, and costs the main screen
 * nothing for it: a move recomposes exactly the same composables with the second screen on as
 * with it off (counted with Compose's composition tracer, as HomeRecompositionTest does).
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SecondScreenHomeFollowTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private var feed: SecondScreenFeed? = null
    private val content = FakeHomeContent.board()
    private val snes = content.entries[FakeHomeContent.BOARD_SNES_INDEX]
    private val calls = mutableListOf<String>()

    /** A compose rule hosts one content per test: the variant is a state, and a new one builds a fresh main screen. */
    private var variant by mutableStateOf(false)
    private var isHosted = false

    private fun show(withSecondScreen: Boolean) {
        if (isHosted) {
            compose.runOnIdle { variant = withSecondScreen }
        } else {
            variant = withSecondScreen
            isHosted = true
            compose.setContent { key(variant) { Hosted(variant) } }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Hosted(withSecondScreen: Boolean) {
        val scope = rememberCoroutineScope()
        router = remember { InputRouter(scope) }
        val made =
            remember {
                if (!withSecondScreen) {
                    null
                } else {
                    SecondScreenFeed(scope).also { it.setFollowing(true) }
                }
            }
        feed = made
        PlumTheme { HomeScreen(content, router, HomeCallbacks(), secondScreen = made) }
    }

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
        // The cursor rests: what was offered reaches the feed (the settle rule has its own test).
        compose.mainClock.advanceTimeBy(SecondScreenFeed.SETTLE_MS)
        compose.waitForIdle()
    }

    private fun shown(): SecondScreenContent = compose.runOnIdle { feed!!.content.value }

    @After
    fun stopTracing() = Composer.setTracer(null)

    @Test
    fun followsTheCursorFromSystemToGameToMenu() {
        show(withSecondScreen = true)
        // The board's ids are not the database's: nothing to read for its last game.
        val recent = SecondScreenContent.System("Recent", content.entries[0].shownCount)
        // The game index is built off the main thread first.
        compose.waitUntil(WAIT_MS) { shown() == recent }
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray())
        val system = shown() as SecondScreenContent.System
        assertEquals(snes.name, system.name)
        assertEquals(snes.shownCount, system.gameCount)
        send(Action.Enter, Action.Move(Direction.Down))
        val game = shown() as SecondScreenContent.Game
        assertEquals(snes.rows[1].title, game.title)
        assertEquals(snes.name, game.systemName)
        send(Action.Menu)
        assertEquals("the menu's game", game, shown())
        send(Action.Back, Action.Back)
        assertTrue("back on the system: ${shown()}", shown() is SecondScreenContent.System)
    }

    @Test
    fun nothingIsDerivedWhileNoWindowIsUp() {
        show(withSecondScreen = true)
        compose.waitUntil(WAIT_MS) { shown() != SecondScreenContent.Calm }
        compose.runOnIdle { feed!!.setFollowing(false) }
        send(Action.Move(Direction.Down), Action.Move(Direction.Down))
        assertEquals("reset when the window went, and nothing offered since", SecondScreenContent.Calm, shown())
        compose.runOnIdle { feed!!.setFollowing(true) }
        assertEquals(EntryKind.Apps, content.entries[2].kind)
        // The game index is built off the main thread first.
        compose.waitUntil(WAIT_MS) { shown() == SecondScreenContent.Apps(content.entries[2].shownCount) }
    }

    @Test
    fun aMoveRecomposesTheSameWithTheSecondScreenOnAsOff() {
        val off = recomposedByAGameMove(withSecondScreen = false)
        val on = recomposedByAGameMove(withSecondScreen = true)
        assertTrue("the move recomposed something: $off", off.isNotEmpty())
        assertEquals("the main screen's recompositions with the second screen on", off, on)
    }

    @Test
    fun aSystemMoveRecomposesTheSameWithTheSecondScreenOnAsOff() {
        assertEquals(recomposedBy(withSecondScreen = false, inGames = false), recomposedBy(withSecondScreen = true, inGames = false))
    }

    private fun recomposedByAGameMove(withSecondScreen: Boolean) = recomposedBy(withSecondScreen, inGames = true)

    /** Our composables that ran for one D-pad down, sorted; the second screen's own follower is left in. */
    private fun recomposedBy(
        withSecondScreen: Boolean,
        inGames: Boolean,
    ): List<String> {
        show(withSecondScreen)
        if (withSecondScreen) compose.waitUntil(WAIT_MS) { shown() != SecondScreenContent.Calm }
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray())
        if (inGames) send(Action.Enter)
        val before = feed?.let { shown() }
        compose.runOnIdle {
            calls.clear()
            Composer.setTracer(
                object : CompositionTracer {
                    override fun isTraceInProgress(): Boolean = true

                    override fun traceEventStart(
                        key: Int,
                        dirty1: Int,
                        dirty2: Int,
                        info: String,
                    ) {
                        if (info.startsWith("com.plumemu.")) calls += info.substringBefore(" (")
                    }

                    override fun traceEventEnd() = Unit
                },
            )
        }
        send(Action.Move(Direction.Down))
        val ran = compose.runOnIdle { calls.toList().sorted().also { Composer.setTracer(null) } }
        if (withSecondScreen) assertTrue("the second screen followed the move", shown() != before)
        return ran
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
