package com.plumemu.scraper.work

import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.PauseReason
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.UnavailableReason
import com.plumemu.scraper.media.MediaSaver
import com.plumemu.scraper.media.BoxFrontRequest
import com.plumemu.scraper.media.MediaSize
import com.plumemu.scraper.media.SavedMedia
import com.plumemu.scraper.timing.GameTiming
import com.plumemu.scraper.timing.msSince
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** What [RunPictures.save] did for one kind of picture. */
public sealed interface PictureOutcome {
    /** The files saved (or kept) at every size; empty when the game has none that works. */
    public data class Saved(val media: List<SavedMedia>) : PictureOutcome

    /** Nothing more can be asked before [resumeAtMillis], for [reason] (the daily quota, the server closed). */
    public data class Paused(val resumeAtMillis: Long, val reason: PauseReason) : PictureOutcome

    /** The scraper cannot work until something changes (a build, the person's sign-in). */
    public data class Unavailable(val reason: UnavailableReason) : PictureOutcome

    /** No connection. */
    public data object Network : PictureOutcome

    /** The picture server answered [code]; asking again later may work. */
    public data class Server(val code: Int) : PictureOutcome

    /** The media folder cannot be written (no room, no permission). Logged. */
    public data object StorageFailed : PictureOutcome
}

/**
 * Downloads and saves a matched game's pictures at every named size: the one way pictures are
 * saved, used by the background run ([ScrapeRun]) and by the person's own pick ("Fix box art..."), so both save the same files at the same sizes.
 *
 * Saves of every [RunPictures] of the process take turns (one lock): a pick and a run saving the
 * same game must not write over each other's temp files. The lock covers the reuse check and the
 * decode, downsize and write (about 50 ms a size), not the download: the run fetches several
 * games' pictures at once, and a person's pick waits only for a save, never
 * for a download. A download's temp file is registered while it is written, so a run's start
 * removes only the leftovers of a killed process, never a download in flight.
 *
 * @property mediaDir where pictures go: `<mediaDir>/<game id>/<kind>-<size>.webp`.
 * @property sizes the named sizes each picture is saved at; empty saves no picture.
 */
public class RunPictures internal constructor(
    private val scraper: Scraper,
    private val mediaDir: File,
    private val sizes: List<MediaSize>,
    private val saver: MediaSaver,
    private val log: (String) -> Unit,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /** @param lowRamDevice decodes in `RGB_565` when true; ask the device with [isLowRamDevice]. */
    public constructor(
        scraper: Scraper,
        mediaDir: File,
        sizes: List<MediaSize>,
        lowRamDevice: Boolean,
        log: (String) -> Unit,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(scraper, mediaDir, sizes, MediaSaver(mediaDir, lowRamDevice, log), log, ioDispatcher)

    /**
     * The first picture of [kind] in [result] that downloads and decodes, saved at every size;
     * none is fine ([PictureOutcome.Saved] with no media). Files saved earlier are reused,
     * unless [fresh]: then the picture is downloaded again and each new file replaces the old one
     * only once all sizes are written ([MediaSaver.save]); when no new picture is saved (none
     * listed, every one broken) the old files are kept and given back. A stop (pause, network)
     * leaves them as they were too.
     */
    public suspend fun save(result: ScrapeResult.Matched, kind: MediaKind, fresh: Boolean): PictureOutcome {
        val gameId = result.match.screenScraperGameId
        if (sizes.isEmpty()) return PictureOutcome.Saved(emptyList())
        val game = currentCoroutineContext()[GameTiming]
        if (!fresh) {
            saves.withLock { saver.existing(gameId, kind, sizes) }?.let {
                game?.picture(kind.name, "reused")
                return PictureOutcome.Saved(it)
            }
        }
        return sharedDownload(result, gameId, kind, fresh, game)
    }

    /**
     * Two ROMs of one ScreenScraper game (USA and USA Rev 1) saving the same picture at once: the
     * second waits for the first's download and takes its outcome, so no second request goes out.
     * If the first's caller is cancelled or fails, the waiting one downloads itself.
     */
    private suspend fun sharedDownload(result: ScrapeResult.Matched, gameId: String, kind: MediaKind, fresh: Boolean, game: GameTiming?): PictureOutcome {
        val key = DownloadKey(mediaDir.path, gameId, kind, sizes)
        while (true) {
            val (shared, mine) = synchronized(sharing) {
                sharing[key]?.let { it to false } ?: (CompletableDeferred<PictureOutcome>().also { sharing[key] = it } to true)
            }
            if (!mine) {
                try {
                    return shared.await().also { game?.picture(kind.name, "shared") }
                } catch (e: DownloadGone) {
                    continue
                }
            }
            try {
                val outcome = downloadOrKeep(result, gameId, kind, fresh, game)
                shared.complete(outcome)
                game?.picture(kind.name, timingLabel(outcome))
                return outcome
            } catch (e: Throwable) {
                shared.completeExceptionally(DownloadGone())
                throw e
            } finally {
                synchronized(sharing) { if (sharing[key] === shared) sharing.remove(key) }
            }
        }
    }

    /** The download, and for a fresh request without a new picture the files saved before. */
    private suspend fun downloadOrKeep(result: ScrapeResult.Matched, gameId: String, kind: MediaKind, fresh: Boolean, game: GameTiming?): PictureOutcome {
        val downloaded = download(result, gameId, kind, game)
        if (fresh && downloaded is PictureOutcome.Saved && downloaded.media.isEmpty()) {
            saves.withLock { savedBefore(gameId, kind) }.takeIf { it.isNotEmpty() }?.let {
                log("scrape run: game $gameId has no new $kind picture; the saved one is kept")
                return PictureOutcome.Saved(it)
            }
        }
        return downloaded
    }

    /** One word for the timing line. */
    private fun timingLabel(outcome: PictureOutcome): String = when (outcome) {
        is PictureOutcome.Saved -> if (outcome.media.isEmpty()) "none" else "saved"
        is PictureOutcome.Paused -> "paused"
        is PictureOutcome.Unavailable -> "unavailable"
        PictureOutcome.Network -> "network"
        is PictureOutcome.Server -> "server-${outcome.code}"
        PictureOutcome.StorageFailed -> "storage-failed"
    }

    /** The files saved earlier for [kind] of game [gameId] at every size, or none. */
    public fun savedBefore(gameId: String, kind: MediaKind): List<SavedMedia> =
        if (sizes.isEmpty()) emptyList() else saver.existing(gameId, kind, sizes).orEmpty()

    /**
     * A process killed mid-download leaves its temp file behind; removed between saves. A download
     * of this process in flight (a person's pick while a run starts) is registered and left alone.
     */
    public suspend fun removeLeftoverDownloads() {
        saves.withLock {
            withContext(ioDispatcher) {
                synchronized(downloading) {
                    mediaDir.listFiles { file ->
                        file.name.startsWith(DOWNLOAD_PREFIX) && file.name.endsWith(DOWNLOAD_SUFFIX) && file.name !in downloading
                    }?.forEach { it.delete() }
                }
            }
        }
    }

    private suspend fun download(result: ScrapeResult.Matched, gameId: String, kind: MediaKind, game: GameTiming?): PictureOutcome {
        // Asked no bigger than the largest size saved: it fits every size at full quality. A box
        // front is also capped at BoxFrontRequest.MAX_HEIGHT and comes as a JPEG (the downloader
        // adds that); the saver never enlarges, so a "large" size is then at most that tall.
        val maxWidth = sizes.maxOf { it.maxWidth }
        val largest = sizes.maxOf { it.maxHeight }
        val maxHeight = if (kind == MediaKind.BoxFront) minOf(largest, BoxFrontRequest.MAX_HEIGHT) else largest
        for (ref in result.media.filter { it.kind == kind }.take(if (result.match.matchedBy == MatchedBy.Fallback) FALLBACK_PICTURE_ATTEMPTS else MAX_PICTURE_ATTEMPTS)) {
            val temp = tempFile() ?: return PictureOutcome.StorageFailed
            try {
                when (val download = scraper.download(ref, temp, maxWidth, maxHeight)) {
                    is MediaDownload.Done -> when (val saved = saves.withLock { savedTimed(game, kind) { saver.save(download.file, gameId, kind, sizes) } }) {
                        is MediaSaver.Saved.Files -> return PictureOutcome.Saved(saved.media)
                        MediaSaver.Saved.NotAnImage -> Unit
                        MediaSaver.Saved.StorageFailed -> return storageFailed(kind)
                    }
                    MediaDownload.Broken -> Unit
                    is MediaDownload.ServerError -> return PictureOutcome.Server(download.code)
                    is MediaDownload.Paused -> return PictureOutcome.Paused(download.resumeAtMillis, download.reason)
                    is MediaDownload.Unavailable -> return PictureOutcome.Unavailable(download.reason)
                    MediaDownload.NetworkDown -> return PictureOutcome.Network
                    MediaDownload.StorageFailed -> return storageFailed(kind)
                }
            } finally {
                synchronized(downloading) {
                    temp.delete()
                    downloading -= temp.name
                }
            }
        }
        return PictureOutcome.Saved(emptyList())
    }

    /** The decode, downsize and write of a downloaded picture, timed for the game's summary. */
    private suspend fun savedTimed(game: GameTiming?, kind: MediaKind, save: () -> MediaSaver.Saved): MediaSaver.Saved {
        val startedAt = System.nanoTime()
        try {
            return withContext(ioDispatcher) { save() }
        } finally {
            game?.saved(kind.name, msSince(startedAt))
        }
    }

    /**
     * No room or no permission in the media folder: every next game would fail the same way. The
     * run ends in a retry; a pick says it could not be stored.
     */
    private fun storageFailed(kind: MediaKind): PictureOutcome {
        log("scrape run: a $kind picture cannot be saved in the media folder; run ends, retried later")
        return PictureOutcome.StorageFailed
    }

    /** A new download file, registered in [downloading] in the same step it is made. */
    private suspend fun tempFile(): File? = withContext(ioDispatcher) {
        try {
            synchronized(downloading) {
                mediaDir.mkdirs()
                File.createTempFile(DOWNLOAD_PREFIX, DOWNLOAD_SUFFIX, mediaDir).also { downloading += it.name }
            }
        } catch (e: IOException) {
            log("scrape run: no room for a download in the media folder (${e.javaClass.simpleName})")
            null
        }
    }

    public companion object {
        /** Per kind, each try is a request on the daily quota; the preferred region comes first. */
        public const val MAX_PICTURE_ATTEMPTS: Int = 2

        /** A fallback source lists its pictures best first (Steam: portrait, its 2x, header), and a missing one is a 404, not a fault. */
        public const val FALLBACK_PICTURE_ATTEMPTS: Int = 3
        private const val DOWNLOAD_PREFIX = "download-"
        private const val DOWNLOAD_SUFFIX = ".part"

        /** One save at a time in the process (see the class). */
        private val saves = Mutex()

        /** Runs [block] holding the save lock, as a save does; for tests that show who waits for it. */
        internal suspend fun <T> holdingSaveLock(block: suspend () -> T): T = saves.withLock { block() }

        /** A picture being downloaded now, by any [RunPictures] of the process. */
        private data class DownloadKey(val mediaDir: String, val gameId: String, val kind: MediaKind, val sizes: List<MediaSize>)

        /** The downloads in flight, each with the outcome its waiters will share. */
        private val sharing = HashMap<DownloadKey, CompletableDeferred<PictureOutcome>>()

        /** The download a caller waited on ended without an outcome (its own caller was cancelled or failed). */
        private class DownloadGone : Exception()

        /** Names of this process's download files being written; also the monitor for making and removing them. */
        private val downloading = HashSet<String>()

        /** Whether the device counts as low on RAM (decode in `RGB_565`). */
        public fun isLowRamDevice(context: android.content.Context): Boolean = MediaSaver.isLowRamDevice(context)
    }
}
