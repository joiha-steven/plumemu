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

/** Plain JVM, like the settings: the account file needs no Android. */
class ScraperAccountStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val logLines = CopyOnWriteArrayList<String>()
    private val log = SettingsLog { logLines += it }
    private val scopes = mutableListOf<Job>()
    private val file: File by lazy { File(tmp.root, "screenscraper_account.preferences_pb") }

    /** A store on [file]; one at a time per file, so [closeAll] before opening again ("a restart"). */
    private fun open(): ScraperAccountStore {
        val job = Job()
        scopes += job
        return ScraperAccountStore.create({ file }, CoroutineScope(Dispatchers.IO + job), log)
    }

    private suspend fun closeAll() {
        scopes.forEach { it.cancelAndJoin() }
        scopes.clear()
    }

    @After
    fun tearDown() = runBlocking { closeAll() }

    @Test
    fun aFreshInstallIsSignedOut() = runBlocking {
        assertNull(open().account.first())
        assertTrue("no warning for a missing file: $logLines", logLines.isEmpty())
    }

    @Test
    fun theAccountSurvivesARestart() = runBlocking {
        open().signIn("plumtester", CIPHERTEXT)
        closeAll()

        assertEquals(ScraperAccount("plumtester", CIPHERTEXT, isRefused = false), open().account.first())
    }

    @Test
    fun theFileHoldsOnlyTheNameAndTheCiphertextNeverAPlainPassword() = runBlocking {
        // The app encrypts before it stores; the store has no way to take a plain password. This
        // checks what reaches the disk is exactly what it was given, and that a sign out removes it.
        open().signIn("plumtester", CIPHERTEXT)
        closeAll()
        val bytes = String(file.readBytes(), Charsets.ISO_8859_1)
        assertTrue("the ciphertext is stored", CIPHERTEXT in bytes)
        assertTrue("the name is stored", "plumtester" in bytes)

        open().signOut()
        closeAll()
        val after = String(file.readBytes(), Charsets.ISO_8859_1)
        assertFalse("sign out removes the ciphertext from the file", CIPHERTEXT in after)
        assertFalse("sign out removes the name from the file", "plumtester" in after)
    }

    @Test
    fun signOutClearsTheNameTheCiphertextAndTheRefusalAndSurvivesARestart() = runBlocking {
        val store = open()
        store.signIn("plumtester", CIPHERTEXT)
        store.markRefused("plumtester", CIPHERTEXT)
        store.signOut()
        assertNull(store.account.first())
        closeAll()

        assertNull("still signed out after a restart", open().account.first())
    }

    @Test
    fun aRefusalIsKeptUntilTheNextSignIn() = runBlocking {
        val store = open()
        store.signIn("plumtester", CIPHERTEXT)
        store.markRefused("plumtester", CIPHERTEXT)
        assertEquals(true, store.account.first()?.isRefused)
        closeAll()

        val again = open()
        assertEquals("the refusal survives a restart", true, again.account.first()?.isRefused)
        again.signIn("plumtester", "bmV3LWNpcGhlcnRleHQ=")
        assertEquals(ScraperAccount("plumtester", "bmV3LWNpcGhlcnRleHQ=", isRefused = false), again.account.first())
    }

    @Test
    fun aRefusalForAnOlderAccountDoesNotMarkTheNewOne() = runBlocking {
        val store = open()
        store.signIn("second", CIPHERTEXT)
        store.markRefused("first", CIPHERTEXT)
        assertEquals(false, store.account.first()?.isRefused)
        store.markRefused("second", "b2xkLXBhc3N3b3Jk")
        assertEquals("a refusal of the old password under the same name does not mark the new one", false, store.account.first()?.isRefused)

        store.signOut()
        store.markRefused("second", CIPHERTEXT)
        assertNull("a refusal after sign out stores nothing", store.account.first())
    }

    @Test
    fun twoSignInsAtOnceLeaveOneWholeAccount() = runBlocking {
        val store = open()
        listOf("alpha" to "YWxwaGEtY2lwaGVy", "beta" to "YmV0YS1jaXBoZXI=")
            .map { (name, text) -> async(Dispatchers.IO) { store.signIn(name, text) } }
            .awaitAll()
        val account = store.account.first()
        // Each write replaces both values in one edit: never alpha's name with beta's ciphertext.
        assertTrue(
            "$account",
            account == ScraperAccount("alpha", "YWxwaGEtY2lwaGVy") || account == ScraperAccount("beta", "YmV0YS1jaXBoZXI="),
        )
    }

    @Test
    fun aDamagedFileReadsAsSignedOutWithOneLogLine() = runBlocking {
        file.writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03))
        assertNull(open().account.first())
        assertEquals(logLines.toString(), 1, logLines.count { "damaged" in it })
    }

    @Test
    fun toStringNeverShowsTheNameOrTheCiphertext() {
        val text = ScraperAccount("plumtester", CIPHERTEXT).toString()
        assertFalse(text, "plumtester" in text || CIPHERTEXT in text)
    }

    private companion object {
        /** What a cipher returns (Base64); never a real password. */
        const val CIPHERTEXT = "AXRlc3QtY2lwaGVydGV4dC1vbmx5"
    }
}
