package com.plumemu.coredata.account

import com.plumemu.coredata.settings.SettingsLog
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Plain JVM, like the ScreenScraper account: the account file needs no Android. */
class RetroAchievementsAccountStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val logLines = CopyOnWriteArrayList<String>()
    private val log = SettingsLog { logLines += it }
    private val scopes = mutableListOf<Job>()
    private val file: File by lazy { File(tmp.root, "retroachievements_account.preferences_pb") }

    /** A store on [file]; one at a time per file, so [closeAll] before opening again ("a restart"). */
    private fun open(): RetroAchievementsAccountStore {
        val job = Job()
        scopes += job
        return RetroAchievementsAccountStore.create({ file }, CoroutineScope(Dispatchers.IO + job), log)
    }

    private suspend fun closeAll() {
        scopes.forEach { it.cancelAndJoin() }
        scopes.clear()
    }

    @After
    fun tearDown() = runBlocking { closeAll() }

    private val account = RetroAchievementsAccount(NAME, ULID, CIPHERTEXT, points = 1234)

    private suspend fun RetroAchievementsAccountStore.signInTester() = signIn(NAME, ULID, CIPHERTEXT, 1234)

    @Test
    fun aFreshInstallIsSignedOutAndBuildingTheStoreReadsNothing() = runBlocking {
        val store = open()
        assertFalse("building the store makes no file", file.exists())
        assertEquals(false, store.isSignedIn.first())
        assertNull(store.account.first())
        assertTrue("no warning for a missing file: $logLines", logLines.isEmpty())
    }

    @Test
    fun theAccountSurvivesARestart() = runBlocking {
        open().signInTester()
        closeAll()

        val again = open()
        assertEquals(account, again.account.first())
        assertEquals(true, again.isSignedIn.first())
    }

    @Test
    fun theFileHoldsTheCiphertextAndLosesEverythingOnSignOut() = runBlocking {
        open().signInTester()
        closeAll()
        val bytes = String(file.readBytes(), Charsets.ISO_8859_1)
        assertTrue(CIPHERTEXT in bytes && ULID in bytes && NAME in bytes)

        val store = open()
        store.markKeyRefused(ULID, CIPHERTEXT)
        store.signOut()
        assertEquals(false, store.isSignedIn.first())
        closeAll()
        val after = String(file.readBytes(), Charsets.ISO_8859_1)
        for (value in listOf(CIPHERTEXT, ULID, NAME, "retroachievements.")) {
            assertFalse("sign out removes $value from the file", value in after)
        }
        assertNull("still signed out after a restart", open().account.first())
    }

    @Test
    fun aRefusalIsKeptUntilTheNextSignInAndOnlyForThatKey() = runBlocking {
        val store = open()
        store.signInTester()
        store.markKeyRefused(ULID, "b2xkLWtleQ==")
        assertEquals("an older key's refusal does not mark the new one", false, store.account.first()?.isKeyRefused)
        store.markKeyRefused("01OTHERULID", CIPHERTEXT)
        assertEquals(false, store.account.first()?.isKeyRefused)

        store.markKeyRefused(ULID, CIPHERTEXT)
        assertEquals(true, store.account.first()?.isKeyRefused)
        assertEquals("a refused account is still signed in (Settings asks again)", true, store.isSignedIn.first())
        closeAll()

        val again = open()
        assertEquals("the refusal survives a restart", true, again.account.first()?.isKeyRefused)
        again.signIn(NAME, ULID, "bmV3LWtleQ==", 1300)
        assertEquals(RetroAchievementsAccount(NAME, ULID, "bmV3LWtleQ==", 1300), again.account.first())
    }

    @Test
    fun updateProfileChangesOnlyTheStoredAccount() = runBlocking {
        val store = open()
        store.signInTester()
        store.updateProfile("01OTHERULID", "Someone", 1)
        assertEquals(account, store.account.first())

        store.updateProfile(ULID, "PlumRenamed", 2000)
        assertEquals(RetroAchievementsAccount("PlumRenamed", ULID, CIPHERTEXT, 2000), store.account.first())

        store.signOut()
        store.updateProfile(ULID, "PlumRenamed", 2000)
        assertNull("an update after sign out stores nothing", store.account.first())
    }

    @Test
    fun twoSignInsAtOnceLeaveOneWholeAccount() = runBlocking {
        val store = open()
        listOf(Triple("alpha", "01ALPHA", "YWxwaGE="), Triple("beta", "01BETA", "YmV0YQ=="))
            .map { (name, ulid, text) -> async(Dispatchers.IO) { store.signIn(name, ulid, text, 1) } }
            .awaitAll()
        val stored = store.account.first()
        assertTrue(
            "$stored",
            stored == RetroAchievementsAccount("alpha", "01ALPHA", "YWxwaGE=", 1) ||
                stored == RetroAchievementsAccount("beta", "01BETA", "YmV0YQ==", 1),
        )
    }

    @Test
    fun aDamagedFileReadsAsSignedOutWithOneLogLine() = runBlocking {
        file.writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03))
        val store = open()
        assertEquals(false, store.isSignedIn.first())
        assertNull(store.account.first())
        assertEquals(logLines.toString(), 1, logLines.count { "damaged" in it })
        assertFalse(logLines.toString(), logLines.any { CIPHERTEXT in it })
    }

    @Test
    fun anIncompleteAccountIsRefusedAndNothingIsStored() = runBlocking {
        val store = open()
        assertTrue(runCatching { store.signIn(NAME, " ", CIPHERTEXT, 0) }.isFailure)
        assertTrue(runCatching { store.signIn(NAME, ULID, "", 0) }.isFailure)
        assertEquals(false, store.isSignedIn.first())
    }

    @Test
    fun toStringNeverShowsTheNameTheUlidOrTheCiphertext() {
        val text = account.toString()
        assertFalse(text, NAME in text || ULID in text || CIPHERTEXT in text)
    }

    private companion object {
        const val NAME = "plumtester"
        const val ULID = "00003EMFWR7XB8SDPEHB3K56ZQ"

        /** What a cipher returns (Base64); never a real key. */
        const val CIPHERTEXT = "AXRlc3QtcmEta2V5LWNpcGhlcnRleHQ="
    }
}
