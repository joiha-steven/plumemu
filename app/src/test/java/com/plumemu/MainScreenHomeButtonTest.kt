package com.plumemu

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GamePanelTags
import com.plumemu.home.FIRST_SYSTEM_INDEX
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.GameRowModel
import com.plumemu.home.GamesPagesInfo
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.HomeContent
import com.plumemu.home.HomeTags
import com.plumemu.home.SystemGames
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.home.search.SearchTags
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.setup.FakeSetupSource
import com.plumemu.setup.HomeRoleActions
import com.plumemu.setup.SetupModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Home button pressed while Plumemu is in front (option A): everything
 * open closes and the strip returns to Favorites' first game (else the Favorites entry with its empty line: the
 * rule since the Collections change, Recent being a folder). A press is one [HomePresses.press], as MainActivity sends it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenHomeButtonTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private val homePresses = HomePresses()
    private lateinit var router: InputRouter
    private val fifthSystem = FIRST_SYSTEM_INDEX + 4 // Nintendo DS
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() = modelScope.cancel()

    private fun show(content: HomeContent = FakeHomeContent.large()) = show(mutableStateOf(content))

    private fun show(content: State<HomeContent>) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SettingsHost("test", update = {}, resetAllSettings = {}, resetLibrary = {}) }
            MainScreen(
                PlumSettings.Defaults,
                HomeState.Ready(content.value),
                router,
                HomeCallbacks(games = games),
                onRetry = {},
                settingsHost = host,
                homePresses = homePresses,
            )
        }
    }

    private fun press(vararg actions: Action) {
        actions.forEach {
            compose.runOnIdle { router.send(it) }
            compose.waitForIdle()
        }
    }

    private fun pressHome() {
        compose.runOnIdle { homePresses.press() }
        compose.waitForIdle()
    }

    private fun shownSystem(): Int =
        compose.onNode(SemanticsMatcher.keyIsDefined(GamesPagesInfo), useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(GamesPagesInfo)!!.pages.firstVisibleItemIndex

    /** A on the focused game: what it plays says where the focus is (the systems column would only move right). */
    private fun gamePlayedByA(): List<String> {
        games.launched.clear()
        press(Action.Enter)
        return games.launched.toList()
    }

    private fun assertLandedOnFavoritesFirstGame() {
        compose.onNodeWithTag(HomeTags.SYSTEMS).assertIsDisplayed()
        assertEquals("Favorites", 0, shownSystem())
        assertEquals(listOf("favorite-0"), gamePlayedByA())
    }

    @Test
    fun homeWhileSettingsIsOpenReturnsToFavoritesFirstGame() {
        show()
        press(Action.TapItem(Column.Systems, fifthSystem), Action.OpenSettings)
        compose.onNodeWithTag(HomeTags.SYSTEMS).assertDoesNotExist()

        pressHome()

        assertLandedOnFavoritesFirstGame()
    }

    @Test
    fun homeWhileSearchIsOpenClosesItAndLands() {
        show()
        press(Action.TapItem(Column.Systems, fifthSystem), Action.Search)
        compose.onNodeWithTag(SearchTags.TOP_BAR).assertIsDisplayed()

        pressHome()

        compose.onNodeWithTag(SearchTags.TOP_BAR).assertDoesNotExist()
        assertLandedOnFavoritesFirstGame()
    }

    @Test
    fun homeWhileTheGameMenuIsOpenClosesItAndLands() {
        show()
        press(Action.TapItem(Column.Systems, fifthSystem), Action.Enter, Action.Move(Direction.Down), Action.Menu)
        compose.onNodeWithTag(GamePanelTags.PANEL).assertIsDisplayed()

        pressHome()

        compose.onNodeWithTag(GamePanelTags.PANEL).assertDoesNotExist()
        assertLandedOnFavoritesFirstGame()
    }

    @Test
    fun homeFromDeepInASystemListLandsOnFavoritesFirstGame() {
        show()
        press(Action.TapItem(Column.Systems, fifthSystem), Action.Enter)
        repeat(30) { press(Action.Move(Direction.Down)) }
        compose.onNode(rowTitled("Nintendo DS title 31")).assertIsDisplayed()

        pressHome()

        assertLandedOnFavoritesFirstGame()
        compose.onNode(rowTitled("Nintendo DS title 31")).assertDoesNotExist()
    }

    // The list that scrolls back is Favorites since the Collections change (the cold start's entry), not Recent.
    @Test
    fun homeFromDeepInFavoritesScrollsBackToItsFirstGame() {
        val favorites = List(40) { GameRowModel("favorite-$it", "Loved $it", "USA") }
        show(HomeContent.of(emptyList(), favorites, emptyList(), listOf(systemOf("alpha", 3))))
        press(Action.Enter)
        repeat(30) { press(Action.Move(Direction.Down)) }
        compose.onNode(rowTitled("Loved 30")).assertIsDisplayed()

        pressHome()

        compose.onNode(rowTitled("Loved 0")).assertIsDisplayed()
        assertEquals(listOf("favorite-0"), gamePlayedByA())
    }

    // New since the Collections change: Home from inside the Recent folder lands on Favorites, the folder is left.
    @Test
    fun homeFromInsideTheRecentFolderLandsOnFavoritesFirstGame() {
        show()
        press(Action.TapItem(Column.Systems, 1), Action.Enter, Action.Enter, Action.Move(Direction.Right))

        pressHome()

        assertLandedOnFavoritesFirstGame()
    }

    // Expectation changed with the requirement: with no favorites Home lands on the Favorites entry, which shows its empty line (not the first system).
    @Test
    fun homeWithNoFavoritesLandsOnTheFavoritesEntryAndItsEmptyLine() {
        show(HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(systemOf("alpha", 5), systemOf("beta", 5))))
        press(Action.TapItem(Column.Systems, FIRST_SYSTEM_INDEX + 1), Action.Enter, Action.Move(Direction.Down))

        pressHome()

        compose.onNodeWithTag(HomeTags.SYSTEMS).assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.EMPTY_FAVORITES).assertIsDisplayed() // the Favorites entry is focused: its empty line, no games list
        assertEquals("A on the empty entry plays nothing", emptyList<String>(), gamePlayedByA())
    }

    @Test
    fun homeWithNothingToLandOnIsSpentByTheNextAction() {
        val apps = List(3) { GameRowModel("app-$it", "App $it", "") }
        val content = mutableStateOf(HomeContent.of(emptyList(), emptyList(), apps, emptyList()))
        val appsIndex = FIRST_SYSTEM_INDEX - 1
        show(content)
        pressHome()
        press(Action.TapItem(Column.Systems, appsIndex))

        // A scan finishes while the person is in Apps: the old press must not pull them to its first system.
        content.value = HomeContent.of(emptyList(), emptyList(), apps, listOf(systemOf("alpha", 5), systemOf("beta", 5)))
        compose.waitForIdle()

        assertEquals(appsIndex, shownSystem())
    }

    // Expectation changed with the requirement: setup ends on Favorites' first game, not Recent's.
    @Test
    fun settingUpEndsOnFavoritesFirstGameWhenItHasGames() {
        assertSetupEndsOn(FakeHomeContent.large(), expectedGame = "favorite-0", expectedSystem = 0)
    }

    // Expectation changed with the requirement: with no favorites setup ends on the Favorites entry, not the first system.
    @Test
    fun settingUpEndsOnTheFavoritesEntryWhenThereAreNoFavorites() {
        val content = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(systemOf("alpha", 5), systemOf("beta", 5)))
        assertSetupEndsOn(content, expectedGame = null, expectedSystem = 0)
    }

    @Test
    fun settingUpEndsOnTheFinalTargetWhenTheLibraryFillsInAfterwards() {
        val content = mutableStateOf<HomeContent>(HomeContent.of(emptyList(), emptyList(), emptyList(), emptyList()))
        val settings = mutableStateOf(PlumSettings.Defaults)
        val model = SetupModel(modelScope, FakeSetupSource()) { }
        val noRole =
            object : HomeRoleActions {
                override fun isHomeApp() = false

                override fun requestHomeRole(onAnswered: (Boolean) -> Unit) = false

                override fun markAsked() = Unit
            }
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SetupHost({ model }, {}, noRole) }
            MainScreen(settings.value, HomeState.Ready(content.value), router, HomeCallbacks(games = games), onRetry = {}, setup = host)
        }
        settings.value = PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = true))
        compose.waitForIdle()

        content.value = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(systemOf("alpha", 5), systemOf("beta", 5)))
        compose.waitForIdle()

        // Expectation changed with the requirement: the final target is the Favorites entry (no favorites here), not the first system.
        compose.onNodeWithTag(HomeTags.EMPTY_FAVORITES).assertIsDisplayed()
        assertEquals(emptyList<String>(), gamePlayedByA())
    }

    private fun assertSetupEndsOn(
        content: HomeContent,
        expectedGame: String?,
        expectedSystem: Int,
    ) {
        val settings = mutableStateOf(PlumSettings.Defaults)
        val noRole =
            object : HomeRoleActions {
                override fun isHomeApp() = false

                override fun requestHomeRole(onAnswered: (Boolean) -> Unit) = false

                override fun markAsked() = Unit
            }
        val model = SetupModel(modelScope, FakeSetupSource()) { }
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SetupHost({ model }, {}, noRole) }
            MainScreen(settings.value, HomeState.Ready(content), router, HomeCallbacks(games = games), onRetry = {}, setup = host)
        }
        compose.onNodeWithTag(HomeTags.SYSTEMS).assertDoesNotExist()

        settings.value = PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = true))
        compose.waitForIdle()

        compose.onNodeWithTag(HomeTags.SYSTEMS).assertIsDisplayed()
        if (expectedGame == null) compose.onNodeWithTag(HomeTags.EMPTY_FAVORITES).assertIsDisplayed() else assertEquals(expectedSystem, shownSystem())
        assertEquals(listOfNotNull(expectedGame), gamePlayedByA())
    }

    private fun systemOf(
        id: String,
        count: Int,
    ) = SystemGames(id, id.replaceFirstChar { it.uppercase() }, List(count) { GameRowModel("$id-$it", "$id game $it", "USA") })

    /** [title] in a games list, not in the text-only details column that names the focused game at 768. */
    private fun rowTitled(title: String) = hasText(title) and !hasAnyAncestor(hasTestTag(HomeTags.DETAILS))
}
