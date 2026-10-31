package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.plumemu.MainScreen
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.settings.screen.TEST_VERSION
import com.plumemu.setup.FakeSetupSource
import com.plumemu.setup.PickResult
import com.plumemu.setup.SetupFakes
import com.plumemu.setup.SetupModel
import com.plumemu.setup.SetupStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The rows of "What fills it" decided above the main screen: Settings, a library that failed or is loading, setup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SecondScreenMainScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = true))
    private val host = SettingsHost(versionName = TEST_VERSION, update = {}, resetAllSettings = {}, resetLibrary = {})
    private lateinit var router: InputRouter
    private lateinit var feed: SecondScreenFeed
    private var state: HomeState? by mutableStateOf(HomeState.Ready(FakeHomeContent.board()))

    private fun showMainScreen() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            feed = remember { SecondScreenFeed(scope).also { it.setFollowing(true) } }
            MainScreen(settings, state, router, HomeCallbacks(), onRetry = {}, settingsHost = host, secondScreen = feed)
        }
        // The main screen's game index is built off the main thread before its first card.
        compose.waitUntil(WAIT_MS) { shown() is SecondScreenContent.System }
    }

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
        settle()
    }

    /** The cursor rests: what was offered reaches the feed (the settle rule has its own test). */
    private fun settle() {
        // First the frame that composes the change and starts its effects, which offer the card.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SecondScreenFeed.SETTLE_MS)
        compose.waitForIdle()
    }

    private fun shown(): SecondScreenContent = compose.runOnIdle { feed.content.value }

    @Test
    fun settingsIsCalmAndBackShowsTheMainScreensCardAgain() {
        showMainScreen()
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray())
        val system = shown()
        assertTrue("on Super Nintendo: $system", (system as? SecondScreenContent.System)?.name == "Super Nintendo")
        send(Action.Menu)
        assertEquals("Settings open", SecondScreenContent.Calm, shown())
        send(Action.Back)
        // Which entry: whatever the main screen's focus is after Settings (its own tests cover that).
        compose.waitUntil(WAIT_MS) { shown() is SecondScreenContent.System }
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }

    @Test
    fun aLibraryThatFailedOrIsLoadingIsCalm() {
        showMainScreen()
        assertTrue(shown() is SecondScreenContent.System)
        compose.runOnIdle { state = HomeState.Failed }
        settle()
        assertEquals(SecondScreenContent.Calm, shown())
        compose.runOnIdle { state = HomeState.Ready(FakeHomeContent.board()) }
        compose.waitUntil(WAIT_MS) { shown() is SecondScreenContent.System }
        compose.runOnIdle { state = null }
        settle()
        assertEquals(SecondScreenContent.Calm, shown())
    }

    @Test
    fun setupShowsTheQuestionOnItsStepAndIsCalmOtherwise() {
        val source = FakeSetupSource().apply { secondScreenQuestion.value = ScreenKind.BuiltIn }
        lateinit var model: SetupModel
        compose.setContent {
            val scope = rememberCoroutineScope()
            feed = remember { SecondScreenFeed(scope).also { it.setFollowing(true) } }
            model = remember { SetupModel(scope, source) { } }
            FollowSetupOnSecondScreen(feed, model)
        }
        compose.waitForIdle()
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        settle()
        assertEquals(SetupStep.Folders, compose.runOnIdle { model.state.value.step })
        assertEquals("the folder step", SecondScreenContent.Calm, shown())
        compose.runOnIdle { model.onAction(Action.Enter) }
        settle()
        assertEquals(SetupStep.SecondScreen, compose.runOnIdle { model.state.value.step })
        assertEquals(SecondScreenContent.SetupQuestion, shown())
        compose.runOnIdle { model.onAction(Action.Enter) }
        settle()
        assertEquals(SetupStep.Scan, compose.runOnIdle { model.state.value.step })
        assertEquals("the scan step", SecondScreenContent.Calm, shown())
    }
}
