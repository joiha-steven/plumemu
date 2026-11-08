package com.plumemu

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.HomeContent
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.setup.FakeSetupSource
import com.plumemu.setup.HomeRoleActions
import com.plumemu.setup.HomeRoleTags
import com.plumemu.setup.SetupModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The home screen question waits for a library with games (SPEC: asked once after the library opens): with no game
 * found Plumemu would become the home app of an empty screen. An empty library asks nothing and marks nothing; the
 * first game found brings the question.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenHomeRoleAskTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var marked = 0

    @After
    fun tearDown() = modelScope.cancel()

    private val notAsked = PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = false))

    private val role =
        object : HomeRoleActions {
            override fun isHomeApp() = false

            override fun requestHomeRole(onAnswered: (Boolean) -> Unit) = false

            override fun markAsked() {
                marked++
            }
        }

    private fun show(content: () -> HomeContent) {
        val model = SetupModel(modelScope, FakeSetupSource()) { }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SetupHost({ model }, {}, role) }
            MainScreen(notAsked, HomeState.Ready(content()), router, HomeCallbacks(), onRetry = {}, setup = host)
        }
        compose.waitForIdle()
    }

    @Test
    fun anEmptyLibraryAsksNothing() {
        show { FakeHomeContent.noGames() }
        compose.onNodeWithTag(HomeRoleTags.PROMPT).assertDoesNotExist()
        assertEquals("nothing marked as asked", 0, marked)
    }

    @Test
    fun aLibraryWithGamesAsks() {
        show { FakeHomeContent.board() }
        compose.onNodeWithTag(HomeRoleTags.PROMPT).assertIsDisplayed()
    }

    @Test
    fun theFirstGameFoundBringsTheQuestion() {
        val content = mutableStateOf(FakeHomeContent.noGames())
        show { content.value }
        compose.onNodeWithTag(HomeRoleTags.PROMPT).assertDoesNotExist()
        content.value = FakeHomeContent.board()
        compose.waitForIdle()
        compose.onNodeWithTag(HomeRoleTags.PROMPT).assertIsDisplayed()
    }
}
