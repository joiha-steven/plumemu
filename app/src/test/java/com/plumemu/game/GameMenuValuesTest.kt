package com.plumemu.game

import com.plumemu.R
import com.plumemu.coredata.db.AddedSession
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.graph.CoreQuestion
import com.plumemu.launcher.choice.CoreToConfirm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The game menu shows a value after a row only when it is real (the emulator chosen, the sort in use), and a chevron
 * on the rows that open something; the core question's No and Yes are side by side and Left and Right move between them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameMenuValuesTest {
    private val game = GameRef("snes-0", "Super Metroid")

    private fun row(facts: GameFacts, id: GameMenuEntryId) = gameMenuEntries(facts).first { it.key == id.name }

    @Test
    fun playWithShowsTheEmulatorThatWouldStart() {
        val facts = sampleFacts().copy(emulator = EmulatorToStart("DuckStation", isInstalled = true))
        assertEquals(UiText.Plain("DuckStation"), row(facts, GameMenuEntryId.PlayWith).value)
    }

    @Test
    fun playWithShowsNothingWhileNoEmulatorIsChosenOrTheChosenOneIsGone() {
        val notChosen = sampleFacts().copy(emulator = EmulatorToStart("", isInstalled = false, isNotChosenYet = true))
        assertNull("nothing chosen for the system", row(notChosen, GameMenuEntryId.PlayWith).value)
        val gone = sampleFacts().copy(emulator = EmulatorToStart("Old Emu", isInstalled = false))
        assertNull("uninstalled: not a value to show", row(gone, GameMenuEntryId.PlayWith).value)
        assertNull("no emulator at all", row(sampleFacts().copy(emulator = null), GameMenuEntryId.PlayWith).value)
    }

    @Test
    fun sortByShowsTheSortInUseAndNothingWhenItIsUnknown() {
        val facts = sampleFacts(canSort = true)
        assertEquals(UiText.Res(R.string.settings_sort_by_last_played), row(facts.copy(sort = SortGamesSetting.LastPlayed), GameMenuEntryId.SortBy).value)
        assertNull(row(facts, GameMenuEntryId.SortBy).value)
    }

    @Test
    fun otherRowsHaveNoMadeUpValue() {
        val rows = gameMenuEntries(sampleFacts(canFixBoxArt = true, canRemoveFromRecent = true, canSort = true, isFavorite = true))
        val withValue = rows.filter { it.value != null }.map { it.key }
        assertEquals("only Play with… (an emulator is set) has one here", listOf(GameMenuEntryId.PlayWith.name), withValue)
        assertTrue("no Achievements row: they stay in Info", rows.none { it.key.contains("Achievements", ignoreCase = true) })
    }

    @Test
    fun chevronsMarkTheRowsThatOpenSomething() {
        val rows = gameMenuEntries(sampleFacts(canFixBoxArt = true, canSort = true, canRemoveFromRecent = true, versions = sampleVersions()))
        val opening = rows.filter { it.command.opensSomething }.map { it.key }.toSet()
        assertEquals(
            setOf(GameMenuEntryId.PlayWith, GameMenuEntryId.OtherVersions, GameMenuEntryId.SortBy, GameMenuEntryId.Name, GameMenuEntryId.FixBoxArt, GameMenuEntryId.Info).map { it.name }.toSet(),
            opening,
        )
        assertFalse(PanelCommand.Play.opensSomething)
        assertFalse(PanelCommand.Hide.opensSomething)
        assertFalse(PanelCommand.SetFavorite(true).opensSomething)
    }

    private class FakeSorts(private val current: SortGamesSetting?) : SortActions {
        override suspend fun choices(): SortChoices = SortChoices(current ?: error("unreadable"), hasYear = false, hasRating = false)

        override suspend fun set(sort: SortGamesSetting) = Unit
    }

    private fun TestScope.controller(sorts: SortActions) = GameMenuController(FakeGameActions(), this, AppActions.None, sorts) {}

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.sortValue() = (panel!!.screen as PanelScreen.Menu).facts.sort

    @Test
    fun theMenuReadsTheSortOnlyFromAListThatFollowsIt() =
        run {
            val inList = controller(FakeSorts(SortGamesSetting.MostPlayed))
            inList.openMenu(game, canSort = true)
            assertEquals(SortGamesSetting.MostPlayed, inList.sortValue())
            val elsewhere = controller(FakeSorts(SortGamesSetting.MostPlayed))
            elsewhere.openMenu(game, canSort = false)
            assertNull("no Sort by… row, nothing read", elsewhere.sortValue())
            val unreadable = controller(FakeSorts(null))
            unreadable.openMenu(game, canSort = true)
            assertNull("a failed read shows no value, and the menu still opens", unreadable.sortValue())
            assertTrue(unreadable.isOpen)
        }

    private val mesen = CoreToConfirm("com.retroarch", "mesen_libretro_android.so")
    private val added = AddedSession(sessionId = 9, gameId = 0, endedAt = 62_000, seconds = 62, previousLastPlayedAt = null)
    private val question = CoreQuestion(1, "snes-0", "Super Metroid", Beetle.id, "RetroArch: Mesen", mesen, added)
    private val actions = FakeGameActions()

    private fun TestScope.asking(): GameMenuController =
        GameMenuController(actions, this, AppActions.None, SortActions.None) {}.also { it.showMessage(game, coreQuestionMessage(question)) }

    private fun GameMenuController.focused() = panel?.content?.focusableRows?.getOrNull(panel!!.focus)?.key

    private fun GameMenuController.left() = onAction(Action.Move(Direction.Left))

    private fun GameMenuController.right() = onAction(Action.Move(Direction.Right))

    @Test
    fun theQuestionIsAButtonRowWithYesFocusedFirst() =
        run {
            val menu = asking()
            assertTrue(menu.panel!!.content.isButtonRow)
            assertEquals(CORE_YES_KEY, menu.focused())
        }

    @Test
    fun leftGoesToNoAndRightBackToYesAndTheEndsStayPut() =
        run {
            val menu = asking()
            menu.right()
            assertEquals("Yes is the rightmost: Right stays", CORE_YES_KEY, menu.focused())
            menu.left()
            assertEquals(CORE_NO_KEY, menu.focused())
            menu.left()
            assertEquals("No is the leftmost: Left stays and does not leave the question", CORE_NO_KEY, menu.focused())
            assertTrue(menu.isOpen)
            menu.right()
            assertEquals(CORE_YES_KEY, menu.focused())
        }

    @Test
    fun aOnYesAnswersYesAndNothingIsTakenBack() =
        run {
            val menu = asking()
            menu.onAction(Action.Enter)
            assertEquals(emptyList<AddedSession>(), actions.takenBack)
            assertFalse("Yes closes the question", menu.isOpen)
        }

    @Test
    fun aOnNoTakesTheTryBackAndOffersWhatIsNext() =
        run {
            val menu = asking()
            menu.left()
            menu.onAction(Action.Enter)
            assertEquals(listOf(added), actions.takenBack)
        }

    @Test
    fun bClosesWithoutAnswering() =
        run {
            val menu = asking()
            menu.left()
            menu.onAction(Action.Back)
            assertFalse(menu.isOpen)
            assertEquals(emptyList<AddedSession>(), actions.takenBack)
            assertEquals(emptyList<Pair<String, String>>(), actions.forgottenCores)
        }

    @Test
    fun otherPanelsKeepLeftAsBack() =
        run {
            val menu = controller(SortActions.None)
            menu.openMenu(game)
            menu.left()
            assertFalse("Left still goes back out of a list", menu.isOpen)
        }
}
