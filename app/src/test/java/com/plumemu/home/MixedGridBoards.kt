package com.plumemu.home

import androidx.compose.runtime.Composable
import com.plumemu.coreinput.Action
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.ProvideGridSize

/*
 * Recent and Favorites shown as grids: games of many systems, each
 * system in its own real shape (realAspect), the cells of the square class. One game has a result
 * without pictures, so its placeholder stands in its system's shape.
 */

/** The games Recent holds, as (system, row of that system's board list): landscape, tall and square shapes mixed. */
private val MIXED_PICKS =
    listOf(
        "snes" to 0, "psp" to 1, "psx" to 2, "nes" to 3, "gba" to 1, "nds" to 2,
        "n64" to 4, "megadrive" to 0, "dreamcast" to 1, "gc" to 2, "snes" to 3, "ps2" to 0,
    )

/** Recent's row (index in [MixedGridLibrary]'s Recent) whose result has no pictures: a tall NES game. */
internal const val MIXED_NO_ART_INDEX = 3

/** The board with box art, every system in its real shape, Recent holding [MIXED_PICKS] and Favorites the same games newest last. */
internal val MixedGridLibrary: HomeContent by lazy {
    val board = FakeHomeContent.withArtwork()
    val systems =
        board.entries.filter { it.kind == EntryKind.System }.map { entry ->
            SystemGames(entry.id, entry.name.orEmpty(), entry.rows, CoverShape(realAspect(entry.id, 0)))
        }
    val recent = MIXED_PICKS.map { (id, index) -> systems.first { it.id == id }.games[index] }
    val favorites = recent.reversed().map { it.copy(isFavorite = true) }
    val apps = board.entries.first { it.kind == EntryKind.Apps }.rows
    HomeContent.of(recent, favorites, apps, systems, hasBoxArt = true).asLibrary()
}

/** Real-looking covers for [MixedGridLibrary]'s games at their systems' shapes; [MIXED_NO_ART_INDEX] without pictures. */
internal fun mixedGridSource(): FakeArtSource =
    realCoverSource(MixedGridLibrary).apply {
        val key = artKey(MixedGridLibrary.recentRows[MIXED_NO_ART_INDEX].id)
        results.value = results.value + (key to results.value.getValue(key).copy(grid = null, large = null))
    }

/** The board's entry [entryIndex]: 0 is Recent (a folder inside Collections), 1 is Favorites (the column's first entry). Kept so the boards read as before the Collections change. */
internal fun HomeContent.mixedEntry(entryIndex: Int): HomeEntry = if (entryIndex == 0) recentEntry else entries[0]

/** Open [entryIndex]'s grid (see [mixedEntry]) with game [focused] focused. */
internal fun mixedGridActions(entryIndex: Int, focused: Int): List<Action> = if (entryIndex == 0) recentGridActions(focused) else gridActions(0, focused)

/** Recent ([entryIndex] 0) or Favorites (1) as a grid at [gridSize], game [focused] focused. */
@Composable
internal fun MixedGridBoard(
    entryIndex: Int,
    focused: Int,
    gridSize: GridSize = GridSize.Small,
) {
    val id = MixedGridLibrary.mixedEntry(entryIndex).id
    ProvideGridSize(gridSize) {
        HomeWithArt(rememberSource { mixedGridSource() }, mixedGridActions(entryIndex, focused), MixedGridLibrary, gridIds = setOf(id))
    }
}
