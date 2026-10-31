package com.plumemu.scraper.work

import androidx.work.ListenableWorker
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.ScraperFixture
import com.plumemu.scraper.ScraperFixture.Companion.fixture
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.PauseStore
import com.plumemu.scraper.SharedPreferencesPauseStore
import com.plumemu.scraper.media.MediaSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Box fronts for every game first, then discs, with as many games at once as
 * the account's `maxthreads`. The fake server answers each game by its file name (game N is
 * ScreenScraper game 2000+N, with its own box front and disc), so games in flight together get
 * their own answers, and it records what arrived in order ([events]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ScrapeRunParallelTest : WorkerTestBase() {
    private val grid = listOf(MediaSize("grid", 200, 200))
    private val box = pngBytes(200, 280)
    private val disc = pngBytes(240, 240)

    /** `lookup:N`, `search:N`, `box:N` and `disc:N`, as they reached the server. */
    private val events = CopyOnWriteArrayList<String>()
    private val serverInFlight = AtomicInteger()
    private val serverMostInFlight = AtomicInteger()

    @Volatile private var maxThreads = 3

    /** Real milliseconds the server takes for each answer, so parallel requests overlap. */
    @Volatile private var holdMillis = 30L

    /** Called with each event as it reaches the server (a scan adding games mid-run). */
    @Volatile private var onEvent: (String) -> Unit = {}

    /** A test's own answer for game N's lookup, or null for the match; called once per lookup. */
    @Volatile private var lookupAnswer: (n: Int, attempt: Int) -> MockResponse? = { _, _ -> null }
    private val lookups = ConcurrentHashMap<Int, AtomicInteger>()

    /** The body length of game N's match, to tell game lines apart. */
    private val answerBytes = ConcurrentHashMap<Int, Long>()

    /** Every HTTP call the scraper made, timed on the client after the gate let it go. */
    private class Call(val path: String, val romName: String?, val startNanos: Long, var endNanos: Long = Long.MAX_VALUE, var code: Int = 0)

    private val calls = CopyOnWriteArrayList<Call>()
    @Volatile private var pauseSavedAtNanos = Long.MAX_VALUE
    @Volatile private var threadLimitAtNanos = Long.MAX_VALUE
    private lateinit var scraper: Scraper

    @Before fun parallelServer() {
        roms = (1..6).map { fx.request(rom(it)) }
        source = FakeScrapeSource(roms)
        fx.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                serverMostInFlight.accumulateAndGet(serverInFlight.incrementAndGet(), ::maxOf)
                try {
                    Thread.sleep(holdMillis)
                    return answer(request)
                } finally {
                    serverInFlight.decrementAndGet()
                }
            }
        }
    }

    private fun rom(n: Int) = fx.rom("Game$n.md", content = "rom $n ".repeat(n * 3))

    private fun answer(request: RecordedRequest): MockResponse {
        val url = request.url
        val path = url.encodedPath
        // libretro's thumbnails (asked for a no-match retro game): a miss. A HEAD answer has no body.
        if (path.startsWith("/libretro/")) return MockResponse.Builder().code(404).build()
        if (path.endsWith("mediaJeu.php")) {
            val n = url.queryParameter("jeuid")!!.toInt() - 2000
            val isDisc = url.queryParameter("media")!!.startsWith("support-2D")
            events += (if (isDisc) "disc:" else "box:") + n
            onEvent((if (isDisc) "disc:" else "box:") + n)
            return imageResponse(if (isDisc) disc else box)
        }
        if (path.endsWith("jeuRecherche.php")) {
            events += "search:" + url.queryParameter("recherche")
            return MockResponse(200, Headers.headersOf("Content-Type", "application/json"), fixture("jeuRecherche-empty.json"))
        }
        val n = url.queryParameter("romnom")!!.removePrefix("Game").removeSuffix(".md").toInt()
        events += "lookup:$n"
        onEvent("lookup:$n")
        val attempt = lookups.getOrPut(n) { AtomicInteger() }.incrementAndGet()
        return lookupAnswer(n, attempt) ?: match(n)
    }

    /** Game N: ScreenScraper id 2000+N, a us box front and a us disc, [maxThreads], padded to its own length. */
    private fun match(n: Int): MockResponse {
        val base = fx.server.url("/").toString()
        fun ref(type: String) =
            """{"type": "$type", "url": "${base}api2/mediaJeu.php?systemeid=1&jeuid=${2000 + n}&media=$type(us)", "region": "us", "format": "png"}"""
        val text = fixture("jeuInfos-match.json").let {
            val start = it.indexOf("\"medias\": [")
            val end = it.indexOf("\"roms\"")
            it.substring(0, start) + "\"medias\": [${ref("box-2D")}, ${ref("support-2D")}],\n      " + it.substring(end)
        }
            .replace("\"id\": \"1001\"", "\"id\": \"${2000 + n}\"")
            .replace("\"maxthreads\": \"1\"", "\"maxthreads\": \"$maxThreads\"")
            .replace("\"rotation\": \"0\"", "\"rotation\": \"0\"" + " ".repeat(n * 100))
        answerBytes[n] = text.toByteArray().size.toLong()
        return MockResponse(200, Headers.headersOf("Content-Type", "application/json"), text)
    }

    /** The pause store, noting when the scraper saved a pause (after it took effect in the gate). */
    private inner class NotingPauseStore(private val inner: PauseStore) : PauseStore by inner {
        override fun save(pause: ScrapeResult.Paused) {
            pauseSavedAtNanos = minOf(pauseSavedAtNanos, System.nanoTime())
            inner.save(pause)
        }
    }

    private fun TestScope.parallelHost(readTimeoutMillis: Long = 10_000): ScraperWork = parallelHost(virtualClock(start), readTimeoutMillis)

    private fun parallelHost(clock: () -> Long, readTimeoutMillis: Long = 10_000): ScraperWork {
        val timing = com.plumemu.scraper.timing.ScrapeTimingLog {
            if (it.startsWith("event=thread-limit")) threadLimitAtNanos = minOf(threadLimitAtNanos, System.nanoTime())
            fx.timingLines += it
        }
        val config = fx.config(clock = clock, pauseStore = NotingPauseStore(SharedPreferencesPauseStore(app())), timingLog = timing)
        scraper = fx.scraper(config) {
            readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            addInterceptor { chain ->
                val call = Call(chain.request().url.encodedPath, chain.request().url.queryParameter("romnom"), System.nanoTime())
                calls += call
                try {
                    chain.proceed(chain.request()).also { call.code = it.code }
                } finally {
                    call.endNanos = System.nanoTime()
                }
            }
        }
        return ScraperWork(scraper, source, mediaDir, grid, lowRamDevice = false, clock, log = { fx.logLines += it }, timingLog = timing)
            .also { app().work = it }
    }

    private fun kinds(n: Int) = events.filter { it.endsWith(":$n") }

    @Test fun `with one thread each game is looked up and gets its box front in order, then the discs in order`() = runTest {
        maxThreads = 1
        parallelHost()

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val expected = (1..6).flatMap { listOf("lookup:$it", "box:$it") } + (1..6).map { "disc:$it" }
        assertEquals(expected, events.toList())
        assertEquals(1, serverMostInFlight.get())
        assertEquals((1..6).map { roms[it - 1].rom.key }, source.mainDeliveries.map { it.request.rom.key })
        assertEquals((1..6).map { roms[it - 1].rom.key }, source.supportDeliveries.map { it.request.rom.key })
        source.supportDeliveries.forEach { assertEquals(listOf(MediaKind.BoxFront, MediaKind.Support), it.media.map { m -> m.kind }) }
    }

    @Test fun `with three threads every box front is saved before any disc is asked, and no game is fetched twice`() = runTest {
        parallelHost()

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val all = events.toList()
        println("server order: $all")
        val lastBox = all.indexOfLast { it.startsWith("box:") }
        val firstDisc = all.indexOfFirst { it.startsWith("disc:") }
        assertTrue("every box front before any disc: $all", lastBox < firstDisc)
        (1..6).forEach { assertEquals("game $it: $all", listOf("lookup:$it", "box:$it", "disc:$it"), kinds(it).sortedBy { k -> all.indexOf(k) }) }
        assertEquals(18, all.size)
        assertEquals("each game delivered once per pass", 6, source.mainDeliveries.map { it.request.rom.key }.distinct().size)
        assertEquals(6, source.mainDeliveries.size)
        assertEquals(6, source.supportDeliveries.size)
        assertTrue("every box front delivered before the first disc delivery", source.deliveries.take(6).all { it.pass == ScrapePass.Main })
        // The disc pass hands games out in the main pass's order (priority); three workers may send a little out of order.
        assertTrue("disc requests start in priority order, give or take the workers in flight: $all",
            all.filter { it.startsWith("disc:") }.map { it.removePrefix("disc:").toInt() }.withIndex().all { (i, n) -> kotlin.math.abs(n - (i + 1)) < 3 })
    }

    /**
     * Real time, not virtual: the client sees a request start on OkHttp's thread, and virtual time
     * jumps ahead whenever every coroutine waits on the network, so virtual starts would drift.
     * About 8 s: 18 requests over three slots at 1.2 s each.
     */
    @Test fun `with three threads at most three requests are in flight and each slot keeps its spacing`() = runBlocking<Unit> {
        val realStart = System.currentTimeMillis()
        parallelHost(clock = { start + System.currentTimeMillis() - realStart })

        assertEquals(ListenableWorker.Result.success(), runWorker())

        println("most in flight: client ${fx.maxInFlight.get()}, server ${serverMostInFlight.get()}")
        assertTrue("at most three at once", fx.maxInFlight.get() <= 3 && serverMostInFlight.get() <= 3)
        assertTrue("games did overlap", serverMostInFlight.get() >= 2)
        // Per slot, starts are 1.2 s apart: of any four starts in a row, two share a slot. The
        // client sees a start a few milliseconds after the gate let it go, hence the allowance.
        val starts = fx.requestStarts.sorted()
        assertEquals(18, starts.size)
        for (i in 0 until starts.size - 3) {
            assertTrue("starts $i..${i + 3}: ${starts.map { it - starts[0] }}", starts[i + 3] - starts[i] >= 1_200 - CLIENT_JITTER_MILLIS)
        }
    }

    /**
     * The disc pass looks at the main pass again every 10 s (virtual time here): twelve discs at
     * 1.2 s each take longer, so the game a scan adds at the first disc goes before the last ones.
     */
    @Test fun `a game added during the disc pass is fetched before the remaining discs`() = runTest {
        maxThreads = 1
        roms = (1..12).map { fx.request(rom(it)) }
        source = FakeScrapeSource(roms)
        val added = fx.request(rom(13))
        onEvent = { if (it == "disc:1" && source.added.isEmpty()) source.added += added }
        parallelHost()

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val all = events.toList()
        println("server order: $all")
        val lookup = all.indexOf("lookup:13")
        assertTrue("asked during the disc pass: $all", lookup > all.indexOf("disc:1"))
        assertTrue("before the remaining discs: $all", lookup < all.indexOf("disc:12"))
        assertEquals("box:13", all[lookup + 1])
        assertEquals("its disc after the others", "disc:13", all.last())
        assertEquals(39, all.size)
        // A re-check that finds nothing writes no page line.
        val pages = fx.timingLines.filter { it.startsWith("page ") }
        assertTrue(pages.toString(), pages.none { it.contains("games=0") })
    }

    @Test fun `no more than four games run at once whatever the account allows`() = runTest {
        maxThreads = 8
        roms = (1..8).map { fx.request(rom(it)) }
        source = FakeScrapeSource(roms)
        parallelHost()

        assertEquals(ListenableWorker.Result.success(), runWorker())

        println("most in flight: client ${fx.maxInFlight.get()}, server ${serverMostInFlight.get()}")
        assertTrue(fx.maxInFlight.get() <= ScrapeRun.MAX_WORKERS)
        assertEquals(8, source.supportDeliveries.size)
    }

    @Test fun `a 429 drops the run to one request at a time and it still completes`() = runTest {
        lookupAnswer = { n, attempt -> if (n == 3 && attempt == 1) status(429, "The maximum threads is already used") else null }
        parallelHost()

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(6, source.mainDeliveries.size)
        assertEquals(6, source.supportDeliveries.size)
        assertEquals(1, scraper.requestSlots.value)
        assertTrue(threadLimitAtNanos < Long.MAX_VALUE)
        // Requests that started after the 429 was handled run one at a time.
        val after = calls.filter { it.startNanos > threadLimitAtNanos }.sortedBy { it.startNanos }
        assertTrue(after.size >= 6)
        after.zipWithNext().forEach { (a, b) -> assertTrue("one at a time after the 429", b.startNanos >= a.endNanos) }
    }

    @Test fun `a daily quota mid-run stops every worker and nothing more is sent`() = runTest {
        lookupAnswer = { n, _ -> if (n == 4) status(430, "Votre quota de scrape est d\u00e9pass\u00e9 pour aujourd'hui !") else null }
        parallelHost()

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertTrue("the pause was saved", pauseSavedAtNanos < Long.MAX_VALUE)
        assertEquals("no request after the pause", emptyList<String>(), calls.filter { it.startNanos > pauseSavedAtNanos }.map { it.path })
        assertTrue("game 4 not delivered", roms[3].rom.key !in source.deliveredKeys)
        assertTrue("the disc pass never started", source.supportDeliveries.isEmpty())
        assertEquals("the follow-up run waits for the pause", 1, uniqueWork().size)
    }

    @Test fun `cancelling mid-run leaves no game half stored`() = runTest {
        val reachedFour = CompletableDeferred<Unit>()
        lookupAnswer = { n, _ ->
            if (n == 4) {
                reachedFour.complete(Unit)
                Thread.sleep(500)
            }
            null
        }
        parallelHost()

        val job = launch { runWorker() }
        reachedFour.await()
        job.cancelAndJoin()

        assertTrue("game 4 was in flight, not delivered", roms[3].rom.key !in source.deliveredKeys)
        source.deliveries.forEach { delivery ->
            assertTrue(delivery.result is ScrapeResult.Matched)
            assertEquals(listOf(MediaKind.BoxFront), delivery.media.map { it.kind })
            assertTrue(delivery.media.all { File(it.path).isFile })
        }
        val leftovers = mediaDir.walkTopDown().filter { it.isFile && (it.name.endsWith(".part") || it.name.endsWith(".tmp")) }.toList()
        assertEquals("no temp file left behind", emptyList<File>(), leftovers)
    }

    @Test fun `game lines stay each game's own when games overlap`() = runTest {
        parallelHost()

        assertEquals(ListenableWorker.Result.success(), runWorker())

        fun field(line: String, name: String) = Regex("(?:^| )$name=(\\S+)").find(line)!!.groupValues[1]
        val games = fx.timingLines.filter { it.startsWith("game ") }
        val main = games.filter { field(it, "pass") == "main" }
        val discs = games.filter { field(it, "pass") == "disc" }
        assertEquals(6, main.size)
        assertEquals(6, discs.size)
        for (line in main) {
            // The file's size tells which game it is; its lookup bytes must be that game's answer.
            val n = (1..6).single { roms[it - 1].rom.sizeBytes == field(line, "size").removeSuffix("B").toLong() }
            assertEquals(line, answerBytes[n], field(line, "lookupBytes").removeSuffix("B").toLong())
            assertEquals(line, "1", field(line, "lookupReqs"))
            assertEquals(line, "1", field(line, "boxReqs"))
            assertEquals(line, "0", field(line, "discReqs"))
            assertEquals(line, box.size.toLong(), field(line, "boxBytes").removeSuffix("B").toLong())
        }
        for (line in discs) {
            assertEquals(line, "0", field(line, "lookupReqs"))
            assertEquals(line, "reused", field(line, "box"))
            assertEquals(line, "0", field(line, "boxReqs"))
            assertEquals(line, "1", field(line, "discReqs"))
            assertEquals(line, disc.size.toLong(), field(line, "discBytes").removeSuffix("B").toLong())
        }
        assertEquals("one gate wait per request, none counted twice", 18, fx.timingLines.count { it.startsWith("req ") })
    }

    @Test fun `a no-match game costs one hash lookup and one name search, and has no disc pass`() = runTest {
        source = FakeScrapeSource(roms.take(1))
        lookupAnswer = { _, _ -> status(404, ScraperFixture.NOT_FOUND_BODY) }
        parallelHost()

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(listOf("lookup:1", "search:Sonic the Hedgehog"), events.toList())
        // Sonic is a Mega Drive game, so libretro's thumbnails are asked once (HEAD) after ScreenScraper's two calls.
        assertEquals(listOf("/api2/jeuInfos.php", "/api2/jeuRecherche.php"), calls.map { it.path }.filter { it.startsWith("/api2/") })
        assertEquals(listOf(ScrapePass.Main), source.deliveries.map { it.pass })
        assertEquals(ScrapeResult.NoMatch, source.deliveries.single().result)
    }

    @Test fun `a game whose lookup times out is set aside, the others are delivered, and the run retries for it`() = runTest {
        maxThreads = 1
        source = FakeScrapeSource(roms.take(4))
        lookupAnswer = { n, _ ->
            if (n == 2) Thread.sleep(1_500)
            null
        }
        parallelHost(readTimeoutMillis = 300)

        // Not a lost connection: everything else is done first; WorkManager then asks game 2 again.
        assertEquals(ListenableWorker.Result.retry(), runWorker())

        assertEquals(listOf(1, 3, 4).map { roms[it - 1].rom.key }, source.mainDeliveries.map { it.request.rom.key })
        assertEquals(3, source.supportDeliveries.size)
        assertFalse(roms[1].rom.key in source.deliveredKeys)
        assertEquals("asked once in the run", 1, events.count { it == "lookup:2" })
    }

    @Test fun `three timeouts in a row end the run with a retry`() = runTest {
        maxThreads = 1
        lookupAnswer = { n, _ ->
            if (n in 1..3) Thread.sleep(1_500)
            null
        }
        parallelHost(readTimeoutMillis = 300)

        assertEquals(ListenableWorker.Result.retry(), runWorker())

        assertTrue(source.deliveries.isEmpty())
        assertEquals(listOf("lookup:1", "lookup:2", "lookup:3"), events.toList())
        assertTrue(fx.logLines.any { it.contains("3 network errors in a row") })
    }

    @Test fun `a game that timed out in the last run is asked after the other games, then delivered`() = runTest {
        maxThreads = 1
        source = FakeScrapeSource(roms.take(3))
        lookupAnswer = { n, attempt ->
            if (n == 1 && attempt == 1) Thread.sleep(1_500)
            null
        }
        val work = parallelHost(readTimeoutMillis = 300)
        assertEquals(ListenableWorker.Result.retry(), runWorker())
        assertFalse(roms[0].rom.key in source.deliveredKeys)

        // The same scraper (one per account in a process) runs again; a scan added game 4.
        events.clear()
        source.added += fx.request(rom(4))
        app().work = work
        assertEquals(ListenableWorker.Result.success(), runWorker())

        // At the end of the main pass, before any disc; the discs then in priority order.
        assertEquals(listOf("lookup:4", "box:4", "lookup:1", "box:1", "disc:1", "disc:4"), events.toList())
        assertTrue(roms[0].rom.key in source.mainDeliveries.map { it.request.rom.key })
    }

    @Test fun `games set aside in the last run time out together, not one after another`() = runTest {
        // Games 1-3 met a network error in an earlier run of this scraper and time out again.
        lookupAnswer = { n, _ ->
            if (n in 1..3) Thread.sleep(2_000)
            null
        }
        parallelHost(readTimeoutMillis = 500)
        val memory = networkMemoryOf(scraper)
        (1..3).forEach { memory.failedThisRun(roms[it - 1].rom.key) }

        assertEquals("they are still unanswered", ListenableWorker.Result.retry(), runWorker())

        val all = events.toList()
        println("server order: $all")
        val lastSetAside = (1..3).maxOf { all.indexOf("lookup:$it") }
        assertTrue("after the other games' lookups: $all", (4..6).all { all.indexOf("lookup:$it") < (1..3).minOf { n -> all.indexOf("lookup:$n") } })
        assertTrue("before any disc: $all", all.indices.filter { all[it].startsWith("disc:") }.all { it > lastSetAside })
        assertEquals("the other games got their discs", 3, source.supportDeliveries.size)
        val setAside = calls.filter { it.romName in setOf("Game1.md", "Game2.md", "Game3.md") }
        assertEquals(3, setAside.size)
        val spanMillis = (setAside.maxOf { it.endNanos } - setAside.minOf { it.startNanos }) / 1_000_000
        println("three set-aside timeouts took $spanMillis ms")
        assertTrue("about one timeout, not three ($spanMillis ms)", spanMillis < 1_200)
    }

    @Test fun `a game that failed on a server error is reported to the source, a not found game or a delivered one is not`() = runTest {
        maxThreads = 1
        source = FakeScrapeSource(roms.take(3))
        lookupAnswer = { n, _ ->
            when (n) {
                1 -> status(503, "overloaded")
                2 -> status(404, ScraperFixture.NOT_FOUND_BODY)
                else -> null
            }
        }
        parallelHost()
        runWorker()
        assertEquals(listOf(roms[0].rom.key), source.failedKeys.toList())
        assertFalse(roms[0].rom.key in source.deliveredKeys)
    }

    @Test fun `a 429 answer is never counted against the game`() = runTest {
        maxThreads = 1
        source = FakeScrapeSource(roms.take(2))
        // Every try of game 1 is a 429: the scraper retries after waits, then gives up with Server(429).
        lookupAnswer = { n, _ -> if (n == 1) status(429, "The maximum threads is already used") else null }
        parallelHost()
        runWorker()
        assertTrue("game 1 was tried more than once, so the 429 reached the end", events.count { it == "lookup:1" } > 1)
        assertEquals(emptyList<String>(), source.failedKeys.toList())
    }

    @Test fun `a timeout is reported only when the run got other games answered`() = runTest {
        maxThreads = 1
        source = FakeScrapeSource(roms.take(3))
        lookupAnswer = { n, _ ->
            if (n == 1) Thread.sleep(1_500)
            null
        }
        parallelHost(readTimeoutMillis = 300)
        runWorker()
        // Game 1 is set aside only in a later run; here it is first, before any game was answered.
        assertEquals("the connection was not shown to work yet", emptyList<String>(), source.failedKeys.toList())
    }

    @Test fun `a game that times out after others were answered is reported`() = runTest {
        maxThreads = 1
        source = FakeScrapeSource(roms.take(3))
        lookupAnswer = { n, _ ->
            if (n == 3) Thread.sleep(1_500)
            null
        }
        parallelHost(readTimeoutMillis = 300)
        runWorker()
        assertEquals(listOf(roms[2].rom.key), source.failedKeys.toList())
    }

    @Test fun `a game that times out in three runs in a row is dropped until box art is asked again`() = runTest {
        maxThreads = 1
        source = FakeScrapeSource(roms.take(2))
        lookupAnswer = { n, _ ->
            if (n == 1) Thread.sleep(1_500)
            null
        }
        val work = parallelHost(readTimeoutMillis = 300)
        suspend fun again(): ListenableWorker.Result {
            app().work = work
            return runWorker()
        }

        assertEquals("run 1", ListenableWorker.Result.retry(), runWorker())
        assertEquals("run 2", ListenableWorker.Result.retry(), again())
        assertEquals("run 3 drops it", ListenableWorker.Result.success(), again())
        assertEquals(3, events.count { it == "lookup:1" })
        assertTrue(fx.logLines.any { it.contains("network errors in 3 runs in a row") })

        assertEquals("run 4 does not ask it", ListenableWorker.Result.success(), again())
        assertEquals(3, events.count { it == "lookup:1" })

        // Download missing or Re-download (the app calls this), or a new process.
        scraper.forgetNetworkFailures()
        lookupAnswer = { _, _ -> null }
        assertEquals(ListenableWorker.Result.success(), again())
        assertEquals(4, events.count { it == "lookup:1" })
        assertTrue(roms[0].rom.key in source.mainDeliveries.map { it.request.rom.key })
    }

    private companion object {
        /** How much later than the gate's start the client's interceptor may see a request start. */
        const val CLIENT_JITTER_MILLIS = 60L
    }
}
