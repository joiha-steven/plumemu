package com.plumemu.secondscreen

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How the "Last played <name>" sentence is split around the name; the details card's file name without its extension. */
class SecondScreenFactsTest {
    private val sep = " · "

    @Test
    fun theNameGoesWhereTheTranslationPutsIt() {
        val facts = lastGameFacts("Last played ${NAME_MARK}.", "Super Metroid", "yesterday", sep, Color.Gray)
        assertEquals("Last played ", facts.before.text)
        assertEquals("Super Metroid", facts.cuttable)
        assertEquals(".$sep" + "yesterday", facts.after.text)
    }

    @Test
    fun aTranslationThatLeavesTheNameOutGivesTheSentenceOnceAndCutsNothing() {
        val facts = lastGameFacts("Played recently", "Super Metroid", "yesterday", sep, Color.Gray)
        assertEquals("Played recently" + sep + "yesterday", facts.before.text)
        assertNull(facts.cuttable)
        assertEquals("", facts.after.text)
    }

    @Test
    fun theLastExtensionIsDroppedOnlyWhenItLooksLikeOne() {
        val cases =
            listOf(
                "Silent Hill (USA).chd" to "Silent Hill (USA)",
                "Final Fantasy VII (USA) (Disc 1).cue" to "Final Fantasy VII (USA) (Disc 1)",
                "Game.v1.0.nsp" to "Game.v1.0",
                "Final Fantasy VII (USA).m3u" to "Final Fantasy VII (USA)",
                "Half-Life 2.steam" to "Half-Life 2",
                "Puyo Puyo.7z" to "Puyo Puyo",
                "Super Mario Bros. 3" to "Super Mario Bros. 3",
                ".hidden" to ".hidden",
                "name." to "name.",
                "a.b" to "a",
                // Six letters is not an extension here; nor is anything with a space, a bracket or a non-ASCII letter.
                "Game.backup" to "Game.backup",
                "Game.(USA)" to "Game.(USA)",
                "Pok\u00e9mon.r\u00e9" to "Pok\u00e9mon.r\u00e9",
                "" to "",
            )
        for ((fileName, shown) in cases) assertEquals(fileName, shown, fileNameWithoutExtension(fileName))
    }
}
