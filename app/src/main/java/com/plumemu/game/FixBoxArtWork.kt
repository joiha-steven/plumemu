package com.plumemu.game

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The work of one open Fix box art… panel: its search, the thumbnails of the games
 * found, and the pick or "No box art". Everything slow runs in [actions] (off the main thread);
 * only [state] is written here, on [parent]'s dispatcher (the main thread in the app).
 *
 * - A second search cancels the first and its thumbnails; an answer for an older query is dropped
 *   ([FixBoxArtState.showSearch]).
 * - At most [MAX_THUMBNAILS_IN_FLIGHT] thumbnails download at once, each on its own; the list never
 *   waits for them, and a pick cancels the ones not done (the pick goes first through the gate).
 * - One pick at a time; while it is stored searches and other picks are ignored. When its picture
 *   save takes longer than [waitNoticeMillis] the state says it may be waiting for the background
 *   run.
 * - [cancel] (Back, START, the screen going away) stops everything not stored yet. The library's
 *   write itself, once begun, is finished by the graph (nothing is left half stored).
 * - Failures are values; an unexpected exception is logged by class and shown as [BoxArtProblem.NotRead].
 */
internal class FixBoxArtWork(
    private val game: GameRef,
    private val state: FixBoxArtState,
    private val actions: GameActions,
    parent: CoroutineScope,
    private val log: (String) -> Unit,
    private val waitNoticeMillis: Long = WAIT_NOTICE_MILLIS,
    /** Once stored: the confirmation for the picked game, or null for No box art. Main thread. */
    private val onStored: (BoxArtHit?) -> Unit,
) {
    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = parent + job
    private var search: Job? = null
    private var thumbnails: Job? = null
    private var pick: Job? = null
    private val thumbnailSlots = Semaphore(MAX_THUMBNAILS_IN_FLIGHT)

    val isPicking: Boolean get() = pick?.isActive == true

    fun search(query: String) {
        if (!job.isActive || isPicking) return
        search?.cancel()
        thumbnails?.cancel()
        state.searching(query)
        search =
            scope.launch {
                val outcome =
                    try {
                        actions.searchBoxArt(game.key, query)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log("box art search: game ${game.key} failed (${e.javaClass.simpleName})")
                        BoxArtSearchOutcome.Failed(BoxArtProblem.NotRead)
                    }
                state.showSearch(query, outcome)
                if (outcome is BoxArtSearchOutcome.Found) loadThumbnails(outcome.hits)
            }
    }

    private fun loadThumbnails(hits: List<BoxArtHit>) {
        thumbnails =
            scope.launch {
                val batch = coroutineContext.job
                for (hit in hits) {
                    if (hit.thumbnail == null) continue
                    launch {
                        thumbnailSlots.withPermit {
                            // A cancelled batch frees its slots one by one: a waiting download must not start then.
                            if (!batch.isActive) return@withPermit
                            val file =
                                try {
                                    actions.boxArtThumbnail(hit)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    // The row shows no picture; the pick still works.
                                    log("box art thumbnail: game ${game.key} failed (${e.javaClass.simpleName})")
                                    null
                                }
                            if (file != null) state.thumbnailReady(hit.id, file)
                        }
                    }
                }
            }
    }

    /** Stores [hit] (fetched by id, its pictures saved) as the person's choice. */
    fun pick(hit: BoxArtHit) = store(hit) { onPictures -> actions.pickBoxArt(game.key, hit, onPictures) }

    /** Stores "No box art" as the person's choice. */
    fun noBoxArt() = store(null) { actions.storeNoBoxArt(game.key) }

    private fun store(
        hit: BoxArtHit?,
        block: suspend (onPictures: () -> Unit) -> BoxArtStoreOutcome,
    ) {
        if (!job.isActive || isPicking) return
        search?.cancel()
        thumbnails?.cancel()
        state.storing()
        pick =
            scope.launch {
                // The notice is told from the graph's thread; launched here, it runs on this scope's.
                val notices = Job(coroutineContext[Job])
                val outcome =
                    try {
                        block {
                            launch(notices) {
                                delay(waitNoticeMillis)
                                state.waitingForPictures()
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log("box art pick: game ${game.key} could not be stored (${e.javaClass.simpleName})")
                        BoxArtStoreOutcome.Failed(BoxArtProblem.NotRead)
                    } finally {
                        notices.cancel()
                    }
                if (!isActive) return@launch
                state.stored(outcome, hit)
                if (outcome == BoxArtStoreOutcome.Stored) {
                    onStored(hit)
                } else {
                    // The list is shown again: the thumbnails the pick cancelled come back.
                    loadThumbnails(state.hits.filter { state.thumbnailOf(it.id) == null })
                }
            }
    }

    fun cancel() {
        job.cancel()
    }

    companion object {
        /** Two at once: the account's thread limit is often one or two, and the pick must not wait long behind them. */
        const val MAX_THUMBNAILS_IN_FLIGHT = 2

        /** A picture save longer than this is said to be possibly waiting for the background run. */
        const val WAIT_NOTICE_MILLIS = 3_000L
    }
}
