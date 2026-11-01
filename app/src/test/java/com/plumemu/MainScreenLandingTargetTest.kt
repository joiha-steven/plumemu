package com.plumemu

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.FakeGameActions
import com.plumemu.home.FIRST_SYSTEM_INDEX
import com.plumemu.home.GameRowModel
import com.plumemu.home.GamesPagesInfo
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.HomeContent
import com.plumemu.home.HomeLandingTarget
import com.plumemu.home.SystemGames
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.SettingsHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A game chosen elsewhere (Statistics' most-played) is landed on like the Home button lands on
 * Recent: its system's list, its row, games column. A Home press afterwards lands on Recent again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenLandingTargetTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private val homePresses = HomePresses()
    private lateinit var router: InputRouter

    private val content =
        HomeContent.of(
            List(5) { GameRowModel("recent-$it", "Played $it", "USA") },
            emptyList(),
            emptyList(),
            listOf(systemOf("alpha", 5), systemOf("beta", 40)),
        )

    private fun show(target: HomeLandingTarget?) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            val host = remember { SettingsHost("test", update = {}, resetAllSettings = {}, resetLibrary = {}) }
            MainScreen(
                PlumSettings.Defaults,
                HomeState.Ready(content),
                router,
                HomeCallbacks(games = games),
                onRetry = {},
                settingsHost = host,
                homePresses = homePresses,
                landOnStart = target,
            )
        }
        compose.waitForIdle()
    }

    private fun shownSystem(): Int =
        compose.onNode(SemanticsMatcher.keyIsDefined(GamesPagesInfo), useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(GamesPagesInfo)!!.pages.firstVisibleItemIndex

    /** A on the focused game: what it plays says where the focus is. */
    private fun gamePlayedByA(): List<String> {
        games.launched.clear()
        compose.runOnIdle { router.send(Action.Enter) }
        compose.waitForIdle()
        return games.launched.toList()
    }

    @Test
    fun aTargetLandsOnThatGameInItsSystem() {
        show(HomeLandingTarget("beta", "beta-30"))

        assertEquals(FIRST_SYSTEM_INDEX + 1, shownSystem())
        assertEquals(listOf("beta-30"), gamePlayedByA())
    }

    @Test
    fun aTargetWhoseGameIsGoneLandsOnTheSystemsFirstGame() {
        show(HomeLandingTarget("beta", "beta-999"))

        assertEquals(FIRST_SYSTEM_INDEX + 1, shownSystem())
        assertEquals(listOf("beta-0"), gamePlayedByA())
    }

    @Test
    fun aTargetWhoseSystemIsGoneLandsAsUsual() {
        show(HomeLandingTarget("gone", "gone-1"))

        assertEquals(0, shownSystem())
        assertEquals(listOf("recent-0"), gamePlayedByA())
    }

    @Test
    fun aHomePressAfterATargetLandsOnRecentAgain() {
        show(HomeLandingTarget("beta", "beta-30"))
        assertEquals(listOf("beta-30"), gamePlayedByA())

        compose.runOnIdle { homePresses.press() }
        compose.waitForIdle()

        assertEquals(0, shownSystem())
        assertEquals(listOf("recent-0"), gamePlayedByA())
    }

    private fun systemOf(
        id: String,
        count: Int,
    ) = SystemGames(id, id.replaceFirstChar { it.uppercase() }, List(count) { GameRowModel("$id-$it", "$id game $it", "USA") })
}
