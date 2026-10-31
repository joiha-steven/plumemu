package com.plumemu.graph

import android.content.Context
import com.plumemu.game.BoxArtHit
import com.plumemu.game.BoxArtProblem
import com.plumemu.game.BoxArtSearchOutcome
import com.plumemu.game.BoxArtStoreOutcome
import com.plumemu.library.scrape.LibraryScrapeSource
import com.plumemu.library.scrape.PersonChoice
import com.plumemu.library.scrape.PersonChoiceStore
import com.plumemu.scraper.FailureReason
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.PauseReason
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.SearchHit
import com.plumemu.scraper.SearchOutcome
import com.plumemu.scraper.UnavailableReason
import com.plumemu.scraper.work.PictureOutcome
import com.plumemu.scraper.work.RunPictures
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Fix box art… on the graph: the person's search on ScreenScraper, the thumbnails,
 * and storing a pick or "No box art" as the person's choice.
 *
 * - The scraper is the signed-in account's ([GraphScraperAccounts.scraper]); none (signed out,
 *   refused, a password that no longer decrypts) answers [BoxArtProblem.SignInAgain]. A refusal
 *   met here marks the account, as a run does, so Settings says "sign in again". The entry is
 *   offered while an account is stored, refused included, so a refused password is said, not hidden.
 * - The person's own action: it needs Wi-Fi (an unmetered network,
 *   as a run) and nothing is asked without it ([BoxArtProblem.NoWifi]); the battery is not checked,
 *   and the person's Pause of the background run does not stop it.
 * - The search's system and regions, and the pick's request, are the library's
 *   ([LibraryScrapeSource.personChoiceRequest], fresh): the same a run would send.
 * - A pick saves its box front and disc or cartridge with [RunPictures] (fresh, [ScrapeMediaSizes])
 *   and then stores it ([LibraryScrapeSource.storePersonChoice]). That last write is never cut
 *   short by a cancel: once begun, it is finished. A disc or cartridge that fails never holds the
 *   pick back (the one saved before, if any, is kept), as in a run.
 * - "No box art" after a pick names the game again at once ([nameAgain], the pass Back to
 *   automatic runs), so the identifier's name replaces the pick's.
 * - Everything runs off the main thread; lines carry game ids and classes, never names or URLs.
 */
internal class GraphBoxArtFixes(
    private val scraper: suspend () -> Scraper?,
    private val markRefused: suspend (Scraper) -> Unit,
    /** An account is stored (signed in, refused or not), in a build with credentials. */
    private val isSignedIn: suspend () -> Boolean,
    /** Whether an unmetered network is up now ([DeviceConditions.unmeteredNetwork]). */
    private val unmeteredNetwork: () -> Boolean,
    private val source: () -> LibraryScrapeSource,
    private val gameExists: suspend (Long) -> Boolean,
    private val mediaDir: () -> File,
    private val thumbnailDir: () -> File,
    private val lowRamDevice: () -> Boolean,
    private val nameAgain: () -> Unit,
    private val io: CoroutineDispatcher,
    private val log: (String) -> Unit,
    /** The library's write; a seam so a test can hold it half-way. */
    private val storeChoice: suspend (LibraryScrapeSource, Long, PersonChoice) -> PersonChoiceStore = { library, id, choice -> library.storePersonChoice(id, choice) },
) {
    /** Whether the game menu lists Fix box art…: credentials in the build and a stored account, even a refused one. */
    suspend fun isOffered(): Boolean = isSignedIn()

    suspend fun search(
        gameId: Long,
        query: String,
    ): BoxArtSearchOutcome =
        withContext(io) {
            val scraper = scraper() ?: return@withContext BoxArtSearchOutcome.Failed(BoxArtProblem.SignInAgain)
            if (!unmeteredNetwork()) return@withContext BoxArtSearchOutcome.Failed(BoxArtProblem.NoWifi)
            val request = source().personChoiceRequest(gameId) ?: return@withContext BoxArtSearchOutcome.GameNotReady
            when (val outcome = scraper.search(query, request.systemId, request.regions)) {
                is SearchOutcome.Found ->
                    if (outcome.games.isEmpty()) BoxArtSearchOutcome.NothingFound else BoxArtSearchOutcome.Found(outcome.games.map(::hitOf))
                SearchOutcome.NothingFound -> BoxArtSearchOutcome.NothingFound
                is SearchOutcome.Paused -> BoxArtSearchOutcome.Failed(pausedProblem(outcome.resumeAtMillis, outcome.reason))
                is SearchOutcome.Unavailable -> BoxArtSearchOutcome.Failed(unavailableProblem(scraper, outcome.reason))
                is SearchOutcome.Failed -> BoxArtSearchOutcome.Failed(failureProblem(outcome.reason))
            }
        }

    /**
     * [hit]'s box front at most [THUMBNAIL_MAX_PX] on each side, in [thumbnailDir] (the cache):
     * kept there, so the same game in a later search costs no request. Null when it has none or
     * it could not be downloaded (the row shows no picture).
     */
    suspend fun thumbnail(hit: BoxArtHit): File? =
        withContext(io) {
            val ref = hit.thumbnail ?: return@withContext null
            val dir = thumbnailDir()
            val target = File(dir, thumbnailName(hit.id, ref.url, ref.md5))
            if (target.isFile && target.length() > 0) return@withContext target
            if (!unmeteredNetwork()) return@withContext null
            val scraper = scraper() ?: return@withContext null
            val temp =
                try {
                    dir.mkdirs()
                    File.createTempFile(THUMBNAIL_PREFIX, ".part", dir)
                } catch (e: IOException) {
                    log("box art thumbnail: no room in the cache (${e.javaClass.simpleName})")
                    return@withContext null
                }
            try {
                when (val download = scraper.download(ref, temp, THUMBNAIL_MAX_PX, THUMBNAIL_MAX_PX)) {
                    is MediaDownload.Done -> if (download.file.renameTo(target) || target.isFile) target else null
                    MediaDownload.Broken -> null
                    else -> null.also { log("box art thumbnail: ${download.javaClass.simpleName}") }
                }
            } finally {
                temp.delete()
            }
        }

    suspend fun pick(
        gameId: Long,
        hit: BoxArtHit,
        onSavingPictures: () -> Unit,
    ): BoxArtStoreOutcome =
        withContext(io) {
            val scraper = scraper() ?: return@withContext BoxArtStoreOutcome.Failed(BoxArtProblem.SignInAgain)
            if (!unmeteredNetwork()) return@withContext BoxArtStoreOutcome.Failed(BoxArtProblem.NoWifi)
            val request = source().personChoiceRequest(gameId) ?: return@withContext notReady(gameId)
            val result =
                when (val answer = scraper.byGameId(hit.id, request)) {
                    is ScrapeResult.Matched -> answer
                    ScrapeResult.NoMatch -> return@withContext BoxArtStoreOutcome.NotFound
                    is ScrapeResult.Paused -> return@withContext BoxArtStoreOutcome.Failed(pausedProblem(answer.resumeAtMillis, answer.reason))
                    is ScrapeResult.Unavailable -> return@withContext BoxArtStoreOutcome.Failed(unavailableProblem(scraper, answer.reason))
                    is ScrapeResult.Failed -> return@withContext BoxArtStoreOutcome.Failed(failureProblem(answer.reason))
                }
            val pictures = RunPictures(scraper, mediaDir(), ScrapeMediaSizes.all, lowRamDevice(), log)
            onSavingPictures()
            val front =
                when (val saved = pictures.save(result, MediaKind.BoxFront, fresh = true)) {
                    is PictureOutcome.Saved -> saved.media
                    else -> return@withContext pictureFailure(gameId, scraper, saved)
                }
            // The person picked this game for its box art: one with none (none listed, every one
            // broken, none saved before) is not stored; another game or No box art can be picked.
            if (front.isEmpty()) {
                log("box art pick: game $gameId has no box front that could be saved")
                return@withContext BoxArtStoreOutcome.NoBoxFront
            }
            val support =
                when (val saved = pictures.save(result, MediaKind.Support, fresh = true)) {
                    is PictureOutcome.Saved -> saved.media
                    else -> pictures.savedBefore(result.match.screenScraperGameId, MediaKind.Support)
                }
            store(gameId, PersonChoice.Picked(result, front + support))
        }

    suspend fun noBoxArt(gameId: Long): BoxArtStoreOutcome =
        withContext(io) { store(gameId, PersonChoice.NoBoxArt) }

    /**
     * Begun, the write and the naming it asks for are finished even when the person leaves the
     * panel meanwhile: both run inside [NonCancellable], so no cancel can come between the write
     * and the naming it asks for.
     */
    private suspend fun store(
        gameId: Long,
        choice: PersonChoice,
    ): BoxArtStoreOutcome = withContext(NonCancellable) { storeOutcome(gameId, storeChoice(source(), gameId, choice)) }

    private fun storeOutcome(
        gameId: Long,
        stored: PersonChoiceStore,
    ): BoxArtStoreOutcome =
        when (stored) {
            PersonChoiceStore.STORED -> BoxArtStoreOutcome.Stored
            PersonChoiceStore.STORED_TO_NAME_AGAIN -> {
                // The pick's name gives way to the identifier's now, not at the next scan.
                nameAgain()
                BoxArtStoreOutcome.Stored
            }
            PersonChoiceStore.GAME_GONE -> BoxArtStoreOutcome.GameGone.also { log("box art pick: game $gameId is gone") }
            PersonChoiceStore.NOT_NAMED_YET -> BoxArtStoreOutcome.NotNamedYet.also { log("box art pick: game $gameId is being named") }
        }

    private suspend fun notReady(gameId: Long): BoxArtStoreOutcome = if (gameExists(gameId)) BoxArtStoreOutcome.NotNamedYet else BoxArtStoreOutcome.GameGone

    private suspend fun pictureFailure(
        gameId: Long,
        scraper: Scraper,
        outcome: PictureOutcome,
    ): BoxArtStoreOutcome {
        log("box art pick: game $gameId pictures not saved (${outcome.javaClass.simpleName})")
        return when (outcome) {
            is PictureOutcome.Saved -> BoxArtStoreOutcome.Stored
            is PictureOutcome.Paused -> BoxArtStoreOutcome.Failed(pausedProblem(outcome.resumeAtMillis, outcome.reason))
            is PictureOutcome.Unavailable -> BoxArtStoreOutcome.Failed(unavailableProblem(scraper, outcome.reason))
            PictureOutcome.Network -> BoxArtStoreOutcome.Failed(BoxArtProblem.Offline)
            is PictureOutcome.Server -> BoxArtStoreOutcome.Failed(BoxArtProblem.ServerError(outcome.code))
            PictureOutcome.StorageFailed -> BoxArtStoreOutcome.StorageFailed
        }
    }

    private fun pausedProblem(
        untilMillis: Long,
        reason: PauseReason,
    ): BoxArtProblem =
        when (reason) {
            PauseReason.DailyQuota -> BoxArtProblem.DailyLimit(untilMillis)
            PauseReason.ServerClosed -> BoxArtProblem.ServerClosed(untilMillis)
        }

    private suspend fun unavailableProblem(
        scraper: Scraper,
        reason: UnavailableReason,
    ): BoxArtProblem {
        log("box art: ScreenScraper unavailable ($reason)")
        return when (reason) {
            UnavailableReason.BadUserCredentials -> {
                markRefused(scraper)
                BoxArtProblem.SignInAgain
            }
            UnavailableReason.NoDeveloperCredentials, UnavailableReason.BadDeveloperCredentials, UnavailableReason.SoftwareBlocked -> BoxArtProblem.NotAvailable
        }
    }

    private fun failureProblem(reason: FailureReason): BoxArtProblem =
        when (reason) {
            FailureReason.Network -> BoxArtProblem.Offline
            is FailureReason.Server -> BoxArtProblem.ServerError(reason.code)
            // A pick and a search never read the ROM; seen only if that changes.
            FailureReason.RomUnreadable -> BoxArtProblem.NotRead
        }

    companion object {
        /**
         * A thumbnail's longest side in pixels: a row's picture is at most one 40 dp row high plus
         * its note line (about 56 dp), 128 px on the Thor (2.29 px per dp); 160 leaves room and is
         * a few KB per game.
         */
        const val THUMBNAIL_MAX_PX = 160

        private const val THUMBNAIL_PREFIX = "thumb-"

        /**
         * One file per game and picture: the hash of the URL and the md5 tells two regions' fronts
         * apart, and a new picture behind the same `mediaJeu.php` URL (its md5 changes); nothing
         * secret is in either.
         */
        internal fun thumbnailName(
            gameId: String,
            url: String,
            md5: String?,
        ): String = "${gameId.filter(Char::isLetterOrDigit)}-${Integer.toHexString("$url|${md5.orEmpty()}".hashCode())}.img"
    }
}

/**
 * The process's Fix box art…, wired to the account ([AppGraph.scraperAccounts]), the device's
 * network ([deviceConditionsOf]) and memory, the run's library source and media folder, and Back to
 * automatic's naming pass ([GraphNameFixes.namingNow]).
 */
internal fun boxArtFixesFor(
    context: Context,
    graph: AppGraph,
    source: () -> LibraryScrapeSource,
    mediaDir: () -> File,
    thumbnailDir: () -> File,
    io: CoroutineDispatcher,
    log: (String) -> Unit,
): GraphBoxArtFixes {
    val app = context.applicationContext
    val accounts = graph.scraperAccounts
    return GraphBoxArtFixes(
        scraper = { accounts.scraper() },
        markRefused = { accounts.markRefused(it) },
        isSignedIn = { accounts.isSignedIn.first() },
        unmeteredNetwork = { deviceConditionsOf(app).unmeteredNetwork },
        source = source,
        gameExists = { id -> graph.database.gameDao().game(id) != null },
        mediaDir = mediaDir,
        thumbnailDir = thumbnailDir,
        lowRamDevice = { RunPictures.isLowRamDevice(app) },
        nameAgain = { graph.nameFixes.namingNow() },
        io = io,
        log = log,
    )
}

private fun hitOf(hit: SearchHit): BoxArtHit = BoxArtHit(hit.screenScraperGameId, hit.name, hit.year, hit.publisher, hit.systemName, hit.boxFront)
