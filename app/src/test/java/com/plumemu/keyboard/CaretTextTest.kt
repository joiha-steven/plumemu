package com.plumemu.keyboard

import com.plumemu.coreinput.TypedKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The edits Search and the game panel's fields share: at the caret, by code point, stopping at the ends. */
class CaretTextTest {
    @Test
    fun insertsAndDeletesAtTheCaret() {
        val text = CaretText("Zork", 2)
        assertEquals(CaretText("ZoXrk", 3), text.insert("X"))
        assertEquals(CaretText("Zrk", 1), text.deleteBefore())
        assertEquals(CaretText("Zok", 2), text.deleteAfter())
    }

    @Test
    fun theEndsStopEveryMoveAndDelete() {
        assertEquals(CaretText("ab", 0), CaretText("ab", 0).deleteBefore())
        assertEquals(CaretText("ab", 2), CaretText("ab", 2).deleteAfter())
        assertEquals(0, CaretText("ab", 0).moveBy(-1).caret)
        assertEquals(2, CaretText("ab", 2).moveBy(1).caret)
        assertEquals(0, CaretText("ab", 1).toStart().caret)
        assertEquals(2, CaretText("ab", 1).toEnd().caret)
        assertEquals("a caret past the end is the end", CaretText("abX", 3), CaretText("ab", 9).insert("X"))
    }

    @Test
    fun anEmojiIsOneCharacter() {
        val face = String(Character.toChars(EMOJI))
        val text = CaretText("a$face", 3)
        assertEquals(CaretText("a", 1), text.deleteBefore())
        assertEquals(1, text.moveBy(-1).caret)
        assertEquals(CaretText("a", 1), CaretText("a$face", 1).deleteAfter())
    }

    @Test
    fun hardwareKeysEditAndEnterIsTheAction() {
        val text = CaretText("ab", 1)
        assertEquals(CaretText("aXb", 2), text.typed(TypedKey.Text("X")))
        assertEquals(CaretText("b", 0), text.typed(TypedKey.Backspace))
        assertEquals(CaretText("a", 1), text.typed(TypedKey.ForwardDelete))
        assertEquals(0, text.typed(TypedKey.CaretLeft)?.caret)
        assertEquals(2, text.typed(TypedKey.CaretRight)?.caret)
        assertNull(text.typed(TypedKey.Enter))
    }

    private companion object {
        /** A character outside the BMP (two UTF-16 units). */
        const val EMOJI = 0x1F600
    }
}
