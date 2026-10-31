package com.plumemu.library.scrape

import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.DAY
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.boxFront
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.matched
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit item 21: a game whose lookup failed on a server error or a timeout on three different days
 * is no longer offered by `pending`; Download missing leaves it, Re-download all asks it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrapeRetryCapTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var lib: ScrapeTestLibrary
    @Before
    fun setUp() {
        lib = ScrapeTestLibrary(temp.newFile("plumemu.db").also { it.delete() })
    }

    @After
    fun tearDown() = lib.close()

    private suspend fun docs(source: LibraryScrapeSource) = source.pending(100).map { it.rom.fileName }.sorted()

    private suspend fun failOnDay(source: LibraryScrapeSource, doc: String, daysFromStart: Int) {
        lib.now = START + daysFromStart * DAY
        source.failedAsking(source.pending(100).single { it.rom.fileName == doc })
    }

    @Test
    fun threeFailedDaysStopTheAskingDownloadMissingKeepsItStoppedAndReDownloadAllAsksAgain() = runTest {
        val source = lib.source()
        lib.add(lib.game("bad.sfc"), lib.game("fine.sfc"))
        failOnDay(source, "bad.sfc", 0)
        failOnDay(source, "bad.sfc", 0)
        failOnDay(source, "bad.sfc", 1)
        assertEquals("two days: asked again", listOf("bad.sfc", "fine.sfc"), docs(source))
        failOnDay(source, "bad.sfc", 2)
        assertEquals("the third day stops it, the other game is untouched", listOf("fine.sfc"), docs(source))
        source.askAgain(AskAgain.Missing)
        assertEquals("Download missing does not lift it", listOf("fine.sfc"), docs(source))
        source.askAgain(AskAgain.All)
        assertEquals("Re-download all does", listOf("bad.sfc", "fine.sfc"), docs(source))
    }

    @Test
    fun aGameThatFinallyAnswersLeavesNoCount() = runTest {
        val source = lib.source()
        lib.add(lib.game("bad.sfc"))
        failOnDay(source, "bad.sfc", 0)
        failOnDay(source, "bad.sfc", 1)
        source.pending(100).forEach { source.deliver(it, matched(), listOf(boxFront())) }
        assertEquals(emptyList<String>(), docs(source))
        assertEquals(0, lib.db.scrapedGameDao().countGivenUp())
        assertEquals(0, lib.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM scrape_failure").use { it.moveToFirst(); it.getInt(0) })
    }

    private companion object {
        const val START = 1_800_000_000_000L
    }
}
