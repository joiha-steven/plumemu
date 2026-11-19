package com.plumemu.home

import androidx.compose.runtime.Immutable
import com.plumemu.collections.BuiltInFolder
import com.plumemu.collections.Folder
import com.plumemu.collections.collectionCount
import com.plumemu.collections.entryIdOf
import com.plumemu.coreinput.strip.OpenFolder
import com.plumemu.coreinput.strip.StripContent
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreinput.strip.SystemEntry
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.identify.name.VariantTag
import com.plumemu.coreui.theme.PlumLayout

/** One game (or app) row: what the list draws. Titles are data, never UI strings. */
@Immutable
data class GameRowModel(
    val id: String,
    val title: String,
    /** Region read from the file name ("USA", "JPN", "EUR"), shown instead of a year without box art; empty for apps. */
    val region: String,
    /**
     * Fixed facts for the details column; null in the library, where they come from ScreenScraper
     * through [HomeArt] by [scrapeKey]. The fakes (`FakeHomeContent.withArtwork`) set them.
     */
    val details: GameDetails? = null,
    /** In Favorites: the row shows the star. Never for apps. */
    val isFavorite: Boolean = false,
    /** The key its ScreenScraper result is stored under (core-data `GameListRow.scrapeKey`); null for apps. */
    val scrapeKey: String? = null,
    /** Its system's short name ("SNES"); empty for apps and for a system the platform data does not know. */
    val systemAbbr: String = "",
    /** Its system's id ("snes"): a Recent or Favorites grid draws its cover in that system's shape. Empty for apps. */
    val systemId: String = "",
    /**
     * The key the list is ordered by (core-data `sort_name`); empty for the fakes, whose letter is made from the title
     * ([LetterKey]). An app's is its folded label.
     */
    val sortName: String = "",
    /** The file's name; read only to find [variantTags] ([withVariantTags]). Empty for apps and the fakes. */
    val fileName: String = "",
    /**
     * What tells this row from another of the same title in its list (Rev 1, English translation); empty for a row whose
     * title is unique there ([withVariantTags] sets it only on rows that collide), so the list shows nothing extra.
     */
    val variantTags: List<VariantTag> = emptyList(),
    /**
     * The row's [region], set by [withVariantTags] only when another row of its system and title has the same
     * [variantTags] (two copies that differ only by region, "Game (USA)" and "Game (Europe)"), so the subtitle can
     * tell them apart; empty otherwise.
     */
    val variantRegion: String = "",
    /**
     * The package of an installed app listed as a game (Android games, Ports apps); null for a file game and for the
     * Apps entry's rows. Such a row has no box art: the grid, the details and the second screen draw its Android icon.
     */
    val appPackage: String? = null,
    /** The saved box front's width over height as stored with the game; null while it is not known. Sizes its cover before the picture's result is read. */
    val artAspect: Float? = null,
) {
    /** What the icons are asked by: an app game's package, else the row's id (the Apps entry's `package/activity`). */
    val iconKey: String get() = appPackage ?: id

    /**
     * The row's right-hand text: with box art the year from
     * [details], else ScreenScraper's ([art]); the region when neither knows one, and nothing while
     * the game's result is still being read (so a region never flashes before its year).
     *
     * [joinPlace] given (a single system's list with room for it): when both the year and the region
     * are known the text is `joinPlace(year, region)`, "1994 · USA". Null keeps the
     * year alone: the narrow details stop, where the list is slid out to its peek and the text must not grow.
     *
     * In a list that mixes systems ([showsSystem]: Recent, Favorites, Search; [EntryKind.mixesSystems])
     * the system's short name takes the place of all that, so the person can tell the systems apart.
     */
    fun trailing(
        hasBoxArt: Boolean,
        art: ArtSlot = ArtSlot.Absent,
        showsSystem: Boolean = false,
        joinPlace: ((year: String, region: String) -> String)? = null,
    ): String {
        if (showsSystem && systemAbbr.isNotEmpty()) return systemAbbr
        if (!hasBoxArt) return region
        val year =
            details?.year
                ?: when (art) {
                    ArtSlot.Unknown -> return ""
                    ArtSlot.Absent -> null
                    is ArtSlot.Found -> art.art.details.year
                }
        if (year == null) return region
        return if (joinPlace != null && region.isNotEmpty()) joinPlace(year.toString(), region) else year.toString()
    }

    /**
     * The row's year and region for the board's two columns (a wide single system's list with room): the year (from
     * [details], else ScreenScraper's; empty without box art, while the result is being read or when none is known) and
     * the [region], which may be empty. An empty year leaves its cell empty, so the region never moves. Null only in a
     * list that mixes systems ([showsSystem] with a short name): the row then shows [trailing] as one text.
     */
    fun yearAndRegion(
        hasBoxArt: Boolean,
        art: ArtSlot = ArtSlot.Absent,
        showsSystem: Boolean = false,
    ): Pair<String, String>? {
        if (showsSystem && systemAbbr.isNotEmpty()) return null
        val year =
            if (!hasBoxArt) {
                null
            } else {
                details?.year
                    ?: when (art) {
                        ArtSlot.Unknown, ArtSlot.Absent -> null
                        is ArtSlot.Found -> art.art.details.year
                    }
            }
        return year?.toString().orEmpty() to region
    }
}

/**
 * The details column's facts. Publisher, genre, players, emulator and
 * description are data, shown as given; numbers are formatted for the locale by the screen.
 */
@Immutable
data class GameDetails(
    val year: Int?,
    val publisher: String,
    /** The main genre's name; empty when unknown. */
    val genre: String,
    /** As ScreenScraper writes it, "1", "1-2", "1-4"; empty when unknown. Kept, not shown. */
    val players: String,
    /** The synopsis (English); empty when there is none or it is not read yet. */
    val description: String,
    /** Last played, play time and emulator (the facts table); null when not known. */
    val play: PlayFacts? = null,
    /**
     * ScreenScraper's rating out of 20, shown out of 10 beside the genre; null when there is none
     * or it is not read yet (the library reads it with the synopsis, [DetailsExtras]).
     */
    val rating: Int? = null,
)

/** The facts table of the details column. [lastPlayedDaysAgo] null means never played. */
@Immutable
data class PlayFacts(
    val lastPlayedDaysAgo: Int?,
    val playTimeMinutes: Int,
    val emulator: String,
    /** The game's usual time on a charge and its drain (the fakes'; a read game has its own pace): both null shows no battery cell. */
    val batteryMinutesOnCharge: Double? = null,
    val batteryPercentPerHour: Double? = null,
)

/** True for a game with no play at all: it gets one "Not played yet" line, not "Played 0 min" and "Last played Never". */
internal fun PlayFacts.neverPlayed(): Boolean = lastPlayedDaysAgo == null && playTimeMinutes == 0

/**
 * The entries of the system column: Favorites, Collections, Apps, then each system with games. Recent is the first
 * folder inside Collections (its entry keeps this kind, so its names stay); Most played, Recently added and the person's
 * own collections are [Collections] entries too ([HomeEntry.isFolder]).
 */
enum class EntryKind {
    Recent,
    Favorites,
    Collections,
    Apps,
    System,
    ;

    /** Recent, Favorites and the folders hold games of many systems, so their rows show the system's short name in place of the year. */
    val mixesSystems: Boolean get() = this == Recent || this == Favorites || this == Collections

    /** A system's list and Favorites follow the stored sort; Recent is newest first and Apps are listed by Android. */
    val followsSort: Boolean get() = this == System || this == Favorites
}

/**
 * The cell of a Recent or Favorites grid: the square class's columns at
 * the grid size, each cover fitted in it at its own system's shape and standing on the shelf.
 */
val MixedGridShape: CoverShape = CoverShape(1f)

/** One system with its games, as the library gives it; [HomeContent.of] drops it when it has none. */
@Immutable
data class SystemGames(
    val id: String,
    val name: String,
    val games: List<GameRowModel>,
    /** The system's box-art shape: the grid's columns and row height follow it. */
    val coverShape: CoverShape = CoverShape.Landscape,
    /** What the platform data knows about it (the band over its list); null for a system it does not know. */
    val info: SystemInfo? = null,
    /** The system's three-character code (platform data `code`) for the peek's System codes; empty where the data has none ([entryCode] then spells one from the name). */
    val code: String = "",
    /** The platform data's short name ("SNES", "Mega Drive"), for the second screen's captions; empty where the data has none. */
    val shortName: String = "",
)

/**
 * One entry of the system column and its rows. [name] is the system's name for [EntryKind.System];
 * the fixed entries take theirs from strings.xml, so it is null for them.
 */
@Immutable
data class HomeEntry(
    val id: String,
    val kind: EntryKind,
    val name: String?,
    val rows: List<GameRowModel>,
    val coverShape: CoverShape = CoverShape.Landscape,
    /**
     * The number beside the entry's name and in the top bar. The rows' count, except in a held
     * Favorites list, which keeps a game taken out on screen but counts only the favorites.
     */
    val shownCount: Int = rows.size,
    /**
     * Recent and Favorites: each system's cover shape by id, so a game's cover keeps its own system's
     * shape in the grid ([shapeOf]). Empty for a system's own entry, whose games all take [coverShape].
     */
    val systemShapes: Map<String, CoverShape> = emptyMap(),
    /** The same ids' display names ("Super Nintendo"), for the poster cover's band in a mixed grid; empty for a system's own entry (its [name] is the one). */
    val systemNames: Map<String, String> = emptyMap(),
    /** The same ids' short names ([SystemGames.shortName]), for the second screen's captions under the covers. */
    val systemShortNames: Map<String, String> = emptyMap(),
    /** A system's badge and facts for the band over its list ([SystemInfo]); null for the fixed entries and unknown systems. */
    val info: SystemInfo? = null,
    /** The rows are in name order; false for a list sorted by last played (the stored sort). */
    val sortedByName: Boolean = true,
    /** The system's three-character code (platform data `code`) for the peek's System codes; empty where the data has none ([entryCode] then spells one from the name). */
    val code: String = "",
    /** A folder inside Collections (Recent, Most played, Recently added, an own collection): not in the column, reached by [HomeContent.folderEntries]. */
    val isFolder: Boolean = false,
) {
    /** The Collections entry itself: its rows are the folders, one per row. */
    val isFolderList: Boolean get() = kind == EntryKind.Collections && !isFolder

    /**
     * L1 / R1 jump by letter in this list: not for Recent, and not
     * where the order is not by name, since the letters would be out of order (they jump a page there).
     */
    val hasLetterRail: Boolean get() = kind != EntryKind.Recent && sortedByName

    /** Rows the focus can stand on. */
    val count: Int get() = rows.size

    /** The rows' ids in order, for the strip's focus (built once per entry). */
    val rowIds: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { rows.map { it.id } }

    /**
     * Every entry has a grid view: a system's games, Recent and Favorites
     * in [MixedGridShape] cells), and Apps (its icons at one fixed size, `AppsGrid.kt`).
     */
    val hasGridView: Boolean get() = kind == EntryKind.System || (kind.mixesSystems && !isFolderList) || kind == EntryKind.Apps

    /** The shape [row]'s cover is drawn in: its own system's in Recent and Favorites (landscape when unknown), else [coverShape]. */
    fun shapeOf(row: GameRowModel): CoverShape = if (kind.mixesSystems) systemShapes[row.systemId] ?: CoverShape.Landscape else coverShape

    /**
     * A system whose games are all installed apps (Android games): its grid is laid out like Apps' grid, icons and names
     * on the background ([AppsGrid]). A system that also holds files (Ports) or a mixed list keeps the cover grid, where
     * an app game's cell draws the same tile.
     */
    val isAppGamesGrid: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) { kind == EntryKind.System && rows.isNotEmpty() && rows.all { it.appPackage != null } }

    /** Every entry has a details column or stop; Apps shows its icon and package there ([AppDetails]), in its list only (see [HomeContent.systemEntryOf]). */
    val hasDetails: Boolean get() = !isFolderList
}

/** One folder with its games, as the content layer gives it; [Folder.NewCollection] has none. */
@Immutable
data class FolderRows(
    val folder: Folder,
    val rows: List<GameRowModel>,
)

/**
 * Everything the main screen shows, immutable: the system column top to bottom (Favorites,
 * Collections, Apps, then each system that has games) and whether there is box art (
 * false in the alpha). Build it with [of], which drops systems without games.
 */
@Immutable
class HomeContent private constructor(
    val entries: List<HomeEntry>,
    val hasBoxArt: Boolean,
    /**
     * Systems the person hid from the column (Settings › Library › Systems), in the column's
     * order: not shown or counted, but their games are still found by Search (Recent and
     * Favorites come from their own queries and hold them already).
     */
    val hiddenEntries: List<HomeEntry> = emptyList(),
    /** The folders inside Collections as entries (not in the column): Recent, Most played, Recently added, the own collections. */
    val folderEntries: List<HomeEntry> = emptyList(),
) {
    /** The entry the cursor stands in for [focus] (reconciled): the open folder inside Collections, else the focused column entry. */
    fun shownEntry(focus: StripFocus): HomeEntry? {
        val host = entries.getOrNull(focus.systemIndex) ?: return null
        if (!host.isFolderList) return host
        val open: OpenFolder = focus.openFolder?.takeIf { it.host == host.id } ?: return host
        return folderEntries.firstOrNull { it.id == open.id } ?: host
    }

    /**
     * The folder of the folder list [host] that is open in [focus] (kept while the cursor is elsewhere, so coming back finds it),
     * or null when [host] is no folder list or none is open.
     */
    fun openFolderIn(host: HomeEntry, focus: StripFocus): HomeEntry? {
        if (!host.isFolderList) return null
        val open = focus.openFolder?.takeIf { it.host == host.id } ?: return null
        return folderEntry(open.id)
    }

    /** The folder entry [id], or null. */
    fun folderEntry(id: String): HomeEntry? = folderEntries.firstOrNull { it.id == id }

    /** Systems with games (the "18 systems" of the top bar). */
    val systemCount: Int = entries.count { it.kind == EntryKind.System }

    /** Games across all systems (the "1,284 games"); Recent and Favorites repeat games and do not count. */
    val gameCount: Int = entries.filter { it.kind == EntryKind.System }.sumOf { it.count }

    /** Where the systems start, after Favorites, Collections and (when shown) Apps; no gap above it, so every count stays on its game row's baseline. */
    val firstSystemIndex: Int = entries.count { it.kind != EntryKind.System }

    /** No system has games: the screen shows the empty-library message instead of the strip. */
    val isLibraryEmpty: Boolean get() = systemCount == 0

    /**
     * What core-input's focus rules need: ids, counts and the rows' ids (so the focused row follows
     * its game through a re-sort), in screen order, every entry a list.
     */
    val strip: StripContent = StripContent(entries.map { systemEntryOf(it, columns = 1) }, hasBoxArt)

    /** Whether any entry, a folder included, is shown as a grid with [gridIds]. */
    private fun anyGrid(gridIds: Set<String>): Boolean = entries.any { showsGrid(it, gridIds) } || folderEntries.any { showsGrid(it, gridIds) }

    /**
     * [strip] with the grid's columns for each entry in [gridIds] shown as a grid (SELECT), so
     * the focus moves by columns there: a game grid's from its cover shape and the grid size, Apps' from
     * the width alone ([appsGridColumns]).
     */
    fun stripWith(
        gridIds: Set<String>,
        layout: PlumLayout,
    ): StripContent {
        if (gridIds.isEmpty() || !anyGrid(gridIds)) return strip
        return StripContent(
            entries.map { entry ->
                val columns =
                    when {
                        !showsGrid(entry, gridIds) -> 1
                        entry.kind == EntryKind.Apps || entry.isAppGamesGrid -> appsGridColumns(layout)
                        else -> layout.gridColumns(entry.coverShape)
                    }
                systemEntryOf(entry, columns, gridIds, layout)
            },
            hasBoxArt,
        )
    }

    private fun systemEntryOf(
        entry: HomeEntry,
        columns: Int,
        gridIds: Set<String> = emptySet(),
        layout: PlumLayout? = null,
    ): SystemEntry {
        val folders =
            if (entry.isFolderList) {
                folderEntries.map { folder ->
                    val folderColumns = if (layout != null && showsGrid(folder, gridIds)) layout.gridColumns(folder.coverShape) else 1
                    SystemEntry(folder.id, folder.count, folderColumns, folder.hasDetails, folder.rowIds, isFolder = true)
                }
            } else {
                emptyList()
            }
        return SystemEntry(entry.id, entry.count, columns, entry.hasDetails && !(entry.kind == EntryKind.Apps && columns > 1), entry.rowIds, folders = folders, isFolderList = entry.isFolderList)
    }

    /** The same screen with [entries] in place of these (Favorites marks, FavoriteMarks). */
    internal fun withEntries(
        entries: List<HomeEntry>,
        hiddenEntries: List<HomeEntry> = this.hiddenEntries,
        folderEntries: List<HomeEntry> = this.folderEntries,
    ): HomeContent = HomeContent(entries, hasBoxArt, hiddenEntries, folderEntries)

    /**
     * Whether [entry] is shown as a grid: it can be one ([hasGridFor]), it is switched to the grid, and it
     * has at least one row. An entry without rows stays a list, which is where the empty-library message lives.
     */
    fun showsGrid(
        entry: HomeEntry?,
        gridIds: Set<String>,
    ): Boolean = entry != null && hasGridFor(entry) && entry.id in gridIds && entry.rows.isNotEmpty()

    /** Whether [entry] can be a grid here: it has a grid view. Box art is not needed; a game without a picture gets a plain titled tile (a poster cover later). */
    fun hasGridFor(entry: HomeEntry): Boolean = entry.hasGridView

    companion object {
        const val RECENT_ID = "recent"
        const val FAVORITES_ID = "favorites"
        const val APPS_ID = "apps"
        const val COLLECTIONS_ID = "collections"

        /** The folder list's last row: it opens no folder (A asks for a name). */
        const val NEW_COLLECTION_ID = "new_collection"

        fun of(
            recent: List<GameRowModel>,
            favorites: List<GameRowModel>,
            apps: List<GameRowModel>,
            systems: List<SystemGames>,
            hasBoxArt: Boolean = false,
            /** Home screen › Show Android apps: off, the Apps entry is left out (not "Apps 0"). */
            showApps: Boolean = true,
            /** Library › Systems: systems left out of the column, still searched. */
            hiddenSystems: List<SystemGames> = emptyList(),
            /** Sort games by name (false: any other sort), which decides whether the lists have letters for the page jump. */
            sortedByName: Boolean = true,
            /** The folders after Recent (Most played, Recently added, then the own collections) with their games; the default has the two built-ins empty. */
            otherFolders: List<FolderRows> = DEFAULT_FOLDERS,
        ): HomeContent {
            val shapes = (systems + hiddenSystems).associate { it.id to it.coverShape }
            val names = (systems + hiddenSystems).associate { it.id to it.name }
            val shortNames = (systems + hiddenSystems).filter { it.shortName.isNotEmpty() }.associate { it.id to it.shortName }
            fun folderEntryOf(folder: Folder, rows: List<GameRowModel>): HomeEntry? {
                val id = entryIdOf(folder) ?: return null
                val own = folder as? Folder.Own
                val smart = folder is Folder.BuiltIn && folder.kind != BuiltInFolder.Recent
                val kind = if (folder is Folder.BuiltIn && folder.kind == BuiltInFolder.Recent) EntryKind.Recent else EntryKind.Collections
                return HomeEntry(id, kind, own?.name, rows, MixedGridShape, systemShapes = shapes, systemNames = names, systemShortNames = shortNames, sortedByName = sortedByName && !smart && kind != EntryKind.Recent, isFolder = true)
            }
            val recentFolder = FolderRows(Folder.BuiltIn(BuiltInFolder.Recent, recent.size), recent)
            val folders = (listOf(recentFolder) + otherFolders.map { it.copy(folder = withCount(it.folder, it.rows.size)) })
            val folderEntries = folders.mapNotNull { folderEntryOf(it.folder, it.rows) }
            val folderRows =
                folders.map { (folder, rows) -> GameRowModel(entryIdOf(folder) ?: NEW_COLLECTION_ID, (folder as? Folder.Own)?.name.orEmpty(), rows.size.toString()) } +
                    GameRowModel(NEW_COLLECTION_ID, "", "")
            val fixed =
                listOfNotNull(
                    HomeEntry(FAVORITES_ID, EntryKind.Favorites, null, favorites, MixedGridShape, systemShapes = shapes, systemNames = names, systemShortNames = shortNames, sortedByName = sortedByName),
                    HomeEntry(COLLECTIONS_ID, EntryKind.Collections, null, folderRows, shownCount = collectionCount(folders.map { it.folder }), sortedByName = false),
                    if (showApps) HomeEntry(APPS_ID, EntryKind.Apps, null, apps) else null,
                )
            fun entriesOf(list: List<SystemGames>) =
                list.filter { it.games.isNotEmpty() }.map { HomeEntry(it.id, EntryKind.System, it.name, it.games, it.coverShape, info = it.info, sortedByName = sortedByName, code = it.code) }
            return HomeContent(fixed + entriesOf(systems), hasBoxArt, entriesOf(hiddenSystems), folderEntries)
        }

        private fun withCount(folder: Folder, count: Int): Folder =
            when (folder) {
                is Folder.BuiltIn -> folder.copy(count = count)
                is Folder.Own -> folder.copy(count = count)
                Folder.NewCollection -> folder
            }

        /** Most played and Recently added with no games: always listed, also where there is no database. */
        internal val DEFAULT_FOLDERS = listOf(FolderRows(Folder.BuiltIn(BuiltInFolder.MostPlayed, 0), emptyList()), FolderRows(Folder.BuiltIn(BuiltInFolder.RecentlyAdded, 0), emptyList()))

        /** Nothing scanned yet. */
        val Empty: HomeContent = of(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** The index of the first system with the Apps entry shown ([HomeContent.firstSystemIndex] in general): Favorites, Collections, Apps. */
internal const val FIRST_SYSTEM_INDEX = 3

/**
 * The row a letter jump (L1 / R1 in a list) lands on in [rows] from [fromIndex]: forward, the first row of
 * the next letter ([LetterKey]); backward, the first row of the current letter group, or of the previous
 * group when already there. Stays put at the ends.
 */
internal fun letterJumpTarget(
    rows: List<GameRowModel>,
    fromIndex: Int,
    forward: Boolean,
): Int {
    if (rows.isEmpty()) return 0
    val from = fromIndex.coerceIn(0, rows.lastIndex)
    fun letterAt(i: Int): Char = LetterKey.of(rows[i])
    fun groupStart(i: Int): Int {
        var start = i
        while (start > 0 && letterAt(start - 1) == letterAt(i)) start--
        return start
    }
    return if (forward) {
        (from + 1..rows.lastIndex).firstOrNull { letterAt(it) != letterAt(from) } ?: from
    } else {
        val start = groupStart(from)
        if (start < from || start == 0) start else groupStart(start - 1)
    }
}
