package com.plumemu

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.FakeGameActions
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Composable calls per frame from a press on the main screen (the composition tracer). Opening or closing the game menu
 * recomposes the panel, its keyboard tray, the hints and the focused row, never the top bar, the systems column or the
 * screen around them, and neither piles into one frame: the panel's body comes a frame after its card, the slide waiting for
 * it, and closing changes the hint bar a frame after the press (on the Nova the menu's slow frames were 25-45 ms). The counts are printed so a report can quote them.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class MainScreenPressFramesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private var calls = 0
    private val stack = mutableListOf<String>()
    private val selfNames = mutableMapOf<String, Int>()
    private val under = mutableMapOf<String, Int>()

    @Before
    fun trace() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    calls++
                    val n = info.substringBefore(" (").substringAfterLast('.')
                    selfNames[n] = (selfNames[n] ?: 0) + 1
                    // The second plumemu composable below HomeScreen names the part.
                    val path = stack.filter { it.first().isUpperCase() && !it.startsWith("Provide") && it != "Box" && it != "Column" && it != "Row" && it != "Layout" }
                    val i = path.indexOf("HomeLayout").takeIf { it >= 0 } ?: path.indexOf("HomeScreen")
                    val part = if (i >= 0) (path.drop(i + 1).take(4) + n).joinToString(">") else (path.takeLast(3) + n).joinToString(">")
                    under[part] = (under[part] ?: 0) + 1
                    stack += n
                }

                override fun traceEventEnd() {
                    stack.removeLastOrNull()
                }
            },
        )
    }

    @After
    fun stopTracing() = Composer.setTracer(null)

    private fun show() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            MainScreen(PlumSettings.Defaults, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(games = FakeGameActions()), onRetry = {}, settingsHost = null)
        }
        compose.waitForIdle()
    }

    private fun press(vararg actions: Action) {
        actions.forEach {
            compose.runOnIdle { router.send(it) }
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    /** Composable calls per frame for [frameCount] frames after [action], and the composables called in the first. */
    private fun frames(
        label: String,
        action: Action,
        frameCount: Int = 4,
    ): Pair<List<Int>, Set<String>> {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.send(action) }
        val perFrame = mutableListOf<Int>()
        var first = emptySet<String>()
        repeat(frameCount) {
            calls = 0
            selfNames.clear()
            under.clear()
            compose.mainClock.advanceTimeByFrame()
            perFrame += calls
            if (it == 0) first = selfNames.keys.toSet()
            println("$label frame $it: $calls under=${under.entries.sortedByDescending { e -> e.value }.take(12)}")
        }
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(1_000)
        println("$label, composable calls per frame: $perFrame")
        return perFrame to first
    }

    @Test
    fun theGameMenuRecomposesOnlyWhatShowsIt() {
        show()
        press(Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Move(Direction.Right))
        val (open, openNames) = frames("menu open", Action.Menu)
        val (close, closeNames) = frames("menu close", Action.Back)
        for ((what, names) in listOf("opening" to openNames, "closing" to closeNames)) {
            for (outside in listOf("HomeTopBar", "SystemsColumn", "HomeWithRolePrompt", "HomeLayout", "HomeStrip")) {
                assertFalse("$outside recomposes when the menu is $what", outside in names)
            }
        }
        // Before: 390 calls opening, 442 closing, in one frame each, the whole main screen with the panel. Now the panel's
        // body comes a frame after its card (the slide waits for it), and closing changes the hint bar a frame after the press.
        assertTrue("opening, no frame composes it all: $open", open.all { it <= MENU_FRAME_BUDGET })
        assertTrue("closing, no frame composes it all: $close", close.all { it <= MENU_FRAME_BUDGET })
        assertTrue("opening, the body has its own frame: $open", open[0] > 0 && open[1] > open[0] / 2)
    }

    private companion object {
        const val MENU_FRAME_BUDGET = 170
    }
}
