package com.plumemu.settings.screen

import android.view.KeyEvent
import com.plumemu.R
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.components.keyboard.KeyId
import com.plumemu.coreui.components.keyboard.keyboardKeyTag
import com.plumemu.home.HomeTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The custom colour's hex field with Plumemu's keyboard on the real Settings
 * screen: A on the hex row brings the tray up on the digits page with "Done" on its action key; the
 * game panel's button map (A, B, X, Y, START, L1 R1, L2 R2, Back, Escape, the hide key), a held B,
 * taps on keys, the field and the hints, a hardware keyboard; the action key uses the code as "Use
 * this colour" does, and a wrong or empty code gets the same line with nothing applied. Android 9
 * (sdk 28), as the other keyboard tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SettingsHexOwnKeyboardTest : SettingsKeyboardPathHarness() {
    private fun text() = textOf(SettingsTags.HEX_TEXT)

    private val isEditing get() = exists(SettingsTags.HEX_FIELD)

    private val accent get() = actions.settings.appearance.accent

    /** Typed accents are dark codes (#1A2B3C …): they read on Paper (accents under 3:1 are refused). */
    private fun useDarkOnPaper() {
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = ColorSetting.Preset("Paper"), textColor = TextColorSetting.Dark))
    }

    private fun showHexRow(swapAB: Boolean = false) {
        useDarkOnPaper()
        show(swapAB)
        openAccentHexRow()
        assertFalse("the hex row opens without a field or a tray", isEditing || isTrayUp)
        assertEquals(TextEntry.None, textEntry())
    }

    @Test
    fun everyButtonWithAConfirming() = everyButton(swapAB = false)

    @Test
    fun everyButtonWithBConfirming() = everyButton(swapAB = true)

    private fun everyButton(swapAB: Boolean) {
        showHexRow(swapAB)
        val before = accent
        press(confirm)
        assertTrue("A opens the tray", isTrayUp)
        assertTrue(isEditing)
        assertEquals(TextEntry.Keyboard, textEntry())
        assertEquals("the field starts empty, the current code in its place", "", text())
        assertTrue(textOf(SettingsTags.HEX_PLACEHOLDER).startsWith("#"))
        // The typing hints, "Done" for the action; the tray opens on the digits ("Y ABC"), no shift.
        assertTrue(hintText(Action.Enter).endsWith("Type"))
        assertTrue(hintText(Action.Erase()).endsWith("Delete"))
        assertTrue(hintText(Action.Search).endsWith("Space"))
        assertTrue(hintText(Action.Menu).endsWith("Done"))
        assertTrue(hintText(Action.ToggleFavorite).endsWith("ABC"))
        assertTrue(hintText(Action.NextSystem).endsWith("Cursor"))
        assertFalse("no shift on the hex keys", exists(HomeTags.hint(Action.ToggleView)))
        // A types the focused key (1); the D-pad moves on the keys; B deletes; X types a space.
        press(confirm, KeyEvent.KEYCODE_DPAD_RIGHT, confirm)
        assertEquals("12", text())
        press(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP)
        assertTrue("Up from the top row stays on the keys", isTrayUp)
        press(back)
        assertEquals("1", text())
        press(KeyEvent.KEYCODE_BUTTON_X)
        assertEquals("1 ", text())
        press(back)
        // Y: the letters, and back to the digits; SELECT does nothing without shift.
        press(KeyEvent.KEYCODE_BUTTON_Y)
        assertTrue(hintText(Action.ToggleFavorite).endsWith("123"))
        press(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_SELECT)
        assertEquals("1", text())
        // L2 / R2 to the ends, L1 / R1 by one: A types where the caret is (2 focused).
        press(KeyEvent.KEYCODE_BUTTON_L2, confirm)
        assertEquals("21", text())
        press(KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_L1, confirm)
        assertEquals("221", text())
        press(KeyEvent.KEYCODE_BUTTON_R1, confirm)
        assertEquals("2212", text())
        // The system's Back and Escape put the tray away; the field stays, typed text and all; A brings it back.
        press(KeyEvent.KEYCODE_BACK)
        assertFalse(isTrayUp)
        assertTrue(isEditing)
        assertEquals("2212", text())
        assertEquals("a hardware keyboard may type into the field", TextEntry.Field, textEntry())
        press(confirm)
        assertTrue(isTrayUp)
        press(KeyEvent.KEYCODE_ESCAPE)
        assertFalse(isTrayUp)
        press(confirm)
        // START: the code is used once, the tray goes down, the router sends no typing keys.
        press(*IntArray(2) { KeyEvent.KEYCODE_BUTTON_R1 }, confirm, confirm)
        assertEquals("221222", text())
        assertEquals("nothing written while typing", before, accent)
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertEquals(ColorSetting.Custom(0x221222), accent)
        assertFalse("the action puts the tray away", isTrayUp)
        assertFalse("and ends editing", isEditing)
        assertEquals(TextEntry.None, textEntry())
        assertFalse("Android's keyboard never shows", ime.keyboardShows)
    }

    @Test
    fun aWrongCodeWithTheActionKeySaysWhatUseThisColourSaysAndAppliesNothing() {
        showHexRow()
        val before = accent
        press(confirm)
        typeOnKeyboard("zz")
        press(KeyEvent.KEYCODE_BUTTON_START)
        val line = ime.context.getString(R.string.settings_hex_error)
        assertEquals(line, textOf(SettingsTags.HEX_ERROR))
        assertTrue("the tray stays to fix the code", isTrayUp)
        assertEquals("zz", text())
        assertEquals(before, accent)
        // "Use this colour" with the same code: the same line, nothing applied.
        press(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_DOWN, confirm)
        assertEquals(line, textOf(SettingsTags.HEX_ERROR))
        assertEquals(before, accent)
    }

    @Test
    fun anEmptyCodeWithTheActionKeyIsRefusedToo() {
        showHexRow()
        val before = accent
        press(confirm)
        tap(keyboardKeyTag(KeyId(3, 3)))
        assertEquals(ime.context.getString(R.string.settings_hex_error), textOf(SettingsTags.HEX_ERROR))
        assertTrue(isTrayUp)
        assertEquals(before, accent)
    }

    @Test
    fun aHeldBErasesToEmptyThenAFreshBHidesTheTrayAndTheNextOneStopsEditing() {
        showHexRow()
        press(confirm)
        typeOnKeyboard("1a2b3c")
        assertEquals("1a2b3c", text())
        holdB()
        assertEquals("held, B erases everything", "", text())
        assertTrue("held, B never hides the tray", isTrayUp)
        press(back)
        assertFalse("a fresh B on the empty field hides the tray", isTrayUp)
        assertTrue(isEditing)
        press(back)
        assertFalse("B with the tray down stops editing", isEditing)
        assertTrue("the palette stays", exists(SettingsTags.HEX_ROW))
        assertEquals(TextEntry.None, textEntry())
    }

    @Test
    fun aHardwareKeyboardTypesWithTheTrayDownAndUpAndEnterUsesTheCodeOnce() {
        showHexRow()
        press(confirm, KeyEvent.KEYCODE_BACK)
        assertFalse(isTrayUp)
        typeOnKeyboard("1a2b")
        assertEquals("1a2b", text())
        assertFalse("typing on a hardware keyboard does not bring the tray up", isTrayUp)
        hardware(KeyEvent.KEYCODE_DEL)
        assertEquals("1a2", text())
        press(confirm)
        typeOnKeyboard("b3c")
        // Seven characters at most ("#" and six digits), as with Android's keyboard: an eighth is not kept.
        typeOnKeyboard("de")
        assertEquals("1a2b3cd", text())
        hardware(KeyEvent.KEYCODE_DEL)
        assertEquals("1a2b3c", text())
        hardware(KeyEvent.KEYCODE_ENTER)
        assertEquals(ColorSetting.Custom(0x1A2B3C), accent)
        assertFalse(isEditing)
    }

    @Test
    fun touchKeysTheHideKeyTheFieldEveryHintAndTheActionKey() {
        showHexRow()
        tap(SettingsTags.HEX_ROW)
        assertTrue("a tap on the hex row starts typing with the tray up", isTrayUp)
        tap(keyboardKeyTag(KeyId(0, 0)))
        assertEquals("a tap on a key types it", "1", text())
        tap(HomeTags.hint(Action.Erase()))
        assertEquals("B Delete deletes", "", text())
        tap(HomeTags.hint(Action.Search))
        assertEquals("X Space types a space", " ", text())
        tap(HomeTags.hint(Action.Erase()))
        tap(HomeTags.hint(Action.ToggleFavorite))
        assertTrue(hintText(Action.ToggleFavorite).endsWith("123"))
        tap(HomeTags.hint(Action.ToggleFavorite))
        // The hide key: the tray goes down, the field stays; a tap on the field brings it back.
        tap(keyboardKeyTag(KeyId(3, 1)))
        assertFalse(isTrayUp)
        assertTrue(isEditing)
        tap(SettingsTags.HEX_FIELD)
        assertTrue(isTrayUp)
        repeat(6) { tap(keyboardKeyTag(KeyId(0, 0))) }
        tap(HomeTags.hint(Action.NextSystem))
        assertEquals("111111", text())
        tap(HomeTags.hint(Action.Menu))
        assertEquals("START Done by touch uses the code", ColorSetting.Custom(0x111111), accent)
        assertFalse(isEditing)
        assertFalse(ime.keyboardShows)
    }

    @Test
    fun theTraysActionKeyUsesTheCodeOnceAndTheTraySlidesAway() {
        showHexRow()
        press(confirm)
        typeOnKeyboard("0a0b0c")
        val writes = actions.writes
        tap(keyboardKeyTag(KeyId(3, 3)))
        assertEquals(ColorSetting.Custom(0x0A0B0C), accent)
        assertEquals("one write", writes + 1, actions.writes)
        assertFalse(isTrayUp)
        assertEquals(TextEntry.None, textEntry())
    }

    @Test
    fun withTheSettingOnAndroidTheHexFieldUsesAndroidsKeyboardAndNoTray() {
        useDarkOnPaper()
        show(androidKeyboard = true)
        openAccentHexRow()
        press(confirm)
        ime.settle()
        assertTrue("A opens Android's keyboard", ime.keyboardShows)
        assertFalse("no tray", isTrayUp)
        assertEquals("the router sends no typing keys", TextEntry.None, textEntry())
    }
}
