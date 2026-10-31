package com.plumemu.home

import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import com.plumemu.R
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.search.SearchHit
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.home.search.SearchState
import com.plumemu.home.search.SearchTags
import com.plumemu.home.search.SearchTopBar
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Search's field and the Android keyboard (the old path since Plumemu's own keyboard,
 * [KeyboardChoice.Android], Settings › Controls › Keyboard set to Android): entering Search is an explicit "I want
 * to type", so the keyboard may come up at once, but never as a full-screen editor and always
 * named. Android 9 (sdk 28), as the sign-in form's test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
class SearchKeyboardTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val ime = ImeProbe(compose)

    private fun sampleHit() = SearchHit(GameRowModel("g0", "Game 0", "USA"), "snes")

    private fun showOpen(search: SearchState) {
        compose.setContent {
            ime.view = LocalView.current
            PlumTheme { SearchTopBar(search, clock = { null }, battery = { null }) }
        }
        compose.runOnIdle { search.open() }
        ime.settle()
    }

    @Test
    fun aBringsTheKeyboardBackAfterTheSystemClosedItAndDoesNotLeaveTheField() {
        val search = SearchState(KeyboardChoice.Android)
        showOpen(search)
        assertTrue(ime.keyboardShows)
        ime.closeKeyboardFromTheSystem()
        assertFalse(ime.keyboardShows)
        compose.runOnIdle { search.onAction(Action.Enter) }
        ime.settle()
        assertTrue("A on the field brings the keyboard back", ime.keyboardShows)
        assertTrue("and stays on the field", search.inField)
    }

    @Test
    fun comingBackUpFromTheResultsOpensNoKeyboardButAAndATapDo() {
        val search = SearchState(KeyboardChoice.Android)
        showOpen(search)
        compose.runOnIdle { search.onQueryChange("zork"); search.showResults("zork", listOf(sampleHit())) }
        compose.runOnIdle { search.onAction(Action.Move(Direction.Down)) }
        ime.settle()
        assertFalse(search.inField)
        assertFalse("the keyboard goes with the cursor", ime.keyboardShows)
        compose.runOnIdle { search.onAction(Action.Move(Direction.Up)) }
        ime.settle()
        assertTrue(search.inField)
        assertFalse("returning to the field opens nothing", ime.keyboardShows)
        compose.runOnIdle { search.onAction(Action.Enter) }
        ime.settle()
        assertTrue("A opens it", ime.keyboardShows)
        compose.runOnIdle { search.onAction(Action.Move(Direction.Down)) }
        compose.runOnIdle { search.onAction(Action.Move(Direction.Up)) }
        ime.settle()
        assertFalse(ime.keyboardShows)
        compose.onNodeWithTag(SearchTags.FIELD, useUnmergedTree = true).performTouchInput { click() }
        ime.settle()
        assertTrue("a tap opens it", ime.keyboardShows)
    }

    @Test
    fun theKeyboardsSearchKeyGoesToTheResultsOnce() {
        val search = SearchState(KeyboardChoice.Android)
        showOpen(search)
        compose.runOnIdle { search.onQueryChange("zork"); search.showResults("zork", listOf(sampleHit())) }
        ime.settle()
        ime.taggedField(SearchTags.FIELD).performImeAction()
        ime.settle()
        assertFalse("the cursor went to the result", search.inField)
        assertFalse(ime.keyboardShows)
    }

    @Test
    fun enteringSearchOpensTheKeyboardNamedAndNotFullScreen() {
        val search = SearchState(KeyboardChoice.Android)
        compose.setContent {
            ime.view = LocalView.current
            PlumTheme { SearchTopBar(search, clock = { null }, battery = { null }) }
        }
        compose.runOnIdle { search.open() }
        ime.settle()
        assertTrue("entering Search opens the keyboard", ime.keyboardShows)
        ime.taggedField(SearchTags.FIELD).assertIsFocused()
        ime.assertInlineKeyboardNamed(ime.context.getString(R.string.home_search_hint))
        assertEquals(EditorInfo.IME_ACTION_SEARCH, ime.editorInfo().imeOptions and EditorInfo.IME_MASK_ACTION)
    }
}
