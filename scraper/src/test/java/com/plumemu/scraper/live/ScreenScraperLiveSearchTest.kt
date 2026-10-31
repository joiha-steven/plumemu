package com.plumemu.scraper.live

import com.plumemu.scraper.DeveloperCredentials
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.RomSource
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.ScraperConfig
import com.plumemu.scraper.ScreenScraperScraper
import com.plumemu.scraper.SearchOutcome
import com.plumemu.scraper.TitleMatch
import com.plumemu.scraper.api.ScreenScraperApi
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Fix box art..." against the real ScreenScraper, opt-in only like
 * [ScreenScraperLiveSmokeTest]: skipped unless Gradle is given `-Pscreenscraper.live=true` and
 * the build has developer credentials; CI and `scripts/verify.sh` never pass the property.
 * Developer credentials only (no account). A search for Super Mario World on the SNES, then that
 * game by its id: two HTTP requests, counted on the engine's own client (at most 3 allowed).
 *
 * `./gradlew :scraper:testDebugUnitTest --tests '*ScreenScraperLiveSearchTest*' -Pscreenscraper.live=true`;
 * the line it prints is in `scraper/build/test-results/testDebugUnitTest/TEST-*ScreenScraperLiveSearchTest.xml`.
 * Nothing printed carries a credential: request paths only, every line through [printSafe].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenScraperLiveSearchTest {
    @get:Rule val tmp = TemporaryFolder()

    /** The path of every HTTP request the engine sent ("jeuRecherche.php", "jeuInfos.php"), no query. */
    private val requests = CopyOnWriteArrayList<String>()

    @Test
    fun superMarioWorldIsFoundBySearchAndFetchedByItsIdWithABoxFront() = runBlocking<Unit> {
        assumeTrue("live tests run only with -Pscreenscraper.live=true", System.getProperty("screenscraper.live") == "true")
        assumeTrue("live tests need a build with developer credentials", DeveloperCredentials.buildHasCredentials)
        val http = ScreenScraperApi.defaultHttpClient(::printSafe).newBuilder()
            .addNetworkInterceptor { chain ->
                requests += chain.request().url.encodedPath.substringAfterLast('/')
                chain.proceed(chain.request())
            }
            .build()
        val scraper = ScreenScraperScraper(ScraperConfig(cacheDir = tmp.newFolder("cache"), user = null, log = ::printSafe, timingLog = com.plumemu.scraper.timing.ScrapeTimingLog.Off), http)
        val regions = listOf("us", "eu", "jp", "wor", "ss")

        val search = scraper.search("Super Mario World", "snes", regions)
        printSafe("LIVE search: ${describe(search)}; ${requests.size} HTTP requests ${requests.joinToString(" ")}")
        val hits = (search as? SearchOutcome.Found)?.games
        assertNotNull("a list of games: $search", hits)
        val hit = hits!!.firstOrNull { TitleMatch.matches("Super Mario World", listOfNotNull(it.name)) }
        assertNotNull("Super Mario World in ${hits.map { it.name }}", hit)

        val request = ScrapeRequest(NoRom, "snes", "Super Mario World", regions, listOf("en"))
        val picked = scraper.byGameId(hit!!.screenScraperGameId, request)
        val matched = picked as? ScrapeResult.Matched
        printSafe(
            "LIVE byGameId ${hit.screenScraperGameId}: ${matched?.let { "game ${it.match.screenScraperGameId} by ${it.match.matchedBy}, " +
                "title ${it.info.title}, box fronts ${it.media.count { m -> m.kind == MediaKind.BoxFront }}" } ?: picked}; " +
                "${requests.size} HTTP requests ${requests.joinToString(" ")}",
        )
        assertNotNull("a match: $picked", matched)
        assertEquals(hit.screenScraperGameId, matched!!.match.screenScraperGameId)
        assertEquals(MatchedBy.PersonChoice, matched.match.matchedBy)
        assertTrue("a box front", matched.media.any { it.kind == MediaKind.BoxFront })
        assertTrue("at most 3 HTTP requests: $requests", requests.size <= 3)
    }

    private fun describe(outcome: SearchOutcome): String = when (outcome) {
        is SearchOutcome.Found ->
            "${outcome.games.size} games, first ${outcome.games.take(5).joinToString { "${it.screenScraperGameId} ${it.name} (${it.year}, box front ${it.boxFront != null})" }}"
        else -> outcome.toString()
    }

    /** The person's game is never read by a lookup by id. */
    private object NoRom : RomSource {
        override val key = "live:snes:super-mario-world"
        override val fileName = "Super Mario World (USA).sfc"
        override val sizeBytes = 524_288L
        override val lastModifiedMillis = 0L

        override fun open(): InputStream = error("byGameId never reads the ROM")
    }

    private companion object {
        private val secrets: List<String> by lazy {
            DeveloperCredentials.fromBuildConfig().let { listOf(it.devId, it.devPassword) }.filter { it.isNotBlank() }.sortedByDescending { it.length }
        }

        /** Prints [line] with any developer credential blanked: a safety net over the engine's own redaction. */
        fun printSafe(line: String) {
            println(secrets.fold(line) { text, secret -> text.replace(secret, "***") })
        }
    }
}
