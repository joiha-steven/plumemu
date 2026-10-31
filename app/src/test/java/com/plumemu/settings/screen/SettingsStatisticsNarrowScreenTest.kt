package com.plumemu.settings.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.MainScreen
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.FakeGameActions
import com.plumemu.graph.LibraryResetState
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeArtSource
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Statistics on a narrow screen: a game row has no side panel, so no "More" hint and nothing opens; A plays. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsStatisticsNarrowScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = mutableStateOf(PlumSettings(flags = SetupFlags(setupCompleted = true)))
    private lateinit var router: InputRouter
    private val games = FakeGameActions()

    private val host =
        SettingsHost(
            versionName = "1.0",
            update = { settings.value = it(settings.value) },
            resetAllSettings = {},
            resetLibrary = {},
            libraryReset = MutableStateFlow(LibraryResetState.Idle),
            content = MutableStateFlow(SettingsContent()),
            statistics =
                object : SettingsStatisticsSource {
                    override suspend fun read(): StatisticsState = StatisticsState.Read(StatisticsShots.played)

                    override val art: HomeArtSource = HomeArtSource.None
                },
        )

    private fun send(vararg actions: Action) {
        for (action in actions) {
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun openStatisticsAtRow(index: Int) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            MainScreen(settings.value, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(games = games), onRetry = {}, settingsHost = host)
        }
        send(Action.Menu)
        repeat(SettingsSection.Statistics.ordinal) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        repeat(index) { send(Action.Move(Direction.Down)) }
    }

    @Test
    fun aGameRowOffersPlayAndNoMoreHintAndDpadRightOpensNothing() {
        openStatisticsAtRow(2)
        compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.HINT_MORE).assertDoesNotExist()
        send(Action.Move(Direction.Right))
        compose.onNodeWithTag(SettingsTags.HINT_MORE).assertDoesNotExist()
        compose.onNodeWithTag(SettingsPanelTags.STATISTIC_PLAY_HINT, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(SettingsTags.statistic(2)).assertIsDisplayed()
        assertEquals("nothing was launched", emptyList<String>(), games.launched)
        send(Action.Enter)
        assertEquals(listOf("1"), games.launched)
    }

    @Test
    fun anotherReadOnlyRowStillOffersTheExplanationOnNarrow() {
        openStatisticsAtRow(0)
        compose.onNodeWithTag(SettingsTags.HINT_MORE).assertIsDisplayed()
        // One way in, not two: "› More" stays, A says nothing and does nothing.
        compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).assertDoesNotExist()
        send(Action.Enter)
        compose.onNodeWithTag(SettingsTags.HINT_MORE).assertIsDisplayed()
        assertEquals("nothing was launched", emptyList<String>(), games.launched)
    }
}
