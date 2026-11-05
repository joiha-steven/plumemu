package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.plumemu.MainScreen
import com.plumemu.R
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.game.FakeGameActions
import com.plumemu.graph.LibraryResetState
import kotlinx.coroutines.flow.MutableStateFlow
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeArtSource
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Statistics inside the main screen: the figures are read once per opening of
 * Settings, the page is walked by buttons and by touch, and A on a game shows it on the main screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsStatisticsScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = mutableStateOf(PlumSettings(flags = SetupFlags(setupCompleted = true)))
    private var writes = 0
    private lateinit var router: InputRouter

    private val source =
        object : SettingsStatisticsSource {
            var reads = 0

            override suspend fun read(): StatisticsState {
                reads++
                return StatisticsState.Read(StatisticsShots.played)
            }

            override val art: HomeArtSource = HomeArtSource.None
        }

    private val library = MutableStateFlow(SettingsContent())
    private val libraryReset = MutableStateFlow(LibraryResetState.Idle)

    private val host =
        SettingsHost(
            versionName = TEST_VERSION,
            update = {
                writes++
                settings.value = it(settings.value)
            },
            resetAllSettings = {},
            resetLibrary = {},
            libraryReset = libraryReset,
            content = library,
            statistics = source,
        )

    private val games = FakeGameActions()

    private fun showMainScreen() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            MainScreen(settings.value, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(games = games), onRetry = {}, settingsHost = host)
        }
    }

    private fun send(vararg actions: Action) {
        for (action in actions) {
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun textUnder(
        tag: String,
        text: String,
    ) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    /** The top bar draws the place name and the section as two texts. */
    private fun assertStatisticsCrumb() {
        // In three columns the section is in its column and the bar says "Settings" only.
        val three = compose.onAllNodesWithTag(SettingsTags.SECTIONS_DIVIDER, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        for (part in if (three) listOf(R.string.settings_title) else listOf(R.string.settings_title, R.string.settings_section_statistics)) {
            compose.onNode(hasText(context.getString(part)) and hasAnyAncestor(hasTestTag(SettingsTags.TOP_BAR)), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun theButtonsWalkTheGamesAndAChangesNothingOnThePageAndBGoesBack() {
        showMainScreen()
        send(Action.Menu)
        assertEquals("read once, as Settings opens", 1, source.reads)
        repeat(SettingsSection.Statistics.shownIndex) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        assertStatisticsCrumb()
        textUnder(StatisticsTags.tile(StatisticTile.Total), context.getString(R.string.settings_statistics_total)).assertIsDisplayed()
        compose.onNodeWithTag(StatisticsTags.figure(StatisticTile.Total), useUnmergedTree = true).assertTextEquals("23\u00A0h 10\u00A0min").assertIsDisplayed()
        compose.onNodeWithTag(StatisticsTags.figure(StatisticTile.Week), useUnmergedTree = true).assertTextEquals("2\u00A0h 10\u00A0min").assertIsDisplayed()
        textUnder(StatisticsTags.game(0), "Super Metroid").assertIsDisplayed()
        repeat(6) { send(Action.Move(Direction.Down)) }
        send(Action.Move(Direction.Right))
        assertStatisticsCrumb()
        assertEquals("nothing is written", 0, writes)
        assertEquals("moving and pressing reads nothing again", 1, source.reads)
        send(Action.Back)
        compose.onNodeWithText(context.getString(R.string.settings_title)).assertIsDisplayed()
        send(Action.Back)
        compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()
        send(Action.Menu)
        assertEquals("once per opening", 2, source.reads)
    }

    @Test
    fun theFiguresAreReadAgainWhenAScanEndsOrResetLibraryForgetsTheGames() {
        showMainScreen()
        send(Action.Menu)
        assertEquals(1, source.reads)
        library.value = SettingsContent(isScanning = true)
        compose.waitForIdle()
        assertEquals("a scan starting changes nothing yet", 1, source.reads)
        library.value = SettingsContent(isScanning = false)
        compose.waitForIdle()
        assertEquals("Rescan ended while Settings is open", 2, source.reads)
        libraryReset.value = LibraryResetState.Forgetting
        compose.waitForIdle()
        libraryReset.value = LibraryResetState.Forgotten
        compose.waitForIdle()
        assertEquals("Reset library forgot the games", 3, source.reads)
        library.value = SettingsContent(isScanning = true)
        compose.waitForIdle()
        library.value = SettingsContent(isScanning = false)
        compose.waitForIdle()
        assertEquals("and its scan ended", 4, source.reads)
    }

    @Test
    fun touchOpensThePageAndATapOnASystemRowDoesNothing() {
        showMainScreen()
        send(Action.Menu)
        compose.onNodeWithTag(SettingsTags.section(SettingsSection.Statistics)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.section(SettingsSection.Statistics)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(StatisticsTags.system(0)).performScrollTo().performClick()
        compose.waitForIdle()
        assertStatisticsCrumb()
        assertEquals(0, writes)
        compose.onNodeWithTag(SettingsTags.HINT_BACK).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.settings_title)).assertIsDisplayed()
    }

    @Test
    fun aOnAGameLeavesSettingsForTheMainScreen() {
        showMainScreen()
        send(Action.Menu)
        repeat(SettingsSection.Statistics.shownIndex) { send(Action.Move(Direction.Down)) }
        send(Action.Enter, Action.Move(Direction.Down), Action.Enter)
        compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()
        assertEquals("nothing was launched: it only shows the game", emptyList<String>(), games.launched)
        assertEquals(0, writes)
    }
}
