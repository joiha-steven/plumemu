package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Recent and Favorites as grids: games of many systems, each cover at
 * its own system's shape in the square class's cells (Small, the default size), one NES game
 * without pictures standing as a tall placeholder. Recent focuses the PSP game (a tall ring),
 * Favorites its first game. At 768x432 and 576x432, Graphite and Paper, and at Largest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeMixedGridScreenshotTest {
    @Test
    fun recentGrid() = ReferenceShots.capture("home_grid_recent") { MixedGridBoard(entryIndex = 0, focused = 1) }

    @Test
    fun recentGridAtLargestText() = ReferenceShots.capture("home_grid_recent", textSize = TextSize.Largest) { MixedGridBoard(entryIndex = 0, focused = 1) }

    @Test
    fun favoritesGrid() = ReferenceShots.capture("home_grid_favorites") { MixedGridBoard(entryIndex = 1, focused = 0) }

    @Test
    fun favoritesGridAtLargestText() = ReferenceShots.capture("home_grid_favorites", textSize = TextSize.Largest) { MixedGridBoard(entryIndex = 1, focused = 0) }
}
