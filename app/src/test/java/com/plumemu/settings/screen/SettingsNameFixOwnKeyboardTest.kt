package com.plumemu.settings.screen

import android.view.KeyEvent
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.components.keyboard.KeyId
import com.plumemu.coreui.components.keyboard.keyboardKeyTag
import com.plumemu.game.FixNameTags
import com.plumemu.game.GamePanelTags
import com.plumemu.game.resultKey
import com.plumemu.home.HomeTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Library › Unidentified games › Fix name… and Use my own name with Plumemu's keyboard
 * on the real Settings screen: they type as from the game menu (the same
 * fields, button map and action key once): A opens the tray, never the page by itself; A, B, X, Y,
 * SELECT, START, L1 R1, L2 R2, Back, a held B, taps on keys, the field and the hints, a hardware
 * keyboard; START in Fix name… goes to the results and picks nothing, in Use my own name it saves
 * once. Android 9 (sdk 28), as the other keyboard tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SettingsNameFixOwnKeyboardTest : SettingsKeyboardPathHarness() {
    private fun text(field: String) = textOf(FixNameTags.typedText(field))

    /** Fix name… for "zork quest 2" (index 0) or Use my own name for "dravon" (index 2, no list). */
    private fun open(
        field: String,
        swapAB: Boolean = false,
    ) {
        show(swapAB)
        openNameFix(if (field == FixNameTags.FIELD) 0 else 2)
        assertTrue(exists(field))
        assertFalse("the page opens with the tray down", isTrayUp)
        assertEquals("a hardware keyboard may type into the field", TextEntry.Field, textEntry())
    }

    @Test
    fun fixNameEveryButtonWithAConfirming() = everyButton(FixNameTags.FIELD, swapAB = false)

    @Test
    fun fixNameEveryButtonWithBConfirming() = everyButton(FixNameTags.FIELD, swapAB = true)

    @Test
    fun ownNameEveryButtonWithAConfirming() = everyButton(FixNameTags.OWN_FIELD, swapAB = false)

    @Test
    fun ownNameEveryButtonWithBConfirming() = everyButton(FixNameTags.OWN_FIELD, swapAB = true)

    private fun everyButton(
        field: String,
        swapAB: Boolean,
    ) {
        open(field, swapAB)
        val start = text(field)
        assertTrue("pre-filled with the shown name", start.isNotEmpty())
        press(confirm)
        assertTrue("A opens the tray", isTrayUp)
        assertEquals(TextEntry.Keyboard, textEntry())
        assertTrue(hintText(Action.Enter).endsWith("Type"))
        assertTrue(hintText(Action.Erase()).endsWith("Delete"))
        assertTrue(hintText(Action.Search).endsWith("Space"))
        assertTrue(hintText(Action.Menu).endsWith(if (field == FixNameTags.OWN_FIELD) "Save" else "Search"))
        assertTrue(hintText(Action.ToggleFavorite).endsWith("123"))
        assertTrue(hintText(Action.ToggleView).endsWith("Shift"))
        assertTrue(hintText(Action.NextSystem).endsWith("Cursor"))
        // A types the focused key (q) at the caret (the end); the D-pad moves on the keys; B deletes; X types a space.
        press(confirm, KeyEvent.KEYCODE_DPAD_RIGHT, confirm)
        assertEquals(start + "qw", text(field))
        press(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP)
        assertTrue("Up from the top row stays on the keys", isTrayUp)
        press(back)
        assertEquals(start + "q", text(field))
        press(KeyEvent.KEYCODE_BUTTON_X)
        assertEquals("${start}q ", text(field))
        // SELECT: one capital; Y: the digits page, back to letters.
        press(KeyEvent.KEYCODE_BUTTON_SELECT, confirm, confirm)
        assertEquals("${start}q Ww", text(field))
        press(KeyEvent.KEYCODE_BUTTON_Y)
        assertTrue(hintText(Action.ToggleFavorite).endsWith("ABC"))
        press(confirm, KeyEvent.KEYCODE_BUTTON_Y)
        assertEquals("${start}q Ww2", text(field))
        // L2 / R2 to the ends, L1 / R1 by one.
        press(KeyEvent.KEYCODE_BUTTON_L2, confirm)
        assertEquals("w${start}q Ww2", text(field))
        press(KeyEvent.KEYCODE_BUTTON_R1, confirm)
        assertEquals("w${start.first()}w${start.drop(1)}q Ww2", text(field))
        press(KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_L1, confirm)
        assertEquals("w${start.first()}w${start.drop(1)}q Www2", text(field))
        // The system's Back and Escape put the tray away; the page stays; A brings it back.
        press(KeyEvent.KEYCODE_BACK)
        assertFalse(isTrayUp)
        assertTrue(exists(field))
        assertEquals(TextEntry.Field, textEntry())
        press(confirm)
        assertTrue(isTrayUp)
        press(KeyEvent.KEYCODE_ESCAPE)
        assertFalse(isTrayUp)
        assertTrue(exists(field))
        press(confirm)
        if (field == FixNameTags.FIELD) {
            // Nothing matches: START has no result to go to, as Android's search key; the tray stays.
            press(KeyEvent.KEYCODE_BUTTON_R2)
            repeat(text(field).length) { press(back) }
            typeOnKeyboard("qqqq")
            compose.waitUntil(TIMEOUT_MS) { exists(FixNameTags.NO_MATCH) }
            press(KeyEvent.KEYCODE_BUTTON_START)
            assertTrue(isTrayUp)
            repeat(text(field).length) { press(back) }
            typeOnKeyboard("zork")
            compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.row(resultKey(0))) }
        }
        val typed = text(field)
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertFalse("the action puts the tray away", isTrayUp)
        if (field == FixNameTags.FIELD) {
            assertTrue("Fix name: picks nothing", names.chosen.isEmpty())
            assertTrue("the page stays, on the results", exists(FixNameTags.FIELD))
            assertEquals("no typing keys on the results", TextEntry.None, textEntry())
            press(confirm)
            assertEquals("A on the first result picks it", listOf(21L to titles[0]), names.chosen)
        } else {
            assertEquals("Use my own name: saved once", listOf(23L to typed.trim()), names.own)
        }
        assertFalse("back on the list", exists(field))
        assertEquals(TextEntry.None, textEntry())
        assertFalse("Android's keyboard never shows", ime.keyboardShows)
    }

    @Test
    fun aHeldBErasesToEmptyThenAFreshBHidesTheTrayAndTheNextOneLeaves() {
        open(FixNameTags.FIELD)
        press(confirm)
        holdB()
        assertEquals("held, B erases everything", "", text(FixNameTags.FIELD))
        assertTrue("held, B never hides the tray", isTrayUp)
        press(back)
        assertFalse("a fresh B on the empty field hides the tray", isTrayUp)
        assertTrue(exists(FixNameTags.FIELD))
        press(back)
        assertFalse("B with the tray down steps out of Fix name…", exists(FixNameTags.FIELD))
        assertTrue("to the list", exists(SettingsTags.panelItem(0)))
        assertEquals(TextEntry.None, textEntry())
        assertTrue("nothing stored", names.chosen.isEmpty() && names.own.isEmpty())
    }

    @Test
    fun aHardwareKeyboardTypesWithTheTrayDownAndUpAndEnterActsOnce() {
        open(FixNameTags.OWN_FIELD)
        val start = text(FixNameTags.OWN_FIELD)
        typeOnKeyboard("zx")
        assertEquals(start + "zx", text(FixNameTags.OWN_FIELD))
        assertFalse("typing on a hardware keyboard does not bring the tray up", isTrayUp)
        hardware(KeyEvent.KEYCODE_DEL)
        hardware(KeyEvent.KEYCODE_DEL)
        assertEquals(start, text(FixNameTags.OWN_FIELD))
        press(confirm)
        typeOnKeyboard("q")
        hardware(KeyEvent.KEYCODE_ENTER)
        assertEquals(listOf(23L to start + "q"), names.own)
    }

    @Test
    fun touchKeysTheHideKeyTheFieldEveryHintAndTheActionKey() {
        open(FixNameTags.OWN_FIELD)
        val start = text(FixNameTags.OWN_FIELD)
        tap(FixNameTags.OWN_FIELD)
        assertTrue("a tap on the field opens the tray", isTrayUp)
        tap(keyboardKeyTag(KeyId(0, 0)))
        assertEquals("a tap on a key types it at the caret", start.length + 1, text(FixNameTags.OWN_FIELD).length)
        tap(HomeTags.hint(Action.Erase()))
        assertEquals("B Delete deletes", start, text(FixNameTags.OWN_FIELD))
        tap(HomeTags.hint(Action.Search))
        assertEquals("X Space types a space", start.length + 1, text(FixNameTags.OWN_FIELD).length)
        tap(HomeTags.hint(Action.ToggleFavorite))
        assertTrue(hintText(Action.ToggleFavorite).endsWith("ABC"))
        tap(HomeTags.hint(Action.ToggleFavorite))
        tap(HomeTags.hint(Action.ToggleView))
        tap(keyboardKeyTag(KeyId(0, 0)))
        assertTrue("SELECT Shift: a capital", text(FixNameTags.OWN_FIELD).contains("Q"))
        tap(HomeTags.hint(Action.NextSystem))
        tap(HomeTags.hint(Action.Enter))
        // The hide key: the tray goes down, the cursor stays on the field.
        tap(keyboardKeyTag(KeyId(3, 1)))
        assertFalse(isTrayUp)
        assertTrue(exists(FixNameTags.OWN_FIELD))
        tap(FixNameTags.OWN_FIELD)
        assertTrue(isTrayUp)
        val typed = text(FixNameTags.OWN_FIELD)
        tap(keyboardKeyTag(KeyId(3, 3)))
        assertEquals("the tray's Save saves once", listOf(23L to typed.trim()), names.own)
        assertFalse(ime.keyboardShows)
    }

    @Test
    fun withTheSettingOnAndroidFixNameUsesAndroidsKeyboardAndNoTray() {
        show(androidKeyboard = true)
        openNameFix(0)
        ime.settle()
        assertFalse("the page opens with the keyboard down", ime.keyboardShows)
        press(confirm)
        ime.settle()
        assertTrue("A opens Android's keyboard", ime.keyboardShows)
        assertFalse("no tray", isTrayUp)
        assertEquals("the router sends no typing keys", TextEntry.None, textEntry())
    }
}
