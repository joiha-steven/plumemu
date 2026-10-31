package com.plumemu.identify.name

import org.junit.Assert.assertEquals
import org.junit.Test

/** The cleanings tried for one file, conservative first. */
class NameCandidatesTest {
    @Test fun conservativeFirstThenSplitThenArticle() {
        assertEquals(listOf("Zork Quest"), NameCandidates.of("Zork Quest", "snes"))
        assertEquals(
            listOf("MagicKnightRayearth2MakingofMagicKnight", "Magic Knight Rayearth 2 Makingof Magic Knight"),
            NameCandidates.of("MagicKnightRayearth2MakingofMagicKnight", "saturn"),
        )
        assertEquals(listOf("PuyoPuyoTsuu", "Puyo Puyo Tsuu"), NameCandidates.of("PuyoPuyoTsuu", "megadrive"))
        assertEquals(listOf("Sims 2 The Castaway", "The Sims 2 Castaway"), NameCandidates.of("Sims 2 The Castaway", "psp"))
        assertEquals(
            listOf("RenStimpyShowTheFireDogs", "Ren Stimpy Show The Fire Dogs", "The Ren Stimpy Show Fire Dogs"),
            NameCandidates.of("RenStimpyShowTheFireDogs", "snes"),
        )
        // Punctuation means the name kept its own spelling: the article is not moved.
        assertEquals(listOf("Zork - The Quest"), NameCandidates.of("Zork - The Quest", "snes"))
    }

    @Test fun releaseNumbersAreDroppedOnDsAndGbaOnly() {
        assertEquals(listOf("1234 - Zork Quest", "Zork Quest"), NameCandidates.of("1234 - Zork Quest", "nds"))
        assertEquals(listOf("1943 - The Battle of Midway", "The Battle of Midway"), NameCandidates.of("1943 - The Battle of Midway", "gba"))
        assertEquals(listOf("1943 - The Battle of Midway"), NameCandidates.of("1943 - The Battle of Midway", "nes"))
        assertEquals(listOf("1942"), NameCandidates.of("1942", "nds"))
    }

    @Test fun wordsSplitAtCaseAndDigitChanges() {
        assertEquals("Zhongguo Xiangqi Chinese Chese", NameCandidates.splitWords("ZhongguoXiangqiChineseChese"))
        assertEquals("Windows 93 Adventure", NameCandidates.splitWords("Windows93 Adventure"))
        assertEquals("XML Parser", NameCandidates.splitWords("XMLParser"))
        assertEquals("Final Fantasy III", NameCandidates.splitWords("FinalFantasyIII"))
        assertEquals(null, NameCandidates.splitWords("Zork Quest"))
        assertEquals(null, NameCandidates.splitWords("ZORK QUEST"))
        assertEquals(null, NameCandidates.splitWords("\u30c9\u30e9\u30b4\u30f3\u30af\u30a8\u30b9\u30c8"))
    }

    @Test fun leadingListNumberIsTriedAfterTheNameAsWritten() {
        assertEquals(listOf("01 - Zork Quest", "Zork Quest"), NameCandidates.of("01 - Zork Quest", "snes"))
        assertEquals(listOf("1. Zork Quest", "Zork Quest"), NameCandidates.of("1. Zork Quest", "snes"))
        assertEquals(listOf("001 - Zork Quest", "Zork Quest"), NameCandidates.of("001 - Zork Quest", "snes"))
        assertEquals(listOf("007 - The World Is Not Enough", "The World Is Not Enough"), NameCandidates.of("007 - The World Is Not Enough", "n64"))
        assertEquals(listOf("1942"), NameCandidates.of("1942", "snes"))
        assertEquals(listOf("2020 Super Baseball"), NameCandidates.of("2020 Super Baseball", "snes"))
        assertEquals(listOf("1080 Snowboarding"), NameCandidates.of("1080 Snowboarding", "n64"))
        assertEquals(listOf("3 Ninjas Kick Back"), NameCandidates.of("3 Ninjas Kick Back", "snes"))
    }
}
