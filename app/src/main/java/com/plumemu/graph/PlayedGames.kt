package com.plumemu.graph

import com.plumemu.coredata.db.PlayDao
import com.plumemu.coredata.db.PlayedCopy
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.identify.name.TitleCleaner
import com.plumemu.library.catalog.gameFolderCopyGroup
import com.plumemu.library.naming.arcadeClonesOf
import com.plumemu.library.naming.arcadeGameOf
import com.plumemu.library.naming.arcadeParentOf
import com.plumemu.library.scan.GameNaming
import java.util.Locale

/*
 * Settings › Statistics counts a game by its group of regional copies: its
 * play time is the sum over every copy, it is shown under the copy the lists show (the primary),
 * and it counts as played once that sum reaches [PLAYED_SECONDS]. So time played on "Zelda (USA)"
 * stays when a rescan makes "Zelda (Europe)" the copy the lists show. Copies kept one game per
 * folder (`Zelda (USA)/…` beside `Zelda (Europe)/…`) group across their sibling folders, as the scan
 * groups them (library's `gameFolderCopyGroup`).
 */

/** A game counts as played from one minute in all its copies: opening a game and quitting at once is not playing it. */
internal const val PLAYED_SECONDS = 60L

/** One game as Statistics counts it: the copy the lists show (null when none of its copies is the primary) and the seconds of all its copies. */
internal data class PlayedGame(
    val shown: PlayedCopy?,
    val seconds: Long,
    /** Every copy's id, for the per-play figures (times played, last played) of the most-played games. */
    val copyIds: List<Long> = emptyList(),
) {
    /** The main screen's lists show it: its primary copy is neither hidden nor "Not a game". */
    val isListed: Boolean get() = shown != null && !shown.isHidden && !shown.isNotGame

    val isPlayed: Boolean get() = seconds >= PLAYED_SECONDS
}

/** Which arcade game a set belongs to, for [playedGamesOf] (the app: library's `arcadeGameOf` and `arcadeParentOf`). */
internal interface ArcadeGames {
    /** The game the set [fileName] of [systemId] belongs to (its group's parent short name), or null: grouped by file name. */
    fun gameOf(systemId: String, fileName: String): String?

    /** The parent short name of the set [fileName] of [systemId], or null; one lookup, cheaper than [gameOf]. */
    fun parentOf(systemId: String, fileName: String): String?

    /**
     * The short names of the sets whose parent is one of [parents], by parent (lower case), from one
     * pass over the pack; null when not known (then [parentOf] is asked per set).
     */
    fun clonesOf(parents: Set<String>): Map<String, List<String>>? = null

    /** No arcade grouping: every copy by file name. */
    object None : ArcadeGames {
        override fun gameOf(systemId: String, fileName: String): String? = null

        override fun parentOf(systemId: String, fileName: String): String? = null
    }
}

/**
 * [ArcadeGames] that looks each set up once per read: a played set is asked for twice (to pick its
 * game's sets, then to group them).
 */
private class OnceEach(private val games: ArcadeGames) : ArcadeGames {
    private val game = HashMap<String, String?>()
    private val parent = HashMap<String, String?>()

    override fun gameOf(systemId: String, fileName: String): String? = game.getOrPut("$systemId/$fileName") { games.gameOf(systemId, fileName) }

    override fun parentOf(systemId: String, fileName: String): String? = parent.getOrPut("$systemId/$fileName") { games.parentOf(systemId, fileName) }

    override fun clonesOf(parents: Set<String>): Map<String, List<String>>? = games.clonesOf(parents)
}

/**
 * Statistics' copies and how they group, in one read: [PlayDao.playedCopies] (copies by file sort
 * name), plus the arcade sets of [arcadeSystems] that the lists show for a played arcade game but
 * that have other file names (`dinou.zip` shown for a played `dino.zip`). Only a game none of whose
 * read copies is shown needs one: it is picked from the shown rows of its directory
 * ([PlayDao.shownRowsIn], id and file name) as its own short name or a clone of it (the clones of
 * every such game from one pass over the pack, [ArcadeGames.clonesOf]), and only the picked ones
 * are read in full. The figures are those of grouping every set of the directory: a set that is
 * not shown adds no time and is not the game's row. A folder of 10,000 sets with 300 played stays
 * in budget (`GraphStatisticsArcadeTest`).
 */
internal suspend fun playedGamesOf(dao: PlayDao, arcadeSystems: List<String>, arcade: ArcadeGames): List<PlayedGame> {
    val once = OnceEach(arcade)
    val copies = dao.playedCopies()
    val games = HashSet<List<Any>>()
    val shown = HashSet<List<Any>>()
    for (copy in copies) {
        if (copy.systemId !in arcadeSystems || (copy.totalSeconds == 0L && !copy.isPrimaryCopy)) continue
        val game = once.gameOf(copy.systemId, copy.fileName) ?: continue
        if (copy.totalSeconds > 0) games += placeOf(copy) + game
        if (copy.isPrimaryCopy) shown += placeOf(copy) + game
    }
    val waiting = games - shown
    if (waiting.isEmpty()) return playedGamesOf(copies, once)
    val known = copies.mapTo(HashSet()) { it.id }
    val parentOfClone = once.clonesOf(waiting.mapTo(HashSet()) { it.last() as String })
        ?.flatMap { (parent, clones) -> clones.map { it.lowercase(Locale.ROOT) to parent } }?.toMap()
    val picked = ArrayList<Long>()
    for (place in waiting.mapTo(LinkedHashSet()) { it.dropLast(1) }) {
        val (folderId, parentDocumentId, systemId) = place
        for (row in dao.shownRowsIn(folderId as Long, parentDocumentId as String, systemId as String)) {
            if (row.id in known) continue
            val own = ArcadeSystems.shortNameOf(row.fileName)
            val parent = if (parentOfClone != null) parentOfClone[own] else once.parentOf(systemId, row.fileName)
            if ((place + own) in waiting || (parent != null && (place + parent) in waiting)) picked += row.id
        }
    }
    return playedGamesOf(copies + dao.playedCopiesOf(picked), once)
}

/**
 * [copies] grouped as the cataloguer groups regional copies: one directory, one system, one file
 * sort name and one variant key, which is not stored and is read again from the file name the way
 * the scan reads it (library's `RenameMatcher` does the same). A copy alone in a game folder groups
 * with the copies in its sibling folders instead (library's `gameFolderCopyGroup`), so time on a
 * copy that rule hides counts under the one shown. Arcade sets of one game have
 * different file names (`dino.zip`, `dinou.zip`): a copy for which [arcade] names a game groups by
 * that game in its directory and system instead, as the naming pass groups them. Only games with a
 * played copy count, so an unplayed set is looked up in full only when its own short name or its
 * parent's is the game of a played set there.
 */
internal fun playedGamesOf(copies: List<PlayedCopy>, arcade: ArcadeGames = ArcadeGames.None): List<PlayedGame> {
    val playedGameOf = copies.filter { it.totalSeconds > 0 }.associateWith { arcade.gameOf(it.systemId, it.fileName) }
    val playedGames = playedGameOf.mapNotNullTo(HashSet()) { (copy, game) -> game?.let { placeOf(copy) + it } }
    // Copies of a file name no played copy has can join no played game by file name either: they
    // need no variant key (TitleCleaner costs more than the rest for thousands of arcade sets).
    // Any directory of the picked folder: copies kept one game per folder sit in sibling directories.
    val playedNames = playedGameOf.keys.mapTo(HashSet()) { listOf(it.folderId, it.systemId, it.fileSortName) }

    fun gameOf(copy: PlayedCopy): String? {
        if (copy.totalSeconds > 0) return playedGameOf[copy]
        val place = placeOf(copy)
        val own = ArcadeSystems.shortNameOf(copy.fileName)
        val mayBelong =
            (place + own) in playedGames || arcade.parentOf(copy.systemId, copy.fileName)?.let { (place + it) in playedGames } == true
        return if (mayBelong) arcade.gameOf(copy.systemId, copy.fileName) else null
    }
    return copies
        .groupBy { copy ->
            gameOf(copy)?.let { placeOf(copy) + listOf("arcade", it) }
                ?: if (copy.totalSeconds == 0L && listOf(copy.folderId, copy.systemId, copy.fileSortName) !in playedNames) {
                    listOf("unplayed", copy.id)
                } else {
                    gameFolderGroupOf(copy)
                        ?: listOf(copy.folderId, copy.parentDocumentId, copy.systemId, copy.fileSortName, variantKeyOf(copy.fileName))
                }
        }
        .values
        .map { group -> PlayedGame(group.firstOrNull { it.isPrimaryCopy }, group.sumOf { it.totalSeconds }, group.map { it.id }) }
}

private fun placeOf(copy: PlayedCopy): List<Any> = listOf(copy.folderId, copy.parentDocumentId, copy.systemId)

/** The group of [copy] when it is a game folder's only row (library's `gameFolderCopyGroup`), else null: grouped per directory. */
private fun gameFolderGroupOf(copy: PlayedCopy): List<Any>? =
    gameFolderCopyGroup(copy.systemId, copy.fileSortName, copy.fileName, copy.relativePath, copy.discCount, copy.rowsBeside, copy.isNotGame)
        ?.let { listOf(copy.folderId, "game folders") + it }

/**
 * [playedGamesOf]'s arcade grouping for the app: library's `arcadeGameOf` and `arcadeParentOf` on
 * [naming]'s packs, read only when Statistics is read (the packs are not opened before).
 */
internal fun arcadeGamesOf(naming: () -> GameNaming): ArcadeGames =
    object : ArcadeGames {
        override fun gameOf(systemId: String, fileName: String): String? =
            if (ArcadeSystems.isArcade(systemId)) arcadeGameOf(naming(), systemId, fileName) else null

        override fun parentOf(systemId: String, fileName: String): String? =
            if (ArcadeSystems.isArcade(systemId)) arcadeParentOf(naming(), systemId, fileName) else null

        override fun clonesOf(parents: Set<String>): Map<String, List<String>>? = arcadeClonesOf(naming(), parents)
    }

private fun variantKeyOf(fileName: String): String = TitleCleaner.clean(fileName.substringBeforeLast('.')).variantKey

/** The listed games played, longest first; a tie goes to the name the lists sort by, then to the id. */
internal fun mostPlayedOf(games: List<PlayedGame>): List<PlayedGame> =
    games
        .filter { it.isListed && it.isPlayed }
        .sortedWith(compareByDescending<PlayedGame> { it.seconds }.thenBy { it.shown?.sortName }.thenBy { it.shown?.id })
