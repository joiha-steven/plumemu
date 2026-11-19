package com.plumemu

import android.os.Looper
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.plumemu.content.HomeState
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.game.FakeGameActions
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.LibrarySystem
import com.plumemu.settings.screen.SettingsActivityActions
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.settings.screen.SettingsOpening
import com.plumemu.telemetry.ScreenId
import com.plumemu.usage.LocalScreenTracker
import com.plumemu.usage.SETTLE_MS
import kotlinx.coroutines.flow.MutableSharedFlow
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

/**
 * The main screen stays composed under Settings, hidden and deaf, with the theme and settings it had when Settings opened
 * (frozen), so closing Settings only shows it: on the Thor the close was one 90-120 ms frame that composed (~20 ms), measured
 * and recorded the whole screen from nothing. Counted as composable calls per frame (the composition tracer). A change made
 * while Settings is open (a colour) reaches the main screen at the close and recomposes nothing before. The counts are printed.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class MainScreenSettingsClosingKeepsHomeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private lateinit var host: SettingsHost
    private var settings by mutableStateOf(PlumSettings.Defaults)
    private var ready by mutableStateOf(HomeState.Ready(FakeHomeContent.board()))
    private val games = FakeGameActions()
    private val tracked = mutableListOf<ScreenId>()
    private val real = SettingsContent(librarySystems = listOf(LibrarySystem("gba", "Game Boy Advance", 196)))

    /** Composable calls in the current frame, and those under the main screen's own composable. */
    private var calls = 0
    private var homeCalls = 0
    private val stack = mutableListOf<String>()

    private val activity =
        object : SettingsActivityActions {
            override fun openFolderPicker(): Boolean = false

            override fun openHomeSettings(onNotHome: () -> Unit): Boolean = false

            override fun isHomeApp(): Boolean = true

            override fun isUsageAccessGranted(): Boolean = false

            override fun openBackupPicker(kind: BackupKind): Boolean = false

            override fun openRestorePicker(): Boolean = false
        }

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
                    stack += info.substringBefore(" (").substringAfterLast('.')
                    if ("HomeWithRolePrompt" in stack) homeCalls++
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
        HomeTreeProbe.live = 0
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            host = remember { SettingsHost("test", update = {}, resetAllSettings = {}, resetLibrary = {}, content = MutableSharedFlow(replay = 1), remembered = { real }, activity = activity) }
            // Kept across recompositions, as MainActivity keeps them: a new object each time would be a changed input.
            val state = ready
            val callbacks = remember { HomeCallbacks(games = games) }
            CompositionLocalProvider(LocalScreenTracker provides { tracked += it }) {
                MainScreen(settings, state, router, callbacks, onRetry = {}, settingsHost = host)
            }
        }
        compose.waitUntil(5_000) { host.deviceFacts.value != null }
        compose.waitForIdle()
    }

    private fun shot(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    private fun PixelMap.sameAs(other: PixelMap): Boolean =
        width == other.width && height == other.height && (0 until 24).all { i -> (0 until 24).all { j -> this[(i * width) / 24, (j * height) / 24] == other[(i * width) / 24, (j * height) / 24] } }

    /** Pixels darker than a mid grey, every 3rd row and column: on a light page, the dark text drawn on it. */
    private fun PixelMap.darkPixels(): Int =
        (0 until height step 3).sumOf { y -> (0 until width step 3).count { x -> this[x, y].let { (it.red + it.green + it.blue) / 3f < 0.4f } } }

    private fun openSettingsWhole() {
        compose.runOnIdle { router.send(Action.OpenSettings) }
        repeat(SettingsOpening.WHOLE + 3) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun frames(count: Int): List<Pair<Int, Int>> =
        List(count) {
            calls = 0
            homeCalls = 0
            compose.mainClock.advanceTimeByFrame()
            calls to homeCalls
        }

    @Test
    fun theCloseFrameAndTheNextTwoComposeFarLessThanTheWholeScreen() {
        show()
        compose.mainClock.autoAdvance = false
        openSettingsWhole()
        assertEquals("the main screen stays composed under Settings", 1, HomeTreeProbe.live)
        compose.runOnIdle { router.send(Action.Back) }
        val perFrame = frames(3)
        println("Settings closing, composable calls per frame (all, under the main screen): $perFrame")
        // Before the change the close frame composed the whole main screen: about 1750 calls.
        assertTrue("the close frame composes far less than the whole screen: $perFrame", perFrame[0].first < CLOSE_FRAME_BUDGET)
        assertTrue("the two frames after it too: $perFrame", perFrame.drop(1).all { it.first < CLOSE_FRAME_BUDGET })
        assertEquals("the main screen is still the only one", 1, HomeTreeProbe.live)
    }

    @Test
    fun aColourChangedWhileSettingsIsOpenRecomposesNothingInTheHiddenScreenAndShowsAfterBack() {
        show()
        compose.mainClock.autoAdvance = false
        openSettingsWhole()
        settings = settings.copy(appearance = settings.appearance.copy(background = ColorSetting.Preset("Paper"), textColor = TextColorSetting.Dark))
        val while_ = frames(4)
        println("colour changed under Settings, composable calls per frame (all, under the main screen): $while_")
        assertTrue("the hidden main screen recomposes nothing for a new colour: $while_", while_.all { it.second == 0 })
        compose.runOnIdle { router.send(Action.Back) }
        val closing = frames(2)
        println("closing after the change: $closing")
        assertTrue("the main screen takes the new colour at the close", closing.any { it.second > 0 })
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS + 100L)
        val darkAfter = shot().darkPixels()
        println("dark pixels after Back on the light set: $darkAfter")
        assertTrue("the main screen draws the new set's dark text (the old light text would not show on it): $darkAfter", darkAfter > MIN_DARK_TEXT)
    }

    @Test
    fun theFocusIsWhereItWasAfterSettingsAndAPressUnderItNeverReachesTheMainScreen() {
        show()
        compose.runOnIdle { router.send(Action.Move(Direction.Right)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS + 100L)
        val before = shot()
        compose.mainClock.autoAdvance = false
        openSettingsWhole()
        // Presses Settings reads: none of them may also move or play the hidden main screen.
        compose.runOnIdle {
            router.send(Action.Move(Direction.Down))
            router.send(Action.Play)
            router.send(Action.Move(Direction.Left))
        }
        repeat(6) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("nothing was played from under Settings", emptyList<String>(), games.launched)
        compose.runOnIdle { router.send(Action.Back) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS + 100L)
        assertTrue("back on the main screen with the focus where it was", shot().sameAs(before))
        assertEquals("still nothing played", emptyList<String>(), games.launched)
    }

    @Test
    fun theMainScreenIsReportedAgainOnceTheCloseHasSettledAndNeverWhileSettingsShows() {
        show()
        compose.mainClock.advanceTimeBy(SETTLE_MS + 200L)
        val first = tracked.toList()
        assertEquals("the main screen is reported once it rests: $first", 1, first.size)
        compose.runOnIdle { router.send(Action.OpenSettings) }
        compose.mainClock.advanceTimeBy(SETTLE_MS + 500L)
        assertTrue("Settings is reported, the hidden main screen is not named again meanwhile: $tracked", tracked.size > 1 && tracked.drop(1).none { it == first[0] })
        compose.runOnIdle { router.send(Action.Back) }
        compose.mainClock.advanceTimeBy(SETTLE_MS - 200L)
        assertTrue("not before the close has settled: $tracked", tracked.last() != first[0])
        compose.mainClock.advanceTimeBy(400L)
        assertEquals("the main screen is reported again after the close: $tracked", first[0], tracked.last())
    }

    @Test
    fun aScreenChangedUnderSettingsIsReportedOnceUnderItsNewNameAfterTheClose() {
        show()
        // The home lands on Favorites' games.
        compose.mainClock.advanceTimeBy(SETTLE_MS + 600L)
        val first = tracked.last()
        assertEquals("the focus stands on the games of Favorites: $tracked", ScreenId.Favorites, first)
        compose.runOnIdle { router.send(Action.OpenSettings) }
        compose.mainClock.advanceTimeBy(SETTLE_MS + 500L)
        // Favorites empties: the focus leaves its games for the systems, a different screen (Home).
        ready = HomeState.Ready(FakeHomeContent.noFavorites())
        compose.mainClock.advanceTimeBy(SETTLE_MS + 500L)
        val before = tracked.size
        compose.runOnIdle { router.send(Action.Back) }
        compose.mainClock.advanceTimeBy(SETTLE_MS * 3)
        val after = tracked.drop(before)
        println("reported after the close: $after (before Settings: $first)")
        assertEquals("one report after the close, the new screen and never the old one: $after", listOf(ScreenId.Home), after)
    }

    @Test
    fun tenOpenAndCloseCyclesNeverComposeTwoMainScreens() {
        show()
        compose.mainClock.autoAdvance = false
        var most = 0
        repeat(10) {
            compose.runOnIdle { router.send(Action.OpenSettings) }
            repeat(SettingsOpening.WHOLE + 2) {
                compose.mainClock.advanceTimeByFrame()
                most = maxOf(most, HomeTreeProbe.live)
            }
            assertEquals("the main screen stays composed while Settings is whole", 1, HomeTreeProbe.live)
            compose.runOnIdle { router.send(Action.Back) }
            repeat(4) {
                compose.mainClock.advanceTimeByFrame()
                most = maxOf(most, HomeTreeProbe.live)
            }
            assertEquals("the main screen is back", 1, HomeTreeProbe.live)
        }
        assertEquals("never two main screens at once", 1, most)
        assertTrue("on the main looper", Looper.myLooper() == Looper.getMainLooper())
    }

    private companion object {
        const val CLOSE_FRAME_BUDGET = 300
        const val MIN_DARK_TEXT = 1_500
    }
}
