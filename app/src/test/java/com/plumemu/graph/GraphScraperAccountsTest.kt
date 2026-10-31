package com.plumemu.graph

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.account.AccountStatus
import com.plumemu.account.SignInResult
import com.plumemu.coredata.account.ScraperAccountStore
import com.plumemu.coredata.backup.BackupAccount
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.PauseReason
import com.plumemu.scraper.PauseStore
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.UnavailableReason
import com.plumemu.scraper.UserCredentials
import com.plumemu.scraper.account.PasswordCipher
import java.io.File
import java.security.GeneralSecurityException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app's one holder of the ScreenScraper account, with the real account file and fakes for
 * ScreenScraper (a [Scraper] answering a set [AccountCheck]) and the Keystore cipher. No network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphScraperAccountsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storeJob = Job()
    private val scopeJob = SupervisorJob()
    private val logLines = CopyOnWriteArrayList<String>()
    private val store: ScraperAccountStore by lazy { ScraperAccountStore.create(context, CoroutineScope(Dispatchers.IO + storeJob)) }
    private val cipher = FakeCipher()
    private val made = CopyOnWriteArrayList<FakeScraper>()
    private var check: AccountCheck = AccountCheck.Ok(maxThreads = 1, requestsToday = 0, maxRequestsPerDay = 20_000)
    private var pause: ScrapeResult.Paused? = null
    private val now = 1_000_000L

    private fun accounts(isAvailable: Boolean = true, onSignedIn: () -> Unit = {}) =
        GraphScraperAccounts(
            isAvailable = isAvailable,
            store = { store },
            cipher = { cipher },
            makeScraper = { user -> FakeScraper(user, check).also { made += it } },
            pauseStore = { FakePauseStore(pause) },
            scope = CoroutineScope(Dispatchers.Default + scopeJob),
            io = Dispatchers.IO,
            main = Dispatchers.Unconfined,
            clock = { now },
            log = { logLines += it },
            onSignedIn = onSignedIn,
        )

    @After
    fun tearDown() = runBlocking {
        scopeJob.cancelAndJoin()
        storeJob.cancelAndJoin()
    }

    /** Where DataStore keeps a Preferences file (`preferencesDataStoreFile`). */
    private val accountFile: File get() = File(context.filesDir, "datastore/${ScraperAccountStore.FILE_NAME}.preferences_pb")

    @Test
    fun anAcceptedAccountIsStoredAsCiphertextOnlyAndShowsAsSignedIn() = runBlocking {
        val accounts = accounts()
        assertEquals(SignInResult.SignedIn("plumtester"), accounts.check("  plumtester ", PASSWORD))

        val stored = store.account.first()
        assertNotNull(stored)
        assertEquals("plumtester", stored?.username)
        assertEquals("what the cipher made is what is stored", cipher.lastCiphertext, stored?.passwordCiphertext)
        assertFalse("the plain password never reaches the store", stored?.passwordCiphertext.orEmpty().contains(PASSWORD))
        assertFalse("nor the file", String(accountFile.readBytes(), Charsets.ISO_8859_1).contains(PASSWORD))
        assertEquals(AccountStatus.SignedIn("plumtester"), accounts.status.first())
        assertEquals("one scraper asked, with the person's account", "plumtester", made.single().user.username)
    }

    /**
     * The main screen's box-art switch reads the account file only: a pause read that never ends
     * does not hold the first content back.
     */
    @Test
    fun isSignedInNeverWaitsForThePauseRead() = runBlocking {
        accounts().check("plumtester", PASSWORD)
        val stuck =
            GraphScraperAccounts(
                isAvailable = true,
                store = { store },
                cipher = { cipher },
                makeScraper = { user -> FakeScraper(user, check) },
                pauseStore = { error("the pause must not be read for the home screen's switch") },
                scope = CoroutineScope(Dispatchers.Default + scopeJob),
                io = Dispatchers.IO,
                main = Dispatchers.Unconfined,
                clock = { now },
                log = { logLines += it },
            )
        assertEquals(true, withTimeout(5_000) { stuck.isSignedIn.first() })
        assertEquals("this build has no credentials: never signed in", false, accounts(isAvailable = false).isSignedIn.first())
        assertTrue("nothing failed on the way: $logLines", logLines.none { it.contains("pause") })
    }

    @Test
    fun anAccountFileThatCannotBeReadCountsAsSignedOutAndIsLogged() = runBlocking {
        val broken =
            GraphScraperAccounts(
                isAvailable = true,
                store = { throw ClassCastException("not an account file") },
                cipher = { cipher },
                makeScraper = { user -> FakeScraper(user, check) },
                pauseStore = { error("not read") },
                scope = CoroutineScope(Dispatchers.Default + scopeJob),
                io = Dispatchers.IO,
                main = Dispatchers.Unconfined,
                clock = { now },
                log = { logLines += it },
            )
        // AppGraph holds this in a StateFlow the first home content waits for: it must get a value.
        assertEquals(false, withTimeout(5_000) { broken.isSignedIn.first() })
        assertTrue("the log names the failure: $logLines", logLines.any { "isSignedIn" in it && "ClassCastException" in it })
    }

    @Test
    fun everyRefusalOrFailureStoresNothing() = runBlocking {
        val cases =
            listOf(
                AccountCheck.Refused(UnavailableReason.BadUserCredentials) to SignInResult.WrongCredentials,
                AccountCheck.Refused(UnavailableReason.BadDeveloperCredentials) to SignInResult.AppRefused,
                AccountCheck.Offline to SignInResult.Offline,
                AccountCheck.ServerClosed to SignInResult.ServerClosed,
                AccountCheck.DailyQuota(now + 3_600_000L) to SignInResult.DailyLimit(now + 3_600_000L),
                AccountCheck.ServerError(500) to SignInResult.ServerError(500),
            )
        val accounts = accounts()
        for ((answer, expected) in cases) {
            check = answer
            assertEquals("$answer", expected, accounts.check("plumtester", PASSWORD))
            assertNull("$answer: nothing stored", store.account.first())
        }
        assertEquals("nothing encrypted for a refused account", 0, cipher.encryptions)
        assertEquals(AccountStatus.SignedOut, accounts.status.first())
    }

    @Test
    fun anEmptyNameOrPasswordAsksNothing() = runBlocking {
        val accounts = accounts()
        assertEquals(SignInResult.Missing, accounts.check("   ", PASSWORD))
        assertEquals(SignInResult.Missing, accounts.check("plumtester", ""))
        assertTrue(made.isEmpty())
    }

    @Test
    fun aBuildWithoutDeveloperCredentialsNeverAsksNorReads() = runBlocking {
        val accounts = accounts(isAvailable = false)
        assertEquals(AccountStatus.NotInThisBuild, accounts.status.first())
        assertEquals(SignInResult.NotAvailable, accounts.check("plumtester", PASSWORD))
        assertNull(accounts.scraper())
        assertTrue("no scraper made", made.isEmpty())
        assertFalse("the account file is never opened", accountFile.exists())
    }

    @Test
    fun aFailedEncryptionIsSaidAndStoresNothing() = runBlocking {
        cipher.fails = true
        val accounts = accounts()
        assertEquals(SignInResult.NotSaved, accounts.check("plumtester", PASSWORD))
        assertNull(store.account.first())
        assertTrue(logLines.toString(), logLines.any { "GeneralSecurityException" in it })
    }

    @Test
    fun theScraperIsMadeOncePerAccountAndANewPasswordMakesANewOne() = runBlocking {
        val accounts = accounts()
        accounts.check("plumtester", PASSWORD)
        val first = accounts.scraper()
        assertSame("the scraper that checked the account is kept", made.single(), first)
        assertSame(first, accounts.scraper())

        accounts.check("plumtester", "another-password")
        val second = accounts.scraper()
        assertNotSame(first, second)
        assertEquals("another-password", (second as FakeScraper).user.password)
    }

    @Test
    fun afterARestartThePasswordIsDecryptedOnDemand() = runBlocking {
        accounts().check("plumtester", PASSWORD)
        made.clear()
        // A new graph in the same process (as after a restart): nothing held, the store has the account.
        val fresh = accounts()
        val scraper = fresh.scraper() as FakeScraper
        assertEquals(PASSWORD, scraper.user.password)
        assertEquals(1, cipher.decryptions)
    }

    @Test
    fun aBackupGetsTheNameAndThePlainPasswordAndNothingWhenSignedOut() = runBlocking {
        val accounts = accounts()
        assertNull(accounts.forBackup())
        accounts.check("plumtester", PASSWORD)
        assertEquals(BackupAccount("plumtester", PASSWORD, isRefused = false), accounts.forBackup())
        cipher.forgetKeys()
        assertEquals("a lost key: the name without a password", BackupAccount("plumtester", null, isRefused = false), accounts.forBackup())
        assertNull("a build without box art has no account", accounts(isAvailable = false).forBackup())
    }

    /**
     * Restore…: the file's plain password is encrypted with this
     * device's key through the store's own path, reads back equal, and is never logged or stored plain.
     * Undo's way back stores the device's earlier account exactly.
     */
    @Test
    fun aRestoredAccountIsEncryptedHereReadsBackEqualAndItsUndoIsExact() = runBlocking {
        val accounts = accounts()
        accounts.check("deviceuser", "device-pass")
        val before = requireNotNull(accounts.storedForRestore())

        val encrypted = accounts.encryptForRestore(BackupAccount("plumtester", PASSWORD, isRefused = false))
        assertEquals("what this device's cipher made", cipher.lastCiphertext, encrypted.passwordCiphertext)
        assertFalse(encrypted.passwordCiphertext.contains(PASSWORD))
        accounts.putForRestore(encrypted)
        assertEquals(BackupAccount("plumtester", PASSWORD, isRefused = false), accounts.forBackup())
        assertEquals(AccountStatus.SignedIn("plumtester"), accounts.status.first())
        assertFalse("the file never holds it plain", String(accountFile.readBytes(), Charsets.ISO_8859_1).contains(PASSWORD))
        assertFalse("no log line names it", logLines.any { it.contains(PASSWORD) || it.contains("plumtester") })

        accounts.putForRestore(accounts.encryptForRestore(BackupAccount("plumtester", PASSWORD, isRefused = true)))
        assertEquals("a refused account stays refused", AccountStatus.SignedIn("plumtester", isRefused = true), accounts.status.first())

        accounts.putForRestore(before)
        assertEquals("Undo: the very ciphertext", before, accounts.storedForRestore())
        accounts.putForRestore(null)
        assertNull(accounts.storedForRestore())
        assertNull("a build without box art keeps nothing", accounts(isAvailable = false).storedForRestore())
    }

    @Test
    fun aPasswordThatCannotBeDecryptedOrWasRefusedGivesNoScraper() = runBlocking {
        val accounts = accounts()
        accounts.check("plumtester", PASSWORD)
        accounts.markRefused(requireNotNull(accounts.scraper()))
        assertNull("refused: nothing is scraped", accounts.scraper())
        assertEquals(AccountStatus.SignedIn("plumtester", isRefused = true), accounts.status.first())

        accounts.check("plumtester", PASSWORD)
        assertEquals("signing in again clears the refusal", AccountStatus.SignedIn("plumtester"), accounts.status.first())
        cipher.forgetKeys()
        val fresh = accounts()
        assertNull("a key lost with a restored backup: no scraper", fresh.scraper())
        assertEquals("and Settings says sign in again", AccountStatus.SignedIn("plumtester", isRefused = true), fresh.status.first())
    }

    @Test
    fun aRefusalOfTheOldPasswordDoesNotMarkTheNewOne() = runBlocking {
        val accounts = accounts()
        accounts.check("plumtester", PASSWORD)
        val old = requireNotNull(accounts.scraper())
        accounts.check("plumtester", "new-password")
        accounts.markRefused(old)
        assertEquals(AccountStatus.SignedIn("plumtester"), accounts.status.first())
        assertEquals("new-password", (accounts.scraper() as FakeScraper).user.password)
    }

    @Test
    fun signOutForgetsTheAccountAndItsScraper() = runBlocking {
        val accounts = accounts()
        accounts.check("plumtester", PASSWORD)
        assertTrue(accounts.signOutNow())
        assertNull(store.account.first())
        assertNull(accounts.scraper())
        assertEquals(AccountStatus.SignedOut, accounts.status.first())
        assertFalse(String(accountFile.readBytes(), Charsets.ISO_8859_1).contains(cipher.lastCiphertext.orEmpty()))
    }

    @Test
    fun aSavedDailyPauseShowsUntilItEnds() = runBlocking {
        accounts().check("plumtester", PASSWORD)
        pause = ScrapeResult.Paused(now + 60_000L, PauseReason.DailyQuota)
        assertEquals(AccountStatus.SignedIn("plumtester", dailyLimitUntilMillis = now + 60_000L), accounts().status.first())
        pause = ScrapeResult.Paused(now - 1L, PauseReason.DailyQuota)
        assertEquals("an ended pause says nothing", AccountStatus.SignedIn("plumtester"), accounts().status.first())
        pause = ScrapeResult.Paused(now + 60_000L, PauseReason.ServerClosed)
        assertEquals("a closed server is not the daily limit", AccountStatus.SignedIn("plumtester"), accounts().status.first())
    }

    @Test
    fun twoSignInsAtOnceAskOneAtATime() = runBlocking {
        val inFlight = AtomicInteger()
        val most = AtomicInteger()
        val accounts =
            GraphScraperAccounts(
                isAvailable = true,
                store = { store },
                cipher = { cipher },
                makeScraper = { user -> FakeScraper(user, check, inFlight, most) },
                pauseStore = { null },
                scope = CoroutineScope(Dispatchers.Default + scopeJob),
                io = Dispatchers.IO,
                main = Dispatchers.Unconfined,
                log = { logLines += it },
            )
        listOf("alpha", "beta").map { name -> async(Dispatchers.Default) { accounts.check(name, PASSWORD) } }.awaitAll()
        assertEquals("never two checks at once", 1, most.get())
        assertTrue(store.account.first()?.username in setOf("alpha", "beta"))
    }

    @Test
    fun theCallbackSignInFinishesAndAnswersOnce() = runBlocking {
        val answer = CompletableDeferred<SignInResult>()
        accounts().signIn("plumtester", PASSWORD) { answer.complete(it) }
        assertEquals(SignInResult.SignedIn("plumtester"), withTimeout(5_000) { answer.await() })
        assertNotNull(store.account.first())
    }

    @Test
    fun noLogLineCarriesTheNameOrThePassword() = runBlocking {
        cipher.fails = true
        val accounts = accounts()
        accounts.check("plumtester", PASSWORD)
        cipher.fails = false
        accounts.check("plumtester", PASSWORD)
        accounts.signOutNow()
        assertTrue(logLines.toString(), logLines.none { "plumtester" in it || PASSWORD in it })
    }

    @Test
    fun onlyAStoredSignInTellsTheGraphAndMakesTheAccountUsable() = runBlocking {
        var told = 0
        val accounts = accounts(onSignedIn = { told++ })
        assertFalse(accounts.hasUsableAccount())

        check = AccountCheck.Refused(UnavailableReason.BadUserCredentials)
        accounts.check("plumtester", PASSWORD)
        cipher.fails = true
        check = AccountCheck.Ok(maxThreads = 1, requestsToday = 0, maxRequestsPerDay = 20_000)
        accounts.check("plumtester", PASSWORD)
        assertEquals("a refusal or an account not saved asks for no run", 0, told)
        assertFalse(accounts.hasUsableAccount())

        cipher.fails = false
        accounts.check("plumtester", PASSWORD)
        assertEquals(1, told)
        assertTrue(accounts.hasUsableAccount())

        accounts.markRefused(made.last())
        assertFalse("a refused account is not usable", accounts.hasUsableAccount())
        assertFalse(accounts(isAvailable = false).hasUsableAccount())
    }

    private class FakeScraper(
        val user: UserCredentials,
        private val answer: AccountCheck,
        private val inFlight: AtomicInteger = AtomicInteger(),
        private val most: AtomicInteger = AtomicInteger(),
    ) : Scraper {
        override val available: Boolean = true

        override suspend fun scrape(request: ScrapeRequest): ScrapeResult = error("not used")

        override suspend fun download(media: MediaRef, target: File, maxWidth: Int?, maxHeight: Int?): MediaDownload = error("not used")

        override suspend fun checkAccount(): AccountCheck {
            most.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            delay(50)
            inFlight.decrementAndGet()
            return answer
        }
    }

    /** Stands in for the Keystore: the ciphertext is a token, the password lives only in its table. */
    private class FakeCipher : PasswordCipher {
        private val table = HashMap<String, String>()
        var fails = false
        var encryptions = 0
        var decryptions = 0
        var lastCiphertext: String? = null

        override fun encrypt(plain: String): String {
            if (fails) throw GeneralSecurityException("key store unreadable")
            encryptions++
            return "dG9rZW4t${table.size}".also {
                table[it] = plain
                lastCiphertext = it
            }
        }

        override fun decrypt(stored: String): String? {
            decryptions++
            return table[stored]
        }

        fun forgetKeys() = table.clear()
    }

    private class FakePauseStore(private val saved: ScrapeResult.Paused?) : PauseStore {
        override fun load(): ScrapeResult.Paused? = saved

        override fun save(pause: ScrapeResult.Paused) = Unit

        override fun clear() = Unit
    }

    private companion object {
        const val PASSWORD = "correct-horse-battery"
    }
}
