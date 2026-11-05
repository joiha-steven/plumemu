package com.plumemu.graph

import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.scraper.PictureFailure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Box art only" has a status with no account: its run, and why its pictures failed. */
internal class GraphBoxArtStatusTest : GraphScrapesHarness() {
    private suspend fun status() = withTimeout(5_000) { scrapes.status.first() }

    @Test
    fun boxArtOnlyWithNoAccountIsNotSignedOutButIdleOrRunning() = runBlocking<Unit> {
        chosenSource.value = BoxArtSourceSetting.LibretroOnly
        graph()
        assertTrue("no account is no reason to say nothing: ${status()}", status() is ScrapeStatus.Idle)
    }

    @Test
    fun aFailedLibretroSourceSaysWhyAndLogsIt() = runBlocking<Unit> {
        chosenSource.value = BoxArtSourceSetting.LibretroOnly
        graph()
        val libretro = checkNotNull(scrapes.scraperWork()).scraper as FakeScraper
        libretro.pictureFailure.value = PictureFailure.NoNetwork
        val failed = status() as ScrapeStatus.Failed
        assertEquals(PictureFailure.NoNetwork, failed.why)
        assertTrue("the cause is logged: $logLines", logLines.any { it.contains("NoNetwork") })

        libretro.pictureFailure.value = PictureFailure.ServerDidNotAnswer
        assertEquals(PictureFailure.ServerDidNotAnswer, (status() as ScrapeStatus.Failed).why)

        libretro.pictureFailure.value = null
        assertTrue(status() is ScrapeStatus.Idle)
    }

    @Test
    fun theOtherSourcesNeverShowALibretroFailure() = runBlocking<Unit> {
        graph()
        assertEquals(ScrapeStatus.SignedOut, status())
    }
}
