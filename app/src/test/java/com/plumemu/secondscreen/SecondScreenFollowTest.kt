package com.plumemu.secondscreen

import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.home.GameDetails
import com.plumemu.home.PlayFacts
import com.plumemu.home.SnesInfo
import com.plumemu.home.GameRowModel
import com.plumemu.home.HomeContent
import com.plumemu.home.SystemGames
import com.plumemu.home.search.SearchHit
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every row of the design's "What fills it" table, derived from the main screen's state. */
class SecondScreenFollowTest {
    private val names = EntryNames(recent = "Recent", favorites = "Favorites")

    private fun details(
        daysAgo: Int?,
        minutes: Int,
    ) = GameDetails(year = 1994, publisher = "Nintendo", genre = "Action", players = "1", description = "", play = PlayFacts(lastPlayedDaysAgo = daysAgo, playTimeMinutes = minutes, emulator = ""))

    private val metroid = GameRowModel("1", "Super Metroid", "USA", details(1, 200), isFavorite = true)
    private val zelda = GameRowModel("2", "Zelda", "", null)
    private val sonic = GameRowModel("3", "Sonic", "EUR", details(null, 0))
    private val app = GameRowModel("org.app/.Main", "Files", "")

    private val content =
        HomeContent.of(
            recent = listOf(sonic, metroid),
            favorites = listOf(metroid),
            apps = listOf(app),
            systems =
                listOf(
                    SystemGames("snes", "Super Nintendo", listOf(metroid, zelda)),
                    SystemGames("md", "Mega Drive", listOf(sonic)),
                ),
        )
    private val systems = GameSystems.of(content)

    // Entries: 0 Recent, 1 Favorites, 2 Apps, 3 Super Nintendo, 4 Mega Drive.
    private fun at(
        systemIndex: Int,
        column: Column = Column.Systems,
        row: Int = 0,
    ): HomeLook {
        val id = content.entries[systemIndex].id
        return HomeLook(StripFocus(column = column, systemIndex = systemIndex, systemId = id, rows = mapOf(id to row)))
    }

    private fun derive(
        look: HomeLook,
        on: HomeContent = content,
    ) = secondScreenContentOf(on, look, if (on === content) systems else GameSystems.of(on), names)

    /** Its play facts are read after the settle (SecondScreenPlayFacts), never taken from the row's details. */
    @Test
    fun aGameInItsSystem() {
        assertEquals(
            SecondScreenContent.Game("Super Metroid", "Super Nintendo", "USA", isFavorite = true, gameId = 1, isPlayKnown = false),
            derive(at(3, Column.Games, 0)),
        )
    }

    @Test
    fun aGameWithNoRegionAndNoDetails() {
        assertEquals(SecondScreenContent.Game("Zelda", "Super Nintendo", region = null, gameId = 2, isPlayKnown = false), derive(at(3, Column.Games, 1)))
    }

    @Test
    fun aGameWhoseIdIsNotTheDatabasesHasNothingToRead() {
        val fake = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("gb", "Game Boy", listOf(GameRowModel("gb-1", "Tetris", "")))))
        assertEquals(SecondScreenContent.Game("Tetris", "Game Boy", gameId = null, isPlayKnown = false), derive(at(3, Column.Games, 0), on = fake))
    }

    @Test
    fun theDetailsStopShowsTheSameGame() {
        assertEquals(derive(at(3, Column.Games, 1)), derive(at(3, Column.Details, 1)))
    }

    @Test
    fun aGameInRecentNamesItsSystem() {
        val card = derive(at(0, Column.Games, 0)) as SecondScreenContent.Game
        assertEquals("Sonic", card.title)
        assertEquals("Mega Drive", card.systemName)
        assertEquals("EUR", card.region)
        assertEquals(3L, card.gameId)
        assertEquals("its facts are read after the settle", false, card.isPlayKnown)
    }

    /** The figures are read after the settle: a card derived at the cursor is its header only. */
    @Test
    fun aSystemIsItsHeaderUntilItsFiguresAreRead() {
        val snes = derive(at(3)) as SystemEntry
        assertEquals("snes", snes.systemId)
        assertEquals("Super Nintendo", snes.name)
        assertEquals(2, snes.gameCount)
        assertEquals(null, snes.figures)
        assertEquals("Mega Drive", (derive(at(4)) as SystemEntry).name)
    }

    /** A system's card carries its badge and facts; a system without info carries none. */
    @Test
    fun aSystemCarriesItsInfo() {
        val withInfo = content.withEntries(content.entries.map { if (it.id == "snes") it.copy(info = SnesInfo) else it })
        assertEquals(SnesInfo, (derive(at(3), on = withInfo) as SystemEntry).info)
        assertEquals(null, (derive(at(4), on = withInfo) as SystemEntry).info)
    }

    @Test
    fun recentCarriesItsCountAndFavoritesItsFavouritesInOrder() {
        val recent = derive(at(0)) as RecentEntry
        assertEquals("Recent", recent.name)
        assertEquals(2, recent.gameCount)
        assertEquals(null, recent.figures)
        val favorites = derive(at(1)) as FavoritesEntry
        assertEquals("Favorites", favorites.name)
        assertEquals(1, favorites.gameCount)
        assertEquals(listOf(1L), favorites.games.map { it.id })
        assertEquals(1, favorites.systemCount)
        assertEquals("no art to read while box art is off", true, favorites.isArtRead)
    }

    @Test
    fun anEmptyRecentOrFavoritesHasNoGames() {
        val none = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("gb", "Game Boy", listOf(zelda))))
        assertEquals(0, (derive(at(0), on = none) as RecentEntry).gameCount)
        val favorites = derive(at(1), on = none) as FavoritesEntry
        assertEquals(0, favorites.gameCount)
        assertEquals(emptyList<EntryGame>(), favorites.games)
    }

    @Test
    fun appsAndAnApp() {
        assertEquals(SecondScreenContent.Apps(1, listOf(AppIconRef("org.app/.Main", "Files"))), derive(at(2)))
        assertEquals(SecondScreenContent.App("Files", "org.app/.Main"), derive(at(2, Column.Games, 0)))
    }

    @Test
    fun anEmptyLibraryIsCalm() {
        assertEquals(SecondScreenContent.Calm, derive(at(0), on = HomeContent.of(emptyList(), emptyList(), listOf(app), emptyList())))
        assertEquals(SecondScreenContent.Calm, derive(HomeLook(StripFocus()), on = HomeContent.Empty))
    }

    @Test
    fun searchWithAResultFocusedShowsTheGame() {
        val look = at(3).copy(isSearchOpen = true, searchHit = SearchHit(sonic, "md"))
        assertEquals(SecondScreenContent.Game("Sonic", "Mega Drive", "EUR", gameId = 3, isPlayKnown = false), derive(look))
    }

    @Test
    fun searchWithNoResultFocusedIsCalm() {
        assertEquals(SecondScreenContent.Calm, derive(at(3, Column.Games, 0).copy(isSearchOpen = true)))
    }

    @Test
    fun theGameMenuShowsItsGame() {
        // Opened from search while the strip is on Mega Drive: the menu's game wins.
        val look = at(4).copy(isSearchOpen = true, searchHit = SearchHit(sonic, "md"), menuGameKey = metroid.id)
        assertEquals(derive(at(3, Column.Games, 0)), derive(look))
    }

    @Test
    fun aFocusPastTheEntriesIsCalm() {
        assertEquals(SecondScreenContent.Calm, derive(HomeLook(StripFocus(systemIndex = 99))))
    }

    @Test
    fun aCoverCaptionIsTheDisplayNameUnlessItIsLong() {
        // The board's labels: SNES, GBA, Mega Drive, PlayStation, Game Boy (the platform data's short names are SNES, GBA, Mega Drive, PS1, GB).
        val captions = listOf("Super Nintendo" to "SNES", "Game Boy Advance" to "GBA", "Mega Drive" to "Mega Drive", "PlayStation" to "PS1", "Game Boy" to "GB").map { (name, short) -> coverCaptionOf(name, short) }
        assertEquals(listOf("SNES", "GBA", "Mega Drive", "PlayStation", "Game Boy"), captions)
        assertEquals("a long name without a short one keeps its name", "Game Boy Advance", coverCaptionOf("Game Boy Advance", ""))
    }
}
