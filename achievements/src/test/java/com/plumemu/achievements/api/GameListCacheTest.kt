package com.plumemu.achievements.api

import com.plumemu.achievements.api.RaFixture.Companion.DAY_MILLIS
import com.plumemu.achievements.api.RaFixture.Companion.KEY
import com.plumemu.achievements.api.RaFixture.Companion.START_MILLIS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** The game list saved per system for 14 days, and what happens when RetroAchievements fails. */
class GameListCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dir: File by lazy { File(tmp.root, "ra-cache") }
    private val fx by lazy { RaFixture(dir) }

    @After fun tearDown() = fx.close()

    private fun firstFetch() = runBlocking {
        fx.enqueue(ok(fixture("game-list.json")))
        val first = fx.client.gameList(1) as RaResult.Ok
        assertNull(first.stale)
        first
    }

    @Test fun `a fresh copy sends no request`() = runBlocking {
        val first = firstFetch()
        fx.now.addAndGet(13 * DAY_MILLIS)

        assertEquals(first, fx.client.gameList(1))
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `an expired copy is fetched again`() = runBlocking {
        firstFetch()
        fx.now.addAndGet(15 * DAY_MILLIS)
        fx.enqueue(ok("""[{"ID":9,"Title":"New","ConsoleID":1,"Hashes":["AA"]}]"""))

        val again = fx.client.gameList(1) as RaResult.Ok
        assertEquals(listOf(9), again.value.games.map { it.id })
        assertNull(again.stale)
        assertEquals(2, fx.server.requestCount)
        // And that answer is the one saved now.
        fx.now.addAndGet(DAY_MILLIS)
        assertEquals(again, fx.client.gameList(1))
        assertEquals(2, fx.server.requestCount)
    }

    @Test fun `a failure with an expired copy returns the copy, marked stale with the reason`() = runBlocking {
        val first = firstFetch()
        fx.now.addAndGet(20 * DAY_MILLIS)
        repeat(2) { fx.enqueue(status(500)) }

        val stale = fx.client.gameList(1) as RaResult.Ok
        assertEquals(first.value, stale.value)
        assertEquals(Stale(START_MILLIS, RaResult.Busy(500)), stale.stale)
    }

    @Test fun `a failure without a copy is the failure`() = runBlocking {
        fx.enqueue(status(401, fixture("unauthorized.json")))
        assertEquals(RaResult.WrongKey, fx.client.gameList(1))
        assertFalse("nothing is saved for a failure", dir.exists() && dir.list().orEmpty().isNotEmpty())
    }

    @Test fun `a copy dated in the future is not trusted as fresh`() = runBlocking {
        firstFetch()
        fx.now.addAndGet(-DAY_MILLIS)
        fx.enqueue(ok(fixture("game-list.json")))

        fx.client.gameList(1)
        assertEquals(2, fx.server.requestCount)
    }

    @Test fun `a damaged file is fetched again, logged`() = runBlocking {
        firstFetch()
        File(dir, "ra-games-1.json").writeText("{ not json")
        fx.enqueue(ok(fixture("game-list.json")))

        assertTrue(fx.client.gameList(1) is RaResult.Ok)
        assertEquals(2, fx.server.requestCount)
        assertTrue(fx.logLines.toString(), fx.logLines.any { "unreadable" in it })
    }

    @Test fun `each system has its own file`() = runBlocking {
        firstFetch()
        fx.enqueue(ok("""[{"ID":50,"Title":"Other","ConsoleID":2,"Hashes":[]}]"""))

        val other = fx.client.gameList(2) as RaResult.Ok
        assertEquals(listOf(50), other.value.games.map { it.id })
        assertEquals(setOf("ra-games-1.json", "ra-games-2.json"), dir.list()!!.toSet())
    }

    @Test fun `the file holds only Plumemu's fields, never the key, the user or RetroAchievements' extras`() = runBlocking {
        firstFetch()
        val names = dir.list()!!.toList()
        assertEquals(listOf("ra-games-1.json"), names)
        val content = File(dir, names.single()).readText()
        assertFalse(KEY in content)
        assertFalse("ForumTopicID" in content || "ConsoleName" in content || "DateModified" in content)
        assertTrue("1bc674be034e43c96b86487ac69d9293" in content)
    }

    @Test fun `two requests for one system at once fetch it once`() = runBlocking {
        fx.enqueue(MockResponse.Builder().code(200).body(fixture("game-list.json")).headersDelay(100, TimeUnit.MILLISECONDS).build())

        val results = List(2) { async(Dispatchers.IO) { fx.client.gameList(1) } }.awaitAll()

        assertEquals(results[0], results[1])
        assertTrue(results[0] is RaResult.Ok)
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `a refused key with a saved copy is a wrong key, never the stale copy`() = runBlocking {
        firstFetch()
        fx.now.addAndGet(20 * DAY_MILLIS)
        fx.enqueue(status(401, fixture("unauthorized.json")))

        assertEquals(RaResult.WrongKey, fx.client.gameList(1))
    }

    @Test fun `a save that cannot be renamed into place still returns the list, logged`() = runBlocking {
        // A non-empty folder where the file should go: neither rename nor delete can replace it.
        File(dir, "ra-games-1.json").apply { mkdirs() }.resolve("blocker").writeText("x")
        fx.enqueue(ok(fixture("game-list.json")))

        val result = fx.client.gameList(1)

        assertTrue("$result", result is RaResult.Ok && result.value.games.size == 2)
        assertTrue(fx.logLines.toString(), fx.logLines.any { "not saved" in it })
        assertFalse("the temporary file is removed", File(dir, "ra-games-1.json.tmp").exists())
    }
}
