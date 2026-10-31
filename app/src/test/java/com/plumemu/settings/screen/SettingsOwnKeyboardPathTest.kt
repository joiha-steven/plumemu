package com.plumemu.settings.screen

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.hasSetTextAction
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import com.plumemu.game.FixNameTags
import com.plumemu.game.GamePanelTags
import com.plumemu.game.resultKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings' fields with Plumemu's keyboard, what must never happen: Android's
 * keyboard showing, a text field anyone could connect to, a state written while typing is idle
 * (no blinking caret, no animation), or B still erasing after Settings left (B out of it,
 * the Home button). Android 9 (sdk 28), as the other keyboard tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SettingsOwnKeyboardPathTest : SettingsKeyboardPathHarness() {
    private fun assertNoAndroidKeyboard(where: String) {
        ime.settle()
        assertFalse("no Android keyboard in $where", ime.keyboardShows)
        assertTrue("no text field to connect to in $where", compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertNull("no input connection in $where", compose.runOnIdle { ime.view.onCreateInputConnection(EditorInfo()) })
    }

    private fun assertNothingWritesWhileIdle(where: String) {
        compose.waitForIdle()
        val writes = mutableListOf<Set<Any>>()
        val observer = Snapshot.registerApplyObserver { changed, _ -> writes += changed }
        try {
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(IDLE_MS)
            compose.mainClock.advanceTimeByFrame()
            assertTrue("no state changes while $where is typing and idle (a caret blink or animation would): $writes", writes.isEmpty())
        } finally {
            observer.dispose()
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun theHexFieldHasNoAndroidKeyboardAndNothingRunsWhileIdle() {
        show()
        openAccentHexRow()
        press(confirm)
        assertTrue(isTrayUp)
        typeOnKeyboard("1a2")
        assertNoAndroidKeyboard("the hex field")
        assertNothingWritesWhileIdle("the hex field")
    }

    @Test
    fun fixNameAndOwnNameHaveNoAndroidKeyboardAndNothingRunsWhileIdle() {
        show()
        openNameFix(0)
        press(confirm)
        assertTrue(isTrayUp)
        press(KeyEvent.KEYCODE_BUTTON_R2)
        repeat(textOf(FixNameTags.typedText(FixNameTags.FIELD)).length) { press(back) }
        typeOnKeyboard("zork")
        compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.row(resultKey(0))) }
        assertNoAndroidKeyboard("Fix name…")
        assertNothingWritesWhileIdle("Fix name…")
        // Out of Fix name… (Back, then B with the tray down), into Use my own name.
        press(KeyEvent.KEYCODE_BACK, back)
        send(Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter)
        assertTrue(exists(FixNameTags.OWN_FIELD))
        press(confirm)
        assertTrue(isTrayUp)
        assertNoAndroidKeyboard("Use my own name")
        assertNothingWritesWhileIdle("Use my own name")
    }

    @Test
    fun settingsLeavingWhileTypingGivesBBack() {
        show()
        openAccentHexRow()
        press(confirm)
        assertEquals(TextEntry.Keyboard, textEntry())
        // The Home button (or anything else) takes Settings out of the composition while the tray is up.
        shown = false
        compose.waitForIdle()
        assertEquals("no other screen gets an erase or a typed key", TextEntry.None, textEntry())
    }

    @Test
    fun bOutOfSettingsStepByStepEndsWithNoTypingKeys() {
        show()
        openNameFix(0)
        press(confirm)
        assertEquals(TextEntry.Keyboard, textEntry())
        // Tray down, out of Fix name…, out of the list, the options: the router is told at each step.
        press(KeyEvent.KEYCODE_BACK)
        assertEquals(TextEntry.Field, textEntry())
        press(back)
        assertEquals(TextEntry.None, textEntry())
        press(back, back, back)
        assertEquals(TextEntry.None, textEntry())
        assertTrue("B closed Settings", actions.closed > 0)
    }

    @Test
    fun noOtherPageGetsTypingKeys() {
        show()
        assertEquals(TextEntry.None, textEntry())
        openAccentHexRow()
        assertEquals("the hex row before A", TextEntry.None, textEntry())
        press(confirm, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("on Use this colour", TextEntry.None, textEntry())
        press(back)
        assertEquals("the palette after editing", TextEntry.None, textEntry())
    }
}
