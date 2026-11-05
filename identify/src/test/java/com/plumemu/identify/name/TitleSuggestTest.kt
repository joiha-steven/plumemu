package com.plumemu.identify.name

import com.plumemu.identify.IdentifyLog
import com.plumemu.identify.pack.BundledPacks
import com.plumemu.identify.pack.PackAssets
import com.plumemu.identify.pack.PackKind
import com.plumemu.identify.pack.PackStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** [TitleSearch.suggest]: the few close titles Settings' Unidentified games offers, on made-up titles and on the real packs. */
class TitleSuggestTest {
    @get:Rule val temp = TemporaryFolder()

    private val names = listOf(
        "Zork Quest", "Zork Quest II", "Zork Quest III", "The Moonlit Harbor", "Magic Knight Rayearth 2", "Metroid Fusion",
        "Final Fantasy VI", "Final Fantasy V", "Advance Wars 2 - Black Hole Rising", "Galfen Island", "Galfen Island Deluxe",
    )
    private val search = TitleSearch(names)

    private fun suggested(query: String, source: TitleSearch = search, list: List<String> = names) =
        source.suggest(query).map { list[it.index] }

    @Test fun aTypoOrMissingLettersStillFindsTheTitle() {
        assertEquals("Galfen Island", suggested("Galfin Island").first())
        assertEquals(listOf("Metroid Fusion"), suggested("mtrd fusn"))
    }

    @Test fun runTogetherNamesAreSplitAndSqueezed() {
        assertEquals("Magic Knight Rayearth 2", suggested("MagicKnightRayearth2").first())
        assertEquals("Magic Knight Rayearth 2", suggested("magicknightrayearth 2").first())
    }

    @Test fun aRomanNumeralMeetsItsDigitAndADifferentNumberIsNeverOffered() {
        assertEquals(listOf("Zork Quest II"), suggested("zork quest 2"))
        assertEquals(listOf("Final Fantasy VI"), suggested("Final Fantasy 6"))
    }

    private val series = listOf(
        "Mega Man 3", "Mega Man X3", "Sonic 3D Blast", "Sonic 3", "F1 2002", "F1 2003", "NBA 2K2", "NBA 2K3",
        "Street Fighter EX2 Plus", "Street Fighter EX Plus", "Street Fighter Alpha", "Street Fighter Alpha 2",
        "Street Fighter Alpha 3", "Zork Quest", "Zork Quest II", "Zork Quest III", "Mother 3", "Metroid Fusion", "Metroid Fusion (Japan)",
    )
    private val seriesSearch = TitleSearch(series)

    private fun inSeries(query: String) = suggested(query, seriesSearch, series)

    @Test fun lettersJoinedToANumberStayTheSameGameOnBothSides() {
        assertEquals(listOf("Mega Man X3"), inSeries("Mega Man X3"))
        assertEquals(listOf("Mega Man X3"), inSeries("MegaManX3"))
        assertEquals(listOf("Mega Man 3"), inSeries("Mega Man 3"))
        assertEquals(listOf("Sonic 3D Blast"), inSeries("Sonic 3D Blast"))
        assertEquals(listOf("F1 2002"), inSeries("F1 2002"))
        assertEquals(listOf("NBA 2K2"), inSeries("NBA 2K2"))
        assertEquals(listOf("Street Fighter EX2 Plus"), inSeries("Street Fighter EX2 Plus"))
    }

    @Test fun aNameWithoutANumberNeverOffersANumberedTitleAndTheOtherWayRound() {
        assertEquals(listOf("Zork Quest"), inSeries("Zork Qest"))
        assertEquals(listOf("Street Fighter Alpha"), inSeries("Street Fighter Alpa"))
        assertEquals(listOf("Zork Quest III"), inSeries("Zork Qest 3"))
        assertEquals(listOf("Street Fighter Alpha 2"), inSeries("Street Fighter Alpa II"))
    }

    @Test fun nothingThatMerelyLooksAlikeIsOffered() {
        assertEquals(listOf("Metroid Fusion"), inSeries("mtrd fusn"))
        assertEquals(emptyList<String>(), inSeries("mthr trd"))
    }

    @Test fun regionalReleasesOfOneNameAreOneSuggestion() {
        assertEquals(listOf("Metroid Fusion"), inSeries("Metroid Fusio"))
    }

    @Test fun aMissingWordFindsTheLongerTitleOnlyWhenTwoWordsAgree() {
        assertEquals(listOf("Advance Wars 2 - Black Hole Rising"), suggested("advance wars 2"))
        assertEquals(emptyList<String>(), suggested("Harbor"))
    }

    @Test fun theSameNameAloneAndNothingWeakAtAll() {
        assertEquals(1.0, search.suggest("zork quest").first().score, 0.0)
        assertEquals(listOf("Zork Quest"), suggested("zork quest"))
        assertEquals(emptyList<String>(), suggested("untitled_rom_final_v3_really_final_this_time"))
        assertEquals(emptyList<String>(), suggested(""))
        assertEquals(emptyList<String>(), suggested("zorq")) // a short name: only a near-identical title counts
    }

    @Test fun atMostThreeBestFirstAndNeverBelowTheFloor() {
        val many = listOf("Red", "Blue", "Green", "Gold", "Silver", "Crystal").map { "Dravon Knight Edition $it" }
        val hits = TitleSearch(many).suggest("Dravon Knight Editon")
        assertEquals(3, hits.size)
        assertTrue(hits.all { it.score >= TitleSearch.SUGGEST_FLOOR })
        assertEquals(hits, hits.sortedByDescending { it.score })
    }

    @Test fun aCheckpointThatThrowsStopsTheScan() {
        val big = TitleSearch((1..10_000).map { "Zork Title $it" })
        var checks = 0
        val stopped = runCatching { big.suggest("zork title") { if (++checks == 2) throw java.util.concurrent.CancellationException() } }
        assertTrue(stopped.exceptionOrNull() is java.util.concurrent.CancellationException)
    }

    private fun realList(system: String): List<String> {
        PackAssets.assumePacks()
        val assets = PackAssets.dir
        val bundled = object : BundledPacks {
            override fun readIndex(): String = File(assets, "packs.txt").readText()
            override fun open(fileName: String): InputStream = FileInputStream(File(assets, fileName))
        }
        val pack = checkNotNull(PackStore(temp.newFolder(), bundled, IdentifyLog { }).packFor(system)) { "no $system pack" }
        // Arcade lists sets, as Fix name… does (BIOS, device and mechanical ones left out).
        if (pack.kind == PackKind.ARCADE) {
            return (0 until pack.titleCount).map(pack::arcadeEntry).filterNot { it.isBios || it.isDevice || it.isMechanical }.map { it.title }
        }
        return List(pack.titleCount) { pack.titleName(it) }
    }

    @Test fun messyNamesFindTheirOfficialTitleInTheRealPacks() {
        val gba = realList("gba")
        val s = TitleSearch(gba)
        val expected = listOf(
            "mtrd fusn" to "Metroid Fusion", // missing vowels
            "Mario Kart Super Circut" to "Mario Kart - Super Circuit", // typo
            "Kirby Nightmare Dreamland" to "Kirby - Nightmare in Dream Land", // missing word, run-together
            "Golden Sun Lost Age" to "Golden Sun - The Lost Age", // missing word
            "Zelda Minish Cap" to "The Legend of Zelda - The Minish Cap", // missing words
            "Advance Wars II" to "Advance Wars 2 - Black Hole Rising", // roman numeral
            "Mother 3" to "Mother 3",
        )
        for ((query, title) in expected) assertEquals(query, title, suggested(query, s, gba).firstOrNull())
        assertEquals(emptyList<String>(), suggested("untitled_rom_final_v3_really_final_this_time", s, gba))
        val snes = realList("snes")
        val t = TitleSearch(snes)
        assertEquals("Super Mario World", suggested("Super Mario Wrld", t, snes).first())
        assertEquals("Chrono Trigger", suggested("Chrono Triger", t, snes).first())
        assertEquals(listOf("Final Fantasy III"), suggested("FinalFantasy3", t, snes))
        assertEquals(listOf("Street Fighter II Turbo"), suggested("Street Fighter 2 Turbo", t, snes))
    }

    @Test fun oneGameCostsMillisecondsEvenOnTheArcadeList() {
        val arcade = realList("arcade")
        val s = TitleSearch(arcade)
        s.suggest("warm up the scan")
        val started = System.nanoTime()
        repeat(10) { s.suggest("streetfighter2 champion edtion") }
        val ms = (System.nanoTime() - started) / 10 / 1e6
        println("TitleSearch.suggest over ${arcade.size} arcade titles: %.1f ms per game".format(ms))
        assertTrue("$ms ms per game", ms < 500)
    }
}
