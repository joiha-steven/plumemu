package com.plumemu.graph

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.account.ScraperAccount
import com.plumemu.coredata.backup.BackupAccount
import com.plumemu.coredata.backup.BackupContents
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.backup.BackupManifest
import com.plumemu.coredata.backup.BackupPart
import com.plumemu.coredata.backup.BackupZip
import com.plumemu.coredata.backup.DatabaseSnapshot
import com.plumemu.coredata.backup.RestoredDatabase
import com.plumemu.coredata.db.PinnedShortcutDao
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.ScrapedPicture
import com.plumemu.coredata.db.ScrapedPictures
import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.SoundLevel
import com.plumemu.coredata.settings.LibrarySettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.library.scan.AndroidFolderPermissions
import com.plumemu.shortcuts.FakeShortcutHost
import com.plumemu.shortcuts.PinnedShortcuts
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Restore… when things go wrong: a failure while replacing, a
 * process death at each step, a backup or scan running, a torn file, Cancel. The device must
 * always end with its library or the file's, never neither.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class GraphRestoreRecoveryTest : GraphRestoreFixture() {
    @Test
    fun aFailureWhileReplacingPutsEverythingBackAndSaysSo() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        // The files move, then the settings write fails.
        failSettingsWriteNumber = 1
        restore.confirm()
        waitForEnd(restore)

        assertEquals("restarted: Room was closed", 1, relaunches)
        assertEquals(listOf("DeviceGame"), titlesInPlace())
        assertArrayEquals(DEVICE_PICTURE, File(places.mediaDir, "77/box-front-small-120x168.webp").readBytes())
        assertFalse(File(places.mediaDir, "1245").exists())
        assertEquals(deviceSettings, storedSettings)
        assertEquals(deviceAccount, accounts.stored)
        assertFalse(places.markerFile.exists())
        assertFalse("no half-made way-back set", File(noBackup, "before-restore").exists())

        val after = restore()
        runBlocking { after.onStartup() }
        assertEquals(RestoreState.Failed(isUndo = false, isPutBack = true), after.state.value)
        assertNull(after.wayBack.value)
    }

    @Test
    fun aMoveThatFailsHalfwayIsPutBack() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        // The file's database disappears before it can be moved in: the device's is already aside.
        assertTrue(File(places.workDir, "database/plumemu.db").delete())
        restore.confirm()
        waitForEnd(restore)
        assertEquals(listOf("DeviceGame"), titlesInPlace())
        assertArrayEquals(DEVICE_PICTURE, File(places.mediaDir, "77/box-front-small-120x168.webp").readBytes())
        assertEquals(deviceSettings, storedSettings)
        assertFalse(places.markerFile.exists())
    }

    @Test
    fun aDeathWhileTheFilesMovedIsPutBackBeforeTheDatabaseOpens() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        // What replace() had done when the process died: kept the settings, marked, closed Room, moved the library aside.
        val files = RestoreFiles(places) { logLines += it }
        files.writeSettingsAndAccount(places.newWayBackDir, deviceSettings, AccountPlan.Store(deviceAccount))
        files.writeMarker(RestoreMarker(RestoreOperation.Restore, BackupKind.Everything, RestoreStep.Moving, MADE_AT))
        deviceDb!!.close()
        files.moveAside(RestoreOperation.Restore)
        assertFalse("no library in place", places.databaseFile.exists())

        // The next process: the database gate runs before Room's first open.
        RestoreFiles(places) { logLines += it }.recoverMovingFiles()
        assertEquals(listOf("DeviceGame"), titlesInPlace())
        assertArrayEquals(DEVICE_PICTURE, File(places.mediaDir, "77/box-front-small-120x168.webp").readBytes())
        val after = restore()
        assertTrue(runBlocking { after.onStartup() })
        assertEquals(RestoreState.Interrupted(isFinished = false), after.state.value)
        assertFalse(places.markerFile.exists())
        assertEquals(deviceSettings, storedSettings)
        assertNull("no half-made way-back set", after.wayBack.value)
    }

    @Test
    fun aDeathAfterTheFilesMovedIsFinishedAtTheNextStart() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        val files = RestoreFiles(places) { logLines += it }
        files.writeSettingsAndAccount(places.newWayBackDir, deviceSettings, AccountPlan.Store(deviceAccount))
        files.writeMarker(RestoreMarker(RestoreOperation.Restore, BackupKind.Everything, RestoreStep.Moving, MADE_AT))
        deviceDb!!.close()
        files.moveAside(RestoreOperation.Restore)
        files.placeIncoming(RestoreOperation.Restore)
        files.writeMarker(RestoreMarker(RestoreOperation.Restore, BackupKind.Everything, RestoreStep.Applying, MADE_AT))

        RestoreFiles(places) { logLines += it }.recoverMovingFiles()
        assertEquals("the gate leaves a finished move in place", listOf("Mario", "Steam Game", "Zelda"), titlesInPlace())
        val after = restore()
        assertTrue(runBlocking { after.onStartup() })
        assertEquals(RestoreState.Interrupted(isFinished = true), after.state.value)
        assertEquals(SoundLevel.Off, storedSettings.advanced.sounds)
        assertEquals("plumtester", accounts.stored?.username)
        assertFalse(places.markerFile.exists())
        assertEquals("Undo is offered", BackupKind.Everything, after.wayBack.value?.kind)
    }

    @Test
    fun restoreIsRefusedWhileBoxArtIsBeingClearedAndClearIsRefusedWhileARestoreReplaces() {
        deviceLibrary()
        val zip = everythingFile()
        val restore = restore()
        assertTrue(restore.holdForFileWork())
        restore.read(input(zip))
        assertEquals(RestoreState.Refused(RestoreProblem.BoxArtBusy), restore.state.value)
        restore.releaseFileWork()

        restore.read(input(zip))
        confirmed(restore)
        restore.confirm()
        assertFalse("a restore that replaces keeps the pictures", restore.holdForFileWork())
    }

    @Test
    fun restoreIsRefusedWhileABackupOrAScanRuns() {
        deviceLibrary()
        val zip = everythingFile()
        backupRunning = true
        val restore = restore()
        assertTrue(restore.read(input(zip)))
        assertEquals(RestoreState.Refused(RestoreProblem.BackupRunning), restore.state.value)
        assertFalse("nothing was read", places.workDir.exists())

        backupRunning = false
        scanning = true
        restore.read(input(zip))
        assertEquals(RestoreState.Refused(RestoreProblem.ScanRunning), restore.state.value)

        // A scan that starts while the confirm is shown stops the restore too.
        scanning = false
        restore.read(input(zip))
        confirmed(restore)
        scanning = true
        restore.confirm()
        assertEquals(RestoreState.Refused(RestoreProblem.ScanRunning), restore.state.value)
        assertEquals(0, closes)
        assertEquals(listOf("DeviceGame"), titlesInPlaceWhileOpen())
    }

    @Test
    fun aTornFileIsRefusedAndLeavesNothingBehind() {
        deviceLibrary()
        val whole = everythingFile().readBytes()
        val torn = File(tmp.root, "torn.zip").apply { writeBytes(whole.copyOf(whole.size - 400)) }
        val restore = restore()
        restore.read(input(torn))
        assertEquals(RestoreState.Refused(RestoreProblem.Torn), waitFor(restore) { it !is RestoreState.Reading })
        assertFalse(places.workDir.exists())
        assertEquals(listOf("DeviceGame"), titlesInPlaceWhileOpen())
    }

    @Test
    fun cancelAtTheConfirmDeletesTheExtractedFile() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        assertTrue(File(places.workDir, "database/plumemu.db").exists())
        restore.cancel()
        assertEquals(RestoreState.Idle, restore.state.value)
        runBlocking { withTimeout(TIMEOUT_MS) { while (places.workDir.exists()) kotlinx.coroutines.delay(10) } }
        assertFalse(restore.confirm())
    }


}
