package com.plumemu.graph

import com.plumemu.account.RaAccountStatus
import com.plumemu.account.SignInResult
import com.plumemu.coredata.db.RaProgressEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.AchievementsHarness.Companion.CIPHERTEXT
import com.plumemu.graph.AchievementsHarness.Companion.KEY
import com.plumemu.graph.AchievementsHarness.Companion.ULID
import com.plumemu.graph.AchievementsHarness.Companion.USER
import com.plumemu.graph.AchievementsHarness.Companion.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Accounts › RetroAchievements on the graph's side: the sign-in check, the stored key, each failure's line, sign-out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphAchievementsSignInTest {
    @get:org.junit.Rule val timeout = graphTestTimeout()

    @get:Rule
    val temp = TemporaryFolder()

    private val h by lazy { AchievementsHarness(temp.newFolder("ra")) }

    @After
    fun tearDown() = h.close()

    private fun profile(user: String = USER, ulid: String = ULID, points: Int = 1234) = ok("""{"User":"$user","ULID":"$ulid","TotalPoints":$points,"TotalSoftcorePoints":5}""")

    private fun slow(code: Int) = MockResponse.Builder().code(code).addHeader("Retry-After", "3600").body("{}").build()

    private fun accounts() = GraphRaAccounts(h.achievements, CoroutineScope(Dispatchers.Default), Dispatchers.Default) { }

    private fun progressRows(): List<Long> =
        h.db.openHelper.readableDatabase.query("SELECT ra_game_id FROM ra_progress ORDER BY ra_game_id").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }

    @Test
    fun aSignInThatRetroAchievementsAcceptsStoresTheNameAndAnEncryptedKey() = runBlocking {
        h.answers[PROFILE] = { profile() }

        val result = h.achievements.signIn("  $USER ", " $KEY ")

        assertEquals(RaSignInResult.SignedIn(USER, 1234), result)
        val stored = h.store.account.first()!!
        assertEquals(USER, stored.userName)
        assertEquals(ULID, stored.ulid)
        assertEquals(1234, stored.points)
        assertEquals("the stored key is the cipher's text", CIPHERTEXT, stored.keyCiphertext)
        assertNotEquals("the plain key is never stored", KEY, stored.keyCiphertext)
        assertFalse(stored.isKeyRefused)
        assertEquals("one profile call", 1, h.server.requestCount)
        assertEquals(KEY, h.server.takeRequest().url.queryParameter("y"))
        assertTrue("neither the key nor the name reaches a log line", h.logLines.none { KEY in it || USER in it })
        assertEquals(RaAccountStatus.SignedIn(USER, 1234), h.achievements.status.first())
    }

    @Test
    fun aWrongKeyIsItsOwnAnswerAndStoresNothing() = runBlocking {
        h.answers[PROFILE] = { MockResponse.Builder().code(401).body("""{"message":"Unauthenticated."}""").build() }
        assertEquals(RaSignInResult.WrongKey, h.achievements.signIn(USER, KEY))
        assertNull(h.store.account.first())
        assertTrue("the key is not in the log", h.logLines.none { KEY in it })
    }

    @Test
    fun anUnknownUserNameIsItsOwnAnswer() = runBlocking {
        h.answers[PROFILE] = { ok("[]") }
        assertEquals(RaSignInResult.UnknownUser, h.achievements.signIn("nobody", KEY))
        assertNull(h.store.account.first())
    }

    @Test
    fun busyAndRateLimitedEachHaveTheirOwnAnswerAndStoreNothing() = runBlocking {
        h.answers[PROFILE] = { slow(503) }
        assertEquals(RaSignInResult.Busy, h.achievements.signIn(USER, KEY))

        h.answers[PROFILE] = { slow(429) }
        assertEquals(RaSignInResult.RateLimited, h.achievements.signIn(USER, KEY))

        h.answers[PROFILE] = { ok("this is not json") }
        assertEquals("an unreadable answer reads as RetroAchievements being busy", RaSignInResult.Busy, h.achievements.signIn(USER, KEY))
        assertNull(h.store.account.first())
    }

    @Test
    fun noConnectionIsItsOwnAnswerAndStoresNothing() = runBlocking {
        h.server.close()
        assertEquals(RaSignInResult.NoConnection, h.achievements.signIn(USER, KEY))
        assertNull(h.store.account.first())
    }

    @Test
    fun aStoreThatCannotWriteIsNotSavedAndNeverSaysSignedIn() = runBlocking {
        h.answers[PROFILE] = { profile() }
        // The account file's folder cannot take a new file: the write fails after RetroAchievements accepted the key.
        val folder = java.io.File(h.context.filesDir, "datastore")
        folder.mkdirs()
        assertTrue(folder.setWritable(false))
        try {
            assertEquals(RaSignInResult.NotSaved, h.achievements.signIn(USER, KEY))
        } finally {
            folder.setWritable(true)
        }
        assertEquals(SignInResult.NotSaved, signInResultOf(RaSignInResult.NotSaved))
        assertNull("nothing was stored", h.store.account.first())
        assertTrue("the failure is logged without the key", h.logLines.any { "store the account" in it } && h.logLines.none { KEY in it })
    }

    @Test
    fun everyAnswerMapsToItsOwnLineOfTheForm() {
        val lines =
            listOf(
                signInResultOf(RaSignInResult.WrongKey),
                signInResultOf(RaSignInResult.UnknownUser),
                signInResultOf(RaSignInResult.NoConnection),
                signInResultOf(RaSignInResult.Busy),
                signInResultOf(RaSignInResult.RateLimited),
                signInResultOf(RaSignInResult.Missing),
                signInResultOf(RaSignInResult.NotSaved),
            )
        assertEquals("one line each", lines.size, lines.toSet().size)
        assertEquals(SignInResult.RaWrongKey, signInResultOf(RaSignInResult.WrongKey))
        assertEquals(SignInResult.SignedIn(USER), signInResultOf(RaSignInResult.SignedIn(USER, 5)))
        assertEquals(SignInResult.Offline, signInResultOf(RaSignInResult.NoConnection))
    }

    @Test
    fun aBlankNameOrKeyAsksNothing() = runBlocking {
        assertEquals(RaSignInResult.Missing, h.achievements.signIn("  ", KEY))
        assertEquals(RaSignInResult.WrongKey, h.achievements.signIn(USER, "  "))
        assertEquals("no request", 0, h.server.requestCount)
    }

    @Test
    fun signInAfterARefusedKeyClearsTheRefusal() = runBlocking {
        h.signIn()
        h.store.markKeyRefused(ULID, CIPHERTEXT)
        assertTrue((h.achievements.status.first() as RaAccountStatus.SignedIn).isKeyRefused)
        h.answers[PROFILE] = { profile(points = 7) }

        h.achievements.signIn(USER, KEY)

        assertEquals(RaAccountStatus.SignedIn(USER, 7, isKeyRefused = false), h.achievements.status.first())
    }

    @Test
    fun signingInAsAnotherUserDropsTheEarlierUsersProgress() = runBlocking {
        h.signIn()
        h.db.raDao().upsertProgress(listOf(RaProgressEntity(11, 3, 2, 0, null, 1)))
        h.marks.markSuccess(AchievementsHarness.START)
        h.answers[PROFILE] = { profile(user = "Someone", ulid = "00000OTHER") }

        h.achievements.signIn("Someone", KEY)

        assertEquals("the earlier user's progress is gone", emptyList<Long>(), progressRows())
        assertEquals(0L, h.marks.lastSuccess())
    }

    @Test
    fun signingInAgainAsTheSameUserKeepsTheProgress() = runBlocking {
        h.signIn()
        h.db.raDao().upsertProgress(listOf(RaProgressEntity(11, 3, 2, 0, null, 1)))
        h.answers[PROFILE] = { profile() }

        h.achievements.signIn(USER, KEY)

        assertEquals(listOf(11L), progressRows())
    }

    @Test
    fun signOutDeletesTheNameAndTheKey() = runBlocking {
        h.answers[PROFILE] = { profile() }
        h.achievements.signIn(USER, KEY)
        assertEquals(RaAccountStatus.SignedIn(USER, 1234), h.achievements.status.first())

        val done = CompletableDeferred<Boolean>()
        accounts().signOut { done.complete(it) }

        assertTrue(done.await())
        assertNull("no name, no ULID, no key", h.store.account.first())
        assertEquals(RaAccountStatus.SignedOut, h.achievements.status.first())
    }

    @Test
    fun openingSettingsWhileSignedOutBuildsNothingOfRetroAchievements() = runBlocking {
        // What Settings reads for its row: the status and the is-there-an-account check.
        assertEquals(RaAccountStatus.SignedOut, accounts().status.first())
        assertFalse(h.achievements.isSignedIn.first())
        assertEquals("no request", 0, h.server.requestCount)
        assertEquals("no client", 0, h.clientsBuilt.get())
        assertEquals("the key is never decrypted", 0, h.decryptions.get())
        assertEquals("no RetroAchievements database query", 0, h.daoReads.get())
        assertEquals("no library read", 0, h.libraryReads.get())
        assertTrue("nothing hashed", h.hashed.isEmpty())
    }

    private companion object {
        const val PROFILE = "API_GetUserProfile.php"
    }
}
