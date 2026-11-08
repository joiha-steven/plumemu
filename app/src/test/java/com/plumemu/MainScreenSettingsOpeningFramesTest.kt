package com.plumemu

import android.os.Looper
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.onAllNodesWithTag
import org.robolectric.annotation.GraphicsMode
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreinput.Direction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.PixelMap
import com.plumemu.content.HomeState
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.FakeGameActions
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.LibrarySystem
import com.plumemu.settings.screen.SettingsActivityActions
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.settings.screen.SettingsOpening
import com.plumemu.settings.screen.SettingsTags
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

/**
 * Opening Settings is spread over frames ([SettingsOpening]): on the Nova it was one 62 ms frame that composed and measured
 * all of Settings, disposed the whole main screen and made a binder call. Counted here as composable calls per frame (the
 * composition tracer; measuring follows what is composed): the press's frame composes almost nothing, no later frame
 * composes the whole screen, the main screen leaves only once Settings is whole, and Android is not asked on the main
 * thread; the press shows in its own frame (the page colour over the main screen), the hidden main screen takes no press and
 * is not in the semantics tree, and ten openings keep one main screen. The counts are printed so a report can quote them.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class MainScreenSettingsOpeningFramesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private val real = SettingsContent(librarySystems = listOf(LibrarySystem("gba", "Game Boy Advance", 196)))
    private var calls = 0
    private val stack = mutableListOf<String>()

    /** Calls per part of the screen in the current frame, for the printed report. */
    private val parts = mutableMapOf<String, Int>()

    /** Questions asked of Android on the main thread (binder calls in the app). */
    private var askedOnMain = 0

    private val activity =
        object : SettingsActivityActions {
            override fun openFolderPicker(): Boolean = false

            override fun openHomeSettings(onNotHome: () -> Unit): Boolean = false

            override fun isHomeApp(): Boolean = true.also { if (Looper.myLooper() == Looper.getMainLooper()) askedOnMain++ }

            override fun isUsageAccessGranted(): Boolean = false.also { if (Looper.myLooper() == Looper.getMainLooper()) askedOnMain++ }

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
                    val part = stack.lastOrNull { it in PARTS } ?: "other"
                    parts[part] = (parts[part] ?: 0) + 1
                }

                override fun traceEventEnd() {
                    stack.removeLastOrNull()
                }
            },
        )
    }

    @After
    fun stopTracing() = Composer.setTracer(null)

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private val games = FakeGameActions()
    private lateinit var host: SettingsHost

    private fun show() {
        HomeTreeProbe.live = 0
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            host = remember { SettingsHost("test", update = {}, resetAllSettings = {}, resetLibrary = {}, content = MutableSharedFlow(replay = 1), remembered = { real }, activity = activity) }
            MainScreen(PlumSettings.Defaults, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(games = games), onRetry = {}, settingsHost = host)
        }
        // The answers are read off the main thread when the screen comes to the front, before any press.
        compose.waitUntil(5_000) { host.deviceFacts.value != null }
        compose.waitForIdle()
    }

    private fun shot(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** The distinct colours on a 24 x 24 grid of samples: 1 for a screen of one colour. */
    private fun PixelMap.colours(): Int = (0 until 24).flatMap { i -> (0 until 24).map { j -> this[(i * width) / 24, (j * height) / 24] } }.toSet().size

    @Test
    fun theOpeningIsSpreadOverFramesAndTheMainScreenLeavesOnceSettingsIsWhole() {
        show()
        assertEquals("the main screen is in the merged semantics tree while it shows", 1, compose.onAllNodesWithTag(HomeTags.STRIP).fetchSemanticsNodes().size)
        askedOnMain = 0
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.send(Action.OpenSettings) }
        val perFrame = mutableListOf<Int>()
        val homeAt = mutableListOf<Int>()
        val homeNodesAt = mutableListOf<Int>()
        val optionsAt = mutableListOf<Int>()
        repeat(SettingsOpening.WHOLE + 3) {
            calls = 0
            parts.clear()
            compose.mainClock.advanceTimeByFrame()
            println("frame $it: $calls ${parts.entries.sortedByDescending { e -> e.value }}")
            perFrame += calls
            homeAt += HomeTreeProbe.live
            homeNodesAt += compose.onAllNodesWithTag(HomeTags.STRIP).fetchSemanticsNodes().size
            optionsAt += count(SettingsTags.OPTIONS)
        }
        println("Settings opening, composable calls per frame: $perFrame; main screen composed: $homeAt; options column: $optionsAt")
        // Before the change the press's frame composed 731 calls (all of Settings) and the next one 244; now the largest
        // frame is the options column alone (about 350).
        assertTrue("the press's frame composes almost nothing: $perFrame", perFrame[0] <= OPENING_FRAME_BUDGET)
        assertTrue("no frame composes all of Settings at once: $perFrame", perFrame.all { it <= LATER_FRAME_BUDGET })
        assertEquals("the main screen stays until Settings is whole, then leaves: $homeAt", listOf(1, 1, 1, 1, 0, 0, 0), homeAt)
        assertEquals("the hidden main screen is not in the merged semantics tree accessibility reads: $homeNodesAt", List(homeNodesAt.size) { 0 }, homeNodesAt)
        assertEquals("the options column has a frame of its own: $optionsAt", listOf(0, 0, 0, 1, 1, 1, 1), optionsAt)
        assertEquals("nothing asks Android on the main thread while Settings opens", 0, askedOnMain)
    }

    @Test
    fun thePressShowsInItsOwnFrameAndTheHiddenMainScreenTakesNoPress() {
        show()
        val before = shot()
        assertTrue("the main screen shows before the press", before.colours() > 1)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.send(Action.OpenSettings) }
        compose.mainClock.advanceTimeByFrame()
        // The press's own frame: the main screen is covered by the page colour Settings then settles in on.
        assertEquals("the press's frame shows the page colour alone", 1, shot().colours())
        // Presses while Settings comes in: the main screen under it must not move or play.
        compose.runOnIdle {
            router.send(Action.Move(Direction.Right))
            router.send(Action.Play)
        }
        repeat(SettingsOpening.WHOLE + 2) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("nothing was played from under Settings", emptyList<String>(), games.launched)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { router.send(Action.Back) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS + 100L)
        assertTrue("back on the main screen with the focus where it was", shot().sameAs(before))
    }

    @Test
    fun closedBeforeItIsWholeTheMainScreenSettlesBackAndTheBackIsReadOnce() {
        show()
        // The focus in the games column: a Back read by the main screen as well would take it to the systems.
        compose.runOnIdle { router.send(Action.Move(Direction.Right)) }
        compose.waitForIdle()
        // One whole opening and closing first, so the collectors are not in their first order.
        compose.runOnIdle { router.send(Action.OpenSettings) }
        compose.waitForIdle()
        compose.runOnIdle { router.send(Action.Back) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS + 100L)
        val before = shot()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.send(Action.OpenSettings) }
        compose.mainClock.advanceTimeByFrame()
        // Back closes Settings; a Down right after it, before the main screen shows again, must not move its focus either.
        // Back read by Settings closes it; the Down comes once every collector has read the Back, before the next frame.
        compose.runOnIdle { router.send(Action.Back) }
        compose.runOnIdle { router.send(Action.Move(Direction.Down)) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the main screen never left", 1, HomeTreeProbe.live)
        assertTrue("it settles back in rather than showing at once", !shot().sameAs(before))
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS + 100L)
        assertTrue("then whole, with the focus where it was: the presses were Settings' only", shot().sameAs(before))
    }

    @Test
    fun tenOpeningsKeepOneMainScreen() {
        show()
        compose.mainClock.autoAdvance = false
        var most = 0
        repeat(10) {
            compose.runOnIdle { router.send(Action.OpenSettings) }
            repeat(SettingsOpening.WHOLE + 2) {
                compose.mainClock.advanceTimeByFrame()
                most = maxOf(most, HomeTreeProbe.live)
            }
            assertEquals("the main screen left once Settings was whole", 0, HomeTreeProbe.live)
            compose.runOnIdle { router.send(Action.Back) }
            repeat(4) {
                compose.mainClock.advanceTimeByFrame()
                most = maxOf(most, HomeTreeProbe.live)
            }
            assertEquals("the main screen is back", 1, HomeTreeProbe.live)
        }
        assertEquals("never two main screens at once", 1, most)
    }

    private fun PixelMap.sameAs(other: PixelMap): Boolean =
        width == other.width && height == other.height && (0 until 24).all { i -> (0 until 24).all { j -> this[(i * width) / 24, (j * height) / 24] == other[(i * width) / 24, (j * height) / 24] } }

    private companion object {
        const val OPENING_FRAME_BUDGET = 100
        const val LATER_FRAME_BUDGET = 450
        val PARTS = setOf("SettingsScreen", "SettingsTopBar", "SettingsStrip", "SectionsColumn", "OptionsColumn", "PanelHost", "SettingsKeyboardTray", "SettingsHints", "SettingsOverHome", "HomeWithRolePrompt", "HomeWithSettings", "HomeScreen", "FadeIn")
    }
}
