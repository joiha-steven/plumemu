package com.plumemu.graph

import com.plumemu.achievements.hash.RaCannotHashReason
import com.plumemu.achievements.hash.RaHashResult
import com.plumemu.coredata.db.RaMatchEntity
import com.plumemu.coredata.db.RaProgressEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.AchievementsHarness.Companion.GAME_LIST
import com.plumemu.graph.AchievementsHarness.Companion.GAME_PROGRESS
import com.plumemu.graph.AchievementsHarness.Companion.KEY
import com.plumemu.graph.AchievementsHarness.Companion.ULID
import com.plumemu.graph.AchievementsHarness.Companion.USER
import com.plumemu.graph.AchievementsHarness.Companion.gameList
import com.plumemu.graph.AchievementsHarness.Companion.ok
import com.plumemu.graph.AchievementsHarness.Companion.progress
import com.plumemu.graph.AchievementsHarness.Companion.status
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** RetroAchievements in the app: nothing while signed out, Info's state, the after-game check, sign-out and restore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphAchievementsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val h by lazy { AchievementsHarness(temp.newFolder("ra")) }

    @After
    fun tearDown() = h.close()

    @Test
    fun aSignedOutAppBuildsNothingOfRetroAchievementsAndSendsNoRequest() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        assertFalse(h.achievements.isSignedIn.first())
        assertEquals(AchievementsState.NotSignedIn, h.achievements.progressFor(zork))
        assertEquals(listOf(AchievementsState.Loading, AchievementsState.NotSignedIn), h.achievements.progress(zork).toList())
        h.achievements.afterGame(zork)
        h.achievements.onFront().join()
        assertFalse(h.achievements.refreshIfDue())
        assertEquals("no request", 0, h.server.requestCount)
        assertEquals("no client", 0, h.clientsBuilt.get())
        assertEquals("the key is never decrypted", 0, h.decryptions.get())
        assertEquals("no RetroAchievements database query", 0, h.daoReads.get())
        assertEquals("no library read", 0, h.libraryReads.get())
        assertTrue("nothing hashed", h.hashed.isEmpty())
        assertFalse("no game list saved", java.io.File(temp.root, "ra/lists").exists())
    }

    @Test
    fun infoMatchesTheFileOnceStoresItAndShowsTheProgress() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.signIn()
        h.answers[GAME_LIST] = { ok(gameList(raId = 11)) }
        h.answers[GAME_PROGRESS] = { ok(progress(raId = 11, awarded = 2, hardcore = 1)) }

        val ready = h.achievements.progressFor(zork) as AchievementsState.Ready
        assertEquals("Zork RA", ready.gameTitle)
        assertEquals(3, ready.total)
        assertEquals(2, ready.awardedSoftcore)
        assertEquals(1, ready.awardedHardcore)
        assertEquals(listOf("A1", "A2", "A3"), ready.achievements.map { it.title })
        val first = ready.achievements.first()
        assertEquals("https://media.retroachievements.org/Badge/101.png", first.badgeUrl)
        assertEquals("https://media.retroachievements.org/Badge/101_lock.png", first.lockedBadgeUrl)
        assertEquals("2026-01-01 10:00:00", first.earnedAt)
        assertFalse(ready.achievements.last().isEarned)

        val match = h.db.raDao().match(zork)!!
        assertEquals(RaMatchEntity.MATCHED, match.status)
        assertEquals(11L, match.raGameId)
        assertEquals(2, h.db.raDao().observeProgress(zork).first()?.awardedSoftcore)

        // The second time the stored hash is used: the file is not read again.
        h.achievements.progressFor(zork)
        assertEquals(listOf("zork.sfc"), h.hashed.toList())
        assertEquals("one client for the account", 1, h.clientsBuilt.get())
    }

    @Test
    fun aFileThatCannotBeHashedIsStoredAndAnUnreadableOneIsNot() = runBlocking {
        val zork = h.addGame("zork.7z", "Zork")
        val mario = h.addGame("mario.sfc", "Mario")
        h.signIn()
        h.hashes["zork.7z"] = RaHashResult.CannotHash(RaCannotHashReason.Empty)
        h.hashes["mario.sfc"] = RaHashResult.Unreadable(IOException("card removed"))

        assertEquals(AchievementsState.CannotHash(RaCannotHashReason.Empty), h.achievements.progressFor(zork))
        assertEquals(RaMatchEntity.CANNOT_HASH, h.db.raDao().match(zork)?.status)
        assertEquals(AchievementsState.CannotHash(RaCannotHashReason.Empty), h.achievements.progressFor(zork))
        assertEquals("a stored cannot-hash is not hashed again", 1, h.hashed.count { it == "zork.7z" })

        assertEquals(AchievementsState.Error(AchievementsError.GameUnavailable), h.achievements.progressFor(mario))
        assertNull("an unreadable file stores nothing", h.db.raDao().match(mario))
        assertEquals("a file that cannot be hashed asks nothing", 0, h.server.requestCount)
    }

    @Test
    fun aHashRetroAchievementsDoesNotListIsNoSetAndAFailedListStoresNothing() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        val doom = h.addGame("doom.sfc", "Doom")
        h.signIn()
        h.answers[GAME_LIST] = { ok(gameList(md5 = "ffffffffffffffffffffffffffffffff")) }
        assertEquals(AchievementsState.NoSet, h.achievements.progressFor(zork))
        assertEquals(RaMatchEntity.NO_SET, h.db.raDao().match(zork)?.status)

        java.io.File(temp.root, "ra/lists").deleteRecursively()
        h.answers.remove(GAME_LIST)
        h.answers["API_GetGameList.php"] = { status(404) }
        assertEquals(AchievementsState.Error(AchievementsError.BadAnswer), h.achievements.progressFor(doom))
        assertNull(h.db.raDao().match(doom))
    }

    @Test
    fun aSystemWithoutRetroAchievementsReadsNoFile() = runBlocking {
        val app = h.addGame("app.apk", "Some app", systemId = "android")
        h.signIn()
        assertEquals(AchievementsState.NotSupported, h.achievements.progressFor(app))
        assertTrue(h.hashed.isEmpty())
        assertEquals(0, h.server.requestCount)
    }

    @Test
    fun aRefusedKeyIsMarkedAndAskedNoMore() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.signIn()
        h.answers[GAME_LIST] = { ok(gameList()) }
        h.answers[GAME_PROGRESS] = { status(401) }
        assertEquals(AchievementsState.Error(AchievementsError.WrongKey), h.achievements.progressFor(zork))
        assertTrue(h.store.account.first()!!.isKeyRefused)
        val sent = h.server.requestCount
        assertEquals(AchievementsState.Error(AchievementsError.WrongKey), h.achievements.progressFor(zork))
        assertEquals("nothing more is asked", sent, h.server.requestCount)
    }

    @Test
    fun afterAGameOnlyARiseIsAnnouncedAndTheRowFollows() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.signIn()
        h.unmetered = false // no daily refresh in this test
        h.answers[GAME_LIST] = { ok(gameList()) }
        h.answers[GAME_PROGRESS] = { ok(progress(awarded = 1)) }
        h.achievements.progressFor(zork)

        val events = mutableListOf<NewAchievements>()
        val collecting = async(start = CoroutineStart.UNDISPATCHED) { h.achievements.newAchievements.collect { events += it } }
        h.achievements.afterGame(zork)
        assertTrue("no rise, no line", events.isEmpty())

        h.answers[GAME_PROGRESS] = { ok(progress(awarded = 3)) }
        h.achievements.afterGame(zork)
        withTimeout(2_000) { while (events.isEmpty()) kotlinx.coroutines.yield() }
        assertEquals(listOf(NewAchievements(zork, 2, "Zork")), events)
        assertEquals(3, h.db.raDao().observeProgress(zork).first()?.awardedSoftcore)

        h.achievements.afterGame(zork)
        assertEquals("the same count again is no rise", 1, events.size)
        collecting.cancel()
    }

    @Test
    fun afterAnUnmatchedGameNothingIsAskedOrHashed() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.signIn()
        h.unmetered = false
        h.achievements.afterGame(zork)
        assertEquals(0, h.server.requestCount)
        assertTrue(h.hashed.isEmpty())
    }

    @Test
    fun signOutDeletesTheAccountAndTheProgressAndDropsTheObject() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.signIn()
        h.answers[GAME_LIST] = { ok(gameList()) }
        h.answers[GAME_PROGRESS] = { ok(progress(awarded = 2)) }
        h.achievements.progressFor(zork)
        h.marks.markSuccess(AchievementsHarness.START)

        assertTrue(h.achievements.signOut())
        assertNull(h.store.account.first())
        assertNull(h.db.raDao().observeProgress(zork).first())
        assertEquals("the match is not personal and stays", 11L, h.db.raDao().match(zork)?.raGameId)
        assertEquals(0L, h.marks.lastSuccess())
        val sent = h.server.requestCount
        assertEquals(AchievementsState.NotSignedIn, h.achievements.progressFor(zork))
        assertEquals(sent, h.server.requestCount)

        // A new sign-in builds a new object.
        h.signIn()
        h.achievements.progressFor(zork)
        assertEquals(2, h.clientsBuilt.get())
    }

    @Test
    fun aRestoredLibraryLosesItsProgressRows() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.db.raDao().saveMatch(RaMatchEntity(zork, RaMatchEntity.MATCHED, 11, AchievementsHarness.MD5, null, "k", 4, 1, 1))
        h.db.raDao().upsertProgress(listOf(RaProgressEntity(11, 3, 3, 3, "mastered", 1)))
        h.marks.markSuccess(AchievementsHarness.START)
        h.achievements.afterRestore()
        assertNull(h.db.raDao().observeProgress(zork).first())
        assertEquals(0L, h.marks.lastSuccess())
        assertEquals(0, h.server.requestCount)
    }

    @Test
    fun noLogLineHoldsTheKeyTheUserNameOrTheUlid() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.signIn()
        h.answers[GAME_LIST] = { ok(gameList()) }
        h.answers[GAME_PROGRESS] = { status(401) }
        h.achievements.progressFor(zork)
        h.signIn() // stored again: refused flag cleared
        h.answers[GAME_PROGRESS] = { status(404) }
        h.achievements.progressFor(zork)
        h.answers[AchievementsHarness.COMPLETION] = { status(401) }
        h.signIn()
        h.achievements.refreshIfDue()
        assertTrue("something was logged", h.logLines.isNotEmpty())
        for (line in h.logLines) {
            for (secret in listOf(KEY, USER, ULID)) assertFalse("log line holds a secret: $line", line.contains(secret))
        }
    }

    @Test
    fun callsThatEndAfterASignOutWriteNoProgressAndNoMatch() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        val doom = h.addGame("doom.sfc", "Doom")
        h.db.raDao().saveMatch(RaMatchEntity(doom, RaMatchEntity.MATCHED, 12, AchievementsHarness.MD5, null, "k", 4, 1, 1))
        h.signIn()
        h.answers[GAME_LIST] = { ok(gameList()) }
        h.answers[GAME_PROGRESS] = { ok(progress(awarded = 2)) }
        h.answers[AchievementsHarness.COMPLETION] = { ok(AchievementsHarness.completion(AchievementsHarness.completionRow(11, "Zork", "mastered"))) }
        listOf(GAME_LIST, GAME_PROGRESS, AchievementsHarness.COMPLETION).forEach(h::hold)

        val refresh = async(Dispatchers.Default) { h.achievements.refreshIfDue() }
        h.awaitArrived(AchievementsHarness.COMPLETION)
        val info = async(Dispatchers.Default) { h.achievements.progressFor(zork) }
        h.awaitArrived(GAME_LIST)
        val back = async(Dispatchers.Default) { h.achievements.afterGame(doom) }
        h.awaitArrived(GAME_PROGRESS)

        assertTrue(h.achievements.signOut())
        listOf(GAME_LIST, GAME_PROGRESS, AchievementsHarness.COMPLETION).forEach(h::release)
        refresh.await()
        info.await()
        back.await()

        assertEquals("no progress row", 0, h.db.openHelper.readableDatabase.query("SELECT * FROM ra_progress").use { it.count })
        assertNull("no match row for the game being matched", h.db.raDao().match(zork))
        assertEquals("only the match stored before", 1, h.db.openHelper.readableDatabase.query("SELECT * FROM ra_match").use { it.count })
        assertEquals(0L, h.marks.lastSuccess() + h.marks.lastFailure())
    }
}
