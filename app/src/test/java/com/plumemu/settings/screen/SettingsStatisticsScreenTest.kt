package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.plumemu.MainScreen
import com.plumemu.R
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.game.FakeGameActions
import com.plumemu.graph.LibraryResetState
import com.plumemu.launcher.FileProblem
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.telemetry.LaunchFrom
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
 * Settings, the page is walked by buttons and by touch, and A on a figure changes nothing.
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

    private fun statisticsCrumb() =
        context.getString(R.string.settings_crumb, context.getString(R.string.settings_title), context.getString(R.string.settings_section_statistics))

    @Test
    fun theButtonsWalkThePageAndAChangesNothingAndBGoesBack() {
        showMainScreen()
        send(Action.Menu)
        assertEquals("read once, as Settings opens", 1, source.reads)
        repeat(SettingsSection.Statistics.ordinal) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        compose.onNodeWithText(statisticsCrumb()).assertIsDisplayed()
        textUnder(SettingsTags.statistic(0), context.getString(R.string.settings_statistics_total)).assertIsDisplayed()
        textUnder(SettingsTags.statisticValue(0), "23 h 10 min").assertIsDisplayed()
        textUnder(SettingsTags.statistic(1), context.getString(R.string.settings_statistics_this_week)).assertIsDisplayed()
        textUnder(SettingsTags.statisticValue(1), "2 h 10 min").assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).assertDoesNotExist()
        repeat(8) { send(Action.Move(Direction.Down)) }
        textUnder(SettingsTags.statisticValue(8), TEST_VERSION).assertIsDisplayed()
        send(Action.Enter, Action.Move(Direction.Right))
        compose.onNodeWithText(statisticsCrumb()).assertIsDisplayed()
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
    fun touchOpensThePageFocusesARowAndASecondTapChangesNothing() {
        showMainScreen()
        send(Action.Menu)
        compose.onNodeWithTag(SettingsTags.section(SettingsSection.Statistics)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.section(SettingsSection.Statistics)).performClick()
        compose.waitForIdle()
        // The most-played system: a game row's second tap plays (below).
        compose.onNodeWithTag(SettingsTags.statistic(5)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.settings_crumb, statisticsCrumb(), "Super Nintendo")).assertDoesNotExist()
        compose.onNodeWithTag(SettingsTags.statistic(5)).performClick()
        compose.onNodeWithTag(SettingsTags.statisticValue(5)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(statisticsCrumb()).assertIsDisplayed()
        assertEquals(0, writes)
        compose.onNodeWithTag(SettingsTags.HINT_BACK).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.settings_title)).assertIsDisplayed()
    }

    private fun openStatisticsAtRow(index: Int) {
        showMainScreen()
        send(Action.Menu)
        repeat(SettingsSection.Statistics.ordinal) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        repeat(index) { send(Action.Move(Direction.Down)) }
    }

    @Test
    fun aPlaysTheFocusedGameFromStatisticsAndTheHintBarSaysPlay() {
        openStatisticsAtRow(2)
        compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).assertIsDisplayed()
        textUnder(SettingsTags.HINT_CONFIRM, context.getString(R.string.home_hint_play)).assertIsDisplayed()
        send(Action.Enter)
        assertEquals(listOf("1"), games.launched)
        assertEquals(listOf(LaunchFrom.Statistics), games.launchesFrom)
        assertEquals("nothing is written", 0, writes)
    }

    @Test
    fun onlyAGameRowsValueIsAButtonForAccessibility() {
        openStatisticsAtRow(0)
        val noClick = SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick)
        compose.onNodeWithTag(SettingsTags.statisticValue(0), useUnmergedTree = true).assert(noClick)
        compose.onNodeWithTag(SettingsTags.statisticValue(3), useUnmergedTree = true).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        // Version, a read-only row outside Statistics.
        send(Action.Back)
        send(Action.Move(Direction.Down))
        repeat(SettingsSection.About.ordinal - SettingsSection.Statistics.ordinal - 1) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        compose.onNodeWithTag(SettingsTags.value(SettingsOption.Version), useUnmergedTree = true).assert(noClick)
    }

    @Test
    fun aTapOnTheFocusedGameRowPlaysIt() {
        openStatisticsAtRow(0)
        compose.onNodeWithTag(SettingsTags.statistic(3)).performClick()
        compose.waitForIdle()
        assertEquals("the first tap only focuses", emptyList<String>(), games.launched)
        compose.onNodeWithTag(SettingsTags.statistic(3)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("2"), games.launched)
        assertEquals(listOf(LaunchFrom.Statistics), games.launchesFrom)
    }

    @Test
    fun aGameWhoseFileIsGoneShowsTheMainScreensMessageAndBClosesIt() {
        games.outcome = { LaunchOutcome.FileMissing(1, "Super Metroid.sfc", FileProblem.Gone) }
        openStatisticsAtRow(2)
        send(Action.Enter)
        compose.onNodeWithText(context.getString(R.string.game_error_file_gone_title)).assertIsDisplayed()
        send(Action.Back)
        compose.onNodeWithText(context.getString(R.string.game_error_file_gone_title)).assertDoesNotExist()
        compose.onNodeWithText(statisticsCrumb()).assertIsDisplayed()
    }

    @Test
    fun theSidePanelShowsTheFocusedGameAndOtherRowsKeepThePrivacyExplanation() {
        openStatisticsAtRow(0)
        val body = context.getString(R.string.settings_statistics_body)
        compose.onNodeWithText(body).assertIsDisplayed()
        compose.onNodeWithTag(SettingsPanelTags.STATISTIC_COVER, useUnmergedTree = true).assertDoesNotExist()
        send(Action.Move(Direction.Down), Action.Move(Direction.Down))
        compose.onNodeWithText(body).assertDoesNotExist()
        compose.onNodeWithTag(SettingsPanelTags.STATISTIC_COVER, useUnmergedTree = true).assertIsDisplayed()
        textUnder(SettingsPanelTags.STATISTIC_FACTS, "12 h 5 min").assertIsDisplayed()
        textUnder(SettingsPanelTags.STATISTIC_FACTS, "41").assertIsDisplayed()
        textUnder(SettingsPanelTags.STATISTIC_FACTS, context.getString(R.string.home_details_today)).assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.settings_statistics_play_hint, "A")) and hasTestTag(SettingsPanelTags.STATISTIC_PLAY_HINT), useUnmergedTree = true).assertIsDisplayed()
        // The third game has no stored play log: those two lines are left out, not shown as 0 or Never.
        send(Action.Move(Direction.Down), Action.Move(Direction.Down))
        compose.onNodeWithTag(SettingsPanelTags.STATISTIC_FACTS, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_statistics_times_played)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.home_details_last_played)).assertDoesNotExist()
        textUnder(SettingsPanelTags.STATISTIC_FACTS, "55 min").assertIsDisplayed()
        send(Action.Move(Direction.Down))
        compose.onNodeWithText(body).assertIsDisplayed()
    }
}
