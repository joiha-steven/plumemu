package com.plumemu.game

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.ImeProbe
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.keyboard.KeyboardChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix name… and Use my own name with the real text input, Settings › Controls ›
 * Keyboard on Android (Plumemu's keyboard: `PanelOwnKeyboardTest`, `PanelOwnKeyboardPathTest`): the Android keyboard
 * comes up only on A or a tap on the field, never when the panel opens; the system's Back may
 * close it and A brings it back; the keyboard's action key acts once; the keyboard is told the
 * field's name and not to go full screen. Android 9 (sdk 28), as the sign-in form's test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
class NameFixKeyboardTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val ime = ImeProbe(compose)
    private val ran = mutableListOf<PanelCommand>()
    private val finder = TitleFinder(listOf(TitleChoice("Super Metroid", "USA", 1994, null), TitleChoice("Super Mario Land", "World", 1989, null)))
    private val fix = FixNameState("super metroid", finder, KeyboardChoice.Android)
    private val own = OwnNameState("Super Metroid", KeyboardChoice.Android)

    private fun route(typing: TypingState): (Action) -> Unit =
        { action ->
            (typing.onAction(action) as? TypingOutcome.Run)?.let { ran += it.command }
        }

    private fun showFixName() {
        compose.setContent {
            ime.view = LocalView.current
            PlumTheme { Column { FixNameBody(fix, route(fix)) } }
        }
        fix.showResults(fix.query, finder.find(fix.query))
        ime.settle()
    }

    private fun showOwnName() {
        compose.setContent {
            ime.view = LocalView.current
            PlumTheme { Column { OwnNameBody(own, route(own)) } }
        }
        ime.settle()
    }

    private fun press(
        typing: TypingState,
        action: Action,
    ) {
        compose.runOnIdle { route(typing)(action) }
        ime.settle()
    }

    @Test
    fun fixNameOpensWithoutTheKeyboard() {
        showFixName()
        assertTrue("the cursor is on the field", fix.inField)
        assertFalse("no keyboard until A or a tap", ime.keyboardShows)
        ime.textInput(FixNameTags.FIELD).assertIsNotFocused()
    }

    @Test
    fun aOpensTheKeyboardAndItIsNamedAndNotFullScreen() {
        showFixName()
        press(fix, Action.Enter)
        assertTrue(fix.inField)
        ime.textInput(FixNameTags.FIELD).assertIsFocused()
        assertTrue("A opens the keyboard", ime.keyboardShows)
        ime.assertInlineKeyboardNamed(ime.context.getString(R.string.game_name_hint))
        assertEquals(EditorInfo.IME_ACTION_SEARCH, ime.editorInfo().imeOptions and EditorInfo.IME_MASK_ACTION)
    }

    @Test
    fun aTapOnTheFieldOpensTheKeyboard() {
        showFixName()
        compose.onNode(hasTestTag(FixNameTags.FIELD)).performTouchInput { click() }
        ime.settle()
        assertTrue(ime.keyboardShows)
    }

    @Test
    fun aTapOnTheFieldWhileTheCursorIsOnTheResultsBringsTheCursorAndTheKeyboardBack() {
        showFixName()
        press(fix, Action.Move(Direction.Down))
        assertFalse(fix.inField)
        assertFalse(ime.keyboardShows)
        compose.onNode(hasTestTag(FixNameTags.FIELD)).performTouchInput { click() }
        ime.settle()
        assertTrue("the cursor is on the field again", fix.inField)
        ime.textInput(FixNameTags.FIELD).assertIsFocused()
        assertTrue(ime.keyboardShows)
    }

    @Test
    fun aBringsTheKeyboardBackAfterTheSystemClosedIt() {
        showFixName()
        press(fix, Action.Enter)
        assertTrue(ime.keyboardShows)
        ime.closeKeyboardFromTheSystem()
        assertFalse(ime.keyboardShows)
        assertTrue("the cursor stays on the field row", fix.inField)
        press(fix, Action.Enter)
        assertTrue("A on the same row brings it back", ime.keyboardShows)
    }

    @Test
    fun downPutsTheKeyboardAwayAndComingBackUpOpensNothing() {
        showFixName()
        press(fix, Action.Enter)
        press(fix, Action.Move(Direction.Down))
        assertFalse(fix.inField)
        assertFalse("the keyboard goes with the cursor", ime.keyboardShows)
        press(fix, Action.Move(Direction.Up))
        assertTrue(fix.inField)
        assertFalse("coming back opens nothing", ime.keyboardShows)
        press(fix, Action.Enter)
        assertTrue(ime.keyboardShows)
    }

    @Test
    fun theSearchKeyGoesToTheResultsOnceAndPicksNothing() {
        showFixName()
        press(fix, Action.Enter)
        ime.textInput(FixNameTags.FIELD).performImeAction()
        ime.settle()
        assertFalse("the cursor is on the first result", fix.inField)
        assertEquals(0, fix.row)
        assertEquals("the search key does not pick", emptyList<PanelCommand>(), ran)
        assertFalse(ime.keyboardShows)
    }

    @Test
    fun ownNameOpensWithoutTheKeyboardAndAOpensItNamed() {
        showOwnName()
        assertFalse(ime.keyboardShows)
        ime.textInput(FixNameTags.OWN_FIELD).assertIsNotFocused()
        press(own, Action.Enter)
        assertTrue(ime.keyboardShows)
        assertEquals("A on the field does not save", emptyList<PanelCommand>(), ran)
        ime.assertInlineKeyboardNamed(ime.context.getString(R.string.game_name_hint))
        assertEquals(EditorInfo.IME_ACTION_DONE, ime.editorInfo().imeOptions and EditorInfo.IME_MASK_ACTION)
    }

    @Test
    fun ownNameDoneSavesExactlyOnce() {
        showOwnName()
        press(own, Action.Enter)
        ime.textInput(FixNameTags.OWN_FIELD).performImeAction()
        ime.settle()
        assertEquals(listOf<PanelCommand>(PanelCommand.SaveOwnName("Super Metroid")), ran)
        assertFalse("the keyboard goes while the name is saved", ime.keyboardShows)
    }

    @Test
    fun ownNameDoneOnABlankNameRefusesAndKeepsTheKeyboardDown() {
        showOwnName()
        compose.runOnIdle { own.onTextChange("   ") }
        press(own, Action.Enter)
        ime.textInput(FixNameTags.OWN_FIELD).performImeAction()
        ime.settle()
        assertTrue(own.isRefused)
        assertEquals(emptyList<PanelCommand>(), ran)
        assertFalse(ime.keyboardShows)
    }
}
