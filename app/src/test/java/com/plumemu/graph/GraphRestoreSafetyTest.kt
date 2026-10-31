package com.plumemu.graph

import com.plumemu.coredata.settings.SoundLevel
import android.database.sqlite.SQLiteDatabase
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coreui.testing.ReferenceShots
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Restore… review round 1: the earlier way-back set survives
 * every failure and a Settings file; an Undo keeps a set's WAL; a finished swap is never rolled back
 * at the next start; an unreadable marker still brings the library back; a write in flight refuses
 * the swap instead of racing it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class GraphRestoreSafetyTest : GraphRestoreFixture() {
    private val setDatabase: File get() = File(places.wayBackDir, "database/plumemu.db")

    /** An Everything restore of [zip], then the restarted process's start; gives that process's GraphRestore. */
    private fun restoredAndRestarted(zip: File): GraphRestore {
        val restore = restore()
        restore.read(input(zip))
        confirmed(restore)
        restore.confirm()
        waitForEnd(restore)
        return restore().also {
            runBlocking { it.onStartup() }
            it.forgetResult()
            // The start's own clean-up job ends before the test acts on the files as this process.
            earlierWorkEnded()
        }
    }

    /** What replace() did up to the moves, for a process that dies there. */
    private fun movedAside(): RestoreFiles {
        val files = RestoreFiles(places) { logLines += it }
        files.writeSettingsAndAccount(places.newWayBackDir, deviceSettings, AccountPlan.Store(deviceAccount))
        files.writeMarker(RestoreMarker(RestoreOperation.Restore, BackupKind.Everything, RestoreStep.Moving, MADE_AT))
        deviceDb!!.close()
        deviceDb = null
        files.moveAside(RestoreOperation.Restore)
        return files
    }

    private fun holdAWrite(): Pair<CountDownLatch, Thread> {
        val inTransaction = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val db = deviceDb!!
        val writer =
            thread {
                val w = db.openHelper.writableDatabase
                w.beginTransaction()
                try {
                    w.execSQL("UPDATE game SET is_favorite = 1")
                    inTransaction.countDown()
                    finish.await()
                    w.setTransactionSuccessful()
                } finally {
                    w.endTransaction()
                }
            }
        inTransaction.await()
        return finish to writer
    }

    @Test
    fun aSettingsFileIsRefusedWhileAnEverythingSetIsKeptAndTheSetStays() {
        deviceLibrary()
        val after = restoredAndRestarted(everythingFile())
        val setHash = sha(setDatabase)
        after.read(input(settingsFile()))
        confirmed(after)
        after.confirm()
        assertEquals(RestoreState.Refused(RestoreProblem.EarlierRestoreKept), after.state.value)
        assertEquals(setHash, sha(setDatabase))
        assertEquals(BackupKind.Everything, after.wayBack.value?.kind)
        runBlocking { kotlinx.coroutines.withTimeout(TIMEOUT_MS) { while (places.workDir.exists()) kotlinx.coroutines.delay(10) } }
    }

    @Test
    fun aSecondEverythingRestoreThatIsRefusedOrFailsKeepsTheFirstSet() {
        deviceLibrary()
        val zip = everythingFile()
        val after = restoredAndRestarted(zip)
        val setHash = sha(setDatabase)

        // Refused early: the library stays busy with a write.
        deviceGate = com.plumemu.coredata.db.DatabaseGate()
        deviceDb = com.plumemu.coredata.db.PlumDatabase.open(context, deviceGate, places.databaseFile.path)
        val (finish, writer) = holdAWrite()
        quietTimeoutMs = 300
        after.read(input(zip))
        confirmed(after)
        after.confirm()
        assertEquals(RestoreState.Refused(RestoreProblem.LibraryBusy), waitFor(after) { it !is RestoreState.Replacing })
        finish.countDown()
        writer.join()
        assertEquals("the first set is whole", setHash, sha(setDatabase))
        assertFalse(places.markerFile.exists())
        assertFalse("the next set was never made", places.newWayBackDir.exists())
        deviceDb!!.close()
        deviceDb = null
        val restoredHash = sha(places.databaseFile)

        // Fails late: the settings write fails after the files moved. Put back, and the first set stays.
        after.forgetResult()
        quietTimeoutMs = 5_000
        after.read(input(zip))
        confirmed(after)
        failSettingsWriteNumber = settingsWrites + 1
        after.confirm()
        waitForEnd(after)
        assertEquals("the library as it was", restoredHash, sha(places.databaseFile))
        assertEquals("the first set is still the way back", setHash, sha(setDatabase))
        val restarted = restore()
        runBlocking { restarted.onStartup() }
        assertEquals(RestoreState.Failed(isUndo = false, isPutBack = true), restarted.state.value)
        assertEquals(BackupKind.Everything, restarted.wayBack.value?.kind)
    }

    @Test
    fun undoOfASetWithAWalKeepsTheRowsOnlyInTheWal() {
        deviceLibrary()
        val after = restoredAndRestarted(everythingFile())
        // The set's database as a live one leaves it: a commit only in its WAL.
        val copyMain = File(tmp.root, "wal-copy/plumemu.db").also { it.parentFile?.mkdirs() }
        val copyWal = File(tmp.root, "wal-copy/plumemu.db-wal")
        val db = SQLiteDatabase.openDatabase(setDatabase.path, null, SQLiteDatabase.OPEN_READWRITE)
        db.rawQuery("PRAGMA journal_mode=WAL", null).use { it.moveToFirst() }
        db.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { it.moveToFirst() }
        db.execSQL("UPDATE game SET title = 'OnlyInTheWal'")
        setDatabase.copyTo(copyMain)
        File(setDatabase.path + "-wal").copyTo(copyWal)
        db.close()
        copyMain.copyTo(setDatabase, overwrite = true)
        copyWal.copyTo(File(setDatabase.path + "-wal"), overwrite = true)
        assertTrue("the WAL holds frames", copyWal.length() > 0)

        val restarts = relaunches
        assertTrue(after.undo())
        waitForEnd(after, restarts)
        assertEquals(listOf("OnlyInTheWal"), titlesInPlace())
        for (side in listOf("-wal", "-journal")) assertFalse("one file in place: $side", File(places.databaseFile.path + side).let { it.exists() && it.length() > 0 })
    }

    @Test
    fun aDeathAfterTheSetWasCompleteFinishesTheRestoreAndNeverRollsItBack() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        val files = movedAside()
        files.placeIncoming(RestoreOperation.Restore)
        files.writeMarker(RestoreMarker(RestoreOperation.Restore, BackupKind.Everything, RestoreStep.Applying, MADE_AT))
        files.completeFolder(places.newWayBackDir, BackupKind.Everything, RESTORED_AT)
        // Died here, before the marker went.

        RestoreFiles(places) { logLines += it }.recoverMovingFiles()
        assertEquals("the gate leaves a finished swap alone", listOf("Mario", "Steam Game", "Zelda"), titlesInPlace())
        val next = restore()
        assertTrue(runBlocking { next.onStartup() })
        assertEquals(RestoreState.Interrupted(isFinished = true), next.state.value)
        assertEquals(listOf("Mario", "Steam Game", "Zelda"), titlesInPlace())
        assertFalse(places.markerFile.exists())
        assertEquals(listOf("DeviceGame"), query(setDatabase, "SELECT title FROM game"))
        assertEquals(0, relaunches)
    }

    @Test
    fun anUndoThatDiedAfterItsSetWentIsFinishedAndTheScanIsSkippedOnlyOnce() {
        deviceLibrary()
        val after = restoredAndRestarted(everythingFile())
        assertNotNull(after.wayBack.value)
        val files = RestoreFiles(places) { logLines += it }
        files.writeSettingsAndAccount(places.undoAsideDir, storedSettings, AccountPlan.Store(requireNotNull(accounts.stored)))
        files.writeMarker(RestoreMarker(RestoreOperation.Undo, BackupKind.Everything, RestoreStep.Moving, RESTORED_AT))
        files.moveAside(RestoreOperation.Undo)
        files.placeIncoming(RestoreOperation.Undo)
        files.writeMarker(RestoreMarker(RestoreOperation.Undo, BackupKind.Everything, RestoreStep.Applying, RESTORED_AT))
        storedSettings = deviceSettings
        accounts.stored = deviceAccount
        files.discard(places.wayBackDir)
        // Died here, before the marker went.

        files.recoverMovingFiles()
        val next = restore()
        assertTrue(runBlocking { next.onStartup() })
        assertEquals(RestoreState.Interrupted(isFinished = true), next.state.value)
        assertEquals(listOf("DeviceGame"), titlesInPlace())
        assertFalse(places.markerFile.exists())
        assertFalse("the start after scans as usual", runBlocking { restore().onStartup() })
    }

    @Test
    fun aMarkerAtApplyingWhoseFilesWentBackPutsTheDeviceBackWithoutARestart() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        val files = movedAside()
        files.placeIncoming(RestoreOperation.Restore)
        files.writeMarker(RestoreMarker(RestoreOperation.Restore, BackupKind.Everything, RestoreStep.Applying, MADE_AT))
        storedSettings = storedSettings.copy(advanced = storedSettings.advanced.copy(sounds = SoundLevel.Off))
        // A failure put the files back but could not move the marker back to "moving".
        files.rollBackFiles(RestoreOperation.Restore)

        files.recoverMovingFiles()
        val next = restore()
        runBlocking { next.onStartup() }
        assertEquals(RestoreState.Interrupted(isFinished = false), next.state.value)
        assertEquals(listOf("DeviceGame"), titlesInPlace())
        assertEquals(deviceSettings, storedSettings)
        assertFalse(places.markerFile.exists())
        assertEquals("recovery never restarts the app", 0, relaunches)
    }

    @Test
    fun anEmptyOrUnreadableMarkerAfterTheMoveStillBringsTheLibraryBack() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        movedAside()
        places.markerFile.writeText("")
        assertFalse(places.databaseFile.exists())

        RestoreFiles(places) { logLines += it }.recoverMovingFiles()
        assertEquals(listOf("DeviceGame"), titlesInPlace())
        assertTrue("the marker is never deleted unread by the gate", places.markerFile.exists())
        val next = restore()
        runBlocking { next.onStartup() }
        assertEquals(RestoreState.Interrupted(isFinished = false), next.state.value)
        assertEquals(deviceSettings, storedSettings)
        assertFalse(places.markerFile.exists())
    }

    /** The marker is torn after the restored library went in place: the device's own library, aside, must come back, never be discarded. */
    @Test
    fun anUnreadableMarkerAfterTheRestoredLibraryWentInPlacePutsTheDevicesLibraryBack() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        val files = movedAside()
        files.placeIncoming(RestoreOperation.Restore)
        files.writeMarker(RestoreMarker(RestoreOperation.Restore, BackupKind.Everything, RestoreStep.Applying, MADE_AT))
        places.markerFile.writeText("{torn")
        assertEquals("the restored library is in place", listOf("Mario", "Steam Game", "Zelda"), titlesInPlace())

        RestoreFiles(places) { logLines += it }.recoverMovingFiles()
        assertEquals(listOf("DeviceGame"), titlesInPlace())
        val next = restore()
        runBlocking { next.onStartup() }
        assertEquals(RestoreState.Interrupted(isFinished = false), next.state.value)
        assertEquals("settings and library agree", deviceSettings, storedSettings)
        assertEquals(listOf("DeviceGame"), titlesInPlace())
        assertFalse(places.markerFile.exists())
    }

    @Test
    fun anUnreadableMarkerWithNoLibraryAnywhereKeepsTheGateShut() {
        places.databaseFile.parentFile?.mkdirs()
        places.markerFile.parentFile?.mkdirs()
        places.markerFile.writeText("{not json")
        try {
            RestoreFiles(places) { logLines += it }.recoverMovingFiles()
            fail("Room must not make an empty library while the marker cannot be read")
        } catch (e: java.io.IOException) {
            assertTrue(places.markerFile.exists())
        }
    }

    @Test
    fun aWriteInFlightRefusesTheSwapAndChangesNothing() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        val (finish, writer) = holdAWrite()
        quietTimeoutMs = 300
        restore.confirm()
        assertEquals(RestoreState.Refused(RestoreProblem.LibraryBusy), waitFor(restore) { it !is RestoreState.Replacing })
        finish.countDown()
        writer.join()
        assertEquals(0, relaunches)
        assertFalse(places.markerFile.exists())
        assertFalse(places.workDir.exists())
        assertEquals(listOf("DeviceGame"), titlesInPlaceWhileOpen())
        assertEquals(deviceSettings, storedSettings)
    }

    @Test
    fun aRestoreRefusedAtTheSwapDeletesTheExtractedFileAtOnce() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        scanSlotTaken = true
        restore.confirm()
        assertEquals(RestoreState.Refused(RestoreProblem.ScanRunning), waitFor(restore) { it !is RestoreState.Replacing })
        assertFalse(places.workDir.exists())
    }
}
