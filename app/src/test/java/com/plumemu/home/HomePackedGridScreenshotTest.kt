package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Cover grids packed in rows of one height with boxes at real shapes (drawn in memory, no publisher artwork): the Super
 * Nintendo with US landscape and Japanese portrait boxes and one poster, and Favorites mixing Super Nintendo, Mega Drive,
 * Game Boy Advance and Game Boy. At 768x432, 576x432 and the Pocket 6's 832x468, Graphite and Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomePackedGridScreenshotTest {
    @Test
    fun superNintendoMixedBoxes() = ReferenceShots.capture("home_grid_packed_snes", sizes = ReferenceShots.Size.WithPocket6) { PackedSnesBoard(focused = 1) }

    @Test
    fun favoritesMixedSystems() = ReferenceShots.capture("home_grid_packed_favorites", sizes = ReferenceShots.Size.WithPocket6) { PackedFavoritesBoard(focused = 2) }
}
