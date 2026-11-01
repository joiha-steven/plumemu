package com.plumemu.graph

import com.plumemu.achievements.api.AwardKind
import com.plumemu.achievements.api.RaCompletion
import com.plumemu.achievements.api.RaGameProgress
import com.plumemu.achievements.api.RaResult
import com.plumemu.achievements.api.RetroAchievementsClient
import com.plumemu.achievements.hash.RaCannotHashReason
import com.plumemu.achievements.match.RaMatch
import com.plumemu.achievements.match.RaMatcher
import com.plumemu.achievements.match.RaSystems
import com.plumemu.coredata.db.RaDao
import com.plumemu.coredata.db.RaMatchEntity
import com.plumemu.coredata.db.RaProgressEntity
import com.plumemu.coredata.db.RaUnmatchedGame
import com.plumemu.identify.name.MatchKey
import com.plumemu.identify.name.TitleCleaner
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * RetroAchievements for the signed-in account [ulid]: made by [GraphAchievements] only once an
 * account is stored and its key decrypted, dropped ([close]) on sign-out.
 *
 * - A game's achievements on demand ([progressFor]): the stored match when the file has not
 *   changed (a stored hash is looked up again, so a set added later is found), else the file is
 *   hashed; then the person's progress, stored as the game's progress row.
 * - After a game ([afterGame]): only an already matched game is asked about, once.
 * - The daily completion refresh ([refreshCompletion]).
 *
 * Hashing runs on [io] and under [hashing], shared by every instance: one file at a time. Database
 * writes go through [writes] and stop once [close] ran, so a call that ends after a sign-out never
 * writes the old account's progress back.
 */
internal class Achievements(
    private val ulid: String,
    private val client: RetroAchievementsClient,
    private val matcher: RaMatcher,
    private val dao: RaDao,
    private val library: RaLibrary,
    private val hashing: Mutex,
    private val writes: Mutex,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long,
    private val log: (String) -> Unit,
    /** RetroAchievements refused the key: the graph marks the stored account and drops this object. */
    private val onWrongKey: suspend () -> Unit,
    /** A game is being played: the completion refresh stops hashing. */
    private val isPlaying: suspend () -> Boolean = { false },
) {
    @Volatile
    private var closed = false

    /** No more writes; called under [writes] by the graph. */
    fun close() {
        closed = true
    }

    suspend fun progressFor(gameId: Long): AchievementsState {
        val game = library.game(gameId) ?: return AchievementsState.Error(AchievementsError.GameUnavailable)
        var byName = false
        val raGameId = when (val match = matchOf(game)) {
            is RaMatch.Matched -> match.raGameId
            is RaMatch.MatchedByName -> {
                byName = true
                match.raGameId
            }
            is RaMatch.NoSet -> return AchievementsState.NoSet
            is RaMatch.CannotHash -> return AchievementsState.CannotHash(match.reason)
            is RaMatch.Unreadable -> return AchievementsState.Error(AchievementsError.GameUnavailable)
            RaMatch.NotSupported -> return AchievementsState.NotSupported
            is RaMatch.Failed -> return failed("game list", match.failure)
        }
        return when (val progress = client.gameProgress(raGameId, ulid)) {
            is RaResult.Failure -> failed("game progress", progress)
            is RaResult.Ok -> {
                write { dao.upsertProgress(listOf(rowOf(progress.value))) }
                readyOf(progress.value).copy(matchedByName = byName)
            }
        }
    }

    /**
     * Once the person is back from [gameId]: when it is matched, its progress is asked once and
     * stored; a rise in unlocked achievements over the stored row is returned. Null when nothing
     * rose, the game is not matched, the call failed, or there is no earlier count to compare with
     * ([hasBaseline] false and no stored row: the first refresh has not run, so every unlock would
     * look new).
     */
    suspend fun afterGame(gameId: Long, hasBaseline: Boolean): NewAchievements? {
        val match = dao.match(gameId)?.takeIf { it.status in RaMatchEntity.MATCHED_STATUS_LIST } ?: return null
        val raGameId = match.raGameId ?: return null
        val before = dao.observeProgress(gameId).first()
        val progress = when (val answer = client.gameProgress(raGameId.toInt(), ulid)) {
            is RaResult.Failure -> {
                failed("game progress after a game", answer)
                return null
            }
            is RaResult.Ok -> answer.value
        }
        if (!write { dao.upsertProgress(listOf(rowOf(progress))) }) return null
        val previous = before?.awardedSoftcore ?: (if (hasBaseline) 0 else return null)
        val rise = progress.awarded - previous
        if (rise <= 0) return null
        val title = library.game(gameId)?.title?.takeIf { it.isNotBlank() } ?: progress.title
        return NewAchievements(gameId, rise, title)
    }

    /**
     * Every game with progress, all pages, as the whole progress table; then mastered games no
     * library game is matched to yet are looked for among library games of the same title. False
     * on a failure (logged once, nothing replaced).
     */
    suspend fun refreshCompletion(): Boolean {
        val games = when (val answer = client.completion(ulid)) {
            is RaResult.Failure -> {
                failed("completion", answer)
                return false
            }
            is RaResult.Ok -> answer.value
        }
        val now = clock()
        if (!write { dao.replaceProgress(games.map { rowOf(it, now) }) }) return false
        matchMastered(games)
        return true
    }

    /**
     * Mastered games the library holds but has not matched: only library games of a system mapped
     * to the game's console whose title reads the same are hashed, never the whole library, and at
     * most [MAX_TITLE_HASHES] files per refresh. A game already hashed is never read again: a stored
     * "no set" hash is looked up in the game list, a stored "cannot hash" is skipped. Hashing stops
     * as soon as a game is being played.
     */
    private suspend fun matchMastered(games: List<RaCompletion>) {
        val mastered = games.filter { it.highestAward == AwardKind.Mastered && it.title.isNotBlank() }
        if (mastered.isEmpty()) return
        val placed = dao.matchedRaGameIdList().toSet()
        var budget = MAX_TITLE_HASHES
        for ((consoleId, wanted) in mastered.filter { it.gameId.toLong() !in placed }.groupBy { it.consoleId }) {
            val systems = RaSystems.systemIds.filter { RaSystems.consoleOf(it) == consoleId }
            if (systems.isEmpty()) continue
            val candidates = library.unmatched(systems)
                .filter { it.matchStatus != RaMatchEntity.CANNOT_HASH }
                .map { it to setOf(MatchKey.of(it.title), MatchKey.of(it.fileTitle)) }
            for (game in wanted) {
                val key = MatchKey.of(TitleCleaner.clean(game.title).title)
                if (key.isEmpty()) continue
                for ((candidate, keys) in candidates) {
                    if (key !in keys) continue
                    if (closed) return
                    val md5 = candidate.md5
                    if (candidate.matchStatus == RaMatchEntity.NO_SET && md5 != null) {
                        lookUpAgain(candidate, md5)
                        continue
                    }
                    if (budget <= 0 || isPlaying()) return
                    budget--
                    library.game(candidate.gameId)?.let { matchOf(it) }
                }
            }
        }
    }

    /** A stored "no set" hash looked up again without reading the file; stored when it now matches. */
    private suspend fun lookUpAgain(candidate: RaUnmatchedGame, md5: String) {
        val match = matcher.matchHash(candidate.systemId, md5) as? RaMatch.Matched ?: return
        val stored = dao.match(candidate.gameId) ?: return
        write { dao.saveMatch(stored.copy(status = RaMatchEntity.MATCHED, raGameId = match.raGameId.toLong(), checkedAt = clock())) }
    }

    /** The game's match: the stored one while its file is unchanged, else the file hashed (one at a time, on [io]); stored. */
    private suspend fun matchOf(game: RaLibraryGame): RaMatch {
        if (RaSystems.consoleOf(game.systemId) == null) return RaMatch.NotSupported
        val file = try {
            withContext(io) { game.file() }
        } catch (e: IOException) {
            log("Game ${game.id}: its file cannot be read for RetroAchievements (${e.javaClass.simpleName})")
            return RaMatch.Unreadable(e)
        }
        val source = file.source
        val stored = dao.freshMatch(game.id, source.key, source.sizeBytes, file.modifiedAt)
        val md5 = stored?.md5
        val match = when {
            stored?.status == RaMatchEntity.CANNOT_HASH -> return RaMatch.CannotHash(reasonOf(stored.cannotHashReason))
            stored?.status == RaMatchEntity.MATCHED_BY_NAME && stored.raGameId != null -> return RaMatch.MatchedByName(stored.raGameId!!.toInt())
            md5 != null -> matcher.matchHash(game.systemId, md5)
            else -> hashing.withLock { withContext(io) { matcher.match(game.systemId, source, listOf(game.title, game.fileTitle)) } }
        }
        val row = when (match) {
            is RaMatch.Matched -> RaMatchEntity(game.id, RaMatchEntity.MATCHED, match.raGameId.toLong(), match.md5, null, source.key, source.sizeBytes, file.modifiedAt, clock())
            is RaMatch.NoSet -> RaMatchEntity(game.id, RaMatchEntity.NO_SET, null, match.md5, null, source.key, source.sizeBytes, file.modifiedAt, clock())
            is RaMatch.MatchedByName -> RaMatchEntity(game.id, RaMatchEntity.MATCHED_BY_NAME, match.raGameId.toLong(), null, null, source.key, source.sizeBytes, file.modifiedAt, clock())
            is RaMatch.CannotHash -> RaMatchEntity(game.id, RaMatchEntity.CANNOT_HASH, null, null, match.reason.name, source.key, source.sizeBytes, file.modifiedAt, clock())
            // Not answers about the file: nothing stored, asked again next time.
            is RaMatch.Unreadable, is RaMatch.Failed, RaMatch.NotSupported -> null
        }
        if (row != null && (stored == null || stored.status != row.status || stored.raGameId != row.raGameId)) {
            write { dao.saveMatch(row) }
        }
        return match
    }

    private suspend fun failed(what: String, failure: RaResult.Failure): AchievementsState {
        log("RetroAchievements $what failed (${causeOf(failure)})")
        if (failure == RaResult.WrongKey) {
            try {
                onWrongKey()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("The refused key could not be marked (${e.javaClass.simpleName})")
            }
        }
        return AchievementsState.Error(errorOf(failure))
    }

    /**
     * Runs [block] under [writes] unless [close] ran; false when it did not run. The graph writes the
     * refresh marks through it, so a refresh that ends after a sign-out leaves no mark behind.
     */
    suspend fun write(block: suspend () -> Unit): Boolean = writes.withLock {
        if (closed) return@withLock false
        block()
        true
    }

    private fun rowOf(progress: RaGameProgress) = RaProgressEntity(
        raGameId = progress.id.toLong(),
        achievementsTotal = progress.achievementCount,
        awardedSoftcore = progress.awarded,
        awardedHardcore = progress.awardedHardcore,
        highestAward = progress.highestAward.raw,
        updatedAt = clock(),
    )

    private fun rowOf(game: RaCompletion, now: Long) = RaProgressEntity(
        raGameId = game.gameId.toLong(),
        achievementsTotal = game.maxPossible,
        awardedSoftcore = game.awarded,
        awardedHardcore = game.awardedHardcore,
        highestAward = game.highestAward.raw,
        updatedAt = now,
    )

    private fun reasonOf(name: String?): RaCannotHashReason =
        RaCannotHashReason.entries.firstOrNull { it.name == name } ?: RaCannotHashReason.NotYetSupported

    companion object {
        /** Files hashed by one completion refresh at most, to place mastered games by title. */
        const val MAX_TITLE_HASHES = 20
    }
}
