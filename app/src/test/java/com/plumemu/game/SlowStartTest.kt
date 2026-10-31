package com.plumemu.game

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.launcher.FileProblem
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.play.HandOffWatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A start that takes long, or never hands over (a sleeping SD card left A dead with no word). */
internal class SlowStartTest {
    private val logs = mutableListOf<String>()
    private val game = GameRef("7", "Chrono Trigger")

    private fun TestScope.controller(actions: FakeGameActions) = GameMenuController(actions, this) { logs += it }

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.title() = panel?.content?.title

    @Test
    fun aSlowStartSaysStartingThenGivesUpWithAMessageAndAWorksAgain() =
        runMenu {
            val actions = FakeGameActions().apply { launchGate = CompletableDeferred() }
            val menu = controller(actions)
            menu.play(game)
            assertNull("a quick start shows nothing", menu.panel)
            advanceTimeBy(600)
            assertEquals(UiText.Res(R.string.game_starting), menu.title())
            assertTrue(menu.isLaunching)
            advanceTimeBy(20_000)
            assertEquals(UiText.Res(R.string.game_error_no_start_title), menu.title())
            assertEquals(listOf(UiText.Res(R.string.game_error_no_start_body)), menu.panel!!.content.body)
            assertFalse("A works again", menu.isLaunching)
            assertTrue(logs.any { it.contains("row 7 did not start within 20 s") })
            menu.onAction(Action.Enter) // OK
            assertNull(menu.panel)
            actions.launchGate = null
            assertTrue(menu.play(game))
            assertEquals(listOf("7", "7"), actions.launched)
            assertNull("the second start went through", menu.panel)
        }

    @Test
    fun aSecondAWhileStartingIsIgnoredButSaysStarting() =
        runMenu {
            val actions = FakeGameActions().apply { launchGate = CompletableDeferred() }
            val menu = controller(actions)
            menu.play(game)
            assertFalse(menu.play(game))
            assertEquals("one launch", listOf("7"), actions.launched)
            assertEquals("not silent: the notice shows at once", UiText.Res(R.string.game_starting), menu.title())
            menu.onAction(Action.Enter) // A on the notice: nothing to choose, still one launch
            assertEquals(listOf("7"), actions.launched)
            actions.launchGate!!.complete(Unit)
            assertNull("the start closes the notice", menu.panel)
            assertFalse(menu.isLaunching)
        }

    @Test
    fun playFromTheMenuThenAFailureReplacesTheNoticeAndTheMenu() =
        runMenu {
            val actions = FakeGameActions(outcome = { LaunchOutcome.FileMissing(7, "x.sfc", FileProblem.Gone) }).apply { launchGate = CompletableDeferred() }
            val menu = controller(actions)
            menu.openMenu(game)
            menu.onAction(Action.Enter) // Play
            advanceTimeBy(600)
            assertEquals(UiText.Res(R.string.game_starting), menu.title())
            actions.launchGate!!.complete(Unit)
            assertEquals(UiText.Res(R.string.game_error_file_gone_title), menu.title())
            assertNull(menu.panel!!.parent)
        }

    @Test
    fun backOnTheNoticeReturnsToTheMenuAndTheStartStillCloses() =
        runMenu {
            val actions = FakeGameActions().apply { launchGate = CompletableDeferred() }
            val menu = controller(actions)
            menu.openMenu(game)
            menu.onAction(Action.Enter) // Play
            val opened = menu.panel
            advanceTimeBy(600)
            assertEquals(UiText.Res(R.string.game_starting), menu.title())
            menu.onAction(Action.Back)
            assertTrue(menu.panel === opened)
            actions.launchGate!!.complete(Unit)
            assertNull(menu.panel)
        }

    @Test
    fun aSlowAppOpenSaysOpeningAndGivesUp() =
        runMenu {
            val apps = FakeAppActions().apply { gate = CompletableDeferred() }
            val menu = GameMenuController(FakeGameActions(), this, apps) { logs += it }
            val app = GameRef("41", "Files")
            menu.openApp(app)
            assertFalse(menu.openApp(app))
            assertEquals(UiText.Res(R.string.app_opening, listOf("Files")), menu.title())
            advanceTimeBy(20_100)
            assertEquals(UiText.Res(R.string.app_error_no_start_title, listOf("Files")), menu.title())
            assertFalse(menu.isLaunching)
        }

    @Test
    fun aStartAlreadyHandingOffAtTheLimitIsWaitedForNotReportedAsFailed() =
        runMenu {
            val actions =
                FakeGameActions().apply {
                    launchGate = CompletableDeferred()
                    beforeGate = { assertTrue(currentCoroutineContext()[HandOffWatch]!!.claim()) }
                }
            val menu = controller(actions)
            menu.play(game)
            advanceTimeBy(20_100)
            assertEquals("the emulator is being opened: still starting, no error", UiText.Res(R.string.game_starting), menu.title())
            assertTrue(menu.isLaunching)
            assertTrue(logs.any { it.contains("the hand-off began; waiting for it") })
            actions.launchGate!!.complete(Unit)
            assertNull("the real outcome (started) closes the notice", menu.panel)
            assertFalse(menu.isLaunching)
        }

    @Test
    fun aSecondAWhileTheNoEmulatorListIsReadSaysStarting() =
        runMenu {
            val actions =
                FakeGameActions(outcome = { LaunchOutcome.NoEmulatorForSystem(7, "psx", emptyList()) }).apply {
                    listing = { sampleListing(installed = emptyList(), automatic = null) }
                    readGate = CompletableDeferred()
                }
            val menu = controller(actions)
            menu.play(game)
            assertNull(menu.panel)
            assertFalse(menu.play(game))
            assertEquals("not silent while the list is read", UiText.Res(R.string.game_starting), menu.title())
            assertEquals(listOf("7"), actions.launched)
            actions.readGate!!.complete(Unit)
            assertTrue("the no-emulator message replaces the notice", menu.title() != UiText.Res(R.string.game_starting))
            assertTrue(menu.panel!!.content.items.filterIsInstance<PanelItem.Row>().any { it.row.key == "get:epsxe" })
            assertFalse(menu.isLaunching)
        }
}
