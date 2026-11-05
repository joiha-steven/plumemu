package com.plumemu.telemetry

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * `game_unidentified` through [Telemetry.reportUnidentified]: only while statistics are on, each
 * fingerprint once per install, at most [MAX_UNIDENTIFIED_PER_REPORT] a call, never a name.
 */
class UnidentifiedTelemetryTest {
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
        h.stop()
        server.close()
    }

    private fun game(n: Int) = TelemetryEvent.GameUnidentified(SystemRef("nes"), "zip", 40_960, 0x10000000L + n, null, null, null)

    /** Sends what is queued, answering 204 to every request, and returns the events that reached the server. */
    private fun sendAll(): List<JsonObject> {
        h.answer(*IntArray(20) { 204 })
        h.send()
        val out = ArrayList<JsonObject>()
        while (true) {
            val request = server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
            if (request.url.encodedPath != "/api/v1/events") continue
            out += request.json().getValue("events").jsonArray.map { it.jsonObject }
        }
        return out
    }

    private fun prints(events: List<JsonObject>) = events.filter { it.getValue("name").jsonPrimitive.content == "game_unidentified" }

    private fun crcs(events: List<JsonObject>) = prints(events).map { it.getValue("props").jsonObject.getValue("crc").jsonPrimitive.content }

    @Test
    fun eachFingerprintGoesOnceEvenAcrossARestart() {
        h.telemetry.reportUnidentified(listOf(game(1), game(2), game(2)))
        assertEquals(listOf("10000001", "10000002"), crcs(sendAll()))

        h.telemetry.reportUnidentified(listOf(game(1), game(2), game(3)))
        assertEquals(listOf("10000003"), crcs(sendAll()))

        h.restart()
        h.telemetry.reportUnidentified(listOf(game(1), game(2), game(3)))
        assertEquals(emptyList<String>(), crcs(sendAll()))
    }

    @Test
    fun a5xxKeepsTheFingerprintQueuedNotMarkedAndNotQueuedTwiceThenA2xxMarksIt() {
        h.answer(204)
        h.send() // first_open
        server.takeRequest()
        h.telemetry.reportUnidentified(listOf(game(1)))
        h.answer(503)
        assertEquals(SendOutcome.Retry, h.send())
        server.takeRequest()
        assertEquals("not sent: not marked", 0, h.telemetry.unidentifiedSent.size())
        assertEquals(1, h.telemetry.queue.size())
        h.telemetry.reportUnidentified(listOf(game(1)))
        h.answer(503)
        h.send()
        server.takeRequest()
        assertEquals("still queued: not queued twice", 1, h.telemetry.queue.size())
        assertEquals("the retry carries it, once", listOf("10000001"), crcs(sendAll()))
        assertEquals("accepted: marked", 1, h.telemetry.unidentifiedSent.size())
    }

    @Test
    fun a4xxForgetsTheFingerprintSoTheNextScanOffersItAgain() {
        h.answer(204)
        h.send()
        server.takeRequest()
        h.telemetry.reportUnidentified(listOf(game(1)))
        h.answer(400)
        assertEquals(SendOutcome.Done, h.send())
        server.takeRequest()
        assertEquals("dropped from the queue", 0, h.telemetry.queue.size())
        assertEquals("and not marked sent", 0, h.telemetry.unidentifiedSent.size())
        h.telemetry.reportUnidentified(listOf(game(1)))
        assertEquals(listOf("10000001"), crcs(sendAll()))
    }

    @Test
    fun aCallQueuesAtMostTheCapTheRestGoNextTime() {
        val many = (1..250).map(::game)
        h.telemetry.reportUnidentified(many)
        assertEquals(MAX_UNIDENTIFIED_PER_REPORT, prints(sendAll()).size)
        h.telemetry.reportUnidentified(many)
        assertEquals(MAX_UNIDENTIFIED_PER_REPORT, prints(sendAll()).size)
        h.telemetry.reportUnidentified(many)
        assertEquals(50, prints(sendAll()).size)
        h.telemetry.reportUnidentified(many)
        assertEquals(0, prints(sendAll()).size)
    }

    @Test
    fun nothingIsQueuedSentOrKeptWhileStatisticsAreOffAndTheGamesGoOnceSwitchedOn() {
        h.storedSwitch = false
        h.clearData()
        h.telemetry.reportUnidentified(listOf(game(1), game(2)))
        h.send()
        assertEquals("nothing reaches the server while off", 0, server.requestCount)
        assertEquals(0, h.telemetry.queue.size())
        assertEquals(0, h.telemetry.unidentifiedSent.size())
        assertFalse("no file is written while off", File(h.dir, UnidentifiedSent.FILE_NAME).exists())

        // Switched on: the next report (after the next scan) sends the games found while it was off.
        h.telemetry.setEnabled(true, SwitchPlace.Settings)
        h.telemetry.reportUnidentified(listOf(game(1), game(2)))
        assertEquals(listOf("10000001", "10000002"), crcs(sendAll()))
    }

    @Test
    fun aReportMadeBeforeTheStoredSwitchIsReadSendsNothingWhenItIsOff() {
        // The quick check still says on (nothing read yet), the stored switch says off: the
        // report passes the quick check and must be stopped where it is handled.
        h.storedSwitch = false
        h.startOnMake = false
        h.clearData()
        h.telemetry.reportUnidentified(listOf(game(1), game(2)))
        h.send()
        assertEquals("nothing reaches the server", 0, server.requestCount)
        assertEquals("nothing queued", 0, h.telemetry.queue.size())
        assertEquals("nothing kept", 0, h.telemetry.unidentifiedSent.size())
        assertFalse("no file is written while off", File(h.dir, UnidentifiedSent.FILE_NAME).exists())
    }

    @Test
    fun aSwitchOffDeletesQueuedFingerprintsAndForgetsThemSoTheyGoAfterTheNextSwitchOn() {
        h.answer(204)
        h.send() // first_open
        server.takeRequest()
        h.telemetry.reportUnidentified(listOf(game(1)))
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        h.answer(204)
        h.send() // the one decline, no events
        assertEquals("/api/v1/declined", h.take().url.encodedPath)
        assertEquals(0, h.telemetry.queue.size())
        assertEquals(0, h.telemetry.unidentifiedSent.size())

        h.telemetry.reportUnidentified(listOf(game(1)))
        h.send()
        assertEquals("off: nothing more", 2, server.requestCount)

        h.telemetry.setEnabled(true, SwitchPlace.Settings)
        h.telemetry.reportUnidentified(listOf(game(1)))
        assertEquals(listOf("10000001"), crcs(sendAll()))
    }

    @Test
    fun trackCannotSendAFingerprintAroundTheOncePerInstallRule() {
        h.telemetry.track(game(1))
        assertEquals(emptyList<String>(), crcs(sendAll()))
    }

    @Test
    fun aCodeOrKindThatIsNotOfItsShapeIsLeftOutAndAGameWithNothingLeftIsNotSent() {
        val path = "/storage/emulated/0/Roms/Secret Game (USA).zip"
        h.telemetry.reportUnidentified(
            listOf(
                TelemetryEvent.GameUnidentified(SystemRef("snes"), "zip/Secret", 1_024, null, "Secret Game", path, null),
                TelemetryEvent.GameUnidentified(SystemRef("snes"), "Secret Game", 1_024, 0x0A1B2C3DL, "snes", "Secret Game", null),
            ),
        )
        val sent = prints(sendAll())
        assertEquals(1, sent.size)
        assertEquals(mapOf("system_id" to "snes", "size" to "1024", "crc" to "0a1b2c3d"), sent.single().getValue("props").jsonObject.mapValues { it.value.jsonPrimitive.content })
        assertFalse(sent.toString().contains("Secret"))
        assertTrue(h.logs.none { it.contains("Secret") })
    }
}
