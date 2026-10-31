package com.plumemu.home

import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The letter rail in the system peek in the states the board does not draw:
 * a long list at Largest (a window around the focused letter), a list with one letter, Favorites, and
 * Recent (an empty peek). The board's own games shot (`home_games`) shows the usual rail.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeLetterRailScreenshotTest {
    private fun gamesFocused(
        entry: Int,
        row: Int,
    ) = listOf(Action.TapItem(Column.Systems, entry), Action.Enter, Action.TapItem(Column.Games, row))

    private fun system(titles: List<String>) = SystemGames("snes", "Super Nintendo", titles.mapIndexed { i, t -> GameRowModel("g$i", t, "USA") })

    /** "#" first, then A to Z: 27 letters. */
    private val alphabet = listOf("1942", "007 Nightfire") + ('A'..'Z').flatMap { listOf("${it}ar Game", "${it}ar Quest") }

    @Test
    fun longListAtLargest() =
        ReferenceShots.capture("home_rail_long", textSize = TextSize.Largest) {
            HomeAt(HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(system(alphabet))).withLeftEdge(LeftEdgeSetting.Letters), gamesFocused(FIRST_SYSTEM_INDEX, 30))
        }

    @Test
    fun oneLetter() =
        ReferenceShots.capture("home_rail_one") {
            HomeAt(HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(system(listOf("Sonic", "Space Harrier", "Streets of Rage")))).withLeftEdge(LeftEdgeSetting.Letters), gamesFocused(FIRST_SYSTEM_INDEX, 1))
        }

    @Test
    fun favorites() = ReferenceShots.capture("home_rail_favorites") { HomeAt(FakeHomeContent.mixedSystems().withLeftEdge(LeftEdgeSetting.Letters), gamesFocused(1, 2)) }

    @Test
    fun recentShowsCountsInThePeek() = ReferenceShots.capture("home_rail_recent") { HomeAt(FakeHomeContent.mixedSystems().withLeftEdge(LeftEdgeSetting.Letters), gamesFocused(0, 2)) }
}
