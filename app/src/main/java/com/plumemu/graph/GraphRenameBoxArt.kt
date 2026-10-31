package com.plumemu.graph

import android.content.Context
import com.plumemu.game.RenameBoxArtNotice
import com.plumemu.library.scrape.LibraryScrapeSource
import com.plumemu.library.scrape.PersonChoice
import com.plumemu.library.scrape.RenameLookup
import com.plumemu.library.scrape.RenameSkip
import com.plumemu.scraper.FailureReason
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.PauseReason
import com.plumemu.scraper.PauseStore
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.SearchOutcome
import com.plumemu.scraper.UnavailableReason
import com.plumemu.scraper.work.PictureOutcome
import com.plumemu.scraper.work.RunPictures
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Box art after a rename: once the person
 * gave a game a chosen title or their own name ([GraphNameFixes]), one ScreenScraper search by that
 * name on the game's system, only for a game with no box art, and a cover stored only when exactly
 * one game of that system carries the name ([Scraper.searchExactTitle]) in an answer below
 * ScreenScraper's 30-game cap. Precision over recall: a second same-titled game, a capped answer,
 * no game, another game than the one a run matched the file to, or any failure stores nothing.
 *
 * - The person's own action. Nothing is asked without a usable
 *   account, with box art paused by the person, with a saved pause (daily quota, server closed)
 *   still ahead, or without an unmetered network (Wi-Fi); the battery is not checked. Each of
 *   those is told to the person right after the rename ([noticeFor], shown by the game menu),
 *   except signed out: box art is off then.
 * - The game's side ([LibraryScrapeSource.renameLookup]): still renamed, named, and with no box
 *   art (a person's choice or a run's box front is never touched).
 * - A certain match is fetched with [Scraper.byGameId] and saved with [RunPictures] like a pick in
 *   Fix box art, then stored only if all of that still holds ([LibraryScrapeSource.storeRenameMatch]),
 *   inside [NonCancellable]: once begun, the write is finished.
 * - One name is asked once per game in a process once it had a definitive outcome (stored,
 *   ambiguous, none, the file's own match); after a failure (quota, network, pictures) the next
 *   rename may ask again. Lookups take turns, so two quick renames never search at once.
 * - Picture lines are prefixed as this lookup's, not as a run's.
 * - One log line per lookup, with the game id and classes only: never the name, the account or a URL.
 */
internal class GraphRenameBoxArt(
    private val scraper: suspend () -> Scraper?,
    /** An account is stored, refused or not ([GraphScraperAccounts.isSignedIn]). */
    private val isSignedIn: suspend () -> Boolean,
    private val hasUsableAccount: suspend () -> Boolean,
    private val markRefused: suspend (Scraper) -> Unit,
    private val pausedByPerson: suspend () -> Boolean,
    /** The scraper's saved pause; read on [io]. */
    private val savedPause: () -> ScrapeResult.Paused?,
    /** Only [DeviceConditions.unmeteredNetwork] is read: the person's action ignores the battery. */
    private val conditions: () -> DeviceConditions,
    private val source: () -> LibraryScrapeSource,
    private val mediaDir: () -> File,
    private val lowRamDevice: () -> Boolean,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit,
) {
    private val turns = Mutex()

    /** `gameId|name` already answered in this process; read and written under [turns]. */
    private val answered = HashSet<String>()

    /**
     * Why the lookup after [gameId]'s rename cannot ask now, for the person; null when it can, and
     * when there is nothing to tell (signed out, or the game is not one the lookup would ask for).
     */
    suspend fun noticeFor(gameId: Long): RenameBoxArtNotice? =
        withContext(io) {
            val notice = hold()?.notice ?: return@withContext null
            if (source().renameLookup(gameId) is RenameLookup.Ready) notice else null
        }

    /** The lookup after [gameId]'s rename. Never throws, except a cancel; what it did is logged and returned. */
    suspend fun afterRename(gameId: Long): RenameArtOutcome {
        val outcome =
            try {
                withContext(io) { turns.withLock { lookUp(gameId) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RenameArtOutcome.Failed(e.javaClass.simpleName)
            }
        log("$LOG_PREFIX game $gameId ${outcome.logText}")
        return outcome
    }

    private suspend fun lookUp(gameId: Long): RenameArtOutcome {
        hold()?.let { return RenameArtOutcome.Skipped(it.logText) }
        val library = source()
        val lookup =
            when (val asked = library.renameLookup(gameId)) {
                is RenameLookup.Skip -> return RenameArtOutcome.Skipped(skipText(asked.reason))
                is RenameLookup.Ready -> asked
            }
        val nameKey = "$gameId|${lookup.title.trim().lowercase(Locale.ROOT)}"
        if (nameKey in answered) return RenameArtOutcome.Skipped("asked before for this name")
        val scraper = scraper() ?: return RenameArtOutcome.Skipped("not signed in")
        // A run's request, but the 30-day answer cache and saved pictures may be used: one search at most.
        val request = lookup.request.copy(fresh = false)
        val found =
            when (val searched = scraper.searchExactTitle(lookup.title, request.systemId, request.regions)) {
                is SearchOutcome.Found -> searched
                SearchOutcome.NothingFound -> return RenameArtOutcome.NoMatch.answeredFor(nameKey)
                is SearchOutcome.Paused -> return RenameArtOutcome.Failed(pausedText(searched.reason))
                is SearchOutcome.Unavailable -> return unavailable(scraper, searched.reason)
                is SearchOutcome.Failed -> return RenameArtOutcome.Failed(failureText(searched.reason))
            }
        // A full answer (30 games) may have cut a second game with the name: not certain.
        if (found.truncated) return RenameArtOutcome.Ambiguous(found.games.size, capped = true).answeredFor(nameKey)
        if (found.games.size != 1) return RenameArtOutcome.Ambiguous(found.games.size).answeredFor(nameKey)
        val gameIdFound = found.games.single().screenScraperGameId
        // A run matched the file itself to another game: the file wins (precision first).
        if (lookup.fileMatchId != null && lookup.fileMatchId != gameIdFound) {
            return RenameArtOutcome.Skipped(skipText(RenameSkip.MATCHED_BY_FILE)).answeredFor(nameKey)
        }
        val result =
            when (val answer = scraper.byGameId(gameIdFound, request)) {
                is ScrapeResult.Matched -> answer
                ScrapeResult.NoMatch -> return RenameArtOutcome.NoMatch.answeredFor(nameKey)
                is ScrapeResult.Paused -> return RenameArtOutcome.Failed(pausedText(answer.reason))
                is ScrapeResult.Unavailable -> return unavailable(scraper, answer.reason)
                is ScrapeResult.Failed -> return RenameArtOutcome.Failed(failureText(answer.reason))
            }
        // RunPictures writes its own lines as a run's ("scrape run: ..."): these are the rename's.
        val picturesLog = { line: String -> log("$LOG_PREFIX pictures: ${line.removePrefix(RUN_LOG_PREFIX)}") }
        val pictures = RunPictures(scraper, mediaDir(), ScrapeMediaSizes.all, lowRamDevice(), picturesLog)
        val front =
            when (val saved = pictures.save(result, MediaKind.BoxFront, fresh = false)) {
                is PictureOutcome.Saved -> saved.media
                // A refused password marks the account, as in Fix box art.
                is PictureOutcome.Unavailable -> return unavailable(scraper, saved.reason)
                else -> return RenameArtOutcome.Failed("pictures ${saved.javaClass.simpleName}")
            }
        // Found for its box art: a game with none to save is not stored, as in Fix box art.
        if (front.isEmpty()) return RenameArtOutcome.Failed("no box front")
        val support =
            when (val saved = pictures.save(result, MediaKind.Support, fresh = false)) {
                is PictureOutcome.Saved -> saved.media
                else -> pictures.savedBefore(result.match.screenScraperGameId, MediaKind.Support)
            }
        val notStored =
            withContext(NonCancellable) {
                library.storeRenameMatch(gameId, lookup.title, PersonChoice.Picked(result, front + support))
            }
        return if (notStored == null) RenameArtOutcome.Stored(result.match.screenScraperGameId).answeredFor(nameKey) else RenameArtOutcome.NotStored(skipText(notStored))
    }

    /**
     * Remembers [nameKey] as answered: only a definitive outcome (stored, ambiguous, none, the
     * file's own match), so a failure before it (quota, network, pictures) may be asked again.
     */
    private fun RenameArtOutcome.answeredFor(nameKey: String): RenameArtOutcome = also { answered += nameKey }

    /** Why nothing may be asked now: the log's words and what the person is told (null: nothing). */
    private class Hold(val logText: String, val notice: RenameBoxArtNotice?)

    /** Why nothing may be asked now, or null. */
    private suspend fun hold(): Hold? {
        if (!isSignedIn()) return Hold("not signed in", null)
        if (!hasUsableAccount()) return Hold("sign-in needed", RenameBoxArtNotice.SignInNeeded)
        if (pausedByPerson()) return Hold("box art paused", RenameBoxArtNotice.Paused)
        savedPause()?.takeIf { it.resumeAtMillis > clock() }?.let { pause ->
            val notice =
                when (pause.reason) {
                    PauseReason.DailyQuota -> RenameBoxArtNotice.DailyLimit(pause.resumeAtMillis)
                    PauseReason.ServerClosed -> RenameBoxArtNotice.ServerClosed(pause.resumeAtMillis)
                }
            return Hold(pausedText(pause.reason), notice)
        }
        if (!conditions().unmeteredNetwork) return Hold("no unmetered network", RenameBoxArtNotice.NoWifi)
        return null
    }

    private suspend fun unavailable(
        scraper: Scraper,
        reason: UnavailableReason,
    ): RenameArtOutcome {
        // A refused password marks the account, as a run and Fix box art do.
        if (reason == UnavailableReason.BadUserCredentials) markRefused(scraper)
        return RenameArtOutcome.Failed("unavailable $reason")
    }

    private fun pausedText(reason: PauseReason): String =
        when (reason) {
            PauseReason.DailyQuota -> "daily quota"
            PauseReason.ServerClosed -> "server closed"
        }

    private fun failureText(reason: FailureReason): String =
        when (reason) {
            FailureReason.Network -> "network"
            is FailureReason.Server -> "server ${reason.code}"
            FailureReason.RomUnreadable -> "rom unreadable"
        }

    private fun skipText(reason: RenameSkip): String =
        when (reason) {
            RenameSkip.GAME_GONE -> "game gone"
            RenameSkip.BEING_NAMED -> "being named"
            RenameSkip.NAME_CHANGED -> "name changed"
            RenameSkip.PERSON_CHOICE -> "box art chosen by the person"
            RenameSkip.HAS_BOX_ART -> "has box art"
            RenameSkip.MATCHED_BY_FILE -> "matched by file"
            RenameSkip.NOT_LOOKED_UP -> "not looked up"
        }

    private companion object {
        const val LOG_PREFIX = "box art after rename:"
        const val RUN_LOG_PREFIX = "scrape run: "
    }
}

/**
 * The process's lookup after a rename, wired to the account's scraper, the person's pause, the
 * scraper's saved pause, the device's network and battery, and the run's library source and media
 * folder (Fix box art's); made once, with [AppGraph.nameFixes].
 */
internal fun renameBoxArtFor(
    context: Context,
    graph: AppGraph,
    source: () -> LibraryScrapeSource,
    pauseStore: () -> PauseStore,
    mediaDir: () -> File,
    io: CoroutineDispatcher,
    log: (String) -> Unit,
): GraphRenameBoxArt {
    val app = context.applicationContext
    val accounts = graph.scraperAccounts
    return GraphRenameBoxArt(
        scraper = { accounts.scraper() },
        isSignedIn = { accounts.isSignedIn.first() },
        hasUsableAccount = { accounts.hasUsableAccount() },
        markRefused = { accounts.markRefused(it) },
        pausedByPerson = { graph.settingsStore.settings.first().library.boxArtPaused },
        savedPause = { pauseStore().load() },
        conditions = { deviceConditionsOf(app) },
        source = source,
        mediaDir = mediaDir,
        lowRamDevice = { RunPictures.isLowRamDevice(app) },
        io = io,
        log = log,
    )
}

/** What one lookup after a rename did; [logText] is the end of its log line. */
internal sealed interface RenameArtOutcome {
    val logText: String

    /** Nothing was asked. */
    data class Skipped(val reason: String) : RenameArtOutcome {
        override val logText get() = "skipped ($reason)"
    }

    /** Stored ScreenScraper game [screenScraperId] with its box front. */
    data class Stored(val screenScraperId: String) : RenameArtOutcome {
        override val logText get() = "searched, took $screenScraperId"
    }

    /** [games] games of the system carry the name, or the answer was [capped] at 30 games: not certain, nothing stored. */
    data class Ambiguous(val games: Int, val capped: Boolean = false) : RenameArtOutcome {
        override val logText get() = if (capped) "searched, ambiguous (results capped)" else "searched, ambiguous ($games games)"
    }

    /** No game of the system carries the name. */
    data object NoMatch : RenameArtOutcome {
        override val logText get() = "searched, none"
    }

    /** A certain match came back, but by then the game no longer qualified: nothing stored. */
    data class NotStored(val reason: String) : RenameArtOutcome {
        override val logText get() = "searched, not stored ($reason)"
    }

    /** A pause, a refusal, the network, the server or the pictures: nothing changed. */
    data class Failed(val reason: String) : RenameArtOutcome {
        override val logText get() = "failed ($reason)"
    }
}
