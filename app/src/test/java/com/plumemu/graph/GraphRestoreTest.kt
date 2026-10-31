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
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.settings.toSetting
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
 * Settings › Advanced › Restore… on the graph, on real files
 * ([GraphRestoreFixture]): what an Everything and a Settings file replace, the way-back set and
 * Undo, the account, another device's folders and shortcuts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class GraphRestoreTest : GraphRestoreFixture() {
    /**
     * A file with a damaged picture (the Thor's backup had one) restores everything else; the
     * confirm counts it, and after the restart the result line says it was left out.
     */
    @Test
    fun aDamagedPictureIsLeftOutAndSaidAfterTheRestart() {
        deviceLibrary()
        val whole = everythingFile()
        val bytes = whole.readBytes()
        val name = "media/1245/box-front-large-600x840.webp".toByteArray()
        val at = (0..bytes.size - name.size).first { i -> name.indices.all { bytes[i + it] == name[it] } } + name.size + 1_000
        bytes[at] = (bytes[at].toInt() xor 0x55).toByte()
        val zip = File(tmp.root, "damaged.zip").apply { writeBytes(bytes) }
        val restore = restore()
        assertTrue(restore.read(input(zip)))
        assertEquals(1, confirmed(restore).summary.damagedMediaFiles)
        assertTrue(restore.confirm())
        waitForEnd(restore)
        assertEquals(1, relaunches)
        assertEquals(listOf("Mario", "Steam Game", "Zelda"), titlesInPlace())
        assertFalse("the damaged picture is not in place", File(places.mediaDir, "1245/box-front-large-600x840.webp").exists())
        val after = restore()
        runBlocking { after.onStartup() }
        assertEquals(RestoreState.Done(isUndo = false, kind = BackupKind.Everything, fromMillis = MADE_AT, damagedMediaFiles = 1), after.state.value)
    }

    @Test
    fun everythingIsCheckedFirstThenReplacesTheLibraryWithItsRowsBoxArtSettingsAndAccount() {
        deviceLibrary()
        val zip = everythingFile()
        val restore = restore()
        assertTrue(restore.read(input(zip)))
        val confirm = confirmed(restore)
        assertEquals(RestoreSummary(BackupKind.Everything, MADE_AT, games = 3, pictures = 1, account = AccountInFile.SignsIn), confirm.summary)
        // Read and checked: nothing on the device has changed yet.
        assertEquals(listOf("DeviceGame"), runBlocking(Dispatchers.IO) { deviceDb!!.query("SELECT title FROM game", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } } })
        assertEquals(deviceSettings, storedSettings)
        assertEquals(deviceAccount, accounts.stored)
        assertEquals(0, closes)

        assertTrue(restore.confirm())
        waitForEnd(restore)
        assertEquals("the app restarts on the new library", 1, relaunches)
        assertEquals(1, closes)

        // The file's library is in place, migrated rows and all.
        assertEquals(listOf("Mario", "Steam Game", "Zelda"), titlesInPlace())
        assertEquals(listOf("1"), query(places.databaseFile, "SELECT is_favorite FROM game WHERE title = 'Zelda'"))
        assertEquals(listOf("3600"), query(places.databaseFile, "SELECT total_seconds FROM play"))
        assertEquals(listOf("10"), query(places.databaseFile, "SELECT seconds FROM play_session"))
        assertEquals(listOf("The Legend of Zelda"), query(places.databaseFile, "SELECT title FROM name_fix"))
        assertEquals(listOf("com.valvesoftware.steamlink"), query(places.databaseFile, "SELECT package_name FROM pinned_shortcut"))
        assertEquals("the shortcut's reserved folder came along", listOf(PinnedShortcutDao.FOLDER_TREE_URI), query(places.databaseFile, "SELECT tree_uri FROM rom_folder WHERE id = 2"))
        // Its box art: the paths are this device's and the files are there.
        val picture = ScrapedPictures.decode(query(places.databaseFile, "SELECT pictures FROM scraped_game").single()).single()
        assertEquals(File(places.mediaDir, "1245/box-front-large-600x840.webp").absolutePath, picture.path)
        assertArrayEquals(FILE_PICTURE, File(picture.path).readBytes())
        assertEquals(listOf(picture.path), query(places.databaseFile, "SELECT path FROM media"))
        assertFalse("the device's own box art moved aside", File(places.mediaDir, "77").exists())
        // Room opens it as the app will after the restart.
        val reopened = Room.databaseBuilder(context, PlumDatabase::class.java, places.databaseFile.path).allowMainThreadQueries().build()
        try {
            assertEquals(3, reopened.query("SELECT COUNT(*) FROM game", null).use { it.moveToFirst(); it.getInt(0) })
        } finally {
            reopened.close()
        }

        // Settings by the flag rules; the account encrypted with this device's key.
        assertEquals(false, storedSettings.library.scanOnStartup)
        assertEquals(SoundLevel.Off, storedSettings.advanced.sounds)
        assertEquals("the device's own pending rescan", true, storedSettings.flags.rescanAfterReset)
        assertEquals("the match version came with its database", 1, storedSettings.flags.folderMatchVersion)
        assertEquals(ScraperAccount("plumtester", "enc:" + PASSWORD.reversed(), isRefused = false), accounts.stored)
        assertFalse("the password never reaches a log", logLines.any { it.contains(PASSWORD) || it.contains("plumtester") })
        assertFalse("nor a file in app storage", storageHolds(PASSWORD))

        // The way back: the device's library, box art, settings and account, moved (not copied).
        val set = File(noBackup, "before-restore")
        assertEquals(listOf("DeviceGame"), query(File(set, "database/plumemu.db"), "SELECT title FROM game"))
        assertArrayEquals(DEVICE_PICTURE, File(set, "media/77/box-front-small-120x168.webp").readBytes())
        assertFalse("no marker after a finished restore", places.markerFile.exists())
        assertFalse("the extracted file is gone", places.workDir.exists() && places.workDir.listFiles()?.isNotEmpty() == true)

        // The restart: a new process finds the result, shows it once, offers Undo, and skips the startup scan.
        val after = restore()
        assertTrue("no startup scan right after a restore", runBlocking { after.onStartup() })
        assertEquals(RestoreState.Done(isUndo = false, kind = BackupKind.Everything, fromMillis = MADE_AT), after.state.value)
        val wayBack = assertNotNullAnd(after.wayBack.value)
        assertEquals(BackupKind.Everything, wayBack.kind)
        assertEquals(RESTORED_AT, wayBack.restoredAtMillis)
        assertTrue("its size is what it keeps", wayBack.bytes >= DEVICE_PICTURE.size + File(set, "database/plumemu.db").length())
        assertFalse("the next start scans as usual", runBlocking { restore().onStartup() })
    }

    @Test
    fun anotherDevicesFoldersAndShortcutsShowTheirExistingUnavailableStates() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        restore.confirm()
        waitForEnd(restore)

        val db = Room.databaseBuilder(context, PlumDatabase::class.java, places.databaseFile.path).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runBlocking(Dispatchers.IO) {
                val folders = settingsFolders(db.scanDao().folders(), db.gameDao().observeFolderCounts().first(), AndroidFolderPermissions(context.contentResolver)::isHeld, emptySet())
                assertEquals("the reserved shortcut folder is not a picked folder", listOf("ROMs"), folders.map { it.displayName })
                assertTrue("Plumemu lost access to it: pick it again", folders.single().isUnavailable)

                // The device knows no such shortcut (Steam Link not installed here, or another user).
                val shortcuts = PinnedShortcuts({ db.pinnedShortcutDao() }, { null }, FakeShortcutHost(), "Shortcuts", scope, log = { logLines += it })
                shortcuts.checkAvailability()
                assertEquals(listOf("0"), query(places.databaseFile, "SELECT is_available FROM pinned_shortcut"))
            }
        } finally {
            scope.cancel()
            db.close()
        }
    }

    @Test
    fun undoPutsBackTheExactLibrarySettingsAndAccount() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(everythingFile()))
        confirmed(restore)
        restore.confirm()
        waitForEnd(restore)
        val keptDatabase = File(noBackup, "before-restore/database/plumemu.db")
        val keptHash = sha(keptDatabase)

        val afterRestart = restore()
        runBlocking { afterRestart.onStartup() }
        afterRestart.forgetResult()
        val restarts = relaunches
        assertTrue(afterRestart.undo())
        waitForEnd(afterRestart, restarts)
        assertEquals(restarts + 1, relaunches)

        assertEquals("the very file that was moved aside", keptHash, sha(places.databaseFile))
        assertEquals(listOf("DeviceGame"), titlesInPlace())
        assertArrayEquals(DEVICE_PICTURE, File(places.mediaDir, "77/box-front-small-120x168.webp").readBytes())
        assertFalse("the restored box art went", File(places.mediaDir, "1245").exists())
        assertEquals(deviceSettings, storedSettings)
        assertEquals("the same ciphertext, not a new sign-in", deviceAccount, accounts.stored)

        val again = restore()
        runBlocking { again.onStartup() }
        assertEquals(RestoreState.Done(isUndo = true, kind = BackupKind.Everything, fromMillis = RESTORED_AT), again.state.value)
        assertNull("the set is used up: Undo is no longer offered", again.wayBack.value)
        assertFalse(File(noBackup, "before-restore").exists())
    }

    /** a backup from before the button set is settled when it is restored, not at the next start. */
    @Test
    fun anOlderBackupWithIvoryColoursKeepsIvoryButtonsAfterTheRestore() {
        deviceLibrary()
        val ivory = ColorSet.Ivory
        fileSettings = fileSettings.copy(appearance = AppearanceSettings(background = ivory.background.toSetting(), textColor = ivory.textMode.toSetting(), accent = ivory.accent.toSetting()))
        val restore = restore()
        restore.read(input(settingsFile()))
        confirmed(restore)
        restore.confirm()
        waitFor(restore) { it !is RestoreState.Replacing }
        assertEquals("Ivory", storedSettings.appearance.buttonSet)
    }

    @Test
    fun aSettingsFileLeavesTheLibraryUntouchedAndItsUndoPutsTheSettingsBack() {
        deviceLibrary()
        val databaseBefore = titlesInPlaceWhileOpen()
        val restore = restore()
        restore.read(input(settingsFile()))
        val confirm = confirmed(restore)
        assertEquals(RestoreSummary(BackupKind.Settings, MADE_AT, games = 0, pictures = 0, account = AccountInFile.SignsIn), confirm.summary)
        restore.confirm()
        assertEquals(RestoreState.Done(isUndo = false, kind = BackupKind.Settings, fromMillis = MADE_AT), waitFor(restore) { it !is RestoreState.Replacing })

        assertEquals("no restart for settings", 0, relaunches)
        assertEquals("Room was never closed", 0, closes)
        assertEquals(databaseBefore, titlesInPlaceWhileOpen())
        assertArrayEquals(DEVICE_PICTURE, File(places.mediaDir, "77/box-front-small-120x168.webp").readBytes())
        assertEquals(SoundLevel.Off, storedSettings.advanced.sounds)
        assertEquals("the file's own colours", fileSettings.appearance.ownColors, storedSettings.appearance.ownColors)
        assertEquals("every flag stays the device's", deviceSettings.flags, storedSettings.flags)
        assertEquals(ScraperAccount("plumtester", "enc:" + PASSWORD.reversed(), isRefused = false), accounts.stored)

        val set = assertNotNullAnd(restore.wayBack.value)
        assertEquals(BackupKind.Settings, set.kind)
        assertFalse("a settings set holds no library", File(noBackup, "before-restore/database").exists())

        restore.forgetResult()
        assertTrue(restore.undo())
        assertEquals(RestoreState.Done(isUndo = true, kind = BackupKind.Settings, fromMillis = RESTORED_AT), waitFor(restore) { it !is RestoreState.Replacing })
        assertEquals(deviceSettings, storedSettings)
        assertEquals(deviceAccount, accounts.stored)
        assertEquals(databaseBefore, titlesInPlaceWhileOpen())
        assertNull(restore.wayBack.value)
    }

    @Test
    fun aFileWithoutAPasswordSignsOutAndOneWithoutAnAccountKeepsTheDevicesOwn() {
        deviceLibrary()
        val restore = restore()
        restore.read(input(settingsFile(account = BackupAccount("plumtester", null, isRefused = false))))
        assertEquals(AccountInFile.SignInAgain, confirmed(restore).summary.account)
        restore.confirm()
        waitFor(restore) { it is RestoreState.Done }
        assertNull("signed out: Settings shows its sign-in row", accounts.stored)

        accounts.stored = deviceAccount
        restore.forgetResult()
        restore.read(input(settingsFile(account = null)))
        assertEquals(AccountInFile.None, confirmed(restore).summary.account)
        restore.confirm()
        waitFor(restore) { it is RestoreState.Done }
        assertEquals(deviceAccount, accounts.stored)
    }

}
