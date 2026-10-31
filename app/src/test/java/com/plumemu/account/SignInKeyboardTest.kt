package com.plumemu.account

import android.content.Context
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performImeAction
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The sign-in form and the Android keyboard, with the real text input: the keyboard
 * comes up only on A or a tap on a field, never when the form opens; the keyboard's Next and Done
 * act; what the keyboard is told (no full-screen editor, the field's name, the password type) is
 * read from the EditorInfo the platform gets, built the platform's way (`onCreateInputConnection`).
 *
 * Android 9 (sdk 28): there Compose shows the keyboard through `InputMethodManager.showSoftInput`,
 * which Robolectric's shadow records; on newer versions it goes through the insets controller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
class SignInKeyboardTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val form = SignInForm()
    private val submits = mutableListOf<SignInStep.Submit>()
    private lateinit var view: View

    /** The form as a host shows it: A and the keyboard's actions reach [SignInForm.onAction], taps [SignInForm.tap]. */
    private fun show() {
        compose.setContent {
            view = LocalView.current
            PlumTheme {
                SignInBody(
                    form = form,
                    leaveLabel = R.string.setup_box_art_skip,
                    onTap = { index -> record(form.tap(index)) },
                    onAction = { action -> record(form.onAction(action)) },
                    gap = PlumDimens.setupContentGap,
                )
            }
        }
        settle()
    }

    private fun record(step: SignInStep) {
        if (step is SignInStep.Submit) submits += step
    }

    private fun press(action: Action) {
        compose.runOnIdle { record(form.onAction(action)) }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private val keyboardShows: Boolean
        get() = shadowOf(context.getSystemService(InputMethodManager::class.java)).isSoftInputVisible

    private fun textInput(tag: String): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    /** What the keyboard is told about the focused field, asked the way the platform asks the window's view. */
    private fun editorInfo(): EditorInfo {
        val info = EditorInfo()
        val connection = compose.runOnIdle { view.onCreateInputConnection(info) }
        assertNotNull("a text field has the input connection", connection)
        return info
    }

    private fun assertInlineKeyboardNamed(name: String) {
        val info = editorInfo()
        assertTrue("no full-screen editor (IME_FLAG_NO_FULLSCREEN)", info.imeOptions and EditorInfo.IME_FLAG_NO_FULLSCREEN != 0)
        assertTrue("no extracted text UI (IME_FLAG_NO_EXTRACT_UI)", info.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0)
        assertEquals("the keyboard can name the field", name, info.hintText?.toString())
    }

    @Test
    fun openingTheFormPutsTheCursorOnTheUsernameWithoutTheKeyboard() {
        show()
        assertEquals(SignInItem.Username, form.focus)
        assertFalse("no keyboard until A or a tap", keyboardShows)
        textInput(SignInTags.USERNAME).assertIsNotFocused()
        textInput(SignInTags.PASSWORD).assertIsNotFocused()
    }

    @Test
    fun movingOntoThePasswordOpensNoKeyboardEither() {
        show()
        press(Action.Move(Direction.Down))
        assertEquals(SignInItem.Password, form.focus)
        assertFalse(keyboardShows)
        textInput(SignInTags.PASSWORD).assertIsNotFocused()
    }

    @Test
    fun aOnTheUsernameOpensItsKeyboardAndTheKeyboardNamesIt() {
        show()
        press(Action.Enter)
        assertEquals("A keeps the cursor on its row", SignInItem.Username, form.focus)
        textInput(SignInTags.USERNAME).assertIsFocused()
        assertTrue("A opens the keyboard", keyboardShows)
        assertInlineKeyboardNamed(context.getString(R.string.account_username_hint))
        assertEquals("next goes to the password", EditorInfo.IME_ACTION_NEXT, editorInfo().imeOptions and EditorInfo.IME_MASK_ACTION)
    }

    @Test
    fun theKeyboardsNextMovesToThePasswordWithItsKeyboard() {
        show()
        press(Action.Enter)
        textInput(SignInTags.USERNAME).performImeAction()
        settle()
        assertEquals(SignInItem.Password, form.focus)
        textInput(SignInTags.PASSWORD).assertIsFocused()
        assertTrue(keyboardShows)
        assertInlineKeyboardNamed(context.getString(R.string.account_password_hint))
        val info = editorInfo()
        assertEquals("Done on the password", EditorInfo.IME_ACTION_DONE, info.imeOptions and EditorInfo.IME_MASK_ACTION)
        assertEquals("the password keyboard", InputType.TYPE_TEXT_VARIATION_PASSWORD, info.inputType and InputType.TYPE_MASK_VARIATION)
    }

    @Test
    fun theKeyboardsDoneSignsIn() {
        form.onUsernameChange("plumtester")
        form.onPasswordChange("hunter2")
        show()
        press(Action.Move(Direction.Down))
        press(Action.Enter)
        textInput(SignInTags.PASSWORD).performImeAction()
        settle()
        assertEquals(listOf(SignInStep.Submit("plumtester", "hunter2")), submits)
        assertTrue(form.isChecking)
        assertFalse("the keyboard goes while the account is checked", keyboardShows)
    }

    @Test
    fun eachFieldTellsTheKeyboardNoFullScreenAndItsName() {
        show()
        compose.runOnIdle { record(form.tap(form.items.indexOf(SignInItem.Username))) }
        settle()
        assertInlineKeyboardNamed(context.getString(R.string.account_username_hint))
        compose.runOnIdle { record(form.tap(form.items.indexOf(SignInItem.Password))) }
        settle()
        assertInlineKeyboardNamed(context.getString(R.string.account_password_hint))
    }

    @Test
    fun aTapOnTheFieldOpensItsKeyboard() {
        show()
        compose.runOnIdle { record(form.tap(form.items.indexOf(SignInItem.Password))) }
        settle()
        assertEquals(SignInItem.Password, form.focus)
        textInput(SignInTags.PASSWORD).assertIsFocused()
        assertTrue(keyboardShows)
    }

    @Test
    fun aBringsTheKeyboardBackAfterTheSystemClosedIt() {
        show()
        press(Action.Enter)
        assertTrue(keyboardShows)
        // The system's Back closes the keyboard; the app hears nothing and the field keeps its focus.
        compose.runOnIdle { context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken, 0) }
        settle()
        assertFalse(keyboardShows)
        textInput(SignInTags.USERNAME).assertIsFocused()
        press(Action.Enter)
        assertTrue("A on the same row brings it back", keyboardShows)
    }

    @Test
    fun leavingTheFieldsPutsTheKeyboardAwayAndAAgainBringsItBack() {
        show()
        press(Action.Enter)
        assertTrue(keyboardShows)
        press(Action.Move(Direction.Down))
        assertFalse("the keyboard goes with the cursor", keyboardShows)
        textInput(SignInTags.PASSWORD).assertIsNotFocused()
        press(Action.Move(Direction.Up))
        assertFalse("coming back opens nothing", keyboardShows)
        press(Action.Enter)
        assertTrue(keyboardShows)
        textInput(SignInTags.USERNAME).assertIsFocused()
    }
}
