package com.plumemu.coreinput

import android.view.KeyEvent
import com.plumemu.coreinput.strip.Reduction
import com.plumemu.coreinput.strip.StripContent
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreinput.strip.StripIntent
import com.plumemu.coreinput.strip.StripReducer
import com.plumemu.coreinput.strip.SystemEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/** The gamepad path of every action: a key code, through the mapping, into the main screen's focus. */
class GamepadPathTest {
    private val content =
        StripContent(listOf(SystemEntry("favorites", 3), SystemEntry("snes", 248), SystemEntry("gba", 5)), hasBoxArt = true)
    private val settings = InputSettings()

    private fun Reduction.press(key: Int): Reduction {
        val control = GamepadMapping.controlFor(key) ?: error("key $key not taken")
        val action = GamepadMapping.actionFor(control, settings) ?: error("key $key has no action")
        return StripReducer.reduce(focus, action, content, isNarrow = false)
    }

    private val start = Reduction(StripFocus())
    private val snesGames = start.press(KeyEvent.KEYCODE_DPAD_DOWN).press(KeyEvent.KEYCODE_BUTTON_A)

    @Test
    fun moveWithTheDpad() {
        assertEquals("snes", start.press(KeyEvent.KEYCODE_DPAD_DOWN).focus.systemId)
        assertEquals(Column.Games, start.press(KeyEvent.KEYCODE_DPAD_RIGHT).focus.column)
        assertEquals(1, snesGames.press(KeyEvent.KEYCODE_DPAD_DOWN).focus.row)
        assertEquals(Column.Systems, snesGames.press(KeyEvent.KEYCODE_DPAD_LEFT).focus.column)
        assertEquals(247, snesGames.press(KeyEvent.KEYCODE_DPAD_UP).focus.row)
    }

    @Test
    fun enterWithAAndBackWithB() {
        assertEquals(Column.Games to "snes", snesGames.focus.column to snesGames.focus.systemId)
        assertEquals(Column.Games, start.press(KeyEvent.KEYCODE_DPAD_CENTER).focus.column)
        assertEquals(Column.Systems, snesGames.press(KeyEvent.KEYCODE_BUTTON_B).focus.column)
        assertEquals(Column.Systems, snesGames.press(KeyEvent.KEYCODE_BACK).focus.column)
    }

    @Test
    fun playWithAOnAGame() {
        assertEquals(listOf(StripIntent.Play("snes", 0)), snesGames.press(KeyEvent.KEYCODE_BUTTON_A).intents)
    }

    @Test
    fun changeSystemWithL1AndR1() {
        assertEquals("gba", snesGames.press(KeyEvent.KEYCODE_BUTTON_R1).focus.systemId)
        assertEquals("favorites", snesGames.press(KeyEvent.KEYCODE_BUTTON_L1).focus.systemId)
    }

    @Test
    fun jumpByLetterWithL2AndR2() {
        assertEquals(listOf(StripIntent.JumpLetter("snes", 0, forward = true)), snesGames.press(KeyEvent.KEYCODE_BUTTON_R2).intents)
        assertEquals(listOf(StripIntent.JumpLetter("snes", 0, forward = false)), snesGames.press(KeyEvent.KEYCODE_BUTTON_L2).intents)
    }

    @Test
    fun startIsTheGameMenuOnAGameAndSettingsOnTheSystemColumn() {
        assertEquals(listOf(StripIntent.OpenGameMenu("snes", 0)), snesGames.press(KeyEvent.KEYCODE_BUTTON_START).intents)
        assertEquals(listOf(StripIntent.OpenSettings), start.press(KeyEvent.KEYCODE_BUTTON_START).intents)
    }

    @Test
    fun selectTogglesListAndGrid() {
        assertEquals(listOf(StripIntent.ToggleView("snes")), snesGames.press(KeyEvent.KEYCODE_BUTTON_SELECT).intents)
    }

    @Test
    fun yTogglesTheFocusedGameAsAFavoriteAndDoesNothingOnTheSystemColumn() {
        // Xbox layout here (InputSettings' default): the north button is Y. GamepadMappingTest has every layout.
        assertEquals(listOf(StripIntent.ToggleFavorite("snes", 1)), snesGames.press(KeyEvent.KEYCODE_DPAD_DOWN).press(KeyEvent.KEYCODE_BUTTON_Y).intents)
        assertEquals(emptyList<StripIntent>(), start.press(KeyEvent.KEYCODE_BUTTON_Y).intents)
    }

    @Test
    fun xOpensSearchFromTheSystemAndGamesColumns() {
        assertEquals(listOf(StripIntent.OpenSearch), start.press(KeyEvent.KEYCODE_BUTTON_X).intents)
        assertEquals(listOf(StripIntent.OpenSearch), snesGames.press(KeyEvent.KEYCODE_BUTTON_X).intents)
    }
}
