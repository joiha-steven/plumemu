package com.plumemu.telemetry

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The module against a local fake server (`/api/v1` batches):
 * the first launch, the batch shape, the switch, the server's answers, the install id.
 */
class TelemetryTest {
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

    private fun events(request: JsonObject): List<JsonObject> = request.getValue("events").jsonArray.map { it.jsonObject }

    private fun names(request: JsonObject): List<String> = events(request).map { it.getValue("name").jsonPrimitive.content }

    @Test
    fun firstOpenIsAskedToGoAtOnceAtTheFirstLaunchBeforeAnyChoice() {
        h.answer(204)
        assertEquals(SendOutcome.Done, h.send())
        assertTrue("first_open asks for an immediate send", h.scheduler.now.get() >= 1)

        val request = h.take()
        assertEquals("/api/v1/events", request.url.encodedPath)
        val body = request.json()
        assertEquals(listOf("first_open"), names(body))
        assertEquals(JsonObject(emptyMap()), events(body).single().getValue("props"))
        assertEquals(0, h.telemetry.queue.size())
    }

    @Test
    fun aFirstLaunchWithStatisticsOffSendsNothingNotEvenADecline() {
        // The default is off: the person never said yes, so there is nothing to decline.
        h.storedSwitch = false
        h.clearData()
        h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home))
        assertEquals(SendOutcome.Done, h.send())

        assertEquals("no first_open, no events, no declined", 0, server.requestCount)
        assertEquals(0, h.telemetry.queue.size())
        assertFalse(runBlocking { h.telemetry.isEnabled() })
        assertEquals("no send was asked for", 0, h.scheduler.now.get())
    }

    @Test
    fun aDefaultOffInstallSendsItsOneDeclineOnlyWhenAPersonSwitchesOnThenOff() {
        h.storedSwitch = false
        h.clearData()
        h.answer(204, 204)
        h.telemetry.setEnabled(true, SwitchPlace.Settings)
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        h.send()

        assertEquals("/api/v1/declined", h.take().url.encodedPath)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun anInstallThatSentFirstOpenAndChoseNothingGoesOffWithoutADecline() {
        // Opened once on a build where statistics were on from the first launch, setup never finished, then updated:
        // the app says nothing was chosen (null), which is not a person switching off.
        h.answer(204)
        h.send()
        assertEquals("/api/v1/events", h.take().url.encodedPath)
        h.storedSwitch = null
        h.restart()
        assertEquals(SendOutcome.Done, h.send())

        assertEquals("no stats_declined", 1, server.requestCount)
        assertFalse(runBlocking { h.telemetry.isEnabled() })
    }

    @Test
    fun anOptInInstallSendsFirstOpenOnItsFirstSwitchOnAndNeverAgain() {
        h.storedSwitch = false
        h.clearData()
        h.answer(204, 204, 204)
        h.telemetry.setEnabled(true, SwitchPlace.Setup)
        h.send()
        val first = h.take().json()
        assertEquals(listOf("first_open", "stats_switched_on"), names(first))

        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        h.send()
        assertEquals("/api/v1/declined", h.take().url.encodedPath)
        h.telemetry.setEnabled(true, SwitchPlace.Settings)
        h.send()
        assertEquals(listOf("stats_switched_on"), names(h.take().json()))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun anOldFormatStateFileCountsAsFirstOpenSentSoSwitchingOffAndOnSendsNoSecondOne() {
        h.stop()
        h.dir.deleteRecursively()
        h.dir.mkdirs()
        // The file a build from before first_open_sent left: an install id and "on", nothing else.
        val job = kotlinx.coroutines.Job()
        val raw =
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = kotlinx.coroutines.CoroutineScope(job + kotlinx.coroutines.Dispatchers.IO),
                produceFile = { java.io.File(h.dir, StateStore.FILE_NAME) },
            )
        runBlocking {
            raw.edit {
                it[androidx.datastore.preferences.core.stringPreferencesKey("install_id")] = "old-install"
                it[androidx.datastore.preferences.core.booleanPreferencesKey("enabled")] = true
            }
            job.cancelAndJoin()
        }
        h.startAgain()
        h.answer(204, 204)
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        h.telemetry.setEnabled(true, SwitchPlace.Settings)
        h.send()

        val requests = listOf(h.take(), h.take()).map { it.url.encodedPath to it }
        val events = requests.single { it.first == "/api/v1/events" }.second.json()
        assertEquals(listOf("stats_switched_on"), names(events))
    }

    @Test
    fun aBatchCarriesTheInstallIdTheCommonFieldsAndEachEventInThePlansShape() {
        h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.SettingsAdvanced))
        h.telemetry.track(TelemetryEvent.LibraryScanned(ScanTrigger.Setup, games = 1234, systems = 7, seconds = 12, result = ScanResult.Ok))
        h.answer(204)
        h.send()

        val request = h.take()
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
        val body = request.json()
        assertEquals(setOf("v", "install_id", "common", "events"), body.keys)
        assertEquals(JsonPrimitive(1), body["v"])
        val installId = body.getValue("install_id").jsonPrimitive.content
        assertEquals(runBlocking { h.telemetry.installId() }, installId)
        assertEquals(TelemetryHarness.COMMON, body["common"])
        assertEquals(listOf("first_open", "screen_opened", "library_scanned"), names(body))
        val scanned = events(body)[2]
        assertEquals(setOf("id", "name", "time", "props"), scanned.keys)
        assertEquals("2025-10-24T10:00:00.000Z", scanned.getValue("time").jsonPrimitive.content)
        assertEquals(
            JsonObject(
                mapOf(
                    "trigger" to JsonPrimitive("setup"),
                    "games_bucket" to JsonPrimitive("1k–5k"),
                    "systems" to JsonPrimitive(7),
                    "seconds" to JsonPrimitive(12L),
                    "result" to JsonPrimitive("ok"),
                ),
            ),
            scanned["props"],
        )
        val ids = events(body).map { it.getValue("id").jsonPrimitive.content }
        assertEquals("each event has its own id", ids.size, ids.toSet().size)
    }

    @Test
    fun offDeletesTheQueueAndSendsOneDeclineWithoutAnInstallId() {
        h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home))
        h.telemetry.setEnabled(false, SwitchPlace.Setup)
        h.answer(204)
        assertEquals(SendOutcome.Done, h.send())

        val request = h.take()
        assertEquals("/api/v1/declined", request.url.encodedPath)
        val body = request.json()
        assertEquals(setOf("v", "where", "common"), body.keys)
        assertNull("never an install id", body["install_id"])
        assertEquals(JsonPrimitive("setup"), body["where"])
        assertEquals(TelemetryHarness.COMMON, body["common"])
        assertEquals("the queue is gone, first_open included", 0, h.telemetry.queue.size())
        assertEquals("nothing else was sent", 1, server.requestCount)
    }

    @Test
    fun neverASecondDeclineEvenAfterOnOffOnOffAndARestart() {
        h.telemetry.setEnabled(false, SwitchPlace.Setup)
        h.answer(204)
        h.send()
        assertEquals("/api/v1/declined", h.take().url.encodedPath)

        h.telemetry.setEnabled(true, SwitchPlace.Settings)
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        h.telemetry.setEnabled(true, SwitchPlace.Settings)
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        h.send()
        // The app stores the same switch the module was given.
        h.storedSwitch = false
        h.restart()
        h.telemetry.setEnabled(true, SwitchPlace.Settings)
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        h.send()

        assertEquals("one request in all: the first decline", 1, server.requestCount)
    }

    @Test
    fun whileOffNothingIsQueuedOrSentAcrossARestart() {
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        h.answer(204)
        h.send()
        h.take()
        // The app stores the same switch the module was given.
        h.storedSwitch = false
        h.restart()
        h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home))
        h.telemetry.track(TelemetryEvent.AppBackground(30, 2, InputUsed.Gamepad))
        assertEquals(SendOutcome.Done, h.send())

        assertEquals(0, h.telemetry.queue.size())
        assertEquals(1, server.requestCount)
        assertFalse(runBlocking { h.telemetry.isEnabled() })
    }

    @Test
    fun anEventTrackedJustBeforeSwitchingOffNeverGoes() {
        // Nothing reached the server yet: the event is in the queue when the switch goes off.
        h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Search))
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        h.answer(204, 204)
        h.send()

        assertEquals("/api/v1/declined", h.take().url.encodedPath)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun onAgainResumesWithTheSameInstallIdAndSaysSo() {
        h.answer(204)
        h.send()
        val before = h.take().json().getValue("install_id").jsonPrimitive.content
        h.telemetry.setEnabled(false, SwitchPlace.Settings)
        h.answer(204)
        h.send()
        h.take()

        h.telemetry.setEnabled(true, SwitchPlace.Settings)
        h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home))
        h.answer(204)
        h.send()

        val body = h.take().json()
        assertEquals(before, body.getValue("install_id").jsonPrimitive.content)
        assertEquals(listOf("stats_switched_on", "screen_opened"), names(body))
        assertEquals(JsonObject(mapOf("where" to JsonPrimitive("settings"))), events(body)[0]["props"])
    }

    @Test
    fun settingTheValueItAlreadyHasDoesNothing() {
        h.telemetry.setEnabled(true, SwitchPlace.Settings)
        h.answer(204)
        h.send()

        assertEquals(listOf("first_open"), names(h.take().json()))
    }

    @Test
    fun aBadRequestAnswerDropsTheBatch() {
        h.answer(400)
        assertEquals(SendOutcome.Done, h.send())
        h.take()

        assertEquals(0, h.telemetry.queue.size())
        assertTrue(h.logs.any { it.contains("400") && it.contains("dropped") })
    }

    @Test
    fun tooBigAndNotDeployedAnswersDropTheBatchToo() {
        for (code in listOf(413, 404, 405)) {
            h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home))
            h.answer(code)
            assertEquals("$code", SendOutcome.Done, h.send())
            h.take()
            assertEquals("$code", 0, h.telemetry.queue.size())
        }
    }

    @Test
    fun anUnavailableServerKeepsTheBatchAndTheRetrySendsTheSameEvents() {
        h.answer(503)
        assertEquals(SendOutcome.Retry, h.send())
        val first = h.take().json()
        assertEquals(1, h.telemetry.queue.size())

        h.answer(204)
        assertEquals(SendOutcome.Done, h.send())
        val again = h.take().json()
        assertEquals("the same event ids, so the server stores them once", events(first), events(again))
        assertEquals(0, h.telemetry.queue.size())
    }

    @Test
    fun tooManyRequestsKeepsTheBatch() {
        h.answer(429)
        assertEquals(SendOutcome.Retry, h.send())
        assertEquals(1, h.telemetry.queue.size())
    }

    @Test
    fun offlineKeepsTheBatchAndTheDecline() {
        server.close()
        assertEquals(SendOutcome.Retry, h.send())
        assertEquals(1, h.telemetry.queue.size())

        h.telemetry.setEnabled(false, SwitchPlace.Setup)
        assertEquals(SendOutcome.Retry, h.send())
        assertTrue(h.logs.any { it.contains("/api/v1/declined") && it.contains("not reached") })
    }

    @Test
    fun aDeclineThatCouldNotGoIsSentAfterARestartWithItsPlace() {
        h.telemetry.setEnabled(false, SwitchPlace.Setup)
        h.answer(503)
        assertEquals(SendOutcome.Retry, h.send())
        h.take()

        // The app stores the same switch the module was given.
        h.storedSwitch = false
        h.restart()
        assertTrue("the restart asks for the pending decline", h.scheduler.now.get() >= 1)
        h.answer(204)
        assertEquals(SendOutcome.Done, h.send())
        val body = h.take().json()
        assertEquals(JsonPrimitive("setup"), body["where"])
        h.send()
        assertEquals("delivered once", 2, server.requestCount)
    }

    @Test
    fun batchesHoldAtMostTwoHundredEvents() {
        repeat(450) { h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home)) }
        h.answer(204, 204, 204)
        assertEquals(SendOutcome.Done, h.send())

        val sizes = List(3) { events(h.take().json()).size }
        assertEquals(listOf(200, 200, 51), sizes)
    }

    @Test
    fun theInstallIdStaysAcrossARestartAndIsNewAfterClearingData() {
        val first = runBlocking { h.telemetry.installId() }
        assertNotNull(first)
        h.restart()
        assertEquals(first, runBlocking { h.telemetry.installId() })
        h.clearData()
        val fresh = runBlocking { h.telemetry.installId() }
        assertNotNull(fresh)
        assertNotEquals(first, fresh)
    }

    @Test
    fun firstOpenGoesOnlyOnceAcrossRestarts() {
        h.answer(204)
        h.send()
        h.restart()
        h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home))
        h.answer(204)
        h.send()

        h.take()
        assertEquals(listOf("screen_opened"), names(h.take().json()))
    }

    @Test
    fun theQueueSurvivesARestart() {
        h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Apps))
        h.answer(503)
        assertEquals(SendOutcome.Retry, h.send())
        h.take()

        h.restart()
        h.answer(204)
        assertEquals(SendOutcome.Done, h.send())
        assertEquals(listOf("first_open", "screen_opened"), names(h.take().json()))
    }

    @Test
    fun theFullQueueDropsTheOldestAndSaysHowManyInQueueOverflow() {
        // 2,000 is the cap; 150 more drop the oldest in chunks of 100.
        repeat(EventQueue.CAP + 150) { h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home)) }
        h.answer(*IntArray(11) { 204 })
        assertEquals(SendOutcome.Done, h.send())

        val bodies = List(10) { h.take().json() }
        val first = events(bodies[0])
        assertEquals("queue_overflow", first[0].getValue("name").jsonPrimitive.content)
        assertEquals(JsonObject(mapOf("dropped" to JsonPrimitive(200))), first[0]["props"])
        assertEquals(200, first.size)
        val sent = bodies.sumOf { events(it).size }
        // first_open and 2,150 screens, 200 of them dropped: 1,951 kept, plus the overflow event.
        assertEquals(1_952, sent)
        assertTrue(bodies.flatMap { names(it) }.count { it == "queue_overflow" } == 1)
    }

    @Test
    fun anUrgentEventAsksForASendAtOnceAndOthersWait() {
        runBlocking { h.telemetry.installId() }
        h.answer(204)
        h.send()
        val now = h.scheduler.now.get()
        h.telemetry.track(TelemetryEvent.ScreenOpened(ScreenId.Home))
        h.answer(204)
        h.send()
        assertEquals("a screen waits for the batch", now, h.scheduler.now.get())
        h.telemetry.track(TelemetryEvent.AppBackground(10, 1, InputUsed.Touch))
        h.answer(204)
        h.send()
        assertEquals("going to the background sends at once", now + 1, h.scheduler.now.get())
    }
}
