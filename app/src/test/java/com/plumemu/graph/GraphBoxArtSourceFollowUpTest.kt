package com.plumemu.graph

import androidx.work.WorkInfo
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.ScanFinish
import com.plumemu.coredata.db.ScrapeMatchedBy
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedGameEntity
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coredata.settings.BoxArtSourceSetting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The source changing under a run or a panel: no ScreenScraper request under "Box art only", a
 * sign-out or a reset that stops only the run it should, and the switch to ScreenScraper that asks
 * for the details a libretro picture never had.
 */
internal class GraphBoxArtSourceFollowUpTest : GraphScrapesHarness() {
    private suspend fun waitUntil(condition: () -> Boolean) = withTimeout(5_000) { while (!condition()) delay(10) }

    private suspend fun setSource(scrapes: GraphScrapes, source: BoxArtSourceSetting): Boolean {
        val answer = CompletableDeferred<Boolean>()
        scrapes.setSource(source) { answer.complete(it) }
        return withTimeout(5_000) { answer.await() }
    }

    private fun startWatching() = scrapes.stopWhenOwnCovers(chosenSource, accounts.isSignedIn)

    @Test
    fun settingsAsksScreenScraperForNoLimitsUnderBoxArtOnlyEvenSignedIn() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        val scraper = made.single()
        val before = scraper.checks.get()
        limitsToGive = AccountLimitsForTest
        chosenSource.value = BoxArtSourceSetting.LibretroOnly

        val visit = scope.launch { scrapes.accountLimits.collect { } }
        delay(500)
        visit.cancelAndJoin()

        assertEquals("no ssuserInfos.php under Box art only", before, scraper.checks.get())
    }

    @Test
    fun settingsStillAsksTheLimitsUnderScreenScraper() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        val scraper = made.single()
        val before = scraper.checks.get()
        limitsToGive = AccountLimitsForTest

        val visit = scope.launch { scrapes.accountLimits.collect { } }
        waitUntil { scraper.checks.get() > before }
        visit.cancelAndJoin()
    }

    @Test
    fun signingOutKeepsTheBoxArtOnlyRunGoingButStopsAScreenScraperRun() = runBlocking<Unit> {
        chosenSource.value = BoxArtSourceSetting.LibretroOnly
        val scrapes = graph()
        signIn(scrapes)
        awaitRun()
        assertTrue(accounts.signOutNow())
        delay(500)
        assertEquals("the libretro run needs no account", 1, runs().size)

        chosenSource.value = BoxArtSourceSetting.ScreenScraper
        signIn(scrapes)
        awaitRun()
        assertTrue(accounts.signOutNow())
        waitUntil { runs().isEmpty() }
    }

    @Test
    fun theSourceBecomingOwnCoversByAnyPathStopsTheRun() = runBlocking<Unit> {
        chosenSource.value = BoxArtSourceSetting.LibretroOnly
        val scrapes = graph()
        signIn(scrapes)
        scrapes.requestRun().join()
        awaitRun()
        val watching = startWatching()
        delay(300)
        assertEquals("the baseline stops nothing", 1, runs().size)

        // Reset all settings: the stored choice goes back to null; the account is signed in, so ScreenScraper is in force.
        chosenSource.value = null
        delay(300)
        assertEquals("ScreenScraper takes over, the run is not stopped", 1, runs().size)

        // A choice of own covers.
        chosenSource.value = BoxArtSourceSetting.OwnCovers
        waitUntil { runs().isEmpty() }
        watching.cancel()
    }

    @Test
    fun resetAllWithNoAccountWhileBoxArtOnlyRunsStopsTheRun() = runBlocking<Unit> {
        chosenSource.value = BoxArtSourceSetting.LibretroOnly
        val scrapes = graph()
        scrapes.requestRun().join()
        awaitRun()
        val watching = startWatching()
        delay(300)

        chosenSource.value = null
        waitUntil { runs().isEmpty() }
        watching.cancel()
    }

    private fun gameWithRow(id: String, screenScraperId: String?, outcome: ScrapeOutcome = ScrapeOutcome.MATCHED, hasBox: Boolean = true) {
        val key = "crc:$id:1"
        runBlocking {
            val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/$id", displayName = id, addedAt = 0))
            val game =
                GameEntity(
                    folderId = folderId, documentId = "doc$id", parentDocumentId = "root", relativePath = id, fileName = id, sizeBytes = 1, modifiedAt = 1,
                    title = id, sortName = id, regions = "", discGroup = null, discCount = 1, systemId = "snes", isPrimaryCopy = true,
                )
            db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity("snes", "snes")), listOf(game)))
            db.scanDao().finishScan(ScanFinish(folderId))
            val gameId = db.scanDao().gamesInFolder(folderId).single().id
            db.openHelper.writableDatabase.execSQL("UPDATE game SET fix_keys = ?, needs_identify = 0 WHERE id = ?", arrayOf<Any>(key, gameId))
            db.scrapedGameDao().insertOrReplace(
                ScrapedGameEntity(
                    scrapeKey = key, gameId = null, outcome = outcome, screenScraperId = screenScraperId, matchedBy = ScrapeMatchedBy.NAME,
                    scrapedAt = 1, detailsJson = null, title = null, year = null, publisher = null, players = null, genre = null,
                    pictures = "", hasBoxFront = hasBox,
                ),
            )
        }
    }

    private fun askedAgain(id: String) = runBlocking { db.scrapedGameDao().get("crc:$id:1")?.askAgain }

    @Test
    fun switchingToScreenScraperAsksAgainForGamesThatOnlyHaveAnotherSourcesPicture() = runBlocking<Unit> {
        chosenSource.value = BoxArtSourceSetting.LibretroOnly
        gameWithRow("libretro", "libretro_ab12cd34")
        gameWithRow("steam", "steam_620")
        gameWithRow("details", "12345")
        gameWithRow("nopicture", null, ScrapeOutcome.NO_MATCH, hasBox = false)
        val scrapes = graph()
        signIn(scrapes)
        awaitRun()
        // The sign-in's own request may already have marked nothing: only the choice below does.

        assertTrue(setSource(scrapes, BoxArtSourceSetting.ScreenScraper))

        assertEquals("a libretro picture has no details: asked again", true, askedAgain("libretro"))
        assertEquals("so does a Steam one", true, askedAgain("steam"))
        assertEquals("a ScreenScraper match has its details", false, askedAgain("details"))
        assertEquals("a game with no picture is asked as before", true, askedAgain("nopicture"))
        val kept = runBlocking { db.scrapedGameDao().get("crc:libretro:1") }
        assertEquals("its picture row stays until ScreenScraper answers", true, kept?.hasBoxFront)
        assertEquals(WorkInfo.State.ENQUEUED, awaitRun().state)
    }

    @Test
    fun choosingBoxArtOnlyMarksNothingForDetails() = runBlocking<Unit> {
        gameWithRow("libretro", "libretro_ab12cd34")
        val scrapes = graph()
        assertTrue(setSource(scrapes, BoxArtSourceSetting.LibretroOnly))
        assertFalse(askedAgain("libretro") == true)
    }

    private companion object {
        val AccountLimitsForTest = com.plumemu.scraper.AccountLimits(maxThreads = 1, requestsToday = 1, maxRequestsPerDay = 10_000, maxDownloadKilobytesPerSecond = 128)
    }
}
