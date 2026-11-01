package com.plumemu.achievements.api

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** A fake RetroAchievements server and a client pointed at it; waits and the clock are recorded, not real. */
internal class RaFixture(val cacheDir: File) : AutoCloseable {
    val server = MockWebServer().apply { start() }
    val logLines = CopyOnWriteArrayList<String>()
    val waits = CopyOnWriteArrayList<Long>()
    val now = AtomicLong(START_MILLIS)
    var httpBuilt = false
        private set

    private val http = lazy {
        httpBuilt = true
        OkHttpClient.Builder()
            .readTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MILLIS * 4, TimeUnit.MILLISECONDS)
            .build()
    }

    val client: RetroAchievementsClient = RetroAchievementsClient(
        RaHttp(http, server.url("/API/").toString(), WebApiKey(KEY), { logLines += it }, wait = { waits += it }, clock = { now.get() }),
        GameListCache(cacheDir) { logLines += it },
        clock = { now.get() },
    )

    fun enqueue(response: MockResponse) = server.enqueue(response)

    fun takeRequest() = server.takeRequest(1, TimeUnit.SECONDS) ?: error("no request was sent")

    override fun close() = server.close()

    companion object {
        /** A made-up key, long enough that any leak of it would be found. */
        const val KEY = "testKeyAbcdef0123456789Zz"
        const val ULID = "00003EMFWR7XB8SDPEHB3K56ZQ"
        const val START_MILLIS = 1_700_000_000_000L
        const val TIMEOUT_MILLIS = 400L
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}

internal fun fixture(name: String): String =
    checkNotNull(RaFixture::class.java.getResource("/ra/$name")) { "missing fixture $name" }.readText()

internal fun ok(body: String): MockResponse = MockResponse.Builder().code(200).body(body).build()

internal fun status(code: Int, body: String = "", retryAfter: String? = null): MockResponse =
    MockResponse.Builder().code(code).body(body).apply { if (retryAfter != null) addHeader("Retry-After", retryAfter) }.build()
