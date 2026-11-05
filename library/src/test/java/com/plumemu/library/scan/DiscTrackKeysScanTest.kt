package com.plumemu.library.scan

import com.plumemu.coredata.db.FixKeys
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.coredata.db.ScrapeMatchedBy
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedGameDao
import com.plumemu.coredata.db.ScrapedGameEntity
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File

/**
 * Discs named by their track sizes in a scan: the keys are added to cached discs once, and they only
 * look a title up, so a disc's fixes and ScreenScraper row stay its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscTrackKeysScanTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var fx: ScanFixture

    @Before
    fun setUp() {
        ShadowLog.clear()
        fx = ScanFixture(temp.newFolder("fixture"), copyFixtures = false)
    }

    @After
    fun tearDown() = fx.close()

    private fun write(path: String, bytes: ByteArray, time: Long = FILE_TIME) =
        File(fx.roms, path).apply { parentFile?.mkdirs(); writeBytes(bytes); setLastModified(time) }

    @Test
    fun aDiscCachedBeforeTrackKeysGetsThemOnceAndIsNamedBySizes() = runTest {
        val sizes = listOf(2_352L * 100, 2_352L * 40)
        val sized = TestNaming.naming(temp.newFolder("packs-track"), trackReleases = mapOf("psx" to listOf("Harbor Lights" to sizes)))
        write("psx/disc1.cue", ("FILE \"a.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\nFILE \"b.bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 01 00:00:00\n").toByteArray())
        write("psx/a.bin", ByteArray(sizes[0].toInt()))
        write("psx/b.bin", ByteArray(sizes[1].toInt()))
        scanWith(sized)
        val first = fx.game("disc1.cue")
        assertEquals("Harbor Lights", first.title)
        assertEquals("TRACK_SIZES", first.idMethod)
        assertTrue(first.fingerprint.toString(), first.fingerprint!!.contains("\"trk\""))

        // As an older scan stored it: a fingerprint without keys (but from the current disc reader, "rv":2), named from the file.
        val old = """{"v":1,"rv":2,"size":${first.sizeBytes},"container":"CUE"}"""
        fx.db.openHelper.writableDatabase.execSQL(
            "UPDATE game SET fingerprint = ?, needs_identify = 1, title = 'disc1' WHERE id = ?",
            arrayOf<Any>(old, first.id),
        )
        scanWith(sized)
        assertEquals("sizes name it from the cached disc, once", "Harbor Lights", fx.game("disc1.cue").title)
        assertTrue(fx.game("disc1.cue").fingerprint!!.contains("\"trk\""))
        assertTrue("the cue and its track files were opened for their sizes", fx.tree.opened.isNotEmpty())

        fx.db.openHelper.writableDatabase.execSQL("UPDATE game SET needs_identify = 1 WHERE id = ?", arrayOf<Any>(first.id))
        scanWith(sized)
        assertEquals("then cached again", emptyList<String>(), fx.tree.opened)
    }

    private suspend fun scanWith(with: GameNaming) {
        fx.tree.opened.clear()
        fx.scanner(naming = with).scan().toList()
    }

    /** A two-file psx disc `psx/<name>.cue` whose track files have [sizes]; no serial. */
    private fun writeDisc(name: String, sizes: List<Long>) {
        val cue = "FILE \"${name}1.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n" +
            "FILE \"${name}2.bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 01 00:00:00\n"
        write("psx/$name.cue", cue.toByteArray())
        write("psx/${name}1.bin", ByteArray(sizes[0].toInt()))
        write("psx/${name}2.bin", ByteArray(sizes[1].toInt()))
    }

    /** The row's ScreenScraper key, as the screens and the scrape source compute it. */
    private fun scrapeKeyOf(id: Long): String =
        fx.db.openHelper.readableDatabase.query("SELECT ${ScrapedGameDao.SCRAPE_KEY} FROM game WHERE id = ?", arrayOf<Any?>(id)).use {
            assertTrue(it.moveToFirst())
            it.getString(0)
        }

    private fun scraped(key: String, title: String, personChoice: Boolean) = ScrapedGameEntity(
        scrapeKey = key, gameId = null, outcome = ScrapeOutcome.MATCHED, screenScraperId = "900",
        matchedBy = ScrapeMatchedBy.HASH, isPersonChoice = personChoice, scrapedAt = 1, detailsJson = null,
        title = title, year = null, publisher = null, players = null, genre = null, hasBoxFront = true,
    )

    /**
     * Track sizes only look a title up. Two different discs with one layout (one GD-ROM layout, a
     * padded CD-i length) must not share a person's fix or a ScreenScraper row.
     */
    @Test
    fun twoGamesWithEqualTrackSizesShareNoFixAndNoScreenScraperRow() = runTest {
        val sizes = listOf(2_352L * 100, 2_352L * 40)
        val sized = TestNaming.naming(temp.newFolder("packs-same"), trackReleases = mapOf("psx" to listOf("Harbor Lights" to sizes)))
        writeDisc("harbor", sizes)
        writeDisc("homebrew", sizes)
        scanWith(sized)
        val a = fx.game("harbor.cue")
        val b = fx.game("homebrew.cue")
        assertEquals("both named by their sizes", "TRACK_SIZES", b.idMethod)

        assertTrue(fx.db.identifyDao().setFix(a.id, NameFixKind.OWN_NAME, "My Harbor", "my harbor", null, fixedAt = 1))
        fx.db.scrapedGameDao().insertOrReplace(scraped(scrapeKeyOf(a.id), "Harbor Lights (SS)", personChoice = true))
        fx.db.openHelper.writableDatabase.execSQL("UPDATE game SET needs_identify = 1")
        scanWith(sized)
        assertEquals("My Harbor", fx.game("harbor.cue").title)
        assertEquals("the other game never shows the fix", "Harbor Lights", fx.game("homebrew.cue").title)
        assertEquals("nor the pick", null, fx.db.scrapedGameDao().get(scrapeKeyOf(b.id)))
        assertEquals("Harbor Lights (SS)", fx.db.scrapedGameDao().get(scrapeKeyOf(a.id))?.title)
        assertEquals("no fix key from sizes", emptyList<String>(), FixKeys.split(fx.game("homebrew.cue").fixKeys))
    }

    /**
     * A disc's ScreenScraper match and the person's pick stay with it when the naming pass adds
     * its track keys, and when a new pack names the system again: the ScreenScraper key is the
     * first fix key, which track keys never change.
     */
    @Test
    fun aDiscKeepsItsScreenScraperMatchAndPickThroughTrackKeysAndANewPack() = runTest {
        val sizesA = listOf(2_352L * 100, 2_352L * 40)
        val sizesB = listOf(2_352L * 120, 2_352L * 40)
        val releases = mapOf("psx" to listOf("Harbor Lights" to sizesA, "Moon Harbor" to sizesB))
        val v1 = TestNaming.naming(temp.newFolder("packs-v1"), trackReleases = releases)
        writeDisc("matched", sizesA)
        writeDisc("picked", sizesB)
        scanWith(v1)
        val matched = fx.game("matched.cue")
        val picked = fx.game("picked.cue")
        // As a build before track keys stored them.
        for (row in listOf(matched, picked)) {
            fx.db.openHelper.writableDatabase.execSQL(
                "UPDATE game SET fingerprint = ?, fix_keys = '', needs_identify = 1 WHERE id = ?",
                arrayOf<Any>("""{"v":1,"rv":2,"size":${row.sizeBytes},"container":"CUE"}""", row.id),
            )
        }
        val keys = mapOf(matched.id to scrapeKeyOf(matched.id), picked.id to scrapeKeyOf(picked.id))
        fx.db.scrapedGameDao().insertOrReplace(scraped(keys.getValue(matched.id), "Harbor Lights", personChoice = false))
        fx.db.scrapedGameDao().insertOrReplace(scraped(keys.getValue(picked.id), "Picked Harbor", personChoice = true))

        suspend fun assertKept(step: String) {
            for (row in listOf(matched, picked)) {
                assertEquals("$step: same ScreenScraper key", keys.getValue(row.id), scrapeKeyOf(row.id))
                val stored = fx.db.scrapedGameDao().get(scrapeKeyOf(row.id))
                assertNotNull("$step: match kept", stored)
                assertTrue("$step: box art kept", stored?.hasBoxFront == true)
            }
            assertEquals("$step: the pick kept", true, fx.db.scrapedGameDao().get(scrapeKeyOf(picked.id))?.isPersonChoice)
        }
        scanWith(v1)
        assertTrue(fx.game("matched.cue").fingerprint.toString(), fx.game("matched.cue").fingerprint.toString().contains("\"trk\""))
        assertKept("track keys added")
        assertEquals("Picked Harbor", fx.game("picked.cue").title)

        scanWith(TestNaming.naming(temp.newFolder("packs-v2"), trackReleases = releases, trackVersion = 2))
        assertEquals("named again by the new pack", 2L, fx.game("matched.cue").packVersion)
        assertKept("new pack")
        assertEquals("Picked Harbor", fx.game("picked.cue").title)
    }

    private companion object {
        const val FILE_TIME = 1_600_000_000_000L
    }
}
