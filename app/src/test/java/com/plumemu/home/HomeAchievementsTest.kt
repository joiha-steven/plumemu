package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.components.MASTERED_MARK_TAG
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.AchievementsSamples
import com.plumemu.graph.NewAchievements
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** RetroAchievements on the main screen: the trophy only on mastered games' rows, and the quiet line after a game. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeAchievementsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun trophies() = compose.onAllNodes(hasTestTag(MASTERED_MARK_TAG), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun showRows(mastered: Set<Long>) {
        compose.setContent {
            PlumTheme { CompositionLocalProvider(LocalMasteredGames provides mastered) { HomeAt(AchievementsSamples.contentWithIds(), BoardGamesFocused) } }
        }
        compose.waitForIdle()
    }

    @Test
    fun noRowHasATrophyWhenNothingIsMastered() {
        showRows(emptySet())
        assertEquals(0, trophies())
    }

    @Test
    fun onlyMasteredGamesHaveATrophy() {
        val first = AchievementsSamples.FIRST_ID
        showRows(setOf(first, first + 2, first + 90_000))
        assertEquals("two of the shown rows are mastered; an id that is in no row marks nothing", 2, trophies())
    }

    @Test
    fun theFocusedRowKeepsItsTrophy() {
        val focused = AchievementsSamples.FIRST_ID + FakeHomeContent.BOARD_REMEMBERED_ROW
        showRows(setOf(focused))
        assertEquals(1, trophies())
    }

    @Test
    fun aRowKeyThatIsNotADatabaseIdIsNeverMastered() {
        assertFalse(setOf(1L, 2L).contains("snes-6"))
        assertFalse(emptySet<Long>().contains("1"))
        assertTrue(setOf(1L).contains("1"))
        assertFalse(setOf(1L).contains("2"))
    }

    private val gameLine = NewAchievements(gameId = 1, count = 3, gameTitle = "Super Metroid")
    private val unlocks = MutableSharedFlow<NewAchievements>(extraBufferCapacity = 4)

    private fun showHome() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            val router = remember { InputRouter(scope) }
            PlumTheme {
                CompositionLocalProvider(LocalHomeAchievements provides HomeAchievements(newAchievements = unlocks)) {
                    HomeScreen(FakeHomeContent.board(), router, HomeCallbacks())
                }
            }
        }
        compose.mainClock.advanceTimeBy(50)
    }

    @Test
    fun afterAGameTheHeaderSaysHowManyUnlockedThenReadsAsUsualAgain() {
        showHome()
        compose.onNodeWithText("+3 achievements · Super Metroid", substring = true, useUnmergedTree = true).assertDoesNotExist()
        unlocks.tryEmit(gameLine)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("+3 achievements · Super Metroid", substring = true, useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(ACHIEVEMENTS_LINE_SHOWN_MS + 600)
        compose.onNodeWithText("+3 achievements", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("systems", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun oneUnlockReadsInTheSingular() {
        showHome()
        unlocks.tryEmit(gameLine.copy(count = 1))
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("+1 achievement · Super Metroid", substring = true, useUnmergedTree = true).assertExists()
    }

}
