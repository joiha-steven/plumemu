package com.plumemu.graph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.FixKeys
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.RenameBoxArtNotice
import com.plumemu.library.scrape.LibraryScrapeSource
import com.plumemu.library.scrape.PersonChoice
import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.FailureReason
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.GameInfo
import com.plumemu.scraper.GameMatch
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.PauseReason
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.SearchHit
import com.plumemu.scraper.SearchOutcome
import com.plumemu.scraper.UnavailableReason
import com.plumemu.scraper.media.SavedMedia
import com.plumemu.scraper.work.ScrapePass
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Box art after a rename, with a fake ScreenScraper and the real library on an in-memory database:
 * a renamed game without box art gets the one game of its system carrying the new name, stored
 * like a pick; anything less than certain, any game that already has box art or a person's choice,
 * and any state in which a box-art run could not ask (signed out, metered network, quota, paused)
 * stores nothing, and the last asks nothing at all. Failures are logged and change nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphRenameBoxArtTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, PlumDatabase::class.java).build()
    private val scraper = FakeScraper()
    private var signedIn = true
    private var accountRefused = false
    private var pausedByPerson = false
    private var savedPause: ScrapeResult.Paused? = null
    private var conditions = DeviceConditions(unmeteredNetwork = true, batteryLow = false)
    private val refused = CopyOnWriteArrayList<Scraper>()
    private val logs = CopyOnWriteArrayList<String>()
    private val mediaDir = File(context.noBackupFilesDir, "test-rename-art-media").apply { deleteRecursively() }
    private val source = LibraryScrapeSource(db, { error("no file is opened") }, { null }, { "us" }, { "en" }, { NOW })

    private val renameArt =
        GraphRenameBoxArt(
            scraper = { scraper.takeIf { signedIn && !accountRefused } },
            isSignedIn = { signedIn },
            hasUsableAccount = { signedIn && !accountRefused },
            markRefused = { refused += it },
            pausedByPerson = { pausedByPerson },
            savedPause = { savedPause },
            conditions = { conditions },
            source = { source },
            mediaDir = { mediaDir },
            lowRamDevice = { false },
            io = Dispatchers.IO,
            clock = { NOW },
            log = { logs += it },
        )

    @After
    fun tearDown() {
        db.close()
        mediaDir.deleteRecursively()
    }

    @Test
    fun aRenamedGameWithoutBoxArtTakesTheOneGameOfItsSystemWithThatName() = runBlocking<Unit> {
        val id = renamedGame()
        scraper.exactAnswer = found("2144")

        assertEquals(RenameArtOutcome.Stored("2144"), renameArt.afterRename(id))

        assertEquals(listOf(Triple(NAME, "snes", listOf("us", "wor", "ss"))), scraper.searches.toList())
        assertEquals("2144", scraper.byId.single().first)
        assertFalse("the 30-day answer cache may be used", scraper.byId.single().second.fresh)
        val row = db.scrapedGameDao().get(KEY)!!
        assertTrue("stored like a pick", row.isPersonChoice)
        assertEquals(ScrapeOutcome.MATCHED, row.outcome)
        assertEquals("2144", row.screenScraperId)
        assertTrue(row.hasBoxFront)
        assertEquals("the person's name stays", NAME, db.gameDao().game(id)!!.title)
        assertEquals(listOf("box art after rename: game $id searched, took 2144"), logs.toList())
    }

    @Test
    fun aChosenTitleIsLookedUpToo() = runBlocking<Unit> {
        val id = renamedGame(NameFixKind.CHOSEN_TITLE)
        scraper.exactAnswer = found("2144")

        assertEquals(RenameArtOutcome.Stored("2144"), renameArt.afterRename(id))
    }

    @Test
    fun twoGamesWithTheNameAreAmbiguousAndStoreNothing() = runBlocking<Unit> {
        val id = renamedGame()
        scraper.exactAnswer = found("2144", "2145")

        assertEquals(RenameArtOutcome.Ambiguous(2), renameArt.afterRename(id))

        assertTrue("no game is fetched", scraper.byId.isEmpty())
        assertNull(db.scrapedGameDao().get(KEY))
        assertEquals(listOf("box art after rename: game $id searched, ambiguous (2 games)"), logs.toList())
    }

    @Test
    fun noGameWithTheNameStoresNothingAndIsNotAskedAgain() = runBlocking<Unit> {
        val id = renamedGame()
        scraper.exactAnswer = SearchOutcome.NothingFound

        assertEquals(RenameArtOutcome.NoMatch, renameArt.afterRename(id))
        assertEquals("the same name again", RenameArtOutcome.Skipped("asked before for this name"), renameArt.afterRename(id))

        assertEquals(1, scraper.searches.size)
        assertNull(db.scrapedGameDao().get(KEY))
        assertEquals("box art after rename: game $id searched, none", logs.first())
    }

    @Test
    fun aGameWithARunsBoxFrontAsksNothing() = runBlocking<Unit> {
        val id = renamedGame()
        source.deliver(source.pending(1).single(), runMatch(), listOf(SavedMedia(MediaKind.BoxFront, "grid", "/media/1/box.webp", 234, 320)), ScrapePass.Main)

        assertEquals(RenameArtOutcome.Skipped("has box art"), renameArt.afterRename(id))

        assertNoRequest()
        assertEquals("1245", db.scrapedGameDao().get(KEY)!!.screenScraperId)
    }

    @Test
    fun aGameWhoseBoxArtThePersonPickedAsksNothing() = runBlocking<Unit> {
        val id = renamedGame()
        source.storePersonChoice(id, PersonChoice.Picked(picked("1001"), emptyList()))

        assertEquals(RenameArtOutcome.Skipped("box art chosen by the person"), renameArt.afterRename(id))

        assertNoRequest()
        assertEquals("1001", db.scrapedGameDao().get(KEY)!!.screenScraperId)
    }

    @Test
    fun aGameThePersonSetToNoBoxArtAsksNothing() = runBlocking<Unit> {
        val id = renamedGame()
        source.storePersonChoice(id, PersonChoice.NoBoxArt)

        assertEquals(RenameArtOutcome.Skipped("box art chosen by the person"), renameArt.afterRename(id))

        assertNoRequest()
        assertEquals(ScrapeOutcome.NO_MATCH, db.scrapedGameDao().get(KEY)!!.outcome)
    }

    @Test
    fun signedOutAsksNothing() = runBlocking<Unit> {
        val id = renamedGame()
        signedIn = false

        assertEquals(RenameArtOutcome.Skipped("not signed in"), renameArt.afterRename(id))
        assertNoRequest()
    }

    @Test
    fun aMeteredNetworkAsksNothingAndALowBatteryDoesNotMatter() = runBlocking<Unit> {
        val id = renamedGame()
        conditions = DeviceConditions(unmeteredNetwork = false, batteryLow = false)
        assertEquals(RenameBoxArtNotice.NoWifi, renameArt.noticeFor(id))
        assertEquals(RenameArtOutcome.Skipped("no unmetered network"), renameArt.afterRename(id))
        assertNoRequest()

        // The person's own action: the battery is not checked.
        conditions = DeviceConditions(unmeteredNetwork = true, batteryLow = true)
        scraper.exactAnswer = found("2144")
        assertNull(renameArt.noticeFor(id))
        assertEquals(RenameArtOutcome.Stored("2144"), renameArt.afterRename(id))
    }

    @Test
    fun theReasonsAreToldToThePerson() = runBlocking<Unit> {
        val id = renamedGame()
        assertNull("all is well: nothing to tell", renameArt.noticeFor(id))

        accountRefused = true
        assertEquals(RenameBoxArtNotice.SignInNeeded, renameArt.noticeFor(id))
        assertEquals(RenameArtOutcome.Skipped("sign-in needed"), renameArt.afterRename(id))
        accountRefused = false

        pausedByPerson = true
        assertEquals(RenameBoxArtNotice.Paused, renameArt.noticeFor(id))
        pausedByPerson = false

        savedPause = ScrapeResult.Paused(NOW + 60_000, PauseReason.DailyQuota)
        assertEquals(RenameBoxArtNotice.DailyLimit(NOW + 60_000), renameArt.noticeFor(id))
        savedPause = ScrapeResult.Paused(NOW + 30_000, PauseReason.ServerClosed)
        assertEquals(RenameBoxArtNotice.ServerClosed(NOW + 30_000), renameArt.noticeFor(id))
        assertNoRequest()
    }

    @Test
    fun nothingIsToldSignedOutOrForAGameWithBoxArt() = runBlocking<Unit> {
        val id = renamedGame()
        conditions = DeviceConditions(unmeteredNetwork = false, batteryLow = false)
        signedIn = false
        assertNull("signed out: box art is off", renameArt.noticeFor(id))

        signedIn = true
        source.storePersonChoice(id, PersonChoice.NoBoxArt)
        assertNull("not a game the lookup would ask for", renameArt.noticeFor(id))
    }

    @Test
    fun theDailyQuotaOrThePersonsPauseAsksNothing() = runBlocking<Unit> {
        val id = renamedGame()
        savedPause = ScrapeResult.Paused(NOW + 60_000, PauseReason.DailyQuota)
        assertEquals(RenameArtOutcome.Skipped("daily quota"), renameArt.afterRename(id))

        savedPause = null
        pausedByPerson = true
        assertEquals(RenameArtOutcome.Skipped("box art paused"), renameArt.afterRename(id))
        assertNoRequest()

        pausedByPerson = false
        savedPause = ScrapeResult.Paused(NOW - 1, PauseReason.DailyQuota)
        scraper.exactAnswer = SearchOutcome.NothingFound
        assertEquals("a pause that is over holds nothing back", RenameArtOutcome.NoMatch, renameArt.afterRename(id))
    }

    @Test
    fun notAGameAndBackToAutomaticAskNothing() = runBlocking<Unit> {
        val notAGame = renamedGame()
        db.identifyDao().setFix(notAGame, NameFixKind.NOT_A_GAME, null, null, null, fixedAt = 2)
        assertEquals(RenameArtOutcome.Skipped("name changed"), renameArt.afterRename(notAGame))

        val automatic = renamedGame(doc = "other")
        assertTrue(db.identifyDao().backToAutomatic(automatic))
        assertTrue(renameArt.afterRename(automatic) is RenameArtOutcome.Skipped)
        assertNoRequest()
    }

    @Test
    fun aNetworkFailureIsLoggedChangesNothingAndTheNextRenameMayAskAgain() = runBlocking<Unit> {
        val id = renamedGame()
        source.deliver(source.pending(1).single(), ScrapeResult.NoMatch, emptyList(), ScrapePass.Main)
        val before = db.scrapedGameDao().get(KEY)
        scraper.exactAnswer = SearchOutcome.Failed(FailureReason.Network)

        assertEquals(RenameArtOutcome.Failed("network"), renameArt.afterRename(id))

        assertEquals("the run's no-match is as it was", before, db.scrapedGameDao().get(KEY))
        assertEquals(listOf("box art after rename: game $id failed (network)"), logs.toList())
        scraper.exactAnswer = found("2144")
        assertEquals("not answered, so asked again", RenameArtOutcome.Stored("2144"), renameArt.afterRename(id))
    }

    @Test
    fun aFailureAfterTheSearchStoresNothingAndTheSameNameIsAskedAgain() = runBlocking<Unit> {
        val id = renamedGame()
        scraper.exactAnswer = found("2144")
        scraper.byIdAnswer = ScrapeResult.Paused(NOW + 1, PauseReason.DailyQuota)
        assertEquals(RenameArtOutcome.Failed("daily quota"), renameArt.afterRename(id))
        scraper.byIdAnswer = ScrapeResult.Failed(FailureReason.Network)
        assertEquals("asked again after the quota", RenameArtOutcome.Failed("network"), renameArt.afterRename(id))
        scraper.byIdAnswer = null
        scraper.downloadAnswer = MediaDownload.NetworkDown
        assertEquals("asked again after the network", RenameArtOutcome.Failed("pictures Network"), renameArt.afterRename(id))
        assertNull(db.scrapedGameDao().get(KEY))

        scraper.downloadAnswer = null
        assertEquals("asked again after the pictures", RenameArtOutcome.Stored("2144"), renameArt.afterRename(id))
        assertEquals(4, scraper.searches.size)
    }

    @Test
    fun aRefusalOnThePictureMarksTheAccount() = runBlocking<Unit> {
        val id = renamedGame()
        scraper.exactAnswer = found("2144")
        scraper.downloadAnswer = MediaDownload.Unavailable(UnavailableReason.BadUserCredentials)

        assertEquals(RenameArtOutcome.Failed("unavailable BadUserCredentials"), renameArt.afterRename(id))
        assertEquals(listOf<Scraper>(scraper), refused.toList())
        assertNull(db.scrapedGameDao().get(KEY))
    }

    @Test
    fun aCappedAnswerIsAmbiguousEvenWithOneExactName() = runBlocking<Unit> {
        val id = renamedGame()
        scraper.exactAnswer = SearchOutcome.Found(found("2144").games, truncated = true)

        assertEquals(RenameArtOutcome.Ambiguous(1, capped = true), renameArt.afterRename(id))

        assertTrue("no game is fetched", scraper.byId.isEmpty())
        assertNull(db.scrapedGameDao().get(KEY))
        assertEquals(listOf("box art after rename: game $id searched, ambiguous (results capped)"), logs.toList())
    }

    @Test
    fun aGameTheFileMatchedIsNotReplacedByAnotherFoundByName() = runBlocking<Unit> {
        // A run matched the file by its hash to game 1245, which had no box front to save.
        val id = renamedGame()
        source.deliver(source.pending(1).single(), runMatch(), emptyList(), ScrapePass.Main)
        scraper.exactAnswer = found("2144")

        assertEquals(RenameArtOutcome.Skipped("matched by file"), renameArt.afterRename(id))
        assertTrue(scraper.byId.isEmpty())
        assertEquals("1245", db.scrapedGameDao().get(KEY)!!.screenScraperId)

        val same = renamedGame(doc = "other")
        source.deliver(source.pending(1).single(), runMatch(), emptyList(), ScrapePass.Main)
        scraper.exactAnswer = found("1245")
        assertEquals("the same game's box art is fine", RenameArtOutcome.Stored("1245"), renameArt.afterRename(same))
    }

    @Test
    fun picturesLinesAreTheRenamesNotARunsLines() = runBlocking<Unit> {
        val id = renamedGame()
        scraper.exactAnswer = found("2144")
        val blocked = File(context.cacheDir, "test-rename-art-not-a-folder").apply { delete(); writeText("x") }
        val withBlockedFolder =
            GraphRenameBoxArt(
                { scraper }, { true }, { true }, { refused += it }, { false }, { null }, { conditions }, { source }, { blocked }, { false },
                Dispatchers.IO, clock = { NOW }, log = { logs += it },
            )

        assertTrue(withBlockedFolder.afterRename(id) is RenameArtOutcome.Failed)

        blocked.delete()
        assertTrue("$logs", logs.any { it.startsWith("box art after rename: pictures: ") })
        assertTrue("$logs", logs.none { it.contains("scrape run") })
    }

    @Test
    fun aThrownErrorIsLoggedByItsClassAndNeverCrashes() = runBlocking<Unit> {
        val id = renamedGame()
        scraper.searchThrows = IOException("socket closed for zork@example.invalid")

        assertEquals(RenameArtOutcome.Failed("IOException"), renameArt.afterRename(id))
        assertEquals(listOf("box art after rename: game $id failed (IOException)"), logs.toList())
    }

    @Test
    fun aRefusedPasswordMarksTheAccount() = runBlocking<Unit> {
        val id = renamedGame()
        scraper.exactAnswer = SearchOutcome.Unavailable(UnavailableReason.BadUserCredentials)

        assertEquals(RenameArtOutcome.Failed("unavailable BadUserCredentials"), renameArt.afterRename(id))
        assertEquals(listOf<Scraper>(scraper), refused.toList())
    }

    @Test
    fun aGameGoneWhileTheLookupRanStoresNothing() = runBlocking<Unit> {
        val id = renamedGame()
        scraper.exactAnswer = found("2144")
        scraper.onById = { db.openHelper.writableDatabase.execSQL("DELETE FROM game WHERE id = $id") }

        assertEquals(RenameArtOutcome.NotStored("game gone"), renameArt.afterRename(id))
        assertNull(db.scrapedGameDao().get(KEY))
    }

    @Test
    fun theContentKeyLandingFirstDoesNotMakeTheStoreMiss() = runBlocking<Unit> {
        // A loose cartridge: fixed under its row key, then the rename's whole-file read gives it a content key.
        val id = renamedGame(keys = emptyList())
        assertTrue(db.identifyDao().addContentKeys(id, 1, 1, "", listOf(KEY)))
        scraper.exactAnswer = found("2144")

        assertEquals(RenameArtOutcome.Stored("2144"), renameArt.afterRename(id))

        val shownKey = FixKeys.forGame(id, db.gameDao().game(id)!!.fixKeys).first()
        assertEquals(KEY, shownKey)
        assertEquals("2144", db.scrapedGameDao().get(shownKey)!!.screenScraperId)
    }

    @Test
    fun noLogLineHoldsTheNameOrTheAccount() = runBlocking<Unit> {
        val id = renamedGame()
        scraper.exactAnswer = found("2144")
        renameArt.afterRename(id)
        scraper.exactAnswer = found("1", "2")
        renameArt.afterRename(renamedGame(doc = "other"))

        assertEquals(2, logs.size)
        assertTrue(logs.none { it.contains("Zork", ignoreCase = true) || it.contains("plumtester") })
    }

    private fun assertNoRequest() {
        assertTrue("no search", scraper.searches.isEmpty())
        assertTrue("no lookup", scraper.byId.isEmpty())
        assertTrue("no download", scraper.downloads.isEmpty())
    }

    private fun found(vararg ids: String) = SearchOutcome.Found(ids.map { SearchHit(it, NAME, 1991, null, "Super Nintendo", null) })

    /** A named, listed game with [keys], renamed by the person to [NAME]. */
    private suspend fun renamedGame(
        kind: NameFixKind = NameFixKind.OWN_NAME,
        keys: List<String>? = null,
        doc: String = "zq",
    ): Long {
        val scanDao = db.scanDao()
        val folder = scanDao.insertFolder(RomFolderEntity(treeUri = "content://test/tree/$doc", displayName = doc, addedAt = 1))
        scanDao.applyBatch(ScanBatch(folder, systems = listOf(SystemEntity("snes", "snes")), newGames = listOf(gameRow(folder, doc))))
        val id = scanDao.gamesInFolder(folder).single().id
        val dao = db.identifyDao()
        val fixKeys = keys ?: listOf(if (doc == "zq") KEY else OTHER_KEY)
        assertEquals(1, dao.recordIdentification(id, 1, 1, "$doc.sfc", null, null, null, FixKeys.join(fixKeys), "FILE_NAME", "UNKNOWN", packVersion = 1))
        dao.setShownName(id, "$doc (file)", "$doc (file)", "", "FILE_NAME", isNotGame = false)
        dao.setFix(id, kind, NAME, NAME.lowercase(), null, fixedAt = 1)
        return id
    }

    private fun gameRow(
        folderId: Long,
        doc: String,
    ) = GameEntity(
        folderId = folderId,
        documentId = doc,
        parentDocumentId = "root",
        relativePath = "snes/$doc.sfc",
        fileName = "$doc.sfc",
        sizeBytes = 1,
        modifiedAt = 1,
        title = doc,
        sortName = doc,
        regions = "",
        discGroup = null,
        discCount = 1,
        systemId = "snes",
        isPrimaryCopy = true,
    )

    private fun runMatch() =
        ScrapeResult.Matched(
            match = GameMatch("1245", MatchedBy.Hash),
            info = GameInfo("Zork by Hash", null, 1991, null, null, null, null, null),
            media = listOf(MediaRef(MediaKind.BoxFront, "us", "https://example.invalid/run.png")),
            details = GameDetails(),
        )

    private fun picked(gameId: String) =
        ScrapeResult.Matched(
            match = GameMatch(gameId, MatchedBy.PersonChoice),
            info = GameInfo("Zork Quest", null, 1991, null, null, "Galfen Soft", null, null),
            media = listOf(
                MediaRef(MediaKind.BoxFront, "us", "https://example.invalid/box.png"),
                MediaRef(MediaKind.Support, "us", "https://example.invalid/disc.png"),
            ),
            details = GameDetails(),
        )

    /** ScreenScraper as the tests need it; pictures are real so the saver can decode them. */
    private inner class FakeScraper : Scraper {
        override val available: Boolean = true
        var exactAnswer: SearchOutcome = SearchOutcome.NothingFound
        var searchThrows: Exception? = null
        val searches = CopyOnWriteArrayList<Triple<String, String, List<String>>>()
        var byIdAnswer: ScrapeResult? = null
        var onById: () -> Unit = {}
        val byId = CopyOnWriteArrayList<Pair<String, ScrapeRequest>>()
        var downloadAnswer: MediaDownload? = null
        val downloads = CopyOnWriteArrayList<MediaKind>()

        override suspend fun scrape(request: ScrapeRequest): ScrapeResult = error("a rename never scrapes")

        override suspend fun search(
            title: String,
            systemId: String,
            regions: List<String>,
        ): SearchOutcome = error("a rename takes only exact titles")

        override suspend fun searchExactTitle(
            title: String,
            systemId: String,
            regions: List<String>,
        ): SearchOutcome {
            searches += Triple(title, systemId, regions)
            searchThrows?.let { throw it }
            return exactAnswer
        }

        override suspend fun byGameId(
            gameId: String,
            request: ScrapeRequest,
        ): ScrapeResult {
            byId += gameId to request
            onById()
            return byIdAnswer ?: picked(gameId)
        }

        override suspend fun download(
            media: MediaRef,
            target: File,
            maxWidth: Int?,
            maxHeight: Int?,
        ): MediaDownload {
            downloads += media.kind
            downloadAnswer?.let { return it }
            target.writeBytes(picture(if (media.kind == MediaKind.BoxFront) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG))
            return MediaDownload.Done(target)
        }

        override suspend fun checkAccount(): AccountCheck = AccountCheck.Ok(maxThreads = 1, requestsToday = 0, maxRequestsPerDay = 20_000)

        private fun picture(format: Bitmap.CompressFormat): ByteArray {
            val bitmap = Bitmap.createBitmap(40, 56, Bitmap.Config.RGB_565).apply { eraseColor(Color.rgb(51, 102, 153)) }
            return ByteArrayOutputStream().also { bitmap.compress(format, 90, it) }.toByteArray()
        }
    }

    private companion object {
        const val NAME = "Zork Quest"
        const val KEY = "crc:0000abcd:1024"
        const val OTHER_KEY = "crc:0000beef:1024"
        const val NOW = 1_000_000L
    }
}
