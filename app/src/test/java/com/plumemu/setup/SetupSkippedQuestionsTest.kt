package com.plumemu.setup

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.plumemu.MainScreen
import com.plumemu.R
import com.plumemu.SetupHost
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.reopensSkippedSetup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Set up later leaves the questions unasked; the first folder brings them back (the model's side). */
class SetupSkippedQuestionsModelTest {
    private val source = FakeSetupSource()

    private fun TestScope.model(): SetupModel {
        val model = SetupModel(backgroundScope, source) { }
        backgroundScope.launch { model.state.collect { } }
        runCurrent()
        return model
    }

    private fun TestScope.setUpLaterAndStartPlaying(model: SetupModel) {
        model.onAction(Action.TapItem(Column.Games, 1)) // Set up later
        runCurrent()
        model.onAction(Action.Enter) // Start playing
        runCurrent()
    }

    @Test
    fun setUpLaterThenStartPlayingStoresThatTheQuestionsWereSkipped() = runTest {
        val model = model()
        setUpLaterAndStartPlaying(model)
        assertEquals(1, source.completions)
        assertTrue(source.questionsSkipped)
    }

    @Test
    fun aFailedLoadLeftWithSetUpLaterDoesNotMarkTheQuestionsSkipped() = runTest {
        source.foldersFailure = IllegalStateException("Room: disk I/O error")
        val model = model()
        model.onAction(Action.TapItem(Column.Games, 1))
        runCurrent()
        assertEquals(1, source.completions)
        assertFalse(source.questionsSkipped)
    }

    @Test
    fun aFullSetupDoesNotMarkThem() = runTest {
        val model = model()
        model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE))
        runCurrent()
        model.onAction(Action.Enter) // Continue
        runCurrent()
        assertEquals(SetupStep.Look, model.state.value.step)
        model.onAction(Action.Enter)
        runCurrent()
        model.onAction(Action.Enter)
        runCurrent()
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        model.onAction(Action.Enter)
        runCurrent()
        assertFalse(source.questionsSkipped)
    }

    @Test
    fun backToTheFolderStepAndOnAnswersTheQuestionsSoNothingIsMarked() = runTest {
        val model = model()
        model.onAction(Action.TapItem(Column.Games, 1)) // Set up later: Ready
        runCurrent()
        model.onAction(Action.Back)
        runCurrent()
        model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE))
        runCurrent()
        model.onAction(Action.Enter) // Continue: Look is asked now
        runCurrent()
        assertEquals(SetupStep.Look, model.state.value.step)
        model.onAction(Action.Enter)
        runCurrent()
        model.onAction(Action.Enter)
        runCurrent()
        source.endScan()
        runCurrent()
        model.onAction(Action.Enter)
        runCurrent()
        assertFalse(source.questionsSkipped)
    }

    @Test
    fun reopeningAfterSetUpLaterAsksTheQuestionsFromTheFirstOneAndFinishingClearsTheMark() = runTest {
        val model = model()
        setUpLaterAndStartPlaying(model)
        assertTrue(source.questionsSkipped)

        source.heldFolders.value = listOf(SetupFakes.romsFolder)
        model.reopenForSkippedQuestions()
        runCurrent()
        assertEquals("the step after Folders", SetupStep.Look, model.state.value.step)
        assertEquals("one scan: Continue's own move", 1, source.scanRequests)

        model.onAction(Action.Enter) // Look
        runCurrent()
        model.onAction(Action.Enter) // Box art
        runCurrent()
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        model.onAction(Action.Enter) // Start playing
        runCurrent()
        assertEquals("setup finished a second time", 2, source.completions)
        assertFalse("finishing clears the mark", source.questionsSkipped)
    }

    @Test
    fun reopenedSetupFinishesByTouchToo() = runTest {
        val model = model()
        setUpLaterAndStartPlaying(model)
        source.heldFolders.value = listOf(SetupFakes.romsFolder)
        model.reopenForSkippedQuestions()
        runCurrent()
        model.onAction(Action.Enter)
        runCurrent()
        model.onAction(Action.Enter)
        runCurrent()
        source.endScan()
        runCurrent()
        model.onAction(Action.TapItem(Column.Games, model.state.value.controls.indexOf(SetupControl.StartPlaying)))
        runCurrent()
        assertEquals(2, source.completions)
        assertFalse(source.questionsSkipped)
    }

    @Test
    fun backOnTheFirstReopenedQuestionGoesToTheFolderStepAndNothingIsLost() = runTest {
        val model = model()
        setUpLaterAndStartPlaying(model)
        source.heldFolders.value = listOf(SetupFakes.romsFolder)
        model.reopenForSkippedQuestions()
        runCurrent()
        model.onAction(Action.Back)
        runCurrent()
        assertEquals(SetupStep.Folders, model.state.value.step)
        assertEquals(1, model.state.value.folders.size)
        model.onAction(Action.Back)
        runCurrent()
        assertEquals("Back from the folder step does nothing", SetupStep.Folders, model.state.value.step)
        assertEquals("setup is not finished by it", 1, source.completions)
    }

    @Test
    fun aReopenedSetupThatIsLeftWithSetUpLaterAgainMarksThemAgain() = runTest {
        val model = model()
        setUpLaterAndStartPlaying(model)
        source.heldFolders.value = listOf(SetupFakes.romsFolder)
        model.reopenForSkippedQuestions()
        runCurrent()
        model.onAction(Action.Back) // folder step, with its folder
        runCurrent()
        source.heldFolders.value = emptyList() // the person removed it
        runCurrent()
        model.onAction(Action.TapItem(Column.Games, 1)) // Set up later
        runCurrent()
        model.onAction(Action.Enter)
        runCurrent()
        assertTrue(source.questionsSkipped)
        assertTrue("the screen can go although the mark stays", model.hasLeftReopened.value)
    }

    @Test
    fun aFailedLoadLeftInAReopenedSetupKeepsTheMark() = runTest {
        val model = model()
        setUpLaterAndStartPlaying(model)
        source.heldFolders.value = listOf(SetupFakes.romsFolder)
        model.reopenForSkippedQuestions()
        runCurrent()
        source.foldersFailure = IllegalStateException("Room")
        source.heldFolders.value = emptyList()
        runCurrent()
        assertTrue(model.state.value.hasLoadFailed)
        model.onAction(Action.TapItem(Column.Games, 1)) // Set up later
        runCurrent()
        assertTrue(source.questionsSkipped)
        assertTrue(model.hasLeftReopened.value)
    }

    @Test
    fun reopeningAgainKeepsThePlace() = runTest {
        val model = model()
        setUpLaterAndStartPlaying(model)
        source.heldFolders.value = listOf(SetupFakes.romsFolder)
        model.reopenForSkippedQuestions()
        runCurrent()
        model.onAction(Action.Enter) // Look: on to box art
        runCurrent()
        val step = model.state.value.step
        model.reopenForSkippedQuestions() // a recreated Activity
        runCurrent()
        assertEquals(step, model.state.value.step)
    }

    @Test
    fun aFolderUnreadableOnHomeIsNoFolderNotACrash() = runTest {
        source.foldersFailure = IllegalStateException("Room")
        assertFalse(model().hasFolder.first())
    }
}

/** The main screen brings setup back for the skipped questions: a folder and Home, nothing else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SetupSkippedQuestionsScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val source = FakeSetupSource()
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val model = SetupModel(modelScope, source) { }
    private val settings = mutableStateOf(PlumSettings())
    private lateinit var router: InputRouter

    @After
    fun tearDown() = modelScope.cancel()

    private fun show(skipped: Boolean) {
        settings.value = PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = true, setupQuestionsSkipped = skipped))
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SetupHost({ model }, {}, SetupHomeRoleStub) }
            MainScreen(settings.value, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(), onRetry = {}, setup = host)
        }
    }

    private fun addFolder() {
        compose.runOnIdle { source.heldFolders.value = listOf(SetupFakes.romsFolder) }
        compose.waitForIdle()
    }

    @Test
    fun withTheMarkAndNoFolderHomeStaysAndNobodyIsBothered() {
        show(skipped = true)
        compose.onNodeWithTag(SetupTags.SCREEN).assertDoesNotExist()
        compose.onNodeWithText("Super Nintendo").assertIsDisplayed()
    }

    @Test
    fun withTheMarkAndAFolderSetupComesBackOnTheFirstQuestion() {
        show(skipped = true)
        addFolder()
        compose.onNodeWithTag(SetupTags.SCREEN).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.setup_look_title)).assertIsDisplayed()
    }

    @Test
    fun aFolderWithoutTheMarkLeavesHomeAlone() {
        show(skipped = false)
        addFolder()
        compose.onNodeWithTag(SetupTags.SCREEN).assertDoesNotExist()
        compose.onNodeWithText("Super Nintendo").assertIsDisplayed()
    }

    @Test
    fun finishingTheReopenedSetupClearsTheMarkAndHomeReturns() {
        show(skipped = true)
        addFolder()
        compose.runOnIdle { router.send(Action.Enter) }
        compose.waitForIdle()
        compose.runOnIdle { router.send(Action.Enter) }
        compose.waitForIdle()
        compose.runOnIdle { source.endScan() }
        compose.waitForIdle()
        compose.runOnIdle { router.send(Action.Enter) } // Start playing
        compose.waitForIdle()
        assertFalse(source.questionsSkipped)
        // What the store does once completeSetup lands.
        settings.value = PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = true))
        compose.waitForIdle()
        compose.onNodeWithTag(SetupTags.SCREEN).assertDoesNotExist()
        compose.onNodeWithText("Super Nintendo").assertExists() // landing on the start row
    }

    @Test
    fun aReopenedSetupLeftWithTheMarkStillSetGoes() {
        show(skipped = true)
        addFolder()
        compose.onNodeWithTag(SetupTags.SCREEN).assertIsDisplayed()
        compose.runOnIdle { router.send(Action.Back) } // the folder step, with its folder
        compose.waitForIdle()
        compose.runOnIdle { source.heldFolders.value = emptyList() } // removed or its permission gone
        compose.waitForIdle()
        compose.runOnIdle { router.send(Action.TapItem(Column.Games, 1)) } // Set up later
        compose.waitForIdle()
        compose.runOnIdle { router.send(Action.Enter) } // Start playing
        compose.waitForIdle()
        assertTrue(source.questionsSkipped)
        compose.onNodeWithTag(SetupTags.SCREEN).assertDoesNotExist()
    }

    @Test
    fun setupWaitsWhileThePersonIsNotOnHome() {
        val onHome = mutableStateOf(false)
        compose.setContent {
            val host = remember { SetupHost({ model }, {}, SetupHomeRoleStub) }
            val reopens = reopensSkippedSetup(host, isSkipped = true, isOnHome = onHome.value)
            androidx.compose.foundation.text.BasicText(if (reopens) "reopened" else "waiting")
        }
        addFolder()
        compose.onNodeWithText("waiting").assertIsDisplayed()
        compose.runOnIdle { onHome.value = true }
        compose.waitForIdle()
        compose.onNodeWithText("reopened").assertIsDisplayed()
    }

    /** Plumemu is not asked about being the home app in these tests. */
    private object SetupHomeRoleStub : HomeRoleActions {
        override fun isHomeApp() = true

        override fun requestHomeRole(onAnswered: (Boolean) -> Unit) = false

        override fun markAsked() = Unit
    }
}
