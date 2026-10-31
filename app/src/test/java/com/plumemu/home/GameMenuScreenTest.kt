package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.Beetle
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GameMenuEntryId
import com.plumemu.game.GamePanelTags
import com.plumemu.game.sampleListing
import com.plumemu.launcher.FileProblem
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.graph.ClosedAtOnce
import com.plumemu.graph.CoreQuestion
import com.plumemu.coredata.db.AddedSession
import com.plumemu.game.CORE_NO_KEY
import com.plumemu.launcher.choice.CoreToConfirm
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Play and the game menu on the whole screen with a real router, by gamepad and by touch: open,
 * move, pick, close, and the focus back on the same game (or the next one after Hide).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GameMenuScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private lateinit var router: InputRouter
    private var content by mutableStateOf(FakeHomeContent.board())

    private fun show() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(content, router, HomeCallbacks(games = games)) }
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) =
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun isPanelOpen() = compose.onAllNodesWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()


    /** Super Nintendo's games, first row focused. */
    private fun openSnes() = send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)

    private fun down(times: Int) = send(*List(times) { Action.Move(Direction.Down) }.toTypedArray())

    @Test
    fun gamepadOpensMovesPicksAndClosesTheMenu() {
        show()
        openSnes()
        down(2)
        send(Action.Menu)
        assertTrue("START opens the menu", isPanelOpen())
        assertTrue("titled with the game", hasText(content.entries[FakeHomeContent.BOARD_SNES_INDEX].rows[2].title))
        assertTrue("inside, the hints are A Select and B Back", hasText("Select") && hasText("Back"))
        down(5) // Info, after Hide and Name…
        send(Action.Enter)
        compose.onNodeWithTag(GamePanelTags.FACTS, useUnmergedTree = true).assertExists()
        assertTrue("Info shows the folder", hasText("ROMs/psx"))
        send(Action.Back)
        assertTrue("B goes back to the menu", isPanelOpen() && !hasText("ROMs/psx"))
        send(Action.Back)
        assertEquals("B again closes it", false, isPanelOpen())
        send(Action.Play)
        assertEquals("the focus is back on the same game", listOf("snes-2"), games.launched)
    }

    @Test
    fun touchOpensMovesPicksAndClosesTheMenu() {
        show()
        tap(HomeTags.system(FakeHomeContent.BOARD_SNES_INDEX))
        tap(HomeTags.system(FakeHomeContent.BOARD_SNES_INDEX))
        compose.onNodeWithTag(HomeTags.game(4), useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        assertTrue("a long press opens the menu", isPanelOpen())
        tap(GamePanelTags.row(GameMenuEntryId.Info.name))
        compose.onNodeWithTag(GamePanelTags.FACTS, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(HomeTags.hint(Action.Back), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTrue("the B Back hint goes back to the menu", isPanelOpen() && !hasText("ROMs/psx"))
        tap(GamePanelTags.OUTSIDE)
        assertEquals("a tap outside closes it", false, isPanelOpen())
        tap(HomeTags.game(4))
        assertEquals("the same game is still focused: one tap plays it", listOf("snes-4"), games.launched)
    }

    @Test
    fun theMenuHintsAreTappable() {
        show()
        openSnes()
        compose.onNodeWithTag(HomeTags.hint(Action.Menu), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTrue("the START Menu hint opens the menu", isPanelOpen())
        compose.onNodeWithTag(HomeTags.hint(Action.Enter), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("A Select on Play plays", listOf("snes-0"), games.launched)
        assertEquals("started: the menu closes", false, isPanelOpen())
    }

    @Test
    fun hideRemovesTheGameAndTheFocusMovesToTheNextOneOrThePreviousAtTheEnd() {
        games.onHide = { key ->
            val board = content
            content = HomeContent.of(
                recent = board.entries[0].rows,
                favorites = board.entries[1].rows,
                apps = board.entries[2].rows,
                systems = board.entries.drop(FIRST_SYSTEM_INDEX).map { SystemGames(it.id, it.name.orEmpty(), it.rows.filterNot { row -> row.id == key }) },
            )
        }
        show()
        openSnes()
        down(2)
        send(Action.Menu)
        down(3) // Hide
        send(Action.Enter)
        assertEquals(listOf("snes-2"), games.hidden)
        assertTrue("Hide says where the game went", isPanelOpen() && hasText("Game hidden") && hasText("Settings › Library › Hidden games", substring = true))
        send(Action.Enter)
        assertEquals("A on OK closes the message", false, isPanelOpen())
        send(Action.Play)
        assertEquals("the next game took its place", listOf("snes-3"), games.launched)
        val last = content.entries[FakeHomeContent.BOARD_SNES_INDEX].rows.last().id
        val beforeLast = content.entries[FakeHomeContent.BOARD_SNES_INDEX].rows.let { it[it.lastIndex - 1].id }
        send(*List(3) { Action.Move(Direction.Up) }.toTypedArray()) // up to the first row, then wraps to the last
        send(Action.Menu)
        down(3)
        send(Action.Enter)
        send(Action.Enter)
        send(Action.Play)
        assertEquals("at the end the previous one", listOf("snes-3", beforeLast), games.launched)
        assertEquals(listOf("snes-2", last), games.hidden)
    }

    private val closedAtOnce = MutableStateFlow<ClosedAtOnce?>(null)
    private val closedAtOnceShown = mutableListOf<Long>()

    private fun showWithClosedAtOnce() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            val notices = HomeNotices(closedAtOnce = closedAtOnce, onClosedAtOnceShown = { closedAtOnceShown += it.serial })
            PlumTheme { HomeScreen(content, router, HomeCallbacks(games = games, notices = notices)) }
        }
        compose.waitForIdle()
    }

    @Test
    fun anEmulatorThatClosedAtOnceIsNamedAndOkClosesTheMessage() {
        showWithClosedAtOnce()
        assertEquals("nothing to say yet", false, isPanelOpen())
        closedAtOnce.value = ClosedAtOnce(1, "snes-0", "Super Mario World", "Snes9x")
        compose.waitForIdle()
        assertTrue("names the emulator", isPanelOpen() && hasText("Snes9x closed right away"))
        assertTrue("points to the game menu's Play with…", hasText("Play with…", substring = true))
        assertEquals("told once, then forgotten", listOf(1L), closedAtOnceShown)
        tap(GamePanelTags.row("ok"))
        assertEquals("a tap on OK closes it", false, isPanelOpen())
    }

    @Test
    fun theClosedAtOnceMessageWaitsForAMenuThePersonOpenedFirst() {
        showWithClosedAtOnce()
        openSnes()
        send(Action.Menu)
        closedAtOnce.value = ClosedAtOnce(2, "snes-0", "Super Mario World", "Snes9x")
        compose.waitForIdle()
        assertTrue("the menu stays", isPanelOpen() && !hasText("Snes9x closed right away"))
        assertEquals(emptyList<Long>(), closedAtOnceShown)
        send(Action.Back)
        assertTrue("then it is said", hasText("Snes9x closed right away"))
        assertEquals(listOf(2L), closedAtOnceShown)
    }

    private val coreQuestion = MutableStateFlow<CoreQuestion?>(null)
    private val coreQuestionsShown = mutableListOf<Long>()
    private val mesen = CoreToConfirm("com.retroarch", "mesen_libretro_android.so")
    private val added = AddedSession(sessionId = 9, gameId = 0, endedAt = 62_000, seconds = 62, previousLastPlayedAt = null)

    private fun showWithCoreQuestion() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            val notices = HomeNotices(coreQuestion = coreQuestion, onCoreQuestionShown = { coreQuestionsShown += it.serial })
            PlumTheme { HomeScreen(content, router, HomeCallbacks(games = games, notices = notices)) }
        }
        compose.waitForIdle()
        coreQuestion.value = CoreQuestion(1, "snes-0", "Super Mario World", Beetle.id, "RetroArch: Mesen", mesen, added)
        compose.waitForIdle()
    }

    @Test
    fun theCoreQuestionNamesGameAndEmulatorAndAFirstAIsYes() {
        showWithCoreQuestion()
        assertTrue(isPanelOpen() && hasText("Did Super Mario World start?"))
        assertTrue("names the emulator and its core", hasText("RetroArch: Mesen", substring = true))
        assertEquals("asked once, then forgotten", listOf(1L), coreQuestionsShown)
        send(Action.Enter)
        assertEquals("Yes is focused first", listOf(mesen), games.confirmedCores)
        assertEquals("the play stays", emptyList<AddedSession>(), games.takenBack)
        assertEquals(false, isPanelOpen())
    }

    @Test
    fun aTapOnNoTakesThePlayBackAndOffersTheOtherEmulatorsForTheSystem() {
        showWithCoreQuestion()
        tap(GamePanelTags.row(CORE_NO_KEY))
        assertEquals(listOf(added), games.takenBack)
        assertEquals(emptyList<CoreToConfirm>(), games.confirmedCores)
        assertTrue("says the core may be missing", hasText("may not be installed in RetroArch", substring = true))
        assertTrue("the emulator that failed is not offered", compose.onAllNodesWithTag(GamePanelTags.row(Beetle.id), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        tap(GamePanelTags.row("duckstation"))
        assertEquals("kept for the system, as the launch's choice was", listOf("snes-0" to "duckstation"), games.systemChoices)
        assertEquals("then the game plays", listOf("snes-0"), games.launched)
    }

    @Test
    fun bAnswersNothingSoTheNextLaunchAsksAgain() {
        showWithCoreQuestion()
        send(Action.Back)
        assertEquals(false, isPanelOpen())
        assertEquals(emptyList<CoreToConfirm>(), games.confirmedCores)
        assertEquals(emptyList<AddedSession>(), games.takenBack)
    }

    @Test
    fun aYesThatCouldNotBeKeptSaysSo() {
        games.writeError = IllegalStateException("disk")
        showWithCoreQuestion()
        send(Action.Enter)
        assertTrue("the save message, never silence", isPanelOpen() && !hasText("Did Super Mario World start?"))
    }

    @Test
    fun aSecondPlayWhileTheFirstRunsIsIgnored() {
        games.launchGate = CompletableDeferred()
        show()
        openSnes()
        send(Action.Play, Action.Play)
        tap(HomeTags.game(0))
        assertEquals(listOf("snes-0"), games.launched)
        compose.runOnIdle { games.launchGate!!.complete(Unit) }
        compose.waitForIdle()
        send(Action.Play)
        assertEquals(listOf("snes-0", "snes-0"), games.launched)
    }

    @Test
    fun aMissingFileSaysWhichAndRescansByTap() {
        games.outcome = { LaunchOutcome.FileMissing(1, "Super Metroid (USA).sfc", FileProblem.Gone) }
        show()
        openSnes()
        send(Action.Play)
        assertTrue("the message names the file", hasText("This game's file is missing") && hasText("Super Metroid (USA).sfc", substring = true))
        tap(GamePanelTags.row("rescan"))
        assertEquals(1, games.rescans)
        assertEquals(false, isPanelOpen())
    }

    @Test
    fun aTapOnTheCurrentBadgeChoosesThatEmulator() {
        games.listing = { sampleListing(current = Beetle.id) }
        show()
        openSnes()
        send(Action.Menu)
        tap(GamePanelTags.row(GameMenuEntryId.PlayWith.name))
        compose.onNodeWithText("Current", useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf(Beetle.id), games.choices.map { it.second })
    }

    private fun hasText(text: String) = hasText(text, substring = false)

    private fun hasText(text: String, substring: Boolean) =
        compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aRescanUnderTheMenuKeepsItOnTheSameGameOrClosesItWhenTheGameLeft() {
        show()
        openSnes()
        down(2)
        send(Action.Menu)
        val snes = content.entries[FakeHomeContent.BOARD_SNES_INDEX]
        // A rescan removes the first game: "snes-2" moves up one row.
        content = withSnesRows(snes.rows.drop(1))
        compose.waitForIdle()
        assertTrue("the menu stays", isPanelOpen())
        send(Action.Back, Action.Play)
        assertEquals("the focus followed the game", listOf("snes-2"), games.launched)
        send(Action.Menu)
        content = withSnesRows(content.entries[FakeHomeContent.BOARD_SNES_INDEX].rows.filterNot { it.id == "snes-2" })
        compose.waitForIdle()
        assertEquals("the game is gone: the menu closes", false, isPanelOpen())
    }

    private fun withSnesRows(rows: List<GameRowModel>): HomeContent {
        val board = content
        return HomeContent.of(
            recent = board.entries[0].rows,
            favorites = board.entries[1].rows,
            apps = board.entries[2].rows,
            systems = board.entries.drop(FIRST_SYSTEM_INDEX).map { SystemGames(it.id, it.name.orEmpty(), if (it.id == "snes") rows else it.rows) },
        )
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun rowsOfAClosingPanelSendNothing() {
        content = FakeHomeContent.withArtwork()
        show()
        openSnes()
        send(Action.Move(Direction.Right)) // the narrow details stop
        send(Action.Menu)
        assertTrue(isPanelOpen())
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.send(Action.Back) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(FRAME_MS * 3)
        assertTrue("still sliding out", isPanelOpen())
        compose.onNodeWithTag(GamePanelTags.row(GameMenuEntryId.Hide.name), useUnmergedTree = true).performTouchInput { click() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("a tap on a leaving row does not play the details' game", emptyList<String>(), games.launched)
        assertEquals(emptyList<String>(), games.hidden)
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
