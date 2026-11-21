package com.plumemu.coreui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** The cut itself, with a made-up measure: every char is one unit wide, the mark too. */
class EllipsizeTest {
    private fun cut(text: String, width: Int) = ellipsize(text) { it.length <= width }

    @Test
    fun aSpaceBeforeTheCutIsTrimmed() {
        // "The Legend of Zelda " (20 chars) + "\u2026" would be the plain cut at width 21.
        assertEquals("The Legend of Zelda\u2026", cut("The Legend of Zelda: A Link to the Past", 21))
        assertEquals("The Legend of Zelda\u2026", cut("The Legend of Zelda A Link to the Past", 21))
    }

    @Test
    fun severalSpacesAreAllTrimmed() {
        assertEquals("Zelda\u2026", cut("Zelda     Link", 9))
    }

    @Test
    fun danglingPunctuationIsTrimmedToo() {
        assertEquals("Zelda\u2026", cut("Zelda - Link", 8))
        assertEquals("Zelda\u2026", cut("Zelda: Link", 7))
        assertEquals("Zelda\u2026", cut("Zelda, Link", 7))
        assertEquals("Mr. Do!\u2026", cut("Mr. Do! Link", 9))
    }

    @Test
    fun aCjkTitleIsCutBetweenCharacters() {
        val title = "\u30BC\u30EB\u30C0\u306E\u4F1D\u8AAC \u795E\u3005\u306E\u30C8\u30E9\u30A4\u30D5\u30A9\u30FC\u30B9"
        assertEquals("\u30BC\u30EB\u30C0\u306E\u4F1D\u8AAC\u2026", cut(title, 8))
        assertEquals("\u30BC\u30EB\u30C0\u2026", cut(title, 4))
    }

    @Test
    fun aTitleThatFitsIsUnchanged() {
        assertEquals("Super Metroid", cut("Super Metroid", 13))
        assertEquals("Super Metroid ", cut("Super Metroid ", 14))
    }

    @Test
    fun aWidthThatHoldsOnlyTheMarkGivesTheMark() {
        assertEquals("\u2026", cut("Super Metroid", 1))
        assertEquals("\u2026", cut("Super Metroid", 0))
    }

    @Test
    fun aSurrogatePairIsNeverSplit() {
        val title = "ab\uD83C\uDFAE\uD83C\uDFAEcd"
        assertEquals("ab\uD83C\uDFAE\u2026", cut(title, 5))
        assertEquals("ab\u2026", cut(title, 4))
    }

    @Test
    fun trimBeforeEllipsisKeepsTheLetters() {
        assertEquals("Zelda", "Zelda :- ".trimBeforeEllipsis())
        assertEquals("Mr.", "Mr.".trimBeforeEllipsis())
    }

    @Test
    fun wholeWordsEndsOnTheLastWholeWord() {
        // "The Legend of Zelda: Echoes of Wis…" fits 35; the cut goes back to "Echoes of".
        val text = "The Legend of Zelda: Echoes of Wisdom"
        assertEquals("The Legend of Zelda: Echoes of\u2026", ellipsize(text, wholeWords = true) { it.length <= 35 })
        assertEquals("The Legend of Zelda: Echoes of Wis\u2026", ellipsize(text) { it.length <= 35 })
    }

    @Test
    fun wholeWordsStillCutsOneLongWord() {
        assertEquals("Supercalifragi\u2026", ellipsize("Supercalifragilistic", wholeWords = true) { it.length <= 15 })
        // Going back to the word's start would give up more than the limit: cut inside.
        assertEquals("A Supercalifra\u2026", ellipsize("A Supercalifragilistic", wholeWords = true) { it.length <= 15 })
    }

    @Test
    fun wholeWordsLeavesATitleThatFits() {
        assertEquals("Mega Man X", ellipsize("Mega Man X", wholeWords = true) { it.length <= 15 })
    }
}
