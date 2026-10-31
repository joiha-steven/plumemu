package com.plumemu.setup

import android.content.Context
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.account.FakeScraperAccounts
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInTags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.settings.ButtonLetters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The box art question on screen, through the router like the device: by gamepad
 * (default focus, left / right, A on each button, B) and by touch (a tap on each button, the Back
 * hint). Use my account shows the form with the cursor on the username and no keyboard (Android 9,
 * where Robolectric's InputMethodManager shadow sees Compose's keyboard requests).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
class SetupBoxArtQuestionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val source = FakeSetupSource()
    private val accounts = FakeScraperAccounts()
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val model = SetupModel(modelScope, source, accounts) { }
    private lateinit var router: InputRouter

    @After
    fun tearDown() = modelScope.cancel()

    /** Setup on its folder step with one folder, then Continue: the question. */
    private fun showTheQuestion() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            // Android's keyboard: the IME path these tests watch (Plumemu's tray is SetupOwnKeyboard*Test's).
            PlumTheme { SetupScreen(model, router, ButtonLetters.Default, onOpenFolderPicker = {}, keyboard = KeyboardChoice.Android) }
        }
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        compose.waitForIdle()
        send(Action.Enter)
        compose.onNodeWithText(context.getString(R.string.setup_box_art_question_title)).assertIsDisplayed()
    }

    private fun send(action: Action) {
        compose.runOnIdle { router.send(action) }
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private val focus: SetupControl? get() = model.state.value.focus

    private val keyboardShows: Boolean
        get() {
            shadowOf(Looper.getMainLooper()).idle()
            return shadowOf(context.getSystemService(InputMethodManager::class.java)).isSoftInputVisible
        }

    @Test
    fun theGamepadStartsOnUseMyAccountWalksBothButtonsAndAOpensTheFormWithoutTheKeyboard() {
        showTheQuestion()
        compose.onNodeWithTag(SetupTags.BOX_ART).assertDoesNotExist()
        assertEquals(SetupControl.UseBoxArt, focus)
        send(Action.Move(Direction.Right))
        assertEquals(SetupControl.SkipBoxArt, focus)
        send(Action.Move(Direction.Left))
        assertEquals(SetupControl.UseBoxArt, focus)

        send(Action.Enter)
        compose.onNodeWithTag(SetupTags.BOX_ART).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.setup_box_art_title)).assertIsDisplayed()
        assertEquals(SignInItem.Username, model.state.value.boxArt?.focus)
        assertFalse("the form opens with the keyboard down", keyboardShows)

        send(Action.Back)
        compose.onNodeWithTag(SetupTags.BOX_ART).assertDoesNotExist()
        assertEquals("B from the form: the question, on Use my account", SetupControl.UseBoxArt, focus)
        send(Action.Back)
        compose.onNodeWithText(context.getString(R.string.setup_folder_title)).assertIsDisplayed()
    }

    private fun textInput(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    @Test
    fun theKeyboardsDoneOnThePasswordSignsInExactlyOnceThroughTheRouter() {
        showTheQuestion()
        send(Action.Enter) // Use my account
        val form = requireNotNull(model.state.value.boxArt)
        compose.runOnIdle {
            form.onUsernameChange("plumtester")
            form.onPasswordChange("hunter2")
        }
        send(Action.Enter) // the username's keyboard
        send(Action.Move(Direction.Down))
        send(Action.Enter) // the password's keyboard
        assertTrue(form.inField)
        textInput(SignInTags.PASSWORD).performImeAction()
        compose.waitForIdle()
        assertEquals("one sign-in, not one per Enter", listOf("plumtester" to "hunter2"), accounts.signIns)
        assertEquals("plumtester", form.signedInAs)
    }

    @Test
    fun theKeyboardsNextOnTheUsernameMovesToThePasswordOnce() {
        showTheQuestion()
        send(Action.Enter)
        val form = requireNotNull(model.state.value.boxArt)
        send(Action.Enter)
        textInput(SignInTags.USERNAME).performTextInput("plumtester")
        textInput(SignInTags.USERNAME).performImeAction()
        compose.waitForIdle()
        assertEquals("Next lands on the password, not past it", SignInItem.Password, form.focus)
        assertTrue("with its keyboard", form.inField)
        assertTrue(accounts.signIns.isEmpty())
        assertEquals("plumtester", form.username)
    }

    @Test
    fun theGamepadsAOnNotNowGoesOnToTheScan() {
        showTheQuestion()
        send(Action.Move(Direction.Right))
        send(Action.Enter)
        compose.onNodeWithText(context.getString(R.string.setup_scan_title)).assertIsDisplayed()
        assertTrue(accounts.signIns.isEmpty())
    }

    @Test
    fun aTapOnUseMyAccountOpensTheFormAndItsBackHintReturnsToTheQuestion() {
        showTheQuestion()
        tap(SetupTags.control(SetupControl.UseBoxArt))
        compose.onNodeWithTag(SetupTags.BOX_ART).assertIsDisplayed()
        assertFalse(keyboardShows)
        tap(SetupTags.HINT_BACK)
        compose.onNodeWithTag(SetupTags.BOX_ART_QUESTION).assertIsDisplayed()
        assertNull(model.state.value.boxArt)
    }

    @Test
    fun aTapOnNotNowGoesOnToTheScanInOneTap() {
        showTheQuestion()
        tap(SetupTags.control(SetupControl.SkipBoxArt))
        compose.onNodeWithText(context.getString(R.string.setup_scan_title)).assertIsDisplayed()
    }
}
