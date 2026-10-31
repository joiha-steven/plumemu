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
    /** [gamesAdded] by system id: what the home's "+24 games · Nintendo 64" notice says. Empty when nothing was added. */
    val addedBySystem: Map<String, Int> = emptyMap(),
    /** [gamesRemoved] by system id; empty when nothing was removed. */
    val removedBySystem: Map<String, Int> = emptyMap(),
    /**
     * Folders whose name matches no system that this scan found with files some system could open
     * and that were not listed before it (Settings › Library › Unknown folder; the home's notice).
     * Empty when the scan wrote nothing (a directory failed to list).
     */
    val newUnknownFolders: List<UnknownFolder> = emptyList(),
)

/** A folder right below a picked folder whose name matches no system: its [relativePath] and how many [files] some system could open. */
data class UnknownFolder(val relativePath: String, val files: Int)
