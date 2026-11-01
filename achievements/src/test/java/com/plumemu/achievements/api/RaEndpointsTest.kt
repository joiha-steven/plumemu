package com.plumemu.achievements.api

import com.plumemu.achievements.api.RaFixture.Companion.KEY
import com.plumemu.achievements.api.RaFixture.Companion.ULID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Each endpoint's good answer, read from fixtures shaped like RetroAchievements' documented answers. */
class RaEndpointsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val fx by lazy { RaFixture(tmp.newFolder("cache")) }

    @After fun tearDown() = fx.close()

    private fun <T> RaResult<T>.value(): T = (this as? RaResult.Ok ?: error("expected Ok, got $this")).value

    @Test fun `profile reads the name, ULID, points and picture`() = runBlocking {
        fx.enqueue(ok(fixture("profile.json")))

        val profile = fx.client.profile("plumtester").value()

        assertEquals(RaProfile("PlumTester", ULID, "https://media.retroachievements.org/UserPic/PlumTester.png", 1234, 56), profile)
        val url = fx.takeRequest().url
        assertEquals("/API/API_GetUserProfile.php", url.encodedPath)
        assertEquals("plumtester", url.queryParameter("u"))
        assertEquals(KEY, url.queryParameter("y"))
    }

    @Test fun `game list reads every game and its hashes, dropping entries without an id`() = runBlocking {
        fx.enqueue(ok(fixture("game-list.json")))

        val list = fx.client.gameList(1).value()

        assertEquals(1, list.consoleId)
        assertEquals(
            listOf(
                RaGame(1, "Sonic the Hedgehog", 1, 23, 445, "/Images/067895.png", listOf("1bc674be034e43c96b86487ac69d9293", "8c3e7b7a7d5b6f3e2e1d0c9b8a796857")),
                RaGame(2, "Streets of Rage", 1, 10, 100, "/Images/000002.png", emptyList()),
            ),
            list.games,
        )
        val url = fx.takeRequest().url
        assertEquals("/API/API_GetGameList.php", url.encodedPath)
        assertEquals(mapOf("i" to "1", "h" to "1", "f" to "1", "y" to KEY), url.queryParameterNames.associateWith { url.queryParameter(it) })
    }

    @Test fun `game progress reads earned and locked achievements in display order`() = runBlocking {
        fx.enqueue(ok(fixture("game-progress.json")))

        val progress = fx.client.gameProgress(1, ULID).value()

        assertEquals(1, progress.id)
        assertEquals(3, progress.achievementCount)
        assertEquals(2, progress.awarded)
        assertEquals(1, progress.awardedHardcore)
        assertEquals("an award kind added later is tolerated", AwardKind.Unknown, progress.highestAward)
        assertEquals(listOf(7, 8, 9), progress.achievements.map { it.id })
        val (hardcore, softcore, locked) = progress.achievements
        assertEquals("2024-05-01 10:00:00", hardcore.earnedHardcoreAt)
        assertEquals(10, hardcore.points)
        assertTrue(softcore.isEarned)
        assertNull(softcore.earnedHardcoreAt)
        assertFalse(locked.isEarned)
        assertEquals("250338", locked.badgeName)
        assertFalse("trigger logic is never kept", progress.toString().contains("0xH"))
        val url = fx.takeRequest().url
        assertEquals("/API/API_GetGameInfoAndUserProgress.php", url.encodedPath)
        assertEquals("1", url.queryParameter("g"))
        assertEquals(ULID, url.queryParameter("u"))
    }

    @Test fun `a game without achievements written as an empty list reads as none`() = runBlocking {
        fx.enqueue(ok("""{"ID":5,"Title":"Bare","ConsoleID":1,"NumAchievements":0,"Achievements":[],"HighestAwardKind":null}"""))

        val progress = fx.client.gameProgress(5, ULID).value()

        assertEquals(emptyList<RaAchievement>(), progress.achievements)
        assertEquals(AwardKind.None, progress.highestAward)
    }

    @Test fun `completion pages until the offset reaches the total`() = runBlocking {
        fx.enqueue(ok(fixture("completion-page-1.json")))
        fx.enqueue(ok(fixture("completion-page-2.json")))

        val games = fx.client.completion(ULID).value()

        assertEquals(listOf(1, 2, 3), games.map { it.gameId })
        assertEquals(listOf(AwardKind.Mastered, AwardKind.None, AwardKind.BeatenHardcore), games.map { it.highestAward })
        assertEquals(23, games[0].awardedHardcore)
        assertEquals(listOf("Sonic the Hedgehog", "", ""), games.map { it.title })
        val offsets = List(2) { fx.takeRequest().url }.map { it.queryParameter("o") to it.queryParameter("c") }
        assertEquals(listOf("0" to "500", "2" to "500"), offsets)
        assertEquals(2, fx.server.requestCount)
    }

    @Test fun `completion stops on an empty page and fails when a later page fails`() = runBlocking {
        fx.enqueue(ok("""{"Count":0,"Total":0,"Results":[]}"""))
        assertEquals(RaResult.Ok(emptyList<RaCompletion>()), fx.client.completion(ULID))

        fx.enqueue(ok(fixture("completion-page-1.json")))
        fx.enqueue(status(401, fixture("unauthorized.json")))
        assertEquals(RaResult.WrongKey, fx.client.completion(ULID))
    }

    @Test fun `award kinds read from RetroAchievements' words`() {
        assertEquals(AwardKind.Mastered, AwardKind.from("mastered"))
        assertEquals(AwardKind.Completed, AwardKind.from("completed"))
        assertEquals(AwardKind.BeatenSoftcore, AwardKind.from("beaten-softcore"))
        assertEquals(AwardKind.None, AwardKind.from(null))
        assertEquals(AwardKind.Unknown, AwardKind.from("gold-star"))
    }

    @Test fun `media URLs stay on the media host`() {
        assertEquals("https://media.retroachievements.org/Badge/250336.png", RaMedia.badge("250336"))
        assertEquals("https://media.retroachievements.org/Badge/250336_lock.png", RaMedia.badgeLocked("250336"))
        assertEquals("https://media.retroachievements.org/Images/067895.png", RaMedia.url("/Images/067895.png"))
        assertTrue(RaMedia.url("//evil.example/../x.png").startsWith("https://media.retroachievements.org/"))
    }

    @Test fun `completion moves past a page whose rows have no game id`() = runBlocking {
        fx.enqueue(ok("""{"Count":2,"Total":3,"Results":[{"ConsoleID":1},{"Title":"no id"}]}"""))
        fx.enqueue(ok(fixture("completion-page-2.json")))

        val games = fx.client.completion(ULID).value()

        assertEquals(listOf(3), games.map { it.gameId })
        val offsets = List(2) { fx.takeRequest().url.queryParameter("o") }
        assertEquals(listOf("0", "2"), offsets)
    }
}
