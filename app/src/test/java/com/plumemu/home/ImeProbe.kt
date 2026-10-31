package com.plumemu.home

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf

/**
 * What a test needs to ask "is the Android keyboard up, and what was it told?" on Robolectric
 * sdk 28 (there Compose shows the keyboard through `InputMethodManager.showSoftInput`, which the
 * shadow records). Shared by the keyboard tests of Fix name, Search and the colour panel; the
 * sign-in form's test keeps its own copy.
 */
internal class ImeProbe(private val compose: ComposeContentTestRule) {
    val context: Context = ApplicationProvider.getApplicationContext()
    lateinit var view: View

    fun settle() {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    val keyboardShows: Boolean
        get() = shadowOf(context.getSystemService(InputMethodManager::class.java)).isSoftInputVisible

    /** The system's Back closing the keyboard: the app hears nothing and the field keeps its focus. */
    fun closeKeyboardFromTheSystem() {
        compose.runOnIdle { context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken, 0) }
        settle()
    }

    fun textInput(tag: String): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    /** A text field that carries the tag itself (search, hex), not a row around it. */
    fun taggedField(tag: String): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasTestTag(tag), useUnmergedTree = true)

    /** What the keyboard is told about the focused field, asked the way the platform asks the window's view. */
    fun editorInfo(): EditorInfo {
        val info = EditorInfo()
        val connection = compose.runOnIdle { view.onCreateInputConnection(info) }
        assertNotNull("a text field has the input connection", connection)
        return info
    }

    fun assertInlineKeyboardNamed(name: String) {
        val info = editorInfo()
        assertTrue("no full-screen editor (IME_FLAG_NO_FULLSCREEN)", info.imeOptions and EditorInfo.IME_FLAG_NO_FULLSCREEN != 0)
        assertTrue("no extracted text UI (IME_FLAG_NO_EXTRACT_UI)", info.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0)
        assertEquals("the keyboard can name the field", name, info.hintText?.toString())
    }
}
