package com.plumemu.content

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.ScrapeMatchedBy
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedGameEntity
import com.plumemu.coredata.db.ScrapedPicture
import com.plumemu.coredata.db.ScrapedPictures
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.ArtPicture
import com.plumemu.home.CoverImages
import com.plumemu.home.DetailsExtras
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.TextByLanguage
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * The home screen's ScreenScraper results from a real (in-memory) `scraped_game` table, and the
 * Coil decoding of a saved picture: only the asked keys, a "no match" shows nothing,
 * the English synopsis from the stored details, a closed database shows no art and logs its class,
 * a picture decoded no larger than drawn and kept in memory, an unreadable file leaves the
 * placeholder.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class LibraryHomeArtTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    private val logged = mutableListOf<String>()
    private val art = LibraryHomeArt({ db.scrapedGameDao() }, CoverImages.None, Dispatchers.Default) { logged += it }

    @After
    fun tearDown() = db.close()

    private fun matched(
        key: String,
        pictures: List<ScrapedPicture>,
        details: GameDetails? = null,
    ) = ScrapedGameEntity(
        scrapeKey = key,
        gameId = null,
        outcome = ScrapeOutcome.MATCHED,
        screenScraperId = "1",
        matchedBy = ScrapeMatchedBy.HASH,
        scrapedAt = 0,
        detailsJson = details?.toJson(),
        title = "Super Metroid",
        year = 1994,
        publisher = "Nintendo",
        players = "1",
        genre = "Action",
        pictures = ScrapedPictures.encode(pictures),
        hasBoxFront = pictures.isNotEmpty(),
    )

    @Test
    fun theAskedKeysMapToTheirPicturesAndFacts() = runBlocking {
        val grid = ScrapedPicture(ScrapedPicture.BOX_FRONT, "grid", "/m/a-grid.webp", 548, 400)
        val large = ScrapedPicture(ScrapedPicture.BOX_FRONT, "large", "/m/a-large.webp", 1240, 905)
        val disc = ScrapedPicture(ScrapedPicture.SUPPORT, "grid", "/m/a-disc.webp", 400, 400)
        db.scrapedGameDao().insertOrReplace(matched("crc:a", listOf(grid, large, disc)).copy(scrapedAt = 7))
        db.scrapedGameDao().insertOrReplace(matched("crc:b", listOf(large)))
        db.scrapedGameDao().insertOrReplace(matched("crc:c", emptyList()))
        db.scrapedGameDao().insertOrReplace(matched("crc:none", emptyList()).copy(outcome = ScrapeOutcome.NO_MATCH, title = null))
        db.scrapedGameDao().insertOrReplace(matched("crc:other", listOf(grid)))

        val found = withTimeout(TIMEOUT_MS) { art.observe(listOf("crc:a", "crc:b", "crc:c", "crc:none", "crc:missing")).first() }

        assertEquals("only the asked keys with a match", setOf("crc:a", "crc:b", "crc:c"), found.keys)
        val a = found.getValue("crc:a")
        assertEquals("the grid size for the grid", "/m/a-grid.webp", a.grid?.path)
        assertEquals("its pixels, for the shape before decoding", 548 to 400, a.grid?.width to a.grid?.height)
        assertEquals("the large size for the details", "/m/a-large.webp", a.large?.path)
        assertEquals(1994, a.details.year)
        assertEquals("Nintendo", a.details.publisher)
        assertEquals("1", a.details.players)
        assertEquals("Action", a.details.genre)
        assertEquals("the row's time versions its pictures", 7L, a.grid?.version)
        assertEquals("one saved size stands in for the other", "/m/a-large.webp", found.getValue("crc:b").grid?.path)
        assertNull("a match without pictures: the title placeholder", found.getValue("crc:c").grid)
    }

    @Test
    fun aSynopsisStoredWithHtmlEntitiesIsShownDecoded() = runBlocking {
        val stored = GameDetails(synopses = listOf(TextByLanguage("en", "Beat &quot;The Master&quot; &amp; Don&#39;t panic &amp;quot;x&amp;quot;")))
        db.scrapedGameDao().insertOrReplace(matched("crc:entities", emptyList(), stored))
        assertEquals("Beat \"The Master\" & Don't panic &quot;x&quot;", art.extras("crc:entities").synopsis)
    }

    @Test
    fun aFreshScrapeOfTextWithALiteralEntityShowsItAsWritten() = runBlocking {
        // What ScrapedRows stores for an answer saying `&amp;quot;x&amp;quot;`: text as given, decoded once here.
        val stored = GameDetails(synopses = listOf(TextByLanguage("en", "&amp;quot;x&amp;quot; AT&amp;amp;T")))
        db.scrapedGameDao().insertOrReplace(matched("crc:literal", emptyList(), stored))
        assertEquals("&quot;x&quot; AT&amp;T", art.extras("crc:literal").synopsis)
    }

    @Test
    fun theSynopsisIsTheEnglishOneAndTheRatingComesWithIt() = runBlocking {
        val details = GameDetails(synopses = listOf(TextByLanguage("fr", "Samus revient."), TextByLanguage("en", "  Samus returns.  ")), rating = 17)
        db.scrapedGameDao().insertOrReplace(matched("crc:a", emptyList(), details))
        db.scrapedGameDao().insertOrReplace(matched("crc:fr", emptyList(), GameDetails(synopses = listOf(TextByLanguage("fr", "Samus revient.")))))
        db.scrapedGameDao().insertOrReplace(matched("crc:odd", emptyList(), GameDetails(rating = 35)))
        db.scrapedGameDao().insertOrReplace(matched("crc:zero", emptyList(), GameDetails(rating = 0)))
        assertEquals(DetailsExtras("Samus returns.", 17), art.extras("crc:a"))
        assertEquals("no English synopsis: none shown (the others are kept, not shown); no rating", DetailsExtras("", null), art.extras("crc:fr"))
        assertEquals("a rating out of ScreenScraper's 0 to 20 is not shown", DetailsExtras("", null), art.extras("crc:odd"))
        assertEquals("0 is no rating (ScreenScraper gives no real 0/20)", DetailsExtras("", null), art.extras("crc:zero"))
        assertEquals("no result", DetailsExtras.None, art.extras("crc:missing"))
    }

    @Test
    fun aDatabaseThatFailsShowsNoArtAndLogsItsClass() = runBlocking {
        val failing = LibraryHomeArt({ throw IllegalStateException("closed") }, CoverImages.None, Dispatchers.Default) { logged += it }
        val found = withTimeout(TIMEOUT_MS) { failing.observe(listOf("crc:a")).first() }
        assertEquals(emptyMap<String, Any>(), found)
        assertEquals(DetailsExtras.None, failing.extras("crc:a"))
        assertTrue("logged by class, never a key: $logged", logged.isNotEmpty() && logged.none { it.contains("crc:") })
    }

    @Test
    fun nothingIsAskedForNoKeys() = runBlocking {
        val failing = LibraryHomeArt({ throw IllegalStateException("closed") }, CoverImages.None, Dispatchers.Default) { logged += it }
        assertEquals(emptyMap<String, Any>(), failing.observe(emptyList()).first())
        assertTrue("no query, so nothing failed", logged.isEmpty())
    }

    @Test
    fun aSavedPictureIsDecodedNoLargerThanDrawnAndKeptInMemory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(folder.root, "box-front-grid.png")
        file.outputStream().use { Bitmap.createBitmap(560, 400, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val coverLogs = mutableListOf<String>()
        val images = CoilCoverImages(context, { CoilCoverImages.imageLoader(context, isLowRam = true, allowHardware = false) }) { coverLogs += it }

        val picture = ArtPicture(file.path, 560, 400, version = 1)
        assertNull("nothing in memory before the first decode", images.cached(picture, 280, 200))
        val decoded = assertNotNull(onMainLoop { images.load(picture, 280, 200) })
        assertTrue("decoded at the drawn size or less (${decoded.width}x${decoded.height})", decoded.width <= 280 && decoded.height <= 200)
        assertEquals("its shape kept", 560f / 400f, decoded.width.toFloat() / decoded.height, 0.02f)
        assertNotNull("kept in memory for the next frame that draws it", images.cached(picture, 280, 200))

        // A re-download writes a new file under the same path: a new row time and maybe a new shape.
        file.outputStream().use { Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val again = ArtPicture(file.path, 400, 400, version = 2)
        assertNull("the old decode is not reused for the new file", images.cached(again, 200, 200))
        val redecoded = assertNotNull(onMainLoop { images.load(again, 200, 200) })
        assertEquals("the new file's shape", 1f, redecoded.width.toFloat() / redecoded.height, 0.02f)
        assertNull("same size, same path, newer row: not the old one either", images.cached(picture.copy(version = 3), 280, 200))

        assertNull("an unreadable file leaves the placeholder", onMainLoop { images.load(ArtPicture(File(folder.root, "gone.webp").path, 10, 10), 280, 200) })
        assertTrue("logged by class: $coverLogs", coverLogs.size == 1 && !coverLogs.single().contains("gone"))
    }

    /**
     * Runs [block] off the test's main thread while turning the main looper: Coil hops to the main
     * thread inside `execute`, which a `runBlocking` on the main thread would never let it do.
     */
    private fun <T> onMainLoop(block: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        CoroutineScope(Dispatchers.Default).launch { runCatching { block() }.fold(result::complete, result::completeExceptionally) }
        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000
        while (!result.isCompleted && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(LOOPER_STEP_MS)
        }
        @OptIn(ExperimentalCoroutinesApi::class)
        return result.getCompleted()
    }

    private fun <T> assertNotNull(value: T?): T {
        assertNotNull("expected a value", value as Any?)
        return value!!
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val LOOPER_STEP_MS = 5L
    }
}
