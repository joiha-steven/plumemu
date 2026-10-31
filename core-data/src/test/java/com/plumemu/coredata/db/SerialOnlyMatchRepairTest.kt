package com.plumemu.coredata.db

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ScrapedGameDao.forgetSerialOnlyHashMatches], the one-time repair after the scraper started
 * checking a serial answer's system: only the serial-only hash matches the run chose
 * go, the name a match gave goes back to the file's, and nothing the person owns is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SerialOnlyMatchRepairTest {
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

    /** One named game per entry: its document and `fix_keys`; the shown name is the file's. */
    private suspend fun insertNamed(vararg docsAndKeys: Pair<String, String>): Map<String, Long> {
        db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity("snes", "snes")), docsAndKeys.map { game(folderId, it.first) }))
        db.scanDao().finishScan(ScanFinish(folderId))
        val ids = db.scanDao().gamesInFolder(folderId).associate { it.documentId to it.id }
        docsAndKeys.forEach { (doc, keys) -> sql("UPDATE game SET fix_keys = ?, needs_identify = 0 WHERE id = ?", keys, ids.getValue(doc)) }
        return ids
    }

    private fun row(key: String, by: ScrapeMatchedBy? = ScrapeMatchedBy.HASH, outcome: ScrapeOutcome = ScrapeOutcome.MATCHED, personChoice: Boolean = false) =
        ScrapedGameEntity(
            scrapeKey = key,
            gameId = null,
            outcome = outcome,
            screenScraperId = "1245".takeIf { outcome == ScrapeOutcome.MATCHED },
            matchedBy = by.takeIf { outcome == ScrapeOutcome.MATCHED },
            isPersonChoice = personChoice,
            scrapedAt = NOW,
            detailsJson = "{}".takeIf { outcome == ScrapeOutcome.MATCHED },
            title = "Zork Quest".takeIf { outcome == ScrapeOutcome.MATCHED },
            year = null,
            publisher = null,
            players = null,
            genre = null,
            hasBoxFront = outcome == ScrapeOutcome.MATCHED,
        )

    private suspend fun pendingDocs(): List<String> =
        dao.pending(NOW - ScrapedGameDao.NO_MATCH_RETRY_MILLIS, null, 100).map { it.documentId }.sorted()

    /**
     * Rows like a real library: a serial-only hash match (with a copy sharing its key, the
     * name it gave, a favourite and play time), one under a CRC key, a file name and size match
     * under a code key, a person's choice under a code key and a no-match under a code key.
     */
    private suspend fun realLibraryLikeRows(): Map<String, Long> {
        val ids = insertNamed(
            "serial.sfc" to "code:snes:ZZZE",
            "serial-rev1.sfc" to "code:snes:ZZZE",
            "crc.sfc" to "crc:1a2b3c4d:1024\ncode:snes:ZZZF",
            "size.sfc" to "code:snes:ZZZG",
            "mine.sfc" to "code:snes:ZZZH",
            "none.sfc" to "code:snes:ZZZJ",
        )
        dao.insertOrReplace(row("code:snes:ZZZE"))
        dao.insertOrReplace(row("crc:1a2b3c4d:1024"))
        dao.insertOrReplace(row("code:snes:ZZZG", by = ScrapeMatchedBy.FILE_NAME_SIZE))
        dao.insertOrReplace(row("code:snes:ZZZH", personChoice = true))
        dao.insertOrReplace(row("code:snes:ZZZJ", outcome = ScrapeOutcome.NO_MATCH))
        // The serial match named both copies; the person's choice named its game too.
        for (doc in listOf("serial.sfc", "serial-rev1.sfc", "crc.sfc")) {
            sql("UPDATE game SET title = 'Zork Quest', sort_name = 'zork quest', name_trust = 'SCRAPER_EXACT' WHERE id = ?", ids.getValue(doc))
        }
        sql("UPDATE game SET title = 'Zork Quest', name_trust = 'SCRAPER_PERSON_CHOICE' WHERE id = ?", ids.getValue("mine.sfc"))
        sql("UPDATE game SET is_favorite = 1 WHERE id = ?", ids.getValue("serial.sfc"))
        sql("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES (?, ?, 60)", ids.getValue("serial.sfc"), NOW)
        sql("INSERT INTO name_fix (fix_key, game_id, kind, title, title_source, fixed_at) VALUES ('crc:1a2b3c4d:1024', NULL, 'OWN_NAME', 'Kept Fix', NULL, 1)")
        assertEquals("everything is stored before the repair", emptyList<String>(), pendingDocs())
        return ids
    }

    @Test
    fun onlySerialOnlyHashMatchesAreForgottenAndComeBackAsNeverAsked() = runTest {
        val ids = realLibraryLikeRows()

        assertEquals("one stored row: both copies share it", 1, dao.forgetSerialOnlyHashMatches())

        assertNull(dao.get("code:snes:ZZZE"))
        assertNotNull(dao.get("crc:1a2b3c4d:1024"))
        assertNotNull(dao.get("code:snes:ZZZG"))
        assertTrue("a person's choice stays", dao.get("code:snes:ZZZH")!!.isPersonChoice)
        assertEquals(ScrapeOutcome.NO_MATCH, dao.get("code:snes:ZZZJ")!!.outcome)

        // The name the wrong answer may have given is the file's again, to be named by the next pass.
        for (doc in listOf("serial.sfc", "serial-rev1.sfc")) {
            val (title, trust, needsIdentify) = query("SELECT title, name_trust, needs_identify FROM game WHERE id = ${ids.getValue(doc)}") {
                Triple(it.getString(0), it.getString(1), it.getInt(2))
            }
            assertEquals(doc.substringBefore('.'), title.substringBefore('.'))
            assertEquals("FILE_NAME", trust)
            assertEquals(1, needsIdentify)
        }
        assertEquals("a name from a row that stays is kept", "SCRAPER_EXACT", query("SELECT name_trust FROM game WHERE id = ${ids.getValue("crc.sfc")}") { it.getString(0) })
        assertEquals("SCRAPER_PERSON_CHOICE", query("SELECT name_trust FROM game WHERE id = ${ids.getValue("mine.sfc")}") { it.getString(0) })

        // Once named again, they are offered to the run as never asked: no ask_again mark.
        sql("UPDATE game SET needs_identify = 0")
        assertEquals(listOf("serial-rev1.sfc", "serial.sfc"), pendingDocs())

        // The person's data stays.
        assertEquals(1, query("SELECT is_favorite FROM game WHERE id = ${ids.getValue("serial.sfc")}") { it.getInt(0) })
        assertEquals(1, query("SELECT COUNT(*) FROM play") { it.getInt(0) })
        assertEquals(1, query("SELECT COUNT(*) FROM name_fix") { it.getInt(0) })
        assertEquals(0, query("SELECT COUNT(*) FROM scraped_game WHERE ask_again = 1") { it.getInt(0) })
    }

    @Test
    fun aSecondRepairAndAnEmptyLibraryTouchNothing() = runTest {
        assertEquals("empty database", 0, dao.forgetSerialOnlyHashMatches())
        realLibraryLikeRows()
        assertEquals(1, dao.forgetSerialOnlyHashMatches())
        sql("UPDATE game SET needs_identify = 0")
        assertEquals(0, dao.forgetSerialOnlyHashMatches())
        assertEquals(4, query("SELECT COUNT(*) FROM scraped_game") { it.getInt(0) })
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
