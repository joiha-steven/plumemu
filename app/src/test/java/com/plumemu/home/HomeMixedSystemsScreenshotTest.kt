package com.plumemu.home

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Recent and Search holding games of several systems: the right-hand column shows each game's system (SNES, GBA, PS1 ...). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeMixedSystemsScreenshotTest {
    @Test
    fun recentWithGamesOfManySystems() =
        ReferenceShots.capture("home_mixed_recent") {
            HomeAt(FakeHomeContent.mixedSystems(), listOf(Action.TapItem(Column.Systems, 0), Action.Enter))
        }

    @Test
    fun searchResultsFromManySystems() =
        ReferenceShots.capture("home_mixed_search") {
            val content = FakeHomeContent.mixedSystems()
            val search =
                SearchState().apply {
                    open()
                    onQueryChange("final fantasy")
                    showResults("final fantasy", SearchIndex.of(content).find("final fantasy"))
                    toResults()
                }
            HomeAt(content, BoardGamesFocused, search = search)
        }

    /** With box art, Recent at the details stop: the list peek shows years, not clipped short names. */
    @Test
    fun recentWithBoxArtAtTheDetailsStop() =
        ReferenceShots.capture("home_mixed_recent_details") {
            val narrow = com.plumemu.coreui.theme.PlumTheme.layout.widthClass == com.plumemu.coreui.theme.WidthClass.Narrow
            val toRecent = listOf(Action.TapItem(Column.Systems, 0), Action.Enter)
            HomeAt(FakeHomeContent.mixedSystems(withBoxArt = true), if (narrow) toRecent + Action.Move(com.plumemu.coreinput.Direction.Right) else toRecent)
        }
}
