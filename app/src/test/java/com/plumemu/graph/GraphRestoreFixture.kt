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
import com.plumemu.coredata.db.DatabaseGate
import com.plumemu.coredata.db.PinnedShortcutDao
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.ScrapedPicture
import com.plumemu.coredata.db.ScrapedPictures
import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.SoundLevel
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.LibrarySettings
import com.plumemu.coredata.settings.OwnColors
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextColorSetting
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
 * The files and fakes of `GraphRestoreTest` and `GraphRestoreRecoveryTest`: Settings › Advanced › Restore… on the graph, on real files: a
 * device library database with a WAL and box art, and a real backup zip of another device's
 * library (made by Back up…'s own writer). A "restart" is a new [GraphRestore] over the same files,
 * as the relaunched process makes it.
 */
internal abstract class GraphRestoreFixture {
    @get:Rule val tmp = TemporaryFolder()

    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected val scopeJob = SupervisorJob()
    protected val logLines = CopyOnWriteArrayList<String>()
    protected val uncaught = CopyOnWriteArrayList<Throwable>()

    protected val noBackup: File by lazy { File(tmp.root, "no_backup") }
    protected val places: RestorePlaces by lazy { RestorePlaces(File(tmp.root, "databases/plumemu.db"), File(noBackup, "screenscraper-media"), noBackup) }
    protected val sourceRoot = "/data/user/0/com.plumemu/no_backup/screenscraper-media"

    // This device: its library, box art, settings and account.
    protected val deviceSettings = PlumSettings(appearance = AppearanceSettings(ownColors = OwnColors(ColorSetting.Preset("Plum"), TextColorSetting.Light, ColorSetting.Preset("Amber"))), advanced = AdvancedSettings(animations = false), flags = SetupFlags(setupCompleted = true, homeRoleAsked = true, rescanAfterReset = true, folderMatchVersion = 2))
    protected val deviceAccount = ScraperAccount("deviceuser", "device-ciphertext", isRefused = false)
    protected var storedSettings = deviceSettings
    protected val accounts = FakeAccounts(deviceAccount)
    protected var settingsWrites = 0
    protected var failSettingsWriteNumber: Int? = null
    protected var deviceDb: PlumDatabase? = null
    protected var deviceGate: DatabaseGate? = null
    protected var quietTimeoutMs = 5_000L

    /** A scan took the slot between the confirm's check and the swap's. */
    protected var scanSlotTaken = false

    // The file's library and account.
    protected var fileSettings = PlumSettings(appearance = AppearanceSettings(ownColors = OwnColors(ColorSetting.Custom(0x2C1111), TextColorSetting.Light, ColorSetting.Custom(0x123456))), library = LibrarySettings(scanOnStartup = false), advanced = AdvancedSettings(sounds = SoundLevel.Off), flags = SetupFlags(setupCompleted = true, folderMatchVersion = 1))
    protected val fileAccount = BackupAccount("plumtester", PASSWORD, isRefused = false)

    protected var backupRunning = false
    protected var scanning = false
    protected var relaunches = 0
    protected var closes = 0

    /** A new GraphRestore over the same files: a "restart". The earlier process's work has ended first ([earlierWorkEnded]). */
    protected fun restore(): GraphRestore {
        earlierWorkEnded()
        return GraphRestore(
            files = RestoreFiles(places) { logLines += it },
            appSchemaVersion = PlumDatabase.SCHEMA_VERSION,
            readSettings = { storedSettings },
            writeSettings = { transform ->
                settingsWrites++
                if (settingsWrites == failSettingsWriteNumber) throw IOException("the settings file could not be written")
                storedSettings = transform(storedSettings)
            },
            accounts = accounts,
            prepareDatabase = { file, from, to -> RestoredDatabase.prepare(context, file, from, to) },
            closeDatabase = {
                closes++
                // The app's own close: waits for a write in flight, else refuses. A "restarted" process has none open.
                val db = deviceDb
                val gate = deviceGate
                if (db == null || gate == null) {
                    true
                } else {
                    gate.closeWhenQuiet(db, quietTimeoutMs).also { if (it) deviceDb = null }
                }
            },
            isBackupRunning = { backupRunning },
            isScanning = { scanning },
            holdScans = { !scanning && !scanSlotTaken },
            releaseScans = {},
            stopBoxArt = {},
            relaunch = { relaunches++ },
            scope = CoroutineScope(Dispatchers.Default + scopeJob + CoroutineExceptionHandler { _, e -> uncaught += e }),
            io = Dispatchers.IO,
            clock = { RESTORED_AT },
            log = { logLines += it },
        )
    }

    /**
     * Waits for every job in the shared scope, the current instance's included, such as
     * onStartup's emptying of the discard folder. A test that leaves a job blocked on purpose must
     * release it first, or this times out after 30 s. Each instance serialises only its own work, so
     * without this a "restarted" instance, or the test acting on the files as a dying process, ran
     * beside the one before: two deletions of the same discard folder at once fail inside Kotlin's
     * file walk ("rootDir must be verified to be directory beforehand"). In the app
     * there is one GraphRestore per process and a restart kills the process first (relaunchApp).
     */
    protected fun earlierWorkEnded() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                while (true) {
                    val running = scopeJob.children.filter { it.isActive }.toList()
                    if (running.isEmpty()) break
                    running.forEach { it.join() }
                }
            }
        }

    @After
    fun tearDown() =
        runBlocking {
            scopeJob.cancelAndJoin()
            deviceDb?.close()
            assertTrue("no failure escaped: $uncaught", uncaught.isEmpty())
        }

    // The device and the file.

    /** This device: one game in a WAL database (left open, as Room holds it), one box-art file. */
    protected fun deviceLibrary() {
        places.databaseFile.parentFile?.mkdirs()
        val gate = DatabaseGate()
        val db = PlumDatabase.open(context, gate, places.databaseFile.path)
        deviceGate = gate
        deviceDb = db
        runBlocking(Dispatchers.IO) {
            val w = db.openHelper.writableDatabase
            w.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
            w.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at) VALUES (1, 'content://tree/this-device', 'Games', 1)")
            insertGame(w, 1, 1, "DeviceGame", favorite = false)
        }
        File(places.mediaDir, "77").mkdirs()
        File(places.mediaDir, "77/box-front-small-120x168.webp").writeBytes(DEVICE_PICTURE)
    }

    /**
     * Another device's Everything backup, written by Back up…'s own code: games with a favourite,
     * play time and sessions, a name fix, a ScreenScraper result whose box art is in the file, and a
     * pinned shortcut in its reserved folder.
     */
    protected fun everythingFile(): File {
        val sourceFile = File(tmp.root, "other-device/plumemu.db")
        sourceFile.parentFile?.mkdirs()
        val source = Room.databaseBuilder(context, PlumDatabase::class.java, sourceFile.path).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()
        val snapshot =
            runBlocking(Dispatchers.IO) {
                val w = source.openHelper.writableDatabase
                w.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
                w.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at) VALUES (1, 'content://tree/other-device', 'ROMs', 1)")
                w.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at) VALUES (2, '${PinnedShortcutDao.FOLDER_TREE_URI}', '', 1)")
                insertGame(w, 1, 1, "Zelda", favorite = true)
                insertGame(w, 2, 1, "Mario", favorite = false)
                insertGame(w, 3, 2, "Steam Game", favorite = false)
                w.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES (1, 1000, 3600)")
                w.execSQL("INSERT INTO play_session (game_id, system_id, started_at, ended_at, seconds) VALUES (1, 'snes', 10, 20, 10)")
                w.execSQL("INSERT INTO name_fix (fix_key, kind, title, fixed_at) VALUES ('crc:1', 'TITLE', 'The Legend of Zelda', 5)")
                val pictures = ScrapedPictures.encode(listOf(ScrapedPicture(ScrapedPicture.BOX_FRONT, "Large", "$sourceRoot/1245/box-front-large-600x840.webp", 600, 840)))
                w.execSQL("INSERT INTO scraped_game (scrape_key, game_id, outcome, scraped_at, pictures, has_box_front) VALUES ('crc:1', NULL, 'MATCHED', 50, ?, 1)", arrayOf<Any>(pictures))
                w.execSQL("INSERT INTO media (game_id, kind, path) VALUES (1, 'box', ?)", arrayOf<Any>("$sourceRoot/1245/box-front-large-600x840.webp"))
                w.execSQL(
                    "INSERT INTO pinned_shortcut (game_id, package_name, shortcut_id, user_serial, label, added_at, is_available) VALUES (3, 'com.valvesoftware.steamlink', 's1', 0, 'Steam Game', 1, 1)",
                )
                DatabaseSnapshot.take(source, File(tmp.root, "other-device/snapshot.db"))
            }
        source.close()
        val mediaRoot = File(tmp.root, "other-device/media")
        File(mediaRoot, "1245").mkdirs()
        File(mediaRoot, "1245/box-front-large-600x840.webp").writeBytes(FILE_PICTURE)
        val media = BackupZip.listMedia(mediaRoot)
        val manifest =
            BackupManifest(
                kind = BackupKind.Everything,
                createdAtMillis = MADE_AT,
                appVersionName = "0.0.3-alpha",
                appVersionCode = 31,
                includes = listOf(BackupPart.Settings, BackupPart.Account, BackupPart.Database, BackupPart.Media),
                databaseSchemaVersion = snapshot.schemaVersion,
                gameCount = snapshot.gameCount,
                databaseBytes = snapshot.file.length(),
                mediaFileCount = media.size,
                mediaBytes = media.sumOf { it.size },
                // The other device's path, as its database's pictures have it.
                mediaRoot = sourceRoot,
            )
        val zip = File(tmp.root, "Plumemu backup 2026-10-23.zip")
        zip.outputStream().use { runBlocking { BackupZip.write(BackupContents(manifest, fileSettings, fileAccount, snapshot.file, media), it) } }
        return zip
    }

    protected fun settingsFile(account: BackupAccount? = fileAccount): File {
        val manifest =
            BackupManifest(
                kind = BackupKind.Settings,
                createdAtMillis = MADE_AT,
                appVersionName = "0.0.3-alpha",
                appVersionCode = 31,
                includes = listOfNotNull(BackupPart.Settings, BackupPart.Account.takeIf { account != null }),
                databaseSchemaVersion = null,
                gameCount = null,
                databaseBytes = null,
                mediaFileCount = 0,
                mediaBytes = 0,
                mediaRoot = null,
            )
        val zip = File(tmp.root, "Plumemu settings 2026-10-23.zip")
        zip.outputStream().use { runBlocking { BackupZip.write(BackupContents(manifest, fileSettings, account, null, emptyList()), it) } }
        return zip
    }

    protected fun insertGame(
        w: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Long,
        folder: Long,
        title: String,
        favorite: Boolean,
    ) = w.execSQL(
        "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, modified_at, title, sort_name, " +
            "regions, disc_count, system_id, is_primary_copy, is_hidden, is_favorite, is_pending, file_title, file_sort_name, file_regions, " +
            "name_trust, fix_keys, is_not_game, needs_identify) VALUES (?, ?, ?, 'root', ?, ?, 1, 1, ?, ?, '', 1, 'snes', 1, 0, ?, 0, ?, ?, '', 'FILE_NAME', '', 0, 0)",
        arrayOf<Any>(id, folder, "doc$id", "$title.sfc", "$title.sfc", title, title.lowercase(), if (favorite) 1 else 0, title, title.lowercase()),
    )

    // Reading what is in place.

    protected fun titlesInPlace(): List<String> = query(places.databaseFile, "SELECT title FROM game ORDER BY title")

    protected fun query(
        file: File,
        sql: String,
    ): List<String> {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            return db.rawQuery(sql, null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        } finally {
            db.close()
        }
    }

    protected fun sha(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    protected fun waitFor(
        restore: GraphRestore,
        predicate: (RestoreState) -> Boolean,
    ): RestoreState = runBlocking { withTimeout(TIMEOUT_MS) { restore.state.first(predicate) } }

    protected fun confirmed(restore: GraphRestore): RestoreState.Confirm = waitFor(restore) { it !is RestoreState.Reading } as RestoreState.Confirm

    /** Waits until the swap asked for the restart (with the library) or ended (without). */
    protected fun waitForEnd(
        restore: GraphRestore,
        relaunchesBefore: Int = relaunches,
    ) = runBlocking {
        withTimeout(TIMEOUT_MS) {
            while (relaunches == relaunchesBefore && restore.state.value is RestoreState.Replacing) kotlinx.coroutines.delay(10)
        }
    }

    protected fun input(file: File) = RestoreInput { file.inputStream() }

    /** Every file under app storage, as text: none may hold the plain password. */
    protected fun storageHolds(text: String): Boolean = tmp.root.walkTopDown().filter { it.isFile && !it.name.endsWith(".zip") }.any { String(it.readBytes(), Charsets.ISO_8859_1).contains(text) }

    protected fun titlesInPlaceWhileOpen(): List<String> =
        runBlocking(Dispatchers.IO) { deviceDb!!.query("SELECT title FROM game ORDER BY title", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } } }

    protected fun <T> assertNotNullAnd(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    /** The account store with an "encryption" that is visibly not the password: reversed, with a prefix. */
    protected class FakeAccounts(
        var stored: ScraperAccount?,
    ) : RestoreAccounts {
        override val isAvailable = true

        override suspend fun storedForRestore(): ScraperAccount? = stored

        override suspend fun encryptForRestore(account: BackupAccount): ScraperAccount =
            withContext(Dispatchers.IO) { ScraperAccount(account.username, "enc:" + requireNotNull(account.password).reversed(), account.isRefused) }

        override suspend fun putForRestore(account: ScraperAccount?) {
            stored = account
        }
    }

    protected companion object {
        const val PASSWORD = "s3cret-pass"
        const val MADE_AT = 1_792_756_800_000L
        const val RESTORED_AT = 1_792_843_200_000L
        const val TIMEOUT_MS = 30_000L
        val DEVICE_PICTURE = ByteArray(5_000) { (it % 7).toByte() }
        val FILE_PICTURE = ByteArray(9_000) { (it % 11).toByte() }
    }
}
