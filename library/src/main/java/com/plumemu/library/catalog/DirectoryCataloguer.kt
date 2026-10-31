package com.plumemu.library.catalog

import com.plumemu.library.matching.FileMatch
import com.plumemu.library.matching.FolderMatch
import com.plumemu.library.matching.SystemMatcher
import com.plumemu.identify.name.Region
import com.plumemu.identify.name.TitleCase
import com.plumemu.identify.name.TitleCleaner
import com.plumemu.library.scan.DocumentEntry
import java.util.Locale

/**
 * What the catalogue needs besides the system rules; the app fills it from Settings. Arcade full
 * names are not here: the identification pass names arcade sets from the arcade name pack.
 */
data class CatalogSettings(
    val preferredRegions: List<Region> = RegionPreference.DEFAULT,
)

/** One game found in a directory, ready to be stored. */
data class CataloguedGame(
    val entry: DocumentEntry,
    val title: String,
    val sortName: String,
    val regions: List<Region>,
    val discGroup: String?,
    val discCount: Int,
    val systemId: String,
    val isPrimaryCopy: Boolean,
)

data class DirectoryCatalogue(
    val games: List<CataloguedGame>,
    val skippedJunk: Int,
    val skippedUnmatched: Int,
    /**
     * Document id of every file that is part of a game without a row of its own (a bin, a later
     * disc, a disc an `.m3u` stands for) → document id of the game row it is part of. A rescan
     * uses it to carry a person's marks from a row that the grouping now folds into another.
     */
    val partOf: Map<String, String> = emptyMap(),
)

/**
 * Turns the files of one directory into games: junk and BIOS out, system by folder then
 * extension, discs grouped, titles cleaned, regional copies grouped. Pure.
 *
 * One game's folder: a directory whose own name names no system (not the picked folder, not
 * `PS2/` or `Sony - PlayStation 2/`) and whose files make exactly one game once discs, tracks and
 * playlists are grouped (`Galerians (France)/Disc 1.chd`, `Disc 2.chd`, `Disc 3.chd`;
 * `Game (USA)/disc.gdi` and its tracks; `Game (USA)/game.iso`). When that game's file name names
 * no game ([DiscMarkers.isGeneric]: `game`, `disc1`, `Disc 2`, `rom`, `main`), the title, sort
 * name, regions and variant come from the folder name, cleaned like a file name. A folder of two
 * or more games keeps every file's own name, and a file named like a game keeps its name
 * (`Title (USA)/Title (USA).iso`).
 *
 * Switch files that carry a title id in their names are one game per base id ([SwitchGrouping]):
 * the base file is the game's row, its update and DLC are part of it.
 */
class DirectoryCataloguer(
    private val matcher: SystemMatcher,
    private val settings: CatalogSettings = CatalogSettings(),
) {
    /** The person's region order, which also picks the shown copy among sibling game folders (`GameFolderCopies`). */
    internal val preferredRegions: List<Region> get() = settings.preferredRegions

    /** [directoryName] is the directory's own name, `null` for the picked folder itself. */
    fun catalogue(
        folder: FolderMatch,
        files: List<DocumentEntry>,
        directoryName: String? = null,
        sheetParts: Map<String, Set<String>> = emptyMap(),
    ): DirectoryCatalogue = catalogue(folder, files, directoryName, shownUnder = null, sheetParts = sheetParts)

    /**
     * [catalogue], also filling [shownUnder] with every matched file that has no row of its own or
     * is a regional copy the lists do not show, mapped to the file it is part of: a bin to its
     * cue, a cue to its m3u, a disc to the first disc of its set, a copy to its group's primary
     * copy (follow the map until a primary row is reached). Only the corpus harness (library
     * tests) passes a map; it reads what a shown game stands for with the scan's own code.
     *
     * [sheetParts] is what the sheets of this directory name: a `.cue`, `.gdi` or `.m3u`'s
     * document id → document ids of the files it lists (the scan reads the sheets,
     * [SheetClaims]). A listed file of this directory is part of the sheet's game whatever it is
     * called; the sheet is the row.
     */
    internal fun catalogue(
        folder: FolderMatch,
        files: List<DocumentEntry>,
        directoryName: String?,
        shownUnder: MutableMap<DocumentEntry, DocumentEntry>?,
        sheetParts: Map<String, Set<String>> = emptyMap(),
    ): DirectoryCatalogue {
        var junk = 0
        val matched = mutableListOf<MatchedFile<DocumentEntry>>()
        val notMatched = mutableListOf<MatchedFile<DocumentEntry>>()
        for (file in files) {
            if (file.isDirectory) continue
            if (JunkFilter.isJunkFile(file.name)) {
                junk++
                continue
            }
            val match = matcher.matchFile(folder, file.name)
            val described = MatchedFile(
                item = file,
                baseName = DiscMarkers.canonical(file.name.substringBeforeLast('.')),
                extension = file.name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT),
                systemId = (match as? FileMatch.Matched)?.systemId.orEmpty(),
            )
            if (match is FileMatch.Matched) matched += described else notMatched += described
        }
        // A .gdi's tracks belong to its game whatever their extension: `track02.raw` is no
        // Dreamcast extension, yet it is part of the game, not an unmatched file.
        val gdis = matched.filter { it.extension == "gdi" }
        val gdiSystem = gdis.map { it.systemId }.distinct().singleOrNull()
        val gdiKeys = gdis.mapTo(HashSet()) { TitleCleaner.key(it.baseName) }
        val tracks = gdiSystem?.let { system ->
            notMatched.filter { DiscGrouping.isGdiTrack(it.baseName, it.extension, gdiKeys) }.map { it.copy(systemId = system) }
        }.orEmpty()
        matched += tracks
        val sheets = SheetClaims.of(sheetParts, matched.filter { it.extension in SheetClaims.EXTENSIONS }, files.mapTo(HashSet()) { it.documentId })
        val trackIds = tracks.mapTo(HashSet()) { it.item.documentId }
        val loose = notMatched.filter { sheets.claimOf.containsKey(it.item.documentId) && it.item.documentId !in trackIds }
        notMatched.removeAll(loose.toSet())
        val unmatched = notMatched.size - tracks.size

        val grouping = HashMap<MatchedFile<DocumentEntry>, MatchedFile<DocumentEntry>>()
        for (part in loose) grouping[part] = sheets.sheetOf(part.item.documentId)
        val gameFolder = isGameFolder(directoryName)
        // Switch base, update and DLC by title id first: their names may share nothing else.
        val switch = SwitchGrouping.take(
            matched,
            grouping,
            settings.preferredRegions,
            fileName = { it.name },
            regions = { TitleCleaner.clean(it.baseName).regions },
        )
        val grouped = sheets.take(
            switch.games.map { GroupedGame(it, null, 1) } +
                DiscGrouping.group(switch.rest, grouping, inOneGamesFolder = gameFolder),
            grouping,
        )
        val folderTitle = if (grouped.size == 1 && gameFolder) directoryName else null
        val baseKeys = matched.mapTo(HashSet()) { TitleCleaner.key(it.baseName) }
        val named = grouped.map { describe(it, folderTitle, baseKeys) }
        val primaries = RegionPreference.primaryCopies(
            named,
            settings.preferredRegions,
            groupKey = copyGroup,
            regions = { it.game.regions },
            fileName = { it.game.entry.name },
        )
        val rows = named.mapTo(HashSet()) { it.game.entry }
        val partOf = HashMap<String, String>()
        for (file in grouping.keys) {
            var at = grouping.getValue(file)
            while (at.item !in rows) at = grouping[at] ?: break
            if (at.item in rows) partOf[file.item.documentId] = at.item.documentId
        }
        if (shownUnder != null) {
            grouping.forEach { (file, under) -> shownUnder[file.item] = under.item }
            for (copies in named.groupBy(copyGroup).values) {
                val primary = copies.single { it in primaries }
                for (copy in copies) if (copy !== primary) shownUnder[copy.game.entry] = primary.game.entry
            }
        }
        val games = named.map { it.game.copy(isPrimaryCopy = it in primaries) }.sortedBy { it.entry.name }
        return DirectoryCatalogue(games, junk, unmatched, partOf)
    }

    /** A directory that may be one game's folder: it has a name, and the name is no system's. */
    private fun isGameFolder(directoryName: String?): Boolean =
        !directoryName.isNullOrBlank() && matcher.matchFolder(directoryName) == FolderMatch.Unknown

    private class Described(val game: CataloguedGame, val variantKey: String)

    /** Regional copies of one game: same system, sort name and variant key. */
    private val copyGroup: (Described) -> Any = { Triple(it.game.systemId, it.game.sortName, it.variantKey) }

    /**
     * [folderTitle], when given, is the name of the one game's folder this game fills; it names the
     * game when the file name names none.
     */
    private fun describe(grouped: GroupedGame<DocumentEntry>, folderTitle: String?, baseKeys: Set<String>): Described {
        val file = grouped.file
        // `Game (1)` is a download copy only beside a `Game` file; `Game (1)`, `Game (2)` alone are
        // different files and stay separate rows.
        val plain = TitleCleaner.withoutCopySuffix(file.baseName)
        val isCopy = plain != null && TitleCleaner.key(plain) in baseKeys
        var cleaned = TitleCleaner.clean(file.baseName, dropCopySuffix = isCopy)
        var discGroup = grouped.discGroup
        if (DiscMarkers.isGeneric(TitleCleaner.withoutDiscTag(cleaned.title))) {
            if (folderTitle != null) {
                cleaned = TitleCleaner.clean(folderTitle)
                if (discGroup != null) discGroup = folderTitle
            } else if (TitleCleaner.withoutDiscTag(file.baseName).isBlank()) {
                // `Disc 1.chd` outside a game's folder: show the file's own words, not "(Disc 1)".
                cleaned = TitleCleaner.clean(file.item.name.substringBeforeLast('.'))
            }
        }
        val game = CataloguedGame(
            entry = file.item,
            // All-lower / ALL-UPPER names shown in title case until a pack names them (arcade untouched).
            title = TitleCase.forSystem(cleaned.title, file.systemId),
            sortName = cleaned.sortName,
            regions = cleaned.regions,
            discGroup = discGroup,
            discCount = grouped.discCount,
            systemId = file.systemId,
            isPrimaryCopy = true,
        )
        return Described(game, cleaned.variantKey)
    }
}
