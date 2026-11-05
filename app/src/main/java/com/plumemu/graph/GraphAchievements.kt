package com.plumemu.graph

import com.plumemu.account.RaAccountStatus
import com.plumemu.achievements.account.ApiKeyCipher
import com.plumemu.achievements.account.SignInCheck
import com.plumemu.achievements.api.RaMedia
import com.plumemu.achievements.api.RaResult
import com.plumemu.achievements.api.WebApiKey
import com.plumemu.achievements.api.RetroAchievementsClient
import com.plumemu.achievements.hash.RaHashResult
import com.plumemu.achievements.hash.RaHasher
import com.plumemu.achievements.hash.RaRomSource
import com.plumemu.achievements.match.RaMatcher
import com.plumemu.coredata.account.RetroAchievementsAccount
import com.plumemu.coredata.account.RetroAchievementsAccountStore
import com.plumemu.coredata.db.RaDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one place the app holds RetroAchievements. While no account is stored, nothing of it is
 * built: only the account file is read ([isSignedIn], and the account check each entry point makes
 * first); no client, hasher, matcher or database query, no request. Signed in, an [Achievements]
 * is made on first use for the stored account and dropped on sign-out or when the key is refused.
 *
 * Settings: [status] (the account file only) and [signIn]; the client for a typed key is built only when
 * Sign in is pressed.
 *
 * Entry points: Info ([progress], [progressFor]), the return from a game ([afterGame], which
 * announces rises on [newAchievements]), Plumemu coming to the front ([onFront], the daily
 * completion refresh, [refreshIfDue]), sign-out ([signOut]) and a restored backup ([afterRestore]).
 * Log lines never carry the user name, the ULID or the key.
 */
internal class GraphAchievements(
    private val store: Lazy<RetroAchievementsAccountStore>,
    private val cipher: Lazy<ApiKeyCipher>,
    private val clientFor: (WebApiKey) -> RetroAchievementsClient,
    private val raDao: () -> RaDao,
    private val library: () -> RaLibrary,
    private val refreshMarks: RefreshMarks,
    private val gates: RefreshGates,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit,
    private val hash: suspend (consoleId: Int, rom: RaRomSource) -> RaHashResult = RaHasher::hash,
) {
    private class Held(val ulid: String, val ciphertext: String, val achievements: Achievements)

    private sealed interface Current {
        data object SignedOut : Current

        data object KeyRefused : Current

        class Ready(val achievements: Achievements) : Current
    }

    private val lock = Mutex()
    private val writes = Mutex()
    private val hashing = Mutex()
    private val refreshing = Mutex()
    private var held: Held? = null

    private val announced = MutableSharedFlow<NewAchievements>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Rises in unlocked achievements found after a game, for the home's one quiet line. */
    val newAchievements: Flow<NewAchievements> = announced.asSharedFlow()

    /** Whether an account is stored: the account file only. A file that cannot be read counts as signed out. */
    val isSignedIn: Flow<Boolean> =
        flow { emitAll(store.value.isSignedIn) }
            .catch { failure ->
                log("RetroAchievements account could not be read (${failure.javaClass.simpleName}); signed out")
                emit(false)
            }.distinctUntilChanged()

    /** The stored account as Settings shows it: the account file only; a file that cannot be read counts as signed out. */
    val status: Flow<RaAccountStatus> =
        flow { emitAll(store.value.account) }
            .map { account -> if (account == null) RaAccountStatus.SignedOut else RaAccountStatus.SignedIn(account.userName, account.points, account.isKeyRefused) }
            .catch { failure ->
                log("RetroAchievements account could not be read (${failure.javaClass.simpleName}); signed out")
                emit(RaAccountStatus.SignedOut)
            }.distinctUntilChanged()

    /**
     * Settings' Sign in: one profile call with the typed [apiKey] ([SignInCheck]), then the key is
     * encrypted and the account stored (this replaces an earlier account and its refusal). Nothing is
     * stored unless RetroAchievements accepted the key. A different user than before starts clean:
     * the earlier user's progress rows and refresh marks go. Neither the name nor the key is logged.
     */
    suspend fun signIn(userName: String, apiKey: String): RaSignInResult {
        val profile = when (val checked = SignInCheck(clientFor).check(userName, apiKey)) {
            is RaResult.Ok -> checked.value
            RaResult.WrongKey -> return RaSignInResult.WrongKey
            RaResult.UnknownUser -> return if (userName.isBlank()) RaSignInResult.Missing else RaSignInResult.UnknownUser
            RaResult.NoConnection -> return RaSignInResult.NoConnection
            is RaResult.Busy -> return RaSignInResult.Busy
            is RaResult.RateLimited -> return RaSignInResult.RateLimited
            is RaResult.BadAnswer -> {
                log("RetroAchievements sign-in got an answer it could not read")
                return RaSignInResult.Busy
            }
        }
        val ciphertext = guarded("encrypt the key") { withContext(io) { cipher.value.encrypt(apiKey.trim()) } }
        if (ciphertext.isNullOrBlank()) return RaSignInResult.NotSaved
        return lock.withLock {
            writes.withLock {
                val earlier = guarded("read the earlier account") { store.value.account.first() }
                val saved = guarded("store the account") { store.value.signIn(profile.userName, profile.ulid, ciphertext, profile.points) }
                if (saved == null) {
                    RaSignInResult.NotSaved
                } else {
                    dropHeld()
                    if (earlier != null && earlier.ulid != profile.ulid) {
                        guarded("clear the earlier account's progress") { raDao().clearProgress() }
                        guarded("clear the refresh marks") { withContext(io) { refreshMarks.clear() } }
                    }
                    RaSignInResult.SignedIn(profile.userName, profile.points)
                }
            }
        }
    }

    /**
     * Library game ids whose RetroAchievements game is mastered, for the trophy on game rows. Signed
     * out it is empty and the database is not asked (the DAO is only fetched while signed in); a
     * sign-out ends the query and empties the set. A failure reading it logs once and shows no marks.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val masteredGames: Flow<Set<Long>> =
        isSignedIn
            .flatMapLatest { signedIn -> if (signedIn) raDao().masteredGameIds() else flowOf(emptySet()) }
            .catch { failure ->
                log("Mastered games could not be read (${failure.javaClass.simpleName})")
                emit(emptySet())
            }.distinctUntilChanged()

    /** Info's state of [gameId]: [AchievementsState.Loading], then the answer. */
    fun progress(gameId: Long): Flow<AchievementsState> = flow {
        emit(AchievementsState.Loading)
        emit(progressFor(gameId))
    }

    /**
     * [gameId]'s progress as the last refresh stored it (the row Info writes and the trophy marks read): one database read,
     * never the network and no hashing, so a card may ask on every settle. Null: signed out, not matched, nothing stored, or an empty set.
     */
    suspend fun storedCountOf(gameId: Long): AchievementCount? {
        if (!isSignedIn.first()) return null
        val row = guarded("read the stored achievements of game $gameId") { raDao().observeProgress(gameId).first() } ?: return null
        return if (row.achievementsTotal > 0) AchievementCount(maxOf(row.awardedSoftcore, row.awardedHardcore), row.achievementsTotal) else null
    }

    /**
     * [storedCountOf] with the points and the latest [RECENT_BADGES] unlocks the stored list holds (Info and after-game reads fill them):
     * the second screen's game card. The database only, never the network. Null: signed out, not matched, nothing stored, or an empty set.
     */
    suspend fun storedFactsOf(gameId: Long): StoredAchievements? {
        if (!isSignedIn.first()) return null
        val row = guarded("read the stored achievements of game $gameId") { raDao().observeProgress(gameId).first() } ?: return null
        if (row.achievementsTotal <= 0) return null
        val recent = guarded("read the latest achievements of game $gameId") { raDao().observeRecentAchievements(row.raGameId, RECENT_BADGES).first() }.orEmpty()
        return StoredAchievements(
            earned = maxOf(row.awardedSoftcore, row.awardedHardcore),
            total = row.achievementsTotal,
            pointsEarned = row.pointsEarned,
            pointsTotal = row.pointsTotal,
            recent = recent.mapNotNull { achievement ->
                val at = maxOf(achievement.earnedAt ?: 0L, achievement.earnedHardcoreAt ?: 0L).takeIf { it > 0 } ?: return@mapNotNull null
                StoredBadge(achievement.title, achievement.badgeName?.let(RaMedia::badge), at)
            },
        )
    }

    /** [gameId]'s achievements and the person's progress; stores the match and the progress row. */
    suspend fun progressFor(gameId: Long): AchievementsState =
        when (val current = current()) {
            Current.SignedOut -> AchievementsState.NotSignedIn
            Current.KeyRefused -> AchievementsState.Error(AchievementsError.WrongKey)
            is Current.Ready -> guarded("read the achievements of game $gameId") { current.achievements.progressFor(gameId) }
                ?: AchievementsState.Error(AchievementsError.BadAnswer)
        }

    /**
     * The person is back from [gameId] (the moment play time counts the session): when signed in
     * and the game is matched, its progress is asked once; a rise is announced on [newAchievements].
     * Then the daily refresh, which the return to the front skipped while the game was still noted.
     */
    suspend fun afterGame(gameId: Long) {
        val achievements = (current() as? Current.Ready)?.achievements ?: return
        val hasBaseline = guarded("read the refresh marks") { withContext(io) { refreshMarks.lastSuccess() > 0 } } == true
        guarded("ask the achievements after game $gameId") { achievements.afterGame(gameId, hasBaseline) }?.let(announced::tryEmit)
        refreshIfDue()
    }

    /** Plumemu came to the front: the daily refresh when due ([refreshIfDue]), in [scope]. */
    fun onFront(): Job = scope.launch { refreshIfDue() }

    /**
     * The completion refresh, when every gate passes: signed in (checked first, so a signed-out
     * front reads nothing else), no game being played, an unmetered network, and the last run more
     * than a day ago (a failure waits a day too). True when it ran and succeeded.
     */
    suspend fun refreshIfDue(): Boolean {
        val signedIn = guarded("read the account") { store.value.account.first() }
        if (signedIn == null || signedIn.isKeyRefused) return false
        if (guarded("check for a game being played") { gates.isPlaying() } != false) return false
        if (guarded("read the network state") { gates.isUnmetered() } != true) return false
        val now = clock()
        val due = guarded("read the refresh marks") {
            withContext(io) { now - maxOf(refreshMarks.lastSuccess(), refreshMarks.lastFailure()) >= DAY_MILLIS }
        } == true
        if (!due || !refreshing.tryLock()) return false
        try {
            val achievements = (current() as? Current.Ready)?.achievements ?: return false
            val done = guarded("refresh the completion list") { achievements.refreshCompletion() } == true
            // Under the write lock and only while the object is open: a sign-out during the call clears
            // the marks, and a mark written after it would hold the next account's refresh back a day.
            guarded("store the refresh mark") {
                achievements.write { withContext(io) { if (done) refreshMarks.markSuccess(clock()) else refreshMarks.markFailure(clock()) } }
            }
            return done
        } finally {
            refreshing.unlock()
        }
    }

    /**
     * Sign out: the account goes, every progress row goes, the [Achievements] is dropped and the
     * refresh marks are cleared (another account refreshes at once). False when the account file
     * could not be written; the progress is cleared even then.
     */
    suspend fun signOut(): Boolean = lock.withLock {
        writes.withLock {
            held?.achievements?.close()
            held = null
            val signedOut = guarded("sign out") { store.value.signOut() } != null
            guarded("clear the progress") { raDao().clearProgress() }
            guarded("clear the refresh marks") { withContext(io) { refreshMarks.clear() } }
            signedOut
        }
    }

    /**
     * After a backup restore: the restored library may hold another account's progress, so every
     * progress row goes and the next front refreshes. Matches stay (they are not personal).
     *
     * The one deliberate exception to "nothing of RetroAchievements while signed out": this runs
     * signed out too (one delete, only after a restore replaced the library), because the restored
     * file may carry another person's progress, which must not wait for a sign-in to be removed.
     */
    suspend fun afterRestore() {
        writes.withLock {
            guarded("clear the progress after a restore") { raDao().clearProgress() }
            guarded("clear the refresh marks") { withContext(io) { refreshMarks.clear() } }
            // The restored file may hold cached rows an older matcher wrote: check them again once.
            guarded("reset the matcher version") { withContext(io) { refreshMarks.markMatcherVersion(0) } }
        }
        // The re-check runs when the account's Achievements is made, so drop the one held now.
        // After the writes lock, never inside it: the order is lock, then writes.
        lock.withLock { dropHeld() }
    }

    /** The stored account's [Achievements], made on first use; nothing while signed out. */
    private suspend fun current(): Current = lock.withLock {
        val account = guarded("read the account") { store.value.account.first() } ?: return@withLock Current.SignedOut.also { dropHeld() }
        if (account.isKeyRefused) return@withLock Current.KeyRefused.also { dropHeld() }
        held?.takeIf { it.ulid == account.ulid && it.ciphertext == account.keyCiphertext }?.let { return@withLock Current.Ready(it.achievements) }
        val key = guarded("decrypt the key") { withContext(io) { cipher.value.decrypt(account.keyCiphertext) } }
        if (key.isNullOrBlank()) {
            // A backup restored on another device, a wiped key store: Settings asks for a new sign-in.
            guarded("mark the key refused") { store.value.markKeyRefused(account.ulid, account.keyCiphertext) }
            dropHeld()
            return@withLock Current.KeyRefused
        }
        val previous = held
        dropHeld()
        if (previous != null && previous.ulid != account.ulid) {
            writes.withLock { guarded("clear the earlier account's progress") { raDao().clearProgress() } }
        }
        redoOldMatchesOnce()
        Current.Ready(build(account, WebApiKey(key)))
    }

    /**
     * When the matcher's rules changed ([MATCHER_VERSION] rose past the stored one), the cached
     * answers it may now give differently are deleted once (cache rows, not personal data); the files
     * are hashed and named again the next time each game is asked about. The version is marked only
     * after the delete worked, so a failure tries again next time.
     */
    private suspend fun redoOldMatchesOnce() {
        val stored = guarded("read the matcher version") { withContext(io) { refreshMarks.matcherVersion() } } ?: return
        if (stored >= MATCHER_VERSION) return
        writes.withLock {
            if (guarded("delete the matches to check again") { raDao().deleteRowsToCheckAgain() } == null) return@withLock
            guarded("mark the matcher version") { withContext(io) { refreshMarks.markMatcherVersion(MATCHER_VERSION) } }
        }
    }

    private fun build(
        account: RetroAchievementsAccount,
        key: WebApiKey,
    ): Achievements {
        val client = clientFor(key)
        val achievements = Achievements(
            ulid = account.ulid,
            client = client,
            matcher = RaMatcher(client::gameList, ::raNameKey, hash),
            dao = raDao(),
            library = library(),
            hashing = hashing,
            writes = writes,
            io = io,
            clock = clock,
            log = log,
            onWrongKey = { keyRefused(account) },
            isPlaying = { gates.isPlaying() },
        )
        held = Held(account.ulid, account.keyCiphertext, achievements)
        return achievements
    }

    private suspend fun keyRefused(account: RetroAchievementsAccount) {
        lock.withLock { if (held?.ciphertext == account.keyCiphertext) dropHeld() }
        store.value.markKeyRefused(account.ulid, account.keyCiphertext)
    }

    /** Called under [lock]. Closing needs no write lock: a write already running ends, later ones see it closed. */
    private fun dropHeld() {
        held?.achievements?.close()
        held = null
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

    companion object {
        const val DAY_MILLIS: Long = 24L * 60 * 60 * 1000

        /**
         * Rises when the matcher or the hasher learns to answer differently for files it could not
         * place (1: name matching; 2: CSO v2, ZSO, CHD zstd, cdzs, RVZ and WIA hashed; raise it when more formats become hashable), so [redoOldMatchesOnce] clears those cache rows.
         */
        const val MATCHER_VERSION = 2

        /** The badges the second screen's game card shows. */
        const val RECENT_BADGES = 5
    }
}

/** When the completion refresh may run, besides being signed in and due. */
internal interface RefreshGates {
    /** A game is being played (a play is noted and Plumemu is not in front). */
    suspend fun isPlaying(): Boolean

    /** The active network is unmetered (Wi-Fi, Ethernet). */
    fun isUnmetered(): Boolean
}

/** The completion refresh's last success and failure (epoch milliseconds, 0 when none), kept across restarts. Blocking. */
internal interface RefreshMarks {
    fun lastSuccess(): Long

    fun lastFailure(): Long

    fun markSuccess(at: Long)

    fun markFailure(at: Long)

    fun clear()

    /** The matcher rules version whose old cache rows were last cleared (0 when never); kept by [clear]. */
    fun matcherVersion(): Int

    fun markMatcherVersion(version: Int)
}

/** How [GraphAchievements.signIn] ended, one case per line Settings says. */
internal sealed interface RaSignInResult {
    data class SignedIn(val userName: String, val points: Int) : RaSignInResult {
        override fun toString(): String = "SignedIn(userName=***, points=$points)"
    }

    /** The name or the key is blank: nothing was asked. */
    data object Missing : RaSignInResult

    data object WrongKey : RaSignInResult

    data object UnknownUser : RaSignInResult

    data object NoConnection : RaSignInResult

    /** RetroAchievements failed (a server error, or an answer that is not the documented one). */
    data object Busy : RaSignInResult

    data object RateLimited : RaSignInResult

    /** The key could not be encrypted or the account could not be written (logged by class). */
    data object NotSaved : RaSignInResult
}
