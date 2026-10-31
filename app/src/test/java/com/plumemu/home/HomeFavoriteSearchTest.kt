package com.plumemu.home

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.TypedKey
import com.plumemu.coreui.components.FAVORITE_STAR
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GamePanelTags
import com.plumemu.home.search.SearchTags
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Y = Favorite and X = Search on the whole screen with a real router, by gamepad and by touch: the
 * star, the hint, the Favorites count and list, search's results, focus return and failures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeFavoriteSearchTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private lateinit var router: InputRouter
    private var content by mutableStateOf(FakeHomeContent.withFavorites())

    private fun show(followWrites: Boolean = true) {
        // The library answers a write as Room would: the mark changes and Favorites follows.
        if (followWrites) games.onFavorite = { key, isFavorite -> content = content.marked(key, isFavorite) }
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(content, router, HomeCallbacks(games = games)) }
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) =
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }

    /** Typed as a hardware keyboard types (Plumemu's keyboard has no text field to type into). */
    private fun typeQuery(text: String) = send(Action.Typed(TypedKey.Text(text)))

    /** The B button through the router's key door: it erases while Search's keyboard is up. */
    private fun pressB() {
        compose.runOnIdle { router.pressButton(KeyEvent.KEYCODE_BUTTON_B) }
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun count(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun down(times: Int) = send(*List(times) { Action.Move(Direction.Down) }.toTypedArray())

    private fun openSnes() = send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)

    private fun openFavorites() = send(Action.Move(Direction.Down), Action.Enter)

    /** The count beside Favorites in the system column (the row's last text). */
    private fun favoritesCountShown(): String =
        compose.onNodeWithTag(HomeTags.system(1), useUnmergedTree = true).fetchSemanticsNode().children
            .mapNotNull { child -> child.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { it.text } }
            .last()

    @Test
    fun yAddsTheFocusedGameAndTheStarHintAndCountFollow() {
        show()
        openSnes()
        down(1) // Donkey Kong Country 2, not a favorite
        assertEquals("Y Favorite on a game that is not one", 1, count("Favorite"))
        val starsBefore = count(FAVORITE_STAR)
        send(Action.ToggleFavorite)
        assertEquals(listOf("snes-1" to true), games.favorites)
        assertEquals("the star appears on the row", starsBefore + 1, count(FAVORITE_STAR))
        assertEquals("the hint turns round", 1, count("Unfavorite"))
        assertEquals("Favorites counts it at once", "5", favoritesCountShown())
        send(Action.ToggleFavorite)
        assertEquals(listOf("snes-1" to true, "snes-1" to false), games.favorites)
        assertEquals(starsBefore, count(FAVORITE_STAR))
        assertEquals("4", favoritesCountShown())
    }

    @Test
    fun twoQuickYPressesBeforeTheScreenRedrawsEndWhereTheyStarted() {
        show()
        openSnes()
        down(1) // Donkey Kong Country 2, not a favorite
        val starsBefore = count(FAVORITE_STAR)
        games.writeGate = kotlinx.coroutines.CompletableDeferred()
        // Both presses are handled before any recomposition and before the library answers.
        compose.runOnIdle {
            router.send(Action.ToggleFavorite)
            router.send(Action.ToggleFavorite)
        }
        compose.runOnIdle { games.writeGate!!.complete(Unit) }
        compose.waitForIdle()
        assertEquals(listOf("snes-1" to true, "snes-1" to false), games.favorites)
        assertEquals("no star left behind", starsBefore, count(FAVORITE_STAR))
        assertEquals("4", favoritesCountShown())
    }

    @Test
    fun yOnTheSystemColumnOrOnAnAppDoesNothingAndHasNoHint() {
        show()
        send(Action.ToggleFavorite)
        assertFalse(exists(HomeTags.hint(Action.ToggleFavorite)))
        send(Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter) // Apps
        send(Action.ToggleFavorite)
        assertFalse(exists(HomeTags.hint(Action.ToggleFavorite)))
        assertTrue(games.favorites.isEmpty())
    }

    @Test
    fun touchTheYHintToFavorite() {
        show()
        openSnes()
        tap(HomeTags.hint(Action.ToggleFavorite))
        assertEquals("Chrono Trigger is a favorite: the tap takes it out", listOf("snes-0" to false), games.favorites)
    }

    @Test
    fun theMenusFavoriteSharesYsPath() {
        show()
        openSnes()
        down(1)
        val starsBefore = count(FAVORITE_STAR)
        send(Action.Menu)
        compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.PANEL) }
        down(2) // Play, Play with, Favorite
        send(Action.Enter)
        assertEquals(listOf("snes-1" to true), games.favorites)
        assertFalse("the menu closes", exists(GamePanelTags.PANEL))
        assertEquals(starsBefore + 1, count(FAVORITE_STAR))
        assertEquals("5", favoritesCountShown())
    }

    @Test
    fun inTheFavoritesListATakenOutGameHoldsItsPlaceUntilTheListIsLeft() {
        show()
        openFavorites()
        val favorites = content.entries[1].rows.map { it.id }
        assertEquals(listOf("snes-0", "snes-2", "snes-3", "snes-6"), favorites)
        down(1)
        send(Action.ToggleFavorite) // EarthBound out
        assertEquals(listOf("snes-2" to false), games.favorites)
        assertEquals("the library dropped it", 3, content.entries[1].rows.size)
        assertTrue("its row stays where it was", exists(HomeTags.game(3)))
        assertEquals("the count says three", "3", favoritesCountShown())
        send(Action.Play)
        assertEquals("the cursor did not jump", listOf("snes-2"), games.launched)
        send(Action.ToggleFavorite) // and back
        assertEquals("snes-2" to true, games.favorites.last())
        send(Action.ToggleFavorite)
        send(Action.Move(Direction.Left))
        assertFalse("left the list: it follows the library", exists(HomeTags.game(3)))
    }

    @Test
    fun aFailedFavoriteSaysSoAndThePreviousMarkComesBack() {
        games.writeError = IOException("disk I/O error")
        show()
        openSnes()
        val starsBefore = count(FAVORITE_STAR)
        send(Action.ToggleFavorite)
        assertTrue("a plain message", exists(GamePanelTags.PANEL))
        assertEquals("the star is back as the library holds it", starsBefore, count(FAVORITE_STAR))
    }

    /**
     * A game of a system hidden in Settings › Library › Systems, found by Search: Y flips its star
     * before the library answers, and the pending mark is kept until the library agrees (the
     * hidden system's rows count as listed). Two presses end where they started.
     */
    @Test
    fun yOnAHiddenSystemsGameInSearchFlipsTheStarAtOnce() {
        content = FakeHomeContent.withFavorites().withSystemHidden("snes")
        show()
        send(Action.Search)
        typeQuery("donkey kong country 2")
        compose.waitUntil(TIMEOUT_MS) { exists(SearchTags.result(0)) }
        send(Action.Menu) // START: to the results
        val starsBefore = count(FAVORITE_STAR)
        games.writeGate = kotlinx.coroutines.CompletableDeferred()
        send(Action.ToggleFavorite)
        // Search re-reads the marked content off the main thread; the library is held by the gate.
        compose.waitUntil(TIMEOUT_MS) { count(FAVORITE_STAR) == starsBefore + 1 }
        compose.runOnIdle { games.writeGate!!.complete(Unit) }
        compose.waitForIdle()
        assertEquals("the library agreed: the star stays", starsBefore + 1, count(FAVORITE_STAR))
        send(Action.ToggleFavorite)
        compose.waitUntil(TIMEOUT_MS) { count(FAVORITE_STAR) == starsBefore }
        assertEquals(listOf("snes-1" to true, "snes-1" to false), games.favorites)
    }

    /** The same game: Y, then two quick presses before the screen redraws or the library answers, end on a favorite. */
    @Test
    fun quickYPressesOnAHiddenSystemsGameInSearchEachReadTheShownStar() {
        content = FakeHomeContent.withFavorites().withSystemHidden("snes")
        show()
        send(Action.Search)
        typeQuery("donkey kong country 2")
        compose.waitUntil(TIMEOUT_MS) { exists(SearchTags.result(0)) }
        send(Action.Menu) // START: to the results
        val starsBefore = count(FAVORITE_STAR)
        games.writeGate = kotlinx.coroutines.CompletableDeferred()
        send(Action.ToggleFavorite)
        compose.waitUntil(TIMEOUT_MS) { count(FAVORITE_STAR) == starsBefore + 1 }
        compose.runOnIdle {
            router.send(Action.ToggleFavorite)
            router.send(Action.ToggleFavorite)
        }
        compose.runOnIdle { games.writeGate!!.complete(Unit) }
        compose.waitForIdle()
        assertEquals(listOf("snes-1" to true, "snes-1" to false, "snes-1" to true), games.favorites)
        compose.waitUntil(TIMEOUT_MS) { count(FAVORITE_STAR) == starsBefore + 1 }
    }

    @Test
    fun xSearchesAPlaysAndBReturnsWhereThePersonWas() {
        show()
        openSnes()
        down(4) // Mega Man X
        send(Action.Search)
        assertTrue(exists(SearchTags.FIELD))
        assertFalse("an empty query lists nothing", exists(SearchTags.result(0)))
        typeQuery("TRIGGER chrono")
        compose.waitUntil(TIMEOUT_MS) { exists(SearchTags.result(0)) }
        assertEquals("one match, counted in the top bar", 1, count("1 game"))
        send(Action.Menu, Action.Enter) // START to the results, A plays
        assertEquals(listOf("snes-0"), games.launched)
        send(Action.Back)
        assertFalse(exists(SearchTags.FIELD))
        send(Action.Play)
        assertEquals("the strip's focus waited on Mega Man X", listOf("snes-0", "snes-4"), games.launched)
    }

    /**
     * Search replaces the strip while it is open, so the games pages leave the composition; each
     * system's list position is held beside the strip's focus (HomeScreen), so Back shows SNES's
     * list where it was left, not opened afresh around the focused row.
     */
    @Test
    fun xThenBackKeepsTheListWhereItWas() {
        content = FakeHomeContent.large()
        show(followWrites = false)
        openSnes()
        down(30)
        send(*List(9) { Action.Move(Direction.Up) }.toTypedArray())
        val top = topRow()
        assertTrue("the list is scrolled: top row $top", top > 0)
        send(Action.Search)
        assertTrue(exists(SearchTags.FIELD))
        assertFalse("the strip is gone while searching", exists(HomeTags.GAMES))
        pressB()
        assertFalse(exists(SearchTags.FIELD))
        assertEquals("same first row after search", top, topRow())
        send(Action.Play)
        assertEquals("the focus waited on row 21", listOf("snes-21"), games.launched)
    }

    /** The first row whose top is inside the games list's window (only the shown page's rows are in the tree). */
    private fun topRow(): Int {
        val list = compose.onNodeWithTag(HomeTags.GAMES, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return (0 until TOP_ROW_SEARCH).first { row ->
            compose.onAllNodesWithTag(HomeTags.game(row)).fetchSemanticsNodes().any { it.boundsInRoot.top >= list.top - 0.5f }
        }
    }

    @Test
    fun noMatchShowsAPlainLineAndStartOpensTheMenuOfAResult() {
        show()
        send(Action.Search)
        typeQuery("zzzz")
        compose.waitUntil(TIMEOUT_MS) { exists(SearchTags.NO_MATCH) }
        repeat("zzzz".length + 1) { pressB() } // four erased, then B on the empty query closes
        assertFalse(exists(SearchTags.FIELD))
        send(Action.Search)
        typeQuery("earthbound")
        compose.waitUntil(TIMEOUT_MS) { exists(SearchTags.result(0)) }
        send(Action.Menu, Action.Menu) // START to the results, START again: its menu
        compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.PANEL) }
        send(Action.Back)
        assertFalse(exists(GamePanelTags.PANEL))
        assertTrue("B from the menu returns to the results", exists(SearchTags.result(0)))
    }

    @Test
    fun touchTheXHintThenTapAResultTwiceToPlayIt() {
        show()
        tap(HomeTags.hint(Action.Search))
        typeQuery("mega")
        compose.waitUntil(TIMEOUT_MS) { exists(SearchTags.result(0)) }
        tap(SearchTags.result(0))
        assertTrue("one tap focuses", games.launched.isEmpty())
        tap(SearchTags.result(0))
        assertEquals(listOf("snes-4"), games.launched)
        compose.onNodeWithTag(SearchTags.result(0), useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.PANEL) }
    }

    @Test
    fun anEmptyLibraryHasNoSearch() {
        content = FakeHomeContent.noGames()
        show(followWrites = false)
        send(Action.Search)
        assertFalse(exists(SearchTags.FIELD))
        assertFalse(exists(HomeTags.hint(Action.Search)))
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        /** Rows looked at for the list's top row: more than the 30 the position test goes down. */
        const val TOP_ROW_SEARCH = 40
    }
}

/** [this] after the library stored [isFavorite] on [key]: the row's mark, and Favorites by sort name as Room lists it. */
internal fun HomeContent.marked(
    key: String,
    isFavorite: Boolean,
): HomeContent {
    fun marked(list: List<HomeEntry>) =
        list.map { entry -> SystemGames(entry.id, entry.name.orEmpty(), entry.rows.map { if (it.id == key) it.copy(isFavorite = isFavorite) else it }) }
    val systems = marked(entries.filter { it.kind == EntryKind.System })
    val hidden = marked(hiddenEntries)
    val favorites = (systems + hidden).flatMap { it.games }.filter { it.isFavorite }.sortedBy { it.title }
    return HomeContent.of(entries[0].rows, favorites, entries.first { it.kind == EntryKind.Apps }.rows, systems, hasBoxArt = hasBoxArt, hiddenSystems = hidden)
}

/** [this] with system [id] hidden from the column (Settings › Library › Systems): still searched, still in Recent and Favorites. */
internal fun HomeContent.withSystemHidden(id: String): HomeContent {
    val systems = entries.filter { it.kind == EntryKind.System }.map { SystemGames(it.id, it.name.orEmpty(), it.rows, it.coverShape) }
    return HomeContent.of(
        entries[0].rows,
        entries[1].rows,
        entries.first { it.kind == EntryKind.Apps }.rows,
        systems.filter { it.id != id },
        hasBoxArt = hasBoxArt,
        hiddenSystems = systems.filter { it.id == id },
    )
}
