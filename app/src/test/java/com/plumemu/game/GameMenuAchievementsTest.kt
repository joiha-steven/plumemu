package com.plumemu.game

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.graph.AchievementLine
import com.plumemu.graph.AchievementsState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Info's achievements through the menu controller: signed out nothing is asked and Info has no section;
 * signed in the state (Loading, then the answer) lands in the open Info; Back stops the read and Back
 * still works while the answer is slow.
 */
class GameMenuAchievementsTest {
    private val game = GameRef("7", "Super Metroid")

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun TestScope.openInfo(actions: FakeGameActions): GameMenuController {
        val menu = GameMenuController(actions, this) {}
        menu.openMenu(game)
        val index = menu.panel!!.content.focusableRows.indexOfFirst { it.key == GameMenuEntryId.Info.name }
        menu.onAction(Action.TapItem(Column.Details, index))
        return menu
    }

    private fun GameMenuController.achievements(): AchievementsState? = (panel!!.screen as PanelScreen.Info).facts.achievements

    private val ready =
        AchievementsState.Ready(
            "Super Metroid", 2, 1, 0,
            listOf(
                AchievementLine(1, "Morph Ball", "Find it", 5, null, null, "2026-10-30 14:22:10", null),
                AchievementLine(2, "Bombs", "Find them", 10, null, null, null, null),
            ),
        )

    @Test
    fun signedOutInfoAsksOnceAndHasNoSection() =
        runMenu {
            val actions = FakeGameActions()
            val menu = openInfo(actions)
            assertEquals("the app was asked whether there is anything to show", listOf("7"), actions.achievementsAsked)
            assertNull("no state, so no section", menu.achievements())
        }

    @Test
    fun signedInInfoShowsLoadingThenTheAnswer() =
        runMenu {
            val gate = CompletableDeferred<Unit>()
            val actions =
                FakeGameActions().apply {
                    achievementsFlow = {
                        flow {
                            emit(AchievementsState.Loading)
                            gate.await()
                            emit(ready)
                        }
                    }
                }
            val menu = openInfo(actions)
            assertEquals(AchievementsState.Loading, menu.achievements())
            gate.complete(Unit)
            assertEquals(ready, menu.achievements())
        }

    @Test
    fun backWorksWhileTheAnswerIsStillComingAndStopsTheRead() =
        runMenu {
            val source = MutableSharedFlow<AchievementsState>()
            val actions = FakeGameActions().apply { achievementsFlow = { source } }
            val menu = openInfo(actions)
            assertEquals("Info is open and the read is waiting", 1, source.subscriptionCount.value)
            menu.back()
            assertTrue("the panel went back to the menu", menu.panel!!.screen is PanelScreen.Menu)
            assertEquals("the read was cancelled with the panel", 0, source.subscriptionCount.value)
        }

    @Test
    fun theNameSourceStillArrivesNextToTheAchievements() =
        runMenu {
            val name = NameFacts(NameOrigin.GameCode, NameCertainty.Sure, null, hasFix = false, sourceInPack = true)
            val nameGate = CompletableDeferred<Unit>()
            val actions =
                FakeGameActions(facts = { sampleFacts(name = name) }).apply {
                    achievementsFlow = { flow { emit(ready) } }
                    nameSourceGate = nameGate
                }
            val menu = openInfo(actions)
            assertEquals(ready, menu.achievements())
            nameGate.complete(Unit)
            val facts = (menu.panel!!.screen as PanelScreen.Info).facts
            assertEquals("the achievements stay after the name's source arrives", ready, facts.achievements)
            assertTrue("and the source arrived", facts.name?.source != null)
        }
}
