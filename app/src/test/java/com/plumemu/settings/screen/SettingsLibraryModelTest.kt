package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.graph.ScanPhase
import com.plumemu.graph.ScanReport
import com.plumemu.graph.ScanResult
import com.plumemu.setup.SetupFolder
import com.plumemu.sound.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings › Library and Emulators, by gamepad and by touch: folders and their Remove
 * confirm (by id, never another folder), + Add folder, Rescan, a system's emulators.
 */
internal class SettingsLibraryModelTest : SettingsModelHarness() {
    private val internal = folder(1, "content://com.android.externalstorage.documents/tree/primary%3AROMs")
    private val sd = folder(2, "content://com.android.externalstorage.documents/tree/1234-ABCD%3AGames")

    private fun openFolders() {
        model.content = SettingsContent(folders = listOf(internal, sd))
        openSection(SettingsSection.Library)
        assertEquals(SettingsOption.RomFolders, model.focusedOption)
        press(Action.Enter)
        assertEquals(PanelPage.Folders, model.focus.page)
    }

    /** From the folder's page (A on a folder), down to its last item, Remove this folder…, and A. */
    private fun openRemove() {
        assertEquals(PanelPage.Folder, model.focus.page)
        repeat(folderPageItems(model.content, model.focus.folderId).size) { move(Direction.Down) }
        press(Action.Enter)
    }

    @Test
    fun aFolderOpensItsPageAndRemoveAsksFirstAndCancelRemovesNothing() {
        openFolders()
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(PanelPage.Folder, model.focus.page)
        assertEquals("the page is about the folder pressed", sd.id, model.focus.folderId)
        assertEquals("Skip a sub-folder… is focused first", 0, model.focus.panelIndex)
        openRemove()
        assertEquals(PanelPage.RemoveFolder, model.focus.page)
        assertEquals("Cancel is focused first", SettingsModel.CONFIRM_CANCEL, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals("Cancel goes back to the folder's page, on Remove", PanelPage.Folder, model.focus.page)
        assertEquals(1, model.focus.panelIndex)
        press(Action.Enter)
        press(Action.Back)
        assertEquals(PanelPage.Folder, model.focus.page)
        press(Action.Back)
        assertEquals(PanelPage.Folders, model.focus.page)
        assertEquals("back on the same folder", 1, model.focus.panelIndex)
        assertEquals(emptyList<Long>(), actions.removed)
    }

    @Test
    fun removeTakesOnlyTheFolderTheConfirmNamedEvenIfTheListChanges() {
        openFolders()
        move(Direction.Down)
        press(Action.Enter)
        openRemove()
        // The first folder leaves meanwhile (another removal): the confirm is still about the SD card.
        model.content = SettingsContent(folders = listOf(sd))
        move(Direction.Right)
        press(Action.Enter)
        assertEquals(listOf(sd.id), actions.removed)
        assertEquals(setOf(sd.id), model.work.removing)
        assertEquals(PanelPage.Folders, model.focus.page)
        // While it waits for a running scan, A on it does not ask again.
        press(Action.Enter)
        assertEquals(PanelPage.Folders, model.focus.page)
        actions.answer(true)
        assertEquals(emptySet<Long>(), model.work.removing)
        assertNull(model.work.notice)
    }

    @Test
    fun aFailedRemovalSaysSo() {
        openFolders()
        press(Action.Enter)
        openRemove()
        move(Direction.Right)
        press(Action.Enter)
        actions.answer(false)
        assertEquals(SettingsOption.RomFolders to SettingsNotice.FolderRemoveFailed, model.work.notice)
    }

    @Test
    fun addFolderOpensThePickerOrSaysThereIsNone() {
        openFolders()
        move(Direction.Down)
        move(Direction.Down)
        assertEquals("+ Add folder is after the folders", 2, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals(1, actions.picks)
        assertNull(model.work.notice)
        actions.hasPicker = false
        press(Action.Enter)
        assertEquals(SettingsOption.RomFolders to SettingsNotice.NoFolderPicker, model.work.notice)
    }

    @Test
    fun touchFocusesAFolderThenASecondTapAsks() {
        model.content = SettingsContent(folders = listOf(internal, sd))
        model.onTapSection(SettingsSection.Library) // focused already: the tap enters it
        model.onTapValue(0, actions.settings, wide)
        assertEquals(PanelPage.Folders, model.focus.page)
        model.onTapPanelItem(1, actions.settings, wide)
        assertEquals(1, model.focus.panelIndex)
        model.onTapPanelItem(1, actions.settings, wide)
        assertEquals(PanelPage.Folder, model.focus.page)
        repeat(2) { model.onTapPanelItem(1, actions.settings, wide) }
        assertEquals(PanelPage.RemoveFolder, model.focus.page)
        model.onTapPanelItem(SettingsModel.CONFIRM_RESET, actions.settings, wide)
        model.onTapPanelItem(SettingsModel.CONFIRM_RESET, actions.settings, wide)
        assertEquals(listOf(sd.id), actions.removed)
    }

    @Test
    fun rescanStartsOrSaysAScanAlreadyRuns() {
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Rescan)
        press(Action.Enter)
        assertEquals(1, actions.rescans)
        assertNull(model.work.notice)
        actions.scanBusy = true
        model.onTapValue(1, actions.settings, wide)
        assertEquals(2, actions.rescans)
        assertEquals(SettingsOption.Rescan to SettingsNotice.ScanBusy, model.work.notice)
        move(Direction.Down)
        assertNull("the line goes with the focus", model.work.notice)
    }

    @Test
    fun theRescanResultGoesWhenTheFocusLeavesTheRow() {
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Rescan)
        val before = actions.scanResultsForgotten
        press(Action.Enter)
        assertEquals("starting a scan keeps what it will show", before, actions.scanResultsForgotten)
        move(Direction.Down)
        assertEquals("moving to another row", before + 1, actions.scanResultsForgotten)
    }

    @Test
    fun theRescanResultGoesWhenTheFocusComesBackToTheRow() {
        openSection(SettingsSection.Library)
        val before = actions.scanResultsForgotten
        move(Direction.Down)
        assertEquals(SettingsOption.Rescan, model.focusedOption)
        assertEquals("a result from a scan that ended meanwhile is not shown on arrival", before + 1, actions.scanResultsForgotten)
    }

    @Test
    fun theScreenGoingAwayDoesNotClearTheRescanResult() {
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Rescan)
        val before = actions.scanResultsForgotten
        model.onDispose()
        // Screen off or an app switch only stops the content flow; a recreated Activity starts Settings over on the sections (the model is not saved).
        assertEquals("only a person's move clears it", before, actions.scanResultsForgotten)
    }

    @Test
    fun backFromTheRescanRowAtWideSizesClearsTheResult() {
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Rescan)
        val before = actions.scanResultsForgotten
        press(Action.Back)
        assertEquals(SettingsColumn.Sections, model.focus.column)
        assertEquals(before + 1, actions.scanResultsForgotten)
        // Right back into the row: nothing old comes back.
        press(Action.Enter)
        assertEquals(before + 2, actions.scanResultsForgotten)
    }

    @Test
    fun theBusyNoticeStaysUnderTheProgressUntilTheScanEndsAndThePressIsAcknowledged() {
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Rescan)
        actions.scanBusy = true
        val sounds = mutableListOf<Outcome>()
        model.feel.sink = { sounds += it }
        press(Action.Enter)
        assertEquals(listOf(Outcome.Activated), sounds)
        assertEquals(SettingsOption.Rescan to SettingsNotice.ScanBusy, model.work.notice)
        model.content = model.content.copy(isScanning = true, scan = ScanReport(phase = ScanPhase.Reading, filesSeen = 10))
        model.content = model.content.copy(scan = ScanReport(phase = ScanPhase.Reading, filesSeen = 500))
        assertEquals("progress does not wipe it", SettingsOption.Rescan to SettingsNotice.ScanBusy, model.work.notice)
        model.content = model.content.copy(isScanning = false, scan = ScanReport(result = ScanResult()))
        assertNull("the end of the scan clears it", model.work.notice)
    }

    @Test
    fun aSystemOpensOnItsCurrentChoiceAndStoresWhatIsPicked() {
        model.content = SettingsContent(systems = listOf(snes, psx))
        openSection(SettingsSection.Emulators)
        assertEquals(listOf(SettingsOption.SystemEmulator, SettingsOption.SystemEmulator, SettingsOption.DetectEmulators), SettingsSection.Emulators.rowsIn(model.content))
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(PanelPage.Emulators, model.focus.page)
        assertEquals("psx", model.focus.systemId)
        assertEquals("SwanStation, the person's choice, is item 2", 2, model.focus.panelIndex)
        move(Direction.Up)
        press(Action.Enter)
        assertEquals(listOf("psx" to "duckstation"), actions.chosen)
        assertEquals(SettingsColumn.Options, model.focus.column)
        // Automatic (item 0) forgets the choice.
        press(Action.Enter)
        move(Direction.Up)
        move(Direction.Up)
        press(Action.Enter)
        assertEquals("psx" to null, actions.chosen.last())
    }

    @Test
    fun aLegalRiskEmulatorCanBeChosenAndAFailedWriteSaysSo() {
        model.content = SettingsContent(systems = listOf(switch))
        openSection(SettingsSection.Emulators)
        press(Action.Enter)
        assertEquals("no automatic choice: Automatic is current", 0, model.focus.panelIndex)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(listOf("switch" to "eden"), actions.chosen)
        actions.answer(false)
        assertEquals(SettingsOption.SystemEmulator to SettingsNotice.EmulatorChoiceFailed, model.work.notice)
    }

    @Test
    fun theChoiceGoesToTheSystemThePanelOpenedForWhenSystemsArrive() {
        model.content = SettingsContent(systems = listOf(psx))
        openSection(SettingsSection.Emulators)
        press(Action.Enter)
        // A scan finds Super Nintendo games: it comes first now, the panel stays on PlayStation.
        model.content = SettingsContent(systems = listOf(snes, psx))
        press(Action.Enter)
        assertEquals("psx", actions.chosen.single().first)
    }

    @Test
    fun detectRunsOnceAtATimeAndSaysWhenDone() {
        openSection(SettingsSection.Emulators)
        assertEquals(SettingsOption.DetectEmulators, model.focusedOption)
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(1, actions.detections)
        assertTrue(model.work.isDetecting)
        actions.answer(true)
        assertEquals(SettingsOption.DetectEmulators to SettingsNotice.DetectDone, model.work.notice)
        press(Action.Enter)
        actions.answer(false)
        assertEquals(SettingsOption.DetectEmulators to SettingsNotice.DetectFailed, model.work.notice)
    }

    @Test
    fun aDetectionStartedInAnEarlierSettingsIsNotStartedAgain() {
        model.content = SettingsContent(systems = listOf(psx), isDetecting = true)
        openSection(SettingsSection.Emulators)
        focusOption(SettingsOption.DetectEmulators)
        press(Action.Enter)
        assertEquals(0, actions.detections)
    }

    @Test
    fun aFolderRemovedFromAnEarlierSettingsIsNotAskedAgain() {
        openFolders()
        model.content = SettingsContent(folders = listOf(internal, sd), removingFolderIds = setOf(sd.id))
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(PanelPage.Folders, model.focus.page)
        assertEquals(emptyList<Long>(), actions.removed)
    }

    @Test
    fun leavingRomFoldersOrSettingsForgetsThePickersAnswer() {
        openFolders()
        move(Direction.Down)
        assertEquals("moving inside the panel keeps it", 0, actions.folderAnswersForgotten)
        press(Action.Back)
        move(Direction.Down)
        assertEquals(SettingsOption.Rescan, model.focusedOption)
        assertEquals(1, actions.folderAnswersForgotten)
        move(Direction.Up)
        model.onDispose()
        assertEquals(2, actions.folderAnswersForgotten)
    }

    @Test
    fun narrowScreensOpenTheEmulatorsListAsAStop() {
        model.content = SettingsContent(systems = listOf(psx))
        openSection(SettingsSection.Emulators)
        move(Direction.Right, narrow)
        assertEquals(PanelPage.Emulators, model.focus.page)
        press(Action.Back, narrow)
        focusOption(SettingsOption.DetectEmulators)
        move(Direction.Right, narrow)
        assertEquals("an action's explanation is a stop on narrow screens", PanelPage.ReadOnly, model.focus.page)
    }

    private fun folder(
        id: Long,
        treeUri: String,
    ) = SetupFolder(id, treeUri, displayName = "", gameCount = 3, systemCount = 1, isCounted = true)

    private companion object {
        val duck = EmulatorEntry("duckstation", "DuckStation", isLegalRisk = false)
        val swan = EmulatorEntry("retroarch-swanstation", "SwanStation", isLegalRisk = false)
        val snes = SystemEmulators("snes", "Super Nintendo", automatic = null, chosen = null, installed = emptyList())
        val psx = SystemEmulators("psx", "PlayStation", automatic = duck, chosen = swan, installed = listOf(duck, swan))
        val switch =
            SystemEmulators("switch", "Nintendo Switch", automatic = null, chosen = null, installed = listOf(EmulatorEntry("eden", "Eden", isLegalRisk = true)))
    }
}
