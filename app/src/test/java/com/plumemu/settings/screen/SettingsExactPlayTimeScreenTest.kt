package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.plumemu.MainScreen
import com.plumemu.R
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.FakeGameActions
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

/** Settings › Statistics › Exact play time on the page: what the row says with and without Usage access, and the way it is turned on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsExactPlayTimeScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = mutableStateOf(PlumSettings(flags = SetupFlags(setupCompleted = true)))
    private var granted = false
    private var hasScreen = true
    private var screenOpened = 0
    private lateinit var router: InputRouter

    private val source =
        object : SettingsStatisticsSource {
            override suspend fun read(): StatisticsState = StatisticsState.Read(StatisticsShots.nothingPlayed)

            override val art: HomeArtSource = HomeArtSource.None
        }

    private val activity =
        object : SettingsActivityActions by SettingsActivityActions.None {
            override fun isUsageAccessGranted(): Boolean = granted

            override fun openUsageAccess(): Boolean {
                screenOpened++
                return hasScreen
            }
        }

    private val host =
        SettingsHost(
            versionName = TEST_VERSION,
            update = { settings.value = it(settings.value) },
            resetAllSettings = {},
            resetLibrary = {},
            activity = activity,
            statistics = source,
        )

    private fun openStatistics() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            MainScreen(settings.value, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(games = FakeGameActions()), onRetry = {}, settingsHost = host)
        }
        send(Action.Menu)
        repeat(SettingsSection.Statistics.shownIndex) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
    }

    private fun send(vararg actions: Action) {
        for (action in actions) {
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun needsAccess() = compose.onAllNodes(hasText(context.getString(R.string.settings_exact_play_time_needs_access)), useUnmergedTree = true)

    @Test
    fun theRowStandsOnAPageWithNoGamesWithItsExplanationAndNoAccessWord() {
        openStatistics()
        compose.onNodeWithTag(SettingsTags.option(SettingsOption.ExactPlayTime)).assertIsDisplayed()
        compose.onNodeWithTag(StatisticsTags.EXACT_PLAY_TIME_BODY).assertIsDisplayed()
        needsAccess().assertCountEquals(0)
    }

    @Test
    fun onWithoutAccessTheRowSaysItNeedsUsageAccess() {
        settings.value = settings.value.copy(advanced = AdvancedSettings(exactPlayTime = true))
        openStatistics()
        needsAccess().assertCountEquals(1)
    }

    @Test
    fun onWithAccessTheRowShowsTheSwitchNotTheWords() {
        granted = true
        settings.value = settings.value.copy(advanced = AdvancedSettings(exactPlayTime = true))
        openStatistics()
        needsAccess().assertCountEquals(0)
    }

    @Test
    fun aTapSwitchesItOnAndOpensAndroidsScreenWhenAccessIsMissing() {
        openStatistics()
        compose.onNodeWithTag(SettingsTags.option(SettingsOption.ExactPlayTime)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, screenOpened)
        assertEquals(true, settings.value.advanced.exactPlayTime)
        needsAccess().assertCountEquals(1)
    }

    @Test
    fun withNoUsageAccessScreenTheMessageShowsAndTheSwitchStaysOff() {
        hasScreen = false
        openStatistics()
        compose.onNodeWithTag(SettingsTags.option(SettingsOption.ExactPlayTime)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(false, settings.value.advanced.exactPlayTime)
        compose.onNodeWithText(context.getString(R.string.settings_exact_play_time_unavailable)).assertIsDisplayed()
    }

    @Test
    fun aTapOnWithoutAccessSwitchesItOffEvenWithNoAndroidScreen() {
        hasScreen = false
        settings.value = settings.value.copy(advanced = AdvancedSettings(exactPlayTime = true))
        openStatistics()
        compose.onNodeWithTag(SettingsTags.option(SettingsOption.ExactPlayTime)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(false, settings.value.advanced.exactPlayTime)
        assertEquals(0, screenOpened)
    }
}
