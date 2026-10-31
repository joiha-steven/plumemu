package com.plumemu.telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Records what [Telemetry] asked WorkManager for; the tests send by calling [Telemetry.sendPending]. */
internal class FakeScheduler : SendScheduler {
    val now = AtomicInteger()
    val soon = AtomicInteger()

    override fun sendNow() {
        now.incrementAndGet()
    }

    override fun sendSoon() {
        soon.incrementAndGet()
    }
}

/**
 * One process's [Telemetry] over [dir], sending to [server] (a local MockWebServer: nothing in
 * these tests reaches the network). [restart] ends this process's telemetry and starts another
 * over the same files, as a relaunch does.
 */
internal class TelemetryHarness(
    val dir: File,
    val server: MockWebServer,
) {
    val logs: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())
    val scheduler = FakeScheduler()
    private var ids = AtomicInteger()
    var clock = 1_761_300_000_000L

    /** The app's stored switch (`advanced.usageStatistics`), read at each start. */
    @Volatile
    var storedSwitch: Boolean? = true

    /** The log text a problem report attaches. */
    @Volatile
    var logText: String? = "log line one\nlog line two"

    /** Android's exit records the next start reads. */
    @Volatile
    var exits: List<ExitRecord> = emptyList()
    private var scope = newScope()
    var telemetry = make()
        private set

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun make(): Telemetry =
        Telemetry(
            dir = dir,
            readCommon = { COMMON },
            sender = Sender({ CLIENT }, server.url("/"), { logs += it }),
            scheduler = scheduler,
            scope = scope,
            log = { logs += it },
            newId = { "id-${ids.incrementAndGet()}-${System.nanoTime()}" },
            clock = { clock },
            storedSwitch = { storedSwitch },
            readExits = { exits },
            readLog = { logText },
        ).also { it.start() }

    fun restart() {
        stop()
        startAgain()
    }

    /** Starts another process's telemetry over the same files, the previous one already stopped. */
    fun startAgain() {
        scope = newScope()
        telemetry = make()
    }

    fun stop() =
        runBlocking {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }

    /** The app's data cleared: every file of the module gone. */
    fun clearData() {
        stop()
        dir.deleteRecursively()
        scope = newScope()
        telemetry = make()
    }

    fun answer(vararg codes: Int) = codes.forEach { server.enqueue(MockResponse(it, Headers.headersOf(), "")) }

    fun take(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS) ?: error("no request reached the server")

    fun send(): SendOutcome = runBlocking { telemetry.sendPending() }

    companion object {
        val CLIENT: OkHttpClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()

        val COMMON: JsonObject =
            buildJsonObject {
                put("app_version", JsonPrimitive("0.0.3-beta"))
                put("version_code", JsonPrimitive(321))
                put("build_type", JsonPrimitive("debug"))
                put("device_model", JsonPrimitive("AYN Thor"))
            }
    }
}

internal fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body!!.utf8()).jsonObject
