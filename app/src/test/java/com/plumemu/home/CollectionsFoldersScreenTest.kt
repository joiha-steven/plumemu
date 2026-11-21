package com.plumemu.home

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.plumemu.collections.BuiltInFolder
import com.plumemu.collections.Folder
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
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
 * The Collections entry on the real main screen: the folders level (A or a tap opens a folder, Back returns with the focus kept),
 * the keys that do nothing on a folder row, grid or list per folder, L2/R2 from inside a folder, and the landing.
 * What a game's A plays says where the cursor is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class CollectionsFoldersScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private val folderViews = mutableListOf<Pair<String, Boolean>>()
    private var newCollections = 0
    private val landing = mutableIntStateOf(0)
    private lateinit var router: InputRouter
    private val down = Action.Move(Direction.Down)
    private val up = Action.Move(Direction.Up)
    private val left = Action.Move(Direction.Left)
    private val right = Action.Move(Direction.Right)

    private fun rows(prefix: String, count: Int) = List(count) { GameRowModel("$prefix$it", "$prefix title $it", "USA", systemAbbr = "SNES", systemId = "snes") }

    private fun content(favorites: Int = 2): HomeContent =
        HomeContent.of(
            recent = rows("r", 3),
            favorites = rows("f", favorites),
            apps = listOf(GameRowModel("app/Main", "An app", "")),
            systems = listOf(SystemGames("snes", "Super Nintendo", rows("s", 3))),
            otherFolders =
                listOf(
                    FolderRows(Folder.BuiltIn(BuiltInFolder.MostPlayed, 0), emptyList()),
                    FolderRows(Folder.BuiltIn(BuiltInFolder.RecentlyAdded, 2), rows("a", 2)),
                    FolderRows(Folder.Own(7, "RPG", 2), rows("c", 2)),
                ),
        )

    private fun callbacks() =
        HomeCallbacks(
            games = games,
            onFolderView = { id, isGrid, _ -> folderViews += id to isGrid },
            onNewCollection = { newCollections++ },
        )

    private fun show(content: HomeContent = content(), lists: Set<String> = emptySet()) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(content, router, callbacks(), landingRequest = landing.intValue, listCollections = lists) }
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun playedByA(): List<String> {
        games.launched.clear()
        send(Action.Enter)
        return games.launched.toList()
    }

    private fun hasText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun gridShown() = compose.onAllNodesWithTag(HomeTags.GRID_PAGE, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The folder list, cursor on its first row: R2 from the cold start's Favorites. */
    private fun intoFolders() = send(Action.JumpLetterNext)

    private fun intoFolder(row: Int) {
        intoFolders()
        repeat(row) { send(down) }
        send(Action.Enter)
    }

    @Test
    fun theColumnReadsFavoritesCollectionsAppsThenTheSystemsAndRecentIsNotAnEntry() {
        val kinds = content().entries.map { it.kind }
        assertEquals(listOf(EntryKind.Favorites, EntryKind.Collections, EntryKind.Apps, EntryKind.System), kinds)
        assertEquals(FIRST_SYSTEM_INDEX, content().firstSystemIndex)
        assertEquals("Recent is a folder", listOf("recent", "most_played", "recently_added", "collection:7"), content().folderEntries.map { it.id })
    }

    @Test
    fun theFoldersListShowsEveryFolderWithItsCountEmptyOnesToo() {
        show()
        intoFolders()
        listOf("Recent", "Most played", "Recently added", "RPG", "New collection…").forEach { assertTrue("$it is a row", hasText(it)) }
        listOf("3", "2").forEach { assertTrue("count $it", hasText(it)) }
        assertFalse("an empty folder (Most played) shows no bare 0", hasText("0"))
    }

    @Test
    fun aOpensAFolderAndItsGamesPlay() {
        show()
        intoFolder(0)
        assertEquals(listOf("r0"), playedByA())
        send(right)
        assertEquals("a grid: Right is the next cover", listOf("r1"), playedByA())
    }

    @Test
    fun aTapOpensAFolderAtOnce() {
        show()
        intoFolders()
        send(Action.TapItem(Column.Games, 3))
        assertEquals("RPG's first game", listOf("c0"), playedByA())
    }

    @Test
    fun backAndTheBackHintReturnToTheFoldersWithTheFocusOnThatFolder() {
        show()
        intoFolder(2)
        assertEquals(listOf("a0"), playedByA())
        send(Action.Back)
        // The cursor is on Recently added again: A opens it, it does not play a game.
        assertTrue(playedByA().isEmpty())
        assertEquals(listOf("a0"), playedByA())
        // The same through a tap on the "Back" hint (it sends Back).
        compose.onAllNodesWithText("Back", useUnmergedTree = true).fetchSemanticsNodes().let { assertTrue("the hint bar has Back", it.isNotEmpty()) }
        compose.onAllNodesWithText("Back", useUnmergedTree = true)[0].performClick()
        assertTrue("back on the folders", playedByA().isEmpty())
        // Back from the folders goes to the system column, as always: A then enters the folders' list, it plays nothing.
        send(Action.Back)
        assertTrue(playedByA().isEmpty())
    }

    @Test
    fun yR3SelectAndStartDoNothingOnAFolderRow() {
        show()
        intoFolders()
        send(Action.ToggleFavorite, Action.Sort, Action.ToggleView, Action.Menu)
        assertTrue(games.favorites.isEmpty())
        assertTrue(folderViews.isEmpty())
        assertFalse("no game menu opened", hasText("Hide"))
        // The list still answers A: no panel took it.
        send(Action.Enter)
        assertEquals(listOf("r0"), playedByA())
    }

    @Test
    fun aOnNewCollectionCallsTheHookAndOpensNothing() {
        show()
        intoFolders()
        send(up) // wraps to the last row, New collection…
        send(Action.Enter)
        assertEquals(1, newCollections)
        assertTrue("A opened no folder: no game was played", games.launched.isEmpty())
    }

    @Test
    fun aFolderOpensAsAGridAndSelectSwitchesItToAListAndBack() {
        show()
        intoFolder(0)
        assertTrue("a folder opens as a grid", gridShown())
        send(Action.ToggleView)
        assertEquals(listOf("recent" to false), folderViews)
        assertFalse(gridShown())
        send(Action.ToggleView)
        assertEquals(listOf("recent" to false, "recent" to true), folderViews)
        assertTrue(gridShown())
    }

    @Test
    fun theChoiceIsPerFolderAListedFolderOpensAsAListTheOthersAsGrids() {
        show(lists = setOf("collection:7"))
        intoFolder(0)
        assertTrue("Recent is a grid", gridShown())
        send(Action.Back, down, down, down, Action.Enter)
        assertFalse("RPG was chosen as a list", gridShown())
        assertEquals(listOf("c0"), playedByA())
    }

    @Test
    fun anOldGridChoiceStoredUnderRecentDoesNotMakeAListedFolderAGrid() {
        val ids = gridIdsWithFolders(setOf("favorites", "recent"), setOf("recent"), content().folderEntries)
        assertFalse("listed", "recent" in ids)
        assertTrue(listOf("most_played", "recently_added", "collection:7", "favorites").all { it in ids })
    }

    @Test
    fun l2R2LeaveAFolderAndCollectionsReopensTheLastFolderWithItsFocus() {
        show()
        intoFolder(0)
        send(right)
        send(Action.JumpLetterNext)
        send(Action.JumpLetterPrevious)
        assertEquals("Recent reopened on its second game", listOf("r1"), playedByA())
    }

    @Test
    fun aFolderOpenedWithAStandsOnItsFirstGameEvenAfterAnEarlierVisit() {
        show()
        intoFolder(3)
        assertEquals("first visit: the first game", listOf("c0"), playedByA())
        send(right, Action.Back, Action.Enter)
        assertEquals("opened again from its row: the first game, not the one left", listOf("c0"), playedByA())
    }

    @Test
    fun anEmptyFolderOpensAndBackLeavesIt() {
        show()
        intoFolder(1)
        assertTrue("nothing to play", playedByA().isEmpty())
        send(Action.Back)
        send(down)
        send(Action.Enter)
        assertEquals(listOf("a0"), playedByA())
    }

    @Test
    fun theHomeButtonLandsOnFavoritesFirstGameFromAnywhere() {
        show()
        intoFolder(0)
        send(left, Action.Back)
        compose.runOnIdle { landing.intValue = 1 }
        compose.waitForIdle()
        assertEquals(listOf("f0"), playedByA())
    }

    @Test
    fun aColdStartAndHomeWithoutFavoritesStandOnTheFavoritesEntryWithItsEmptyLine() {
        show(content(favorites = 0))
        compose.onNodeWithTag(HomeTags.EMPTY_FAVORITES).assertExists()
        assertTrue(playedByA().isEmpty())
        intoFolders()
        compose.runOnIdle { landing.intValue = 1 }
        compose.waitForIdle()
        compose.onNodeWithTag(HomeTags.EMPTY_FAVORITES).assertExists()
    }

    @Test
    fun aProcessDeathRestoresTheOpenFolderAndItsRow() {
        val tester = StateRestorationTester(compose)
        tester.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(content(), router, callbacks()) }
        }
        compose.waitForIdle()
        intoFolder(0)
        send(right)
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("the same folder, the same row", listOf("r1"), playedByA())
    }
}
