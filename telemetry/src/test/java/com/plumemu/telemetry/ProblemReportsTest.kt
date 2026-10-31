package com.plumemu.telemetry

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Problem reports: the JSON with and without
 * details, never an install id while statistics are off, a report that waits offline and goes when
 * online (also after a restart), the 4,000-character and 512 KB caps, at most 5 kept.
 */
class ProblemReportsTest {
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

    private fun submit(
        text: String = "The grid is blank after a scan",
        email: String? = null,
        details: Boolean = true,
    ): ReportResult = runBlocking { h.telemetry.problems.submit(text, email, details) }

    /** Sends the first launch's `first_open`, so the answers a test queues are for the report. */
    private fun sendFirstOpen() {
        h.answer(200)
        assertEquals(SendOutcome.Done, h.send())
        assertEquals("/api/v1/events", h.take().url.encodedPath)
    }

    private val pending get() = File(dir, "reports").listFiles { f -> f.name.endsWith(".json") }.orEmpty()

    @Test
    fun `with details the report carries common, the log and the install id`() {
        h.answer(200)
        assertEquals(ReportResult.Sent, submit(email = "a@b.com"))
        val request = h.take()
        assertEquals("/api/v1/reports", request.url.encodedPath)
        val body = request.json()
        assertEquals("1", body["v"]!!.jsonPrimitive.content)
        assertEquals("app", body["source"]!!.jsonPrimitive.content)
        assertEquals("The grid is blank after a scan", body["text"]!!.jsonPrimitive.content)
        assertEquals("a@b.com", body["email"]!!.jsonPrimitive.content)
        assertEquals("log line one\nlog line two", body["log"]!!.jsonPrimitive.content)
        assertEquals(TelemetryHarness.COMMON, body["common"])
        assertNotNull(body["install_id"])
        assertEquals(setOf("v", "source", "text", "email", "install_id", "common", "log"), body.keys)
        assertEquals(0, pending.size)
    }

    @Test
    fun `without details there is no common and no log, and no e-mail when it is blank`() {
        h.answer(200)
        assertEquals(ReportResult.Sent, submit(email = "  ", details = false))
        val body = h.take().json()
        assertEquals(setOf("v", "source", "text", "install_id"), body.keys)
    }

    @Test
    fun `statistics off sends no install id, with or without details`() {
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        runBlocking { h.telemetry.sendPending() }
        // The decline goes first; the report is the second request.
        h.answer(200, 200, 200)
        h.take()
        assertEquals(ReportResult.Sent, submit(details = true))
        val withDetails = h.take().json()
        assertNull(withDetails["install_id"])
        assertNotNull(withDetails["common"])
        assertEquals(ReportResult.Sent, submit(details = false))
        assertNull(h.take().json()["install_id"])
    }

    @Test
    fun `switched off while a report waits, the install id is left out when it goes`() {
        h.answer(503)
        assertEquals(ReportResult.Saved, submit())
        h.take()
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        h.answer(200, 200)
        assertEquals(SendOutcome.Done, h.send())
        val requests = generateSequence { server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS) }.toList()
        val report = requests.first { it.url.encodedPath == "/api/v1/reports" }.json()
        assertNull(report["install_id"])
    }

    @Test
    fun `offline the report is kept, then goes when online and is removed`() {
        sendFirstOpen()
        // The server is gone: the connection is refused, as with a lost network.
        server.close()
        assertEquals(ReportResult.Saved, submit())
        assertEquals(1, pending.size)
        assertTrue("work was asked for", h.scheduler.now.get() >= 1)
        assertEquals(SendOutcome.Retry, h.send())
        assertEquals(1, pending.size)

        h.stop()
        val online = MockWebServer().apply { start() }
        val online2 = TelemetryHarness(dir, online)
        try {
            online2.answer(200)
            assertEquals(SendOutcome.Done, online2.send())
            val sent = online2.take()
            assertEquals("/api/v1/reports", sent.url.encodedPath)
            assertEquals("The grid is blank after a scan", sent.json()["text"]!!.jsonPrimitive.content)
            assertEquals(0, pending.size)
        } finally {
            online2.stop()
            online.close()
        }
    }

    @Test
    fun `429 and 500 keep the report, 400 and 413 drop it with a log line`() {
        sendFirstOpen()
        h.answer(429)
        assertEquals(ReportResult.Saved, submit())
        assertEquals(1, pending.size)
        h.answer(500)
        assertEquals(SendOutcome.Retry, h.send())
        assertEquals(1, pending.size)
        h.answer(413)
        assertEquals(SendOutcome.Done, h.send())
        assertEquals(0, pending.size)
        assertTrue(h.logs.any { it.contains("/api/v1/reports answered 413") })

        h.answer(400)
        assertEquals(ReportResult.Refused, submit())
        assertEquals(0, pending.size)
    }

    @Test
    fun `a pending report survives a restart`() {
        sendFirstOpen()
        h.answer(503)
        assertEquals(ReportResult.Saved, submit())
        h.restart()
        h.answer(200)
        assertEquals(SendOutcome.Done, h.send())
        val requests = generateSequence { server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS) }.toList()
        assertEquals(2, requests.count { it.url.encodedPath == "/api/v1/reports" })
        assertEquals(0, pending.size)
    }

    @Test
    fun `text is cut at 4000 characters and a blank text sends nothing`() {
        h.answer(200)
        assertEquals(ReportResult.Sent, submit("x".repeat(5_000)))
        assertEquals(4_000, h.take().json()["text"]!!.jsonPrimitive.content.length)
        assertEquals(ReportResult.Empty, submit("   \n "))
        assertEquals(0, pending.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the log is cut to the newest 512 KB on a character boundary`() {
        val line = "\u00e9 line of the log with some text in it\n"
        h.logText = line.repeat(40_000)
        h.answer(200)
        assertEquals(ReportResult.Sent, submit())
        val log = h.take().json()["log"]!!.jsonPrimitive.content
        assertTrue(log.toByteArray(Charsets.UTF_8).size <= ProblemReports.MAX_LOG_BYTES)
        assertTrue(log.toByteArray(Charsets.UTF_8).size > ProblemReports.MAX_LOG_BYTES - 5_000)
        assertTrue("starts at a line", log.startsWith("\u00e9 line"))
        assertTrue(log.endsWith("in it\n"))
    }

    @Test
    fun `a log full of control characters is shortened until the request fits 1 MB`() {
        h.logText = "\u0001".repeat(ProblemReports.MAX_LOG_BYTES)
        h.answer(200)
        assertEquals(ReportResult.Sent, submit())
        val request = h.take()
        assertTrue(request.body!!.size <= 1024 * 1024)
        assertTrue(request.json()["log"]!!.jsonPrimitive.content.isNotEmpty())
    }

    @Test
    fun `at most 5 reports are kept, the newest`() {
        server.close()
        repeat(7) {
            h.clock += 1_000
            assertEquals(ReportResult.Saved, submit("report $it"))
        }
        assertEquals(5, pending.size)
        val texts = pending.sortedBy { it.name }.map { kotlinx.serialization.json.Json.parseToJsonElement(it.readText()).let { e -> (e as JsonObject)["text"]!!.jsonPrimitive.content } }
        assertEquals(listOf("report 2", "report 3", "report 4", "report 5", "report 6"), texts)
        assertTrue(h.logs.any { it.contains("older ones deleted") })
    }

    @Test
    fun `a report is never logged`() {
        h.answer(200)
        submit("secret words", "me@x.com")
        h.take()
        assertFalse(h.logs.any { it.contains("secret words") || it.contains("me@x.com") })
    }

    @Test
    fun `e-mail check`() {
        assertNull(ProblemReports.emailProblem(""))
        assertNull(ProblemReports.emailProblem(" a@b.co "))
        assertEquals(EmailProblem.NotAnAddress, ProblemReports.emailProblem("ab.co"))
        assertEquals(EmailProblem.NotAnAddress, ProblemReports.emailProblem("a b@c.d"))
        assertEquals(EmailProblem.NotAnAddress, ProblemReports.emailProblem("@c.d"))
        assertEquals(EmailProblem.NotAnAddress, ProblemReports.emailProblem("a@b@c"))
        assertEquals(EmailProblem.TooLong, ProblemReports.emailProblem("a".repeat(250) + "@b.co"))
    }

    @Test
    fun `a build with no endpoint keeps nothing and says so`() {
        val reports =
            ProblemReports(
                dir = File(dir, "none"),
                sender = null,
                scheduler = h.scheduler,
                ready = {},
                installIdIfOn = { null },
                statisticsOn = { false },
                common = { TelemetryHarness.COMMON },
                readLog = { null },
                log = { h.logs += it },
            )
        assertEquals(ReportResult.Unavailable, runBlocking { reports.submit("it broke", null, true) })
        assertEquals(0, File(dir, "none").listFiles().orEmpty().size)
        assertEquals(SendOutcome.Done, runBlocking { reports.sendPending() })
    }
}
