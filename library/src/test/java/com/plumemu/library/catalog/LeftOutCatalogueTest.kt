package com.plumemu.library.catalog

import com.plumemu.library.TestRules
import com.plumemu.library.matching.FolderMatch
import com.plumemu.library.matching.SystemMatcher
import com.plumemu.library.scan.DocumentEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the catalogue reports as left out, one reason per file: BIOS, saves, other junk, files the
 * folder's system does not open, files several systems open outside a system's folder. Parts of a
 * game (a cue's bin, a gdi's tracks, a later disc, a regional copy) are in the game and have none.
 * Each list agrees with the catalogue's own counts.
 */
class LeftOutCatalogueTest {
    private val matcher = SystemMatcher(TestRules.ALL)
    private val cataloguer = DirectoryCataloguer(matcher)

    private fun files(vararg names: String) = names.map { DocumentEntry("id:$it", it, 1, 1, isDirectory = false) }

    private fun leftOut(folder: FolderMatch, vararg names: String): Map<String, Pair<LeftOutReason, String?>> {
        val catalogue = cataloguer.catalogue(folder, files(*names))
        assertEquals(catalogue.skippedJunk + catalogue.skippedUnmatched, catalogue.leftOut.size)
        return catalogue.leftOut.associate { it.entry.name to (it.reason to it.systemId) }
    }

    private val psx = matcher.matchFolder("psx")

    @Test
    fun biosFilesAreLeftOutAsBios() {
        assertEquals(mapOf("scph1001.bin" to (LeftOutReason.BIOS to "psx")), leftOut(psx, "scph1001.bin", "Game.chd"))
    }

    @Test
    fun savesAreLeftOutAsSaves() {
        assertEquals(mapOf("Game.srm" to (LeftOutReason.SAVE to "psx"), "Game.state2" to (LeftOutReason.SAVE to "psx")), leftOut(psx, "Game.chd", "Game.srm", "Game.state2"))
    }

    @Test
    fun hiddenFilesNotesAndPicturesAreLeftOutAsJunk() {
        assertEquals(
            mapOf("._Game.chd" to (LeftOutReason.JUNK to "psx"), "cover.png" to (LeftOutReason.JUNK to "psx"), "readme.txt" to (LeftOutReason.JUNK to "psx")),
            leftOut(psx, "._Game.chd", "Game.chd", "cover.png", "readme.txt"),
        )
    }

    @Test
    fun aFileTheFoldersSystemDoesNotOpenIsUnmatchedWithThatSystem() {
        assertEquals(mapOf("Game.sfc" to (LeftOutReason.UNMATCHED to "psx")), leftOut(psx, "Game.sfc", "Game.chd"))
    }

    @Test
    fun outsideASystemsFolderAFileSeveralSystemsOpenWaitsForOneAndAnUnknownTypeIsUnmatched() {
        assertEquals(
            mapOf("Game.iso" to (LeftOutReason.NO_SYSTEM to null), "data.xyz" to (LeftOutReason.UNMATCHED to null)),
            leftOut(FolderMatch.Unknown, "Game.iso", "data.xyz", "Mario.sfc"),
        )
    }

    @Test
    fun partsOfGamesAreInTheirGameAndNotLeftOut() {
        assertEquals(
            emptyMap<String, Pair<LeftOutReason, String?>>(),
            leftOut(
                psx,
                "Game (USA).cue", "Game (USA).bin",
                "Saga (USA) (Disc 1).chd", "Saga (USA) (Disc 2).chd",
                "Run (USA).chd", "Run (Europe).chd",
            ),
        )
        val dreamcast = SystemMatcher(listOf(com.plumemu.library.matching.SystemRule("dreamcast", setOf("dreamcast"), setOf("gdi", "bin", "cdi"))))
        val gdi = DirectoryCataloguer(dreamcast).catalogue(dreamcast.matchFolder("dreamcast"), files("disc.gdi", "track01.bin", "track02.raw", "track03.bin"))
        assertEquals("a gdi's raw track is part of its game", emptyList<LeftOutFile>(), gdi.leftOut)
    }
}
