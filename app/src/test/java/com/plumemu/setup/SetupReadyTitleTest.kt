package com.plumemu.setup

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.plumemu.MainScreen
import com.plumemu.R
import com.plumemu.SetupHost
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The last step says what is true: games found, no games in the folder, or no folder (Set up later). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SetupReadyTitleTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val source = FakeSetupSource()
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val model = SetupModel(modelScope, source) { }
    private lateinit var router: InputRouter

    @After
    fun tearDown() = modelScope.cancel()

    private fun show() {
        val settings = mutableStateOf(PlumSettings())
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SetupHost({ model }, {}, StubHomeRole) }
            MainScreen(settings.value, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(), onRetry = {}, setup = host)
        }
    }

    private fun send(action: Action) {
        compose.runOnIdle { router.send(action) }
        compose.waitForIdle()
    }

    private fun text(id: Int) = context.getString(id)

    @Test
    fun setUpLaterByGamepadSaysYouCanAddGamesLaterNotThatTheLibraryIsReady() {
        show()
        send(Action.Move(com.plumemu.coreinput.Direction.Down))
        send(Action.Enter)
        compose.onNodeWithText(text(R.string.setup_ready_no_folder_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_ready_no_folder_desc)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_ready_title)).assertDoesNotExist()
    }

    @Test
    fun setUpLaterByTouch() {
        show()
        compose.onNodeWithTag(SetupTags.control(SetupControl.SetUpLater)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.setup_ready_no_folder_title)).assertIsDisplayed()
    }

    @Test
    fun aFolderWithNoGamesSaysNoGamesFoundYetAndWhereToAddMore() {
        show()
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        compose.waitForIdle()
        send(Action.Enter) // Continue
        send(Action.Enter) // Look
        send(Action.Enter) // Box art
        compose.runOnIdle { source.endScan() }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.setup_ready_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_ready_empty_desc)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_ready_title)).assertDoesNotExist()
    }

    @Test
    fun aFolderWithGamesStillSaysTheLibraryIsReady() {
        show()
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        compose.waitForIdle()
        source.systems.value = SetupFakes.boardSystems
        send(Action.Enter)
        send(Action.Enter)
        send(Action.Enter)
        compose.runOnIdle { source.endScan() }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.setup_ready_title)).assertIsDisplayed()
    }
}

/** Plumemu is not asked about being the home app in these tests. */
internal object StubHomeRole : HomeRoleActions {
    override fun isHomeApp() = true

    override fun requestHomeRole(onAnswered: (Boolean) -> Unit) = false

    override fun markAsked() = Unit
}
