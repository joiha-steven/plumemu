package com.plumemu.home

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Favorites and Recent with no game yet, as the main screen shows them: `home_favorites_empty` and `home_recent_empty` at 768x432 and 576x432 (Graphite and Paper). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeEmptyFavoritesScreenshotTest {
    @Test
    fun emptyFavorites() =
        ReferenceShots.capture("home_favorites_empty", sizes = ReferenceShots.Size.entries) {
            HomeAt(FakeHomeContent.noFavorites(), listOf(Action.TapItem(Column.Systems, FAVORITES_INDEX)))
        }

    @Test
    fun emptyRecent() =
        ReferenceShots.capture("home_recent_empty", sizes = ReferenceShots.Size.entries) {
            HomeAt(FakeHomeContent.noRecent(), listOf(Action.TapItem(Column.Systems, 0)))
        }

    private companion object {
        const val FAVORITES_INDEX = 1
    }
}
