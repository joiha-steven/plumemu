package com.plumemu.setup

import android.view.KeyEvent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.pressButton
import com.plumemu.home.rulesAround
import com.plumemu.settings.ButtonLetters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The confirm step through the real router with real key events: two rows (Use A, Use B), A chooses the
 * focused row, B and the system's Back go back to the folders and store nothing; no key is read as an answer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
class SetupConfirmRouterTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val source = FakeSetupSource().apply { hasGamepad.value = true }
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val model = SetupModel(modelScope, source) { }
    private lateinit var router: InputRouter

    @After
    fun tearDown() = modelScope.cancel()

    private fun press(code: Int) {
        compose.runOnIdle { router.pressButton(code) }
        compose.waitForIdle()
    }

    /** Setup with a folder, then A on Continue: the confirm step. */
    private fun showTheConfirmStep() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            // The letters follow the stored setting, as MainScreen's do.
            PlumTheme { SetupScreen(model, router, ButtonLetters.of(source.confirmButton ?: ConfirmButtonSetting.Right), onOpenFolderPicker = {}) }
        }
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        compose.waitForIdle()
        press(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(SetupStep.Confirm, model.state.value.step)
    }

    @Test
    fun theHintBarOffersASelectAndBBack() {
        showTheConfirmStep()
        compose.onNodeWithTag(SetupTags.HINT_BACK).assertIsDisplayed()
        compose.onNode(hasTestTag(SetupTags.HINT_BACK) and hasAnyDescendant(hasText("B"))).assertIsDisplayed()
        compose.onNode(hasTestTag(SetupTags.HINT_CONFIRM) and hasAnyDescendant(hasText("Select"))).assertIsDisplayed()
        compose.onNode(hasTestTag(SetupTags.HINT_CONFIRM) and hasAnyDescendant(hasText("A"))).assertIsDisplayed()
    }

    @Test
    fun aRealBKeyGoesBackAndIsNotTakenAsAnAnswer() {
        showTheConfirmStep()
        press(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(SetupStep.Folders, model.state.value.step)
        assertNull(source.confirmButton)
    }

    @Test
    fun aRealAKeyOnUseAStoresAAndMovesOn() {
        showTheConfirmStep()
        press(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(ConfirmButtonSetting.Right, source.confirmButton)
        assertEquals(SetupStep.Look, model.state.value.step)
    }

    @Test
    fun theDpadDownThenAStoresBAndMovesOn() {
        showTheConfirmStep()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(ConfirmButtonSetting.Bottom, source.confirmButton)
        assertEquals(SetupStep.Look, model.state.value.step)
    }

    @Test
    fun xAndYDoNothingHere() {
        showTheConfirmStep()
        press(KeyEvent.KEYCODE_BUTTON_X)
        press(KeyEvent.KEYCODE_BUTTON_Y)
        assertEquals(SetupStep.Confirm, model.state.value.step)
        assertNull(source.confirmButton)
    }

    @Test
    fun theSystemsBackKeyGoesBackToTheFoldersAndStoresNothing() {
        showTheConfirmStep()
        press(KeyEvent.KEYCODE_BACK)
        assertEquals(SetupStep.Folders, model.state.value.step)
        assertNull(source.confirmButton)
    }

    @Test
    fun aTapOnTheBackHintGoesBack() {
        showTheConfirmStep()
        compose.onNodeWithTag(SetupTags.HINT_BACK).performClickAndSettle()
        assertEquals(SetupStep.Folders, model.state.value.step)
        assertNull(source.confirmButton)
    }

    @Test
    fun goingBackWithBThroughTheConfirmStepDoesNotStoreB() {
        showTheConfirmStep()
        press(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(SetupStep.Look, model.state.value.step)
        press(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
        press(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(SetupStep.Look, model.state.value.step)
        press(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(SetupStep.Confirm, model.state.value.step)
        press(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(ConfirmButtonSetting.Right, source.confirmButton)
    }

    /** Answer A, go on to the box art step, then back with B twice: the confirm step reached again. */
    private fun revisitTheConfirmStep() {
        showTheConfirmStep()
        press(KeyEvent.KEYCODE_BUTTON_A)
        press(KeyEvent.KEYCODE_BUTTON_A)
        press(KeyEvent.KEYCODE_BUTTON_B)
        press(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(SetupStep.Confirm, model.state.value.step)
    }

    @Test
    fun aRevisitShowsTheStoredAnswerAsTwoRowsFocusedOnIt() {
        revisitTheConfirmStep()
        assertEquals(listOf(SetupControl.ConfirmKeep, SetupControl.ConfirmUseB), model.state.value.controls)
        assertEquals(SetupControl.ConfirmKeep, model.state.value.focus)
        compose.onNodeWithText("Use A").assertIsDisplayed()
        compose.onNodeWithText("Use B").assertIsDisplayed()
        compose.onNodeWithText("Confirm button: A").assertIsDisplayed()
    }

    @Test
    fun aRevisitDpadMoveToUseBThenConfirmStoresBAndSwitchesTheHints() {
        revisitTheConfirmStep()
        assertConfirmCap("A")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(SetupControl.ConfirmUseB, model.state.value.focus)
        assertEquals(ConfirmButtonSetting.Right, source.confirmButton)
        press(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(ConfirmButtonSetting.Bottom, source.confirmButton)
        assertEquals(SetupStep.Confirm, model.state.value.step)
        compose.onNodeWithText("Confirm button: B").assertIsDisplayed()
        assertConfirmCap("B")
    }

    @Test
    fun aRevisitTapOnUseBChoosesIt() {
        revisitTheConfirmStep()
        compose.onNodeWithText("Use B").performClickAndSettle()
        assertEquals(ConfirmButtonSetting.Bottom, source.confirmButton)
        assertEquals(SetupStep.Confirm, model.state.value.step)
    }

    @Test
    fun aRevisitBGoesBackAndStoresNothingNew() {
        revisitTheConfirmStep()
        press(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(SetupStep.Folders, model.state.value.step)
        assertEquals(ConfirmButtonSetting.Right, source.confirmButton)
    }

    @Test
    fun aRevisitConfirmOnTheStoredRowGoesOn() {
        revisitTheConfirmStep()
        press(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(SetupStep.Look, model.state.value.step)
        assertEquals(ConfirmButtonSetting.Right, source.confirmButton)
    }

    private fun assertConfirmCap(letter: String) {
        compose.onNode(hasTestTag(SetupTags.HINT_CONFIRM) and hasAnyDescendant(hasText(letter))).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickAndSettle() {
        performClick()
        compose.waitForIdle()
    }
}
