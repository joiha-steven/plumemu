package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.game.FakeGameActions
import com.plumemu.sound.Heard
import com.plumemu.sound.LocalUiSounds
import com.plumemu.sound.UiSound
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sounds of the whole main screen with a real router: what each press earns, and the presses that earn nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeSoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private val heard = Heard()
    private lateinit var router: InputRouter

    private fun show(
        content: HomeContent,
        gridSize: GridSize = GridSize.Default,
    ) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            CompositionLocalProvider(LocalUiSounds provides heard.uiSounds) { PlumTheme(PlumThemeSettings.Default.copy(gridSize = gridSize)) { HomeScreen(content, router, HomeCallbacks(games = games)) } }
        }
        compose.waitForIdle()
    }

    /** Presses [actions] one by one and returns what played after each. */
    private fun press(vararg actions: Action): List<List<UiSound>> =
        actions.map { action ->
            heard.tick()
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
            heard.drain()
        }

    private val down = Action.Move(Direction.Down)
    private val move = listOf(UiSound.Move)
    private val confirm = listOf(UiSound.Confirm)
    private val none = emptyList<UiSound>()

    @Test
    fun theStripMovesOpensAndGoesBack() {
        show(FakeHomeContent.board())
        val heardAfter = press(down, Action.Enter, down, Action.Back, Action.Move(Direction.Up), Action.Move(Direction.Up, isRepeat = true))
        // Down moves; A opens the games column (confirm); Down moves; B goes back; Up moves; a held Up at the top stops.
        assertEquals(listOf(move, confirm, move, listOf(UiSound.Back), move, none), heardAfter)
    }

    private fun openSnesGrid() =
        press(*List(FakeHomeContent.BOARD_SNES_INDEX) { down }.toTypedArray(), Action.Enter, Action.ToggleView)

    @Test
    fun aGridSizeChangeMovesAndTheEndsAreSilent() {
        show(FakeHomeContent.withArtwork(), GridSize.Large)
        openSnesGrid()
        // Large: R1 has nowhere to go. L1 asks for Medium and again for Small (this theme does not follow the writes), then L1 at Small.
        assertEquals(listOf(none, move, move, none), press(Action.NextSystem, Action.PreviousSystem, Action.PreviousSystem, Action.PreviousSystem))
    }

    @Test
    fun aPressThatDoesNothingIsSilent() {
        show(FakeHomeContent.noGames())
        val heardAfter =
            press(
                Action.ToggleView, // Recent has no grid
                Action.Search, // the library is empty
                down, // Favorites
                down, // Apps
                Action.Move(Direction.Right), // into the apps
                Action.ToggleFavorite, // apps are no favorites
            )
        assertEquals(listOf(none, none, move, move, move, none), heardAfter)
    }

    @Test
    fun startOnAnAppOpensItsMenuWithAConfirmSound() {
        show(FakeHomeContent.noGames())
        val heardAfter = press(down, down, Action.Move(Direction.Right), Action.Menu)
        assertEquals(listOf(move, move, move, confirm), heardAfter)
    }

    @Test
    fun aTapBackToTheSystemsMovesInsteadOfConfirming() {
        show(FakeHomeContent.board())
        val snes = Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)
        assertEquals(listOf(move, confirm, move), press(snes, snes, snes))
    }

    @Test
    fun aPlayWhileALaunchRunsIsSilent() {
        games.launchGate = CompletableDeferred()
        show(FakeHomeContent.board())
        val heardAfter = press(Action.Enter, Action.Enter, Action.Enter)
        assertEquals("open the games, start one, then A again while it launches", listOf(confirm, confirm, none), heardAfter)
    }

    @Test
    fun aTapThatFocusesMovesAndASecondOneOpens() {
        show(FakeHomeContent.board())
        val snes = Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)
        assertEquals("the first tap focuses the system, the second opens its games", listOf(move, confirm), press(snes, snes))
    }
}
