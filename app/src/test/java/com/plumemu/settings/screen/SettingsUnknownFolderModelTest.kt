package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.sound.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings › Library › Files not added › unknown folders: one line each (unanswered first, answered under them), no row of
 * their own, the folder's page (Ignore this folder, then the systems), A applies by the folder's id, B goes back to the
 * line, an answer can be changed, a failed answer is said in a plain line.
 */
internal class SettingsUnknownFolderModelTest : SettingsModelHarness() {
    private val xbox = SettingsFolderRow(FolderKey(1, "Xbox 360"), 3, FolderAnswer.Unanswered)
    private val wiiU = SettingsFolderRow(FolderKey(1, "Wii U"), 5, FolderAnswer.Unanswered)
    private val halo = SettingsFolderRow(FolderKey(2, "Halo"), 0, FolderAnswer.OfSystem("x360", "Xbox 360"))
    private val skip = SettingsFolderRow(FolderKey(2, "Docs"), 0, FolderAnswer.Ignored)
    private val systems = listOf(FolderSystemChoice("gc", "GameCube"), FolderSystemChoice("x360", "Xbox 360"), FolderSystemChoice("wiiu", "Wii U"))

    private fun content(vararg rows: SettingsFolderRow) =
        SettingsContent(unknownFolders = rows.toList(), folderSystemChoices = systems, notAdded = notAddedItems(emptyList(), rows.toList()))

    private fun lineOf(key: FolderKey) = model.content.notAdded.indexOfFirst { it is NotAddedItem.Folder && it.folder == key }

    /** On Files not added, the cursor on [key]'s line. */
    private fun toLine(key: FolderKey) {
        while (model.focus.panelIndex < lineOf(key)) move(Direction.Down)
        while (model.focus.panelIndex > lineOf(key)) move(Direction.Up)
        assertEquals(lineOf(key), model.focus.panelIndex)
    }

    private fun openList(vararg rows: SettingsFolderRow) {
        model.content = content(*rows)
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.FilesNotAdded)
        press(Action.Enter)
        assertEquals(PanelPage.FilesNotAdded, model.focus.page)
    }

    private fun open(vararg rows: SettingsFolderRow, at: FolderKey = rows.first().key) {
        openList(*rows)
        toLine(at)
        press(Action.Enter)
        assertEquals(PanelPage.UnknownFolder, model.focus.page)
        assertEquals(SettingsOption.FilesNotAdded, model.focusedOption)
    }

    private fun assertOnLine(key: FolderKey) {
        assertEquals(PanelPage.FilesNotAdded, model.focus.page)
        assertEquals(SettingsColumn.Panel, model.focus.column)
        assertEquals(lineOf(key), model.focus.panelIndex)
        assertNull(model.focus.folderKey)
    }

    @Test
    fun aFolderHasNoRowInLibraryAndEveryFolderHasALineInFilesNotAdded() {
        model.content = content(xbox, wiiU, halo).copy(unidentifiedGames = listOf(SettingsGame(1, "x", "y")))
        assertEquals(
            listOf(
                SettingsOption.RomFolders,
                SettingsOption.Rescan,
                SettingsOption.SortGames,
                SettingsOption.FilesNotAdded,
                SettingsOption.UnidentifiedGames,
                SettingsOption.ClearRecent,
            ),
            SettingsSection.Library.rowsIn(model.content),
        )
        assertEquals(listOf(xbox.key, wiiU.key, halo.key), model.content.notAdded.filterIsInstance<NotAddedItem.Folder>().map { it.folder })
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
        assertOnLine(xbox.key)
    }

    @Test
    fun ignoreStoresNullAndBCancelsWithoutAnyWrite() {
        open(wiiU)
        press(Action.Back)
        assertOnLine(wiiU.key)
        assertEquals(emptyList<Triple<Long, String, String?>>(), actions.folderAnswers)
        press(Action.Enter)
        assertEquals(PanelPage.UnknownFolder, model.focus.page)
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
        assertOnLine(halo.key)
        toLine(skip.key)
        press(Action.Enter)
        assertEquals(skip.key, model.focus.folderKey)
        assertEquals(0, model.focus.panelIndex)
    }

    @Test
    fun changingAnAnswerAsksForTheNewOneAndTheCursorFollowsTheFolderWhenItMoves() {
        open(xbox, wiiU, halo, at = wiiU.key)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(listOf(Triple(1L, "Wii U", "gc")), actions.folderAnswers)
        assertOnLine(wiiU.key)
        // The answer lands: Wii U leaves the unanswered lines and sits under them; the cursor stays on it.
        model.content = content(xbox, halo, wiiU.copy(files = 0, answer = FolderAnswer.OfSystem("gc", "GameCube")))
        assertOnLine(wiiU.key)
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
        assertEquals(SettingsOption.FilesNotAdded to SettingsNotice.FolderAnswerFailed, model.work.notice)
    }

    @Test
    fun theAnswerGoesToTheFolderThePanelOpenedForWhenItLeavesMeanwhile() {
        open(xbox, wiiU, at = wiiU.key)
        // The folder leaves the list (its picked folder was removed): the page goes back to the list by itself, nothing
        // is written, and a later A cannot reach another folder through it.
        model.content = content(xbox)
        assertEquals(PanelPage.FilesNotAdded, model.focus.page)
        assertEquals(SettingsColumn.Panel, model.focus.column)
        assertEquals(0, model.focus.panelIndex)
        assertNull(model.focus.folderKey)
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
        openList(xbox)
        val line = lineOf(xbox.key)
        assertEquals("the list opens on its first real line", line, model.focus.panelIndex)
        assertEquals("the cursor is on it, so one tap opens it", PanelPage.FilesNotAdded, model.focus.page)
        model.onTapPanelItem(line, actions.shown, wide)
        assertEquals(PanelPage.UnknownFolder, model.focus.page)
        model.onTapPanelItem(2, actions.shown, wide)
        assertEquals("a first tap focuses", 2, model.focus.panelIndex)
        model.onTapPanelItem(2, actions.shown, wide)
        assertEquals(listOf(Triple(1L, "Xbox 360", "x360")), actions.folderAnswers)
        assertOnLine(xbox.key)
    }

    @Test
    fun aSecondWayBackFromTheListClosesIt() {
        open(xbox)
        press(Action.Back)
        press(Action.Back)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.FilesNotAdded, model.focusedOption)
    }

    @Test
    fun theCursorKeepsItsLineWhenTheListChangesUnderIt() {
        openList(xbox, wiiU)
        toLine(wiiU.key)
        model.content = content(wiiU)
        assertOnLine(wiiU.key)
        model.content = content(xbox)
        assertEquals("its line left: the nearest", model.content.notAdded.lastIndex, model.focus.panelIndex)
    }

    @Test
    fun goingInAndComingBackSoundLikeAStepDeeper() {
        openList(xbox)
        toLine(xbox.key)
        val sounds = mutableListOf<Outcome>()
        model.feel.sink = { sounds += it }
        press(Action.Enter)
        press(Action.Back)
        assertEquals(listOf(Outcome.Activated, Outcome.WentBack), sounds)
    }
}
