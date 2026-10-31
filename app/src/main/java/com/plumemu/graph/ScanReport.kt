package com.plumemu.graph

import com.plumemu.library.scan.FolderOutcome
import com.plumemu.library.scan.ScanEvent
import com.plumemu.library.scan.UnavailableReason

/**
 * What Settings › Rescan library shows about the scan: counts that grow while it
 * runs, then what it did. Built from the scan's own events by [ScanReporter]; no total is known
 * before the walk, so none is shown.
 */
data class ScanReport(
    /** What the running pass is doing now; [ScanPhase.Idle] when none runs. */
    val phase: ScanPhase = ScanPhase.Idle,
    /** Files seen so far in every folder's walk. */
    val filesSeen: Int = 0,
    /** Games named so far, and how many are to be named in all: 0 while not yet known (see [ScanReporter]). */
    val gamesNamed: Int = 0,
    val gamesToName: Int = 0,
    /**
     * Ids of the folders whose walk has ended (read, or found unavailable). Naming starts after the last
     * one, so first-run setup knows the games are all in the database once it holds every folder it shows.
     */
    val walkedFolderIds: Set<Long> = emptySet(),
    /** How the last pass that ended went; kept until [StartupWork.clearScanResult]. */
    val result: ScanResult? = null,
)

enum class ScanPhase {
    Idle,

    /** A pass started and no event arrived yet. */
    Starting,

    /** Walking folders. */
    Reading,

    /** Naming the games a walk wrote. */
    Naming,
}

/** One pass's outcome, summed over its folders. */
data class ScanResult(
    /** The scan could not start at all (platform data or scanner could not be set up). */
    val failed: Boolean = false,
    val scannedFolders: Int = 0,
    val gamesAdded: Int = 0,
    val gamesRemoved: Int = 0,
    val unavailable: List<UnavailableFolder> = emptyList(),
)

data class UnavailableFolder(val folderId: Long, val reason: UnavailableReason)

/** Folds one pass's [ScanEvent]s into a [ScanReport]; not thread-safe, one pass at a time. */
class ScanReporter {
    private val files = HashMap<Long, Int>()
    private val named = HashMap<Long, Int>()
    private val toName = HashMap<Long, Int>()
    private var phase = ScanPhase.Starting
    private var scanned = 0
    private var added = 0
    private var removed = 0
    private val unavailable = ArrayList<UnavailableFolder>()

    /** Folders whose walk ended, in order: naming goes through them in the same order, after every walk. */
    private val walked = ArrayList<Long>()

    /** Folders proven deleted and forgotten: their walk ended too, but they are never named. */
    private val deleted = ArrayList<Long>()
    private var namingFolder: Long? = null

    /** The report to publish after [event]; the result stays null until [finished]. */
    fun onEvent(event: ScanEvent): ScanReport {
        when (event) {
            is ScanEvent.Progress -> {
                files[event.folderId] = event.filesSeen
                phase = ScanPhase.Reading
            }
            is ScanEvent.Named -> {
                named[event.folderId] = event.gamesNamed
                toName[event.folderId] = event.gamesToName
                namingFolder = event.folderId
                phase = ScanPhase.Naming
            }
            is ScanEvent.FolderFinished ->
                when (val outcome = event.outcome) {
                    is FolderOutcome.Scanned -> {
                        files[event.folderId] = outcome.stats.filesSeen
                        scanned++
                        walked += event.folderId
                        added += outcome.stats.gamesAdded
                        removed += outcome.stats.gamesRemoved
                    }
                    is FolderOutcome.Unavailable -> unavailable += UnavailableFolder(event.folderId, outcome.reason)
                    // Proven deleted and forgotten: its games count as removed; the folder is no longer listed, so not unavailable.
                    is FolderOutcome.Deleted -> {
                        removed += outcome.gamesRemoved
                        deleted += event.folderId
                    }
                }
        }
        return running()
    }

    /** The report of a pass that has begun, before any event. */
    fun running(): ScanReport =
        ScanReport(phase = phase, filesSeen = files.values.sum(), gamesNamed = named.values.sum(), gamesToName = if (toNameIsFinal()) toName.values.sum() else 0, walkedFolderIds = walked.toSet() + unavailable.map { it.folderId } + deleted)

    /**
     * The total to name is known only once the last folder walked is the one being named: a later
     * folder may still add games to name, so before that "830 of 500" style totals would grow mid-run.
     */
    private fun toNameIsFinal() = namingFolder != null && namingFolder == walked.lastOrNull()

    /** The report once the pass ended; [ok] false when the scan could not run. */
    fun finished(ok: Boolean): ScanReport =
        ScanReport(result = ScanResult(failed = !ok, scannedFolders = scanned, gamesAdded = added, gamesRemoved = removed, unavailable = unavailable.toList()))
}
