package com.plumemu.library.scrape

import com.plumemu.coredata.db.NameFixKind
import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.coredata.db.ScrapeMatchedBy
import com.plumemu.coredata.db.ScrapedPicture
import com.plumemu.coredata.db.ScrapedPictures
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.CrcSource
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.library.naming.FingerprintCodec
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.boxFront
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.matched
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.support
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.RomHints
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.work.ScrapePass
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a request carries and what a delivered result writes: the row, its pictures, and the shown name. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrapeDeliverTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var lib: ScrapeTestLibrary

    @Before
    fun setUp() {
        lib = ScrapeTestLibrary(temp.newFile("plumemu.db").also { it.delete() })
    }

    @After
    fun tearDown() = lib.close()

    /** Adds one game, delivers [result] for it and returns its row id. */
    private val withCrc = FingerprintCodec.encode(
        Fingerprints(size = 1024, container = Container.NONE, contentSize = 1024, crc32 = 0xABCDL, crcSource = CrcSource.WHOLE_FILE),
    )

    private suspend fun deliverTo(trust: String, result: ScrapeResult, title: String = "zork quest (file)", fingerprint: String? = withCrc): Long {
        val (id) = lib.add(lib.game("zq.sfc", fixKeys = "crc:0000abcd:1024", title = title, trust = trust, fingerprint = fingerprint))
        val source = lib.source()
        source.deliver(source.pending(1).single(), result, listOf(boxFront()))
        return id
    }

    private suspend fun shown(id: Long) = lib.db.gameDao().game(id)!!.let { it.title to it.nameTrust }

    @Test
    fun aRequestCarriesTheDocumentHintsRegionsAndLanguages() = runTest {
        val zipped = Fingerprints(size = 600, container = Container.ZIP, innerName = "zq.sfc", entryCount = 1, contentSize = 1_048_576, crc32 = 0xABCDL, crcSource = CrcSource.ZIP_DIRECTORY)
        lib.add(lib.game("zq.zip", regions = "FRA,EUR", fingerprint = FingerprintCodec.encode(zipped)))
        lib.preferred = "jp"
        val request = lib.source().pending(1).single()
        assertEquals("content://test/tree/roms/document/zq.zip", request.rom.key)
        assertEquals("zq.zip", request.rom.fileName)
        assertEquals(1024L, request.rom.sizeBytes)
        assertEquals(7L, request.rom.lastModifiedMillis)
        assertEquals("snes", request.systemId)
        assertEquals("zq", request.title)
        assertEquals(listOf("fr", "eu", "jp", "wor", "ss"), request.regions)
        assertEquals(listOf("fr", "en"), request.languages)
        assertEquals(RomHints(crc32 = "0000ABCD", serial = null, sizeBytes = 1_048_576), request.hints)
    }

    @Test
    fun aMatchStoresItsDetailsPlainColumnsAndPictures() = runTest {
        deliverTo(NameTrustNames.FILE_NAME, matched())
        val row = lib.db.scrapedGameDao().get("crc:0000abcd:1024")!!
        assertEquals("1245", row.screenScraperId)
        assertEquals(ScrapeMatchedBy.HASH, row.matchedBy)
        assertEquals(ScrapeTestLibrary.NOW, row.scrapedAt)
        assertEquals(matched().details, GameDetails.fromJson(row.detailsJson!!))
        assertEquals(listOf("Zork Quest", "1991", "Zork Soft", "1-2", "Adventure"), listOf(row.title, row.year.toString(), row.publisher, row.players, row.genre))
        assertEquals(
            listOf(ScrapedPicture(ScrapedPicture.BOX_FRONT, "grid", "/media/1245/box-front-grid-240x336.webp", 234, 320)),
            ScrapedPictures.decode(row.pictures),
        )
        assertTrue(row.hasBoxFront)
        assertTrue("a support ref without its picture", row.supportMissing)
        assertFalse(row.isPersonChoice)
    }

    /** The main pass saves box fronts only; its matches go to the disc pass at once. */
    @Test
    fun aMainPassMatchGoesToTheDiscPassUntilItsDiscIsSaved() = runTest {
        lib.add(lib.game("zq.sfc"), lib.game("no-support.sfc"), lib.game("both.sfc"))
        val source = lib.source()
        val (zq, noSupport, both) = source.pending(3)
        source.deliver(zq, matched(), listOf(boxFront()))
        source.deliver(noSupport, matched(withSupport = false), listOf(boxFront()))
        // Its disc was on disk already: nothing for the disc pass.
        source.deliver(both, matched(), listOf(boxFront(), support()))
        val key = lib.db.gameDao().game(1)!!.fixKeys
        assertEquals("the main pass did not ask the disc: no attempt", 0, lib.db.scrapedGameDao().get(key)?.supportAttempts)

        val again = source.pendingSupport(10)
        assertEquals("delivered a moment ago, offered in the same run", listOf("zq.sfc"), again.map { it.rom.fileName })
        assertFalse("no disc saved yet: not fresh", again.single().fresh)
        lib.now += 1
        source.deliver(again.single(), matched(), listOf(boxFront()), ScrapePass.Support)
        assertEquals(1, lib.db.scrapedGameDao().get(key)?.supportAttempts)
        // The run itself (the app's ScrapeRunSource) leaves out what its disc pass tried; the next run asks again.
        assertEquals(listOf("zq.sfc"), source.pendingSupport(10).map { it.rom.fileName })
        source.deliver(again.single(), matched(), listOf(boxFront(), support()), ScrapePass.Support)
        assertEquals(emptyList<String>(), source.pendingSupport(10).map { it.rom.fileName })
        assertEquals(0, lib.db.scrapedGameDao().get(key)?.supportAttempts)
        assertEquals("a match is never looked up again by the main pass", emptyList<String>(), source.pending(10).map { it.rom.fileName })
    }

    @Test
    fun theDiscPassKeepsABoxFrontThatCameFromAnotherSource() = runTest {
        lib.add(lib.game("zq.sfc"))
        val source = lib.source()
        val request = source.pending(1).single()
        val key = lib.db.gameDao().game(1)!!.fixKeys
        // Main pass: ScreenScraper matched with a disc but no box front; Steam's box was saved under steam_98765.
        val steamBox = ScrapedPicture(ScrapedPicture.BOX_FRONT, "grid", "/media/steam_98765/box.webp", 1, 1)
        source.deliver(request, matched(), listOf(boxFront().copy(path = steamBox.path)))
        assertTrue(lib.db.scrapedGameDao().get(key)!!.hasBoxFront)

        // Disc pass: the ScreenScraper result has no box front to hand back, the disc fails.
        lib.now += 1
        source.deliver(request, matched(), emptyList(), ScrapePass.Support)
        val row = lib.db.scrapedGameDao().get(key)!!
        assertTrue("the Steam box is still the game's box", row.hasBoxFront)
        assertEquals(listOf(steamBox.path), ScrapedPictures.decode(row.pictures).map { it.path })

        // And a disc that is saved later keeps it beside the box.
        lib.now += 1
        source.deliver(request, matched(), listOf(support()), ScrapePass.Support)
        val both = ScrapedPictures.decode(lib.db.scrapedGameDao().get(key)!!.pictures)
        assertEquals(listOf(ScrapedPicture.BOX_FRONT, ScrapedPicture.SUPPORT), both.map { it.kind })
    }

    @Test
    fun aDiscPictureThatKeepsFailingIsGivenUpAfterThreeAttempts() = runTest {
        lib.add(lib.game("zq.sfc"))
        val source = lib.source()
        source.deliver(source.pending(1).single(), matched(), listOf(boxFront()))
        // Three disc-pass attempts (the main pass asks no disc, so it counts none).
        repeat(3) { attempt ->
            lib.now += 10
            val request = source.pendingSupport(10).single()
            lib.now += 1
            source.deliver(request, matched(), listOf(boxFront()), ScrapePass.Support)
            assertEquals(attempt + 1, lib.db.scrapedGameDao().get(lib.db.gameDao().game(1)!!.fixKeys)?.supportAttempts)
        }
        lib.now += 10
        assertEquals("three failures: not asked again", emptyList<Any>(), source.pendingSupport(10))
        assertEquals("the main pass never offers a match", emptyList<String>(), source.pending(10).map { it.rom.fileName })
    }

    @Test
    fun anExactScreenScraperTitleUpgradesAFileNameAndALikelyIdentifierName() = runTest {
        val fromFile = deliverTo(NameTrustNames.FILE_NAME, matched())
        assertEquals("Zork Quest" to "SCRAPER_EXACT", shown(fromFile))
        assertEquals("zork quest", lib.db.gameDao().game(fromFile)!!.sortName)
        lib.sql("DELETE FROM game")
        lib.sql("DELETE FROM scraped_game")
        val likely = deliverTo("IDENTIFIER_LIKELY", matched(), title = "Zork Quest?")
        assertEquals("Zork Quest" to "SCRAPER_EXACT", shown(likely))
    }

    @Test
    fun aTitleWithAmpersandAndApostropheStaysPlainThroughStoreAndRenamePass() = runTest {
        for (title in listOf("Sonic & Knuckles", "Yoshi's Island")) {
            lib.sql("DELETE FROM game")
            lib.sql("DELETE FROM scraped_game")
            // The scraper hands over the title decoded (`GameInfo.title`); the row keeps it plain.
            val id = deliverTo(NameTrustNames.FILE_NAME, matched(title = title))
            assertEquals(title to "SCRAPER_EXACT", shown(id))
            val stored = lib.db.scrapedGameDao().matchedTitles(listOf("crc:0000abcd:1024")).single().title
            assertEquals("a rescan or Back to automatic writes this as the name", title, stored)
        }
    }

    @Test
    fun aRomLookupByNameAndSizeOnlyIsALikelyMatch() = runTest {
        val fromFile = deliverTo(NameTrustNames.FILE_NAME, matched(), fingerprint = null)
        assertEquals(ScrapeMatchedBy.FILE_NAME_SIZE, lib.db.scrapedGameDao().get("crc:0000abcd:1024")?.matchedBy)
        assertEquals("Zork Quest" to "SCRAPER_LIKELY", shown(fromFile))
        lib.sql("DELETE FROM game")
        lib.sql("DELETE FROM scraped_game")
        val likely = deliverTo("IDENTIFIER_LIKELY", matched(), title = "Zork Quest?", fingerprint = null)
        assertEquals("Zork Quest?" to "IDENTIFIER_LIKELY", shown(likely))
    }

    @Test
    fun aFileThatChangedSincePendingGetsNothingAndComesBackUnderItsNewState() = runTest {
        val ids = lib.add(lib.game("size.sfc", fingerprint = withCrc), lib.game("time.sfc", fingerprint = withCrc), lib.game("renamed.sfc"), lib.game("rekeyed.sfc"))
        val source = lib.source()
        val requests = source.pending(10)
        lib.sql("UPDATE game SET size_bytes = 2048 WHERE id = ?", ids[0])
        lib.sql("UPDATE game SET modified_at = 8 WHERE id = ?", ids[1])
        lib.sql("UPDATE game SET needs_identify = 1 WHERE id = ?", ids[2])
        lib.sql("UPDATE game SET fix_keys = 'crc:00000fff:1024' WHERE id = ?", ids[3])
        requests.forEach { source.deliver(it, matched(), listOf(boxFront())) }
        assertEquals("nothing stored", 0, lib.db.scrapedGameDao().countWithBoxFront())
        assertEquals("names untouched", listOf("size", "time", "renamed", "rekeyed"), ids.map { shown(it).first })
        assertEquals("this process does not ask them again: the run must not stop", emptyList<String>(), source.pending(10).map { it.rom.fileName })
        lib.sql("UPDATE game SET needs_identify = 0 WHERE id = ?", ids[2])
        assertEquals("the next process does", listOf("size.sfc", "time.sfc", "renamed.sfc", "rekeyed.sfc"), lib.source().pending(10).map { it.rom.fileName })
    }

    @Test
    fun everyCopyOfTheFileIsOfferedTheTitleByItsOwnTrust() = runTest {
        val ids = lib.add(
            lib.game("a.sfc", fixKeys = "crc:0000abcd:1024", fingerprint = withCrc),
            lib.game("copy.sfc", fixKeys = "crc:0000abcd:1024", fingerprint = withCrc),
            lib.game("exact-copy.sfc", fixKeys = "crc:0000abcd:1024", fingerprint = withCrc, trust = "IDENTIFIER_EXACT"),
        )
        val source = lib.source()
        source.deliver(source.pending(10).single(), matched(), listOf(boxFront()))
        assertEquals(listOf("Zork Quest" to "SCRAPER_EXACT", "Zork Quest" to "SCRAPER_EXACT", "exact-copy" to "IDENTIFIER_EXACT"), ids.map { shown(it) })
    }

    @Test
    fun anExactIdentifierNameIsNotReplaced() = runTest {
        val id = deliverTo("IDENTIFIER_EXACT", matched(title = "Zork Quest (ScreenScraper)"), title = "Zork Quest")
        assertEquals("Zork Quest" to "IDENTIFIER_EXACT", shown(id))
        assertEquals("the result is stored all the same", "1245", lib.db.scrapedGameDao().get("crc:0000abcd:1024")?.screenScraperId)
    }

    @Test
    fun aMatchByNameUpgradesOnlyAFileName() = runTest {
        val fromFile = deliverTo(NameTrustNames.FILE_NAME, matched(by = MatchedBy.Name))
        assertEquals("Zork Quest" to "SCRAPER_LIKELY", shown(fromFile))
        lib.sql("DELETE FROM game")
        lib.sql("DELETE FROM scraped_game")
        val likely = deliverTo("IDENTIFIER_LIKELY", matched(by = MatchedBy.Name), title = "Zork Quest?")
        assertEquals("Zork Quest?" to "IDENTIFIER_LIKELY", shown(likely))
    }

    @Test
    fun aPersonsNameFixWins() = runTest {
        val (id) = lib.add(lib.game("zq.sfc", fixKeys = "crc:0000abcd:1024"))
        lib.db.identifyDao().setFix(id, NameFixKind.OWN_NAME, "My Zork", "my zork", null, fixedAt = 1)
        val source = lib.source()
        source.deliver(source.pending(1).single(), matched(), listOf(boxFront()))
        assertEquals("My Zork" to NameTrustNames.PERSON_FIX, shown(id))
    }

    @Test
    fun aRunNeverReplacesThePersonsChosenMatch() = runTest {
        lib.add(lib.game("zq.sfc", fixKeys = "crc:0000abcd:1024"))
        val source = lib.source()
        val request = source.pending(1).single()
        source.deliver(request, matched(), listOf(boxFront()))
        lib.sql("UPDATE scraped_game SET is_person_choice = 1, title = 'Chosen Zork', screenscraper_id = '999'")
        source.deliver(request, ScrapeResult.NoMatch, emptyList())
        source.deliver(request, matched(title = "Other Zork"), listOf(boxFront(), support()))
        val row = lib.db.scrapedGameDao().get("crc:0000abcd:1024")!!
        assertEquals(listOf("Chosen Zork", "999"), listOf(row.title, row.screenScraperId))
        assertTrue(row.isPersonChoice)
        assertEquals("its missing disc picture is not asked by a run", emptyList<Any>(), source.pendingSupport(10))
    }

    @Test
    fun aNoMatchNeverRenamesAndAResultWithoutTitleKeepsTheName() = runTest {
        val id = deliverTo(NameTrustNames.FILE_NAME, ScrapeResult.NoMatch)
        assertEquals("zork quest (file)" to NameTrustNames.FILE_NAME, shown(id))
        lib.sql("DELETE FROM game")
        lib.sql("DELETE FROM scraped_game")
        val untitled = deliverTo(NameTrustNames.FILE_NAME, matched(title = null))
        assertEquals("zork quest (file)" to NameTrustNames.FILE_NAME, shown(untitled))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aPauseIsNotSomethingToDeliver() = runTest {
        lib.add(lib.game("zq.sfc"))
        val source = lib.source()
        source.deliver(source.pending(1).single(), ScrapeResult.Paused(0, com.plumemu.scraper.PauseReason.DailyQuota), emptyList())
    }
}
