package com.plumemu.library.scan

/** What a scan reports while it runs. */
sealed interface ScanEvent {
    /** Sent after every batch written; the library list already shows these games. */
    data class Progress(
        val folderId: Long,
        val directoriesListed: Int,
        val filesSeen: Int,
        val gamesWritten: Int,
    ) : ScanEvent

    data class FolderFinished(val folderId: Long, val outcome: FolderOutcome) : ScanEvent

    /**
     * Sent once per folder whose scan stamped `added_at`, after its games were named: what the lists show of the games
     * this scan added, by system ([ScanStats.listedAddedBySystem]). Naming groups arcade copies and marks "Not a game",
     * so the count is final only now. Fold it into the folder's stats with [ScanStats.withListed].
     */
    data class AddedListed(val folderId: Long, val bySystem: Map<String, Int>) : ScanEvent

    /**
     * Sent after each page of names the identification pass wrote ([IdentifyPass]), after every
     * folder's walk; the lists already show the new names.
     */
    data class Named(val folderId: Long, val gamesNamed: Int, val gamesToName: Int) : ScanEvent
}

sealed interface FolderOutcome {
    data class Scanned(val stats: ScanStats) : FolderOutcome

    /** Nothing was read and nothing was deleted; the folder's games stay as they were. */
    data class Unavailable(val reason: UnavailableReason) : FolderOutcome

    /**
     * The picked folder was proven deleted ([FolderPresence]) and is forgotten the way Settings ›
     * ROM folders › Remove forgets one: its row, its [gamesRemoved] games (by system in
     * [removedBySystem]) with their favourites, play time and per-game choices, and its grant.
     */
    data class Deleted(val gamesRemoved: Int, val removedBySystem: Map<String, Int>) : FolderOutcome
}

enum class UnavailableReason {
    /** Access was revoked in Android's settings; the person must pick the folder again. */
    PERMISSION_REVOKED,

    /** The folder is gone, or its SD card is not inserted. */
    FOLDER_MISSING,

    /** The provider failed for another reason; see the log. */
    READ_FAILED,
}

data class ScanStats(
    val directoriesListed: Int,
    val directoriesUnchanged: Int,
    val filesSeen: Int,
    val gamesAdded: Int,
    val gamesUpdated: Int,
    val gamesRemoved: Int,
    val skippedJunk: Int,
    val skippedUnmatched: Int,
    /**
     * Files that can become games (not junk such as saves) whose own modified time was within
     * [FolderScan.RECENT_FILE_MS] of the scan: possibly still being copied. Their directories are read again by the next scan.
     */
    val filesStillWriting: Int = 0,
    /** [gamesAdded] by system id: the rows this scan inserted. Not what the home notice says: see [listedAddedBySystem]. */
    val addedBySystem: Map<String, Int> = emptyMap(),
    /** [gamesRemoved] by system id; empty when nothing was removed. */
    val removedBySystem: Map<String, Int> = emptyMap(),
    /**
     * Folders whose name matches no system that this scan found with files some system could open
     * and that were not listed before it (Settings › Library › Unknown folder; the home's notice).
     * Empty when the scan wrote nothing (a directory failed to list).
     */
    val newUnknownFolders: List<UnknownFolder> = emptyList(),
    /**
     * True when this scan stamped `game.added_at` on its new games (setup done and the library already held scanned games).
     * It is the one rule for "news": the home's "+N games" notice and the second screen's "N new this week" both count only such scans.
     */
    val stampedAddedAt: Boolean = false,
    /**
     * What the home's "+24 games · Nintendo 64" notice says, by system id: the games this scan inserted that a list
     * shows once naming is done (primary copies, not hidden, pending or "not a game": what the second screen's
     * "new this week" counts). Filled from [ScanEvent.AddedListed]; empty until then and when the scan stamped nothing.
     */
    val listedAddedBySystem: Map<String, Int> = emptyMap(),
) {
    fun withListed(event: ScanEvent.AddedListed) = copy(listedAddedBySystem = event.bySystem)
}

/** A folder right below a picked folder whose name matches no system: its [relativePath] and how many [files] some system could open. */
data class UnknownFolder(val relativePath: String, val files: Int)
