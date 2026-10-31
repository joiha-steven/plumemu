package com.plumemu.coredata.backup

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.ScrapedPicture
import com.plumemu.coredata.db.ScrapedPictures
import com.plumemu.coredata.settings.LibrarySettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A backup's database made ready for the swap: an older schema
 * (a file from a device that had not updated) is migrated to the app's with every row the person
 * made, the box-art paths move to this device's folder, and the result is one file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestoredDatabaseTest {
    @get:Rule val tmp = TemporaryFolder()

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    private val oldRoot = "/data/user/0/com.plumemu/no_backup/screenscraper-media"
    private val newRoot = "/data/user/10/com.plumemu/no_backup/screenscraper-media"

    @Test
    fun aVersion12SnapshotIsMigratedWithNoSubfolderSkipped() = checkMigrated(12)

    @Test
    fun aVersion11SnapshotIsMigratedWithEveryPlayedGameStillInRecent() = checkMigrated(11)

    @Test
    fun aVersion8SnapshotIsMigratedToTheCurrentVersionWithEveryRowAndNoFolderChoice() = checkMigrated(8)

    @Test
    fun aVersion6SnapshotIsMigratedToTheCurrentVersionWithEveryRowAndNoCopyChoice() = checkMigrated(6)

    @Test
    fun aVersion5SnapshotIsMigratedToTheCurrentVersionWithEveryRowAndItsPicturesMoved() = checkMigrated(5)

    @Test
    fun aVersion4SnapshotIsMigratedToTheCurrentVersionWithEveryRowAndItsPicturesMoved() = checkMigrated(4)

    private fun checkMigrated(version: Int) {
        val name = "restore-v$version.db"
        helper.createDatabase(name, version).use { db ->
            db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
            db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at) VALUES (1, 'content://tree/other-device', 'ROMs', 10)")
            db.execSQL(
                "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, modified_at, title, sort_name, " +
                    "regions, disc_count, system_id, is_primary_copy, is_hidden, is_favorite, is_pending, file_title, file_sort_name, file_regions, " +
                    "name_trust, fix_keys, is_not_game, needs_identify) VALUES (1, 1, 'd1', 'root', 'Zelda.sfc', 'Zelda.sfc', 1, 1, 'Zelda', 'zelda', " +
                    "'', 1, 'snes', 1, 0, 1, 0, 'Zelda', 'zelda', '', 'FILE_NAME', '', 0, 0)",
            )
            db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES (1, 1000, 3600)")
            if (version >= 5) db.execSQL("INSERT INTO play_session (game_id, system_id, started_at, ended_at, seconds) VALUES (1, 'snes', 10, 20, 10)")
            db.execSQL("INSERT INTO name_fix (fix_key, kind, title, fixed_at) VALUES ('crc:1', 'TITLE', 'The Legend of Zelda', 5)")
            val pictures = ScrapedPictures.encode(listOf(ScrapedPicture(ScrapedPicture.BOX_FRONT, "Large", "$oldRoot/1245/box-front-large-600x840.webp", 600, 840)))
            db.execSQL(
                "INSERT INTO scraped_game (scrape_key, game_id, outcome, scraped_at, pictures, has_box_front) VALUES ('crc:1', NULL, 'MATCHED', 50, ?, 1)",
                arrayOf<Any>(pictures),
            )
            db.execSQL("INSERT INTO media (game_id, kind, path) VALUES (1, 'box', ?)", arrayOf<Any>("$oldRoot/1245/box.webp"))
            db.execSQL("INSERT INTO media (game_id, kind, path) VALUES (1, 'box', ?)", arrayOf<Any>("/somewhere/else.webp"))
        }
        val source = ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(name)
        val file = File(tmp.root, "work/database/plumemu.db").also { it.parentFile?.mkdirs() }
        source.copyTo(file)

        val prepared = RestoredDatabase.prepare(ApplicationProvider.getApplicationContext(), file, oldRoot, newRoot)

        assertEquals(1, prepared.gameCount)
        assertEquals(1, prepared.picturesRewritten)
        assertEquals("only the paths under the old folder", 1, prepared.mediaRowsRewritten)
        listOf("-wal", "-shm", "-journal").forEach { assertFalse("no $it beside the file", File(file.path + it).exists()) }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(PlumDatabase.SCHEMA_VERSION, db.version)
            fun one(sql: String): String = db.rawQuery(sql, null).use { it.moveToFirst(); it.getString(0) }
            assertEquals("1", one("SELECT is_favorite FROM game WHERE id = 1"))
            assertEquals("3600", one("SELECT total_seconds FROM play WHERE game_id = 1"))
            assertEquals(if (version >= 5) "1" else "0", one("SELECT COUNT(*) FROM play_session"))
            assertEquals("an older backup restores with its played games in Recent", "0", one("SELECT recent_removed FROM play WHERE game_id = 1"))
            assertEquals("The Legend of Zelda", one("SELECT title FROM name_fix WHERE fix_key = 'crc:1'"))
            assertEquals("0", one("SELECT COUNT(*) FROM pinned_shortcut"))
            assertEquals("an older backup restores with no copy chosen", "0", one("SELECT COUNT(*) FROM copy_choice"))
            assertEquals("an older backup restores with no folder's system chosen", "0", one("SELECT COUNT(*) FROM folder_system"))
            assertEquals("an older backup restores with no sub-folder skipped", "0", one("SELECT COUNT(*) FROM skipped_subfolder"))
            val moved = ScrapedPictures.decode(one("SELECT pictures FROM scraped_game WHERE scrape_key = 'crc:1'")).single()
            assertEquals("$newRoot/1245/box-front-large-600x840.webp", moved.path)
            assertEquals(600 to 840, moved.width to moved.height)
            assertEquals("$newRoot/1245/box.webp", one("SELECT path FROM media WHERE path LIKE '%box.webp'"))
            assertEquals("/somewhere/else.webp", one("SELECT path FROM media WHERE path LIKE '%else.webp'"))
            assertEquals("ok", one("PRAGMA quick_check"))
        }
    }

    @Test
    fun theFlagsFollowTheirRules() {
        val device = PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = true, rescanAfterReset = true, folderMatchVersion = 2, namingRulesVersion = 2, scrapeRulesVersion = 2))
        val file = PlumSettings(library = LibrarySettings(scanOnStartup = false), flags = SetupFlags(setupCompleted = false, homeRoleAsked = false, rescanAfterReset = false, folderMatchVersion = 1, namingRulesVersion = 1, scrapeRulesVersion = 1))

        val everything = RestoreSettings.merge(file, device, withLibrary = true)
        assertEquals("every setting from the file", false, everything.library.scanOnStartup)
        assertEquals("never back to first-run setup", true, everything.flags.setupCompleted)
        assertEquals("the device's own home question", true, everything.flags.homeRoleAsked)
        assertEquals("the device's own pending rescan", true, everything.flags.rescanAfterReset)
        assertEquals("the device's own second-screen note, not shown", false, everything.flags.inGameDriftNoteShown)
        val noteShown = RestoreSettings.merge(file.copy(flags = file.flags.copy(inGameDriftNoteShown = false)), device.copy(flags = device.flags.copy(inGameDriftNoteShown = true)), withLibrary = true)
        assertEquals("the device's own second-screen note, shown", true, noteShown.flags.inGameDriftNoteShown)
        assertEquals("the match version comes with its database", 1, everything.flags.folderMatchVersion)
        assertEquals("the naming rules version comes with its database", 1, everything.flags.namingRulesVersion)
        assertEquals("the scrape rules version comes with its database", 1, everything.flags.scrapeRulesVersion)

        val settingsOnly = RestoreSettings.merge(file, device, withLibrary = false)
        assertEquals(false, settingsOnly.library.scanOnStartup)
        assertEquals("without its library every flag stays the device's", device.flags, settingsOnly.flags)

        val fresh = PlumSettings()
        assertEquals("an Everything file finished setup on a fresh device", true, RestoreSettings.merge(device, fresh, withLibrary = true).flags.setupCompleted)
        assertEquals("a Settings file does not", false, RestoreSettings.merge(device, fresh, withLibrary = false).flags.setupCompleted)
    }
}
