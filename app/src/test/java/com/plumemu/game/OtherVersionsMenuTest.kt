package com.plumemu.game

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.library.naming.GameCopies
import com.plumemu.library.naming.GameCopy
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
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
 * The game menu's Other versions:
 * the entry only for a row of two or more copies, the labels, the current copy, choosing with a
 * gamepad and by touch, Back, and an arcade set's versions that arrive after the menu opened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OtherVersionsMenuTest {
    private val game = GameRef("11", "Zork Quest")
    private val logs = mutableListOf<String>()

    private fun TestScope.controller(actions: FakeGameActions) = GameMenuController(actions, this) { logs += it }

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.focusedKey() = panel?.content?.focusableRows?.getOrNull(panel!!.focus)?.key

    private fun GameMenuController.down(times: Int = 1) = repeat(times) { onAction(Action.Move(Direction.Down)) }

    private fun keys(facts: GameFacts) = gameMenuEntries(facts).map { it.key }

    private fun copies(vararg copies: GameCopy, played: Long = copies.first().id) = GameCopies(copies.first().id, copies.toList(), played)

    @Test
    fun theEntryIsListedOnlyForTwoOrMoreCopiesAndNeverForAShortcut() {
        val single = VersionListing(listOf(VersionItem("11", UiText.Plain("USA"), isCurrent = true)))
        assertFalse(GameMenuEntryId.OtherVersions.name in keys(sampleFacts()))
        assertFalse("one copy: nothing to choose", GameMenuEntryId.OtherVersions.name in keys(sampleFacts(versions = single)))
        assertEquals(
            listOf("Play", "PlayWith", "OtherVersions", "Favorite", "Hide", "Name", "Info"),
            keys(sampleFacts(versions = sampleVersions())),
        )
        assertTrue("arcade sets too", GameMenuEntryId.OtherVersions.name in keys(sampleFacts(isArcade = true, versions = sampleArcadeVersions())))
        assertFalse(GameMenuEntryId.OtherVersions.name in keys(sampleFacts(isShortcut = true, versions = sampleVersions())))
        val entry = gameMenuEntries(sampleFacts(versions = sampleVersions(currentIndex = 1))).single { it.key == "OtherVersions" }
        assertEquals("the value is the version A plays now", UiText.Plain("Europe"), entry.value)
        assertEquals(UiText.Res(R.string.game_menu_other_versions), entry.label)
    }

    @Test
    fun labelsAreTheArcadeDetailsOrTheRegionNamesAndADuplicateCarriesItsFileName() {
        val arcade = versionListingOf(
            copies(
                GameCopy(21, "dino.zip", emptyList(), listOf("World 930201")),
                GameCopy(22, "dinou.zip", emptyList(), listOf("USA 930201")),
                played = 22,
            ),
        )
        assertEquals(listOf(UiText.Plain("World 930201"), UiText.Plain("USA 930201")), arcade.versions.map { it.label })
        assertEquals("22", arcade.current?.key)

        val console = versionListingOf(
            copies(
                GameCopy(11, "Zork Quest (USA).sfc", listOf("USA"), emptyList()),
                GameCopy(12, "Zork Quest (Europe).sfc", listOf("EUR"), emptyList()),
                GameCopy(13, "Zork Quest (Japan, USA).sfc", listOf("JPN", "USA"), emptyList()),
                GameCopy(14, "Zork Quest.sfc", emptyList(), emptyList()),
            ),
        )
        assertEquals(
            listOf(UiText.Plain("USA"), UiText.Plain("Europe"), UiText.Plain("Japan, USA"), UiText.Plain("Zork Quest.sfc")),
            console.versions.map { it.label },
        )
        assertEquals("the row's own file is current without a choice", "11", console.current?.key)

        val twins = versionListingOf(
            copies(
                GameCopy(11, "Zork Quest (USA).sfc", listOf("USA"), emptyList()),
                GameCopy(15, "Zork Quest (USA).zip", listOf("USA"), emptyList()),
            ),
        )
        assertEquals(
            listOf(
                UiText.Format(R.string.game_version_with_file, listOf(UiText.Plain("USA"), UiText.Plain("Zork Quest (USA).sfc"))),
                UiText.Format(R.string.game_version_with_file, listOf(UiText.Plain("USA"), UiText.Plain("Zork Quest (USA).zip"))),
            ),
            twins.versions.map { it.label },
        )
    }

    @Test
    fun thePanelMarksTheCurrentCopyAndOpensOnIt() {
        val content = versionsContent(sampleVersions(currentIndex = 1))
        assertEquals(UiText.Res(R.string.game_versions_title), content.title)
        assertEquals(listOf("version:11", "version:12"), content.focusableRows.map { it.key })
        assertEquals(listOf(false, true), content.focusableRows.map { it.isCurrent })
        val menu = OpenPanel(game, PanelScreen.Menu(sampleFacts(versions = sampleVersions(currentIndex = 1))))
        assertEquals(1, versionsPanel(game, sampleVersions(currentIndex = 1), menu).focus)
    }

    @Test
    fun withAGamepadOtherVersionsDownAPlaysThatCopyAndBReturnsToTheMenu() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(versions = sampleVersions()) })
            val menu = controller(actions)
            menu.openMenu(game)
            menu.down(2)
            assertEquals("OtherVersions", menu.focusedKey())
            menu.onAction(Action.Enter)
            assertTrue(menu.panel!!.screen is PanelScreen.Versions)
            assertEquals("focus starts on the current copy", "version:11", menu.focusedKey())
            menu.onAction(Action.Back)
            assertEquals("B returns to the menu on its row", "OtherVersions", menu.focusedKey())
            menu.onAction(Action.Enter)
            menu.down()
            menu.onAction(Action.Enter)
            assertEquals(listOf("11" to "12"), actions.chosenVersions)
            assertEquals("A plays the row; the launch opens the chosen copy", listOf("11"), actions.launched)
            assertNull("the game started: the panel closed", menu.panel)

            // The next menu shows the chosen copy as current (the library answers it from now on).
            actions.facts = { sampleFacts(versions = sampleVersions(currentIndex = 1)) }
            menu.openMenu(game)
            val entry = menu.panel!!.content.focusableRows.single { it.key == "OtherVersions" }
            assertEquals(UiText.Plain("Europe"), entry.value)
        }

    @Test
    fun aTapOnACopyPlaysIt() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(versions = sampleVersions()) })
            val menu = controller(actions)
            menu.openMenu(game)
            menu.onAction(Action.TapItem(Column.Details, 2))
            assertTrue(menu.panel!!.screen is PanelScreen.Versions)
            menu.onAction(Action.TapItem(Column.Details, 1))
            assertEquals(listOf("11" to "12"), actions.chosenVersions)
            assertEquals(listOf("11"), actions.launched)
        }

    @Test
    fun aFailedSavePlaysNothingAndSaysSo() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(versions = sampleVersions()) })
            val menu = controller(actions)
            menu.openMenu(game)
            menu.onAction(Action.TapItem(Column.Details, 2))
            actions.writeError = IOException("disk")
            menu.onAction(Action.TapItem(Column.Details, 1))
            assertEquals(UiText.Res(R.string.game_error_save_title), menu.panel!!.content.title)
            assertTrue(actions.launched.isEmpty())
            assertTrue(logs.any { it == "version: game 11 could not be saved (IOException)" })
        }

    /** A rescan removed or renamed the chosen copy's file after the list was read. */
    @Test
    fun aVersionGoneSinceTheListWasReadSaysSoAndTheListIsReadAgain() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(versions = sampleVersions()) })
            val menu = controller(actions)
            menu.openMenu(game)
            menu.onAction(Action.TapItem(Column.Details, 2))
            actions.chooseVersionAnswer = false
            val after = VersionListing(listOf(VersionItem("11", UiText.Plain("USA"), isCurrent = true), VersionItem("13", UiText.Plain("Japan"), isCurrent = false)))
            actions.versionsAnswer = { after }
            menu.onAction(Action.TapItem(Column.Details, 1))

            assertTrue("a gone copy is not played", actions.launched.isEmpty())
            val message = menu.panel!!.content
            assertEquals(UiText.Res(R.string.game_version_gone_title), message.title)
            assertEquals(listOf(UiText.Res(R.string.game_version_gone_body)), message.body)
            assertEquals(listOf(VERSION_GONE_OK_KEY), message.focusableRows.map { it.key })
            assertTrue(logs.any { it == "version: game 11: the chosen copy is no copy of it any more" })

            menu.onAction(Action.Enter) // OK
            assertTrue("OK returns to the list, read again", menu.panel!!.screen is PanelScreen.Versions)
            assertEquals(listOf("version:11", "version:13"), menu.panel!!.content.focusableRows.map { it.key })
            assertEquals("version:11", menu.focusedKey())
            menu.onAction(Action.Back)
            assertEquals("the menu follows the new list", "OtherVersions", menu.focusedKey())
            assertEquals(UiText.Plain("USA"), menu.panel!!.content.focusableRows.single { it.key == "OtherVersions" }.value)
            assertTrue(menu.panel!!.screen is PanelScreen.Menu)
        }

    @Test
    fun anArcadeSetsVersionsArriveAfterTheMenuOpensAndKeepTheFocusedRow() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(isArcade = true, versionsPending = true) })
            actions.versionsAnswer = { sampleArcadeVersions() }
            actions.versionsGate = CompletableDeferred()
            val menu = controller(actions)
            menu.openMenu(game)
            assertTrue("the menu opens without waiting for the pack", menu.panel!!.screen is PanelScreen.Menu)
            assertFalse("no entry until the pack answers", menu.panel!!.content.focusableRows.any { it.key == "OtherVersions" })
            assertEquals(1, actions.versionReads)
            menu.down(2)
            assertEquals("Favorite", menu.focusedKey())
            actions.versionsGate!!.complete(Unit)
            assertEquals("the entry is added, the focus stays on Favorite", "Favorite", menu.focusedKey())
            assertEquals(listOf("Play", "PlayWith", "OtherVersions", "Favorite", "Hide", "Name", "Info"), menu.panel!!.content.focusableRows.map { it.key })
            assertEquals(UiText.Plain("World 930201"), menu.panel!!.content.focusableRows.single { it.key == "OtherVersions" }.value)
        }

    @Test
    fun aConsoleGamesVersionsAreInTheFactsAndNothingMoreIsRead() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(versions = sampleVersions()) })
            val menu = controller(actions)
            menu.openMenu(game)
            assertTrue(menu.panel!!.content.focusableRows.any { it.key == "OtherVersions" })
            assertEquals("the menu opens on the facts alone", 0, actions.versionReads)
        }

    @Test
    fun anArcadeSetWithoutOtherSetsGetsNoEntry() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(isArcade = true, versionsPending = true) })
            actions.versionsAnswer = { VersionListing(listOf(VersionItem("21", UiText.Plain("World"), isCurrent = true))) }
            val menu = controller(actions)
            menu.openMenu(game)
            assertFalse(menu.panel!!.content.focusableRows.any { it.key == "OtherVersions" })
        }

    @Test
    fun versionsThatArriveWhileAnotherPanelIsOpenAreThereWhenBackReturnsToTheMenu() =
        runMenu {
            val gate = CompletableDeferred<Unit>()
            val actions = FakeGameActions(facts = { sampleFacts(isArcade = true, versionsPending = true) })
            actions.versionsAnswer = { sampleArcadeVersions() }
            actions.versionsGate = gate
            val menu = controller(actions)
            menu.openMenu(game)
            menu.onAction(Action.TapItem(Column.Details, 1)) // Play with
            assertTrue(menu.panel!!.screen is PanelScreen.PlayWith)
            gate.complete(Unit)
            assertTrue("Play with stays", menu.panel!!.screen is PanelScreen.PlayWith)
            menu.onAction(Action.Back)
            assertEquals("PlayWith", menu.focusedKey())
            assertTrue(menu.panel!!.content.focusableRows.any { it.key == "OtherVersions" })
        }

    /**
     * game A's lookup answering late, after B closed A's menu and B's menu opened,
     * must not land on B's menu, nor push out B's own answer that arrived while Play with was open.
     */
    @Test
    fun aLateAnswerForTheMenuBackedOutOfNeverReachesTheNextGamesMenu() =
        runMenu {
            val gameB = GameRef("31", "Zork Quest II")
            val ofB = VersionListing(listOf(VersionItem("31", UiText.Plain("Japan"), isCurrent = true), VersionItem("32", UiText.Plain("Asia"), isCurrent = false)))
            val actions = FakeGameActions(facts = { sampleFacts(isArcade = true, versionsPending = true) })
            actions.versionsAnswer = { key -> if (key == game.key) sampleArcadeVersions() else ofB }
            val menu = controller(actions)
            val lateA = CompletableDeferred<Unit>()
            actions.versionsGate = lateA
            menu.openMenu(game)
            menu.onAction(Action.Back)
            assertNull("B closed A's menu", menu.panel)

            val answerB = CompletableDeferred<Unit>()
            actions.versionsGate = answerB
            menu.openMenu(gameB)
            menu.onAction(Action.TapItem(Column.Details, 1)) // Play with
            answerB.complete(Unit)
            lateA.complete(Unit)
            menu.onAction(Action.Back)
            val entry = menu.panel!!.content.focusableRows.single { it.key == "OtherVersions" }
            assertEquals("B's menu shows B's versions", UiText.Plain("Japan"), entry.value)
            assertEquals(gameB, menu.panel!!.game)
            menu.onAction(Action.TapItem(Column.Details, 2))
            assertEquals(listOf("version:31", "version:32"), menu.panel!!.content.focusableRows.map { it.key })
        }
}
