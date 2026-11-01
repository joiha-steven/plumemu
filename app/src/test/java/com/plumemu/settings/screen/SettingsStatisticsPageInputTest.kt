package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Statistics' page by gamepad and touch, with [SettingsScreen] hosted directly so the "show it on the main screen"
 * call can be read: only games take the cursor, Down and Up past the ends scroll the page, A or a tap on a game goes to it,
 * a system row does nothing, and the hint bar says "Go to" (and no "More" on a narrow screen).
 */
abstract class SettingsStatisticsPageInputBase(
    private val isNarrow: Boolean,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var router: InputRouter
    private val shown = mutableListOf<Pair<String, Long>>()

    private fun open(figures: PlayStatistics = StatisticsShots.played) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            PlumTheme(PlumThemeSettings.Graphite) {
                StatisticsShots.WithCovers {
                    SettingsScreen(
                        PlumSettings(flags = SetupFlags(setupCompleted = true)), router, NoActions, TEST_VERSION,
                        content = StatisticsShots.contentOf(figures), showOnHome = { system, id -> shown += system to id },
                    )
                }
            }
        }
        send(*Array(SettingsSection.Statistics.shownIndex) { Action.Move(Direction.Down) })
        send(Action.Enter)
    }

    private fun send(vararg actions: Action) {
        for (action in actions) {
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun down(times: Int = 1) = send(*Array(times) { Action.Move(Direction.Down) })

    private fun up(times: Int = 1) = send(*Array(times) { Action.Move(Direction.Up) })

    private fun top(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    private fun bottom(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom.value

    /** Whether the footer is whole inside the page's window. */
    private fun footerInView() = bottom(StatisticsTags.FOOTER) <= bottom(StatisticsTags.PAGE) + 0.5f

    private fun tilesAtTop() = Math.abs(top(StatisticsTags.TILES) - top(StatisticsTags.PAGE)) < 0.5f

    @Test
    fun onlyGamesTakeTheCursorAndDownStepsGameToGame() {
        open()
        for ((step, game) in StatisticsShots.played.topGames.withIndex()) {
            if (step > 0) down()
            send(Action.Enter)
            assertEquals("the cursor is on game $step", game.systemId to game.id, shown.last())
        }
        assertEquals("one A per game, nothing else answered", 5, shown.size)
    }

    @Test
    fun downOnTheLastGameReadsOnToTheEndAndUpComesBackToItBeforeMoving() {
        open()
        assertTrue("the footer starts below the fold on this screen", !footerInView())
        down(4)
        var presses = 0
        while (!footerInView() && presses < MAX_PRESSES) {
            down()
            presses++
        }
        // Narrow screens stack Systems under the games, taller than the window: the D-pad still reaches the footer.
        assertTrue("the footer came into view after $presses presses on the last game", footerInView())
        assertTrue("the page scrolled off its top", !tilesAtTop())
        send(Action.Enter)
        assertEquals("the cursor is still on the last game", "gba" to 5L, shown.last())
        var ups = 0
        while (!lastGameWhole() && ups < MAX_PRESSES) {
            send(Action.Move(Direction.Up))
            ups++
        }
        assertTrue("Up brought the last game back whole after $ups presses", lastGameWhole())
        send(Action.Enter)
        assertEquals("Up scrolled back without leaving the last game", "gba" to 5L, shown.last())
    }

    private fun lastGameWhole(): Boolean {
        val game = compose.onNodeWithTag(StatisticsTags.game(4), useUnmergedTree = true).getUnclippedBoundsInRoot()
        return game.top.value >= top(StatisticsTags.PAGE) - 0.5f && game.bottom.value <= bottom(StatisticsTags.PAGE) + 0.5f
    }

    @Test
    fun upOnTheFirstGameScrollsBackToTheTop() {
        open()
        assertTrue("starts at the top", tilesAtTop())
        repeat(SWIPES) { compose.onNodeWithTag(StatisticsTags.PAGE).performTouchInput { swipeUp() } }
        compose.waitForIdle()
        assertTrue("a finger scrolled the page away from the top", !tilesAtTop())
        var presses = 0
        while (!tilesAtTop() && presses < MAX_PRESSES) {
            up()
            presses++
        }
        assertTrue("the tiles are back at the top after $presses presses", tilesAtTop())
        send(Action.Enter)
        assertEquals("the cursor never left the first game", "snes" to 1L, shown.last())
    }

    @Test
    fun withNothingPlayedNoGameTakesTheCursorAndAChangesNothing() {
        open(StatisticsShots.nothingPlayed)
        down(2)
        up(2)
        send(Action.Enter)
        assertEquals(emptyList<Pair<String, Long>>(), shown)
        compose.onNodeWithTag(StatisticsTags.game(0), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).assertDoesNotExist()
        compose.onNodeWithTag(StatisticsTags.INVITE, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aTapOnAGameGoesToItAndATapOnASystemDoesNothing() {
        open()
        compose.onNodeWithTag(StatisticsTags.game(1), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("snes" to 2L), shown)
        compose.onNodeWithTag(StatisticsTags.system(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("a system row is information only", listOf("snes" to 2L), shown)
        compose.onNodeWithTag(StatisticsTags.PAGE).assertIsDisplayed()
    }

    @Test
    fun theHintBarSaysGoToOnAGameAndHasNoMoreHintOnNarrow() {
        open()
        compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.settings_hint_go_to)) and hasAnyAncestor(hasTestTag(SettingsTags.HINT_CONFIRM)), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.HINT_MORE).assertDoesNotExist()
        send(Action.Move(Direction.Right))
        compose.onNodeWithTag(SettingsTags.HINT_MORE).assertDoesNotExist()
        compose.onNodeWithTag(StatisticsTags.game(0), useUnmergedTree = true).assertIsDisplayed()
        assertEquals("Right on the page does nothing, narrow ($isNarrow) or wide", emptyList<Pair<String, Long>>(), shown)
    }

    private companion object {
        const val MAX_PRESSES = 12
        const val SWIPES = 4
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsStatisticsPageInput768Test : SettingsStatisticsPageInputBase(isNarrow = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsStatisticsPageInput576Test : SettingsStatisticsPageInputBase(isNarrow = true)
