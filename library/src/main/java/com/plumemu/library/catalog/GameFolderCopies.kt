package com.plumemu.library.catalog

import com.plumemu.coredata.db.GameFolderRow
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.identify.name.Region
import com.plumemu.identify.name.TitleCleaner

/**
 * Regional copies kept one game per folder (`PS2/Ape Escape (USA)/Ape Escape (USA).iso` beside
 * `PS2/Ape Escape (Europe)/Ape Escape (Europe).iso`). The catalogue sees one directory at a time, so this rule runs on the stored rows
 * after a scan (`FolderScan`) and again when the game menu lists a row's copies (`copiesOf`).
 *
 * A game folder is a directory below the picked folder holding exactly one row. Game folders with
 * the same parent directory group like copies in one directory (rule 7): same system, file sort
 * name and [variantKey]; [RegionPreference] picks the shown one. Rows marked not a game and arcade
 * systems are left out (arcade sets are grouped per directory by `ArcadeCopies`).
 */
internal object GameFolderCopies {
    /**
     * What copies in sibling game folders must share besides system and file sort name:
     * - the file name's variant key (the folder's when the file name names no game, as the
     *   catalogue titles it: `Galerians (USA)/disc.chd`) and the folder name's, so
     *   `Ape Escape [T+Eng]/Ape Escape (Japan).iso` is a translation, not a copy of
     *   `Ape Escape (Japan)/Ape Escape (Japan).iso`;
     * - the folder's own cleaned title (sort name, regions and languages left out), so a hack kept
     *   under its own folder name with the original's file name (`SMW Kaizo/Super Mario World
     *   (USA).sfc`) is another game;
     * - for a row of one disc ([discCount] 1), its disc number (the file name's, else the folder's),
     *   because the cleaner leaves disc tags out of both the sort name and the variant key:
     *   `FF7 (USA) (Disc 1)/` and `FF7 (Europe) (Disc 1)/` are copies, `FF7 (USA) (Disc 2)/` is
     *   another disc and stays shown. A row of several discs is named after its first disc
     *   (`G-Police (Japan)/disc1.chd` with `disc2.chd`) and stands for the whole game.
     */
    fun variantKey(fileName: String, relativePath: String, discCount: Int): String {
        val folder = TitleCleaner.clean(DiscMarkers.canonical(relativePath.substringBeforeLast('/', "").substringAfterLast('/')))
        val file = TitleCleaner.clean(DiscMarkers.canonical(fileName.substringBeforeLast('.')))
        val fileKey = if (DiscMarkers.isGeneric(TitleCleaner.withoutDiscTag(file.title))) folder.variantKey else file.variantKey
        val disc = if (discCount == 1) (file.disc ?: folder.disc)?.number?.toString().orEmpty() else ""
        return listOf(fileKey, folder.variantKey, folder.sortName, disc).joinToString("\u0000")
    }

    /** Whether [systemId]'s rows are grouped by this rule. */
    fun applies(systemId: String): Boolean = !ArcadeSystems.isArcade(systemId)

    /**
     * Which rows of game folders among [rows] (every row of some directories) the lists show: row
     * id → shown. Rows of directories with more than one row, rows marked not a game and arcade
     * rows are not in the answer (their `is_primary_copy` is the catalogue's or the naming pass's).
     */
    internal fun decide(rows: List<GameFolderRow>, preferred: List<Region>): Map<Long, Boolean> {
        val alone = rows.groupBy { it.parentDocumentId }.values.mapNotNull { it.singleOrNull() }
            .filter { isCandidate(it.systemId, it.relativePath, it.isNotGame) }
            .sortedBy { it.relativePath }
        val shown = RegionPreference.primaryCopies(
            alone,
            preferred,
            groupKey = ::groupKey,
            regions = { row -> row.fileRegions.split(',').mapNotNull { Region.fromCode(it.trim()) } },
            fileName = { it.fileName },
        )
        return alone.associate { it.id to (it in shown) }
    }

    /** A row alone in its directory is grouped by this rule: a game, not arcade, below the picked folder. */
    fun isCandidate(systemId: String, relativePath: String, isNotGame: Boolean): Boolean =
        !isNotGame && applies(systemId) && relativePath.contains('/')

    /** Rows of game folders with the same key are copies of one game: parent directory, system, file sort name, variant. */
    internal fun groupKey(row: GameFolderRow): List<Any> = groupKey(row.systemId, row.fileSortName, row.fileName, row.relativePath, row.discCount)

    fun groupKey(systemId: String, fileSortName: String, fileName: String, relativePath: String, discCount: Int): List<Any> =
        listOf(parentOf(parentOf(relativePath)), systemId, fileSortName, variantKey(fileName, relativePath, discCount))

    private fun parentOf(path: String) = path.substringBeforeLast('/', missingDelimiterValue = "")
}

/**
 * The group of copies kept one game per folder a stored row of one picked folder belongs to, the
 * key [GameFolderCopies] groups the rows by after a scan; null when the rule leaves the row to the
 * per-directory grouping ([rowsInDirectory] is the number of rows in its directory, itself
 * included). For app's Statistics (`PlayedGames`): time on a copy this rule hides counts under the
 * copy the lists show.
 */
fun gameFolderCopyGroup(
    systemId: String,
    fileSortName: String,
    fileName: String,
    relativePath: String,
    discCount: Int,
    rowsInDirectory: Int,
    isNotGame: Boolean,
): List<Any>? =
    if (rowsInDirectory == 1 && GameFolderCopies.isCandidate(systemId, relativePath, isNotGame)) {
        GameFolderCopies.groupKey(systemId, fileSortName, fileName, relativePath, discCount)
    } else {
        null
    }
