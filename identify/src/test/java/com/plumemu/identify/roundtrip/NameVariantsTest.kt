package com.plumemu.identify.roundtrip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameVariantsTest {
    private fun names(title: String, region: String = "USA") = NameVariants.of(title, region).associate { it.kind to it.fileName }

    @Test fun aTitleGetsTheKindsThatChangeIt() {
        val v = names("Zork Quest II - Caf\u00e9 & Friends")
        assertEquals("Zork Quest II - Caf\u00e9 & Friends (USA).zip", v["exact"])
        assertEquals("Zork_Quest_II_-_Caf\u00e9_&_Friends_(USA).zip", v["underscores"])
        assertEquals("Zork.Quest.II.-.Caf\u00e9.&.Friends.USA-GRP.zip", v["dots"])
        assertEquals("Zork Quest 2 - Caf\u00e9 & Friends (USA).zip", v["roman-to-digit"])
        assertEquals("Zork Quest II Caf\u00e9 & Friends (USA).zip", v["dash-dropped"])
        assertEquals("Zork Quest II - Caf\u00e9 and Friends (USA).zip", v["ampersand-and"])
        assertEquals("Zork Quest II - Cafe & Friends (USA).zip", v["accents-dropped"])
        assertEquals("Zork Quest II - Caf\u00e9 & Friends (USA) (Rev 1).zip", v["rev-tag"])
        assertNull(v["digit-to-roman"])
        assertNull(v["the-moved"])
    }

    @Test fun kindsThatDoNotApplyAreLeftOut() {
        val v = names("ZORK")
        assertNull(v["upper"])
        assertTrue("underscores" !in v && "no-space" !in v && "dash-dropped" !in v)
    }

    @Test fun theMovesBothWays() {
        assertEquals("Simpsons, The - Bart", NameVariants.theMoved("The Simpsons - Bart"))
        assertEquals("The Simpsons - Bart", NameVariants.theMoved("Simpsons, The - Bart"))
        assertNull(NameVariants.theMoved("Theme Park"))
    }

    @Test fun digitsBecomeRomanOnlyAsWholeWords() {
        assertEquals("Zork IV (USA).zip", names("Zork 4")["digit-to-roman"])
        assertNull(names("Zork 44")["digit-to-roman"])
    }

    @Test fun everyKindMadeIsListed() {
        val seen = NameVariants.of("The Zork 2 - Caf\u00e9 & Friends: Part II", "Europe").map { it.kind }.toSet()
        assertTrue(NameVariants.KINDS.containsAll(seen))
    }
}
