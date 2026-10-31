package com.plumemu.library.naming

import android.util.Log
import com.plumemu.coredata.db.CopyChoiceDao
import com.plumemu.coredata.db.GameCopyRow
import com.plumemu.coredata.db.IdentifyDao
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.identify.arcade.ArcadeLookup
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.identify.arcade.ArcadeTitles
import com.plumemu.identify.name.TitleCleaner
import com.plumemu.identify.pack.PackKind
import com.plumemu.library.catalog.GameFolderCopies
import com.plumemu.library.scan.GameNaming
import com.plumemu.library.scan.LOG_TAG
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * One copy of the game a row stands for (the game menu's Other versions). [regions] are region
 * codes (the shown ones, else the file name's); [arcadeDetails] the bracket texts of an arcade
 * set's description ("USA 930201"), empty for any other copy. For the screen only.
 */
data class GameCopy(
    val id: Long,
    val fileName: String,
    val regions: List<String>,
    val arcadeDetails: List<String>,
)

/**
 * Every copy of the game the row [shownId] stands for, by file name, the row itself included, and
 * [playedId], the copy A plays now (the person's latest choice among them, else the row's own).
 */
data class GameCopies(
    val shownId: Long,
    val copies: List<GameCopy>,
    val playedId: Long,
)

/**
 * The copies of [gameId]'s game, by the rules that made it one row, read again here because which files a row stands for is not stored:
 *
 * - **Consoles** (and arcade files the pack does not know): the rows of the same folder, directory
 *   and system with the same file sort name and the same variant key (identify's
 *   `TitleCleaner.clean(name).variantKey`, as Statistics' `PlayedGames` reads it), so a Rev 1 or a
 *   translation is another game.
 * - **A row alone in a game folder** (a directory below the picked folder with one row): the rows
 *   alone in the sibling folders of the same parent with the same system, file sort name and
 *   [GameFolderCopies.variantKey] (`PS2/Ape Escape (USA)/` and `PS2/Ape Escape (Europe)/`).
 * - **Arcade sets named by the pack** (`ARCADE_NAME`): the sets of the same folder, directory and
 *   system in the same [ArcadeCopies] group (the parent's short name, without bootlegs, hacks and
 *   clones of another title). Only the group's possible members are looked up in the pack (its
 *   clones of the parent), not every set of a large folder.
 *
 * Rows marked not a game are left out; hidden copies are listed. Null when [gameId] is gone. When
 * the arcade pack cannot answer, the row is its game's only copy (logged). The pack is read on the
 * I/O dispatcher; no file is opened.
 */
suspend fun copiesOf(database: PlumDatabase, naming: GameNaming?, gameId: Long): GameCopies? {
    val dao = database.copyChoiceDao()
    val own = dao.copyRow(gameId) ?: return null
    val copies: List<GameCopy> =
        when {
            own.isNotGame -> listOf(own.asCopy())
            own.idMethod == IdentifyDao.ARCADE_NAME && ArcadeSystems.isArcade(own.systemId) ->
                withContext(Dispatchers.IO) { arcadeCopiesOf(database, naming, own) }
            isInGameFolder(dao, own) -> {
                val variant = GameFolderCopies.variantKey(own.fileName, own.relativePath, own.discCount)
                val parent = own.relativePath.substringBeforeLast('/').substringBeforeLast('/', missingDelimiterValue = "")
                dao.gameFolderCopies(gameId, parentPrefix = if (parent.isEmpty()) "" else "$parent/", slashes = own.relativePath.count { it == '/' })
                    .filter { it.id == gameId || GameFolderCopies.variantKey(it.fileName, it.relativePath, it.discCount) == variant }
                    .map { it.asCopy() }
            }
            else -> {
                val variant = variantKeyOf(own.fileName)
                dao.fileNameCopies(gameId).filter { it.id == gameId || variantKeyOf(it.fileName) == variant }.map { it.asCopy() }
            }
        }
    val played = dao.choicesAmong(copies.map { it.id }).firstOrNull()?.gameId ?: gameId
    return GameCopies(gameId, copies, played)
}

/**
 * The row whose file a launch of [gameId] opens: the copy the person chose last among the game's
 * copies, else [gameId]'s own. One indexed read when nothing near the row is chosen (the usual
 * case), so the launch opens no pack for it. A failed lookup plays the row's own file (logged).
 */
suspend fun copyToPlay(database: PlumDatabase, naming: GameNaming?, gameId: Long): Long {
    if (!database.copyChoiceDao().hasChoiceNear(gameId)) return gameId
    return copiesOf(database, naming, gameId)?.playedId ?: gameId
}

/**
 * The person chose [copyId] in the game menu's Other versions of the row [gameId]: from now on A on
 * that row plays it ([copyToPlay]); [copyId] == [gameId] goes back to the row's own file. Nothing
 * else changes: the shown copy, the lists and every mark stay as they are.
 *
 * @return false when [copyId] is not a copy of [gameId]'s game (any more: a rescan between the
 *   menu and the choice); nothing is stored then.
 */
suspend fun chooseCopy(database: PlumDatabase, naming: GameNaming?, gameId: Long, copyId: Long, at: Long): Boolean {
    val copies = copiesOf(database, naming, gameId) ?: return false
    val ids = copies.copies.map { it.id }
    if (copyId !in ids) return false
    database.copyChoiceDao().choose(shownId = gameId, chosenId = copyId, copies = ids, at = at)
    return true
}

private fun GameCopyRow.asCopy(arcadeDetails: List<String> = emptyList()) =
    GameCopy(
        id = id,
        fileName = fileName,
        regions = regions.ifBlank { fileRegions }.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        arcadeDetails = arcadeDetails,
    )

/** [own] is alone in a directory below the picked folder: its copies are in sibling game folders ([GameFolderCopies]). */
private suspend fun isInGameFolder(dao: CopyChoiceDao, own: GameCopyRow): Boolean =
    own.relativePath.contains('/') && GameFolderCopies.applies(own.systemId) && dao.rowsBeside(own.id) == 1

private fun variantKeyOf(fileName: String): String = TitleCleaner.clean(fileName.substringBeforeLast('.')).variantKey

/** The sets of [own]'s arcade game in its place, [own] first if the pack cannot tell. */
private suspend fun arcadeCopiesOf(database: PlumDatabase, naming: GameNaming?, own: GameCopyRow): List<GameCopy> {
    if (naming == null) return listOf(own.asCopy())
    return try {
        val pack = naming.packs.packFor(ArcadeSystems.PACK_ID)?.takeIf { it.kind == PackKind.ARCADE }
        if (pack == null) {
            Log.w(LOG_TAG, "Game ${own.id}: arcade pack unavailable; its other versions are not listed")
            return listOf(own.asCopy())
        }
        val lookup = ArcadeLookup(pack)
        val ownSet = arcadeSetOf(lookup, own.fileName) ?: return listOf(own.asCopy())
        val key = ArcadeCopies.groupKey(ownSet)
        val members = buildSet {
            add(key)
            pack.arcadeClonesOf(setOf(key))[key].orEmpty().forEach { add(it.lowercase(Locale.ROOT)) }
        }
        val details = HashMap<Long, List<String>>()
        for (row in database.copyChoiceDao().arcadeSetsBeside(own.id)) {
            if (ArcadeSystems.shortNameOf(row.fileName) !in members) continue
            val set = arcadeSetOf(lookup, row.fileName) ?: continue
            if (row.id == own.id || ArcadeCopies.groupKey(set) == key) details[row.id] = ArcadeTitles.details(set.description)
        }
        details.getOrPut(own.id) { ArcadeTitles.details(ownSet.description) }
        database.copyChoiceDao().copyRows(details.keys.toList()).map { it.asCopy(details[it.id].orEmpty()) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        // A damaged pack (CorruptPackException): the row stands alone until it is reinstalled.
        Log.w(LOG_TAG, "Game ${own.id}: arcade versions not looked up (${e.javaClass.simpleName})")
        listOf(own.asCopy())
    }
}

/** What the pack says about the set [fileName] and its parent, as [arcadeGameOf] reads it; null when not a game of the pack. */
private fun arcadeSetOf(lookup: ArcadeLookup, fileName: String): ArcadeCopies.ArcadeSet? {
    val found = lookup.find(fileName)?.takeIf { !it.hide } ?: return null
    val parent = found.parent?.let { lookup.find(it) }
    return ArcadeCopies.ArcadeSet(ArcadeSystems.shortNameOf(fileName), found.title, found.parent, parent?.title)
}
