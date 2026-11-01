package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * Installed apps listed as games: an "Android games" system made only of app games, and a "Ports" system that holds
 * files and app games side by side. Made-up packages; icons from FakeAppIcons, keyed by package.
 */

/** Recent, Favorites, Android games, Ports (no Apps entry). */
internal const val ANDROID_GAMES_ENTRY = 2
internal const val PORTS_ENTRY = 3

private val ANDROID_TITLES =
    listOf(
        "Sky Racer", "Pixel Quest", "Tower Defense Deluxe", "Chess", "Space Pilot Adventures of the Forgotten Galaxy", "Puzzle Pop",
        "Sudoku", "Farm Story", "Dungeon Crawl", "Solitaire", "Bubble Shooter", "Word Trek",
    )

/** The package of the app game [index] of Android games. */
internal fun gamePackage(index: Int) = "com.example.game$index"

private fun appGameRow(index: Int, title: String, systemId: String) =
    GameRowModel(id = (100 + index).toString(), title = title, region = "", systemAbbr = if (systemId == "android") "AND" else "PRT", systemId = systemId, sortName = title.lowercase(), appPackage = gamePackage(index))

/** The rows of Android games: every one an app game. */
internal val AndroidGameRows: List<GameRowModel> = ANDROID_TITLES.mapIndexed { i, title -> appGameRow(i, title, "android") }

/** The rows of Ports: files (no package) at 0, 1 and 4, app games at 2, 3 and 5. */
internal val PortsRows: List<GameRowModel> =
    listOf(
        GameRowModel("200", "Quake", region = "", systemAbbr = "PRT", systemId = "ports", sortName = "quake", scrapeKey = "ports-quake"),
        GameRowModel("201", "Doom", region = "", systemAbbr = "PRT", systemId = "ports", sortName = "doom", scrapeKey = "ports-doom"),
        appGameRow(20, "Delta Touch", "ports"),
        appGameRow(21, "Ship of Harkinian", "ports"),
        GameRowModel("202", "Wolfenstein", region = "", systemAbbr = "PRT", systemId = "ports", sortName = "wolfenstein", scrapeKey = "ports-wolf"),
        appGameRow(22, "Mario 64 Port", "ports"),
    )

/** Android games and Ports, signed out of ScreenScraper unless [hasBoxArt]. */
internal fun appGamesBoard(hasBoxArt: Boolean = false): HomeContent =
    HomeContent.of(
        emptyList(),
        emptyList(),
        emptyList(),
        listOf(SystemGames("android", "Android games", AndroidGameRows), SystemGames("ports", "Ports", PortsRows)),
        hasBoxArt = hasBoxArt,
        showApps = false,
    )

/** Icons whose lookup throws, as a broken package manager would. */
internal class ThrowingAppIcons : HomeAppIcons {
    override val generation: StateFlow<Int> = MutableStateFlow(0)

    override fun cached(rowId: String, sizePx: Int): ImageBitmap? = null

    override suspend fun load(rowId: String, sizePx: Int): ImageBitmap? = error("package manager is gone")
}

/** [content] with [icons] provided, [gridIds] shown as grids, the focus moved by [actions]. */
@Composable
internal fun AppGamesBoard(
    entry: Int,
    focused: Int = 0,
    grid: Boolean = true,
    hasBoxArt: Boolean = false,
    icons: HomeAppIcons = remember { FakeAppIcons(missing = setOf(gamePackage(3))) },
) {
    val content = remember(hasBoxArt) { appGamesBoard(hasBoxArt) }
    val id = content.entries[entry].id
    CompositionLocalProvider(LocalHomeAppIcons provides icons) {
        HomeAt(content, gridActions(entry, focused), gridIds = if (grid) setOf(id) else emptySet())
    }
}
