package com.plumemu.scraper

import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** "Box art only" says why its source failed: no network, or the server did not answer; a later success clears it. */
class LibretroFailureTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture
    private lateinit var scraper: Scraper
    private var code = 200

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
        fx.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(code).build()
        }
        scraper = Scraper.createLibretroOnly(fx.config())
    }

    @After fun tearDown() = fx.close()

    private fun request() = fx.request(fx.rom("Game (USA).nds", "x"), systemId = "nds", title = "Game")

    @Test fun `nothing has failed before the first request`() {
        assertNull(scraper.pictureFailure.value)
    }

    @Test fun `a server that answers 503 is the server not answering, logged`() = runTest {
        code = 503
        assertTrue(scraper.fallbackArt(request()) is FallbackArt.Failed)
        assertEquals(PictureFailure.ServerDidNotAnswer, scraper.pictureFailure.value)
        assertTrue(fx.logLines.any { it.contains("HTTP 503") })
    }

    @Test fun `a later answer clears the failure`() = runTest {
        code = 503
        scraper.fallbackArt(request())
        code = 200
        assertTrue(scraper.fallbackArt(request()) is FallbackArt.Found)
        assertNull(scraper.pictureFailure.value)
    }

    @Test fun `no server reachable is no network, logged`() = runTest {
        fx.server.close()
        assertTrue(scraper.fallbackArt(request()) is FallbackArt.Failed)
        assertEquals(PictureFailure.NoNetwork, scraper.pictureFailure.value)
        assertTrue(fx.logLines.any { it.contains("network error") })
    }
}
