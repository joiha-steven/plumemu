package com.plumemu.home

import com.plumemu.R
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreui.components.GameRowLook
import com.plumemu.coreui.components.SystemRowLook
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
import com.plumemu.settings.ButtonLetters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeContentTest {
    private val board = FakeHomeContent.board()

    @Test
    fun systemColumnIsRecentFavoritesAppsThenSystems() {
        assertEquals(listOf(EntryKind.Recent, EntryKind.Favorites, EntryKind.Apps), board.entries.take(3).map { it.kind })
        assertTrue(board.entries.drop(3).all { it.kind == EntryKind.System })
    }

    @Test
    fun boardNumbersMatchTheBoard() {
        // RP5-1-systems: "18 systems · 1,284 games"; Recent 12, Favorites 9, Apps 41; SNES 248 … Mega Drive 173.
        assertEquals(18, board.systemCount)
        assertEquals(1284, board.gameCount)
        assertEquals(listOf(12, 9, 41), board.entries.take(3).map { it.count })
        assertEquals(
            listOf("Super Nintendo" to 248, "Game Boy Advance" to 96, "PlayStation" to 142, "PSP" to 64, "Nintendo DS" to 88, "GameCube" to 37, "Mega Drive" to 173),
            board.entries.drop(3).take(7).map { it.name to it.count },
        )
        val snes = board.entries[FakeHomeContent.BOARD_SNES_INDEX]
        assertEquals("Super Metroid", snes.rows[FakeHomeContent.BOARD_REMEMBERED_ROW].title)
        // RP5-2-games without art: EarthBound USA, Final Fantasy VI JPN, Super Mario World EUR.
        assertEquals(listOf("USA", "JPN", "EUR"), listOf(2, 3, 5).map { snes.rows[it].region })
    }

    @Test
    fun systemsWithoutGamesAreHidden() {
        val content =
            HomeContent.of(
                recent = emptyList(),
                favorites = emptyList(),
                apps = emptyList(),
                systems = listOf(SystemGames("snes", "Super Nintendo", listOf(GameRowModel("a", "A", "USA"))), SystemGames("n64", "Nintendo 64", emptyList())),
            )
        assertEquals(listOf("recent", "favorites", "apps", "snes"), content.entries.map { it.id })
        assertEquals(1, content.systemCount)
        assertFalse(content.isLibraryEmpty)
    }

    @Test
    fun emptyLibraryHasNoSystemsButKeepsTheFixedEntries() {
        assertTrue(HomeContent.Empty.isLibraryEmpty)
        assertEquals(0, HomeContent.Empty.gameCount)
        assertEquals(3, HomeContent.Empty.strip.systems.size)
    }

    @Test
    fun largeLibraryIsDeterministicAndExact() {
        val large = FakeHomeContent.large()
        assertEquals(10_000, large.gameCount)
        assertEquals(18, large.systemCount)
        assertEquals(large.entries.map { it.rows }, FakeHomeContent.large().entries.map { it.rows })
        assertEquals("ids are unique", 10_000, large.entries.filter { it.kind == EntryKind.System }.flatMap { it.rows }.map { it.id }.toSet().size)
    }

    @Test
    fun stripContentFollowsTheEntries() {
        assertEquals(board.entries.map { it.id to it.count }, board.strip.systems.map { it.id to it.itemCount })
        assertFalse("the alpha has no box art", board.strip.hasBoxArt)
    }

    @Test
    fun letterJumpGoesToTheNextOrPreviousLetter() {
        val rows = listOf("Alpha", "Avenue", "Bravo", "Brick", "Charlie").mapIndexed { i, t -> GameRowModel("$i", t, "") }
        assertEquals(2, letterJumpTarget(rows, 0, forward = true))
        assertEquals(4, letterJumpTarget(rows, 3, forward = true))
        assertEquals("stays at the last letter", 4, letterJumpTarget(rows, 4, forward = true))
        assertEquals("to the start of its own letter first", 2, letterJumpTarget(rows, 3, forward = false))
        assertEquals("then to the previous letter", 0, letterJumpTarget(rows, 2, forward = false))
        assertEquals(0, letterJumpTarget(rows, 0, forward = false))
        assertEquals(0, letterJumpTarget(emptyList(), 5, forward = true))
    }

    @Test
    fun rowLooksFollowTheFocusLanguage() {
        val onSystems = StripFocus(Column.Systems, 3, "snes", mapOf("snes" to 6))
        val onGames = onSystems.copy(column = Column.Games)
        assertEquals(SystemRowLook.Focused, systemLook(onSystems, 3))
        assertEquals(SystemRowLook.Current, systemLook(onGames, 3))
        assertEquals(SystemRowLook.Normal, systemLook(onGames, 4))
        assertEquals(GameRowLook.PreviewRemembered, gameLook(onSystems, "snes", 6))
        assertEquals(GameRowLook.Preview, gameLook(onSystems, "snes", 5))
        assertEquals(GameRowLook.Focused, gameLook(onGames, "snes", 6))
        assertEquals(GameRowLook.Normal, gameLook(onGames, "snes", 5))
    }

    @Test
    fun aListOpensWithItsRememberedRowInView() {
        assertEquals(0, firstIndexShowing(row = 6, fit = 8))
        assertEquals(1, firstIndexShowing(row = 8, fit = 8))
        assertEquals(193, firstIndexShowing(row = 200, fit = 8))
    }

    @Test
    fun hintsFollowTheFocus() {
        val snes = board.entries[FakeHomeContent.BOARD_SNES_INDEX]
        val apps = board.entries[2]
        val systems = hintsFor(board, Column.Systems, snes).map { it.label }
        assertEquals("the board's RP5-1", listOf(R.string.home_hint_select, R.string.home_hint_search, R.string.home_hint_settings), systems)
        val games = hintsFor(board, Column.Games, snes).map { it.label }
        assertEquals(
            "no SELECT without box art; Y Favorite and X Search (D3); START opens the game menu",
            listOf(
                R.string.home_hint_play,
                R.string.home_hint_back,
                R.string.home_hint_favorite,
                R.string.home_hint_search,
                R.string.home_hint_menu,
                R.string.home_hint_switch_system,
            ),
            games,
        )
        assertEquals(
            "a favorite reads Y Unfavorite",
            R.string.home_hint_unfavorite,
            hintsFor(board, Column.Games, snes, focusedIsFavorite = true).single { it.action == Action.ToggleFavorite }.label,
        )
        val menu = hintsFor(board, Column.Games, snes).first { it.label == R.string.home_hint_menu }
        assertEquals(R.string.home_key_start, menu.key)
        assertEquals(Action.Menu, menu.action)
        assertEquals(R.string.home_hint_open, hintsFor(board, Column.Games, apps).first().label)
        assertEquals("START opens the app menu", true, hintsFor(board, Column.Games, apps).any { it.action == Action.Menu })
        assertEquals("apps cannot be favorites", false, hintsFor(board, Column.Games, apps).any { it.action == Action.ToggleFavorite })
        assertEquals("nothing to mark on the system column", false, hintsFor(board, Column.Systems, snes).any { it.action == Action.ToggleFavorite })
        val empty = HomeContent.of(emptyList(), emptyList(), apps.rows, emptyList())
        assertEquals("nothing to find in an empty library", false, hintsFor(empty, Column.Systems, empty.entries[2]).any { it.action == Action.Search })
    }

    @Test
    fun thePanelsHintLettersFollowTheConfirmSetting() {
        val menu = OpenPanel(GameRef("7", "Chrono Trigger"), PanelScreen.Menu(sampleFacts()))
        val swapped = ButtonLetters.of(ConfirmButtonSetting.Bottom)
        assertEquals(
            listOf(R.string.home_key_b to Action.Enter, R.string.home_key_a to Action.Back),
            panelHints(menu, swapped).map { it.key to it.action },
        )
    }

    @Test
    fun insideTheGamePanelTheHintsAreSelectAndBack() {
        val game = GameRef("7", "Chrono Trigger")
        val menu = OpenPanel(game, PanelScreen.Menu(sampleFacts()))
        assertEquals(
            listOf(R.string.home_key_a to Action.Enter, R.string.home_key_b to Action.Back),
            panelHints(menu).map { it.key to it.action },
        )
        assertEquals(listOf(R.string.home_hint_select, R.string.home_hint_back), panelHints(menu).map { it.label })
        val focus = StripFocus(column = Column.Games, systemIndex = FakeHomeContent.BOARD_SNES_INDEX, systemId = "snes", rows = mapOf("snes" to 3))
        assertEquals("the game's row keeps its mark without a fill", GameRowLook.MenuOpen, gameLook(focus, "snes", 3, isPanelOpen = true))
        assertEquals(GameRowLook.Normal, gameLook(focus, "snes", 4, isPanelOpen = true))
        assertEquals("Info has its Back row to choose", listOf(Action.Enter, Action.Back), panelHints(OpenPanel(game, PanelScreen.Info(sampleFacts()))).map { it.action })
    }

    @Test
    fun withNoGamesHintsOfferOnlyWhatWorks() {
        val noGames = FakeHomeContent.noGames()
        val recent = noGames.entries[0]
        val apps = noGames.entries[2]
        assertEquals("an empty library asks for a folder, with nothing to open", listOf(R.string.home_hint_add_folder, R.string.home_hint_settings), hintsFor(noGames, Column.Systems, recent).map { it.label })
        assertEquals(listOf(R.string.home_hint_select, R.string.home_hint_settings), hintsFor(noGames, Column.Systems, apps).map { it.label })
        // Apps' grid of icons needs no box art: SELECT is offered on the Apps list too.
        assertEquals(
            "no other list to switch to",
            listOf(R.string.home_hint_open, R.string.home_hint_back, R.string.home_hint_menu, R.string.home_hint_grid_view),
            hintsFor(noGames, Column.Games, apps).map { it.label },
        )
        assertTrue(noGames.isLibraryEmpty)
        assertEquals(listOf(0, 0, 41), noGames.entries.map { it.count })
    }
}
