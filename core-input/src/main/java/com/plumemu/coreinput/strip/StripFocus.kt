package com.plumemu.coreinput.strip

import androidx.compose.runtime.Immutable
import com.plumemu.coreinput.Column

/**
 * One entry of the system column, in screen order: Recent, Favorites, Apps, then each system
 * with games. [itemCount] is its games (or apps);
 * [gridColumns] is 1 for the list view, else the grid's column count. [hasDetails] false: the
 * entry never has a details column or a details stop, even with box art (Apps: apps have no art).
 * [itemIds] are the items' ids in screen order (empty: rows are kept by index only); with them the
 * focused row follows its game when the list is re-sorted (names arriving during a scan).
 * [hasGridWithoutArt]: SELECT switches the entry's view even without box art (Apps: its icons come
 * from Android, not from ScreenScraper).
 */
@Immutable
data class SystemEntry(
    val id: String,
    val itemCount: Int,
    val gridColumns: Int = 1,
    val hasDetails: Boolean = true,
    val itemIds: List<String> = emptyList(),
    val hasGridWithoutArt: Boolean = false,
)

/** What the strip shows; [hasBoxArt] false means no details stop and no grid but Apps' ([SystemEntry.hasGridWithoutArt]) (wide screens show a text-only details column, which is not a stop). */
@Immutable
data class StripContent(
    val systems: List<SystemEntry>,
    val hasBoxArt: Boolean,
)

/**
 * Where the cursor is (read it through `StripState.focus`, which keeps it valid for the content): the focused [column], the focused system and, per system, the remembered
 * game row, so returning to a system puts the cursor back where it was. The system is kept by
 * [systemId] so a rescan that adds or hides a system keeps it focused; [systemIndex] is the
 * fallback when that system is gone. Likewise the row is kept by the game's id in [rowIds] when
 * the entry gives [SystemEntry.itemIds]: a re-sort (identified names arriving) keeps the same
 * game focused, so A never plays another one; [rows] is the fallback (nearest index) when that
 * game is gone. Set rows only through [withRow], which keeps both maps in step.
 */
@Immutable
data class StripFocus(
    val column: Column = Column.Systems,
    val systemIndex: Int = 0,
    val systemId: String? = null,
    val rows: Map<String, Int> = emptyMap(),
    val rowIds: Map<String, String> = emptyMap(),
) {
    fun rowOf(systemId: String): Int = rows[systemId] ?: 0

    /** Row [row] of [entry] remembered, with its item's id when the entry gives ids. */
    internal fun withRow(entry: SystemEntry, row: Int): StripFocus {
        val clamped = row.coerceIn(0, maxOf(entry.itemCount - 1, 0))
        val id = entry.itemIds.getOrNull(clamped)
        return copy(rows = rows + (entry.id to clamped), rowIds = if (id == null) rowIds - entry.id else rowIds + (entry.id to id))
    }

    /** The remembered row of [entry] in its current order: found again by id, else the nearest index. */
    private fun rowIn(entry: SystemEntry): Int {
        val stored = rowOf(entry.id).coerceIn(0, maxOf(entry.itemCount - 1, 0))
        val id = rowIds[entry.id] ?: return stored
        if (entry.itemIds.getOrNull(stored) == id) return stored
        val found = entry.itemIds.indexOf(id)
        return if (found >= 0) found else stored
    }

    /** The focused game row of the focused system (0 when nothing is focused). */
    val row: Int
        get() = systemId?.let { rowOf(it) } ?: 0

    /**
     * This focus made valid for [content]: the system found again by id (or the nearest index),
     * the row inside the system's games, and the column one the cursor can stand in (no games
     * column for an empty system, no details stop when [hasDetailsStop] is false or the entry
     * has no details).
     *
     * With no systems yet (the library is still loading) the focus is returned unchanged, so a
     * focus restored after process death survives until the systems arrive.
     */
    internal fun reconciled(
        content: StripContent,
        hasDetailsStop: Boolean,
    ): StripFocus {
        val systems = content.systems
        if (systems.isEmpty()) return this
        val byId = systemId?.let { id -> systems.indexOfFirst { it.id == id } } ?: -1
        val index = if (byId >= 0) byId else systemIndex.coerceIn(0, systems.lastIndex)
        val entry = systems[index]
        val row = rowIn(entry)
        val column =
            when {
                entry.itemCount == 0 -> Column.Systems
                column == Column.Details && (!hasDetailsStop || !entry.hasDetails) -> Column.Games
                else -> column
            }
        val newRows = if (rowOf(entry.id) == row) rows else rows + (entry.id to row)
        val idAtRow = entry.itemIds.getOrNull(row)
        val newRowIds = if (idAtRow == null || rowIds[entry.id] == idAtRow) rowIds else rowIds + (entry.id to idAtRow)
        val same = column == this.column && index == systemIndex && entry.id == systemId && newRows === rows && newRowIds === rowIds
        return if (same) this else StripFocus(column, index, entry.id, newRows, newRowIds)
    }
}
