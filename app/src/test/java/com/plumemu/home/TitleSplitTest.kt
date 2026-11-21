package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleSplitTest {
    @Test
    fun aDashSplitsMainFromSubtitle() {
        assertEquals(SplitTitle("Zone of the Enders", "The Fist of Mars"), splitTitle("Zone of the Enders - The Fist of Mars"))
    }

    @Test
    fun aColonSplitsAtTheFirstMark() {
        assertEquals(SplitTitle("Star Wars", "Episode I: Racer"), splitTitle("Star Wars: Episode I: Racer"))
        assertEquals(SplitTitle("The Legend of Zelda", "Echoes of Wisdom"), splitTitle("The Legend of Zelda: Echoes of Wisdom"))
    }

    @Test
    fun enAndEmDashesSplitToo() {
        assertEquals(SplitTitle("Castlevania", "Symphony of the Night"), splitTitle("Castlevania – Symphony of the Night"))
        assertEquals(SplitTitle("Metroid", "Zero Mission"), splitTitle("Metroid — Zero Mission"))
    }

    @Test
    fun aTitleWithoutAMarkStaysWhole() {
        assertEquals(SplitTitle("Mega Man X", ""), splitTitle("Mega Man X"))
        // A hyphen inside a word is no mark.
        assertEquals(SplitTitle("Spider-Man", ""), splitTitle("Spider-Man"))
    }

    @Test
    fun aMarkWithAnEmptyOrOneLetterSideDoesNotSplit() {
        assertEquals(SplitTitle("X: Y", ""), splitTitle("X: Y"))
        assertEquals(SplitTitle("Tetris - ", ""), splitTitle("Tetris - "))
    }

    @Test
    fun aPosterBreaksADashSubtitleOntoItsOwnLine() {
        assertEquals("Zone of the Enders\nThe Fist of Mars", BoxFrontLayout.posterTitleText("Zone of the Enders - The Fist of Mars"))
        assertEquals("The Legend of Zelda: Echoes of Wisdom", BoxFrontLayout.posterTitleText("The Legend of Zelda: Echoes of Wisdom"))
        assertEquals("Mega Man X", BoxFrontLayout.posterTitleText("Mega Man X"))
    }
}
