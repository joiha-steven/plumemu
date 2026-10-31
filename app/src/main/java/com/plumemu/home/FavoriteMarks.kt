package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreinput.strip.StripState
import com.plumemu.game.FavoriteListener
import com.plumemu.game.GameRef
import com.plumemu.home.search.SearchState

/**
 * Favorites in one press: what the lists show between Y and the
 * library's answer, and the Favorites list held still while the person is in it.
 *
 * - **Pending marks**: Y flips the star at once; the mark is dropped when the library says the
 *   same (or the write failed and [failed] puts the row back). A second Y reads the shown value, so
 *   two quick presses end where they started.
 * - **Held Favorites**: Y inside the Favorites list freezes its rows; a game taken out stays (star
 *   gone) until the list is left ([release]), so the cursor never jumps; Y again puts it back.
 *   Games that left the library meanwhile (hidden, gone) leave the held list too.
 *
 * Main thread only (Compose state).
 */
@Stable
internal class FavoriteMarks {
    private val pending = mutableStateMapOf<String, Boolean>()

    private var held: List<GameRowModel>? by mutableStateOf(null)

    /**
     * Games taken out with Y while the list is held that no system lists: a favorite that is no
     * longer the primary regional copy is only in Favorites, so once taken out it is in no list at
     * all, yet keeps its row until the list is left. Any other game in no list (hidden, removed by a
     * rescan) leaves the held list; hiding one of these from its menu ([hidden]) drops it too.
     */
    private var takenOutOffList: Set<String> by mutableStateOf(emptySet())

    val isHolding: Boolean get() = held != null

    /**
     * Y (or the menu) asked for [isFavorite] on [gameKey]. [favoritesRows] are the Favorites rows on
     * screen when the person is in that list (they are held from now), null anywhere else.
     * [inSystemList]: a system's list shows the game (false for a copy that is not the primary one).
     */
    fun requested(
        gameKey: String,
        isFavorite: Boolean,
        favoritesRows: List<GameRowModel>?,
        inSystemList: Boolean = true,
    ) {
        if (favoritesRows != null && held == null) held = favoritesRows
        if (held != null) {
            takenOutOffList = if (isFavorite || inSystemList) takenOutOffList - gameKey else takenOutOffList + gameKey
        }
        pending[gameKey] = isFavorite
    }

    /** The write of [isFavorite] failed: the row shows what the library holds again. */
    fun failed(
        gameKey: String,
        isFavorite: Boolean,
    ) {
        if (pending[gameKey] == isFavorite) pending.remove(gameKey)
    }

    /** [gameKey] was hidden from its menu: it leaves the held list and nothing about it stays pending. */
    fun hidden(gameKey: String) {
        takenOutOffList = takenOutOffList - gameKey
        pending.remove(gameKey)
    }

    /** The person left the Favorites list: its rows follow the library again. */
    fun release() {
        held = null
        takenOutOffList = emptySet()
    }

    /**
     * Drops the pending marks [live] (the library's content) already agrees with, and those of
     * games no list has any more (hidden, removed by a rescan: their mark can never show).
     */
    fun settle(live: HomeContent) {
        if (pending.isEmpty()) return
        val stored = live.favoriteMarksOf(pending.keys)
        val listed = live.listedGameIds()
        pending.keys
            .filter { key -> stored[key] == pending[key] || (key !in listed && key !in takenOutOffList) }
            .forEach(pending::remove)
    }

    /** [live] as the lists show it: pending marks applied, Favorites held while the person is in it. */
    fun shown(live: HomeContent): HomeContent {
        val marks = pending.toMap()
        val frozen = held
        if (marks.isEmpty() && frozen == null) return live
        val entries =
            live.entries.map { entry ->
                if (entry.kind == EntryKind.Favorites) {
                    favoritesShown(entry, frozen, marks, live)
                } else {
                    entry.withMarks(marks)
                }
            }
        // Hidden systems' games are found by Search, which reads these too: their star flips at once as well.
        return live.withEntries(entries, live.hiddenEntries.map { it.withMarks(marks) })
    }

    private fun favoritesShown(
        live: HomeEntry,
        frozen: List<GameRowModel>?,
        marks: Map<String, Boolean>,
        content: HomeContent,
    ): HomeEntry {
        val rows =
            if (frozen == null) {
                live.rows
            } else {
                val current = live.rows.associateBy { it.id }
                val inLibrary = content.systemGameIds()
                val out = takenOutOffList
                val kept = frozen.filter { it.id in current || it.id in inLibrary || it.id in out }.map { current[it.id] ?: it.copy(isFavorite = false) }
                val keptIds = kept.mapTo(HashSet()) { it.id }
                kept + live.rows.filter { it.id !in keptIds }
            }
        val marked = rows.withMarks(marks)
        val listed = marked.mapTo(HashSet()) { it.id }
        // Favorites the library has not listed yet count at once (the count updates on Y), but only
        // games a system still lists: a late Y on a game that left the library adds nothing.
        val inSystems = if (marks.any { it.value && it.key !in listed }) content.systemGameIds() else emptySet()
        val added = marks.count { (key, isFavorite) -> isFavorite && key !in listed && key in inSystems }
        return live.copy(rows = marked, shownCount = marked.count { it.isFavorite } + added)
    }
}

/**
 * Connects the game menu's favorite writes (Y and the menu's entry share them) to [marks]: Y in the
 * Favorites list (not from search) holds that list as it is on screen.
 */
internal class HomeFavoriteListener(
    private val marks: FavoriteMarks,
    private val state: StripState,
    private val search: SearchState,
    private val shown: () -> HomeContent,
) : FavoriteListener {
    override fun requested(
        game: GameRef,
        isFavorite: Boolean,
    ) {
        val inFavorites = !search.isOpen && isInFavoritesList(state.focus)
        val content = shown()
        val rows = if (inFavorites) content.entries.firstOrNull { it.kind == EntryKind.Favorites }?.rows else null
        val inSystemList = content.systemEntries().any { entry -> entry.rows.any { it.id == game.key } }
        marks.requested(game.key, isFavorite, rows, inSystemList)
    }

    override fun failed(
        game: GameRef,
        isFavorite: Boolean,
    ) = marks.failed(game.key, isFavorite)

    override fun hidden(game: GameRef) = marks.hidden(game.key)
}

/** The cursor is on the Favorites list (its games, or the details stop of one). */
internal fun isInFavoritesList(focus: StripFocus): Boolean = focus.systemId == HomeContent.FAVORITES_ID && focus.column != Column.Systems

/** Lets the held Favorites go as soon as the cursor leaves the list (to the system column or another list). */
@Composable
internal fun ReleaseFavoritesWhenLeft(
    marks: FavoriteMarks,
    state: StripState,
) {
    LaunchedEffect(marks, state) {
        snapshotFlow { isInFavoritesList(state.focus) }.collect { inside -> if (!inside) marks.release() }
    }
}

private fun HomeEntry.withMarks(marks: Map<String, Boolean>): HomeEntry {
    if (marks.isEmpty() || kind == EntryKind.Apps) return this
    val marked = rows.withMarks(marks)
    return if (marked === rows) this else copy(rows = marked)
}

/** The same list instance when no row changes, so Compose skips the entry. */
private fun List<GameRowModel>.withMarks(marks: Map<String, Boolean>): List<GameRowModel> {
    if (marks.isEmpty() || none { (marks[it.id] ?: it.isFavorite) != it.isFavorite }) return this
    return map { row -> marks[row.id]?.let { if (it != row.isFavorite) row.copy(isFavorite = it) else row } ?: row }
}

/**
 * The stored mark of each of [keys]: from a system's list, else true when Favorites lists it, else
 * false (in no list: not a favorite, or a copy that is not the primary one and not a favorite).
 */
private fun HomeContent.favoriteMarksOf(keys: Set<String>): Map<String, Boolean> {
    val found = HashMap<String, Boolean>()
    for (entry in systemEntries()) {
        for (row in entry.rows) if (row.id in keys) found[row.id] = row.isFavorite
    }
    val favorites = entries.firstOrNull { it.kind == EntryKind.Favorites }?.rows.orEmpty().mapTo(HashSet()) { it.id }
    for (key in keys) if (key !in found) found[key] = key in favorites
    return found
}

/**
 * Every system's list, those hidden from the column (Settings › Library › Systems) included: their
 * games are still in the library, in Search, Recent and Favorites.
 */
private fun HomeContent.systemEntries(): List<HomeEntry> = entries.filter { it.kind == EntryKind.System } + hiddenEntries

/** Every game a system's list (shown or hidden) or Favorites holds. */
private fun HomeContent.listedGameIds(): Set<String> =
    (systemEntries() + entries.filter { it.kind == EntryKind.Favorites }).flatMapTo(HashSet()) { entry -> entry.rows.map { it.id } }

private fun HomeContent.systemGameIds(): Set<String> = systemEntries().flatMapTo(HashSet()) { entry -> entry.rows.map { it.id } }
