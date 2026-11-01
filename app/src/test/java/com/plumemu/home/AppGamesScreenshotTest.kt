package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Installed apps listed as games, signed out of ScreenScraper, 768x432 and 576x432, Graphite and Paper: the Android games
 * grid (Apps-style tiles, one app without an icon showing its letter, long names), its list with the details column
 * (768 only; 576 shows the list alone) and the Ports grid where app games stand among files wearing posters.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class AppGamesScreenshotTest {
    @Test
    fun androidGamesGrid() = ReferenceShots.capture("home_grid_app_games") { AppGamesBoard(ANDROID_GAMES_ENTRY, focused = 1) }

    @Test
    fun androidGamesList() = ReferenceShots.capture("home_list_app_games") { AppGamesBoard(ANDROID_GAMES_ENTRY, focused = 1, grid = false) }

    @Test
    fun portsGridWithFilesAndApps() = ReferenceShots.capture("home_grid_app_games_ports") { AppGamesBoard(PORTS_ENTRY, focused = 2) }

    @Test
    fun favoritesWithBoxArtPostersAndAppGames() = ReferenceShots.capture("home_grid_app_games_favorites") { AppGameMixedBoard(focused = 5) }
}
