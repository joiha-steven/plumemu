package com.plumemu.telemetry

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Crash reports: the handler writes a file and
 * hands over; the next launch sends it once and deletes it; statistics off delete it unsent; 429
 * keeps it without holding the events back; requests stay within 10 crashes and 256 KB.
 */
class CrashTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var h: TelemetryHarness
    private lateinit var dir: File

    @Before
    fun setUp() {
        server.start()
        dir = folder.newFolder("telemetry")
        h = TelemetryHarness(dir, server)
    }

    @After
    fun tearDown() {
        h.stop()
        server.close()
    }

    private val store get() = CrashStore(CrashStore.dirIn(dir)) {}

    private fun writeCrash(stackChars: Int = 100): String {
        val id = UUID.randomUUID().toString()
        val crash =
            CrashReports.item(
                id = id,
                kind = CrashKind.Exception,
                time = "2026-10-24T08:00:00.000Z",
                exception = "java.lang.IllegalStateException",
                message = "boom",
                stack = "Plumemu 0.0.3-beta (version code 321, debug)\n" + "x".repeat(stackChars),
                thread = "main",
                screen = "home",
                uptimeSeconds = 12,
            )
        store.write(crash, System.nanoTime(), id)
        return id
    }

    /** Every request of a send, in order (each answered from [codes], then 204). */
    private fun sendAnswering(vararg codes: Int): Pair<SendOutcome, List<Pair<String, JsonObject>>> {
        h.answer(*codes)
        repeat(20) { h.answer(204) }
        val before = server.requestCount
        val outcome = h.send()
        val requests = List(server.requestCount - before) { h.take().let { it.url.encodedPath to it.json() } }
        return outcome to requests
    }

    private fun crashIds(body: JsonObject): List<String> = body.getValue("crashes").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }

    @Test
    fun theHandlerWritesOneFileThenCallsThePreviousHandler() {
        var handedOver: Throwable? = null
        val handler =
            CrashCapture.Handler(
                store = store,
                header = "Plumemu 0.0.3-beta (version code 321, release)",
                startedAt = 1_000,
                uptime = { 43_500 },
                previous = { _, e -> handedOver = e },
                clock = { 1_761_300_000_000L },
                newId = { "0b7e8a64-6c1d-4f27-9a51-3e7f2c9d1a00" },
            )
        val error = IllegalStateException("library unreadable", java.io.IOException("disk gone"))
        CrashCapture.screen = "settings_advanced"
        handler.uncaughtException(Thread.currentThread(), error)

        assertSame("the previous handler still gets the crash", error, handedOver)
        val crash = store.entries().single().json
        assertEquals(setOf("id", "kind", "time", "exception", "message", "stack", "thread", "screen", "uptime_s"), crash.keys)
        assertEquals("0b7e8a64-6c1d-4f27-9a51-3e7f2c9d1a00", crash.getValue("id").jsonPrimitive.content)
        assertEquals("exception", crash.getValue("kind").jsonPrimitive.content)
        assertEquals("2025-10-24T10:00:00.000Z", crash.getValue("time").jsonPrimitive.content)
        assertEquals("java.lang.IllegalStateException", crash.getValue("exception").jsonPrimitive.content)
        assertEquals("library unreadable", crash.getValue("message").jsonPrimitive.content)
        assertEquals(Thread.currentThread().name, crash.getValue("thread").jsonPrimitive.content)
        assertEquals("settings_advanced", crash.getValue("screen").jsonPrimitive.content)
        assertEquals("42", crash.getValue("uptime_s").jsonPrimitive.content)
        val stack = crash.getValue("stack").jsonPrimitive.content
        assertTrue("the header line the retrace script reads", stack.startsWith("Plumemu 0.0.3-beta (version code 321, release)\njava.lang.IllegalStateException: library unreadable"))
        assertTrue("frames", stack.contains("\tat com.plumemu.telemetry.CrashTest"))
        assertTrue("causes", stack.contains("Caused by: java.io.IOException: disk gone"))
    }

    @Test
    fun theHandlerStillHandsOverWhenTheFileCannotBeWritten() {
        val blocked = folder.newFile("not-a-folder")
        var handedOver = false
        val handler = CrashCapture.Handler(CrashStore(File(blocked, "crashes")) {}, "h", 0, { 0 }, { _, _ -> handedOver = true })
        handler.uncaughtException(Thread.currentThread(), RuntimeException("x"))
        assertTrue(handedOver)
    }

    @Test
    fun theHandlerHandsOverEvenWhenBuildingTheReportRunsOutOfMemory() {
        var handedOver = false
        val handler =
            CrashCapture.Handler(
                store = store,
                header = "h",
                startedAt = 0,
                uptime = { 0 },
                previous = { _, _ -> handedOver = true },
                report = { _, _, _, _ -> throw OutOfMemoryError("no room") },
            )
        handler.uncaughtException(Thread.currentThread(), RuntimeException("x"))
        assertTrue(handedOver)
        assertTrue(store.entries().isEmpty())
    }

    @Test
    fun aFailedReportWriteKeepsTheExitForTheNextStart() {
        h.stop()
        h.startAgain()
        runBlocking { h.telemetry.isEnabled() }
        val anr = ExitRecord(CrashKind.Anr, 1_761_300_000_000L, "Input dispatching timed out", 0, importance = 100) { "trace" }
        h.exits = listOf(anr)
        // The report's own file name is taken by a folder: its write fails, the marker must stay.
        val installId = runBlocking { h.telemetry.installId() }!!
        val id = Telemetry.exitId(installId, anr)
        val blocker = File(CrashStore.dirIn(dir), "${anr.timestamp.toString().padStart(15, '0')}-$id.json")
        assertTrue(blocker.mkdirs())
        h.restart()
        runBlocking { h.telemetry.isEnabled() }
        assertTrue(store.entries().isEmpty())

        blocker.delete()
        h.restart()
        runBlocking { h.telemetry.isEnabled() }
        assertEquals("reported at the next start", listOf(id), store.entries().map { it.json.getValue("id").jsonPrimitive.content })
    }

    @Test
    fun theNextLaunchSendsAFileOnceThenDeletesIt() {
        val id = writeCrash()
        h.restart()
        runBlocking { h.telemetry.isEnabled() }
        assertTrue("a waiting crash asks for a send at once", h.scheduler.now.get() >= 1)

        val (outcome, requests) = sendAnswering()
        assertEquals(SendOutcome.Done, outcome)
        val crashes = requests.filter { it.first == "/api/v1/crashes" }
        assertEquals(1, crashes.size)
        val body = crashes.single().second
        assertEquals(setOf("v", "install_id", "common", "crashes"), body.keys)
        assertEquals(runBlocking { h.telemetry.installId() }, body.getValue("install_id").jsonPrimitive.content)
        assertEquals(TelemetryHarness.COMMON, body["common"])
        assertEquals(listOf(id), crashIds(body))
        assertTrue("deleted once accepted", store.entries().isEmpty())

        val (_, again) = sendAnswering()
        assertTrue("never sent twice", again.none { it.first == "/api/v1/crashes" })
    }

    @Test
    fun statisticsOffDeleteCrashesUnsent() {
        writeCrash()
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        val (_, requests) = sendAnswering()
        assertTrue(requests.none { it.first == "/api/v1/crashes" })
        assertTrue(store.entries().isEmpty())

        // Written while off (the handler does not know the switch): the next start deletes it.
        writeCrash()
        h.storedSwitch = false
        h.restart()
        runBlocking { h.telemetry.isEnabled() }
        val (_, later) = sendAnswering()
        assertTrue(later.none { it.first == "/api/v1/crashes" })
        assertTrue(store.entries().isEmpty())
    }

    @Test
    fun theDaysCapKeepsTheFilesAndStillLetsTheEventsGo() {
        writeCrash()
        h.restart()
        h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home))
        val (outcome, requests) = sendAnswering(429)
        assertEquals("WorkManager tries the crashes again later", SendOutcome.Retry, outcome)
        assertEquals(listOf("/api/v1/crashes", "/api/v1/events"), requests.map { it.first })
        assertEquals(1, store.entries().size)
        assertEquals(0, h.telemetry.queue.size())

        val (_, retried) = sendAnswering()
        assertEquals(listOf("/api/v1/crashes"), retried.map { it.first })
        assertTrue(store.entries().isEmpty())
    }

    @Test
    fun aRefusedBatchIsDroppedAndLogged() {
        writeCrash()
        h.restart()
        val (outcome, _) = sendAnswering(400)
        assertEquals(SendOutcome.Done, outcome)
        assertTrue(store.entries().isEmpty())
        assertTrue(h.logs.any { it.contains("refused by the server") })
    }

    @Test
    fun atMostTenAreKeptAndARequestHoldsTenAndUnder256Kb() {
        repeat(12) { writeCrash() }
        h.restart()
        runBlocking { h.telemetry.isEnabled() }
        assertEquals(10, store.entries().size)
        repeat(4) { writeCrash(stackChars = CrashReports.MAX_STACK_CHARS) }

        val (_, requests) = sendAnswering()
        val crashes = requests.filter { it.first == "/api/v1/crashes" }
        assertEquals(14, crashes.sumOf { crashIds(it.second).size })
        crashes.forEach {
            assertTrue(crashIds(it.second).size <= 10)
            assertTrue(it.second.toString().toByteArray().size <= 256 * 1024)
        }
        assertTrue(store.entries().isEmpty())
    }

    @Test
    fun aLongStackIsCutAndMarked() {
        val item = CrashReports.item("id", CrashKind.Exception, "t", "E", "m".repeat(20_000), "s".repeat(100_000))
        assertTrue(item.getValue("stack").jsonPrimitive.content.endsWith("[cut at ${CrashReports.MAX_STACK_CHARS} characters]"))
        assertTrue(item.getValue("message").jsonPrimitive.content.length < 8_100)
    }

    @Test
    fun theScreenOpenedIsKeptForTheNextCrash() {
        CrashCapture.screen = null
        h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Search))
        assertEquals("search", CrashCapture.screen)
    }

    @Test
    fun nothingWaitingSendsNoCrashRequest() {
        val (_, requests) = sendAnswering()
        assertTrue(requests.none { it.first == "/api/v1/crashes" })
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        assertFalse(store.dir.listFiles().orEmpty().any { it.name.endsWith(".json") })
    }
}
