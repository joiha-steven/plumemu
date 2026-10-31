package com.plumemu.setup

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.performTouchInput
import com.plumemu.graph.ScanPhase
import com.plumemu.graph.ScanReport
import androidx.test.core.app.ApplicationProvider
import com.plumemu.MainScreen
import com.plumemu.R
import com.plumemu.SetupHost
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.secondscreen.ScreenKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Setup on screen through MainScreen: shown while `flags.setup_completed` is false, the same path
 * by gamepad (the router's actions) and by touch (taps on controls and hints), then the home
 * screen question once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SetupScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val source = FakeSetupSource()
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val model = SetupModel(modelScope, source) { }
    private var pickerOpened = 0
    private val homeRole = RecordingHomeRole()
    private lateinit var router: InputRouter

    @After
    fun tearDown() = modelScope.cancel()

    private fun show(
        settings: PlumSettings,
        state: HomeState? = HomeState.Ready(FakeHomeContent.board()),
    ) {
        val current = mutableStateOf(settings)
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SetupHost({ model }, { pickerOpened++ }, homeRole) }
            MainScreen(current.value, state, router, HomeCallbacks(), onRetry = {}, setup = host)
        }
    }

    private fun send(action: Action) {
        compose.runOnIdle { router.send(action) }
        compose.waitForIdle()
    }

    private fun text(id: Int) = context.getString(id)

    @Test
    fun setupShowsUntilItIsCompletedThenTheMainScreen() {
        show(PlumSettings.Defaults)
        compose.onNodeWithTag(SetupTags.SCREEN).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_folder_title)).assertIsDisplayed()
        compose.onNodeWithText("Super Nintendo").assertDoesNotExist()
    }

    @Test
    fun aCompletedSetupGoesStraightToTheMainScreen() {
        show(completed(homeRoleAsked = true))
        compose.onNodeWithTag(SetupTags.SCREEN).assertDoesNotExist()
        compose.onNodeWithText("Super Nintendo").assertIsDisplayed()
    }

    @Test
    fun chooseFolderOpensThePickerByButtonByTapAndByItsHint() {
        show(PlumSettings.Defaults)
        send(Action.Enter)
        assertEquals("A on the gamepad", 1, pickerOpened)
        compose.onNodeWithTag(SetupTags.control(SetupControl.ChooseFolder)).performClick()
        compose.waitForIdle()
        assertEquals("a tap on the button", 2, pickerOpened)
        compose.onNodeWithTag(SetupTags.HINT_CONFIRM).performClick()
        compose.waitForIdle()
        assertEquals("a tap on the hint", 3, pickerOpened)
    }

    @Test
    fun aPickedFolderShowsAsACardWithContinueThenTheScanAndBack() {
        show(PlumSettings.Defaults)
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        compose.waitForIdle()
        compose.onNodeWithText("Internal storage › ROMs").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_folder_counting)).assertIsDisplayed()

        compose.onNodeWithTag(SetupTags.control(SetupControl.Continue)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.setup_scan_title)).assertIsDisplayed()
        compose.onNodeWithTag(SetupTags.PROGRESS).assertIsDisplayed()

        compose.onNodeWithTag(SetupTags.HINT_BACK).performClick() // touch: the B Back hint
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.setup_folder_title)).assertIsDisplayed()

        send(Action.Enter) // gamepad: Continue again
        compose.onNodeWithText(text(R.string.setup_scan_title)).assertIsDisplayed()
        send(Action.Back)
        compose.onNodeWithText(text(R.string.setup_folder_title)).assertIsDisplayed()
    }

    @Test
    fun theSecondScreenQuestionIsAnsweredByAAndItsBackHintReturnsToTheFolders() {
        source.secondScreenQuestion.value = ScreenKind.BuiltIn
        show(PlumSettings.Defaults)
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        compose.waitForIdle()
        compose.onAllNodesWithTag(SetupTags.DOT, useUnmergedTree = true).assertCountEquals(3)

        send(Action.Enter) // Continue
        compose.onNodeWithText(text(R.string.setup_second_screen_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_second_screen_desc)).assertIsDisplayed()
        compose.onNodeWithTag(SetupTags.HINT_BACK).performClick() // touch: the B Back hint
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.setup_folder_title)).assertIsDisplayed()

        send(Action.Enter) // Continue again
        send(Action.Enter) // Use it, focused for a built-in screen
        assertEquals(true, source.secondScreenAnswer)
        compose.onNodeWithText(text(R.string.setup_scan_title)).assertIsDisplayed()
    }

    @Test
    fun theSecondScreenQuestionIsAnsweredByATap() {
        source.secondScreenQuestion.value = ScreenKind.Unsure
        show(PlumSettings.Defaults)
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        compose.waitForIdle()
        compose.onNodeWithTag(SetupTags.control(SetupControl.Continue)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SetupTags.control(SetupControl.SkipSecondScreen)).performClick()
        compose.waitForIdle()
        assertEquals(false, source.secondScreenAnswer)
        compose.onNodeWithText(text(R.string.setup_scan_title)).assertIsDisplayed()
    }

    @Test
    fun noSecondScreenNoQuestionAndTwoDots() {
        show(PlumSettings.Defaults)
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        compose.waitForIdle()
        compose.onAllNodesWithTag(SetupTags.DOT, useUnmergedTree = true).assertCountEquals(2)
        send(Action.Enter)
        compose.onNodeWithText(text(R.string.setup_scan_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_second_screen_title)).assertDoesNotExist()
    }

    @Test
    fun theLibraryReadyStepStoresTheSwitchAndStartPlayingByTap() {
        show(PlumSettings.Defaults)
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        compose.waitForIdle()
        source.systems.value = SetupFakes.boardSystems
        send(Action.Enter)
        compose.runOnIdle { source.endScan() }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.setup_ready_title)).assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.setup_emulators_all, 2, "2")).assertIsDisplayed()
        // The row's words; the switch starts off.
        compose.onNodeWithText("Send anonymous usage statistics").assertIsDisplayed()
        compose.onNodeWithText("crash reports", substring = true).assertIsDisplayed()

        assertFalse("a fresh install shows the switch off", source.usageStatistics.value)
        compose.onNodeWithTag(SetupTags.control(SetupControl.UsageStatistics)).performClick()
        compose.waitForIdle()
        assertTrue("switched on by a tap", source.usageStatistics.value)

        compose.onNodeWithTag(SetupTags.control(SetupControl.StartPlaying)).performClick()
        compose.waitForIdle()
        assertTrue(source.setupCompleted)
        assertTrue("finishing setup keeps the person's choice", source.usageStatistics.value)
    }

    /** Ready shows while naming runs and its progress keeps changing; one tap on Start playing completes setup once. */
    @Test
    fun startPlayingByTapWorksWhileNamingStillRunsAndItsProgressKeepsChanging() {
        show(PlumSettings.Defaults)
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        compose.waitForIdle()
        source.systems.value = SetupFakes.boardSystems
        send(Action.Enter)
        compose.runOnIdle {
            source.scanReport.value = ScanReport(phase = ScanPhase.Naming, walkedFolderIds = setOf(1L), gamesNamed = 10, gamesToName = 1837)
        }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.setup_ready_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_ready_naming)).assertIsDisplayed()

        // The progress changes between the finger going down and coming up, as it does on a device.
        var named = 10
        compose.onNodeWithTag(SetupTags.control(SetupControl.StartPlaying)).performTouchInput {
            down(center)
            compose.runOnIdle {
                source.scanReport.value = ScanReport(phase = ScanPhase.Naming, walkedFolderIds = setOf(1L), gamesNamed = ++named, gamesToName = 1837)
            }
            up()
        }
        compose.waitForIdle()
        assertTrue("one tap completed setup", source.setupCompleted)
        assertEquals("exactly once", 1, source.completions)
    }

    @Test
    fun aDatabaseErrorShowsAMessageAndTryAgainByTapRecovers() {
        source.foldersFailure = IllegalStateException("Room")
        show(PlumSettings.Defaults)
        compose.onNodeWithText(text(R.string.setup_load_failed_title)).assertIsDisplayed()
        source.foldersFailure = null
        compose.onNodeWithTag(SetupTags.control(SetupControl.TryAgain)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.setup_folder_title)).assertIsDisplayed()
    }

    @Test
    fun theHomeScreenQuestionShowsOnceOverTheLibraryAndNotNowMarksItAsked() {
        show(completed(homeRoleAsked = false))
        compose.onNodeWithTag(HomeRoleTags.PROMPT).assertIsDisplayed()
        compose.onNodeWithTag(HomeRoleTags.HINT_NOT_NOW).performClick()
        compose.waitForIdle()
        assertEquals(1, homeRole.marked)
        assertEquals(0, homeRole.requests)
        compose.onNodeWithTag(HomeRoleTags.PROMPT).assertDoesNotExist()
    }

    @Test
    fun yesByTheButtonOpensAndroidsScreenAndMarksItAsked() {
        show(completed(homeRoleAsked = false))
        send(Action.Enter)
        assertEquals(1, homeRole.requests)
        assertEquals(1, homeRole.marked)
        compose.onNodeWithTag(HomeRoleTags.PROMPT).assertDoesNotExist()
    }

    @Test
    fun yesWithoutAndroidsScreenSaysWhereToLookThenOkCloses() {
        homeRole.canRequest = false
        show(completed(homeRoleAsked = false))
        compose.onNodeWithTag(HomeRoleTags.button(0)).performClick() // touch: Yes
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.home_role_unavailable)).assertIsDisplayed()
        assertEquals(1, homeRole.marked)
        send(Action.Enter) // OK
        compose.onNodeWithTag(HomeRoleTags.PROMPT).assertDoesNotExist()
    }

    @Test
    fun noQuestionOnceAskedOrWhenPlumemuIsAlreadyTheHomeApp() {
        homeRole.isHome = true
        show(completed(homeRoleAsked = false))
        compose.onNodeWithTag(HomeRoleTags.PROMPT).assertDoesNotExist()
        assertEquals("already home: stored as asked", 1, homeRole.marked)
    }

    @Test
    fun noQuestionWhenItWasAskedBefore() {
        show(completed(homeRoleAsked = true))
        compose.onNodeWithTag(HomeRoleTags.PROMPT).assertDoesNotExist()
        assertEquals(0, homeRole.marked)
    }

    private fun completed(homeRoleAsked: Boolean) = PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = homeRoleAsked))

    private class RecordingHomeRole : HomeRoleActions {
        var isHome = false
        var canRequest = true
        var requests = 0
        var marked = 0

        override fun isHomeApp(): Boolean = isHome

        override fun requestHomeRole(): Boolean {
            requests++
            return canRequest
        }

        override fun markAsked() {
            marked++
        }
    }
}
