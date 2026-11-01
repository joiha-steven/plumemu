package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.ProvideGridSize

/*
 * Favorites with box art, a poster (a game with no pictures) and app games side by side: the cover cards of
 * `AppGameCover`. Indexes of the app games among the 12 favorites, and the one with no icon.
 */
internal val FavoriteAppGameIndexes = listOf(2, 5, 8)
internal const val FAVORITE_APP_WITHOUT_ICON = 8

private fun favoriteAppGame(index: Int): GameRowModel {
    val ports = index == 5
    val title = listOf("Sky Racer", "Delta Touch", "Pixel Quest")[FavoriteAppGameIndexes.indexOf(index)]
    return GameRowModel(
        id = (300 + index).toString(),
        title = title,
        region = "",
        systemAbbr = if (ports) "PRT" else "AND",
        systemId = if (ports) "ports" else "android",
        sortName = title.lowercase(),
        isFavorite = true,
        appPackage = "com.example.fav$index",
    )
}

/** [MixedGridLibrary]'s Favorites with the app games put in. */
internal val AppGameMixedLibrary: HomeContent by lazy {
    val base = MixedGridLibrary
    val systems =
        base.entries.filter { it.kind == EntryKind.System }.map { SystemGames(it.id, it.name.orEmpty(), it.rows, it.coverShape) } +
            listOf(
                SystemGames("android", "Android games", AndroidGameRows, CoverShape(1f)),
                SystemGames("ports", "Ports", PortsRows, CoverShape(1f)),
            )
    val favorites = base.entries[1].rows.mapIndexed { i, row -> if (i in FavoriteAppGameIndexes) favoriteAppGame(i) else row }
    HomeContent.of(base.entries[0].rows, favorites, emptyList(), systems, hasBoxArt = true, showApps = false).asLibrary()
}

/** Favorites as a grid, game [focused] focused, icons by package, one app without an icon. */
@Composable
internal fun AppGameMixedBoard(
    focused: Int = 0,
    gridSize: GridSize = GridSize.Small,
) {
    val icons = remember { FakeAppIcons(missing = setOf("com.example.fav$FAVORITE_APP_WITHOUT_ICON")) }
    ProvideGridSize(gridSize) {
        CompositionLocalProvider(LocalHomeAppIcons provides icons) {
            HomeWithArt(rememberSource { mixedGridSource() }, gridActions(1, focused), AppGameMixedLibrary, gridIds = setOf(AppGameMixedLibrary.entries[1].id))
        }
    }
}
