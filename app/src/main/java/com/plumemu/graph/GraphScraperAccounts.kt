package com.plumemu.graph

import com.plumemu.account.AccountStatus
import com.plumemu.account.ScraperAccounts
import com.plumemu.account.SignInResult
import com.plumemu.account.signInResultOf
import com.plumemu.coredata.account.ScraperAccount
import com.plumemu.coredata.backup.BackupAccount
import com.plumemu.coredata.account.ScraperAccountStore
import com.plumemu.scraper.PauseReason
import com.plumemu.scraper.PauseStore
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.UserCredentials
import com.plumemu.scraper.account.PasswordCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one place the app holds the person's ScreenScraper account: it reads
 * the stored account, decrypts the password only when a [Scraper] is made, checks a new account
 * with [Scraper.checkAccount] and stores it encrypted once ScreenScraper accepts it.
 *
 * - A build without developer credentials ([isAvailable] false) reads nothing and asks nothing.
 * - One [Scraper] per stored name and ciphertext ([scraper]); a new password means a new one, and a refused
 *   scraper stays stopped.
 * - Sign-ins and sign-outs run one at a time in [scope], so a screen that goes away does not cut
 *   a sign-in short, and two at once never interleave their writes.
 * - Nothing here logs a name or a password; failures are logged by exception class.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class GraphScraperAccounts(
    override val isAvailable: Boolean,
    private val store: () -> ScraperAccountStore,
    private val cipher: () -> PasswordCipher,
    private val makeScraper: (UserCredentials) -> Scraper,
    private val pauseStore: () -> PauseStore?,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val main: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit,
    /** After ScreenScraper accepted and the account is stored: the graph asks for a box-art run. */
    private val onSignedIn: () -> Unit = {},
    /** After a sign out: the graph stops a run still going or waiting with the old account. */
    private val onSignedOut: () -> Unit = {},
) : ScraperAccounts, RestoreAccounts {
    private class Held(val username: String, val ciphertext: String, val scraper: Scraper)

    private val lock = Mutex()
    private var held: Held? = null

    /** Bumped after a sign-in: the saved pause may have changed (a sign-in can hit the daily limit). */
    private val pauseReads = MutableStateFlow(0)

    /**
     * Whether an account is stored (box art set up): the account file alone, never the
     * saved pause, so the main screen's first content does not wait for that read.
     * An account file that cannot be read (any failure, not only I/O) counts as signed out: the
     * main screen shows the library without box art instead of waiting for ever, and the log says why.
     */
    val isSignedIn: Flow<Boolean> =
        if (!isAvailable) {
            flowOf(false)
        } else {
            flow { emitAll(store().account) }
                .map { it != null }
                .catch { failure ->
                    log("isSignedIn: the account could not be read (${failure.javaClass.simpleName}); box art stays off")
                    emit(false)
                }.distinctUntilChanged()
        }

    override val status: Flow<AccountStatus> =
        if (!isAvailable) {
            flowOf(AccountStatus.NotInThisBuild)
        } else {
            combine(flow { emitAll(store().account) }, pauseReads) { account, _ -> account }
                .mapLatest { account -> statusOf(account) }
        }

    override fun signIn(
        username: String,
        password: String,
        onDone: (SignInResult) -> Unit,
    ) {
        scope.launch {
            val result =
                try {
                    check(username, password)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("Sign-in failed (${e.javaClass.simpleName})")
                    SignInResult.NotSaved
                }
            withContext(main) { onDone(result) }
        }
    }

    override fun signOut(onDone: (Boolean) -> Unit) {
        scope.launch {
            val done = signOutNow()
            withContext(main) { onDone(done) }
        }
    }

    /** Asks ScreenScraper; when it accepts, stores the name and the encrypted password and keeps the scraper. */
    suspend fun check(
        username: String,
        password: String,
    ): SignInResult {
        if (!isAvailable) return SignInResult.NotAvailable
        val name = username.trim()
        if (name.isEmpty() || password.isEmpty()) return SignInResult.Missing
        val result = lock.withLock {
            val scraper = makeScraper(UserCredentials(name, password))
            val result = signInResultOf(scraper.checkAccount(), name)
            pauseReads.value++
            if (result !is SignInResult.SignedIn) return@withLock result
            val ciphertext = guarded("encrypt the password") { withContext(io) { cipher().encrypt(password) } } ?: return@withLock SignInResult.NotSaved
            guarded("store the account") { store().signIn(name, ciphertext) } ?: return@withLock SignInResult.NotSaved
            held = Held(name, ciphertext, scraper)
            result
        }
        if (result is SignInResult.SignedIn) onSignedIn()
        return result
    }

    /** Forgets the account and its scraper. The Keystore key stays: it holds no password, and the next sign-in reuses it. */
    suspend fun signOutNow(): Boolean {
        val done =
            lock.withLock {
                held = null
                guarded("sign out") { store().signOut() } != null
            }
        // Even when the file could not be written: the person asked that nothing more be asked.
        guarded("stop the box art run") { onSignedOut() }
        return done
    }

    /**
     * The scraper for the stored account, made the first time for each name and password; null
     * when signed out, when ScreenScraper refused the stored password, or when it cannot be
     * decrypted (a restored backup on another device: the person signs in again).
     */
    suspend fun scraper(): Scraper? {
        if (!isAvailable) return null
        return lock.withLock {
            val account = guarded("read the account") { store().account.first() } ?: return@withLock null
            if (account.isRefused) return@withLock null
            held?.takeIf { it.username == account.username && it.ciphertext == account.passwordCiphertext }?.let { return@withLock it.scraper }
            val password = withContext(io) { cipher().decrypt(account.passwordCiphertext) }
            if (password == null) {
                // The key is gone (a backup restored on another device, a wiped key store): say so
                // in Settings ("sign in again") instead of a signed-in row that never scrapes.
                guarded("mark the account for a new sign-in") { store().markRefused(account.username, account.passwordCiphertext) }
                return@withLock null
            }
            makeScraper(UserCredentials(account.username, password)).also { held = Held(account.username, account.passwordCiphertext, it) }
        }
    }

    /**
     * The account for Settings › Advanced › Back up…: the name and the password
     * decrypted, so another device can restore it (its key store cannot read this one's ciphertext).
     * Null when signed out or in a build without box art; the password null when it cannot be
     * decrypted here (the restore then asks for a sign-in). A failed read of the account file throws:
     * the backup says it failed rather than leaving the account out unseen.
     */
    suspend fun forBackup(): BackupAccount? {
        if (!isAvailable) return null
        val account = store().readOnce() ?: return null
        val password = withContext(io) { cipher().decrypt(account.passwordCiphertext) }
        return BackupAccount(account.username, password, account.isRefused)
    }

    /** The stored account exactly as stored, for a restore's way-back set; null when signed out. A read error throws. */
    override suspend fun storedForRestore(): ScraperAccount? = if (isAvailable) store().readOnce() else null

    /**
     * A restored backup's account, encrypted with this device's key the way a
     * sign-in stores it: another device's ciphertext cannot be read here. ScreenScraper is not
     * asked: the file's refusal mark goes with it, and a run that meets a refusal marks it as always.
     */
    override suspend fun encryptForRestore(account: BackupAccount): ScraperAccount {
        val password = requireNotNull(account.password) { "a restored account without a password is a sign-out" }
        val ciphertext = withContext(io) { cipher().encrypt(password) }
        return ScraperAccount(account.username.trim(), ciphertext, account.isRefused)
    }

    /**
     * Stores exactly [account] (a restore, or its Undo), or signs out for null. Unlike a sign-in, a
     * failure throws: the restore then puts everything back. The scraper held for the old account goes.
     */
    override suspend fun putForRestore(account: ScraperAccount?) {
        if (!isAvailable) return
        lock.withLock {
            held = null
            if (account == null) {
                store().signOut()
            } else {
                store().signIn(account.username, account.passwordCiphertext)
                if (account.isRefused) store().markRefused(account.username, account.passwordCiphertext)
            }
            pauseReads.value++
        }
        if (account == null) guarded("stop the box art run") { onSignedOut() }
    }

    /**
     * True when a run could scrape: a build with developer credentials and a stored account that
     * ScreenScraper has not refused. Reads the account file only (no Keystore): the triggers ask
     * this, the run itself asks [scraper].
     */
    suspend fun hasUsableAccount(): Boolean {
        if (!isAvailable) return false
        val account = guarded("read the account") { store().account.first() } ?: return false
        return !account.isRefused
    }

    /**
     * A run with [scraper] (from [scraper]) met a refusal: its account is marked, so Settings says
     * "sign in again" (the run's [com.plumemu.scraper.work.ScraperWork.onUnavailable]).
     * Nothing is marked when the person has signed in again since that scraper was made: the
     * refusal was for the old password.
     */
    suspend fun markRefused(scraper: Scraper) {
        lock.withLock {
            val refused = held?.takeIf { it.scraper === scraper } ?: return@withLock
            held = null
            guarded("mark the account refused") { store().markRefused(refused.username, refused.ciphertext) }
        }
    }

    private suspend fun statusOf(account: ScraperAccount?): AccountStatus {
        if (account == null) return AccountStatus.SignedOut
        val pause = guarded("read the scraper pause") { withContext(io) { pauseStore()?.load() } }
        val until = pause?.takeIf { it.reason == PauseReason.DailyQuota && it.resumeAtMillis > clock() }?.resumeAtMillis
        return AccountStatus.SignedIn(account.username, account.isRefused, until)
    }

    private suspend fun <T> guarded(
        what: String,
        block: suspend () -> T,
    ): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Could not $what (${e.javaClass.simpleName})")
            null
        }
}
