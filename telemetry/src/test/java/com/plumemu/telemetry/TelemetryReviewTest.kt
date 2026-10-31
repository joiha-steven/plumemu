package com.plumemu.telemetry

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Review round 1 of A1: races between a send and the switch, and a send that must end. */
class TelemetryReviewTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var h: TelemetryHarness

    @Before
    fun setUp() {
        server.start()
        h = TelemetryHarness(folder.newFolder("telemetry"), server)
    }

    @After
    fun tearDown() {
        File(h.dir, "queue").setWritable(true)
        h.stop()
        server.close()
    }

    private fun names(request: RecordedRequest): List<String> =
        request.json().getValue("events").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }

    @Test
    fun aSwitchOffDuringASendStopsTheNextBatchAndSendsTheDeclineInTheSameRun() {
        repeat(450) { h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home)) }
        val paths = Collections.synchronizedList(ArrayList<String>())
        val inFlight = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths += request.url.encodedPath
                    if (paths.size == 1) {
                        inFlight.countDown()
                        release.await(5, TimeUnit.SECONDS)
                    }
                    return MockResponse(204, Headers.headersOf(), "")
                }
            }
        val sending = Thread { h.send() }.apply { start() }
        assertTrue(inFlight.await(5, TimeUnit.SECONDS))
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        Thread.sleep(200)
        release.countDown()
        sending.join(10_000)

        assertEquals(listOf("/api/v1/events", "/api/v1/declined"), paths.toList())
        assertEquals(0, h.telemetry.queue.size())
    }

    @Test(timeout = 30_000)
    fun aSendEndsEvenWhenTheOverflowCountCannotBeDeleted() {
        repeat(EventQueue.CAP + 150) { h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home)) }
        // Let the queue take every event, then make its folder read-only.
        h.answer(503)
        h.send()
        h.take()
        assertTrue(File(h.dir, "queue").setWritable(false))
        h.answer(*IntArray(40) { 204 })

        assertEquals(SendOutcome.Done, h.send())

        val overflows = (1..server.requestCount - 1).flatMap { names(h.take()) }.count { it == "queue_overflow" }
        assertEquals("sent once in this run", 1, overflows)
        assertTrue(h.logs.any { it.contains("overflow") })
    }

    /** Statistics are off by default, so a first run with the switch off is someone who never said yes: no decline either. */
    @Test
    fun aFirstRunWithTheSwitchStoredOffSendsNothingAtAll() {
        h.stop()
        h.dir.deleteRecursively()
        h.storedSwitch = false
        h.startAgain()
        // The worker's send may come before the app's follower: it must already know.
        assertEquals(SendOutcome.Done, h.send())

        assertEquals(0, server.requestCount)
        assertEquals(false, kotlinx.coroutines.runBlocking { h.telemetry.isEnabled() })
    }

    @Test
    fun aStoredSwitchThatCannotBeReadSendsNothing() {
        h.stop()
        h.dir.deleteRecursively()
        h.storedSwitch = true
        val failing =
            Telemetry(
                dir = h.dir,
                readCommon = { TelemetryHarness.COMMON },
                sender = Sender({ TelemetryHarness.CLIENT }, server.url("/"), {}),
                scheduler = FakeScheduler(),
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO),
                log = {},
                storedSwitch = { throw java.io.IOException("unreadable") },
            )
        failing.start()
        failing.track(TelemetryEvent.ScreenOpened(ScreenId.Home))
        assertEquals(SendOutcome.Done, kotlinx.coroutines.runBlocking { failing.sendPending() })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun switchesAreIgnoredWhileTheStoredSwitchCannotBeRead() {
        h.stop()
        h.dir.deleteRecursively()
        val failing =
            Telemetry(
                dir = h.dir,
                readCommon = { TelemetryHarness.COMMON },
                sender = Sender({ TelemetryHarness.CLIENT }, server.url("/"), {}),
                scheduler = FakeScheduler(),
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO),
                log = {},
                storedSwitch = { throw java.io.IOException("unreadable") },
            )
        failing.start()
        // The app's settings follower can still read the store and moves the switch: nothing goes.
        failing.setEnabled(true, SwitchPlace.Settings)
        failing.setEnabled(false, SwitchPlace.Settings)
        failing.setEnabled(true, SwitchPlace.Settings)
        failing.track(TelemetryEvent.ScreenOpened(ScreenId.Home))
        assertEquals(SendOutcome.Done, kotlinx.coroutines.runBlocking { failing.sendPending() })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aStoredSwitchOffAtALaterStartSwitchesOffBeforeAnythingGoes() {
        h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home))
        h.answer(503)
        h.send()
        h.take()
        h.stop()
        h.storedSwitch = false
        h.startAgain()
        h.answer(204, 204)
        h.send()

        assertEquals("/api/v1/declined", h.take().url.encodedPath)
        assertEquals(2, server.requestCount)
        assertEquals(0, h.telemetry.queue.size())
    }

    @Test
    fun anUpdateFromSomethingNotAVersionNameIsDroppedAndLogged() {
        h.telemetry.track(TelemetryEvent.UpdateInstalled("Super Mario World (USA).sfc"))
        h.telemetry.track(TelemetryEvent.UpdateInstalled("0.0.2-alpha"))
        h.answer(204)
        h.send()

        val events = h.take().json().getValue("events").jsonArray.map { it.jsonObject }
        assertEquals(listOf("first_open", "update_installed"), events.map { it.getValue("name").jsonPrimitive.content })
        assertEquals("0.0.2-alpha", events[1].getValue("props").jsonObject.getValue("from_version").jsonPrimitive.content)
        assertTrue(h.logs.any { it.contains("update_installed dropped") })
    }

    @Test
    fun versionNamesOfPlumemuAreAccepted() {
        listOf("0.0.3-beta", "1.2.0", "1.0.0-rc.2", "0.0.1-alpha").forEach { assertTrue(it, VERSION_NAME.matches(it)) }
        listOf("", "beta", "0.0.3 beta", "C:/roms/a.zip", "1.0").forEach { assertTrue(it, !VERSION_NAME.matches(it)) }
    }

    @Test
    fun languageTagsLoseTheirUnicodeExtensions() {
        assertEquals("en-US", CommonFields.tagOf(java.util.Locale.forLanguageTag("en-US-u-fw-mon-mu-celsius")))
        assertEquals("zh-Hans-CN", CommonFields.tagOf(java.util.Locale.forLanguageTag("zh-Hans-CN")))
        assertEquals("de-DE", CommonFields.tagOf(java.util.Locale.forLanguageTag("de-DE-x-private")))
        assertEquals("vi", CommonFields.tagOf(java.util.Locale.forLanguageTag("vi")))
    }

    @Test
    fun systemAndEmulatorIdsComeFromThePlatformData() {
        val text = File("../platforms/src/main/assets/platforms.json").readText()
        val catalog = (com.plumemu.platforms.PlatformLoader.load(text, "platforms.json") as com.plumemu.platforms.PlatformLoadResult.Loaded).catalog
        val event = TelemetryEvent.GameLaunched(SystemRef.of(catalog.system("psx")!!), EmulatorRef.of(catalog.emulator("duckstation")!!), LaunchFrom.List, LaunchResult.Ok)
        assertEquals("psx", event.props().getValue("system_id").content)
        assertEquals("duckstation", event.props().getValue("emulator_id").content)
    }
}
