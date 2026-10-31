package com.plumemu.game

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column as StripColumn
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BoxArtPanelFixtures
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
 * Fix box art… with the real text input and touch, Settings › Controls ›
 * Keyboard on Android (Plumemu's keyboard: `PanelOwnKeyboardTest`): the Android keyboard
 * comes up only on A or a tap on the field, never when the panel opens, and is named and not full
 * screen; the search key goes to Search once and searches; a tap on each kind of row sends the
 * tap the state expects and runs what A would. Android 9 (sdk 28), as the Fix name test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
class FixBoxArtPanelTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val ime = ImeProbe(compose)
    private val ran = mutableListOf<PanelCommand>()
    private val sent = mutableListOf<Action>()
    private val hit = BoxArtPanelFixtures.portrait
    private val state = BoxArtPanelFixtures.results(listOf(hit), keyboard = KeyboardChoice.Android)

    private fun route(action: Action) {
        sent += action
        (state.onAction(action) as? TypingOutcome.Run)?.let { ran += it.command }
    }

    private fun show() {
        compose.setContent {
            ime.view = LocalView.current
            PlumTheme { Column { FixBoxArtBody(state, ::route) } }
        }
        ime.settle()
    }

    private fun press(action: Action) {
        compose.runOnIdle { route(action) }
        ime.settle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click() }
        ime.settle()
    }

    @Test
    fun openingThePanelShowsNoKeyboard() {
        show()
        assertTrue("the cursor is on the field", state.inField)
        assertFalse("no keyboard until A or a tap", ime.keyboardShows)
        ime.textInput(FixBoxArtTags.FIELD).assertIsNotFocused()
    }

    @Test
    fun aOnTheFieldOpensTheKeyboardNamedAndNotFullScreen() {
        show()
        press(Action.Enter)
        ime.textInput(FixBoxArtTags.FIELD).assertIsFocused()
        assertTrue("A opens the keyboard", ime.keyboardShows)
        ime.assertInlineKeyboardNamed(ime.context.getString(R.string.game_name_hint))
        assertEquals(EditorInfo.IME_ACTION_SEARCH, ime.editorInfo().imeOptions and EditorInfo.IME_MASK_ACTION)
        assertEquals("A on the field searches nothing", emptyList<PanelCommand>(), ran)
    }

    @Test
    fun aTapOnTheFieldOpensTheKeyboard() {
        show()
        compose.onNode(hasTestTag(FixBoxArtTags.FIELD)).performTouchInput { click() }
        ime.settle()
        assertTrue(ime.keyboardShows)
    }

    @Test
    fun downPutsTheKeyboardAwayAndTheSearchKeyAsksOnce() {
        show()
        press(Action.Enter)
        ime.textInput(FixBoxArtTags.FIELD).performImeAction()
        ime.settle()
        assertFalse("the cursor left the field", state.inField)
        assertFalse(ime.keyboardShows)
        assertEquals("the search key searched once, as A on Search", listOf<PanelCommand>(PanelCommand.SearchBoxArt("super metroid")), ran)
    }

    @Test
    fun aTapOnSearchSearches() {
        show()
        tap(GamePanelTags.row(BOX_ART_SEARCH_KEY))
        assertEquals(listOf<Action>(Action.TapItem(StripColumn.Details, 0)), sent)
        assertEquals(listOf<PanelCommand>(PanelCommand.SearchBoxArt("super metroid")), ran)
    }

    @Test
    fun aTapOnAGamePicksIt() {
        show()
        tap(GamePanelTags.row(boxArtHitKey(hit)))
        assertEquals(listOf<Action>(Action.TapItem(StripColumn.Details, 1)), sent)
        assertEquals(listOf<PanelCommand>(PanelCommand.PickBoxArt(hit)), ran)
        assertEquals("the cursor follows the tap", 1, state.cursor)
    }

    @Test
    fun aTapOnNoBoxArtStoresIt() {
        show()
        tap(GamePanelTags.row(BOX_ART_NONE_KEY))
        assertEquals(listOf<Action>(Action.TapItem(StripColumn.Details, 2)), sent)
        assertEquals(listOf<PanelCommand>(PanelCommand.StoreNoBoxArt), ran)
    }

    @Test
    fun aTapOnAGameWhileStoringStartsNothingMore() {
        show()
        compose.runOnIdle { state.storing() }
        tap(GamePanelTags.row(boxArtHitKey(hit)))
        assertEquals(emptyList<PanelCommand>(), ran)
    }

    @Test
    fun theStatusLineFollowsThePhase() {
        val idle = FixBoxArtState("zork", "PlayStation", KeyboardChoice.Android)
        compose.setContent { PlumTheme { Column { FixBoxArtBody(idle, ::route) } } }
        compose.onNodeWithTag(FixBoxArtTags.LINE, useUnmergedTree = true).assertExists()
        compose.runOnIdle { idle.onAction(Action.Move(Direction.Down)); idle.searching("zork") }
        compose.waitForIdle()
        compose.onNodeWithTag(FixBoxArtTags.LINE, useUnmergedTree = true).assertExists()
        compose.runOnIdle { idle.showSearch("zork", BoxArtSearchOutcome.Found(listOf(hit))) }
        compose.waitForIdle()
        compose.onNodeWithTag(FixBoxArtTags.LINE, useUnmergedTree = true).assertDoesNotExist()
    }
}
