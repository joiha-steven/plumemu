package com.plumemu.home

import androidx.compose.runtime.getValue
import com.plumemu.coreinput.strip.StripFocus
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FakeGameActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Recent and Favorites as grids on the whole screen: the empty library keeps its
 * message, the bottom bar names the system only in these two grids, and Y in the Favorites grid dims the
 * cover of the game taken out until the list is left. Recent's grid is a folder inside Collections and Favorites is the column's
 * first entry since the Collections change, so the tests reach and read them that way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeMixedGridScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private lateinit var router: InputRouter

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun dimmedCovers() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(CoverDimmed), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hasText(text: String) = compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    // The empty library: no grid, the message, and no grid hint that would lead into nothing.

    @Test
    fun anEmptyLibraryWithTheGridOnStillShowsTheMessageAndNoGrid() {
        val empty = withBoxArt(FakeHomeContent.noGames())
        assertTrue(empty.isLibraryEmpty)
        assertTrue(empty.hasBoxArt)
        val gridIds = setOf(HomeContent.RECENT_ID, HomeContent.FAVORITES_ID)
        compose.setContent { PlumTheme { HomeAt(empty, gridIds = gridIds) } }
        compose.waitForIdle()
        assertTrue("the message", exists(HomeTags.EMPTY_MESSAGE))
        assertFalse("no grid", exists(HomeTags.GRID))
    }

    @Test
    fun anEntryWithoutGamesIsNeverAGridSoTheStripAndTheHintsAgree() {
        val empty = withBoxArt(FakeHomeContent.noGames())
        val gridIds = setOf(HomeContent.RECENT_ID, HomeContent.FAVORITES_ID)
        val recent = empty.entries[0] // Favorites since the Collections change: the first entry
        assertFalse(empty.showsGrid(recent, gridIds))
        val strip = empty.stripWith(gridIds, PlumLayout(width = 768.dp, height = 432.dp))
        assertEquals("one column: the focus rules see a list", 1, strip.systems[0].gridColumns)
        val hints = hintsFor(empty, Column.Games, recent, showsGrid = empty.showsGrid(recent, gridIds))
        assertTrue("no grid-size hint", hints.none { it.cyclesGridSize })
        assertTrue("no \"List view\" hint, there is no grid to leave", hints.none { it.label == R.string.home_hint_list_view })
    }

    // The bar's system.

    private val snesGame = GameRowModel("snes-0", "Chrono Trigger", "USA", details = details(1995, "Square"), systemAbbr = "SNES", systemId = "snes")
    private val pspGame = GameRowModel("psp-0", "Daxter", "USA", details = details(null, "Ready at Dawn"), systemAbbr = "PSP", systemId = "psp")
    private val unknownGame = GameRowModel("x-0", "Link", "", details = details(2001, "Fan"), systemId = "x")

    private val mixed =
        HomeContent.of(
            recent = listOf(snesGame, pspGame, unknownGame),
            favorites = listOf(snesGame),
            apps = emptyList(),
            systems = listOf(SystemGames("snes", "Super Nintendo", listOf(snesGame), CoverShape(1.4f)), SystemGames("psp", "PSP", listOf(pspGame), CoverShape(0.58f))),
            hasBoxArt = true,
        )

    private fun showGrid(
        actions: List<Action>,
        id: String,
    ) {
        compose.setContent { PlumTheme { HomeAt(mixed, actions, gridIds = setOf(id)) } }
        compose.waitForIdle()
    }

    @Test
    fun theRecentGridsBarStartsWithTheSystemThenYearAndPublisher() {
        showGrid(recentGridActions(0), HomeContent.RECENT_ID)
        assertTrue(hasText("Chrono Trigger SNES · 1995 · Square"))
    }

    @Test
    fun theFavoritesGridsBarNamesTheSystemToo() {
        showGrid(gridActions(0, 0), HomeContent.FAVORITES_ID)
        assertTrue(hasText("Chrono Trigger SNES · 1995 · Square"))
    }

    @Test
    fun aMissingYearLeavesNoStrayDot() {
        showGrid(recentGridActions(1), HomeContent.RECENT_ID)
        assertTrue("system and publisher", hasText("Daxter PSP · Ready at Dawn"))
        assertFalse(hasText("PSP ·  ·"))
        assertFalse(hasText("· · "))
        assertFalse(hasText("Daxter · "))
    }

    @Test
    fun aGameWhoseSystemIsUnknownShowsYearAndPublisherOnly() {
        showGrid(recentGridActions(2), HomeContent.RECENT_ID)
        assertTrue(hasText("Link 2001 · Fan"))
    }

    @Test
    fun aSystemsOwnGridKeepsYearAndPublisherWithoutTheSystem() {
        showGrid(gridActions(FIRST_SYSTEM_INDEX, 0), "snes")
        assertTrue(hasText("Chrono Trigger 1995 · Square"))
        assertFalse(hasText("SNES"))
    }

    // Y in the Favorites grid.

    private var library by mutableStateOf(boxed())

    /** The board with Super Nintendo games 0, 2, 3 and 6 favorite and box art on, so Favorites can be a grid. */
    private fun boxed(): HomeContent {
        val plain = FakeHomeContent.withFavorites()
        return plain.rebuilt(hasBoxArt = true)
    }

    private fun showFavoritesGrid() {
        games.onFavorite = { key, isFavorite -> library = library.marked(key, isFavorite) }
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(library, router, HomeCallbacks(games = games), gridSystems = setOf(HomeContent.FAVORITES_ID), initialFocus = StripFocus()) }
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) =
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }

    /** The game count drawn in the top bar while the Favorites list is focused ("Favorites · 3 games"). */
    private fun favoritesCountInTopBar(): String {
        val texts =
            compose.onNodeWithTag(HomeTags.TOP_BAR, useUnmergedTree = true).fetchSemanticsNode().let { bar ->
                generateSequence(listOf(bar)) { level -> level.flatMap { it.children }.takeIf { it.isNotEmpty() } }.flatten()
                    .mapNotNull { it.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { t -> t.text } }.toList()
            }
        return texts.firstNotNullOf { Regex("(\\d+) games?").find(it)?.groupValues?.get(1) }
    }

    @Test
    fun yInTheFavoritesGridDimsTheCoverAndDropsTheCountThenLeavingRemovesIt() {
        showFavoritesGrid()
        send(Action.Enter) // the cold start of this harness is the Favorites entry
        assertTrue("Favorites is a grid", exists(HomeTags.GRID))
        assertEquals(listOf("snes-0", "snes-2", "snes-3", "snes-6"), library.favoriteRows.map { it.id })
        assertEquals("no cover is dimmed yet", 0, dimmedCovers())
        send(Action.Move(Direction.Right)) // snes-2
        send(Action.ToggleFavorite)
        assertEquals(listOf("snes-2" to false), games.favorites)
        assertEquals("the count says three", "3", favoritesCountInTopBar())
        assertTrue("its cover stays", exists(HomeTags.cover(3)))
        assertEquals("and is dimmed, the one cue the grid has", 1, dimmedCovers())
        send(Action.ToggleFavorite) // and back
        assertEquals("a favorite again: not dimmed", 0, dimmedCovers())
        send(Action.ToggleFavorite)
        assertEquals(1, dimmedCovers())
        send(Action.Back) // to the system column: the list is left
        send(Action.Enter) // and opened again
        assertFalse("the cover is gone", exists(HomeTags.cover(3)))
        assertTrue("three covers", exists(HomeTags.cover(2)) && !exists(HomeTags.cover(3)))
        assertEquals(0, dimmedCovers())
        send(Action.Play)
        val played = games.launched.single()
        assertTrue("the cursor stands on a game still in Favorites, not the one taken out: $played", played in setOf("snes-0", "snes-3", "snes-6"))
    }

    @Test
    fun yInTheRecentGridDimsNothing() {
        showGridWithY(open = OpenRecentFolder, id = HomeContent.RECENT_ID)
        assertEquals("Recent's covers are never dimmed", 0, dimmedCovers())
    }

    @Test
    fun yInASystemsOwnGridDimsNothing() {
        showGridWithY(open = List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) } + Action.Enter, id = "snes")
        assertEquals("a system's covers are never dimmed", 0, dimmedCovers())
    }

    /** Opens [id]'s grid, takes the first game's mark out with Y (a favorite in a system's list: SNES row 0 is one) and leaves the cover. */
    private fun showGridWithY(
        open: List<Action>,
        id: String,
    ) {
        games.onFavorite = { key, isFavorite -> library = library.marked(key, isFavorite) }
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(library, router, HomeCallbacks(games = games), gridSystems = setOf(id), initialFocus = StripFocus()) }
        }
        compose.waitForIdle()
        send(*open.toTypedArray())
        assertTrue("$id is a grid", exists(HomeTags.GRID))
        send(Action.ToggleFavorite)
        assertEquals("Y reached the library", 1, games.favorites.size)
    }

    private fun withBoxArt(content: HomeContent): HomeContent =
        content.rebuilt(hasBoxArt = true)

    private fun details(
        year: Int?,
        publisher: String,
    ) = GameDetails(year = year, publisher = publisher, genre = "", players = "", description = "")
}
