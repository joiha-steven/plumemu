package com.plumemu.graph

import androidx.work.WorkInfo
import com.plumemu.coredata.settings.BoxArtSourceSetting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which scraper a run gets for each box art source: ScreenScraper needs a usable account, "Box art
 * only" runs with no account on the libretro-only scraper, Plumemu's own covers make no request.
 */
internal class GraphBoxArtSourceTest : GraphScrapesHarness() {
    private suspend fun answerOf(call: (onDone: (Boolean) -> Unit) -> Unit): Boolean {
        val answer = CompletableDeferred<Boolean>()
        call { answer.complete(it) }
        return withTimeout(5_000) { answer.await() }
    }

    @Test
    fun notChosenFollowsTheAccount() = runBlocking<Unit> {
        val scrapes = graph()
        assertNull("no account, nothing chosen: own covers, no work", scrapes.scraperWork())
        scrapes.requestRun().join()
        assertEquals(emptyList<WorkInfo>(), runs())

        signIn(scrapes)
        awaitRun()
        assertSame("a usable account: ScreenScraper", made.single(), scrapes.scraperWork()?.scraper)
        assertTrue(libretroMade.isEmpty())
    }

    @Test
    fun boxArtOnlyRunsWithNoAccountOnTheLibretroScraperAndAsksForARun() = runBlocking<Unit> {
        chosenSource.value = BoxArtSourceSetting.LibretroOnly
        val scrapes = graph()

        val work = checkNotNull(scrapes.scraperWork())
        assertSame(libretroMade.single(), work.scraper)
        assertTrue("no ScreenScraper scraper was made", made.isEmpty())

        scrapes.requestRun().join()
        assertEquals(WorkInfo.State.ENQUEUED, runs().single().state)
        assertTrue("one libretro scraper for the process", scrapes.scraperWork()?.scraper === libretroMade.single())
    }

    @Test
    fun boxArtOnlyWhileSignedInStillNeverUsesTheScreenScraperScraper() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        awaitRun()
        chosenSource.value = BoxArtSourceSetting.LibretroOnly

        val work = checkNotNull(scrapes.scraperWork())
        assertSame(libretroMade.single(), work.scraper)
        assertTrue("the account's scraper was not used", made.none { it === work.scraper })
    }

    @Test
    fun ownCoversMakeNoWorkAndNoRunEvenSignedIn() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        awaitRun()
        assertTrue(answerOf { scrapes.setSource(BoxArtSourceSetting.OwnCovers, it) })

        assertEquals("the waiting run is stopped", emptyList<WorkInfo>(), runs())
        assertNull(scrapes.scraperWork())
        scrapes.requestRun().join()
        assertEquals(emptyList<WorkInfo>(), runs())
        assertTrue(libretroMade.isEmpty())
    }

    @Test
    fun choosingBoxArtOnlyStoresItAndAsksForARunChoosingOwnCoversStopsIt() = runBlocking<Unit> {
        val scrapes = graph()

        assertTrue(answerOf { scrapes.setSource(BoxArtSourceSetting.LibretroOnly, it) })
        assertEquals(BoxArtSourceSetting.LibretroOnly, chosenSource.value)
        assertEquals(WorkInfo.State.ENQUEUED, awaitRun().state)

        assertTrue(answerOf { scrapes.setSource(BoxArtSourceSetting.OwnCovers, it) })
        assertEquals(BoxArtSourceSetting.OwnCovers, chosenSource.value)
        assertEquals(emptyList<WorkInfo>(), runs())
    }

    @Test
    fun aSourceThatCouldNotBeStoredIsReportedAndStartsNothing() = runBlocking<Unit> {
        val scrapes = graph()
        sourceWriteFails = true

        assertEquals(false, answerOf { scrapes.setSource(BoxArtSourceSetting.LibretroOnly, it) })

        assertNull(chosenSource.value)
        assertEquals(emptyList<WorkInfo>(), runs())
    }

    @Test
    fun pausedBoxArtOnlyAsksForNoRun() = runBlocking<Unit> {
        paused.value = true
        val scrapes = graph()

        assertTrue(answerOf { scrapes.setSource(BoxArtSourceSetting.LibretroOnly, it) })
        scrapes.requestRun().join()

        assertEquals(emptyList<WorkInfo>(), runs())
    }
}
