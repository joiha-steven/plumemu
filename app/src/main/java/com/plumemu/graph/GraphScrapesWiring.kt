package com.plumemu.graph

import android.content.Context
import com.plumemu.coredata.db.ArtAspectPass
import com.plumemu.coredata.db.ArtAspects
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.library.scrape.LibraryScrapeSource
import com.plumemu.scraper.PauseStore
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.ScraperConfig
import com.plumemu.scraper.work.ScrapeScheduler
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/** [AppGraph.scrapes]: kept out of the graph file, which is at the code-length limit. */
internal fun graphScrapesFor(
    app: Context,
    graph: AppGraph,
    librarySource: () -> LibraryScrapeSource,
    mediaDir: () -> File,
    retryMark: () -> RetryMark,
    pauseStore: () -> PauseStore,
    io: CoroutineDispatcher,
    log: (String) -> Unit,
) = GraphScrapes(
    accounts = graph.scraperAccounts,
    librarySource = librarySource,
    // Not backed up: pictures are large and the next run downloads them again.
    mediaDir = mediaDir,
    enqueue = { ScrapeScheduler.enqueue(app, wifiOnly = true) },
    cancel = { ScrapeScheduler.cancel(app) },
    inputs = deviceScrapeStatusInputs(app, { graph.database }, pauseStore),
    retryMark = retryMark,
    scope = graph.appScope,
    io = io,
    log = log,
    // Read from the store itself: a worker started right after Resume must see the write.
    paused = graph.settingsStore.settings.map { it.library.boxArtPaused }.distinctUntilChanged(),
    writePaused = { isPaused -> graph.settingsStore.update { it.copy(library = it.library.copy(boxArtPaused = isPaused)) } },
    clearBoxArt = { onDone -> graph.boxArtFiles.clear(onDone) },
    holdForPlay = { graph.content.held.holds(HeldWork.BoxArt) },
    chosenSource = { graph.settingsStore.readOnce().library.boxArtSource },
    writeSource = { source -> graph.settingsStore.update { it.copy(library = it.library.copy(boxArtSource = source)) } },
    makeLibretroScraper = { Scraper.createLibretroOnly(ScraperConfig(cacheDir = app.cacheDir)) },
    sourceChanges = graph.settingsStore.settings.map { it.library.boxArtSource }.distinctUntilChanged(),
).also {
    // Reset all settings, a choice or a sign-out that leaves own covers in force stop the run.
    it.stopWhenOwnCovers(graph.settingsStore.settings.map { s -> s.library.boxArtSource }, graph.scraperAccounts.isSignedIn)
}

/**
 * Whether the main screen shows box art (grid, details, live pictures): an account is stored, or
 * "Box art only" is chosen (libretro's pictures need no account). Plumemu's own covers with no account
 * keep it off, as before.
 */
internal fun boxArtShown(isSignedIn: Flow<Boolean>, settings: Flow<PlumSettings?>): Flow<Boolean> =
    combine(isSignedIn, settings.filterNotNull().map { it.library.boxArtSource }.distinctUntilChanged()) { signedIn, source ->
        signedIn || source == BoxArtSourceSetting.LibretroOnly
    }.distinctUntilChanged()

/**
 * Keeps each game's stored shape in step with its saved art while collected with the home content, so it stops with the screen and
 * the first pass after coming back catches up with a run that went on without it. Emits nothing. A failed pass is logged and the
 * passes start again [retryMillis] later for as long as this is collected, as `LibraryHomeContent` does for the system shapes: rows
 * written so far stand, the next pass writes the rest, and until then the grid has the live pictures. Each pass logs its cost.
 */
internal fun keepArtAspects(
    hasBoxArt: Flow<Boolean>,
    passes: Flow<ArtAspectPass>,
    log: (String) -> Unit,
    info: (String) -> Unit,
    retryMillis: Long = ART_ASPECTS_RETRY_MILLIS,
): Flow<Nothing> =
    flow {
        try {
            hasBoxArt.distinctUntilChanged().collectLatest { on ->
                if (on) {
                    passes.retryWhen { e, _ ->
                        if (e is CancellationException || e !is Exception) throw e
                        log(failureLine(e))
                        delay(retryMillis)
                        true
                    }.collect { info("Box art shapes: ${it.rowsWritten} rows written, read ${it.readMillis} ms, write ${it.writeMillis} ms") }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(failureLine(e))
        }
    }

private fun failureLine(e: Exception) = "Box art shapes could not be stored (${e.javaClass.simpleName})"

private const val ART_ASPECTS_RETRY_MILLIS = 30_000L

/** The passes on the app's picture sizes; [database] is opened when they are collected. */
internal fun artAspectPasses(database: () -> PlumDatabase): Flow<ArtAspectPass> =
    flow { emitAll(ArtAspects.follow(database(), ScrapeMediaSizes.Grid.name, ScrapeMediaSizes.Large.name)) }
