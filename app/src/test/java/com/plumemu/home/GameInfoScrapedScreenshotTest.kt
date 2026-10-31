package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.textsize.FULL_SCRAPED
import com.plumemu.textsize.scrapedGameFacts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Info with ScreenScraper's year, publisher, developer, genre, players and a long description (it
 * scrolls above Back), at the default and the largest text size; and a games list in the order
 * Settings › Library › Sort games › Year gives (oldest first, the years shown on the rows).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GameInfoScrapedScreenshotTest {
    private val panel = OpenPanel(GameRef("snes-6", "Super Metroid"), PanelScreen.Info(scrapedGameFacts(FULL_SCRAPED)))

    @Test
    fun infoWithEveryRow() = ReferenceShots.capture("game_info_scraped") { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }

    @Test
    fun infoWithEveryRowAtLargest() = ReferenceShots.capture("game_info_scraped", textSize = TextSize.Largest) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }

    /** The year-sorted order, as the library gives it: oldest first, by name within a year. */
    @Test
    fun listSortedByYear() = ReferenceShots.capture("home_list_sorted_by_year") { HomeAt(sortedByYear(FakeHomeContent.withArtwork()), BoardGamesFocused) }

    private fun sortedByYear(content: HomeContent): HomeContent {
        val systems =
            content.entries.filter { it.kind == EntryKind.System }.map { entry ->
                val games = entry.rows.sortedWith(compareBy({ it.details?.year ?: Int.MAX_VALUE }, { it.title }))
                SystemGames(entry.id, entry.name.orEmpty(), games, entry.coverShape, code = entry.code)
            }
        fun rows(kind: EntryKind) = content.entries.first { it.kind == kind }.rows
        return HomeContent.of(rows(EntryKind.Recent), rows(EntryKind.Favorites), rows(EntryKind.Apps), systems, hasBoxArt = true)
    }
}
