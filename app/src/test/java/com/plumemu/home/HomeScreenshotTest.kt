package com.plumemu.home

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The main screen as the alpha shows it (no-art list), against the board's
 * RP5-1-systems, RP5-N-noart, Nova-1 and Nova-2 (no-art variant), plus the states the board does
 * not draw: an empty library and a title too long for its row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeScreenshotTest {
    @Test
    fun systemsFocused() = ReferenceShots.capture("home_systems", sizes = ReferenceShots.Size.entries) { HomeAt(FakeHomeContent.board(), BoardSystemsFocused) }

    @Test
    fun gamesFocused() =
        ReferenceShots.capture("home_games", sizes = ReferenceShots.Size.entries, fonts = PlumFont.entries) { HomeAt(FakeHomeContent.board(), BoardGamesFocused) }

    @Test
    fun emptyLibrary() = ReferenceShots.capture("home_empty") { HomeAt(FakeHomeContent.noGames()) }

    @Test
    fun longTitle() =
        ReferenceShots.capture("home_long_title") {
            // The long title is row 0, focused: weight 500, cut with an ellipsis before the region.
            HomeAt(FakeHomeContent.longTitle(), BoardGamesFocused.dropLast(1) + Action.TapItem(Column.Games, 0))
        }
}
