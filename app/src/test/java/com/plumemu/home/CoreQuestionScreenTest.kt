package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coredata.db.AddedSession
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.Beetle
import com.plumemu.game.CORE_NO_KEY
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GamePanelTags
import com.plumemu.graph.LibraryNotices
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.launcher.play.PlaySessionOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Did <game> start?" on the whole home with the real [LibraryNotices]: each question's play is held until its panel closes, then sent once unless No took it
 * back; queued questions come one after the other, each over before the next shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class CoreQuestionScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private val notices = LibraryNotices()
    private lateinit var router: InputRouter
    private val content by mutableStateOf(FakeHomeContent.board())
    private val mesen = CoreToConfirm("com.retroarch", "mesen_libretro_android.so")

    /** What the graph would send as `game_session` ([com.plumemu.graph.coreQuestionOver]). */
    private val sent = mutableListOf<PlaySessionOutcome.Recorded>()
    private val overs = mutableListOf<Long>()

    private fun show() {
        // As AppGameActions.takeBackPlay: No drops the held event.
        games.onTakeBack = notices::dropHeldSession
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            val home =
                HomeNotices(
                    coreQuestion = notices.coreQuestion,
                    onCoreQuestionShown = notices::coreQuestionShown,
                    onCoreQuestionOver = { question ->
                        overs += question.serial
                        sent += notices.releaseHeldSession(question)
                    },
                )
            PlumTheme { HomeScreen(content, router, HomeCallbacks(games = games, notices = home)) }
        }
        compose.waitForIdle()
    }

    /** A return of [title] that recorded a play with an unconfirmed core, as `afterPlay` posts it. */
    private fun returnFrom(title: String, sessionId: Long) {
        val added = AddedSession(sessionId = sessionId, gameId = 0, endedAt = 62_000, seconds = 62, previousLastPlayedAt = null)
        notices.holdSession(added, PlaySessionOutcome.Recorded(0, 62, Beetle.id, mesen, added))
        // A game key per title: the same game and core collapse into one question.
        notices.askAboutCore("snes-$sessionId", title, Beetle.id, "RetroArch: Mesen", mesen, added)
    }

    /** Another try at the same game and core (same key): joins the question still waiting. */
    private fun tryAgain(title: String, sessionId: Long, gameSessionId: Long) {
        val added = AddedSession(sessionId = sessionId, gameId = 0, endedAt = 62_000, seconds = 62, previousLastPlayedAt = null)
        notices.holdSession(added, PlaySessionOutcome.Recorded(0, 62, Beetle.id, mesen, added))
        notices.askAboutCore("snes-$gameSessionId", title, Beetle.id, "RetroArch: Mesen", mesen, added)
    }

    private fun send(action: Action) {
        compose.runOnIdle { router.send(action) }
        compose.waitForIdle()
    }

    private fun hasText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun isPanelOpen() = compose.onAllNodesWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun yesSendsThePlayOnce() {
        returnFrom("Super Mario World", sessionId = 1)
        show()
        assertTrue(hasText("Did Super Mario World start?"))
        assertEquals("held while asked", 0, sent.size)
        send(Action.Enter)
        assertEquals(false, isPanelOpen())
        assertEquals(listOf(1L), sent.map { it.added?.sessionId })
        assertEquals(1, overs.size)
        assertEquals("Yes keeps the emulator choices", emptyList<Pair<String, String>>(), games.forgottenCores)
    }

    @Test
    fun threeTriesAtTheSameGameAreOneQuestionAndYesSendsAllThree() {
        returnFrom("Super Mario World", sessionId = 1)
        tryAgain("Super Mario World", sessionId = 2, gameSessionId = 1)
        tryAgain("Super Mario World", sessionId = 3, gameSessionId = 1)
        show()
        send(Action.Enter)
        assertEquals(false, isPanelOpen())
        assertEquals(listOf(1L, 2L, 3L), sent.map { it.added?.sessionId })
        assertEquals("one question, once", 1, overs.size)
    }

    @Test
    fun threeTriesAtTheSameGameAreOneQuestionAndNoTakesAllThreeBack() {
        returnFrom("Super Mario World", sessionId = 1)
        tryAgain("Super Mario World", sessionId = 2, gameSessionId = 1)
        tryAgain("Super Mario World", sessionId = 3, gameSessionId = 1)
        show()
        compose.onNodeWithTag(GamePanelTags.row(CORE_NO_KEY), useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf(1L, 2L, 3L), games.takenBack.map { it.sessionId })
        compose.onNodeWithTag(GamePanelTags.row("duckstation"), useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals("No took every play back: nothing sent", 0, sent.size)
        assertEquals(1, overs.size)
    }

    @Test
    fun noSendsNothingEvenAfterTheNextEmulatorIsPicked() {
        returnFrom("Super Mario World", sessionId = 1)
        show()
        compose.onNodeWithTag(GamePanelTags.row(CORE_NO_KEY), useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithTag(GamePanelTags.row("duckstation"), useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(false, isPanelOpen())
        assertEquals("the question is over", 1, overs.size)
        assertEquals("No took the play back: nothing sent", 0, sent.size)
        assertEquals("No forgets a choice of the failed core", listOf("snes-1" to Beetle.id), games.forgottenCores)
    }

    @Test
    fun twoQueuedQuestionsAreEachOverBeforeTheNextShows() {
        returnFrom("Super Mario World", sessionId = 1)
        returnFrom("Zork Quest", sessionId = 2)
        show()
        assertTrue(hasText("Did Super Mario World start?"))
        send(Action.Enter) // Yes to the first
        assertEquals("the first is over, its play sent", listOf(1L), sent.map { it.added?.sessionId })
        assertTrue("then the second shows", hasText("Did Zork Quest start?"))
        send(Action.Back) // B to the second
        assertEquals(false, isPanelOpen())
        assertEquals(listOf(1L, 2L), sent.map { it.added?.sessionId })
        assertEquals("each over once", 2, overs.distinct().size)
        assertEquals(2, overs.size)
    }
}
