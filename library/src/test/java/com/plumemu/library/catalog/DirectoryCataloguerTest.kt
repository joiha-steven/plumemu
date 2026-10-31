package com.plumemu.library.catalog

import com.plumemu.library.TestRules
import com.plumemu.library.matching.FolderMatch
import com.plumemu.library.matching.SystemMatcher
import com.plumemu.identify.name.Region
import com.plumemu.library.scan.DocumentEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectoryCataloguerTest {
    private val matcher = SystemMatcher(TestRules.ALL)
    private val cataloguer = DirectoryCataloguer(matcher)

    private fun files(vararg names: String) = names.map { DocumentEntry("id:$it", it, 1, 1, isDirectory = false) }

    private fun catalogue(folder: String, vararg names: String, with: DirectoryCataloguer = cataloguer) =
        with.catalogue(matcher.matchFolder(folder), files(*names))

    private fun DirectoryCatalogue.shown() = games.filter { it.isPrimaryCopy }.map { it.entry.name }

    /** [names] in a directory called [directory] below the PlayStation folder (`psx/<directory>/`). */
    private fun inFolder(directory: String, vararg names: String, system: String = "psx") =
        cataloguer.catalogue(matcher.matchPath("ROMs", "$system/$directory"), files(*names), directory)

    private val dreamcast = SystemMatcher(listOf(com.plumemu.library.matching.SystemRule("dreamcast", setOf("dreamcast"), setOf("gdi", "bin", "cdi"))))

    @Test
    fun emptyDirectoryGivesNoGames() {
        assertEquals(DirectoryCatalogue(emptyList(), 0, 0), cataloguer.catalogue(FolderMatch.Unknown, emptyList()))
    }

    @Test
    fun directoryWithOnlyBiosAndJunkGivesNoGames() {
        val result = catalogue("psx", "scph1001.bin", "scph5501.bin", "readme.txt", "cover.png", ".hidden", "Game.srm")
        assertEquals(emptyList<CataloguedGame>(), result.games)
        assertEquals(6, result.skippedJunk)
    }

    @Test
    fun subdirectoriesInTheListingAreNotGames() {
        val listing = listOf(DocumentEntry("d", "hacks", 0, 1, isDirectory = true)) + files("Game.sfc")
        assertEquals(listOf("Game.sfc"), cataloguer.catalogue(matcher.matchFolder("snes"), listing).games.map { it.entry.name })
    }

    @Test
    fun cueHidesItsBinAndTrackFiles() {
        val result = catalogue("psx", "Crash (USA).cue", "Crash (USA).bin", "Rayman (USA).cue", "Rayman (USA) (Track 1).bin", "Rayman (USA) (Track 2).bin")
        assertEquals(listOf("Crash (USA).cue", "Rayman (USA).cue"), result.shown())
        assertEquals(listOf(1, 1), result.games.map { it.discCount })
    }

    @Test
    fun loneBinWithoutCueIsAGame() {
        assertEquals(listOf("Crash (USA).bin"), catalogue("psx", "Crash (USA).bin").shown())
    }

    @Test
    fun m3uStandsForItsDiscsAndHidesThem() {
        val result = catalogue(
            "psx",
            "Final Fantasy VII (USA).m3u",
            "Final Fantasy VII (USA) (Disc 1).cue", "Final Fantasy VII (USA) (Disc 1).bin",
            "Final Fantasy VII (USA) (Disc 2).cue", "Final Fantasy VII (USA) (Disc 2).bin",
            "Final Fantasy VII (USA) (Disc 3).cue", "Final Fantasy VII (USA) (Disc 3).bin",
        )
        val game = result.games.single()
        assertEquals("Final Fantasy VII (USA).m3u", game.entry.name)
        assertEquals("Final Fantasy VII", game.title)
        assertEquals("Final Fantasy VII (USA)", game.discGroup)
        assertEquals(3, game.discCount)
    }

    @Test
    fun discSiblingsWithoutM3uBecomeOneGameOnTheFirstDisc() {
        val result = catalogue("psx", "MGS (USA) (Disc 2 of 2).chd", "MGS (USA) (Disc 1 of 2).chd", "Other (USA).chd")
        val mgs = result.games.single { it.discGroup != null }
        assertEquals("MGS (USA) (Disc 1 of 2).chd", mgs.entry.name)
        assertEquals("MGS (USA)", mgs.discGroup)
        assertEquals(2, mgs.discCount)
        assertEquals(2, result.games.size)
    }

    @Test
    fun discsOfDifferentRegionsAreSeparateGamesGroupedAsRegionalCopies() {
        val result = catalogue("psx", "MGS (USA) (Disc 1).chd", "MGS (USA) (Disc 2).chd", "MGS (Europe) (Disc 1).chd", "MGS (Europe) (Disc 2).chd")
        assertEquals(2, result.games.size)
        assertEquals(listOf("MGS (USA) (Disc 1).chd"), result.shown())
    }

    @Test
    fun regionalCopiesShowThePreferredRegion() {
        val names = arrayOf("Mario (Europe).sfc", "Mario (USA).sfc", "Mario (Japan).sfc", "Zelda (Japan).sfc")
        assertEquals(listOf("Mario (USA).sfc", "Zelda (Japan).sfc"), catalogue("snes", *names).shown())
        val european = DirectoryCataloguer(matcher, CatalogSettings(preferredRegions = listOf(Region.EUROPE)))
        assertEquals(listOf("Mario (Europe).sfc", "Zelda (Japan).sfc"), catalogue("snes", *names, with = european).shown())
    }

    @Test
    fun worldReleaseBeatsUnlistedRegionsAndCleanDumpBeatsBetaAndBadDump() {
        val european = DirectoryCataloguer(matcher, CatalogSettings(preferredRegions = listOf(Region.EUROPE)))
        assertEquals(listOf("Tetris (World).sfc"), catalogue("snes", "Tetris (USA).sfc", "Tetris (World).sfc", with = european).shown())
        assertEquals(listOf("Contra (USA).sfc"), catalogue("snes", "Contra (USA) [b].sfc", "Contra (USA).sfc").shown())
    }

    @Test
    fun translationsHacksRevisionsAndBetasAreNotHiddenAsRegionalCopies() {
        assertEquals(
            listOf("Mother 3 (Japan) [T+Eng].gba", "Mother 3 (Japan).gba"),
            catalogue("gba", "Mother 3 (Japan).gba", "Mother 3 (Japan) [T+Eng].gba").shown(),
        )
        val names = arrayOf(
            "Contra (USA).sfc", "Contra (Europe) (En,Fr,De).sfc", "Contra (USA) (Beta).sfc", "Contra (USA) (Rev 1).sfc",
            "Contra (USA) [h1].sfc", "Contra (Hack).sfc",
        )
        assertEquals(
            listOf("Contra (Hack).sfc", "Contra (USA) (Beta).sfc", "Contra (USA) (Rev 1).sfc", "Contra (USA) [h1].sfc", "Contra (USA).sfc"),
            catalogue("snes", *names).shown(),
        )
    }

    @Test
    fun gdiHidesItsTrackFilesAndMdsHidesItsMdf() {
        val dreamcast = DirectoryCataloguer(SystemMatcher(listOf(com.plumemu.library.matching.SystemRule("dc", setOf("dreamcast"), setOf("gdi", "bin", "raw", "cdi")))))
        val dc = dreamcast.catalogue(FolderMatch.Unknown, files("Shenmue (USA).gdi", "track01.bin", "track02.raw", "track03.bin", "Track04.BIN"))
        assertEquals(listOf("Shenmue (USA).gdi"), dc.games.map { it.entry.name })
        val ps2 = DirectoryCataloguer(SystemMatcher(listOf(com.plumemu.library.matching.SystemRule("ps2", setOf("ps2"), setOf("mds", "mdf", "iso")))))
        assertEquals(listOf("Okami (USA).mds"), ps2.catalogue(FolderMatch.Unknown, files("Okami (USA).mds", "Okami (USA).mdf")).games.map { it.entry.name })
    }

    @Test
    fun arcadeShortNameIsTheWalksTitleUntilTheIdentificationPassNamesIt() {
        // The arcade name pack is read by IdentifyPass (LibraryIdentifyTest), not by the walk.
        val game = catalogue("mame", "sf2.zip").games.single()
        assertEquals("sf2", game.title)
        assertEquals("sf2", game.sortName)
    }

    @Test
    fun unmatchedFilesAreCounted() {
        val result = cataloguer.catalogue(FolderMatch.Unknown, files("Mystery.iso", "Game.xyz", "Sonic.md"))
        assertEquals(listOf("Sonic.md"), result.games.map { it.entry.name })
        assertEquals(2, result.skippedUnmatched)
    }

    @Test
    fun aReadmeBesideTheGamesIsNotAMegaDriveGame() {
        val result = cataloguer.catalogue(FolderMatch.Unknown, files("README.md", "LICENSE.md", "Sonic.md"))
        assertEquals(listOf("Sonic.md"), result.games.map { it.entry.name })
        assertEquals(2, result.skippedJunk)
    }

    @Test
    fun discMarkersInEveryFormGroupTheDiscsAndLeaveTheTitleWithoutThem() {
        val result = catalogue(
            "psx",
            "Star Rover - Disc 1.chd", "Star Rover - Disc 2.chd",
            "Moon Tale [Disc1].chd", "Moon Tale [Disc2].chd",
            "Ice Path CD1.cue", "Ice Path CD1.bin", "Ice Path CD2.cue", "Ice Path CD2.bin",
            "Sand Run (CD1).chd", "Sand Run (CD2).chd",
            "Fog Town Disc1.chd", "Fog Town Disc2.chd",
            "Dune Cry Disk 1.chd", "Dune Cry Disk 2.chd",
            "Pale Sea (USA) (Disc 1 of 2).chd", "Pale Sea (USA) (Disc 2 of 2).chd",
        )
        assertEquals(
            listOf("Dune Cry", "Fog Town", "Ice Path", "Moon Tale", "Pale Sea", "Sand Run", "Star Rover"),
            result.games.map { it.title }.sorted(),
        )
        assertEquals(List(7) { 2 }, result.games.map { it.discCount })
        assertEquals(listOf("Ice Path CD1.cue", "Star Rover - Disc 1.chd"), result.shown().filter { it.startsWith("Ice") || it.startsWith("Star") })
        assertEquals("Star Rover", result.games.single { it.title == "Star Rover" }.discGroup)
    }

    @Test
    fun discsOfDifferentTitlesAndATranslationNeverGroup() {
        val result = catalogue(
            "psx",
            "Final Quest VII - Disc 1.chd", "Final Quest VII - Disc 2.chd", "Final Quest VIII - Disc 1.chd", "Final Quest VIII - Disc 2.chd",
            "Alpha Disc 1.chd", "Beta Disc 1.chd",
            "Hero Saga (Japan) (Disc 1).chd", "Hero Saga (Japan) (Disc 2).chd",
            "Hero Saga (Japan) [T+Eng] (Disc 1).chd", "Hero Saga (Japan) [T+Eng] (Disc 2).chd",
        )
        assertEquals(6, result.games.size)
        assertEquals(6, result.shown().size)
        assertEquals(listOf(2, 2, 2, 2, 1, 1), result.games.sortedBy { -it.discCount }.map { it.discCount })
    }

    @Test
    fun wordsThatAreNotDiscMarkersStayInTheTitle() {
        val result = catalogue("psx", "Sonic Racer CD.chd", "Disc Jockey 2000.chd", "Tape Quest (Side A).chd", "Tape Quest (Side B).chd")
        assertEquals(listOf("Disc Jockey 2000", "Sonic Racer CD", "Tape Quest", "Tape Quest"), result.games.map { it.title }.sorted())
        assertEquals(4, result.shown().size)
    }

    @Test
    fun gdiHidesItsTracksWhateverTheirExtensionAndItsFolderNamesTheGame() {
        val folder = DirectoryCataloguer(dreamcast)
        val under = HashMap<DocumentEntry, DocumentEntry>()
        val names = arrayOf("disc.gdi", "track01.bin", "track02.raw", "track03.bin")
        val result = folder.catalogue(dreamcast.matchPath("ROMs", "dreamcast/Moon Racer (USA)"), files(*names), "Moon Racer (USA)", under)
        val game = result.games.single()
        assertEquals("disc.gdi", game.entry.name)
        assertEquals("Moon Racer", game.title)
        assertEquals(listOf(Region.USA), game.regions)
        assertEquals("a .raw track is no unmatched file", 0, result.skippedUnmatched)
        assertEquals(setOf("track01.bin", "track02.raw", "track03.bin"), under.keys.map { it.name }.toSet())
        assertEquals(mapOf("id:track01.bin" to "id:disc.gdi", "id:track02.raw" to "id:disc.gdi", "id:track03.bin" to "id:disc.gdi"), result.partOf)

        val named = folder.catalogue(dreamcast.matchFolder("dreamcast"), files("Sky Fox (Europe).gdi", "Sky Fox (Europe) (Track 1).bin", "Sky Fox (Europe) (Track 2).raw"))
        assertEquals(listOf("Sky Fox (Europe).gdi"), named.games.map { it.entry.name })
        assertEquals(0, named.skippedUnmatched)

        val copies = folder.catalogue(dreamcast.matchFolder("dreamcast"), files("Crazy Racer (USA).gdi", "Crazy Racer (USA) (Track 1).bin", "Crazy Racer (USA).cdi"))
        assertEquals("a .cdi copy beside the .gdi is a game of its own", listOf("Crazy Racer (USA).cdi", "Crazy Racer (USA).gdi"), copies.games.map { it.entry.name })

        val noGdi = folder.catalogue(dreamcast.matchFolder("dreamcast"), files("Sky Fox (Europe).cdi", "track02.raw"))
        assertEquals("without a .gdi a .raw file is unmatched", 1, noGdi.skippedUnmatched)
    }

    @Test
    fun oneGamesFolderWithGenericFileNamesTakesItsTitleFromTheFolder() {
        val discs = inFolder("Moon Tale (France)", "Disc 1.chd", "Disc 2.chd", "Disc 3.chd").games.single()
        assertEquals("Disc 1.chd", discs.entry.name)
        assertEquals("Moon Tale", discs.title)
        assertEquals(listOf(Region.FRANCE), discs.regions)
        assertEquals(3, discs.discCount)
        assertEquals("Moon Tale (France)", discs.discGroup)

        assertEquals("Ice Path", inFolder("Ice Path (Europe)", "disc1.chd", "disc2.chd").games.single().title)
        assertEquals("Fog Town", inFolder("Fog Town (USA)", "game.iso").games.single().title)
        assertEquals("Fog Town", inFolder("Fog Town (USA)", "main.chd").games.single().title)
    }

    @Test
    fun aFolderOfSeveralGamesOrAFileNamedLikeAGameKeepsTheFileNames() {
        assertEquals(listOf("Alpha", "Game"), inFolder("RPGs", "game.iso", "Alpha (USA).chd").games.map { it.title }.sorted())
        assertEquals(listOf("Disc", "Game"), inFolder("Mixed", "game.iso", "disc.chd").games.map { it.title }.sorted())
        assertEquals("Space Hunter", inFolder("Hunter Collection", "Space Hunter (USA).iso").games.single().title)
        assertEquals("a title of digits names a game", "007", inFolder("Agent (USA)", "007.iso").games.single().title)
        // A system's folder is no game's folder; a lone `Disc 1` there keeps its own words
        // (all-lower names are shown title-cased until a pack names them).
        assertEquals("Game", cataloguer.catalogue(matcher.matchFolder("psx"), files("game.iso"), "psx").games.single().title)
        assertEquals("Disc 1", cataloguer.catalogue(matcher.matchFolder("psx"), files("Disc 1.chd", "Disc 2.chd"), "psx").games.single().title)
    }

    @Test
    fun ps2FolderOfOneGameTakesTheTitleFromTheFileAndGroupsItsDiscs() {
        val single = inFolder("Space Hunter (USA)", "Space Hunter (USA).iso", system = "ps2").games.single()
        assertEquals("ps2", single.systemId)
        assertEquals("Space Hunter", single.title)
        val inside = inFolder("Space Hunter II (USA)", "Space Hunter II (USA) (Disc 1).iso", "Space Hunter II (USA) (Disc 2).iso", system = "ps2")
        assertEquals(listOf(2), inside.games.map { it.discCount })
        val outside = catalogue("ps2", "Space Hunter II (USA) CD1.iso", "Space Hunter II (USA) CD2.iso", "Space Hunter (USA).iso")
        assertEquals(listOf("Space Hunter", "Space Hunter II"), outside.games.map { it.title }.sorted())
    }

    @Test
    fun aLonePlaylistTakesTheDirectorysOneGameWhateverItsName() {
        val set = inFolder("FQ7", "FQ7.m3u", "Final Quest VII (USA) (Disc 1).chd", "Final Quest VII (USA) (Disc 2).chd", "Final Quest VII (USA) (Disc 3).chd")
        val game = set.games.single()
        assertEquals("FQ7.m3u", game.entry.name)
        assertEquals(3, game.discCount)
        assertEquals(setOf("id:Final Quest VII (USA) (Disc 1).chd", "id:Final Quest VII (USA) (Disc 2).chd", "id:Final Quest VII (USA) (Disc 3).chd"), set.partOf.keys)
        assertEquals(setOf("id:FQ7.m3u"), set.partOf.values.toSet())

    }

    @Test
    fun aPlaylistInASystemFolderOrBesideALoneFileTakesNothing() {
        // The discs live in PS1/multidisc/; the m3u and an unrelated game sit in PS1/ itself.
        val ps1 = cataloguer.catalogue(matcher.matchPath("ROMs", "PS1"), files("Final Fantasy VII (USA).m3u", "Crash Bandicoot (USA).chd"), "PS1")
        assertEquals(listOf("Crash Bandicoot (USA).chd", "Final Fantasy VII (USA).m3u"), ps1.shown())
        assertEquals(emptyMap<String, String>(), ps1.partOf)
        // Same in the picked folder itself, and in a system folder with a disc set.
        val root = cataloguer.catalogue(matcher.matchPath("ROMs", ""), files("FF7.m3u", "Final Quest VII (USA) (Disc 1).cue", "Final Quest VII (USA) (Disc 2).cue"), null)
        assertEquals(2, root.games.size)
        val system = catalogue("psx", "FF7.m3u", "Final Quest VII (USA) (Disc 1).chd", "Final Quest VII (USA) (Disc 2).chd")
        assertEquals(2, system.games.size)
        // In one game's folder a lone file is not taken, nor one disc-tagged file: a set is two or more discs.
        assertEquals(2, inFolder("Roar", "Roar 2.m3u", "Bloody Roar II (USA).chd").games.size)
        assertEquals(2, inFolder("F", "X.m3u", "Game (USA) (Disc 1).chd").games.size)
    }

    @Test
    fun aPlaylistBesideTwoGamesOrASecondPlaylistTakesNothing() {
        val two = catalogue("psx", "Mix.m3u", "Alpha (USA) (Disc 1).chd", "Alpha (USA) (Disc 2).chd", "Beta (USA).chd")
        assertEquals(listOf("Alpha (USA) (Disc 1).chd", "Beta (USA).chd", "Mix.m3u"), two.shown())
        val playlists = catalogue("psx", "A.m3u", "B.m3u", "Alpha (USA) (Disc 1).chd", "Alpha (USA) (Disc 2).chd")
        assertEquals(3, playlists.games.size)
    }

    @Test
    fun partOfPointsEveryFileWithoutARowAtItsGamesRow() {
        val result = catalogue("psx", "Crash (USA).cue", "Crash (USA).bin", "Duo - Disc 1.chd", "Duo - Disc 2.chd", "Saga (USA).m3u", "Saga (USA) (Disc 1).cue", "Saga (USA) (Disc 1).bin")
        assertEquals(
            mapOf(
                "id:Crash (USA).bin" to "id:Crash (USA).cue",
                "id:Duo - Disc 2.chd" to "id:Duo - Disc 1.chd",
                "id:Saga (USA) (Disc 1).cue" to "id:Saga (USA).m3u",
                "id:Saga (USA) (Disc 1).bin" to "id:Saga (USA).m3u",
            ),
            result.partOf,
        )
    }

    @Test
    fun shownUnderTellsWhatEachShownGameStandsForAndChangesNothing() {
        val names = arrayOf(
            "Saga (USA).m3u", "Saga (USA) (Disc 1).cue", "Saga (USA) (Disc 1).bin", "Saga (USA) (Disc 2).cue", "Saga (USA) (Disc 2).bin",
            "Duo (Disc 1).chd", "Duo (Disc 2).chd", "Racer (USA).sfc", "Racer (Europe).sfc", "Alone (USA).cue", "Alone (USA).bin",
        )
        val folder = matcher.matchFolder("psx")
        val plain = cataloguer.catalogue(folder, files(*names))
        val under = HashMap<DocumentEntry, DocumentEntry>()
        assertEquals(plain, cataloguer.catalogue(folder, files(*names), null, under))
        val snes = HashMap<DocumentEntry, DocumentEntry>()
        cataloguer.catalogue(matcher.matchFolder("snes"), files("Racer (USA).sfc", "Racer (Europe).sfc"), null, snes)
        fun top(name: String, map: Map<DocumentEntry, DocumentEntry>): String {
            var at = files(name).single()
            while (true) at = map[at] ?: return at.name
        }
        for (disc in names.filter { it.startsWith("Saga") }) assertEquals("Saga (USA).m3u", top(disc, under))
        assertEquals("Duo (Disc 1).chd", top("Duo (Disc 2).chd", under))
        assertEquals("Alone (USA).cue", top("Alone (USA).bin", under))
        assertEquals("Racer (USA).sfc", top("Racer (Europe).sfc", snes))
        assertEquals(setOf("Racer (Europe).sfc"), snes.keys.map { it.name }.toSet())
    }

    @Test
    fun numberedFilesAreCopiesOnlyBesideThePlainName() {
        // No plain "Game.chd": three different files, three rows as before.
        assertEquals(
            listOf("Game (1).chd", "Game (2).chd", "Game (3).chd"),
            catalogue("psx", "Game (1).chd", "Game (2).chd", "Game (3).chd").shown(),
        )
        // A download copy beside its original folds under it.
        assertEquals(
            listOf("Zelda (USA).sfc"),
            catalogue("snes", "Zelda (USA).sfc", "Zelda (USA) (1).sfc").shown(),
        )
    }

    // Sheets (.cue, .gdi, .m3u) name their parts whatever the parts are called.

    private fun withSheets(
        folder: FolderMatch,
        names: List<String>,
        sheets: Map<String, Set<String>>,
        directoryName: String? = null,
        with: DirectoryCataloguer = cataloguer,
    ): DirectoryCatalogue {
        val listing = files(*names.toTypedArray())
        val ids = listing.associate { it.name to it.documentId }
        return with.catalogue(folder, listing, directoryName, sheets.entries.associate { (sheet, parts) -> ids.getValue(sheet) to parts.map(ids::getValue).toSet() })
    }

    @Test
    fun aCueHidesABinWithAnotherName() {
        val result = withSheets(matcher.matchFolder("psx"), listOf("Game (USA).cue", "Track 01.bin", "Track 02.bin"), mapOf("Game (USA).cue" to setOf("Track 01.bin", "Track 02.bin")))
        assertEquals(listOf("Game (USA).cue"), result.shown())
        assertEquals(setOf("id:Track 01.bin", "id:Track 02.bin"), result.partOf.keys)
        assertEquals(setOf("id:Game (USA).cue"), result.partOf.values.toSet())
    }

    @Test
    fun aStrayBinNoSheetNamesStaysARow() {
        val result = withSheets(matcher.matchFolder("psx"), listOf("Game (USA).cue", "Track 01.bin", "Stray.bin"), mapOf("Game (USA).cue" to setOf("Track 01.bin")))
        assertEquals(listOf("Game (USA).cue", "Stray.bin"), result.shown())
    }

    @Test
    fun aBinInAFolderNoSystemClaimsIsNotCountedUnmatched() {
        val unknown = withSheets(matcher.matchFolder("psx"), listOf("Game.cue", "game_t1.dat"), mapOf("Game.cue" to setOf("game_t1.dat")))
        assertEquals(listOf("Game.cue"), unknown.shown())
        assertEquals(0, unknown.skippedUnmatched)
    }

    @Test
    fun aGdiHidesTracksNamedAnyway() {
        val result = withSheets(
            dreamcast.matchFolder("dreamcast"), listOf("Crazy Taxi.gdi", "Track 01.bin", "t2.raw"), mapOf("Crazy Taxi.gdi" to setOf("Track 01.bin", "t2.raw")),
            with = DirectoryCataloguer(dreamcast),
        )
        assertEquals(listOf("Crazy Taxi.gdi"), result.shown())
    }

    @Test
    fun anM3uNamedDifferentlyTakesItsDiscsAndCountsThem() {
        val discs = listOf("FF7 (Disc 1).chd", "FF7 (Disc 2).chd", "FF7 (Disc 3).chd")
        val result = withSheets(matcher.matchFolder("psx"), listOf("Final Fantasy VII.m3u") + discs, mapOf("Final Fantasy VII.m3u" to discs.toSet()))
        assertEquals(listOf("Final Fantasy VII.m3u"), result.shown())
        assertEquals(3, result.games.single().discCount)
    }

    @Test
    fun anM3uNamingCuesTakesTheirBinsThroughTheChain() {
        val under = HashMap<DocumentEntry, DocumentEntry>()
        val listing = files("Saga.m3u", "D1.cue", "D2.cue", "a.bin", "b.bin")
        val id = listing.associate { it.name to it.documentId }
        val result = cataloguer.catalogue(
            matcher.matchFolder("psx"), listing, null, under,
            mapOf(id.getValue("Saga.m3u") to setOf(id.getValue("D1.cue"), id.getValue("D2.cue")), id.getValue("D1.cue") to setOf(id.getValue("a.bin")), id.getValue("D2.cue") to setOf(id.getValue("b.bin"))),
        )
        assertEquals(listOf("Saga.m3u"), result.shown())
        assertEquals(2, result.games.single().discCount)
        assertEquals(setOf("id:D1.cue", "id:D2.cue", "id:a.bin", "id:b.bin"), result.partOf.keys)
        assertEquals(setOf("id:Saga.m3u"), result.partOf.values.toSet())
    }

    @Test
    fun sheetsNamingEachOtherClaimNothing() {
        val result = withSheets(matcher.matchFolder("psx"), listOf("A.m3u", "B.m3u"), mapOf("A.m3u" to setOf("B.m3u"), "B.m3u" to setOf("A.m3u")))
        assertEquals(listOf("A.m3u", "B.m3u"), result.shown())
    }

    @Test
    fun aFileOfAnotherSystemIsNotTakenFromTheRows() {
        val mixed = SystemMatcher(listOf(com.plumemu.library.matching.SystemRule("psx", setOf("psx"), setOf("cue", "m3u")), com.plumemu.library.matching.SystemRule("snes", setOf("snes"), setOf("sfc"))))
        val listing = files("Mix.m3u", "Game.sfc")
        val ids = listing.associate { it.name to it.documentId }
        val result = DirectoryCataloguer(mixed).catalogue(FolderMatch.Unknown, listing, null, mapOf(ids.getValue("Mix.m3u") to setOf(ids.getValue("Game.sfc"))))
        assertEquals(listOf("Game.sfc", "Mix.m3u"), result.shown())
    }
}
