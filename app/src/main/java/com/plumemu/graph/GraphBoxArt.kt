package com.plumemu.graph

import android.content.Context
import android.content.SharedPreferences
import com.plumemu.coredata.db.ScrapedGameDao
import com.plumemu.coredata.db.ScrapedPictures
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The saved box-art pictures as a thing the person manages (audit item 21;
 *): how much space they use, Settings'
 * "Clear box art…" and the daily sweep of pictures whose game is gone. The pictures live in
 * [mediaDir], one folder per ScreenScraper game id (`MediaSaver`); which ids are still needed is in
 * `scraped_game` ([ScrapedGameDao.idsWithPictures]).
 *
 * - [size]: the bytes, measured off the main thread, kept until a delivery, [clear] or a sweep
 *   changes them (a measure at most every [MEASURE_SPACING_MILLIS] while a run delivers).
 * - [clear]: stops the run, makes every automatic result forget its pictures, deletes the folders
 *   nothing needs any more. The person's own picks keep theirs; the account stays signed in.
 * - [sweepIfDue]: at most once a day, when a scan ends. Never touches a game that is only hidden
 *   (a hidden game is still a `game` row), a person's pick, or a folder younger than [MIN_AGE_MILLIS]
 *   (a run saves the files before it stores their row).
 */
internal class GraphBoxArt(
    private val mediaDir: () -> File,
    private val dao: () -> ScrapedGameDao,
    /** One more per picture stored by a run ([GraphScrapes.deliveryCount]). */
    private val deliveries: Flow<Int>,
    private val cancelRuns: () -> Unit,
    /** Held by every run while it works ([GraphScrapes.runLock]): taken here, no run saves a file or writes a row meanwhile. */
    private val runLock: Mutex,
    private val lastSweep: SweepMark,
    /** Takes the pictures from backup and restore for the whole of the work; false when one of them is using them. */
    private val hold: () -> Boolean,
    private val release: () -> Unit,
    private val runStopTimeoutMillis: Long = RUN_STOP_TIMEOUT_MILLIS,
    private val io: CoroutineDispatcher,
    private val main: CoroutineDispatcher = Dispatchers.Main,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit,
) {
    private val manualChanges = MutableStateFlow(0)

    @Volatile private var cached: Long? = null

    @Volatile private var cachedAtVersion = -1

    /** The space the pictures use; null until measured (or when the folder could not be read: logged). */
    val size: Flow<Long?> =
        flow {
            cached?.let { emit(it) }
            combine(manualChanges, deliveries) { manual, delivered -> manual + delivered }.conflate().collect { version ->
                if (cached == null || version != cachedAtVersion) {
                    val bytes = measure()
                    cached = bytes
                    cachedAtVersion = version
                    emit(bytes)
                    // A run delivers a picture a second: the walk is not repeated more often than this.
                    delay(MEASURE_SPACING_MILLIS)
                }
            }
        }

    /** Games given up on after server errors (Settings says so; Re-download all asks them again). */
    private val givenUp: Flow<Int> =
        flow { emitAll(dao().observeGivenUp()) }.catch { e ->
            if (e is CancellationException) throw e
            log("Box art: the given-up count could not be read (${e.javaClass.simpleName})")
            emit(0)
        }

    /** What Settings shows under the status line. */
    val facts: Flow<BoxArtFacts> = combine(size, givenUp, ::BoxArtFacts)

    private suspend fun measure(): Long? =
        withContext(io) {
            try {
                sizeOf(mediaDir())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Box art: the pictures' size could not be read (${e.javaClass.simpleName})")
                null
            }
        }

    /**
     * Settings' Clear box art. [onDone] gets, on the main thread, how it ended. The run is stopped
     * and waited for (its lock) before anything is deleted, so it cannot save a picture or write a row
     * meanwhile; it is not started again (Download missing does).
     */
    fun clear(onDone: (BoxArtClearResult) -> Unit) {
        scope.launch(io) {
            val result =
                try {
                    if (!hold()) {
                        log("Box art: not cleared, a backup or a restore is using the pictures")
                        BoxArtClearResult.InUse
                    } else {
                        try {
                            clearHeld()
                        } finally {
                            release()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("Box art: clearing failed (${e.javaClass.simpleName}: ${e.message})")
                    BoxArtClearResult.Failed
                }
            manualChanges.update { it + 1 }
            withContext(main) { onDone(result) }
        }
    }

    private suspend fun clearHeld(): BoxArtClearResult {
        cancelRuns()
        // The timeout is on getting the lock only: once it is ours, the deletion is never cut short.
        // (withTimeoutOrNull: its own timeout is not a cancellation of this job.)
        if (withTimeoutOrNull(runStopTimeoutMillis) { runLock.lock() } == null) {
            log("Box art: not cleared, the run did not stop in time")
            return BoxArtClearResult.RunStillGoing
        }
        try {
            val forgotten = dao().forgetPictures()
            val removed = deleteFoldersNotIn(foldersInUse(dao()), minAgeMillis = 0)
            log("Box art cleared: $forgotten results forgot their pictures, $removed folders deleted")
        } finally {
            runLock.unlock()
        }
        return BoxArtClearResult.Done
    }

    /**
     * The sweep, when a day has passed since the last one. Skipped while the library has no game at
     * all (a library being reset is empty for a moment, and everything would look orphaned), while a
     * run is going (it may be saving a picture whose row is not written yet), and while a backup or a
     * restore uses the pictures.
     *
     * @return the folders deleted, or null when it did not run.
     */
    suspend fun sweepIfDue(): Int? =
        withContext(io) {
            val now = clock()
            val last = lastSweep.get()
            // A stored time in the future (the clock was ahead once) is due, or the sweep would
            // wait for the date to catch up.
            if (now >= last && now - last < SWEEP_SPACING_MILLIS) return@withContext null
            if (!hold()) return@withContext null
            try {
                if (!runLock.tryLock()) return@withContext null
                try {
                    sweepHeld(now)
                } finally {
                    runLock.unlock()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Box art: the sweep failed (${e.javaClass.simpleName}: ${e.message})")
                null
            } finally {
                release()
            }
        }

    private suspend fun sweepHeld(now: Long): Int? {
        val dao = dao()
        if (!dao.hasAnyGame()) return null
        val rows = dao.deleteOrphanPictureRows()
        dao.deleteOrphanFailures()
        val removed = deleteFoldersNotIn(foldersInUse(dao), MIN_AGE_MILLIS)
        lastSweep.set(now)
        if (rows > 0 || removed > 0) {
            log("Box art sweep: $rows results of games no longer in the library, $removed picture folders deleted")
            manualChanges.update { it + 1 }
        }
        return removed
    }

    /**
     * The picture folders some row still needs: its ScreenScraper id, and the folder each stored picture
     * sits in (a box front from a fallback source is in `steam_<appid>`, under a row with a ScreenScraper id).
     */
    private suspend fun foldersInUse(dao: ScrapedGameDao): Set<String> =
        dao.idsWithPictures().toSet() + dao.allPictures().flatMap { ScrapedPictures.decode(it) }.mapNotNull { File(it.path).parentFile?.name }

    /** Deletes every picture folder of [mediaDir] (a game's number, or `<source>_<id>` of a fallback source: `steam_98765`) whose name is not in [keep] and that is older than [minAgeMillis]. */
    private fun deleteFoldersNotIn(keep: Set<String>, minAgeMillis: Long): Int {
        val now = clock()
        var deleted = 0
        for (folder in mediaDir().listFiles().orEmpty()) {
            if (!folder.isDirectory || folder.name in keep || !PICTURE_FOLDER.matches(folder.name)) continue
            if (now - folder.lastModified() < minAgeMillis) continue
            if (folder.deleteRecursively()) deleted++ else log("Box art: could not delete the pictures of game ${folder.name}")
        }
        return deleted
    }

    companion object {
        /** A game's folder: its number, or a fallback source's `<source>_<id>`; nothing else in the media folder is touched. */
        private val PICTURE_FOLDER = Regex("[0-9]+|[a-z]+_[A-Za-z0-9]+")

        /** The folder walk is not repeated more often than this while a run delivers. */
        const val MEASURE_SPACING_MILLIS = 3_000L

        /** Clear box art waits this long for a run to stop before it gives up and says so. */
        const val RUN_STOP_TIMEOUT_MILLIS = 30_000L

        const val SWEEP_SPACING_MILLIS = 24L * 60 * 60 * 1000

        /** A picture folder this young may belong to a run that has not stored its row yet. */
        const val MIN_AGE_MILLIS = 60L * 60 * 1000

        /** The bytes of every file under [folder]; 0 when it does not exist. */
        fun sizeOf(folder: File): Long = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

/** How Clear box art ended; each non-Done result has its own line in Settings. */
internal enum class BoxArtClearResult { Done, InUse, RunStillGoing, Failed }

/** What Settings shows about the box art on the device: the space it uses and the games given up on. */
internal data class BoxArtFacts(val bytes: Long?, val givenUp: Int)

/** When the sweep last ran, in epoch milliseconds (0: never). */
internal interface SweepMark {
    fun get(): Long

    fun set(millis: Long)
}

/** [SweepMark] in a small SharedPreferences file; read and written on the IO dispatcher. */
internal class SharedPreferencesSweepMark(context: Context, private val log: (String) -> Unit) : SweepMark {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun get(): Long = prefs.getLong(KEY, 0L)

    override fun set(millis: Long) {
        if (!prefs.edit().putLong(KEY, millis).commit()) log("Could not save the box art sweep time")
    }

    companion object {
        const val FILE_NAME = "plumemu.box.art"
        private const val KEY = "last_sweep"
    }
}

/** [AppGraph.boxArtFiles]: kept out of the graph file, which is at the code-length limit. */
internal fun graphBoxArt(app: Context, graph: AppGraph, mediaDir: () -> File, io: CoroutineDispatcher, log: (String) -> Unit) =
    GraphBoxArt(
        mediaDir = mediaDir,
        dao = { graph.database.scrapedGameDao() },
        deliveries = graph.scrapes.deliveryCount,
        cancelRuns = { graph.scrapes.cancelRuns() },
        runLock = graph.scrapes.runLock,
        lastSweep = SharedPreferencesSweepMark(app, log),
        hold = { holdFiles(graph) },
        release = {
            graph.restore.releaseFileWork()
            graph.backup.releaseFileWork()
        },
        io = io,
        scope = graph.appScope,
        log = log,
    )

/** Both are asked under their own locks, so neither a backup nor a restore can start between this check and its end. */
private fun holdFiles(graph: AppGraph): Boolean {
    if (!graph.backup.holdForFileWork()) return false
    if (graph.restore.holdForFileWork()) return true
    graph.backup.releaseFileWork()
    return false
}
