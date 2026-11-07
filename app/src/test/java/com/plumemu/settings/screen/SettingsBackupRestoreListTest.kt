package com.plumemu.settings.screen

import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.BackupFailure
import com.plumemu.graph.BackupState
import com.plumemu.graph.RestoreProblem
import com.plumemu.graph.RestoreState
import com.plumemu.graph.WayBackSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Advanced › Back up and restore by gamepad and touch: the list, each item's page, and when a result counts as seen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class SettingsBackupRestoreListTest : SettingsModelHarness() {
    private val copy = WayBackSet(BackupKind.Everything, restoredAtMillis = 0L, bytes = 120_000_000L)
    private val interrupted = BackupState.Interrupted(deleted = true, fileName = null)

    private val restoreForgets get() = actions.restoreCalls.count { it == "forget result" }

    private fun openRow(content: SettingsContent = SettingsContent()) {
        model.content = content
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.BackupAndRestore)
    }

    private fun openList(content: SettingsContent = SettingsContent()) {
        openRow(content)
        press(Action.Enter)
        assertEquals(PanelPage.BackupRestore, model.focus.page)
    }

    @Test
    fun undoRestoreIsListedOnlyWhileTheCopyExists() {
        assertEquals(listOf(BackupRestoreItem.Backup, BackupRestoreItem.Restore), backupRestoreItems(SettingsContent()))
        assertEquals(BackupRestoreItem.entries, backupRestoreItems(SettingsContent(wayBack = copy)))
    }

    @Test
    fun aOnEachItemOpensItsPageAndBComesBackOnThatItem() {
        openList(SettingsContent(wayBack = copy))
        val pages = listOf(PanelPage.Backup, PanelPage.Restore, PanelPage.UndoRestore)
        pages.forEachIndexed { index, page ->
            assertEquals(index, model.focus.panelIndex)
            press(Action.Enter)
            assertEquals(page, model.focus.page)
            assertEquals("the panel on screen is the item's", listItemOption(index), model.panelOption)
            assertEquals(0, model.focus.panelIndex)
            press(Action.Back)
            assertEquals(PanelPage.BackupRestore, model.focus.page)
            assertEquals(index, model.focus.panelIndex)
            move(Direction.Down)
        }
        press(Action.Back)
        assertEquals("B on the list goes to the row", SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.BackupAndRestore, model.focusedOption)
    }

    private fun listItemOption(index: Int) = listOf(SettingsOption.Backup, SettingsOption.Restore, SettingsOption.UndoRestore)[index]

    @Test
    fun aTapOpensAnItemTheFirstTapOnlyFocusesAnother() {
        openRow()
        val settings = actions.shown
        model.onTapPanelItem(BackupRestoreItem.Restore.ordinal, settings, wide)
        assertEquals(PanelPage.BackupRestore, model.focus.page)
        assertEquals(BackupRestoreItem.Restore.ordinal, model.focus.panelIndex)
        model.onTapPanelItem(BackupRestoreItem.Restore.ordinal, settings, wide)
        assertEquals(PanelPage.Restore, model.focus.page)
    }

    @Test
    fun theCopyGoingAwayLeavesUndosPageForTheList() {
        openList(SettingsContent(wayBack = copy))
        repeat(2) { move(Direction.Down) }
        press(Action.Enter)
        assertEquals(PanelPage.UndoRestore, model.focus.page)
        model.content = model.content.copy(wayBack = null)
        assertEquals(PanelPage.BackupRestore, model.focus.page)
        assertEquals(BackupRestoreItem.Restore.ordinal, model.focus.panelIndex)
    }

    @Test
    fun forgetTheCopyAsksFirstAndEndsOnTheList() {
        openList(SettingsContent(wayBack = copy))
        repeat(2) { move(Direction.Down) }
        press(Action.Enter)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals("the cursor lands on Cancel", UndoPanelItem.Cancel, undoPanelItems(model.content, model.focus.undoAsk)[model.focus.panelIndex])
        move(Direction.Up)
        press(Action.Enter)
        model.content = model.content.copy(wayBack = null)
        assertEquals(PanelPage.BackupRestore, model.focus.page)
    }

    private fun leaveTheRowWithoutOpening(geometry: SettingsGeometry) {
        openRow(SettingsContent(backup = interrupted, restore = RestoreState.Interrupted(isFinished = true)))
        move(Direction.Down, geometry)
        assertEquals("left without opening its page: not seen", 0 to 0, actions.backupForgets to restoreForgets)
    }

    @Test
    fun aResultNotOpenedStaysThroughLeavingTheRowOnAWideScreen() = leaveTheRowWithoutOpening(wide)

    @Test
    fun aResultNotOpenedStaysThroughLeavingTheRowOnANarrowScreen() = leaveTheRowWithoutOpening(narrow)

    @Test
    fun anInterruptedBackupIsForgottenOnlyAfterItsPageWasOpenedAndTheRowLeft() {
        openList(SettingsContent(backup = interrupted))
        press(Action.Enter)
        assertEquals(PanelPage.Backup, model.focus.page)
        press(Action.Back)
        press(Action.Back)
        assertEquals("still on the row: the word stays", 0, actions.backupForgets)
        move(Direction.Down)
        assertEquals(1, actions.backupForgets)
        assertEquals("Restore…'s page was not opened", 0, restoreForgets)
    }

    @Test
    fun aRestoreResultIsForgottenAfterItsOwnPageWasOpened() {
        openList(SettingsContent(restore = RestoreState.Interrupted(isFinished = false), wayBack = copy))
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(PanelPage.Restore, model.focus.page)
        leaveThePageAndTheRow()
        assertEquals(1, restoreForgets)
        assertEquals(0, actions.backupForgets)
    }

    @Test
    fun anInterruptedRestoreIsNotReadOnUndosPageWhichDoesNotSayIt() {
        openList(SettingsContent(restore = RestoreState.Interrupted(isFinished = false), wayBack = copy))
        repeat(2) { move(Direction.Down) }
        press(Action.Enter)
        assertEquals(PanelPage.UndoRestore, model.focus.page)
        leaveThePageAndTheRow()
        assertEquals(0, restoreForgets)
    }

    @Test
    fun aRefusalIsReadOnUndosPageWhichSaysIt() {
        openList(SettingsContent(restore = RestoreState.Refused(RestoreProblem.entries.first()), wayBack = copy))
        repeat(2) { move(Direction.Down) }
        press(Action.Enter)
        assertEquals(PanelPage.UndoRestore, model.focus.page)
        leaveThePageAndTheRow()
        assertEquals(1, restoreForgets)
    }

    @Test
    fun aRunningBackupIsNotCancelledByLeavingItsPage() {
        openList()
        press(Action.Enter)
        model.content = model.content.copy(backup = BackupState.Running(BackupKind.Everything))
        press(Action.Back)
        press(Action.Back)
        assertEquals(0, actions.backupCancels)
        assertTrue(model.content.backup is BackupState.Running)
    }

    private val running = BackupState.Running(BackupKind.Settings)
    private val failed = BackupState.Failed(deleted = true)
    private val undoFailed = RestoreState.Failed(isUndo = true, isPutBack = true)
    private val replacingUndo = RestoreState.Replacing(isUndo = true, withLibrary = false)

    /** B out of the item's page and the list, then off the row. */
    private fun leaveThePageAndTheRow() {
        press(Action.Back)
        press(Action.Back)
        move(Direction.Down)
    }

    @Test
    fun aPageLeftBeforeTheBackupEndedDoesNotCountAsSeen() {
        openList()
        press(Action.Enter)
        model.content = model.content.copy(backup = running)
        press(Action.Back)
        model.content = model.content.copy(backup = failed)
        press(Action.Back)
        move(Direction.Down)
        assertEquals("the reason was never on the screen", 0, actions.backupForgets)
        move(Direction.Up)
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(PanelPage.Backup, model.focus.page)
        leaveThePageAndTheRow()
        assertEquals("read on its page, then left", 1, actions.backupForgets)
    }

    @Test
    fun aBackupThatEndsWhileItsPageIsOpenIsSeen() {
        openList()
        press(Action.Enter)
        model.content = model.content.copy(backup = running)
        model.content = model.content.copy(backup = failed)
        leaveThePageAndTheRow()
        assertEquals(1, actions.backupForgets)
    }

    @Test
    fun aResultReadOnceDoesNotCoverTheNextRun() {
        openList(SettingsContent(backup = failed))
        press(Action.Enter)
        model.content = model.content.copy(backup = running)
        press(Action.Back)
        model.content = model.content.copy(backup = failed.copy(reason = BackupFailure.Damaged))
        press(Action.Back)
        move(Direction.Down)
        assertEquals("the second failure was not read", 0, actions.backupForgets)
    }

    @Test
    fun aPageLeftBeforeTheRestoreEndedDoesNotCountAsSeen() {
        openList()
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(PanelPage.Restore, model.focus.page)
        model.content = model.content.copy(restore = RestoreState.Replacing(isUndo = false, withLibrary = false))
        press(Action.Back)
        model.content = model.content.copy(restore = RestoreState.Failed(isUndo = false, isPutBack = true))
        press(Action.Back)
        move(Direction.Down)
        assertEquals(0, restoreForgets)
    }

    @Test
    fun aPageLeftBeforeTheUndoEndedDoesNotCountAsSeen() {
        openList(SettingsContent(wayBack = copy))
        repeat(2) { move(Direction.Down) }
        press(Action.Enter)
        assertEquals(PanelPage.UndoRestore, model.focus.page)
        model.content = model.content.copy(restore = replacingUndo)
        press(Action.Back)
        model.content = model.content.copy(restore = undoFailed)
        press(Action.Back)
        move(Direction.Down)
        assertEquals(0, restoreForgets)
    }

    @Test
    fun anUndoThatFailsOnItsOwnPageIsSeen() {
        openList(SettingsContent(wayBack = copy))
        repeat(2) { move(Direction.Down) }
        press(Action.Enter)
        model.content = model.content.copy(restore = replacingUndo)
        model.content = model.content.copy(restore = undoFailed)
        leaveThePageAndTheRow()
        assertEquals(1, restoreForgets)
    }

    @Test
    fun anUndoThatWorkedTookItsPageAwayUnreadAndWaitsForRestoresPage() {
        openList(SettingsContent(wayBack = copy))
        repeat(2) { move(Direction.Down) }
        press(Action.Enter)
        model.content = model.content.copy(restore = replacingUndo)
        model.content = model.content.copy(restore = RestoreState.Done(isUndo = true, kind = BackupKind.Everything, fromMillis = 0L), wayBack = null)
        assertEquals(PanelPage.BackupRestore, model.focus.page)
        press(Action.Back)
        move(Direction.Down)
        assertEquals("Undo's page never says it worked", 0, restoreForgets)
    }

    @Test
    fun aRestoreDoneIsNotReadOnUndosPageWhichDoesNotSayIt() {
        openList(SettingsContent(restore = RestoreState.Done(isUndo = false, kind = BackupKind.Everything, fromMillis = 0L), wayBack = copy))
        repeat(2) { move(Direction.Down) }
        press(Action.Enter)
        assertEquals(PanelPage.UndoRestore, model.focus.page)
        leaveThePageAndTheRow()
        assertEquals(0, restoreForgets)
    }
}
