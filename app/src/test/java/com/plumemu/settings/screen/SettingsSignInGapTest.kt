package com.plumemu.settings.screen

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import com.plumemu.account.SignInForm
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInTags
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.keyboard.KeyboardChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings' sign-in form keeps one gap between its rows, setup's 8, whether Plumemu's keyboard tray is up or
 * down: opening the tray moves the form as a whole and never pulls its rows 2 dp closer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsSignInGapTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun top(tag: String): Dp = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().top

    private fun bottom(tag: String): Dp = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().bottom

    /** The gaps username → password and password → Sign in (dp). */
    private fun gaps(): List<Float> =
        listOf(
            (top(SignInTags.PASSWORD) - bottom(SignInTags.USERNAME)).value,
            (top(SignInTags.item(SignInItem.SignIn)) - bottom(SignInTags.PASSWORD)).value,
        )

    @Test
    fun theFormsGapIsTheSameWithTheTrayUpOrDown() {
        val form = SignInForm(stackedButtons = true, keyboardKind = KeyboardChoice.Plumemu)
        compose.setContent { PlumTheme { SignInSettingsWith(form) } }
        compose.waitForIdle()
        assertTrue("the tray starts down", !form.isTrayUp)
        val down = gaps()
        compose.runOnIdle { form.onAction(Action.Enter) }
        compose.waitForIdle()
        assertTrue("A on the username opens the tray", form.isTrayUp)
        val up = gaps()
        assertEquals("the gaps with the tray up as with it down", down, up)
    }
}
