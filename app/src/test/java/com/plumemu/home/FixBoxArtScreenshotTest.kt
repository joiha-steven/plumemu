package com.plumemu.home

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.BoxArtProblem
import com.plumemu.game.BoxArtSearchOutcome
import com.plumemu.game.BoxArtStoreOutcome
import com.plumemu.game.FixBoxArtState
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.boxArtStoredContent
import com.plumemu.game.sampleFacts
import com.plumemu.home.BoxArtPanelFixtures.game
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fix box art… over the board's games screen, at every reference size in
 * Graphite and Paper: idle, searching, the games with thumbnails of three shapes and one missing,
 * a very long name, nothing found, the daily limit, the wait, a game with no box front, the
 * confirmations, and the game menu with its seventh row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class FixBoxArtScreenshotTest {
    private fun shot(
        name: String,
        panel: OpenPanel,
    ) = ReferenceShots.capture(name) { BoxArtPanelFixtures.Screen(panel) }

    private fun down(state: FixBoxArtState, times: Int = 1) = repeat(times) { state.onAction(Action.Move(Direction.Down)) }

    @Test
    fun idle() = shot("game_fix_box_art_idle", BoxArtPanelFixtures.panel(FixBoxArtState("Super Metroid", "Super Nintendo")))

    @Test
    fun searching() {
        val state = FixBoxArtState("Super Metroid", "Super Nintendo")
        down(state)
        state.searching("Super Metroid")
        shot("game_fix_box_art_searching", BoxArtPanelFixtures.panel(state))
    }

    /** The cursor on the second game: the focus fill covers the thumbnail, the name and the system. */
    @Test
    fun results() {
        val state = BoxArtPanelFixtures.results()
        down(state, 2)
        shot("game_fix_box_art_results", BoxArtPanelFixtures.panel(state))
    }

    @Test
    fun resultsOnTheField() = shot("game_fix_box_art_results_field", BoxArtPanelFixtures.panel(BoxArtPanelFixtures.results()))

    @Test
    fun resultsWithAVeryLongName() {
        val state = BoxArtPanelFixtures.results(listOf(BoxArtPanelFixtures.longName, BoxArtPanelFixtures.portrait))
        down(state, 1)
        shot("game_fix_box_art_long_name", BoxArtPanelFixtures.panel(state))
    }

    @Test
    fun resultsFocusOnNoBoxArt() {
        val state = BoxArtPanelFixtures.results()
        down(state, 5)
        shot("game_fix_box_art_results_none", BoxArtPanelFixtures.panel(state))
    }

    @Test
    fun nothingFound() = answered("game_fix_box_art_nothing_found", BoxArtSearchOutcome.NothingFound)

    @Test
    fun dailyLimit() = answered("game_fix_box_art_daily_limit", BoxArtSearchOutcome.Failed(BoxArtProblem.DailyLimit(MIDNIGHT_PARIS)))

    @Test
    fun offline() = answered("game_fix_box_art_offline", BoxArtSearchOutcome.Failed(BoxArtProblem.Offline))

    private fun answered(
        name: String,
        outcome: BoxArtSearchOutcome,
    ) {
        val state = FixBoxArtState("Super Metroid", "Super Nintendo")
        down(state)
        state.searching("Super Metroid")
        state.showSearch("Super Metroid", outcome)
        shot(name, BoxArtPanelFixtures.panel(state))
    }

    /** A pick that takes a while: the games stay under the line. */
    @Test
    fun waitingForPictures() {
        val state = BoxArtPanelFixtures.results()
        down(state, 3)
        state.storing()
        state.waitingForPictures()
        shot("game_fix_box_art_waiting", BoxArtPanelFixtures.panel(state))
    }

    @Test
    fun storingFailedNoBoxFront() {
        val state = BoxArtPanelFixtures.results()
        down(state, 5)
        state.storing()
        state.stored(BoxArtStoreOutcome.NoBoxFront, BoxArtPanelFixtures.missing)
        shot("game_fix_box_art_no_box_front", BoxArtPanelFixtures.panel(state))
    }

    @Test
    fun storedConfirmation() = shot("game_fix_box_art_saved", OpenPanel(game, PanelScreen.Message(boxArtStoredContent(BoxArtPanelFixtures.portrait))))

    @Test
    fun noBoxArtConfirmation() = shot("game_fix_box_art_saved_none", OpenPanel(game, PanelScreen.Message(boxArtStoredContent(null))))

    /** Seven rows now: the cursor on Info, the last, must be fully on screen. */
    @Test
    fun gameMenuWithFixBoxArt() = shot("game_menu_fix_box_art", OpenPanel(game, PanelScreen.Menu(sampleFacts(title = "Super Metroid", canFixBoxArt = true)), focus = 6))

    @Test
    fun gameMenuWithFixBoxArtOnItsRow() = shot("game_menu_fix_box_art_row", OpenPanel(game, PanelScreen.Menu(sampleFacts(title = "Super Metroid", canFixBoxArt = true)), focus = 5))

    private companion object {
        /** Any later time; the line shows it as a clock time. */
        const val MIDNIGHT_PARIS = 2_115_360_000_000L // same time of day as 1_800_000_000_000, ten years on, so the limit has not passed when the test runs
    }
}
