package com.plumemu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a paste inserts: one line, only what fits, never half of a character. */
class PastableTest {
    @Test
    fun aLineEndCopiedWithAKeyGoes() {
        assertEquals("AbCdEf0123456789", pastable("AbCdEf0123456789\n", room = 100))
        assertEquals("AbCd", pastable("\r\nAbCd\r\n", room = 100))
    }

    @Test
    fun linesInsideBecomeOneSpaceEach() {
        assertEquals("Super Mario World", pastable("Super\nMario\r\n\r\nWorld", room = 100))
        assertEquals("a b", pastable("a b", room = 100))
    }

    @Test
    fun onlyWhatFitsIsKept() {
        assertEquals("abc", pastable("abcdef", room = 3))
        assertEquals("", pastable("abcdef", room = 0))
        assertEquals("", pastable("abcdef", room = -2))
        assertEquals("abcdef", pastable("abcdef", room = 6))
    }

    @Test
    fun anEmojiIsNeverCutInHalf() {
        val smile = "\uD83D\uDE00"
        assertEquals("a", pastable("a$smile", room = 2))
        assertEquals("a$smile", pastable("a$smile", room = 3))
    }
}
