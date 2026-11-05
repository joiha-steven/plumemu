package com.plumemu

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.FakeGameActions
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.LibrarySystem
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.settings.screen.SettingsTags
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings does not draw its Library rows from an empty content and then move them: a repeat opening has them at once, a first one waits for them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenSettingsFirstFrameTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private val live = MutableSharedFlow<SettingsContent>(replay = 1)
    private val real = SettingsContent(librarySystems = listOf(LibrarySystem("gba", "Game Boy Advance", 196)))

    private fun show(remembered: SettingsContent?) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SettingsHost("test", update = {}, resetAllSettings = {}, resetLibrary = {}, content = live, remembered = { remembered }) }
            MainScreen(PlumSettings.Defaults, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(games = FakeGameActions()), onRetry = {}, settingsHost = host)
        }
    }

    private fun open() {
        compose.runOnIdle { router.send(Action.OpenSettings) }
        compose.waitForIdle()
    }

    private fun systemsRows() = compose.onAllNodesWithText("Systems").fetchSemanticsNodes().size

    @Test
    fun repeatOpeningDrawsTheRememberedRowsBeforeAnyNewContent() {
        show(real)
        open()
        compose.onNodeWithTag(SettingsTags.SCREEN).assertExists()
        assertEquals(true, systemsRows() > 0)
    }

    @Test
    fun firstOpeningDrawsNothingUntilTheContentArrives() {
        show(null)
        open()
        compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()
        live.tryEmit(real)
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.SCREEN).assertExists()
        assertEquals(true, systemsRows() > 0)
    }

    @Test
    fun firstOpeningDrawsFromTheEmptyContentAfterTheDeadline() {
        show(null)
        open()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag(SettingsTags.SCREEN).assertExists()
        assertEquals(0, systemsRows())
    }

    @Test
    fun backDuringTheHoldClosesSettings() {
        show(null)
        open()
        compose.runOnIdle { router.send(Action.Back) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()
    }
}
