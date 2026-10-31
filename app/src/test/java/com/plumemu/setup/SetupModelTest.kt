package com.plumemu.setup

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.graph.ScanPhase
import com.plumemu.graph.ScanReport
import com.plumemu.library.scan.AddFolderResult
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * First-run setup's rules without a screen: the picker's answers, the scan step, the library
 * ready, and the same path by gamepad (moves, Enter, Back) and by touch (taps on controls).
 */
class SetupModelTest {
    private val source = FakeSetupSource()
    private val logLines = CopyOnWriteArrayList<String>()

    private fun TestScope.model(): SetupModel {
        val model = SetupModel(backgroundScope, source) { logLines += it }
        // The screen's collection keeps the state flowing (it is shared while subscribed).
        backgroundScope.launch { model.state.collect { } }
        runCurrent()
        return model
    }

    private fun TestScope.pick(
        model: SetupModel,
        tree: String = TREE,
    ) {
        model.onPicked(PickResult.Picked(tree))
        runCurrent()
    }

    @Test
    fun opensOnChooseFolderAndEnterAsksForThePicker() = runTest {
        val model = model()
        assertEquals(SetupStep.Folders, model.state.value.step)
        assertEquals(SetupControl.ChooseFolder, model.state.value.focus)
        assertEquals("gamepad", SetupEffect.OpenFolderPicker, model.onAction(Action.Enter))
        assertEquals("touch: a tap on the button", SetupEffect.OpenFolderPicker, model.onAction(Action.TapItem(Column.Games, 0)))
    }

    @Test
    fun aGrantedFolderIsKeptCountedAtOnceAndContinueIsFocused() = runTest {
        val model = model()
        pick(model)
        val state = model.state.value
        assertEquals(listOf(TREE), source.added.toList())
        assertEquals("counting starts at once", 1, source.scanRequests)
        assertEquals(1, state.folders.size)
        assertEquals(listOf(SetupControl.FolderCard0, SetupControl.AddFolder, SetupControl.Continue), state.controls)
        assertEquals(SetupControl.Continue, state.focus)
        assertNull(state.message)
    }

    @Test
    fun aCancelledPickerChangesNothing() = runTest {
        val model = model()
        val before = model.state.value
        model.onPicked(PickResult.Cancelled)
        runCurrent()
        assertEquals(before, model.state.value)
        assertEquals(0, source.scanRequests)
    }

    @Test
    fun aRefusedGrantSaysSoAndAddsNothing() = runTest {
        source.addAnswer = { AddFolderResult.PermissionNotGranted }
        val model = model()
        pick(model)
        assertEquals(SetupMessage.FolderNotKept, model.state.value.message)
        assertTrue(model.state.value.folders.isEmpty())
        assertEquals("focus stays on the only button", SetupControl.ChooseFolder, model.state.value.focus)
        assertEquals(0, source.scanRequests)
    }

    @Test
    fun aProviderErrorIsAMessageAndALogLineWithTheClassOnly() = runTest {
        source.addAnswer = { throw SecurityException("Permission Denial: $TREE") }
        val model = model()
        pick(model)
        assertEquals(SetupMessage.FolderCouldNotOpen, model.state.value.message)
        assertEquals(listOf("Picked folder could not be added (SecurityException)"), logLines.toList())
        assertFalse("never the folder's URI", logLines.any { "ROMs" in it || "content:" in it })
    }

    @Test
    fun aFolderTheDatabaseCouldNotRecordIsTheSameMessage() = runTest {
        source.addAnswer = { AddFolderResult.NotRecorded }
        val model = model()
        pick(model)
        assertEquals(SetupMessage.FolderCouldNotOpen, model.state.value.message)
    }

    @Test
    fun theSameFolderTwiceIsSaidOnceAndNotAddedAgain() = runTest {
        val model = model()
        pick(model)
        source.addAnswer = { AddFolderResult.AlreadyAdded(1) }
        pick(model)
        assertEquals(SetupMessage.FolderAlreadyAdded, model.state.value.message)
        assertEquals(1, model.state.value.folders.size)
        assertEquals("no second count for a folder already there", 1, source.scanRequests)
    }

    @Test
    fun noFolderPickerOnTheDeviceIsAMessage() = runTest {
        val model = model()
        model.onPicked(PickResult.NoPicker)
        runCurrent()
        assertEquals(SetupMessage.NoFolderPicker, model.state.value.message)
    }

    @Test
    fun aFolderWithNoGamesStillLetsThePersonContinue() = runTest {
        val model = model()
        pick(model)
        source.heldFolders.value = source.heldFolders.value.map { it.copy(isCounted = true, gameCount = 0) }
        source.endScan()
        runCurrent()
        assertEquals(SetupControl.Continue, model.state.value.focus)
        model.onAction(Action.Enter)
        runCurrent()
        assertEquals(SetupStep.Scan, model.state.value.step)
    }

    @Test
    fun aFolderTheScanCouldNotReadIsMarkedOnItsCard() = runTest {
        val model = model()
        pick(model)
        source.endScan(unavailable = setOf(1L))
        runCurrent()
        assertTrue(model.state.value.folders.single().isUnavailable)
    }

    @Test
    fun continueShowsTheScanUntilItEndsThenTheLibraryWithEmulators() = runTest {
        source.coverage = EmulatorCoverage(16, 18, listOf("GameCube", "PlayStation 2"))
        val model = model()
        pick(model)
        source.endScan()
        runCurrent()

        model.onAction(Action.Enter) // Continue
        runCurrent()
        assertEquals(SetupStep.Scan, model.state.value.step)
        assertTrue(model.state.value.isScanning)
        assertNull("nothing to choose while it runs", model.state.value.focus)
        assertEquals(2, source.scanRequests)

        source.systems.value = listOf(SystemCount("Super Nintendo", 248), SystemCount("Mega Drive", 173))
        source.endScan()
        runCurrent()
        val ready = model.state.value
        assertEquals(SetupStep.Ready, ready.step)
        assertEquals(421, ready.gameCount)
        assertEquals(2, ready.systemCount)
        assertEquals(EmulatorCoverage(16, 18, listOf("GameCube", "PlayStation 2")), ready.emulators)
        assertEquals(SetupControl.StartPlaying, ready.focus)
    }

    @Test
    fun twoPressesOnStartPlayingCompleteSetupOnce() = runTest {
        val model = model()
        pick(model)
        source.endScan()
        model.onAction(Action.Enter) // Continue
        runCurrent()
        source.systems.value = listOf(SystemCount("Super Nintendo", 248))
        source.endScan()
        runCurrent()
        assertEquals(SetupControl.StartPlaying, model.state.value.focus)

        model.onAction(Action.Enter)
        model.onAction(Action.Enter) // a second press before the screen is gone
        runCurrent()
        assertEquals("setup completed once", 1, source.completions)
    }

    @Test
    fun startPlayingCanBePressedAgainWhenStoringSetupFailed() = runTest {
        val model = model()
        pick(model)
        source.endScan()
        model.onAction(Action.Enter) // Continue
        runCurrent()
        source.systems.value = listOf(SystemCount("Super Nintendo", 248))
        source.endScan()
        runCurrent()

        source.saveFailure = IllegalStateException("disk")
        model.onAction(Action.Enter)
        runCurrent()
        assertEquals(SetupMessage.SaveFailed, model.state.value.message)
        source.saveFailure = null
        model.onAction(Action.Enter)
        runCurrent()
        assertEquals("the retry completed it", 1, source.completions)
    }

    @Test
    fun theLibraryIsReadyOnceTheWalkEndsWhileNamingStillRuns() = runTest {
        val model = model()
        pick(model)
        source.endScan()
        model.onAction(Action.Enter) // Continue
        runCurrent()
        source.systems.value = listOf(SystemCount("Super Nintendo", 248))

        source.scanReport.value = ScanReport(phase = ScanPhase.Reading, filesSeen = 900)
        runCurrent()
        assertEquals("the walk has not ended", SetupStep.Scan, model.state.value.step)

        // Every folder walked; the scan stays busy while it names the games.
        source.scanReport.value = ScanReport(phase = ScanPhase.Naming, walkedFolderIds = setOf(1L), gamesNamed = 200, gamesToName = 1837)
        runCurrent()
        val ready = model.state.value
        assertTrue("naming goes on in the background", ready.isScanning)
        assertEquals(SetupStep.Ready, ready.step)
        assertEquals(SetupControl.StartPlaying, ready.focus)
        assertEquals(248, ready.gameCount)
        assertEquals(2, source.scanRequests)
    }

    @Test
    fun aRevokedFolderThatIsWalkedDoesNotStandInForAShownOne() = runTest {
        val model = model()
        pick(model) // folder 1 is shown; the scan also walks folder 99, whose access was revoked (not shown)
        source.endScan()
        model.onAction(Action.Enter)
        runCurrent()
        source.systems.value = listOf(SystemCount("Super Nintendo", 248))
        source.scanReport.value = ScanReport(phase = ScanPhase.Reading, walkedFolderIds = setOf(99L))
        runCurrent()
        assertEquals("folder 1 is not walked yet", SetupStep.Scan, model.state.value.step)
        assertFalse(model.state.value.isNaming)
        source.scanReport.value = ScanReport(phase = ScanPhase.Naming, walkedFolderIds = setOf(99L, 1L))
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
    }

    @Test
    fun theLibraryWaitsForTheGameCountToCatchUpWithTheWalk() = runTest {
        val model = model()
        pick(model)
        source.endScan()
        model.onAction(Action.Enter)
        runCurrent()
        // The walk has ended but the database flow behind the count has not shown the games yet.
        source.scanReport.value = ScanReport(phase = ScanPhase.Naming, walkedFolderIds = setOf(1L))
        runCurrent()
        assertEquals("no 'No games were found yet' flash", SetupStep.Scan, model.state.value.step)
        source.systems.value = listOf(SystemCount("Super Nintendo", 248))
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
    }

    @Test
    fun theNamingLineIsOnlyForANamingScanNotOneThatWalks() = runTest {
        val model = model()
        pick(model)
        source.endScan()
        model.onAction(Action.Enter)
        runCurrent()
        source.systems.value = listOf(SystemCount("Super Nintendo", 248))
        source.scanReport.value = ScanReport(phase = ScanPhase.Naming, walkedFolderIds = setOf(1L), gamesNamed = 10, gamesToName = 100)
        runCurrent()
        assertTrue("walk over, scan busy: naming", model.state.value.isNaming)
        // A later pass that is only walking its folders.
        source.scanReport.value = ScanReport(phase = ScanPhase.Reading, filesSeen = 5)
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        assertTrue(model.state.value.isScanning)
        assertFalse("a walk is not naming", model.state.value.isNaming)
    }

    @Test
    fun theScanStepReportsTheNamingPhaseWithCounts() = runTest {
        val model = model()
        pick(model)
        source.endScan()
        model.onAction(Action.Enter)
        runCurrent()
        // Two folders, one walked: still on the scan step, naming is not final.
        source.heldFolders.value = source.heldFolders.value + SetupFolder(2, "content://x/tree/2", "SD", 0, 0, isCounted = false)
        source.scanReport.value = ScanReport(phase = ScanPhase.Naming, walkedFolderIds = setOf(1L), gamesNamed = 640, gamesToName = 1837)
        runCurrent()
        val state = model.state.value
        assertEquals(SetupStep.Scan, state.step)
        assertEquals(ScanPhase.Naming, state.scanProgress.phase)
        assertEquals(640, state.scanProgress.gamesNamed)
        assertEquals(1837, state.scanProgress.gamesToName)
        assertEquals(640f / 1837f, state.scanProgress.namedFraction!!, 0.0001f)
    }

    @Test
    fun aFailedWalkStillGivesTryAgainNotTheLibrary() = runTest {
        val model = model()
        pick(model)
        source.endScan()
        model.onAction(Action.Enter)
        runCurrent()
        source.endScan(failed = true)
        runCurrent()
        assertEquals(SetupStep.Scan, model.state.value.step)
        assertTrue(model.state.value.hasScanFailed)
        assertEquals(SetupControl.TryAgain, model.state.value.focus)
    }

    @Test
    fun aFailedScanSaysSoAndTryAgainScansAgain() = runTest {
        val model = model()
        pick(model)
        model.onAction(Action.Enter)
        runCurrent()
        source.endScan(failed = true)
        runCurrent()
        assertTrue(model.state.value.hasScanFailed)
        assertEquals(SetupControl.TryAgain, model.state.value.focus)

        model.onAction(Action.TapItem(Column.Games, 0)) // touch: Try again
        runCurrent()
        assertFalse(model.state.value.hasScanFailed)
        assertTrue(model.state.value.isScanning)
        assertEquals(3, source.scanRequests)
    }

    @Test
    fun backDuringTheScanReturnsToTheFoldersAndLeavesTheScanRunning() = runTest {
        val model = model()
        pick(model)
        model.onAction(Action.Enter)
        runCurrent()

        model.onAction(Action.Back)
        runCurrent()
        assertEquals(SetupStep.Folders, model.state.value.step)
        assertEquals(SetupControl.Continue, model.state.value.focus)
        assertTrue("the scan goes on", model.state.value.isScanning)

        // The scan ending while the person is on the folder step does not jump ahead.
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Folders, model.state.value.step)
    }

    @Test
    fun movesWalkTheControlsAndStopAtTheEnds() = runTest {
        val model = model()
        pick(model)
        model.onAction(Action.Move(Direction.Down))
        assertEquals("already the last", SetupControl.Continue, model.state.value.focus)
        model.onAction(Action.Move(Direction.Up))
        runCurrent()
        assertEquals(SetupControl.AddFolder, model.state.value.focus)
        assertEquals("Add another folder opens the picker", SetupEffect.OpenFolderPicker, model.onAction(Action.Enter))
        model.onAction(Action.Move(Direction.Up))
        runCurrent()
        assertEquals("the folder's card is a stop of its own (A removes it)", SetupControl.FolderCard0, model.state.value.focus)
        model.onAction(Action.Move(Direction.Up))
        runCurrent()
        assertEquals("already the first", SetupControl.FolderCard0, model.state.value.focus)
    }

    @Test
    fun theStatisticsSwitchIsStoredByButtonAndByTap() = runTest {
        val model = readyModel()
        model.onAction(Action.Move(Direction.Up))
        runCurrent()
        assertEquals(SetupControl.PushRightToPlay, model.state.value.focus)
        model.onAction(Action.Move(Direction.Up))
        runCurrent()
        assertEquals(SetupControl.UsageStatistics, model.state.value.focus)
        assertFalse("off until the person switches it on", model.state.value.usageStatistics)
        model.onAction(Action.Enter)
        runCurrent()
        assertTrue("switched on by the button", source.usageStatistics.value)
        assertTrue(model.state.value.usageStatistics)

        model.onAction(Action.TapItem(Column.Games, 0))
        runCurrent()
        assertFalse("off again by a tap", source.usageStatistics.value)
    }

    @Test
    fun theOneHandOptionIsUncheckedAndTheSwitchStoresItByButtonAndByTap() = runTest {
        val model = readyModel()
        assertFalse("unchecked by default", model.state.value.pushRightToPlay)
        assertFalse(source.pushRightToPlay.value)
        model.onAction(Action.Move(Direction.Up))
        runCurrent()
        assertEquals(SetupControl.PushRightToPlay, model.state.value.focus)
        model.onAction(Action.Enter)
        runCurrent()
        assertTrue("checked by the button", source.pushRightToPlay.value)
        assertTrue(model.state.value.pushRightToPlay)
        model.onAction(Action.TapItem(Column.Games, 1))
        runCurrent()
        assertFalse("unchecked again by a tap", source.pushRightToPlay.value)
        assertFalse("the question did not finish setup", source.setupCompleted)
    }

    @Test
    fun startPlayingWithoutTouchingTheSwitchLeavesStatisticsOff() = runTest {
        val model = readyModel()
        assertFalse(model.state.value.usageStatistics)
        model.onAction(Action.Enter)
        runCurrent()
        assertTrue(source.setupCompleted)
        assertFalse("setup completion does not switch statistics on", source.usageStatistics.value)
    }

    @Test
    fun startPlayingCompletesSetupByButtonOrTap() = runTest {
        val model = readyModel()
        model.onAction(Action.Enter)
        runCurrent()
        assertTrue(source.setupCompleted)

        // A second press on the same model does nothing (twoPresses...), so the tap gets a model of its own.
        source.setupCompleted = false
        val tapped = readyModel()
        tapped.onAction(Action.TapItem(Column.Games, 2))
        runCurrent()
        assertTrue(source.setupCompleted)
    }

    @Test
    fun aSettingThatCannotBeStoredIsAMessage() = runTest {
        val model = readyModel()
        source.saveFailure = IllegalStateException("disk full")
        model.onAction(Action.Enter)
        runCurrent()
        assertFalse(source.setupCompleted)
        assertEquals(SetupMessage.SaveFailed, model.state.value.message)
        assertEquals(listOf("Setup could not store a setting (IllegalStateException)"), logLines.toList())
    }

    @Test
    fun emulatorsThatCannotBeCheckedLeaveTheLineOutAndAreLogged() = runTest {
        source.coverageFailure = IllegalStateException("binder")
        val model = readyModel()
        assertEquals(SetupStep.Ready, model.state.value.step)
        assertNull(model.state.value.emulators)
        assertEquals(listOf("Emulators could not be checked (IllegalStateException)"), logLines.toList())
    }

    @Test
    fun backFromTheLibraryReturnsToTheFolders() = runTest {
        val model = readyModel()
        model.onAction(Action.Back)
        runCurrent()
        assertEquals(SetupStep.Folders, model.state.value.step)
    }

    @Test
    fun aDatabaseErrorShowsAMessageAndTryAgainReadsAgain() = runTest {
        source.foldersFailure = IllegalStateException("Room: disk I/O error")
        val model = model()
        assertTrue("the error reaches the screen", model.state.value.hasLoadFailed)
        assertEquals(SetupControl.TryAgain, model.state.value.focus)
        assertTrue(logLines.toString(), "Setup could not read the library (IllegalStateException)" in logLines)
        assertFalse("class only, never the message", logLines.any { "disk" in it })

        source.foldersFailure = null // the database answers again
        val collected = source.foldersCollections
        model.onAction(Action.Enter) // Try again
        runCurrent()
        assertFalse("read again and recovered", model.state.value.hasLoadFailed)
        assertEquals("the folders were read anew", collected + 1, source.foldersCollections)
        // Setup works again: a pick shows its card.
        pick(model)
        assertEquals(1, model.state.value.folders.size)
    }

    @Test
    fun aFailedLoadOffersSetUpLaterAndEnterLeavesSetup() = runTest {
        source.foldersFailure = IllegalStateException("Room: disk I/O error")
        val model = model()
        assertEquals(listOf(SetupControl.TryAgain, SetupControl.SetUpLater), model.state.value.controls)
        model.onAction(Action.Move(Direction.Right))
        runCurrent()
        assertEquals("focus moved to Set up later", SetupControl.SetUpLater, model.state.value.focus)
        assertEquals("nothing is stored yet", 0, source.completions)
        model.onAction(Action.Enter)
        runCurrent()
        assertEquals("setup was left, as Set up later and Start playing would", 1, source.completions)
    }

    @Test
    fun aFailedLoadLetsATapOnSetUpLaterLeaveSetup() = runTest {
        source.foldersFailure = IllegalStateException("Room: disk I/O error")
        val model = model()
        model.onAction(Action.TapItem(Column.Games, 1)) // touch: Set up later
        runCurrent()
        assertEquals(1, source.completions)
    }

    @Test
    fun aFailedLoadShowsTheMessageWhenSetUpLaterCannotBeSaved() = runTest {
        source.foldersFailure = IllegalStateException("Room: disk I/O error")
        source.saveFailure = IllegalStateException("disk full")
        val model = model()
        model.onAction(Action.TapItem(Column.Games, 1)) // Set up later
        runCurrent()
        assertTrue(model.state.value.hasLoadFailed)
        assertEquals("the person sees the refusal", SetupMessage.SaveFailed, model.state.value.message)
        source.saveFailure = null
        model.onAction(Action.TapItem(Column.Games, 1)) // may be pressed again
        runCurrent()
        assertEquals(1, source.completions)
    }

    @Test
    fun aFolderNeverCountedIsScannedWhenSetupStarts() = runTest {
        // Process death during the first scan: the folder is recorded and granted, never counted.
        source.heldFolders.value = listOf(SetupFolder(1, TREE, "ROMs", 0, 0, isCounted = false))
        val model = model()
        assertEquals("a scan is asked for at once", 1, source.scanRequests)
        assertTrue(model.state.value.isScanning)
    }

    @Test
    fun foldersAlreadyCountedAskForNoScanWhenSetupStarts() = runTest {
        source.heldFolders.value = listOf(SetupFolder(1, TREE, "ROMs", 12, 2, isCounted = true))
        model()
        assertEquals("nothing to count, no scan", 0, source.scanRequests)
    }

    @Test
    fun countingShowsOnlyWhileAScanRunsAndAFailedScanSaysSoOnTheFolderStep() = runTest {
        val model = model()
        pick(model)
        assertTrue("counting while the scan runs", model.state.value.isScanning)
        source.endScan(failed = true)
        runCurrent()
        assertFalse(model.state.value.isScanning)
        assertEquals(SetupStep.Folders, model.state.value.step)
        assertEquals(SetupMessage.ScanFailed, model.state.value.message)
    }

    @Test
    fun pickingAnUnreadableFolderAgainScansItWithoutSayingItIsThere() = runTest {
        val model = model()
        pick(model)
        source.endScan(unavailable = setOf(1L))
        runCurrent()
        assertTrue(model.state.value.folders.single().isUnavailable)

        source.addAnswer = { AddFolderResult.AlreadyAdded(1) }
        pick(model)
        assertNull("not \"already on the list\"", model.state.value.message)
        assertEquals("scanned again", 2, source.scanRequests)
    }

    @Test
    fun pickingAFolderWhoseGrantWasRevokedBringsItBackAndScansIt() = runTest {
        val model = model()
        // The database row remains but Android no longer holds the grant: no card.
        assertTrue(model.state.value.folders.isEmpty())
        source.addAnswer = {
            // Picking it again takes the grant back; the held list now has it.
            source.heldFolders.value = listOf(SetupFolder(7, TREE, "ROMs", 10, 1, isCounted = true))
            AddFolderResult.AlreadyAdded(7)
        }
        pick(model)
        assertNull(model.state.value.message)
        assertEquals(1, model.state.value.folders.size)
        assertEquals(1, source.scanRequests)
    }

    private fun TestScope.readyModel(): SetupModel {
        val model = model()
        pick(model)
        model.onAction(Action.Enter)
        runCurrent()
        source.endScan()
        runCurrent()
        return model
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
    }
}
