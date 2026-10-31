package com.plumemu

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.loggingHandler
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A library that cannot be read shows a clear message; background failures never crash the process. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun aFailedLibraryShowsTheMessageAndWhatToTry() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            MainScreen(PlumSettings.Defaults, HomeState.Failed, InputRouter(scope, onDebug = {}), HomeCallbacks(), onRetry = {})
        }
        compose.onNodeWithTag(MainScreenTags.LIBRARY_ERROR).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.home_library_error_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.home_library_error_body)).assertIsDisplayed()
    }

    @Test
    fun tryAgainByTheButtonOrByATapReadsTheLibraryAgainAndThenShowsIt() {
        val state = mutableStateOf<HomeState>(HomeState.Failed)
        var retries = 0
        lateinit var router: InputRouter
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            MainScreen(PlumSettings.Defaults, state.value, router, HomeCallbacks(), onRetry = { retries++ })
        }
        compose.onNodeWithTag(MainScreenTags.TRY_AGAIN).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.home_hint_try_again)).assertIsDisplayed()

        compose.runOnIdle { router.send(Action.Enter) } // A on the gamepad (the router's Enter)
        compose.waitForIdle()
        assertEquals("by the button", 1, retries)

        compose.onNodeWithTag(MainScreenTags.TRY_AGAIN).performClick()
        compose.waitForIdle()
        assertEquals("by a tap on the hint", 2, retries)

        state.value = HomeState.Ready(FakeHomeContent.board())
        compose.waitForIdle()
        compose.onNodeWithTag(MainScreenTags.LIBRARY_ERROR).assertDoesNotExist()
        compose.onNodeWithText("Super Nintendo").assertIsDisplayed()
        compose.runOnIdle { router.send(Action.Enter) }
        compose.waitForIdle()
        assertEquals("Enter belongs to the main screen once the library shows", 2, retries)
    }

    @Test
    fun nothingIsDrawnBeforeTheSettingsArrive() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            MainScreen(null, HomeState.Failed, InputRouter(scope, onDebug = {}), HomeCallbacks(), onRetry = {})
        }
        compose.onNodeWithTag(MainScreenTags.LIBRARY_ERROR).assertDoesNotExist()
    }

    @Test
    fun aFailureInTheAppScopeIsLoggedByClassNotThrown() = runBlocking {
        val logged = CopyOnWriteArrayList<String>()
        val scope = CoroutineScope(SupervisorJob() + loggingHandler { logged += it })

        scope.launch { throw IllegalStateException("content://x/tree/primary%3AROMs/Secret Quest.sfc") }.join()
        val stillWorks = scope.launch { logged += "after" }
        stillWorks.join()

        assertEquals(listOf("Background work failed (IllegalStateException)", "after"), logged.toList())
        assertTrue(logged.none { "Secret" in it })
    }
}
