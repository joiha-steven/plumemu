package com.plumemu.home

import androidx.compose.runtime.Immutable
import com.plumemu.coreinput.strip.StripContent
import com.plumemu.coreinput.strip.SystemEntry
import com.plumemu.coredata.settings.LeftEdgeSetting
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
) {
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
)

/** The entries of the system column: three fixed ones, then each system with games. */
enum class EntryKind {
    Recent,
    Favorites,
    Apps,
    System,
    ;

    /** Recent and Favorites hold games of many systems, so their rows show the system's short name in place of the year. */
    val mixesSystems: Boolean get() = this == Recent || this == Favorites
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
    /** A system's badge and facts for the band over its list ([SystemInfo]); null for the fixed entries and unknown systems. */
    val info: SystemInfo? = null,
    /** The rows are in name order; false for a list sorted by last played (Settings › Library › Sort games). */
    val sortedByName: Boolean = true,
    /** The system's three-character code (platform data `code`) for the peek's System codes; empty where the data has none ([entryCode] then spells one from the name). */
    val code: String = "",
) {
    /**
     * The peek shows a letter rail of this list: not for Recent, and not
     * where the order is not by name, since the letters would be out of order.
     */
    val hasLetterRail: Boolean get() = kind != EntryKind.Recent && sortedByName

    /** The rail's letters, built once per entry. */
    val railLetters: List<Char> by lazy(LazyThreadSafetyMode.PUBLICATION) { if (hasLetterRail) railLettersOf(rows, railStart) else emptyList() }

    /** Rows before the rail's first (the Apps list's pinned Settings app): focus on them lights no letter. */
    val railStart: Int by lazy(LazyThreadSafetyMode.PUBLICATION) { if (kind == EntryKind.Apps) pinnedLeadingRows(rows) else 0 }

    /** Rows the focus can stand on. */
    val count: Int get() = rows.size

    /** The rows' ids in order, for the strip's focus (built once per entry). */
    val rowIds: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { rows.map { it.id } }

    /**
     * Every entry has a grid view: a system's games, Recent and Favorites
     * in [MixedGridShape] cells), and Apps (its icons at one fixed size, `AppsGrid.kt`).
     */
    val hasGridView: Boolean get() = kind == EntryKind.System || kind.mixesSystems || kind == EntryKind.Apps

    /** Covers come from ScreenScraper, so a game grid needs box art; Apps' icons come from Android and do not. */
    val gridNeedsBoxArt: Boolean get() = kind != EntryKind.Apps

    /** The shape [row]'s cover is drawn in: its own system's in Recent and Favorites (landscape when unknown), else [coverShape]. */
    fun shapeOf(row: GameRowModel): CoverShape = if (kind.mixesSystems) systemShapes[row.systemId] ?: CoverShape.Landscape else coverShape

    /** Apps never has a details column or details stop: apps have no art. */
    val hasDetails: Boolean get() = kind != EntryKind.Apps
}

/**
 * Everything the main screen shows, immutable: the system column top to bottom (Recent,
 * Favorites, Apps, then each system that has games) and whether there is box art (
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
    /** Settings › Appearance › Left edge: what the peek shows while a games list is focused. Counts is the default. */
    val leftEdge: LeftEdgeSetting = LeftEdgeSetting.Default,
) {
    /** Systems with games (the "18 systems" of the top bar). */
    val systemCount: Int = entries.count { it.kind == EntryKind.System }

    /** Games across all systems (the "1,284 games"); Recent and Favorites repeat games and do not count. */
    val gameCount: Int = entries.filter { it.kind == EntryKind.System }.sumOf { it.count }

    /** Where the systems start, after Recent, Favorites and (when shown) Apps; the 4 dp gap goes above it. */
    val firstSystemIndex: Int = entries.count { it.kind != EntryKind.System }

    /** No system has games: the screen shows the empty-library message instead of the strip. */
    val isLibraryEmpty: Boolean get() = systemCount == 0

    /**
     * What core-input's focus rules need: ids, counts and the rows' ids (so the focused row follows
     * its game through a re-sort), in screen order, every entry a list.
     */
    val strip: StripContent = StripContent(entries.map { systemEntryOf(it, columns = 1) }, hasBoxArt)

    /**
     * [strip] with the grid's columns for each entry in [gridIds] shown as a grid (SELECT), so
     * the focus moves by columns there: a game grid's from its cover shape and the grid size, Apps' from
     * the width alone ([appsGridColumns]). Without box art only Apps can be a grid.
     */
    fun stripWith(
        gridIds: Set<String>,
        layout: PlumLayout,
    ): StripContent {
        if (gridIds.isEmpty() || entries.none { showsGrid(it, gridIds) }) return strip
        return StripContent(
            entries.map { entry ->
                val columns =
                    when {
                        !showsGrid(entry, gridIds) -> 1
                        entry.kind == EntryKind.Apps -> appsGridColumns(layout)
                        else -> layout.gridColumns(entry.coverShape)
                    }
                systemEntryOf(entry, columns)
            },
            hasBoxArt,
        )
    }

    private fun systemEntryOf(
        entry: HomeEntry,
        columns: Int,
    ) = SystemEntry(entry.id, entry.count, columns, entry.hasDetails, entry.rowIds, hasGridWithoutArt = !entry.gridNeedsBoxArt)

    /** The same screen with [entries] in place of these (Favorites marks, FavoriteMarks). */
    internal fun withEntries(
        entries: List<HomeEntry>,
        hiddenEntries: List<HomeEntry> = this.hiddenEntries,
    ): HomeContent = HomeContent(entries, hasBoxArt, hiddenEntries, leftEdge)

    /** The same screen with the peek showing [edge] (Settings › Appearance › Left edge). */
    fun withLeftEdge(edge: LeftEdgeSetting): HomeContent = if (edge == leftEdge) this else HomeContent(entries, hasBoxArt, hiddenEntries, edge)

    /**
     * Whether [entry] is shown as a grid: it can be one ([hasGridFor]), it is switched to the grid, and it
     * has at least one row. An entry without rows stays a list, which is where the empty-library message lives.
     */
    fun showsGrid(
        entry: HomeEntry?,
        gridIds: Set<String>,
    ): Boolean = entry != null && hasGridFor(entry) && entry.id in gridIds && entry.rows.isNotEmpty()

    /** Whether [entry] can be a grid here: it has a grid view, and box art when its grid needs it (Apps' does not). */
    fun hasGridFor(entry: HomeEntry): Boolean = entry.hasGridView && (hasBoxArt || !entry.gridNeedsBoxArt)

    companion object {
        const val RECENT_ID = "recent"
        const val FAVORITES_ID = "favorites"
        const val APPS_ID = "apps"

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
            /** Sort games by name (false: by last played), which decides whether the lists have a letter rail. */
            sortedByName: Boolean = true,
            /** Settings › Appearance › Left edge. */
            leftEdge: LeftEdgeSetting = LeftEdgeSetting.Default,
        ): HomeContent {
            val shapes = (systems + hiddenSystems).associate { it.id to it.coverShape }
            val fixed =
                listOfNotNull(
                    HomeEntry(RECENT_ID, EntryKind.Recent, null, recent, MixedGridShape, systemShapes = shapes),
                    HomeEntry(FAVORITES_ID, EntryKind.Favorites, null, favorites, MixedGridShape, systemShapes = shapes, sortedByName = sortedByName),
                    if (showApps) HomeEntry(APPS_ID, EntryKind.Apps, null, apps) else null,
                )
            fun entriesOf(list: List<SystemGames>) =
                list.filter { it.games.isNotEmpty() }.map { HomeEntry(it.id, EntryKind.System, it.name, it.games, it.coverShape, info = it.info, sortedByName = sortedByName, code = it.code) }
            return HomeContent(fixed + entriesOf(systems), hasBoxArt, entriesOf(hiddenSystems), leftEdge)
        }

        /** Nothing scanned yet. */
        val Empty: HomeContent = of(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** The index of the first system with the Apps entry shown ([HomeContent.firstSystemIndex] in general). */
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

/**
 * The row the letter rail's tap or drag lands on for [letter]: the first row of that letter's group, found by
 * stepping [letterJumpTarget] forward from [skip] (the rows left out of the rail, [HomeEntry.railStart]) so the
 * rail and L1 / R1 always agree. Null when the list has no such letter.
 */
internal fun letterStartRow(
    rows: List<GameRowModel>,
    letter: Char,
    skip: Int = 0,
): Int? {
    if (skip > rows.lastIndex) return null
    var at = skip
    while (true) {
        if (LetterKey.of(rows[at]) == letter) return at
        val next = letterJumpTarget(rows, at, forward = true)
        if (next == at) return null
        at = next
    }
}
