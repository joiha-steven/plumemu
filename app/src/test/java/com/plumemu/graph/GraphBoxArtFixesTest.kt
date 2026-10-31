package com.plumemu.graph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coredata.account.ScraperAccountStore
import com.plumemu.coredata.db.FixKeys
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedPicture
import com.plumemu.coredata.db.ScrapedPictures
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.BoxArtHit
import com.plumemu.game.BoxArtProblem
import com.plumemu.game.BoxArtSearchOutcome
import com.plumemu.game.BoxArtStoreOutcome
import com.plumemu.game.UiText
import com.plumemu.game.boxArtProblemText
import com.plumemu.identify.NameTrust
import com.plumemu.library.scrape.LibraryScrapeSource
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
import com.plumemu.scraper.account.PasswordCipher
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fix box art… on the graph with a fake ScreenScraper and the real library on an
 * in-memory database: every answer becomes a plain outcome, a pick is stored as the person's
 * choice with its pictures at both sizes, No box art after a pick names the game again at once,
 * nothing is stored when the pick fails or is cancelled, thumbnails are cached, and the entry is
 * offered only with a usable account in a build with credentials. Logs carry no names or URLs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphBoxArtFixesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, PlumDatabase::class.java).build()
    private val scraper = FakeScraper()
    private var usable: Scraper? = scraper
    private var onWifi = true
    private val refused = CopyOnWriteArrayList<Scraper>()
    private val namings = CopyOnWriteArrayList<String>()
    private val logs = CopyOnWriteArrayList<String>()
    private val mediaDir = File(context.noBackupFilesDir, "test-box-art-media").apply { deleteRecursively() }
    private val thumbDir = File(context.cacheDir, "test-box-art-thumbs").apply { deleteRecursively() }
    private val source = LibraryScrapeSource(db, { error("no file is opened") }, { null }, { "us" }, { "en" }, { NOW })
    private val storeJob = Job()

    /** When set, the library's write waits for it after [storeStarted] (a write half-way). */
    private var storeGate: CompletableDeferred<Unit>? = null
    private var storeStarted = CompletableDeferred<Unit>()

    private val fixes =
        GraphBoxArtFixes(
            scraper = { usable },
            markRefused = { refused += it },
            isSignedIn = { usable != null },
            unmeteredNetwork = { onWifi },
            source = { source },
            gameExists = { id -> db.gameDao().game(id) != null },
            mediaDir = { mediaDir },
            thumbnailDir = { thumbDir },
            lowRamDevice = { false },
            nameAgain = { namings += "now" },
            io = Dispatchers.IO,
            log = { logs += it },
            storeChoice = { library, id, choice ->
                storeStarted.complete(Unit)
                storeGate?.await()
                library.storePersonChoice(id, choice)
            },
        )

    @After
    fun tearDown() = runBlocking<Unit> {
        storeJob.cancelAndJoin()
        db.close()
        mediaDir.deleteRecursively()
        thumbDir.deleteRecursively()
    }

    private val hit = BoxArtHit("2144", "Zork Quest", 1991, "Galfen Soft", "Super Nintendo", MediaRef(MediaKind.BoxFront, "us", "https://example.invalid/box.png"))

    @Test
    fun aSearchAsksForTheGamesSystemAndRegionsAndListsEveryGame() = runBlocking<Unit> {
        val id = namedGame()
        scraper.searchAnswer = SearchOutcome.Found(listOf(SearchHit("2144", "Zork Quest", 1991, "Galfen Soft", "Super Nintendo", hit.thumbnail), SearchHit("77", null, null, null, null, null)))

        val outcome = fixes.search(id, "zork")

        assertEquals(listOf(Triple("zork", "snes", listOf("us", "wor", "ss"))), scraper.searches.map { Triple(it.first, it.second, it.third) })
        assertEquals(BoxArtSearchOutcome.Found(listOf(hit, BoxArtHit("77", null, null, null, null, null))), outcome)
    }

    @Test
    fun everySearchAnswerBecomesAPlainOutcome() = runBlocking<Unit> {
        val id = namedGame()
        val cases =
            listOf(
                SearchOutcome.Found(emptyList()) to BoxArtSearchOutcome.NothingFound,
                SearchOutcome.NothingFound to BoxArtSearchOutcome.NothingFound,
                SearchOutcome.Paused(5_000, PauseReason.DailyQuota) to BoxArtSearchOutcome.Failed(BoxArtProblem.DailyLimit(5_000)),
                SearchOutcome.Paused(6_000, PauseReason.ServerClosed) to BoxArtSearchOutcome.Failed(BoxArtProblem.ServerClosed(6_000)),
                SearchOutcome.Unavailable(UnavailableReason.NoDeveloperCredentials) to BoxArtSearchOutcome.Failed(BoxArtProblem.NotAvailable),
                SearchOutcome.Unavailable(UnavailableReason.SoftwareBlocked) to BoxArtSearchOutcome.Failed(BoxArtProblem.NotAvailable),
                SearchOutcome.Failed(FailureReason.Network) to BoxArtSearchOutcome.Failed(BoxArtProblem.Offline),
                SearchOutcome.Failed(FailureReason.Server(503)) to BoxArtSearchOutcome.Failed(BoxArtProblem.ServerError(503)),
            )
        for ((answer, expected) in cases) {
            scraper.searchAnswer = answer
            assertEquals("$answer", expected, fixes.search(id, "zork"))
        }
        assertTrue("only a refused password marks the account", refused.isEmpty())
    }

    @Test
    fun aRefusedPasswordMarksTheAccountAndSaysSignInAgain() = runBlocking<Unit> {
        val id = namedGame()
        scraper.searchAnswer = SearchOutcome.Unavailable(UnavailableReason.BadUserCredentials)
        assertEquals(BoxArtSearchOutcome.Failed(BoxArtProblem.SignInAgain), fixes.search(id, "zork"))
        assertEquals(listOf<Scraper>(scraper), refused.toList())

        usable = null
        assertEquals("no usable account asks nothing", BoxArtSearchOutcome.Failed(BoxArtProblem.SignInAgain), fixes.search(id, "zork"))
        assertEquals(BoxArtStoreOutcome.Failed(BoxArtProblem.SignInAgain), fixes.pick(id, hit) {})
        assertEquals(1, scraper.searches.size)
    }

    @Test
    fun withoutWifiNothingIsAskedAndItSaysSo() = runBlocking<Unit> {
        val id = namedGame()
        onWifi = false

        assertEquals(BoxArtSearchOutcome.Failed(BoxArtProblem.NoWifi), fixes.search(id, "zork"))
        assertEquals(BoxArtStoreOutcome.Failed(BoxArtProblem.NoWifi), fixes.pick(id, hit) {})
        assertNull("no thumbnail download", fixes.thumbnail(hit))
        assertTrue(scraper.searches.isEmpty() && scraper.byId.isEmpty() && scraper.downloads.isEmpty())
        assertEquals(UiText.Res(R.string.game_fix_box_art_no_wifi), boxArtProblemText(BoxArtProblem.NoWifi))
    }

    @Test
    fun aGameBeingNamedOrGoneAsksNothing() = runBlocking<Unit> {
        val id = namedGame()
        assertEquals("a rescan is naming it", 1, db.scrapedGameDao().nameAgainIfTrust(id, "FILE_NAME"))
        assertEquals(BoxArtSearchOutcome.GameNotReady, fixes.search(id, "zork"))
        assertEquals(BoxArtStoreOutcome.NotNamedYet, fixes.pick(id, hit) {})
        assertEquals(BoxArtStoreOutcome.GameGone, fixes.pick(id + 100, hit) {})
        assertTrue(scraper.searches.isEmpty() && scraper.byId.isEmpty())
    }

    @Test
    fun aPickIsStoredAsThePersonsChoiceWithItsPicturesAtBothSizes() = runBlocking<Unit> {
        val id = namedGame()
        var picturesTold = 0

        val outcome = fixes.pick(id, hit) { picturesTold++ }

        assertEquals(BoxArtStoreOutcome.Stored, outcome)
        assertEquals(1, picturesTold)
        val request = scraper.byId.single()
        assertEquals("2144", request.first)
        assertTrue("a pick is fetched fresh", request.second.fresh)
        assertEquals("snes", request.second.systemId)
        val row = db.scrapedGameDao().get(KEY)!!
        assertTrue(row.isPersonChoice)
        assertEquals(ScrapeOutcome.MATCHED, row.outcome)
        assertEquals("2144", row.screenScraperId)
        assertEquals(
            listOf(ScrapedPicture.BOX_FRONT to "grid", ScrapedPicture.BOX_FRONT to "large", ScrapedPicture.SUPPORT to "grid", ScrapedPicture.SUPPORT to "large"),
            ScrapedPictures.decode(row.pictures).map { it.kind to it.sizeName },
        )
        assertTrue("every saved file is there", ScrapedPictures.decode(row.pictures).all { File(it.path).isFile })
        assertEquals("box front and disc, at most the largest size", listOf("box-front" to 1240, "support" to 1240), scraper.downloads.map { it.first to it.second })
        assertEquals("a box front is capped at 720, a disc keeps the largest size", listOf("box-front" to 720, "support" to 1080), scraper.askedHeights.map { it.first to it.second })
        val game = db.gameDao().game(id)!!
        assertEquals("Picked Zork" to NameTrust.SCRAPER_PERSON_CHOICE.name, game.title to game.nameTrust)
        assertTrue("a pick needs no naming pass", namings.isEmpty())
        assertTrue(logs.none { "Zork" in it || "example.invalid" in it })
    }

    @Test
    fun aPickSavesItsBoxFrontFromAJpegBodyAtItsOwnSizeWithoutEnlarging() = runBlocking<Unit> {
        val id = namedGame()
        assertEquals(BoxArtStoreOutcome.Stored, fixes.pick(id, hit) {})
        val boxFront = ScrapedPictures.decode(db.scrapedGameDao().get(KEY)!!.pictures).filter { it.kind == ScrapedPicture.BOX_FRONT }
        assertEquals(listOf("grid", "large"), boxFront.map { it.sizeName })
        // The fake's JPEG is 40 x 56: smaller than both sizes, so neither is enlarged.
        assertTrue(boxFront.all { it.width == 40 && it.height == 56 })
        assertTrue(boxFront.all { File(it.path).isFile && File(it.path).length() > 0 })
    }

    @Test
    fun aDiscPictureThatFailsDoesNotHoldThePickBack() = runBlocking<Unit> {
        val id = namedGame()
        scraper.downloadAnswer = { kind -> if (kind == MediaKind.Support) MediaDownload.ServerError(502) else null }
        assertEquals(BoxArtStoreOutcome.Stored, fixes.pick(id, hit) {})
        assertEquals(listOf(ScrapedPicture.BOX_FRONT, ScrapedPicture.BOX_FRONT), ScrapedPictures.decode(db.scrapedGameDao().get(KEY)!!.pictures).map { it.kind })
    }

    @Test
    fun aPickThatFailsStoresNothing() = runBlocking<Unit> {
        val id = namedGame()
        val cases =
            listOf<Pair<() -> Unit, BoxArtStoreOutcome>>(
                { scraper.byIdAnswer = ScrapeResult.NoMatch } to BoxArtStoreOutcome.NotFound,
                { scraper.byIdAnswer = ScrapeResult.Paused(9_000, PauseReason.DailyQuota) } to BoxArtStoreOutcome.Failed(BoxArtProblem.DailyLimit(9_000)),
                { scraper.byIdAnswer = ScrapeResult.Failed(FailureReason.Network) } to BoxArtStoreOutcome.Failed(BoxArtProblem.Offline),
                {
                    scraper.byIdAnswer = null
                    scraper.downloadAnswer = { MediaDownload.ServerError(500) }
                } to BoxArtStoreOutcome.Failed(BoxArtProblem.ServerError(500)),
                { scraper.downloadAnswer = { MediaDownload.NetworkDown } } to BoxArtStoreOutcome.Failed(BoxArtProblem.Offline),
                { scraper.downloadAnswer = { MediaDownload.Paused(4_000, PauseReason.DailyQuota) } } to BoxArtStoreOutcome.Failed(BoxArtProblem.DailyLimit(4_000)),
            )
        for ((set, expected) in cases) {
            set()
            assertEquals(expected, fixes.pick(id, hit) {})
            assertNull("nothing stored for $expected", db.scrapedGameDao().get(KEY))
        }
        assertEquals("the name is unchanged", "Zork Quest (file)", db.gameDao().game(id)!!.title)
    }

    @Test
    fun aPauseWhileThePicturesAreSavedSaysWhichPause() = runBlocking<Unit> {
        val id = namedGame()
        scraper.downloadAnswer = { MediaDownload.Paused(4_000, PauseReason.ServerClosed) }
        assertEquals(BoxArtStoreOutcome.Failed(BoxArtProblem.ServerClosed(4_000)), fixes.pick(id, hit) {})
        scraper.downloadAnswer = { MediaDownload.Paused(5_000, PauseReason.DailyQuota) }
        assertEquals(BoxArtStoreOutcome.Failed(BoxArtProblem.DailyLimit(5_000)), fixes.pick(id, hit) {})
        assertNull(db.scrapedGameDao().get(KEY))
    }

    @Test
    fun aPickWhoseBoxFrontSavesNothingIsNotStored() = runBlocking<Unit> {
        val id = namedGame()
        scraper.downloadAnswer = { kind -> if (kind == MediaKind.BoxFront) MediaDownload.Broken else null }
        assertEquals(BoxArtStoreOutcome.NoBoxFront, fixes.pick(id, hit) {})
        assertNull(db.scrapedGameDao().get(KEY))
        assertEquals("Zork Quest (file)", db.gameDao().game(id)!!.title)
    }

    @Test
    fun aWriteAlreadyBegunFinishesWhenThePersonLeavesAndStillAsksForTheNaming() = runBlocking<Unit> {
        val id = namedGame()
        assertEquals(BoxArtStoreOutcome.Stored, fixes.pick(id, hit) {})
        storeStarted = CompletableDeferred()
        val gate = CompletableDeferred<Unit>().also { storeGate = it }

        val leaving = CoroutineScope(Dispatchers.IO).async { fixes.noBoxArt(id) }
        withTimeout(5_000) { storeStarted.await() }
        leaving.cancel()
        gate.complete(Unit)
        leaving.join()

        assertEquals("the write landed", ScrapeOutcome.NO_MATCH, db.scrapedGameDao().get(KEY)!!.outcome)
        assertEquals("and the naming it needs was asked for", listOf("now"), namings.toList())
    }

    @Test
    fun aPickBegunToBeWrittenIsStoredThoughThePersonLeft() = runBlocking<Unit> {
        val id = namedGame()
        val gate = CompletableDeferred<Unit>().also { storeGate = it }
        val leaving = CoroutineScope(Dispatchers.IO).async { fixes.pick(id, hit) {} }
        withTimeout(5_000) { storeStarted.await() }
        leaving.cancel()
        gate.complete(Unit)
        leaving.join()
        assertTrue(db.scrapedGameDao().get(KEY)!!.isPersonChoice)
        assertEquals("Picked Zork", db.gameDao().game(id)!!.title)
    }

    @Test
    fun aNewPictureBehindTheSameUrlIsDownloadedAgain() = runBlocking<Unit> {
        val old = fixes.thumbnail(hit.copy(thumbnail = hit.thumbnail!!.copy(md5 = "aaaa")))
        val new = fixes.thumbnail(hit.copy(thumbnail = hit.thumbnail!!.copy(md5 = "bbbb")))
        assertTrue(old != null && new != null && old != new)
        assertEquals(2, scraper.downloads.size)
        assertEquals(old, fixes.thumbnail(hit.copy(thumbnail = hit.thumbnail!!.copy(md5 = "aaaa"))))
        assertEquals(2, scraper.downloads.size)
    }

    @Test
    fun aPickCancelledBeforeItsWriteStoresNothing() = runBlocking<Unit> {
        val id = namedGame()
        val asked = CompletableDeferred<Unit>()
        scraper.byIdGate = { asked.complete(Unit); awaitCancellation() }
        val pick = CoroutineScope(Dispatchers.IO).async { fixes.pick(id, hit) {} }
        withTimeout(5_000) { asked.await() }
        pick.cancelAndJoin()
        assertNull(db.scrapedGameDao().get(KEY))
        assertTrue(scraper.downloads.isEmpty())
    }

    @Test
    fun noBoxArtAfterAPickNamesTheGameAgainAtOnce() = runBlocking<Unit> {
        val id = namedGame()
        assertEquals(BoxArtStoreOutcome.Stored, fixes.pick(id, hit) {})

        assertEquals(BoxArtStoreOutcome.Stored, fixes.noBoxArt(id))

        assertEquals("the naming pass is asked before the answer comes back", listOf("now"), namings.toList())
        val row = db.scrapedGameDao().get(KEY)!!
        assertTrue(row.isPersonChoice)
        assertEquals(ScrapeOutcome.NO_MATCH, row.outcome)
        assertEquals(emptyList<ScrapedPicture>(), ScrapedPictures.decode(row.pictures))
        assertTrue("waiting for the identifier's name", db.gameDao().game(id)!!.needsIdentify)
    }

    @Test
    fun noBoxArtOnAGameNeverPickedKeepsItsNameAndNamesNothing() = runBlocking<Unit> {
        val id = namedGame()
        assertEquals(BoxArtStoreOutcome.Stored, fixes.noBoxArt(id))
        assertTrue(namings.isEmpty())
        assertFalse(db.gameDao().game(id)!!.needsIdentify)
        assertEquals(BoxArtStoreOutcome.GameGone, fixes.noBoxArt(id + 100))
    }

    @Test
    fun aThumbnailIsDownloadedSmallOnceAndKeptInTheCache() = runBlocking<Unit> {
        val file = fixes.thumbnail(hit)
        assertNotNull(file)
        assertTrue(file!!.isFile && file.parentFile == thumbDir)
        assertEquals(listOf("box-front" to GraphBoxArtFixes.THUMBNAIL_MAX_PX), scraper.downloads.map { it.first to it.second })
        assertEquals("a later search costs no request", file, fixes.thumbnail(hit))
        assertEquals(1, scraper.downloads.size)
        assertNull("a hit with no box front asks nothing", fixes.thumbnail(hit.copy(id = "5", thumbnail = null)))
        scraper.downloadAnswer = { MediaDownload.Broken }
        assertNull(fixes.thumbnail(hit.copy(id = "6")))
        assertTrue("no part file is left", thumbDir.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun theEntryIsOfferedWithAStoredAccountInABuildWithCredentials() = runBlocking<Unit> {
        val store = ScraperAccountStore.create(context, CoroutineScope(Dispatchers.IO + storeJob))
        fun accounts(isAvailable: Boolean) =
            GraphScraperAccounts(
                isAvailable = isAvailable,
                store = { store },
                cipher = { PlainCipher },
                makeScraper = { scraper },
                pauseStore = { null },
                scope = CoroutineScope(Dispatchers.IO + storeJob),
                io = Dispatchers.IO,
                main = Dispatchers.Unconfined,
                log = { logs += it },
            )
        fun offered(accounts: GraphScraperAccounts) =
            GraphBoxArtFixes(
                scraper = accounts::scraper,
                markRefused = accounts::markRefused,
                isSignedIn = { accounts.isSignedIn.first() },
                unmeteredNetwork = { true },
                source = { source },
                gameExists = { true },
                mediaDir = { mediaDir },
                thumbnailDir = { thumbDir },
                lowRamDevice = { false },
                nameAgain = {},
                io = Dispatchers.IO,
                log = { logs += it },
            )
        val withCredentials = offered(accounts(isAvailable = true))
        assertFalse("signed out", withCredentials.isOffered())

        store.signIn("plumtester", PlainCipher.encrypt("secret"))
        assertTrue("signed in", withCredentials.isOffered())
        assertFalse("a build without developer credentials", offered(accounts(isAvailable = false)).isOffered())

        // An account that cannot be used is said, not hidden. The panel then
        // answers "sign in again" (aRefusedPasswordMarksTheAccountAndSaysSignInAgain).
        store.markRefused("plumtester", PlainCipher.encrypt("secret"))
        assertTrue("refused by ScreenScraper: offered, and it says so", withCredentials.isOffered())
        assertEquals(BoxArtSearchOutcome.Failed(BoxArtProblem.SignInAgain), withCredentials.search(namedGame(), "zork"))
        store.signOut()
    }

    /** A named, listed game with one fix key, as the identifier leaves it. */
    private suspend fun namedGame(): Long {
        val scanDao = db.scanDao()
        val folder = scanDao.insertFolder(RomFolderEntity(treeUri = "content://test/tree/x", displayName = "x", addedAt = 1))
        scanDao.applyBatch(ScanBatch(folder, systems = listOf(SystemEntity("snes", "snes")), newGames = listOf(gameRow(folder))))
        val id = scanDao.gamesInFolder(folder).single().id
        val dao = db.identifyDao()
        assertEquals(1, dao.recordIdentification(id, 1, 1, "zq.sfc", null, null, null, FixKeys.join(listOf(KEY)), "FILE_NAME", "UNKNOWN", packVersion = 1))
        dao.setShownName(id, "Zork Quest (file)", "zork quest (file)", "", "FILE_NAME", isNotGame = false)
        return id
    }

    private fun gameRow(folderId: Long) =
        GameEntity(
            folderId = folderId,
            documentId = "zq",
            parentDocumentId = "root",
            relativePath = "snes/zq.sfc",
            fileName = "zq.sfc",
            sizeBytes = 1,
            modifiedAt = 1,
            title = "zq",
            sortName = "zq",
            regions = "",
            discGroup = null,
            discCount = 1,
            systemId = "snes",
            isPrimaryCopy = true,
        )

    /** ScreenScraper as the tests need it; pictures are real PNGs so the saver can decode them. */
    private class FakeScraper : Scraper {
        override val available: Boolean = true
        var searchAnswer: SearchOutcome = SearchOutcome.NothingFound
        val searches = CopyOnWriteArrayList<Triple<String, String, List<String>>>()
        var byIdAnswer: ScrapeResult? = null
        var byIdGate: suspend () -> Unit = {}
        val byId = CopyOnWriteArrayList<Pair<String, ScrapeRequest>>()

        /** A download answer by kind; null downloads a picture. */
        var downloadAnswer: (MediaKind) -> MediaDownload? = { null }
        val downloads = CopyOnWriteArrayList<Pair<String, Int?>>()
        val askedHeights = CopyOnWriteArrayList<Pair<String, Int?>>()

        override suspend fun scrape(request: ScrapeRequest): ScrapeResult = error("not used")

        override suspend fun search(title: String, systemId: String, regions: List<String>): SearchOutcome {
            searches += Triple(title, systemId, regions)
            return searchAnswer
        }

        override suspend fun byGameId(gameId: String, request: ScrapeRequest): ScrapeResult {
            byId += gameId to request
            byIdGate()
            return byIdAnswer ?: picked(gameId)
        }

        override suspend fun download(media: MediaRef, target: File, maxWidth: Int?, maxHeight: Int?): MediaDownload {
            val label = if (media.kind == MediaKind.BoxFront) "box-front" else "support"
            downloads += label to maxWidth
            askedHeights += label to maxHeight
            downloadAnswer(media.kind)?.let { return it }
            // ScreenScraper answers a scaled box front as a JPEG (outputformat=jpg); a disc stays PNG.
            target.writeBytes(if (media.kind == MediaKind.BoxFront) jpeg() else png())
            return MediaDownload.Done(target)
        }

        override suspend fun checkAccount(): AccountCheck = AccountCheck.Ok(maxThreads = 1, requestsToday = 0, maxRequestsPerDay = 20_000)

        private fun picked(gameId: String) =
            ScrapeResult.Matched(
                match = GameMatch(gameId, MatchedBy.PersonChoice),
                info = GameInfo("Picked Zork", null, 1991, null, null, "Galfen Soft", null, null),
                media = listOf(
                    MediaRef(MediaKind.BoxFront, "us", "https://example.invalid/box.png"),
                    MediaRef(MediaKind.Support, "us", "https://example.invalid/disc.png"),
                ),
                details = GameDetails(),
            )

        private fun jpeg(): ByteArray {
            val bitmap = Bitmap.createBitmap(40, 56, Bitmap.Config.RGB_565).apply { eraseColor(Color.rgb(51, 102, 153)) }
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        }

        private fun png(): ByteArray {
            val bitmap = Bitmap.createBitmap(40, 56, Bitmap.Config.RGB_565).apply { eraseColor(Color.rgb(51, 102, 153)) }
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
    }

    private object PlainCipher : PasswordCipher {
        override fun encrypt(plain: String): String = "c:$plain"

        override fun decrypt(stored: String): String? = stored.removePrefix("c:")
    }

    private companion object {
        const val KEY = "crc:0000abcd:1024"
        const val NOW = 1_000_000L
    }
}
