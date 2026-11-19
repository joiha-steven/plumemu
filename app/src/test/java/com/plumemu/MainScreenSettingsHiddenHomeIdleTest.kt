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
 * While Settings is open and whole, a Settings frame (a D-pad move between its rows) does no composition, measure, placement or
 * draw in the hidden main screen. Counted by the composition tracer and the root's `homeProbed` counters; the counts are printed.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class MainScreenSettingsHiddenHomeIdleTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private lateinit var host: SettingsHost
    private var settings by mutableStateOf(PlumSettings.Defaults)
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
            val state = remember { HomeState.Ready(FakeHomeContent.board()) }
            val callbacks = remember { HomeCallbacks(games = games) }
            CompositionLocalProvider(LocalScreenTracker provides { tracked += it }) {
                MainScreen(settings, state, router, callbacks, onRetry = {}, settingsHost = host)
            }
        }
        compose.waitUntil(5_000) { host.deviceFacts.value != null }
        compose.waitForIdle()
    }

    private fun openSettingsWhole() {
        compose.runOnIdle { router.send(Action.OpenSettings) }
        repeat(SettingsOpening.WHOLE + 3) { compose.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun aSettingsFrameDoesNothingInTheHiddenMainScreen() {
        HomeFrameProbe.isOn = true
        try {
            show()
            compose.mainClock.autoAdvance = false
            openSettingsWhole()
            repeat(3) { compose.mainClock.advanceTimeByFrame() }
            val frames =
                List(6) { i ->
                    calls = 0
                    homeCalls = 0
                    HomeFrameProbe.reset()
                    compose.runOnIdle { router.send(Action.Move(if (i % 2 == 0) Direction.Down else Direction.Up)) }
                    repeat(3) { compose.mainClock.advanceTimeByFrame() }
                    listOf(calls, homeCalls, HomeFrameProbe.measures, HomeFrameProbe.places, HomeFrameProbe.draws)
                }
            println("Settings D-pad moves (3 frames each) [all calls, home calls, home measures, places, draws]: $frames")
            assertTrue("Settings itself composes something per move: $frames", frames.any { it[0] > 0 })
            assertTrue("no composition in the hidden main screen: $frames", frames.all { it[1] == 0 })
            assertTrue("no measure of the hidden main screen: $frames", frames.all { it[2] == 0 })
            assertTrue("no placement of the hidden main screen: $frames", frames.all { it[3] == 0 })
            assertTrue("no draw of the hidden main screen: $frames", frames.all { it[4] == 0 })
            assertEquals("the main screen is still the only one", 1, HomeTreeProbe.live)
        } finally {
            HomeFrameProbe.isOn = false
        }
    }
}
