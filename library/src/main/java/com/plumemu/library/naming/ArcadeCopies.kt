package com.plumemu.library.naming

import android.util.Log
import androidx.room.withTransaction
import com.plumemu.coredata.db.ArcadeCopyRow
import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.identify.Identification
import com.plumemu.identify.IdentifyRequest
import com.plumemu.identify.Method
import com.plumemu.identify.arcade.ArcadeLookup
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.identify.arcade.ArcadeTitles
import com.plumemu.identify.name.Region
import com.plumemu.identify.pack.PackKind
import com.plumemu.library.catalog.RegionPreference
import com.plumemu.library.scan.GameNaming
import com.plumemu.library.scan.LOG_TAG
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Arcade sets of one game in one directory show as one row, as regional copies of a console game
 * do (arcade versions, step 2): `dino.zip` "Cadillacs and Dinosaurs (World 930201)" and its clone
 * `dinou.zip` "Cadillacs and Dinosaurs (USA 930201)" are one game. Every set stays a row; the ones
 * not shown get `is_primary_copy = 0`, which every system list filters. Pure: the naming pass
 * ([com.plumemu.library.scan.IdentifyPass]) reads the rows and the pack and writes the answer.
 *
 * **Who groups**: sets the pack knows, of one system in one directory, by the parent's short name
 * ([groupKey]). A clone joins its parent only when its description names no bootleg or hack
 * (whole words, any case) and its short title is the parent's (any case), so "Buster Bros. (USA)"
 * stays apart from its parent "Pang (World)" and "Out Run (bootleg)" from "Out Run". Clones of one
 * parent group with each other even when the parent's file is missing. A set whose parent the
 * pack does not know is a group of its own (with copies of the same short name, `x.zip` and
 * `x.7z`). Sets the pack does not know, and BIOS or device sets, are not this rule's: they keep
 * what the catalogue gave them (its regional-copy rule by file name).
 *
 * **Which copy shows** ([shownCopies]): one the person has not hidden; then one with the person's
 * marks (favourite, play time, a name fix); then the region order ([RegionPreference]); then the
 * parent set before a clone; then the shortest file name; then the file name. Nothing moves
 * between rows. Every copy hidden: the shown one is hidden too, so the game leaves the lists.
 */
internal object ArcadeCopies {
    /** What the pack says about one set, and about its parent when it has one. */
    data class ArcadeSet(
        val shortName: String,
        val description: String,
        val parentShortName: String?,
        /** The parent's description from the pack (its file need not be there); null when unknown. */
        val parentDescription: String?,
    )

    /** One arcade row; [set] is null when the pack does not know it. */
    data class Copy(
        val id: Long,
        val systemId: String,
        val directory: String,
        val fileName: String,
        val set: ArcadeSet?,
        val isHidden: Boolean,
        /** Favourite, play time or a person's name fix on this row. */
        val isMarked: Boolean,
    )

    /** What the arcade pack says about one short name, for grouping. */
    sealed interface Found {
        data class Known(val set: ArcadeSet) : Found

        /** The pack does not list it: its own row. */
        data object Unknown : Found

        /** A BIOS, device or other non-game set: never grouped, left as it is. */
        data object NotAGame : Found
    }

    /** The arcade pack did not answer a lookup (missing, or failing): nothing is grouped this time. */
    class NotLookedUp : Exception()

    /**
     * What the pack says about [shortName] and its parent, through [identify] (the identifier on a
     * short name of an arcade system; no file is opened). The parent's description is looked up
     * whether or not its file is there.
     *
     * @throws NotLookedUp when the pack did not answer.
     */
    fun find(shortName: String, identify: (String) -> Identification): Found {
        if (shortName.isEmpty()) return Found.Unknown
        val found = identify(shortName)
        if (found.packVersion == null) throw NotLookedUp()
        if (found.method != Method.ARCADE_NAME) return Found.Unknown
        if (found.hide) return Found.NotAGame
        val parent = found.parent?.let(identify)
        if (parent != null && parent.packVersion == null) throw NotLookedUp()
        val parentDescription = parent?.takeIf { it.method == Method.ARCADE_NAME }?.let { it.fullTitle ?: it.title }
        return Found.Known(ArcadeSet(shortName, found.fullTitle ?: found.title, found.parent, parentDescription))
    }

    /**
     * [row] as a copy of [set]. The person's marks: a favourite, play time, a name fix, a
     * ScreenScraper game picked for it, an emulator chosen for it.
     */
    fun copyOf(row: ArcadeCopyRow, set: ArcadeSet) = Copy(
        id = row.id,
        systemId = row.systemId,
        directory = row.parentDocumentId,
        fileName = row.fileName,
        set = set,
        isHidden = row.isHidden,
        isMarked = row.isFavorite || row.totalSeconds > 0 || row.nameTrust == NameTrustNames.PERSON_FIX ||
            row.hasScraperChoice || row.hasEmulatorChoice,
    )

    /** "bootleg" or "hack" as a whole word anywhere in a description, any case. */
    private val BOOTLEG_OR_HACK = Regex("\\b(bootleg|hack)\\b", RegexOption.IGNORE_CASE)

    fun isBootlegOrHack(description: String): Boolean = BOOTLEG_OR_HACK.containsMatchIn(description)

    /** The short name of the set whose group [set] belongs to: its parent's, or its own. */
    fun groupKey(set: ArcadeSet): String {
        val own = set.shortName.lowercase(Locale.ROOT)
        val parent = set.parentShortName?.lowercase(Locale.ROOT) ?: return own
        val parentDescription = set.parentDescription ?: return own
        if (isBootlegOrHack(set.description)) return own
        val sameTitle = ArcadeTitles.shortTitle(set.description).equals(ArcadeTitles.shortTitle(parentDescription), ignoreCase = true)
        return if (sameTitle) parent else own
    }

    /**
     * For each of [copies], whether it is the copy its group shows. A copy the pack does not know
     * is its own group, so it shows.
     */
    fun shownCopies(copies: List<Copy>, preferred: List<Region>): Map<Long, Boolean> {
        val shown = HashMap<Long, Boolean>()
        val known = copies.filter { it.set != null }
        copies.filter { it.set == null }.forEach { shown[it.id] = true }
        val groups = known.groupBy { Triple(it.systemId.lowercase(Locale.ROOT), it.directory, groupKey(it.set!!)) }
        for ((key, group) in groups) {
            val parent = key.third
            val best = group.minWith(
                compareBy<Copy>(
                    { it.isHidden },
                    { !it.isMarked },
                    { RegionPreference.rank(ArcadeTitles.regions(it.set!!.description), preferred) },
                    { if (it.set!!.shortName.lowercase(Locale.ROOT) == parent) 0 else 1 },
                    { it.fileName.length },
                    { it.fileName },
                ),
            )
            group.forEach { shown[it.id] = it === best }
        }
        return shown
    }
}

/**
 * The game the arcade set [fileName] of [systemId] belongs to, for counting copies together
 * (app's Settings › Statistics sums play time over a game's copies): the short name of its
 * group's parent, the key [ArcadeCopies] groups by, or null when [systemId] is no arcade system,
 * the pack does not list the set as a game (then the catalogue's file-name rule groups it, as in
 * the scan), or the pack cannot be read. Reads the set and its parent straight from the arcade
 * pack by short name (no identification is built: Statistics may ask for thousands); no file is
 * opened. Call it off the main thread (the first call may unpack the pack).
 */
fun arcadeGameOf(naming: GameNaming, systemId: String, fileName: String): String? {
    if (!ArcadeSystems.isArcade(systemId)) return null
    return try {
        val pack = naming.packs.packFor(ArcadeSystems.PACK_ID)?.takeIf { it.kind == PackKind.ARCADE } ?: return null
        val lookup = ArcadeLookup(pack)
        val shortName = ArcadeSystems.shortNameOf(fileName)
        val found = lookup.find(shortName)?.takeIf { !it.hide } ?: return null
        val parent = found.parent?.let { lookup.find(it) }
        ArcadeCopies.groupKey(ArcadeCopies.ArcadeSet(shortName, found.title, found.parent, parent?.title))
    } catch (e: RuntimeException) {
        // A damaged pack (CorruptPackException): counted by file name, as without the pack.
        null
    }
}

/**
 * The parent short name the arcade pack gives the set [fileName] of [systemId], from one lookup
 * (cheaper than [arcadeGameOf]); null when it has none, [systemId] is no arcade system, the pack
 * does not list it as a game or cannot be read. A set's game is its own short name or this
 * parent's, so Statistics asks [arcadeGameOf] only of sets that may belong to a played game.
 */
fun arcadeParentOf(naming: GameNaming, systemId: String, fileName: String): String? {
    if (!ArcadeSystems.isArcade(systemId)) return null
    return try {
        val pack = naming.packs.packFor(ArcadeSystems.PACK_ID)?.takeIf { it.kind == PackKind.ARCADE } ?: return null
        ArcadeLookup(pack).find(ArcadeSystems.shortNameOf(fileName))?.takeIf { !it.hide }?.parent?.lowercase(Locale.ROOT)
    } catch (e: RuntimeException) {
        null
    }
}

/**
 * The short names of the arcade sets whose parent is one of [parents], by parent (lower case),
 * from one pass over the arcade pack (identify's `NamePack.arcadeClonesOf`): Statistics finds the
 * sets that may belong to a played game without a lookup per set. Null when the pack cannot be read.
 */
fun arcadeClonesOf(naming: GameNaming, parents: Set<String>): Map<String, List<String>>? =
    try {
        naming.packs.packFor(ArcadeSystems.PACK_ID)?.takeIf { it.kind == PackKind.ARCADE }?.arcadeClonesOf(parents)
    } catch (e: RuntimeException) {
        null
    }

/**
 * Hides or shows again [gameId] and, when it is an arcade set the pack knows, every copy of its
 * arcade game ([ArcadeCopies]: same folder, directory, system and group), at once, in one
 * transaction: hiding the copy the lists show must not bring another copy of the game up at the
 * next scan. Any other game is the one row. When the pack does not answer, only [gameId] changes
 * (logged). No file is opened.
 *
 * @return the ids whose hidden mark was written; empty when the game is gone.
 */
suspend fun setHiddenWithArcadeCopies(database: PlumDatabase, naming: GameNaming?, gameId: Long, isHidden: Boolean): List<Long> {
    val ids = withContext(Dispatchers.IO) { arcadeCopiesOf(database, naming, gameId) }
    if (ids.isEmpty()) return emptyList()
    database.withTransaction { for (id in ids) database.gameDao().setHidden(id, isHidden) }
    return ids
}

/** [gameId] and the other copies of its arcade game; just [gameId] for any other game; empty when it is gone. */
private suspend fun arcadeCopiesOf(database: PlumDatabase, naming: GameNaming?, gameId: Long): List<Long> {
    val rows = database.identifyDao().copiesBesides(gameId)
    val own = rows.firstOrNull { it.id == gameId } ?: return if (database.gameDao().game(gameId) == null) emptyList() else listOf(gameId)
    if (naming == null || !ArcadeSystems.isArcade(own.systemId)) return listOf(gameId)
    val looked = HashMap<String, Identification>()
    val identify = { shortName: String ->
        looked.getOrPut(shortName) { naming.identifier.identify(IdentifyRequest(own.systemId, shortName, cleanedTitle = null)) }
    }
    return try {
        fun keyOf(row: ArcadeCopyRow): String? =
            (ArcadeCopies.find(ArcadeSystems.shortNameOf(row.fileName), identify) as? ArcadeCopies.Found.Known)?.let { ArcadeCopies.groupKey(it.set) }
        val key = keyOf(own) ?: return listOf(gameId)
        rows.filter { it.id == gameId || keyOf(it) == key }.map { it.id }
    } catch (e: ArcadeCopies.NotLookedUp) {
        Log.w(LOG_TAG, "Game $gameId: arcade pack did not answer; only this copy hidden or shown")
        listOf(gameId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        Log.w(LOG_TAG, "Game $gameId: arcade copies not looked up (${e.javaClass.simpleName}); only this copy hidden or shown")
        listOf(gameId)
    }
}
