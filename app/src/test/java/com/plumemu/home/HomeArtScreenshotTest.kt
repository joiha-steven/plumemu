package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.ProvideGridSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The main screen with box art, against the boards
 * RP5-2-games and Nova-3-details (details), RP5-3 / Nova-4 (landscape grid) and RP5-4 / Nova-5
 * (portrait grid). Covers are stand-in tiles, not real images.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeArtScreenshotTest {
    @Test
    fun details() = ReferenceShots.capture("home_details", sizes = ReferenceShots.Size.entries) { ArtDetailsBoard() }

    @Test
    fun gridLandscape() = ReferenceShots.capture("home_grid_landscape", sizes = ReferenceShots.Size.entries) { ArtGridLandscapeBoard() }

    @Test
    fun gridPortrait() = ReferenceShots.capture("home_grid_portrait", sizes = ReferenceShots.Size.entries) { ProvideGridSize(GridSize.Medium) { ArtGridPortraitBoard() } }

    /** Not on the board: Nintendo DS at its measured 1.12, 4 / 3 columns at Medium. */
    @Test
    fun gridSquare() = ReferenceShots.capture("home_grid_square") { ProvideGridSize(GridSize.Medium) { ArtGridSquareBoard() } }
}
