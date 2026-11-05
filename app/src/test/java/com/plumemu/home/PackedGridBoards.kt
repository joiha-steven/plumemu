package com.plumemu.home

import androidx.compose.runtime.Composable
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.ProvideGridSize

/*
 * Boards for the packed cover grids (rows of one height, each box as wide as its own shape), with box fronts drawn in
 * memory at real shapes (RealLookingCovers.kt): one Super Nintendo with US landscape and Japanese portrait boxes and a
 * poster, and a Favorites that mixes Super Nintendo, Mega Drive, Game Boy Advance and Game Boy.
 */

/** The Super Nintendo's boxes: every fifth and third-after game is a Japanese portrait box (about 0.71), the rest US boxes (1.40). */
internal fun packedSnesAspect(
    systemId: String,
    index: Int,
): Float = if (systemId == "snes") (if (index % 5 == 1 || index % 5 == 3) 0.71f else 1.40f) else realAspect(systemId, index)

/** The Super Nintendo game with no result at all: its poster stands among the pictures. */
internal const val PACKED_SNES_POSTER = 4

/** Results for the board's Super Nintendo at [packedSnesAspect]'s shapes, [PACKED_SNES_POSTER] without a result. */
internal fun packedSnesSource(): FakeArtSource =
    realCoverSource(aspectOf = ::packedSnesAspect).apply {
        val row = FakeHomeContent.withArtwork().entries[boardIndexOf("snes")].rows[PACKED_SNES_POSTER]
        results.value = results.value - artKey(row.id)
    }

/** The Super Nintendo's grid at [gridSize], game [focused] focused. */
@Composable
internal fun PackedSnesBoard(
    focused: Int,
    gridSize: GridSize = GridSize.Medium,
    onState: (com.plumemu.coreinput.strip.StripState) -> Unit = {},
) = ProvideGridSize(gridSize) {
    HomeWithArt(rememberSource { packedSnesSource() }, gridActions(boardIndexOf("snes"), focused), gridIds = setOf("snes"), onState = onState)
}

/** Favorites' games as (system, row of that system's board list): landscape, portrait and square boxes in turn. */
internal val FAVORITE_PICKS =
    listOf(
        "snes" to 0, "megadrive" to 0, "gba" to 1, "gb" to 0, "snes" to 1, "megadrive" to 2, "gb" to 1, "gba" to 3,
        "snes" to 5, "megadrive" to 4, "gb" to 2, "snes" to 6, "gba" to 5, "snes" to 7, "megadrive" to 6, "gb" to 3,
    )

/** Recent and Favorites over [FAVORITE_PICKS]; Favorites is entry 1. */
internal val PackedFavoritesLibrary: HomeContent by lazy {
    val board = FakeHomeContent.withArtwork()
    val systems =
        board.entries.filter { it.kind == EntryKind.System }.map { entry ->
            SystemGames(entry.id, entry.name.orEmpty(), entry.rows, CoverShape(realAspect(entry.id, 0)))
        }
    val favorites = FAVORITE_PICKS.map { (id, index) -> systems.first { it.id == id }.games[index].copy(isFavorite = true) }
    val apps = board.entries.first { it.kind == EntryKind.Apps }.rows
    HomeContent.of(favorites.take(3), favorites, apps, systems, hasBoxArt = true).asLibrary()
}

/** Favorites ([PackedFavoritesLibrary]) as a grid at [gridSize], game [focused] focused. */
@Composable
internal fun PackedFavoritesBoard(
    focused: Int,
    gridSize: GridSize = GridSize.Medium,
    onState: (com.plumemu.coreinput.strip.StripState) -> Unit = {},
) = ProvideGridSize(gridSize) {
    HomeWithArt(
        rememberSource { realCoverSource(PackedFavoritesLibrary) },
        gridActions(1, focused),
        PackedFavoritesLibrary,
        gridIds = setOf(HomeContent.FAVORITES_ID),
        onState = onState,
    )
}
