package com.plumemu.library.scrape

import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.library.scan.FakeFolderPermissions
import com.plumemu.library.scan.LibraryScanner
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.boxFront
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.matched
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.IdName
import com.plumemu.scraper.ScrapeResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [LibraryScanner.askAgainForNewScrapeRules]: after the scraper started checking a serial answer's
 * system, the games matched by a serial alone are asked again once, without `fresh`, so the
 * scraper's cached answer is judged again and saved pictures are reused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrapeRulesRepairTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var lib: ScrapeTestLibrary

    @Before
    fun setUp() {
        lib = ScrapeTestLibrary(temp.newFile("plumemu.db").also { it.delete() })
    }

    @After
    fun tearDown() = lib.close()

    private fun scanner() = LibraryScanner(lib.db, FakeFolderPermissions(), { error("no tree in this test") }, emptyList())

    @Test
    fun aNewRulesVersionMakesNoMatchesDueOnceAndLeavesMatchesAlone() = runTest {
        val source = lib.source()
        lib.add(lib.game("miss.sfc"), lib.game("hit.sfc", fixKeys = "crc:snes:1"))
        source.pending(100).forEach { request ->
            if (request.rom.fileName == "miss.sfc") source.deliver(request, ScrapeResult.NoMatch, emptyList())
            else source.deliver(request, matched(), listOf(boxFront()))
        }
        assertEquals("both stored, nothing due yet", emptyList<Any>(), source.pending(100))

        assertEquals("the one no-match", 1, scanner().askAgainForNewScrapeRules(1))
        assertEquals(listOf("miss.sfc"), source.pending(100).map { it.rom.fileName })

        assertEquals("a current version marks nothing", 0, scanner().askAgainForNewScrapeRules(SCRAPE_RULES_VERSION))
        source.pending(100).forEach { source.deliver(it, ScrapeResult.NoMatch, emptyList()) }
        assertEquals("asked again and stored: not due again", emptyList<Any>(), source.pending(100))
    }

    @Test
    fun serialOnlyMatchesAreAskedAgainOnceAndNotFresh() = runTest {
        val source = lib.source()
        lib.add(
            lib.game("serial.sfc", fixKeys = "code:snes:ZZZE", trust = NameTrustNames.FILE_NAME),
            lib.game("crc.sfc"),
            lib.game("size.sfc", fixKeys = "code:snes:ZZZF"),
        )
        source.pending(100).forEach { source.deliver(it, matched(), listOf(boxFront())) }
        // As the run stores them with fingerprints: a serial or CRC sent is HASH, size alone FILE_NAME_SIZE.
        lib.sql("UPDATE scraped_game SET matched_by = CASE WHEN scrape_key = 'code:snes:ZZZF' THEN 'FILE_NAME_SIZE' ELSE 'HASH' END")
        assertEquals(emptyList<Any>(), source.pending(100))

        assertEquals("only the serial-only hash match", 1, scanner().askAgainForNewScrapeRules(0))
        // The scan that follows names it again; then it is offered as never asked.
        lib.sql("UPDATE game SET needs_identify = 0")
        val again = source.pending(100)
        assertEquals(listOf("serial.sfc"), again.map { it.rom.fileName })
        assertTrue("the cached answer and saved pictures are used", again.none { it.fresh })

        assertEquals("a version already current marks nothing", 0, scanner().askAgainForNewScrapeRules(SCRAPE_RULES_VERSION))
    }

    private fun answeredOn(key: String, screenScraperSystem: String?) = lib.sql(
        "UPDATE scraped_game SET details_json = ? WHERE scrape_key = ?",
        GameDetails(system = screenScraperSystem?.let { IdName(it, "System $it") }).toJson(), key,
    )

    private fun <T> query(statement: String, read: (android.database.Cursor) -> T): T =
        lib.db.openHelper.readableDatabase.query(statement).use { it.moveToFirst(); read(it) }

    private fun shown(doc: String): Triple<String, String, Int> =
        query("SELECT title, name_trust, needs_identify FROM game WHERE document_id = '$doc'") { Triple(it.getString(0), it.getString(1), it.getInt(2)) }

    /**
     * On an AYN Thor and a Retroid Pocket Nova: a SNES fan translation's CRC is in no database, its game
     * code is a 3DS game's, and the crc+serial lookup stored the 3DS game as a hash match under the
     * `crc:` key ("Fish Eyes 3D" for Star Ocean). Version 3 forgets what the live check now refuses:
     * an answer on neither the asked system nor the system of the code's format, for every game
     * sharing the row. Folders that hold another system's cartridges keep their right answers.
     */
    @Test
    fun version3ForgetsHashMatchesAnsweredOnAnotherSystemOnly() = runTest {
        val source = lib.source()
        // Document, system, fix keys, the answer's ScreenScraper system.
        val games = listOf(
            listOf("star-ocean-t-en.sfc", "snes", "crc:aaaa0001:1024\ncode:SNES:ARFJ", "17"),
            listOf("right.sfc", "snes", "crc:aaaa0002:1024\ncode:SNES:ABCE", "4"),
            listOf("dat.sfc", "snes", "crc:aaaa0003:1024\ncode:SNES:ABCF", "4"),
            listOf("no-serial.sfc", "snes", "crc:aaaa0004:1024", "17"),
            listOf("no-system.sfc", "snes", "crc:aaaa0005:1024\ncode:SNES:ABCG", null),
            listOf("mine.sfc", "snes", "crc:aaaa0006:1024\ncode:SNES:ABCH", "17"),
            listOf("by-name.sfc", "snes", "crc:aaaa0007:1024\ncode:SNES:ABCJ", "17"),
            // A .gbc in gba/ (12) answered as Game Boy Color, a Game Boy file in
            // sgb/ (127), an .sfc in snesmsu1/ (210) answered as SNES, a .md in genesismsu/ (301) as Mega Drive.
            listOf("zork.gbc", "gba", "crc:aaaa0010:1024\ncode:GB:AZQE", "10"),
            listOf("zork-sgb.gb", "sgb", "crc:aaaa0011:1024\ncode:GB:AZQF", "9"),
            listOf("zork-msu.sfc", "snesmsu1", "crc:aaaa0012:1024\ncode:SNES:AZQG", "4"),
            listOf("zork-msu.md", "genesismsu", "crc:aaaa0013:1024\ncode:MEGADRIVE:T-99999", "1"),
            // One content in snes/ and snesmsu1/: the MSU-1 answer is right for the second copy, so it stays.
            listOf("shared.sfc", "snes", "crc:aaaa0020:1024\ncode:SNES:AZQH", "210"),
            listOf("shared-msu.sfc", "snesmsu1", "crc:aaaa0020:1024\ncode:SNES:AZQH", "210"),
            // The same, answered as a 3DS game: wrong for both copies, forgotten.
            listOf("both-wrong.sfc", "snes", "crc:aaaa0021:1024\ncode:SNES:AZQJ", "17"),
            listOf("both-wrong-msu.sfc", "snesmsu1", "crc:aaaa0021:1024\ncode:SNES:AZQJ", "17"),
        )
        lib.add(*games.map { (doc, system, keys) -> lib.game(doc!!, fixKeys = keys!!, system = system!!) }.toTypedArray())
        source.pending(100).forEach { source.deliver(it, matched(), listOf(boxFront())) }
        // As the run stores them with fingerprints (these games have none): a serial or CRC sent is HASH.
        lib.sql("UPDATE scraped_game SET matched_by = 'HASH'")
        lib.sql("UPDATE scraped_game SET matched_by = 'NAME' WHERE scrape_key = 'crc:aaaa0007:1024'")
        lib.sql("UPDATE scraped_game SET is_person_choice = 1 WHERE scrape_key = 'crc:aaaa0006:1024'")
        games.forEach { (_, _, keys, answered) -> answeredOn(keys!!.substringBefore('\n'), answered) }
        // The names the stored answers gave.
        lib.sql("UPDATE game SET title = 'Fish Eyes 3D', name_trust = 'SCRAPER_EXACT' WHERE document_id = 'star-ocean-t-en.sfc'")
        lib.sql("UPDATE game SET title = 'Dat Quest', name_trust = 'SCRAPER_EXACT' WHERE document_id = 'dat.sfc'")
        lib.sql("UPDATE game SET title = 'My Pick', name_trust = 'SCRAPER_PERSON_CHOICE', is_favorite = 1 WHERE document_id = 'mine.sfc'")
        lib.sql("UPDATE game SET id_method = CASE WHEN document_id = 'dat.sfc' THEN 'CRC' ELSE 'PRODUCT_CODE' END")
        lib.played(query("SELECT id FROM game WHERE document_id = 'mine.sfc'") { it.getLong(0) }, ScrapeTestLibrary.NOW)
        assertEquals(emptyList<Any>(), source.pending(100))

        // Version 3 alone judges only games whose lookup sent a serial: no-serial.sfc keeps its row.
        assertEquals("the translation's row and the row wrong for both copies", 2, WrongSystemMatches.forget(lib.db))
        val dao = lib.db.scrapedGameDao()
        assertNotNull("version 3 keeps the code-less game's row", dao.get("crc:aaaa0004:1024"))
        // From 2, version 4 runs too: no-serial.sfc's 3DS answer goes as well (a cartridge with no code left
        // is judged as if it had sent one, since the naming pass may have dropped it).
        assertEquals("the code-less one", 1, scanner().askAgainForNewScrapeRules(2))

        assertNull(dao.get("crc:aaaa0001:1024"))
        assertNull(dao.get("crc:aaaa0021:1024"))
        assertNull(dao.get("crc:aaaa0004:1024"))
        for (kept in listOf("0002", "0003", "0005", "0006", "0007", "0010", "0011", "0012", "0013", "0020")) {
            assertNotNull("kept: $kept", dao.get("crc:aaaa$kept:1024"))
        }
        // The wrong title goes at once (Home lists read game.title); the scan's naming pass names it next.
        assertEquals(Triple("star-ocean-t-en", NameTrustNames.FILE_NAME, 1), shown("star-ocean-t-en.sfc"))
        assertEquals(Triple("Dat Quest", "SCRAPER_EXACT", 0), shown("dat.sfc"))
        assertEquals(Triple("My Pick", NameTrustNames.SCRAPER_PERSON_CHOICE, 0), shown("mine.sfc"))
        assertTrue("the person's pick stays theirs", dao.get("crc:aaaa0006:1024")!!.isPersonChoice)
        assertEquals("favourite and play time stay", 1, query("SELECT COUNT(*) FROM game JOIN play ON play.game_id = game.id WHERE game.is_favorite = 1") { it.getInt(0) })

        // Named again by the scan, they are offered as never asked: the cached answer is judged by the new rule.
        lib.sql("UPDATE game SET needs_identify = 0")
        val again = source.pending(100)
        assertEquals("one request per forgotten key", listOf("both-wrong.sfc", "no-serial.sfc", "star-ocean-t-en.sfc"), again.map { it.rom.fileName }.sorted())
        assertTrue(again.none { it.fresh })

        assertEquals("a current version forgets nothing", 0, scanner().askAgainForNewScrapeRules(SCRAPE_RULES_VERSION))
        assertEquals("run again from 2, nothing is left to forget", 0, scanner().askAgainForNewScrapeRules(2))
    }

    /** The three games of the two tests below: a `code:` match on its own system, a wrong-system and a right-system crc+serial match. */
    private suspend fun libraryWithBothKinds() {
        val source = lib.source()
        lib.add(
            lib.game("serial-only.sfc", fixKeys = "code:snes:ZZZE"),
            lib.game("translation.sfc", fixKeys = "crc:aaaa0001:1024\ncode:snes:ARFJ"),
            lib.game("right.sfc", fixKeys = "crc:aaaa0002:1024\ncode:snes:ABCE"),
        )
        source.pending(100).forEach { source.deliver(it, matched(), listOf(boxFront())) }
        lib.sql("UPDATE scraped_game SET matched_by = 'HASH'")
        answeredOn("code:snes:ZZZE", "4")
        answeredOn("crc:aaaa0001:1024", "17")
        answeredOn("crc:aaaa0002:1024", "4")
    }

    @Test
    fun aLibraryFromBeforeVersion1GetsBothRepairs() = runTest {
        libraryWithBothKinds()

        assertEquals("the serial-only match (version 1) and the wrong-system one (version 3)", 2, scanner().askAgainForNewScrapeRules(0))

        val dao = lib.db.scrapedGameDao()
        assertNull(dao.get("code:snes:ZZZE"))
        assertNull(dao.get("crc:aaaa0001:1024"))
        assertNotNull(dao.get("crc:aaaa0002:1024"))
    }

    /**
     * A library at version 2 had its serial-only matches forgotten once already; those stored since
     * were judged by the system check. Forgetting them again would only spend ScreenScraper quota.
     */
    @Test
    fun aLibraryAtVersion2KeepsItsCodeMatchesAndLosesOnlyTheWrongSystemOne() = runTest {
        libraryWithBothKinds()

        assertEquals("only the wrong-system match", 1, scanner().askAgainForNewScrapeRules(2))

        val dao = lib.db.scrapedGameDao()
        assertNotNull("the code: match stays", dao.get("code:snes:ZZZE"))
        assertNull(dao.get("crc:aaaa0001:1024"))
        assertNotNull(dao.get("crc:aaaa0002:1024"))
        assertEquals("from version 1 it stays too", 0, scanner().askAgainForNewScrapeRules(1))
        assertNotNull(dao.get("code:snes:ZZZE"))
    }

    private fun shownName(doc: String): List<String> =
        query("SELECT title, name_trust, needs_identify, file_title FROM game WHERE document_id = '${doc.replace("'", "''")}'") {
            listOf(it.getString(0), it.getString(1), it.getInt(2).toString(), it.getString(3))
        }

    /**
     * On an AYN Thor: "Star Ocean (Japan) [T-En by DeJap v1.0].sfc" still showed "Fish Eyes
     * 3D, 2011, Marvelous" after the version 3 repair. The earlier naming change (identify's
     * `HackNames`) drops a translation's cartridge code from its result, so the naming pass that ran
     * before the repair wrote its `fix_keys` without the `code:` line; the repair then read "no serial
     * was sent" and kept the 3DS answer the crc+serial lookup had stored. Version 4 judges every
     * cartridge game with no code left as if it had sent one: the code is also dropped without a tag
     * in the file name (identify's word rule, "Pokemon Radical Red"), which the database cannot tell.
     * Look-alikes keep their rows.
     */
    @Test
    fun version4ForgetsTheWrongSystemMatchOfATranslationWhoseCodeTheNamingPassDropped() = runTest {
        val source = lib.source()
        val star = "Star Ocean (Japan) [T-En by DeJap v1.0].sfc"
        // File name, fix keys (no code: the naming pass dropped it), the answer's ScreenScraper system, the folder's system.
        val games = listOf(
            listOf(star, "crc:bbbb0001:1024", "17"),
            listOf("Zork (Japan) [T-En by Zork].sfc", "crc:bbbb0002:1024", "4"),
            // A hack with no tag, its code dropped by the word rule.
            listOf("Zork Board (USA).sfc", "crc:bbbb0003:1024", "17"),
            listOf("Zork Pick (Japan) [T-En].sfc", "crc:bbbb0004:1024", "17"),
            listOf("Zork Named (Japan) [T-En].sfc", "crc:bbbb0005:1024", "17"),
            listOf("Zork Fixed (Japan) (Hack).sfc", "crc:bbbb0006:1024", "17"),
            listOf("Zork Code (Japan) [T-En].sfc", "crc:bbbb0007:1024\ncode:SNES:AZQK", "4"),
            // A Game Boy cartridge in gba/ answered as Game Boy: right on its format's system.
            listOf("Zork Pocket (USA).gb", "crc:bbbb0008:1024", "9", "gba"),
            // A disc has no cartridge format: its answer cannot be judged, it stays.
            listOf("Zork Disc (USA).cue", "crc:bbbb0009:1024", "17", "psx"),
            // An archive may hold another format (a .gb in gba/): it cannot be judged, it stays.
            listOf("Tetris (World).zip", "crc:bbbb0010:1024", "9", "gba"),
        )
        lib.add(*games.map { lib.game(it[0], fixKeys = it[1], system = it.getOrElse(3) { "snes" }) }.toTypedArray())
        source.pending(100).forEach { source.deliver(it, matched(), listOf(boxFront())) }
        lib.sql("UPDATE scraped_game SET matched_by = 'HASH'")
        lib.sql("UPDATE scraped_game SET matched_by = 'NAME' WHERE scrape_key = 'crc:bbbb0005:1024'")
        lib.sql("UPDATE scraped_game SET is_person_choice = 1 WHERE scrape_key = 'crc:bbbb0004:1024'")
        games.forEach { answeredOn(it[1].substringBefore('\n'), it[2]) }
        lib.sql("UPDATE game SET title = 'Fish Eyes 3D', name_trust = 'SCRAPER_EXACT' WHERE document_id = ?", star)
        lib.sql("UPDATE game SET title = 'My Pick', name_trust = 'SCRAPER_PERSON_CHOICE' WHERE document_id = 'Zork Pick (Japan) [T-En].sfc'")
        lib.sql("UPDATE game SET title = 'My Own Name', name_trust = 'PERSON_FIX' WHERE document_id = 'Zork Fixed (Japan) (Hack).sfc'")
        assertEquals(emptyList<Any>(), source.pending(100))

        assertEquals("the translation's, the untagged hack's and the fixed game's rows", 3, scanner().askAgainForNewScrapeRules(3))

        val dao = lib.db.scrapedGameDao()
        assertNull("the 3DS answer (title, year, publisher, pictures) is gone", dao.get("crc:bbbb0001:1024"))
        assertNull("no tag in the file name, the code dropped all the same", dao.get("crc:bbbb0003:1024"))
        assertNull("wrong box art goes even under a person's own name", dao.get("crc:bbbb0006:1024"))
        for (kept in listOf("0002", "0004", "0005", "0007", "0008", "0009", "0010")) assertNotNull("kept: $kept", dao.get("crc:bbbb$kept:1024"))
        val fileName = star.substringBeforeLast('.')
        assertEquals(listOf(fileName, NameTrustNames.FILE_NAME, "1", fileName), shownName(star))
        assertEquals("the person's name stays", listOf("My Own Name", NameTrustNames.PERSON_FIX), shownName("Zork Fixed (Japan) (Hack).sfc").take(2))
        assertEquals("the pick stays", listOf("My Pick", NameTrustNames.SCRAPER_PERSON_CHOICE), shownName("Zork Pick (Japan) [T-En].sfc").take(2))

        lib.sql("UPDATE game SET needs_identify = 0")
        val again = source.pending(100)
        assertEquals(listOf(star, "Zork Board (USA).sfc", "Zork Fixed (Japan) (Hack).sfc"), again.map { it.rom.fileName }.sorted())
        assertTrue(again.none { it.fresh })
        assertEquals("a current version forgets nothing", 0, scanner().askAgainForNewScrapeRules(SCRAPE_RULES_VERSION))
        assertEquals("run again from 3, nothing is left to forget", 0, scanner().askAgainForNewScrapeRules(3))
    }

    /**
     * A game named from a wrong-system match whose `file_title` is empty kept the wrong title after the
     * version 3 repair: `forgetHashMatches` keeps `title` when `file_title` is '' but sets `FILE_NAME`,
     * and the naming pass never replaces a `FILE_NAME` title with a file-name result (a tie keeps the
     * stored name). The repair now writes the name from the file name first.
     */
    @Test
    fun aWrongSystemMatchOfAGameWithNoFileTitleShowsTheFileName() = runTest {
        val source = lib.source()
        val star = "Star Ocean (Japan) [T-En by DeJap v1.0].sfc"
        lib.add(lib.game(star, fixKeys = "crc:cccc0001:1024\ncode:SNES:ARFJ"))
        source.pending(100).forEach { source.deliver(it, matched(), listOf(boxFront())) }
        lib.sql("UPDATE scraped_game SET matched_by = 'HASH'")
        answeredOn("crc:cccc0001:1024", "17")
        lib.sql(
            "UPDATE game SET title = 'Fish Eyes 3D', sort_name = 'fish eyes 3d', name_trust = 'SCRAPER_EXACT', " +
                "file_title = '', file_sort_name = '', file_regions = ''",
        )

        assertEquals(1, scanner().askAgainForNewScrapeRules(2))

        assertNull(lib.db.scrapedGameDao().get("crc:cccc0001:1024"))
        assertEquals(listOf("Star Ocean", NameTrustNames.FILE_NAME, "1", "Star Ocean"), shownName(star))
        assertEquals("star ocean", query("SELECT sort_name FROM game") { it.getString(0) })
    }

    /**
     * Libraries that ran version 3 with an empty `file_title` kept the wrong title under `FILE_NAME`
     * with no stored match. Version 4 shows those games' file name and names them again; a game whose
     * title already is its file's name, a person's name and every game with a file title stay.
     */
    @Test
    fun aThirtyTwoXAnswerOfADotThirtyTwoXInMegadriveIsNotForgotten() = runTest {
        val source = lib.source()
        lib.add(
            lib.game("Zork 32X (USA).32x", fixKeys = "crc:dddd0001:1024\ncode:MEGADRIVE:GM4567", system = "megadrive"),
            lib.game("Zork 32X Bare (USA).32x", fixKeys = "crc:dddd0002:1024", system = "megadrive"),
        )
        source.pending(100).forEach { source.deliver(it, matched(), listOf(boxFront())) }
        lib.sql("UPDATE scraped_game SET matched_by = 'HASH'")
        answeredOn("crc:dddd0001:1024", "19")
        answeredOn("crc:dddd0002:1024", "19")

        // A `.32x` in megadrive/ reads a Mega Drive code and ScreenScraper answers it on 32X (19): right, 0.0.6 accepted it.
        assertEquals(0, scanner().askAgainForNewScrapeRules(2))

        val dao = lib.db.scrapedGameDao()
        assertNotNull(dao.get("crc:dddd0001:1024"))
        assertNotNull(dao.get("crc:dddd0002:1024"))
    }

    @Test
    fun version4ResetsGamesTheVersion3RepairLeftWithTheWrongTitle() = runTest {
        lib.add(
            lib.game("Star Ocean (Japan) [T-En by DeJap v1.0].sfc", title = "Fish Eyes 3D"),
            lib.game("Zork Quest (USA).sfc", title = "Zork Quest"),
            lib.game("Zork Fixed (USA).sfc", title = "My Own Name", trust = NameTrustNames.PERSON_FIX),
            lib.game("Zork Clash (USA).sfc", title = "Zork Clash (Set 2)"),
        )
        lib.sql("UPDATE game SET file_title = '', file_sort_name = '', file_regions = '' WHERE document_id != 'Zork Clash (USA).sfc'")
        lib.sql("UPDATE game SET file_title = 'Zork Clash' WHERE document_id = 'Zork Clash (USA).sfc'")

        assertEquals("the one left with the wrong title", 1, scanner().askAgainForNewScrapeRules(3))

        assertEquals(listOf("Star Ocean", NameTrustNames.FILE_NAME, "1", "Star Ocean"), shownName("Star Ocean (Japan) [T-En by DeJap v1.0].sfc"))
        assertEquals("already its file's name: untouched", listOf("Zork Quest", NameTrustNames.FILE_NAME, "0", ""), shownName("Zork Quest (USA).sfc"))
        assertEquals(listOf("My Own Name", NameTrustNames.PERSON_FIX, "0", ""), shownName("Zork Fixed (USA).sfc"))
        assertEquals(listOf("Zork Clash (Set 2)", NameTrustNames.FILE_NAME, "0", "Zork Clash"), shownName("Zork Clash (USA).sfc"))
        assertEquals("once", 0, scanner().askAgainForNewScrapeRules(3))
    }
}
