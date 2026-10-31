package com.plumemu.identify.name

import org.junit.Assert.assertEquals
import org.junit.Test

/** The shown fallback of an all-lower or ALL-UPPER file name. */
class TitleCaseTest {
    private val cases = listOf(
        "dynamite cop!" to "Dynamite Cop!",
        "DYNAMITE COP!" to "Dynamite Cop!",
        "PAC-MAN" to "Pac-Man",
        "NBA JAM" to "NBA Jam",
        "world cup usa '94" to "World Cup USA '94",
        "the legend of zelda - a link to the past" to "The Legend of Zelda - A Link to the Past",
        "FINAL FANTASY III" to "Final Fantasy III",
        "m.a.s.h" to "M.A.S.H",
        "game hits!" to "Game Hits!",
        "super mario kart" to "Super Mario Kart",
        "meine tierpension 2" to "Meine Tierpension 2",
        "nba 2k10" to "NBA 2K10",
        "tmnt" to "TMNT",
        "ESPN NFL 2K5" to "ESPN NFL 2K5",
        "MLB SLUGFEST" to "MLB Slugfest",
        "UFC THROWDOWN" to "UFC Throwdown",
        "gta advance" to "GTA Advance",
        "BMX XXX" to "BMX XXX",
        "zq" to "Zq", // no vowel, but the file did not write it in capitals
        "f1 race" to "F1 Race",
        "WRC 3D HD" to "WRC 3D HD",
        "DR. MARIO" to "Dr. Mario",
        "ys book i & ii" to "Ys Book I & II",
        "sunday vs. magazine" to "Sunday vs. Magazine",
        "xmen2p" to "xmen2p",
        // Mixed case, kana: never touched.
        "KAO the Kangaroo" to "KAO the Kangaroo",
        "SpongeBob" to "SpongeBob",
        "\u30c9\u30e9\u30b4\u30f3\u30af\u30a8\u30b9\u30c8V" to "\u30c9\u30e9\u30b4\u30f3\u30af\u30a8\u30b9\u30c8V",
        "1942" to "1942",
    )

    @Test fun table() {
        val failures = cases.mapNotNull { (title, expected) ->
            val got = TitleCase.readable(title)
            if (got == expected) null else "'$title': expected '$expected', got '$got'"
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    @Test fun arcadeShortNamesStayAsWritten() {
        assertEquals("mslug", TitleCase.forSystem("mslug", "mame"))
        assertEquals("dkong", TitleCase.forSystem("dkong", "fbneo"))
        assertEquals("dkong", TitleCase.forSystem("dkong", "neogeo"))
        assertEquals("Dkong", TitleCase.forSystem("dkong", "nes"))
    }

    @Test fun fallbackDropsAListNumberButKeepsNumbersThatAreTitles() {
        assertEquals("Zork Quest", TitleCase.forSystem("01 - Zork Quest", "snes"))
        assertEquals("Zork Quest", TitleCase.forSystem("1. Zork Quest", "snes"))
        assertEquals("Zork Quest", TitleCase.forSystem("001_ Zork Quest", "snes"))
        assertEquals("007 - The World Is Not Enough", TitleCase.forSystem("007 - The World Is Not Enough", "n64"))
        assertEquals("300 - March to Glory", TitleCase.forSystem("300 - March to Glory", "ps2"))
        assertEquals("1942", TitleCase.forSystem("1942", "snes"))
        assertEquals("3 Ninjas Kick Back", TitleCase.forSystem("3 Ninjas Kick Back", "snes"))
        assertEquals("2020 Super Baseball", TitleCase.forSystem("2020 Super Baseball", "snes"))
        assertEquals("1080 Snowboarding", TitleCase.forSystem("1080 Snowboarding", "n64"))
        assertEquals("1942", TitleCase.forSystem("1942", "mame"))
    }
}
