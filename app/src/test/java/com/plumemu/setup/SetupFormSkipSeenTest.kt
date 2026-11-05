package com.plumemu.setup

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Box art and game info" without a usable account opens the sign-in form with the keyboard down and the cursor on the username:
 * both fields and Sign in and Skip are in view at once (a person must see that Skip exists), at the default text size.
 */
internal abstract class SetupFormSkipSeenBase : SetupKeyboardPathHarness() {
    @Test
    fun theDescriptionIsHiddenWhileTheTrayIsUpAndTheTitleStays() {
        showTheForm(startTyping = false)
        assertTrue("the description shows with the tray down", exists(SetupTags.DESCRIPTION))
        press(confirm)
        assertTrue("the tray is up", isTrayUp)
        assertFalse("the description is hidden while the tray is up", exists(SetupTags.DESCRIPTION))
        assertTrue("the title stays", exists(SetupTags.TITLE))
        val window = compose.onRoot().getBoundsInRoot()
        val title = compose.onNodeWithTag(SetupTags.TITLE, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the title is whole: $title in $window", title.top >= window.top && title.bottom <= window.bottom)
    }

    @Test
    fun theFormOpensWithTheTrayDownAndSkipInsideTheWindow() {
        showTheForm(startTyping = false)
        assertFalse("the keyboard is down", form.isTyping)
        assertFalse("no tray", isTrayUp)
        assertEquals(SignInItem.Username, form.focus)
        val window = compose.onRoot().getBoundsInRoot()
        for (item in listOf(SignInItem.SignIn, SignInItem.Leave)) {
            val box = compose.onNodeWithTag(SignInTags.item(item), useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$item is inside the window: $box in $window", box.top >= window.top && box.bottom <= window.bottom && box.left >= window.left && box.right <= window.right)
        }
        for (field in listOf(SignInTags.USERNAME, SignInTags.PASSWORD)) {
            val box = compose.onNodeWithTag(field, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$field is inside the window: $box in $window", box.bottom <= window.bottom)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w832dp-h468dp-land-xhdpi")
internal class SetupFormSkipSeen832Test : SetupFormSkipSeenBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SetupFormSkipSeen768Test : SetupFormSkipSeenBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w576dp-h432dp-land-xhdpi")
internal class SetupFormSkipSeen576Test : SetupFormSkipSeenBase()
