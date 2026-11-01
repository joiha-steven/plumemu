package com.plumemu.achievements.api

import com.plumemu.achievements.api.RaFixture.Companion.KEY
import com.plumemu.achievements.api.RaFixture.Companion.ULID
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import okio.Buffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

/** Every failure, on every endpoint: what it maps to, how many requests and waits it costs. */
class RaErrorMappingTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Endpoint(val name: String, val isUserCall: Boolean, val good: String, val call: suspend (RetroAchievementsClient) -> RaResult<*>)

    private val endpoints = listOf(
        Endpoint("profile", true, fixture("profile.json")) { it.profile("plumtester") },
        Endpoint("gameList", false, fixture("game-list.json")) { it.gameList(1) },
        Endpoint("gameProgress", true, fixture("game-progress.json")) { it.gameProgress(1, ULID) },
        Endpoint("completion", true, fixture("completion-page-2.json").replace("\"Total\":3", "\"Total\":1")) { it.completion(ULID) },
    )

    /** Runs [check] once per endpoint, each on a fresh server and an empty cache. */
    private fun eachEndpoint(check: suspend RaFixture.(Endpoint) -> Unit) = runBlocking {
        for (endpoint in endpoints) {
            RaFixture(tmp.newFolder()).use { fx ->
                try {
                    fx.check(endpoint)
                } catch (e: AssertionError) {
                    throw AssertionError("${endpoint.name}: ${e.message}", e)
                }
                assertFalse("${endpoint.name} logged the key: ${fx.logLines}", fx.logLines.any { KEY in it })
            }
        }
    }

    @Test fun `401 is a wrong key, asked once`() = eachEndpoint { e ->
        enqueue(status(401, fixture("unauthorized.json")))
        assertEquals(RaResult.WrongKey, e.call(client))
        assertEquals(1, server.requestCount)
        assertEquals(emptyList<Long>(), waits)
    }

    @Test fun `429 with a short Retry-After waits that long once, then gives up`() = eachEndpoint { e ->
        repeat(2) { enqueue(status(429, retryAfter = "3")) }
        assertEquals(RaResult.RateLimited(3), e.call(client))
        assertEquals(2, server.requestCount)
        assertEquals(listOf(3000L), waits)
    }

    @Test fun `429 without Retry-After waits the default once`() = eachEndpoint { e ->
        repeat(2) { enqueue(status(429)) }
        assertEquals(RaResult.RateLimited(null), e.call(client))
        assertEquals(listOf(RaHttp.DEFAULT_WAIT_SECONDS * 1000), waits)
    }

    @Test fun `429 with a long Retry-After fails at once without waiting`() = eachEndpoint { e ->
        enqueue(status(429, retryAfter = "3600"))
        assertEquals(RaResult.RateLimited(3600), e.call(client))
        assertEquals(1, server.requestCount)
        assertEquals(emptyList<Long>(), waits)
    }

    @Test fun `429 then a good answer is Ok`() = eachEndpoint { e ->
        enqueue(status(429, retryAfter = "1"))
        enqueue(ok(e.good))
        assertTrue(e.call(client) is RaResult.Ok)
        assertEquals(listOf(1000L), waits)
    }

    @Test fun `500 twice is busy, after one wait`() = eachEndpoint { e ->
        repeat(2) { enqueue(status(500, "oops")) }
        assertEquals(RaResult.Busy(500), e.call(client))
        assertEquals(2, server.requestCount)
        assertEquals(1, waits.size)
    }

    @Test fun `503 then a good answer is Ok`() = eachEndpoint { e ->
        enqueue(status(503))
        enqueue(ok(e.good))
        assertTrue(e.call(client) is RaResult.Ok)
    }

    @Test fun `malformed JSON is a bad answer`() = eachEndpoint { e ->
        enqueue(ok("{\"ID\": 1, \"Title\": "))
        assertTrue(e.call(client) is RaResult.BadAnswer)
    }

    @Test fun `an empty body is a bad answer`() = eachEndpoint { e ->
        enqueue(ok(""))
        assertEquals(RaResult.BadAnswer("empty body"), e.call(client))
    }

    @Test fun `a dropped connection is no connection`() = eachEndpoint { e ->
        enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        assertEquals(RaResult.NoConnection, e.call(client))
    }

    @Test fun `an answer cut off halfway is no connection`() = eachEndpoint { e ->
        enqueue(MockResponse.Builder().code(200).body(Buffer().write(ByteArray(200_000))).onResponseBody(SocketEffect.CloseSocket()).build())
        assertEquals(RaResult.NoConnection, e.call(client))
    }

    @Test fun `a timeout is no connection`() = eachEndpoint { e ->
        enqueue(MockResponse.Builder().code(200).body(e.good).headersDelay(RaFixture.TIMEOUT_MILLIS * 5, TimeUnit.MILLISECONDS).build())
        assertEquals(RaResult.NoConnection, e.call(client))
    }

    @Test fun `404 is an unknown user on user calls and a bad answer elsewhere`() = eachEndpoint { e ->
        enqueue(status(404, """{"message":"Not Found"}"""))
        val expected = if (e.isUserCall) RaResult.UnknownUser else RaResult.BadAnswer("HTTP 404")
        assertEquals(expected, e.call(client))
        assertEquals(1, server.requestCount)
    }

    @Test fun `an empty profile is an unknown user`() = runBlocking {
        for (body in listOf("{}", "[]", """{"User":null,"ULID":null}""")) {
            RaFixture(tmp.newFolder()).use { fx ->
                fx.enqueue(ok(body))
                assertEquals(body, RaResult.UnknownUser, fx.client.profile("nobody-here"))
            }
        }
    }

    @Test fun `cancelling a pending call cancels it, without a no-connection result or log line`() = runBlocking {
        RaFixture(tmp.newFolder()).use { fx ->
            fx.enqueue(MockResponse.Builder().code(200).body(fixture("game-progress.json")).headersDelay(300, TimeUnit.MILLISECONDS).build())
            val call = async(Dispatchers.IO) { fx.client.gameProgress(1, ULID) }
            fx.takeRequest()
            call.cancel()

            val thrown = runCatching { call.await() }.exceptionOrNull()
            assertTrue("$thrown", thrown is CancellationException)
            delay(400)
            assertFalse(fx.logLines.toString(), fx.logLines.any { "no connection" in it })
        }
    }
}
