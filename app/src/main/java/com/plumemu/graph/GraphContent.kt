package com.plumemu.graph

import com.plumemu.library.scan.ContentReadBudget
import com.plumemu.library.scan.ContentReadResult
import kotlinx.coroutines.CancellationException

/**
 * The app's side of the content pass: when a run is
 * asked for ([scanEnded]) and what a worker gets ([work]). Nothing here is visible to the person.
 */
internal class GraphContent(
    private val hasRows: suspend () -> Boolean,
    private val read: suspend (ContentReadBudget) -> ContentReadResult,
    private val enqueue: () -> Unit,
    private val requestBoxArt: () -> Unit,
    private val log: (String) -> Unit,
    /** Normal lines of a run; [log] is for errors. */
    val info: (String) -> Unit = log,
    /** The content pass and the box-art run stop while a game is played and go on at the return. */
    val held: HeldForPlay = HeldForPlay(NoHeldWork, { false }),
) {
    /** After a scan: a background run only when files are left to read. A failure is logged, never thrown. */
    suspend fun scanEnded() {
        val rows = try {
            hasRows()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("content pass: could not check for files to read (${e.javaClass.simpleName})")
            return
        }
        if (rows) enqueue()
    }

    fun work(): ContentReadWork = ContentReadWork(read, requestBoxArt, log, info = info, holdForPlay = { held.holds(HeldWork.ContentPass) })
}

/** The app's [GraphContent]: reads through [scanner], asks for box art through [requestBoxArt] after a run. */
internal fun graphContentFor(
    context: android.content.Context,
    scanner: suspend () -> com.plumemu.library.scan.LibraryScanner?,
    requestBoxArt: () -> Unit,
    log: (String) -> Unit,
    info: (String) -> Unit,
    /** Whether a play is noted and not yet ended (`PlayTimeTracker.isPending`). */
    isPlaying: suspend () -> Boolean,
): GraphContent = GraphContent(
    hasRows = { scanner()?.hasContentToRead() == true },
    read = { budget -> scanner()?.readContent(budget) ?: ContentReadResult() },
    enqueue = { ContentReadScheduler.enqueue(context) },
    requestBoxArt = requestBoxArt,
    log = log,
    info = info,
    held = HeldForPlay(SharedPreferencesHeldWorkStore(context), isPlaying, log),
)
