package com.plumemu.library.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Disc markers, generic names and disc counts; made-up titles. */
class DiscGroupingTest {
    private val markers = listOf(
        "Star Rover - Disc 1" to "Star Rover (Disc 1)",
        "Star Rover - Disc 2 (USA)" to "Star Rover (Disc 2) (USA)",
        "Star Rover Disc1" to "Star Rover (Disc 1)",
        "Star Rover disc 02" to "Star Rover (Disc 2)",
        "Star Rover CD1" to "Star Rover (Disc 1)",
        "Star Rover CD 2" to "Star Rover (Disc 2)",
        "Star Rover_CD1" to "Star Rover (Disc 1)",
        "Star Rover-CD3" to "Star Rover (Disc 3)",
        "Star Rover Disk 2 of 3" to "Star Rover (Disc 2 of 3)",
        "Star Rover [Disc1]" to "Star Rover (Disc 1)",
        "Star Rover [Disc 2]" to "Star Rover (Disc 2)",
        "Star Rover [CD 1 of 2]" to "Star Rover (Disc 1 of 2)",
        "Star Rover [Disc1] (Europe)" to "Star Rover (Disc 1) (Europe)",
        "My Game - Disc 1" to "My Game (Disc 1)",
        "Street Fighter II Movie CD1" to "Street Fighter II Movie (Disc 1)",
        "disc1" to "(Disc 1)",
        "Disc 2" to "(Disc 2)",
        "CD1" to "(Disc 1)",
    )

    private val notMarkers = listOf(
        "Star Rover (Disc 1)", // already the No-Intro form: unchanged, so stored disc groups stay
        "Star Rover (USA) (Disc 1 of 2)",
        "Star Rover (CD1)",
        "Sonic Racer CD", // a word without a number
        "Disc Jockey 2000", // the word inside the title
        "Tape Quest (Side A)", // a side, not a disc
        "Star Rover 2",
        "Official Magazine Demo Disc 34 (USA)", // two digits: a title
        "Official Magazine Demo Disc 3", // a demo disc is a title
        "Rock Band Bonus CD 1",
        // Separate releases in the DATs and in the wild: the disc word is part of the title.
        "Tango - Game On Demo Disc 2 (Europe)",
        "NekoNeko Fan Disc 2 (Japan)",
        "Sweet Days Append Disc 2",
        "Hero Saga Drama CD 2",
        "Hero Saga Sound CD 2",
        "Hero Saga Bonus Disc 1",
        "Action Replay - Premium CD 2 (Japan)",
        "Hero Saga Special Disc 3",
        "GameStar Special-CD 6 (Germany)",
        "Sakura Wars Tokuten Disc 1",
        "Interactive CD Sampler Disc 6 (USA)",
        "Star Rover",
    )

    @Test
    fun discMarkersInOtherFormsBecomeTheNoIntroForm() {
        for ((name, expected) in markers) assertEquals(name, expected, DiscMarkers.canonical(name))
    }

    @Test
    fun namesWithoutAnotherFormOfMarkerAreUnchanged() {
        for (name in notMarkers) assertEquals(name, name, DiscMarkers.canonical(name))
    }

    @Test
    fun genericNamesNameNoGame() {
        for (title in listOf("game", "Game", "disc", "Disc", "rom", "main", "track01", "Disc 2", "", "playlist", "image1")) {
            assertTrue(title, DiscMarkers.isGeneric(title))
        }
        for (title in listOf("Galerians", "Game Over Zone", "Disc Jockey", "Roms and Robbers", "Main Street 2", "720", "007", "1942")) {
            assertFalse(title, DiscMarkers.isGeneric(title))
        }
    }

    private fun file(name: String, system: String = "psx") =
        MatchedFile(name, DiscMarkers.canonical(name.substringBeforeLast('.')), name.substringAfterLast('.'), system)

    @Test
    fun discCountIsTheNumberOfDiscsForADiscSetAndAPlaylistAlike() {
        // Disc 1 kept twice (.chd and .iso) is one disc, with or without the playlist.
        val discs = listOf(file("Duo (Disc 1).chd"), file("Duo (Disc 1).iso"), file("Duo (Disc 2).chd"))
        assertEquals(listOf(2), DiscGrouping.group(discs).map { it.discCount })
        assertEquals(listOf(2), DiscGrouping.group(discs + file("Duo.m3u")).map { it.discCount })
    }

    @Test
    fun gdiTracksAreTracksWhateverTheirExtensionButPlaylistsAndSheetsAreNot() {
        val keys = setOf("moon racer (usa)")
        assertTrue(DiscGrouping.isGdiTrack("track02", "raw", keys))
        assertTrue(DiscGrouping.isGdiTrack("Track04", "bin", keys))
        assertTrue(DiscGrouping.isGdiTrack("Moon Racer (USA) (Track 3)", "raw", keys))
        assertFalse(DiscGrouping.isGdiTrack("track01", "gdi", keys))
        assertFalse(DiscGrouping.isGdiTrack("track01", "cue", keys))
        assertTrue("old dumps list data tracks as .iso", DiscGrouping.isGdiTrack("track03", "iso", keys))
        assertTrue(DiscGrouping.isGdiTrack("Moon Racer (USA) (Track 2)", "wav", keys))
        // Another copy of the game beside its .gdi is no track.
        assertFalse(DiscGrouping.isGdiTrack("Moon Racer (USA)", "chd", keys))
        assertFalse(DiscGrouping.isGdiTrack("Moon Racer (USA)", "cdi", keys))
        assertFalse(DiscGrouping.isGdiTrack("Moon Racer (USA)", "iso", keys))
        assertFalse(DiscGrouping.isGdiTrack("track04", "chd", keys))
        assertFalse(DiscGrouping.isGdiTrack("Other Game (USA) (Track 3)", "bin", keys))
        assertFalse("no .gdi in the directory", DiscGrouping.isGdiTrack("track02", "raw", emptySet()))
    }
}
