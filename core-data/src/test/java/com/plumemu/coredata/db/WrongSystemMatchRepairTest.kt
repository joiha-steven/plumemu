package com.plumemu.coredata.db

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ScrapedGameDao.hashMatchesAskedWithSerial] and [ScrapedGameDao.forgetHashMatches], the scrape-rules
 * repair (library's `WrongSystemMatches` judges the answers' systems between them).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WrongSystemMatchRepairTest {
    private lateinit var db: PlumDatabase
    private val dao get() = db.scrapedGameDao()
    private var folderId = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private fun sql(statement: String, vararg args: Any) = db.openHelper.writableDatabase.execSQL(statement, args)

    private fun <T> query(statement: String, read: (android.database.Cursor) -> T): T =
        db.openHelper.readableDatabase.query(statement).use { it.moveToFirst(); read(it) }

    /** Games by document, each with its `fix_keys`, `id_method` and a stored row under its first key. */
    private suspend fun library(vararg games: Triple<String, String, String?>): Map<String, Long> {
        db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity("snes", "snes")), games.map { game(folderId, it.first) }))
        db.scanDao().finishScan(ScanFinish(folderId))
        val ids = db.scanDao().gamesInFolder(folderId).associate { it.documentId to it.id }
        games.forEach { (doc, keys, method) ->
            sql("UPDATE game SET fix_keys = ?, needs_identify = 0, title = 'Fish Eyes 3D', name_trust = 'SCRAPER_EXACT' WHERE id = ?", keys, ids.getValue(doc))
            method?.let { sql("UPDATE game SET id_method = ? WHERE id = ?", it, ids.getValue(doc)) }
            dao.insertOrReplace(row(keys.substringBefore('\n')))
        }
        return ids
    }

    private fun row(key: String) = ScrapedGameEntity(
        scrapeKey = key, gameId = null, outcome = ScrapeOutcome.MATCHED, screenScraperId = "900",
        matchedBy = ScrapeMatchedBy.HASH, scrapedAt = 1, detailsJson = "{\"details\":{\"system\":{\"id\":\"17\"}}}",
        title = "Fish Eyes 3D", year = null, publisher = null, players = null, genre = null, hasBoxFront = true,
    )

    @Test
    fun automaticHashRowsOfGamesWithACodeAreOfferedWithEveryGameSharingThem() = runTest {
        library(
            Triple("translation.sfc", "crc:00000001:1024\ncode:snes:ARFJ", "PRODUCT_CODE"),
            Triple("never-named.sfc", "crc:00000002:1024\ncode:snes:ARFK", null),
            Triple("code-only.sfc", "code:snes:ARFL", "FILE_NAME"),
            Triple("dat.sfc", "crc:00000003:1024\ncode:snes:ARFM", "CRC"),
            Triple("no-serial.sfc", "crc:00000004:1024", "FILE_NAME"),
            Triple("by-name.sfc", "crc:00000005:1024\ncode:snes:ARFN", null),
            Triple("mine.sfc", "crc:00000006:1024\ncode:snes:ARFP", null),
        )
        sql("UPDATE scraped_game SET matched_by = 'NAME' WHERE scrape_key = 'crc:00000005:1024'")
        sql("UPDATE scraped_game SET is_person_choice = 1 WHERE scrape_key = 'crc:00000006:1024'")
        // A copy of the translation without its code (another reader, or none): it shares the row.
        sql("UPDATE game SET fix_keys = 'crc:00000001:1024' WHERE document_id = 'no-serial.sfc'")

        val offered = dao.hashMatchesAskedWithSerial()

        assertEquals(
            "a DAT-identified game is judged too: the live check refuses a wrong system for it as well",
            listOf("code:snes:ARFL", "crc:00000001:1024", "crc:00000001:1024", "crc:00000002:1024", "crc:00000003:1024"),
            offered.map { it.scrapeKey }.sorted(),
        )
        assertEquals("the copy without a code comes with its key", setOf("crc:00000001:1024"), offered.filter { "code:" !in it.fixKeys }.map { it.scrapeKey }.toSet())
    }

    @Test
    fun forgettingDeletesOnlyAutomaticHashMatchesAndShowsTheFileNameAgain() = runTest {
        val ids = library(
            Triple("translation.sfc", "crc:00000001:1024\ncode:snes:ARFJ", null),
            Triple("mine.sfc", "crc:00000006:1024\ncode:snes:ARFP", null),
            Triple("other.sfc", "crc:00000007:1024\ncode:snes:ARFQ", null),
        )
        sql("UPDATE scraped_game SET is_person_choice = 1 WHERE scrape_key = 'crc:00000006:1024'")
        sql("UPDATE game SET name_trust = 'SCRAPER_PERSON_CHOICE', is_favorite = 1 WHERE id = ?", ids.getValue("mine.sfc"))

        // A person's choice passed by mistake is still kept.
        assertEquals(1, dao.forgetHashMatches(listOf("crc:00000001:1024", "crc:00000006:1024", "crc:00000001:1024")))

        assertNull(dao.get("crc:00000001:1024"))
        assertNotNull(dao.get("crc:00000006:1024"))
        assertNotNull(dao.get("crc:00000007:1024"))
        assertEquals("translation.sfc|FILE_NAME|1", query("SELECT title || '|' || name_trust || '|' || needs_identify FROM game WHERE id = ${ids.getValue("translation.sfc")}") { it.getString(0) })
        assertEquals("Fish Eyes 3D|SCRAPER_PERSON_CHOICE|1", query("SELECT title || '|' || name_trust || '|' || is_favorite FROM game WHERE id = ${ids.getValue("mine.sfc")}") { it.getString(0) })
        assertEquals("Fish Eyes 3D|SCRAPER_EXACT", query("SELECT title || '|' || name_trust FROM game WHERE id = ${ids.getValue("other.sfc")}") { it.getString(0) })

        assertEquals("again: nothing left", 0, dao.forgetHashMatches(listOf("crc:00000001:1024")))
        assertEquals("no keys", 0, dao.forgetHashMatches(emptyList()))
    }

    /** Scrape rules 4: rows some game without a code stands for, with every game sharing them; answers read by key. */
    @Test
    fun automaticHashRowsOfGamesWithoutACodeAreOfferedWithEveryGameSharingThem() = runTest {
        library(
            Triple("translation.sfc", "crc:00000011:1024", null),
            Triple("copy.sfc", "crc:00000012:1024", null),
            Triple("coded.sfc", "crc:00000014:1024\ncode:snes:ARFR", null),
            Triple("named.sfc", "crc:00000015:1024", null),
            Triple("mine.sfc", "crc:00000016:1024", null),
        )
        sql("UPDATE scraped_game SET matched_by = 'NAME' WHERE scrape_key = 'crc:00000015:1024'")
        sql("UPDATE scraped_game SET is_person_choice = 1 WHERE scrape_key = 'crc:00000016:1024'")
        // A coded copy of the translation shares its row and comes with it.
        sql("UPDATE game SET fix_keys = 'crc:00000011:1024' || char(10) || 'code:snes:ARFS' WHERE document_id = 'copy.sfc'")

        val offered = dao.hashMatchGamesWithoutCode()

        assertEquals(listOf("copy.sfc", "translation.sfc"), offered.map { it.fileName }.sorted())
        assertEquals(setOf("crc:00000011:1024"), offered.map { it.scrapeKey }.toSet())
        assertEquals(listOf("crc:00000011:1024"), dao.detailsOf(listOf("crc:00000011:1024", "crc:00000015:1024", "crc:00000016:1024")).map { it.scrapeKey })
    }

    @Test
    fun aFileTitleIsWrittenOnlyWhereThereIsNoneAndTheNameShownAgainOnlyUnderFileName() = runTest {
        val ids = library(
            Triple("empty.sfc", "crc:00000021:1024", null),
            Triple("kept.sfc", "crc:00000022:1024", null),
        )
        sql("UPDATE game SET file_title = '', file_sort_name = '', file_regions = '', name_trust = 'FILE_NAME' WHERE document_id = 'empty.sfc'")
        sql("UPDATE game SET name_trust = 'PERSON_FIX' WHERE document_id = 'kept.sfc'")

        assertEquals(listOf(ids.getValue("empty.sfc")), dao.gamesWithoutFileTitle(listOf("crc:00000021:1024", "crc:00000022:1024")).map { it.id })
        assertEquals(listOf(ids.getValue("empty.sfc")), dao.gamesShownWithoutFileTitle().map { it.id })
        assertEquals(1, dao.fillFileName(ids.getValue("empty.sfc"), "Empty", "empty", "US"))
        assertEquals("never over a stored file title", 0, dao.fillFileName(ids.getValue("kept.sfc"), "X", "x", ""))
        assertEquals(1, dao.showFileNameAgain(ids.getValue("empty.sfc")))
        assertEquals("a person's name is never replaced", 0, dao.showFileNameAgain(ids.getValue("kept.sfc")))

        assertEquals("Empty|empty|US|1", query("SELECT title || '|' || sort_name || '|' || regions || '|' || needs_identify FROM game WHERE id = ${ids.getValue("empty.sfc")}") { it.getString(0) })
        assertEquals("Fish Eyes 3D|PERSON_FIX|0", query("SELECT title || '|' || name_trust || '|' || needs_identify FROM game WHERE id = ${ids.getValue("kept.sfc")}") { it.getString(0) })
        assertEquals("nothing left", emptyList<GameFileName>(), dao.gamesShownWithoutFileTitle())
    }
}
