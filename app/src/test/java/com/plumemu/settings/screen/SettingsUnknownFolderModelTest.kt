package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.sound.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings › Library › unknown folders: one row each (unanswered first, answered under them), the side panel
 * (Ignore this folder, then the systems), A applies by the folder's id, B cancels, an answer can be changed,
 * a failed answer is said in a plain line.
 */
internal class SettingsUnknownFolderModelTest : SettingsModelHarness() {
    private val xbox = SettingsFolderRow(FolderKey(1, "Xbox 360"), 3, FolderAnswer.Unanswered)
    private val wiiU = SettingsFolderRow(FolderKey(1, "Wii U"), 5, FolderAnswer.Unanswered)
    private val halo = SettingsFolderRow(FolderKey(2, "Halo"), 0, FolderAnswer.OfSystem("x360", "Xbox 360"))
    private val skip = SettingsFolderRow(FolderKey(2, "Docs"), 0, FolderAnswer.Ignored)
    private val systems = listOf(FolderSystemChoice("gc", "GameCube"), FolderSystemChoice("x360", "Xbox 360"), FolderSystemChoice("wiiu", "Wii U"))

    private fun content(vararg rows: SettingsFolderRow) = SettingsContent(unknownFolders = rows.toList(), folderSystemChoices = systems)

    private fun focusFolder(key: FolderKey) {
        repeat(SettingsSection.Library.rowsIn(model.content).size) {
            if (model.content.unknownFolderAtRow(model.optionIndex)?.key != key || model.focusedOption != SettingsOption.UnknownFolder) move(Direction.Down)
        }
        assertEquals(key, model.content.unknownFolderAtRow(model.optionIndex)?.key)
    }

    private fun open(vararg rows: SettingsFolderRow, at: FolderKey = rows.first().key) {
        model.content = content(*rows)
        openSection(SettingsSection.Library)
        focusFolder(at)
        press(Action.Enter)
        assertEquals(PanelPage.UnknownFolder, model.focus.page)
    }

    @Test
    fun oneRowPerFolderBetweenTheLibraryRowsAndTheGameLists() {
        model.content = content(xbox, wiiU, halo).copy(unidentifiedGames = listOf(SettingsGame(1, "x", "y")))
        val rows = SettingsSection.Library.rowsIn(model.content)
        assertEquals(3, rows.count { it == SettingsOption.UnknownFolder })
        assertEquals(SettingsOption.UnidentifiedGames, rows.dropLast(2).last()) // the two Recent rows come after
        assertEquals(listOf(xbox, wiiU, halo), List(3) { model.content.unknownFolderAtRow(rows.indexOf(SettingsOption.UnknownFolder) + it)!! })
        assertNull("another option is no folder", model.content.unknownFolderAtRow(0))
        model.content = SettingsContent()
        assertTrue(SettingsSection.Library.rowsIn(model.content).none { it == SettingsOption.UnknownFolder })
    }

    @Test
    fun theFolderStartsOnIgnoreAndAStoresTheSystemPickedByThePanelsFolderId() {
        open(xbox, wiiU)
        assertEquals(xbox.key, model.focus.folderKey)
        assertEquals("Ignore this folder is item 0", 0, model.focus.panelIndex)
        move(Direction.Down)
        move(Direction.Down)
        val sounds = mutableListOf<Outcome>()
        model.feel.sink = { sounds += it }
        press(Action.Enter)
        assertEquals(listOf(Triple(1L, "Xbox 360", "x360")), actions.folderAnswers)
        assertEquals(listOf(Outcome.Activated), sounds)
        assertEquals("the panel closes on its row", SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun ignoreStoresNullAndBCancelsWithoutAnyWrite() {
        open(wiiU)
        press(Action.Back)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(emptyList<Triple<Long, String, String?>>(), actions.folderAnswers)
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(listOf(Triple(1L, "Wii U", null as String?)), actions.folderAnswers)
    }

    @Test
    fun moveStopsAtTheEndsOfTheList() {
        open(xbox)
        move(Direction.Up)
        assertEquals(0, model.focus.panelIndex)
        repeat(10) { move(Direction.Down) }
        assertEquals(systems.size, model.focus.panelIndex)
    }

    @Test
    fun anAnsweredFolderStaysListedBelowAndOpensOnItsCurrentAnswer() {
        open(xbox, halo, skip, at = halo.key)
        assertEquals("Xbox 360 is item 2 (Ignore, GameCube, Xbox 360)", 2, model.focus.panelIndex)
        press(Action.Back)
        focusFolder(skip.key)
        press(Action.Enter)
        assertEquals(0, model.focus.panelIndex)
    }

    @Test
    fun changingAnAnswerAsksForTheNewOneAndTheCursorFollowsTheFolderWhenItMoves() {
        open(xbox, wiiU, halo, at = wiiU.key)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(listOf(Triple(1L, "Wii U", "gc")), actions.folderAnswers)
        // The answer lands: Wii U leaves the unanswered rows and sits under them; the cursor stays on it.
        model.content = content(xbox, halo, wiiU.copy(files = 0, answer = FolderAnswer.OfSystem("gc", "GameCube")))
        assertEquals(wiiU.key, model.content.unknownFolderAtRow(model.optionIndex)?.key)
        press(Action.Enter)
        assertEquals("opens on the new answer", 1, model.focus.panelIndex)
    }

    @Test
    fun aSecondAWhileTheAnswerIsBeingStoredAsksNothingMore() {
        open(xbox)
        press(Action.Enter)
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(1, actions.folderAnswers.size)
        actions.answer(true)
        assertNull(model.work.notice)
        assertTrue(model.work.answering.isEmpty())
    }

    @Test
    fun aFailedAnswerIsSaidInAPlainLine() {
        open(xbox)
        press(Action.Enter)
        actions.answer(false)
        assertEquals(SettingsOption.UnknownFolder to SettingsNotice.FolderAnswerFailed, model.work.notice)
    }

    @Test
    fun theAnswerGoesToTheFolderThePanelOpenedForWhenItLeavesMeanwhile() {
        open(xbox, wiiU, at = wiiU.key)
        // The folder leaves the list (its picked folder was removed): the panel closes by itself, nothing is written,
        // and a later A cannot reach another folder through it.
        model.content = content(xbox)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
        assertEquals(emptyList<Triple<Long, String, String?>>(), actions.folderAnswers)
    }

    @Test
    fun theCursorStaysOnItsOptionWhenUnknownFoldersAppearAboveIt() {
        model.content = SettingsContent(unidentifiedGames = listOf(SettingsGame(1, "x", "y")))
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.UnidentifiedGames)
        model.content = SettingsContent(unidentifiedGames = listOf(SettingsGame(1, "x", "y")), unknownFolders = listOf(xbox, wiiU), folderSystemChoices = systems)
        assertEquals(SettingsOption.UnidentifiedGames, model.focusedOption)
    }

    @Test
    fun aTapOpensThePanelAndATapOnASystemApplies() {
        model.content = content(xbox)
        openSection(SettingsSection.Library)
        val row = SettingsSection.Library.rowsIn(model.content).indexOf(SettingsOption.UnknownFolder)
        model.onTapOption(row, actions.shown, wide)
        model.onTapOption(row, actions.shown, wide)
        assertEquals(PanelPage.UnknownFolder, model.focus.page)
        model.onTapPanelItem(2, actions.shown, wide)
        assertEquals("a first tap focuses", 2, model.focus.panelIndex)
        model.onTapPanelItem(2, actions.shown, wide)
        assertEquals(listOf(Triple(1L, "Xbox 360", "x360")), actions.folderAnswers)
    }
}
