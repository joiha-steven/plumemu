package com.plumemu.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatchTest {
    @Test fun `case, accents and punctuation do not matter`() {
        assertEquals("pokemon puzzle league", TitleMatch.normalise("Pok\u00e9mon: Puzzle  League!"))
        assertEquals("street fighter ii turbo", TitleMatch.normalise("Street Fighter II' - Turbo"))
        assertEquals("pokemon rouge feu", TitleMatch.normalise("POK\u00c9MON Rouge-Feu"))
    }

    @Test fun `bracketed and parenthesised parts are dropped`() {
        assertEquals("super mario bros", TitleMatch.normalise("Super Mario Bros. (World) [!] (Rev 1)"))
    }

    @Test fun `a trailing comma The and a leading The are the same`() {
        assertEquals(TitleMatch.normalise("The Legend of Zelda"), TitleMatch.normalise("Legend of Zelda, The"))
        assertEquals(TitleMatch.normalise("The Legend of Zelda"), TitleMatch.normalise("Legend of Zelda, The (USA)"))
        // A "the" inside the title stays.
        assertEquals("sonic the hedgehog", TitleMatch.normalise("Sonic The Hedgehog"))
    }

    @Test fun `a sequel is not the game`() {
        assertFalse(TitleMatch.matches("Sonic the Hedgehog", listOf("Sonic The Hedgehog 2")))
        assertTrue(TitleMatch.matches("Sonic the Hedgehog", listOf("Sonic The Hedgehog 2", "Sonic The Hedgehog")))
    }

    @Test fun `a title that cleans to nothing matches nothing`() {
        assertFalse(TitleMatch.matches("(Demo)", listOf("[Demo]", "")))
        assertFalse(TitleMatch.matches("Sonic", listOf(null)))
    }

    @Test fun `exactName compares case and accents only`() {
        assertTrue(TitleMatch.exactName("LIMBO", listOf("Limbo")))
        assertTrue(TitleMatch.exactName("Pokemon Gold", listOf("x", "Pok\u00E9mon gold".replace("\u00E9", "e\u0301"))))
        assertTrue(TitleMatch.exactName("Pok\u00E9mon Gold", listOf("pokemon gold")))
        assertFalse(TitleMatch.exactName("Limbo", listOf("Limbo (Special Edition)")))
        assertFalse(TitleMatch.exactName("Metroid Prime - Hunters", listOf("Metroid Prime : Hunters")))
        assertFalse(TitleMatch.exactName("The Zelda", listOf("Zelda, The")))
        assertFalse(TitleMatch.exactName("Sonic", listOf(null, "")))
    }

    @Test fun `looksLikeOtherRelease sees a year, remake, remastered and reboot but not an edition`() {
        assertTrue(TitleMatch.looksLikeOtherRelease(listOf("Prey (2017)")))
        assertTrue(TitleMatch.looksLikeOtherRelease(listOf("x", "Sonic the Hedgehog (Remake)")))
        assertTrue(TitleMatch.looksLikeOtherRelease(listOf("Game - Remastered")))
        assertTrue(TitleMatch.looksLikeOtherRelease(listOf("Game [Reboot]")))
        assertFalse(TitleMatch.looksLikeOtherRelease(listOf("Limbo (Special Edition)", null)))
        assertFalse(TitleMatch.looksLikeOtherRelease(listOf("Metroid Prime - Hunters - First Hunt", "Game 2017")))
    }
}
