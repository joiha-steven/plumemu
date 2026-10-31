package com.plumemu.scraper

import com.plumemu.scraper.timing.ScrapeTimingLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A fake ScreenScraper (MockWebServer) and a scraper pointed at it. Never touches the network.
 * Credentials are fake and distinctive so a test can prove they never leak.
 */
internal class ScraperFixture(private val root: File) : AutoCloseable {
    val server = MockWebServer().apply { start() }
    val logLines = CopyOnWriteArrayList<String>()

    /** What the scraper and the run wrote to the timing log (tag PlumScrapeTiming). */
    val timingLines = CopyOnWriteArrayList<String>()
    val cacheDir = File(root, "cache")

    /** Virtual time of each request's start, seen by the client; filled when [clock] is given. */
    val requestStarts = CopyOnWriteArrayList<Long>()
    private val inFlight = AtomicInteger()
    val maxInFlight = AtomicInteger()

    fun config(
        clock: () -> Long = System::currentTimeMillis,
        developer: DeveloperCredentials = DEVELOPER,
        user: UserCredentials? = USER,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        pauseStore: PauseStore? = null,
        timingLog: ScrapeTimingLog = ScrapeTimingLog { timingLines += it },
    ) = ScraperConfig(
        cacheDir = cacheDir,
        user = user,
        developer = developer,
        baseUrl = server.url("/api2/").toString(),
        steamStoreSearchUrl = server.url("/steam-store/").toString(),
        steamImagesUrl = server.url("/steam-cdn/apps/").toString(),
        libretroThumbnailsUrl = server.url("/libretro/").toString(),
        clock = clock,
        ioDispatcher = ioDispatcher,
        pauseStore = pauseStore,
        log = { logLines += it },
        timingLog = timingLog,
    )

    fun scraper(
        config: ScraperConfig,
        fullHashLimitBytes: Long = 64L * 1024 * 1024,
        configure: OkHttpClient.Builder.() -> Unit = {},
    ): ScreenScraperScraper {
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestStarts += config.clock()
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                try {
                    chain.proceed(chain.request())
                } finally {
                    inFlight.decrementAndGet()
                }
            }
            .apply(configure)
            .build()
        return ScreenScraperScraper(config, http, fullHashLimitBytes)
    }

    fun rom(name: String = "Sonic.md", content: String = "hello world"): FileRomSource {
        val file = File(root, "roms/$name")
        file.parentFile!!.mkdirs()
        file.writeText(content)
        return FileRomSource(file)
    }

    fun request(rom: RomSource, systemId: String = "megadrive", title: String = "Sonic the Hedgehog") =
        ScrapeRequest(rom, systemId, title, regions = listOf("us", "wor", "eu", "jp"), languages = listOf("en"))

    fun takeRequest(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS) ?: error("no request reached the server")

    /** Every file the scraper wrote, as text: for proving no credential was stored. */
    fun cachedText(): String = cacheDir.walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }

    fun responseFiles(): List<File> =
        File(cacheDir, ScreenScraperScraper.RESPONSES_DIR).listFiles()?.filter { it.isFile }.orEmpty()

    override fun close() {
        server.close()
    }

    companion object {
        const val DEV_ID = "test-dev-id"
        const val DEV_PASSWORD = "test-dev-password"
        const val USER_NAME = "test-user"
        const val USER_PASSWORD = "test-user-password"
        val DEVELOPER = DeveloperCredentials(DEV_ID, DEV_PASSWORD)
        val USER = UserCredentials(USER_NAME, USER_PASSWORD)
        val SECRETS = listOf(DEV_PASSWORD, USER_PASSWORD, "devid=$DEV_ID", "ssid=$USER_NAME", USER_NAME)

        fun fixture(name: String): String =
            ScraperFixture::class.java.getResource("/screenscraper/$name")!!.readText()

        fun json(name: String, edit: (String) -> String = { it }) =
            MockResponse(200, Headers.headersOf("Content-Type", "application/json"), edit(fixture(name)))

        fun status(code: Int, body: String) = MockResponse(code, Headers.headersOf(), body)

        /** ScreenScraper's 404 text for an unknown hash. */
        const val NOT_FOUND_BODY = "Erreur : Rom/Iso/Dossier non trouv\u00e9e ! "
    }
}
