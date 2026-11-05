package com.plumemu.coredata.db

/**
 * Games one scan step found, written in one transaction by [ScanDao.applyBatch]. Nothing is
 * deleted here: deletions wait for [ScanFinish], so a file that moved can keep its row.
 */
data class ScanBatch(
    val folderId: Long,
    val systems: List<SystemEntity> = emptyList(),
    val newGames: List<GameEntity> = emptyList(),
    val changedGames: List<StoredGame> = emptyList(),
    /**
     * Written as `added_at` on the rows this batch inserts (the scan's clock); never on a stored
     * row. Null before setup is finished and in the first scan after Reset library: those games are
     * the library itself, not new to it.
     */
    val addedAt: Long? = System.currentTimeMillis(),
)

/**
 * What a scan writes at its end, in one transaction by [ScanDao.finishScan].
 *
 * The library scanner writes directory records only here, so a scan that stops halfway keeps the
 * old records and the next rescan lists those directories again.
 */
data class ScanFinish(
    val folderId: Long,
    /** A file that moved or was renamed: the new row's place is moved onto the old row, which keeps
     * its id (and so its favourite, hidden mark, play time and emulator choice); the new row goes. */
    val moves: List<GameMove> = emptyList(),
    /** A row whose file is now part of another game's row (a later disc now grouped with the
     * first, discs an `.m3u` now stands for): its marks join that row, then it goes. */
    val folds: List<GameFold> = emptyList(),
    val removedGameIds: List<Long> = emptyList(),
    /** Relative paths of directories that disappeared; their directory records go. */
    val removedDirectoryPaths: List<String> = emptyList(),
    val directories: List<ScannedDirectoryEntity> = emptyList(),
    /** The files the listed directories left out (Settings › Library › Files not added); null writes none. */
    val leftOut: LeftOutWrite? = null,
)

/**
 * [ontoNewLaunchRow]: the old row moves onto a new row that now launches its game (an
 * `.m3u` with another name took over the discs); the old row keeps its hidden mark and the new
 * row's "Not a game" fix stays behind (`ScanDao.moveOntoNewLaunchRow`).
 */
data class GameMove(val keptGameId: Long, val newRow: StoredGame, val ontoNewLaunchRow: Boolean = false)

data class GameFold(val keptGameId: Long, val foldedGameId: Long)
