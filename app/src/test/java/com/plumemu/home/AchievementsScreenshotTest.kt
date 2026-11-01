package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.AchievementsSamples
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.graph.AchievementsError
import com.plumemu.graph.AchievementsState
import com.plumemu.graph.NewAchievements
import com.plumemu.achievements.hash.RaCannotHashReason
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RetroAchievements on the screens: Info's section (a set with earned and locked rows and long text, loading,
 * no set, cannot hash, an error), a mastered game's row focused and not, and the line after a game.
 * 768x432 and 576x432, Graphite and Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class AchievementsScreenshotTest {
    /** Info for [state], scrolled with Down [downs] times (every press shows the next row whole; the end stops it). */
    @Composable
    private fun InfoShot(
        state: AchievementsState,
        downs: Int,
    ) {
        val screen = remember(state) { PanelScreen.Info(AchievementsSamples.facts(state)) }
        val panel = remember(screen) { OpenPanel(GameRef("1", "Super Metroid"), screen) }
        HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel)
        LaunchedEffect(screen) {
            // The rows' places are read a frame after they are first laid out.
            repeat(2) { withFrameNanos { } }
            var pressed = 0
            while (pressed < downs && screen.scroll.press(isDown = true)) {
                pressed++
                withFrameNanos { }
            }
        }
    }

    @Test
    fun infoWithASetTop() = ReferenceShots.capture("game_info_achievements_set") { InfoShot(AchievementsSamples.set(), downs = SUMMARY_DOWNS) }

    @Test
    fun infoWithASetFoundByName() =
        ReferenceShots.capture("game_info_achievements_by_name") { InfoShot(AchievementsSamples.set().copy(matchedByName = true), downs = SUMMARY_DOWNS) }

    @Test
    fun infoWithASetEnd() = ReferenceShots.capture("game_info_achievements_set_end") { InfoShot(AchievementsSamples.set(), downs = NO_LIMIT) }

    @Test
    fun infoLoading() = ReferenceShots.capture("game_info_achievements_loading") { InfoShot(AchievementsState.Loading, downs = NO_LIMIT) }

    @Test
    fun infoNoSet() = ReferenceShots.capture("game_info_achievements_no_set") { InfoShot(AchievementsState.NoSet, downs = NO_LIMIT) }

    @Test
    fun infoCannotHash() =
        ReferenceShots.capture("game_info_achievements_cannot_hash") { InfoShot(AchievementsState.CannotHash(RaCannotHashReason.NeedsDecoder), downs = NO_LIMIT) }

    @Test
    fun infoError() = ReferenceShots.capture("game_info_achievements_error") { InfoShot(AchievementsState.Error(AchievementsError.WrongKey), downs = NO_LIMIT) }

    /** A mastered game's row focused (Super Metroid) and the one above it not. */
    @Test
    fun masteredRows() =
        ReferenceShots.capture("home_row_mastered") {
            val focused = AchievementsSamples.FIRST_ID + FakeHomeContent.BOARD_REMEMBERED_ROW
            val unfocused = focused - 1
            CompositionLocalProvider(LocalMasteredGames provides setOf(focused, unfocused)) { HomeAt(AchievementsSamples.contentWithIds(), BoardGamesFocused) }
        }

    @Test
    fun lineAfterAGame() =
        ReferenceShots.capture("home_achievements_line") {
            val line = NewAchievements(gameId = 1, count = 3, gameTitle = "Super Metroid")
            val achievements = HomeAchievements(newAchievements = flow { emit(line); awaitCancellation() })
            CompositionLocalProvider(LocalHomeAchievements provides achievements) { HomeAt(FakeHomeContent.board(), BoardGamesFocused) }
        }

    private companion object {
        /** Enough presses to bring the section's first rows into view under the facts. */
        const val SUMMARY_DOWNS = 1
        const val NO_LIMIT = 80
    }
}
