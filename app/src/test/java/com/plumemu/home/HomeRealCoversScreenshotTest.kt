package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The main screen with box fronts that look like real ones ([RealLookingCoverImages]: drawn, not a
 * publisher's artwork) at the real boxes' shapes: the Super Nintendo's wide boxes, Mega Drive and
 * PSP portrait boxes, PlayStation's square CD cases, the focused cover on the top-left and on the
 * shelf, and the details of a wide and a portrait box. At 768x432 and 576x432, Graphite and Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeRealCoversScreenshotTest {
    @Test
    fun superNintendoGridFocusedTopLeft() = ReferenceShots.capture("home_art_real_grid_snes") { RealCoverGrid("snes", 0, GridSize.Large) }

    @Test
    fun megaDriveGridPortraitBoxesInWideCells() = ReferenceShots.capture("home_art_real_grid_megadrive") { RealCoverGrid("megadrive", 4, GridSize.Large) }

    @Test
    fun playStationGridSquareCases() = ReferenceShots.capture("home_art_real_grid_psx") { RealCoverGrid("psx", 4, GridSize.Large) }

    @Test
    fun pspGridPortrait() = ReferenceShots.capture("home_art_real_grid_psp") { RealCoverGrid("psp", 0) }

    @Test
    fun detailsWideBox() = ReferenceShots.capture("home_art_real_details_snes") { RealCoverDetails("snes", FakeHomeContent.BOARD_REMEMBERED_ROW) }

    @Test
    fun detailsPortraitBox() = ReferenceShots.capture("home_art_real_details_psp") { RealCoverDetails("psp", PERSONA_3_PORTABLE) }
}
