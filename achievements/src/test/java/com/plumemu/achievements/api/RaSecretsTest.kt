package com.plumemu.achievements.api

import com.plumemu.achievements.api.RaFixture.Companion.KEY
import com.plumemu.achievements.api.RaFixture.Companion.ULID
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The key and the user never reach a log line, a result's text or a saved file; and nothing runs before a call. */
class RaSecretsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `log lines blank the key and the user, and name the endpoint and the cause`() = runBlocking {
        RaFixture(tmp.newFolder()).use { fx ->
            // The drop first: on a pooled connection OkHttp would retry it on a new one.
            fx.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
            fx.client.profile("plumtester")
            fx.enqueue(status(401, fixture("unauthorized.json")))
            fx.client.gameProgress(1, ULID)
            fx.enqueue(ok("{ broken"))
            fx.client.completion(ULID)

            val lines = fx.logLines.toList()
            assertEquals(lines.toString(), 3, lines.size)
            for (secret in listOf(KEY, ULID, "plumtester")) {
                assertFalse("'$secret' leaked: $lines", lines.any { secret in it })
            }
            assertTrue(lines[0], lines[0].startsWith("API_GetUserProfile.php: no connection ("))
            assertTrue(lines[1], lines[1].startsWith("API_GetGameInfoAndUserProgress.php: WrongKey (HTTP 401)"))
            assertTrue(lines[1], "y=***" in lines[1] && "u=***" in lines[1])
            assertTrue(lines[2], lines[2].startsWith("API_GetUserCompletionProgress.php: BadAnswer"))
        }
    }

    @Test fun `results and the key's own text never show the key`() {
        assertEquals("WebApiKey(***)", WebApiKey(KEY).toString())
        val texts = listOf(RaResult.WrongKey, RaResult.RateLimited(3), RaResult.BadAnswer("HTTP 404"), RaResult.NoConnection)
            .map { it.toString() } + RaFixture(tmp.newFolder()).use { it.client.toString() }
        assertFalse(texts.toString(), texts.any { KEY in it })
    }

    @Test fun `redact blanks y and u wherever they stand, and the given secrets`() {
        val url = "https://retroachievements.org/API/API_GetUserProfile.php?u=Plum&y=$KEY&g=1"
        assertEquals("https://retroachievements.org/API/API_GetUserProfile.php?u=***&y=***&g=1", Redact.text(url))
        assertEquals("key *** in text", Redact.text("key $KEY in text", listOf(KEY)))
        assertEquals("body=x&yy=1", Redact.text("body=x&yy=1"))
    }

    @Test fun `building a client sends nothing, builds no HTTP client and makes no folder`() {
        val dir = File(tmp.root, "never-made")
        RaFixture(dir).use { fx ->
            fx.client.toString()
            assertEquals(0, fx.server.requestCount)
            assertFalse(fx.httpBuilt)
            assertFalse(dir.exists())
        }
    }

    @Test fun `the public constructor reads its HTTP client only on the first call`() = runBlocking {
        var built = false
        val http = lazy {
            built = true
            okhttp3.OkHttpClient()
        }
        RaFixture(tmp.newFolder()).use { fx ->
            val client = RetroAchievementsClient(WebApiKey(KEY), File(tmp.root, "c"), log = {}, httpClient = http, baseUrl = fx.server.url("/API/").toString())
            assertFalse(built)
            assertEquals(0, fx.server.requestCount)

            fx.enqueue(ok(fixture("profile.json")))
            assertTrue(client.profile("plumtester") is RaResult.Ok)
            assertTrue(built)
        }
    }
}
