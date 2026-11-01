package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A system's grid signed out of ScreenScraper: no box art, so every cell wears its game's poster cover. The board's Super Nintendo, its second game focused. 768x432 and 576x432, Graphite and Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeSignedOutGridScreenshotTest {
    @Test
    fun signedOutGrid() =
        ReferenceShots.capture("home_grid_signed_out") {
            val board = FakeHomeContent.board()
            HomeAt(board, gridActions(board.entries.indexOfFirst { it.id == "snes" }, 1), gridIds = setOf("snes"))
        }
}
