package com.plumemu.settings.screen

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.plumemu.account.AccountStatus
import com.plumemu.account.SignInTags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Library › Sign in to ScreenScraper on the real screen: the keyboard's
 * Done reaches the model as an Enter through the router, as on the device, and signs in exactly
 * once; its Next on the username lands on the password once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SettingsSignInKeyboardTest : SettingsModelHarness() {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private val content = SettingsContent(account = AccountStatus.SignedOut)

    // Typed as the interface: the Compose compiler gives the large FakeActions no `$stable`
    // field, and a composable call typed FakeActions reads it (NoSuchFieldError).
    private val screenActions: SettingsActions get() = actions

    private fun send(action: Action) {
        compose.runOnIdle { router.send(action) }
        compose.waitForIdle()
    }

    private fun textInput(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    /** Settings on its sections, then Library › the account row and A: the sign-in panel. */
    private fun openTheForm() {
        // Android's keyboard: the IME path these tests watch (Plumemu's tray is SettingsSignInOwnKeyboardTest's).
        useAndroidKeyboard()
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            PlumTheme { SettingsScreen(actions.shown, router, screenActions, versionName = "test", content = content) }
        }
        compose.waitForIdle()
        repeat(SettingsSection.Library.ordinal) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        repeat(SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.ScraperAccount)) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        compose.onNodeWithTag(SignInTags.FORM).assertIsDisplayed()
    }

    @Test
    fun theKeyboardsDoneOnThePasswordSignsInExactlyOnce() {
        openTheForm()
        send(Action.Enter) // the username's keyboard
        textInput(SignInTags.USERNAME).performTextInput("plumtester")
        send(Action.Move(Direction.Down))
        send(Action.Enter) // the password's keyboard
        textInput(SignInTags.PASSWORD).performTextInput("hunter2")
        textInput(SignInTags.PASSWORD).performImeAction()
        compose.waitForIdle()
        assertEquals("one sign-in, not one per Enter", listOf("plumtester" to "hunter2"), actions.accountsFake.signIns)
        compose.onNodeWithTag(SignInTags.FORM).assertDoesNotExist()
    }

    @Test
    fun theKeyboardsNextOnTheUsernameLandsOnThePasswordOnce() {
        openTheForm()
        send(Action.Enter)
        textInput(SignInTags.USERNAME).performTextInput("plumtester")
        textInput(SignInTags.USERNAME).performImeAction()
        compose.waitForIdle()
        // The password's text field takes the input focus only while it is the one being typed in.
        textInput(SignInTags.PASSWORD).assertIsFocused()
        assertTrue("nothing asked", actions.accountsFake.signIns.isEmpty())
    }
}
