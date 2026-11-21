package com.plumemu.home

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.collections.BuiltInFolder
import com.plumemu.collections.Folder
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FakeGameActions
import com.plumemu.textsize.findTagged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The folders page's hint bar, its header, the empty folders' lines and the peek code, on the real main screen:
 * gamepad keys and taps on the hints.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class CollectionsFoldersFixesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private var newCollections = 0
    private lateinit var router: InputRouter
    private val down = Action.Move(Direction.Down)
    private val up = Action.Move(Direction.Up)

    private fun rows(prefix: String, count: Int) = List(count) { GameRowModel("$prefix$it", "$prefix title $it", "USA", systemAbbr = "SNES", systemId = "snes") }

    private fun content(mostPlayed: Int = 0, recentlyAdded: Int = 2, own: Int = 2, recent: Int = 3): HomeContent =
        HomeContent.of(
            recent = rows("r", recent),
            favorites = rows("f", 2),
            apps = listOf(GameRowModel("app/Main", "An app", "")),
            systems = listOf(SystemGames("snes", "Super Nintendo", rows("s", 3))),
            otherFolders =
                listOf(
                    FolderRows(Folder.BuiltIn(BuiltInFolder.MostPlayed, mostPlayed), rows("m", mostPlayed)),
                    FolderRows(Folder.BuiltIn(BuiltInFolder.RecentlyAdded, recentlyAdded), rows("a", recentlyAdded)),
                    FolderRows(Folder.Own(7, "RPG", own), rows("c", own)),
                ),
        )

    private fun show(content: HomeContent, lists: Set<String> = emptySet()) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(content, router, HomeCallbacks(games = games, onNewCollection = { newCollections++ }), landingRequest = 0, listCollections = lists) }
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun hasText(text: String, substring: Boolean = false) = compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hasTag(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tagText(tag: String) = findTagged(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), tag)?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    /** The folder list with the cursor on row [row] (R2 from the cold start's Favorites, then Down). */
    private fun intoFolders(row: Int = 0) {
        send(Action.JumpLetterNext)
        repeat(row) { send(down) }
    }

    private fun intoFolder(row: Int) {
        intoFolders(row)
        send(Action.Enter)
    }

    @Test
    fun aFolderRowOffersOpenSearchSwitchAndBackAndNothingElse() {
        show(content())
        intoFolders(0)
        listOf("Open", "Search", "Switch list", "Back").forEach { assertTrue("hint $it", hasText(it)) }
        listOf("Y", "R3", "SELECT", "START", "Favorite", "Sort", "Menu", "Play", "New collection").forEach { assertFalse("no hint $it", hasText(it)) }
    }

    @Test
    fun newCollectionRowOffersNewCollectionInPlaceOfOpen() {
        show(content())
        intoFolders(0)
        send(up) // wraps to New collection…
        assertTrue(hasText("New collection"))
        assertFalse("A reads New collection, not Open", hasText("Open"))
        listOf("Search", "Switch list", "Back").forEach { assertTrue("hint $it", hasText(it)) }
        listOf("Y", "R3", "SELECT", "START").forEach { assertFalse("no hint $it", hasText(it)) }
    }

    @Test
    fun aTapOnTheOpenHintOpensTheFolder() {
        show(content())
        intoFolders(0)
        compose.onAllNodesWithText("Open", useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        games.launched.clear()
        send(Action.Enter)
        assertEquals("the folder is open: A plays its first game", listOf("r0"), games.launched.toList())
    }

    @Test
    fun aTapOnTheNewCollectionHintAsksForTheName() {
        show(content())
        intoFolders(0)
        send(up)
        compose.onAllNodesWithText("New collection", useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        assertEquals(1, newCollections)
    }

    @Test
    fun aTapOnTheSearchHintSendsSearch() {
        show(content())
        intoFolders(0)
        compose.onAllNodesWithText("Search", useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        assertTrue("Search opened: only it shows its empty line", hasText("Type a game name to search"))
    }

    @Test
    fun theFoldersHeaderCountsCollectionsNotRowsAndAFolderKeepsItsNameAndGames() {
        show(content())
        intoFolders(0)
        assertTrue("1 collection: only the own one counts", hasText("1 collection", substring = true))
        send(Action.Enter)
        assertTrue("the folder's name and its games", hasText("3 games", substring = true))
        assertFalse(hasText("collections", substring = true))
    }

    @Test
    fun withNoCollectionMadeTheHeaderSaysSoNotZeroOrTheBuiltIns() {
        show(HomeContent.of(rows("r", 3), rows("f", 1), emptyList(), listOf(SystemGames("snes", "Super Nintendo", rows("s", 1)))))
        intoFolders(0)
        assertTrue(hasText("No collections yet"))
        assertFalse("not the built-ins' 3", hasText("3 collections", substring = true))
    }

    @Test
    fun anEmptyFolderRowShowsNoCountBuiltInOrOwn() {
        val rows = content(mostPlayed = 0, own = 0).entries.first { it.kind == EntryKind.Collections }.rows
        assertEquals("Recent 3, Most played and the empty RPG blank, Recently added 2, New collection… none", listOf("3", "", "2", "", ""), rows.map { it.region })
    }

    @Test
    fun theEntrysCountIsTheFoldersNotNewCollection() {
        val one = HomeContent.of(rows("r", 1), rows("f", 1), emptyList(), listOf(SystemGames("snes", "Super Nintendo", rows("s", 1))), otherFolders = emptyList())
        assertEquals("Recent is not counted, nor New collection…", 0, one.entries.first { it.kind == EntryKind.Collections }.shownCount)
    }

    @Test
    fun noPlayAndNoMenuHintInAnEmptyFolder() {
        show(content())
        intoFolder(1) // Most played, empty
        assertFalse("A Play in an empty folder", hasText("Play"))
        assertFalse("START Menu in an empty folder", hasText("Menu"))
        assertFalse("SELECT does nothing in an empty folder", hasText("SELECT"))
        assertTrue("B Back is shown", hasText("Back"))
    }

    @Test
    fun aFolderListShowsBackAndATapOnItGoesBackToTheFolders() {
        show(content(), lists = setOf("recent"))
        intoFolder(0)
        assertTrue("B Back in a list", hasText("Back"))
        compose.onAllNodesWithText("Back", useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        assertTrue("back on the folders: A reads Open", hasText("Open"))
    }

    @Test
    fun anEmptyFolderBackHintTapGoesBackToTheFoldersAndBGoesToo() {
        show(content())
        intoFolder(1)
        compose.onAllNodesWithText("Back", useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        assertTrue(hasText("Open"))
        send(Action.Enter)
        send(Action.Back)
        assertTrue("the gamepad's B too", hasText("Open"))
    }

    private fun assertEmptyLine(content: HomeContent, row: Int, tag: String, line: String) {
        show(content)
        intoFolder(row)
        assertEquals("folder row $row", line, tagText(tag))
    }

    @Test
    fun anEmptyRecentKeepsItsLine() = assertEmptyLine(content(recent = 0), 0, HomeTags.EMPTY_RECENT, "Games you play show up here")

    @Test
    fun anEmptyMostPlayedShowsItsLine() = assertEmptyLine(content(), 1, HomeTags.EMPTY_FOLDER, "Games you play the longest show up here")

    @Test
    fun anEmptyRecentlyAddedShowsItsLine() = assertEmptyLine(content(recentlyAdded = 0), 2, HomeTags.EMPTY_FOLDER, "Games found after setup show up here for 30 days")

    @Test
    fun anEmptyOwnCollectionNamesTheStartKey() = assertEmptyLine(content(own = 0), 3, HomeTags.EMPTY_FOLDER, "Add games with START › Add to collection…")

    @Test
    fun theCollectionsPeekCodeIsAStringResource() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("COL", context.getString(R.string.home_code_collections))
    }
}
