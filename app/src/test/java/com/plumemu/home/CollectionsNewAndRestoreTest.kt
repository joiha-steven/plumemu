package com.plumemu.home

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.collections.BuiltInFolder
import com.plumemu.collections.Folder
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.TypedKey
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FakeGameActions
import com.plumemu.graph.GraphCollections
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * New collection… on the folders page (gamepad and touch, with the own keyboard), and where the screen stands after a
 * start: Favorites' first game with no saved state, the open folder and row when the Activity is recreated from saved state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class CollectionsNewAndRestoreTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    private val games = FakeGameActions()
    private lateinit var router: InputRouter
    private val down = Action.Move(Direction.Down)
    private val right = Action.Move(Direction.Right)

    @After
    fun tearDown() = db.close()

    private fun rows(prefix: String, count: Int) = List(count) { GameRowModel("$prefix$it", "$prefix title $it", "USA", systemAbbr = "SNES", systemId = "snes") }

    private fun content(): HomeContent =
        HomeContent.of(
            recent = rows("r", 3),
            favorites = rows("f", 2),
            apps = emptyList(),
            systems = listOf(SystemGames("snes", "Super Nintendo", rows("s", 3))),
            otherFolders = listOf(FolderRows(Folder.BuiltIn(BuiltInFolder.MostPlayed, 3), rows("m", 3)), FolderRows(Folder.BuiltIn(BuiltInFolder.RecentlyAdded, 0), emptyList())),
        )

    private fun show(collections: GraphCollections? = GraphCollections(db)) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(content(), router, HomeCallbacks(games = games, collections = collections)) }
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

    private fun names() = runBlocking { db.collectionDao().observeCollections().first().map { it.name } }

    private fun waitForNamePanelToClose() = compose.waitUntil(10_000) { !hasText("Collection name") }

    @Test
    fun aColdStartWithNoSavedStateLandsOnFavoritesFirstGame() {
        show()
        assertEquals(listOf("f0"), playedByA())
    }

    @Test
    fun aRecreatedActivityComesBackInsideTheFolderOnItsThirdGameNotOnFavorites() {
        val tester = StateRestorationTester(compose)
        tester.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(content(), router, HomeCallbacks(games = games)) }
        }
        compose.waitForIdle()
        send(Action.JumpLetterNext) // Collections' folders
        send(down, Action.Enter) // Most played
        send(right, right)
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("Most played, third game", listOf("m2"), playedByA())
    }

    @Test
    fun newCollectionOnTheFoldersPageWithTheGamepadMakesAnEmptyCollection() {
        show()
        send(Action.JumpLetterNext)
        send(Action.Move(Direction.Up), Action.Enter) // wraps to New collection…
        assertTrue("the own keyboard's panel", hasText("Collection name"))
        assertTrue(games.launched.isEmpty())
        send(Action.Enter) // the field: the keyboard comes up
        send(Action.Typed(TypedKey.Text("Kids")))
        send(Action.TapItem(Column.Details, 0)) // Save
        waitForNamePanelToClose()
        assertEquals(listOf("Kids"), names())
    }

    @Test
    fun newCollectionByTouchRefusesAnEmptyNameThenSavesOne() {
        show()
        send(Action.JumpLetterNext)
        send(Action.TapItem(Column.Games, 3)) // New collection… (Recent, Most played, Recently added, New)
        assertTrue(hasText("Collection name"))
        send(Action.TapItem(Column.Details, 0))
        assertTrue("an empty name is refused, the panel stays", hasText("Collection name"))
        assertTrue(names().isEmpty())
        send(Action.Enter, Action.Typed(TypedKey.Text("RPG")), Action.TapItem(Column.Details, 0))
        waitForNamePanelToClose()
        assertEquals(listOf("RPG"), names())
    }

    @Test
    fun withoutCollectionsWiredNewCollectionDoesNothing() {
        show(collections = null)
        send(Action.JumpLetterNext)
        send(Action.Move(Direction.Up), Action.Enter)
        assertTrue(!hasText("Collection name"))
    }
}
