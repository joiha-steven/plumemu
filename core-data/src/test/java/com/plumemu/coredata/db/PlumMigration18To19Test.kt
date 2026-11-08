package com.plumemu.coredata.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema 18 (the released 0.1.3) to 19 on a library shaped like a person's: several picked folders with games, favourites,
 * hidden games, play time and sessions. Every row of every table keeps its old columns to the byte, the new column is null
 * on every folder, the migration runs again safely, and a build that only knows 18 refuses the file with its rows intact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlumMigration18To19Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun fill(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo'), ('psx', 'PlayStation')")
        for (folder in 1..FOLDERS) {
            db.execSQL(
                "INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) " +
                    "VALUES ($folder, 'content://tree/$folder', 'ROMs $folder', ${10 * folder}, ${if (folder == FOLDERS) "NULL" else "${20 * folder}"})",
            )
            db.execSQL(
                "INSERT INTO scanned_directory (folder_id, document_id, relative_path, last_modified, unknown_files) VALUES " +
                    "($folder, 'root$folder', '', 5, 0), ($folder, 'psx$folder', 'psx', 6, 2)",
            )
        }
        for (id in 1..GAMES) {
            val folder = id % FOLDERS + 1
            val system = if (id % 3 == 0) "psx" else "snes"
            db.execSQL(
                "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, " +
                    "modified_at, title, sort_name, regions, disc_group, disc_count, system_id, is_primary_copy, is_hidden, " +
                    "is_favorite, hash, is_pending, file_title, file_sort_name, file_regions, name_trust, id_method, " +
                    "id_confidence, fingerprint, fingerprint_size, fingerprint_mtime, pack_version, fix_keys, is_not_game, " +
                    "needs_identify, added_at, art_aspect) VALUES ($id, $folder, 'doc$id', 'root$folder', 'g$id', 'g$id', ${1024 + id}, " +
                    "1700000000000, 'Game $id', 'game $id', 'USA', NULL, 1, '$system', 1, ${id % 7 / 6}, ${id % 2}, 'abc$id', 0, " +
                    "'Game $id', 'game $id', 'USA', 'DAT', 'CRC', 'EXACT', 'crc:$id', 1024, 99, 7, 'crc:$id', 0, 0, " +
                    "${if (id % 3 == 0) "NULL" else "1800000000$id"}, ${if (id % 5 == 0) "0.75" else "NULL"})",
            )
            if (id % 2 == 0) {
                db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds, recent_removed) VALUES ($id, ${70000 + id}, ${id * 60}, 0)")
                db.execSQL("INSERT INTO play_session (game_id, system_id, started_at, ended_at, seconds) VALUES ($id, '$system', ${60000 + id}, ${70000 + id}, ${id * 60})")
            }
        }
        db.execSQL("INSERT INTO name_fix (fix_key, kind, title, title_source, game_id, fixed_at) VALUES ('crc:1', 'TITLE', 'Zork I', 'PERSON', 1, 30)")
        db.execSQL("INSERT INTO skipped_subfolder (folder_id, relative_path, skipped_at) VALUES (1, 'bios', 40)")
        db.setTransactionSuccessful()
        db.endTransaction()
    }

    private fun column(db: SupportSQLiteDatabase, sql: String): List<String?> =
        db.query(sql).use { c -> buildList { while (c.moveToNext()) add(if (c.isNull(0)) null else c.getString(0)) } }

    /** table to row count and SHA-256 of every column schema 18 has, in a stable order (typed: 1 and '1' differ). */
    private fun checksums(db: SupportSQLiteDatabase): Map<String, Pair<Int, String>> =
        column(db, "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name != 'android_metadata' ORDER BY name")
            .filterNotNull()
            .associateWith { table ->
                val columns = column(db, "SELECT name FROM pragma_table_info('$table')").filter { it != "unreadable_paths" }.joinToString(", ") { "`$it`" }
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0
                db.query("SELECT $columns FROM `$table` ORDER BY 1, 2").use { c ->
                    while (c.moveToNext()) {
                        count++
                        for (i in 0 until c.columnCount) digest.update("${c.getType(i)}:${if (c.isNull(i)) "" else c.getString(i)}|".toByteArray())
                    }
                }
                count to digest.digest().joinToString("") { "%02x".format(it) }
            }

    @Test
    fun aLibraryOfSeveralFoldersKeepsEveryRowToTheByteAndNoFolderHasPaths() {
        val before = helper.createDatabase(DB_NAME, 18).use { db ->
            fill(db)
            checksums(db)
        }
        assertEquals(GAMES, before.getValue("game").first)
        assertEquals(FOLDERS, before.getValue("rom_folder").first)
        assertEquals(GAMES / 2, before.getValue("play").first)
        helper.runMigrationsAndValidate(DB_NAME, 19, true, PlumMigrations.MIGRATION_18_19).use { db ->
            assertEquals(before, checksums(db))
            assertEquals(List<String?>(FOLDERS) { null }, column(db, "SELECT unreadable_paths FROM rom_folder ORDER BY id"))
        }
    }

    @Test
    fun theMigrationItselfWritesNoRow() {
        val db = helper.createDatabase(DB_NAME, 18)
        fill(db)
        val changesBefore = column(db, "SELECT total_changes()")
        PlumMigrations.MIGRATION_18_19.migrate(db)
        assertEquals("no row written", changesBefore, column(db, "SELECT total_changes()"))
        db.close()
    }

    @Test
    fun theMigrationCanRunAgainAndKeepsWhatWasStoredInBetween() {
        helper.createDatabase(DB_NAME, 18).use(::fill)
        val db = helper.runMigrationsAndValidate(DB_NAME, 19, true, PlumMigrations.MIGRATION_18_19)
        db.execSQL("UPDATE rom_folder SET unreadable_paths = 'psx\nsnes/hacks' WHERE id = 2")
        val before = checksums(db)
        PlumMigrations.MIGRATION_18_19.migrate(db)
        assertEquals(before, checksums(db))
        assertEquals(listOf("psx\nsnes/hacks"), column(db, "SELECT unreadable_paths FROM rom_folder WHERE id = 2"))
        db.close()
    }

    /** The whole chain opens through Room: the scan's write and the folder list read the new column; removing a folder takes it. */
    @Test
    fun aMigratedLibraryStoresAndForgetsPathsThroughTheDao() = runBlocking {
        helper.createDatabase(DB_NAME, 18).use(::fill)
        val room =
            Room.databaseBuilder(context, PlumDatabase::class.java, DB_NAME)
                .addMigrations(*PlumMigrations.all(TestDetailsRating))
                .allowMainThreadQueries()
                .build()
        val dao = room.scanDao()
        assertEquals(FOLDERS, dao.folders().size)
        dao.setUnreadablePaths(1, "psx")
        assertEquals(listOf("psx", null, null), dao.folders().sortedBy { it.id }.map { it.unreadablePaths })
        dao.setUnreadablePaths(1, null)
        assertNull(dao.folders().single { it.id == 1L }.unreadablePaths)
        dao.setUnreadablePaths(2, "psx")
        dao.deleteFolder(2)
        assertEquals(listOf(1L, 3L), dao.folders().map { it.id }.sorted())
        room.close()
    }

    /** Going back: a build that only knows 18 refuses a schema 19 file and touches nothing; the copy taken before opens at 18. */
    @Test
    fun anOlderBuildRefusesTheFileAndNothingIsLost() {
        val database = helper.createDatabase(DB_NAME, 18).use { db ->
            fill(db)
            context.getDatabasePath(DB_NAME)
        }
        val copyBeforeUpdate = File(database.parentFile, "before-update-19.db")
        database.copyTo(copyBeforeUpdate, overwrite = true)
        helper.runMigrationsAndValidate(DB_NAME, 19, true, PlumMigrations.MIGRATION_18_19).close()

        val refused = assertThrows(IllegalStateException::class.java) { helper.runMigrationsAndValidate(DB_NAME, 18, true) }
        assertNotEquals(null, refused.message)

        android.database.sqlite.SQLiteDatabase.openDatabase(database.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(19, db.version)
            db.rawQuery("SELECT COUNT(*) FROM game", null).use { it.moveToFirst(); assertEquals(GAMES, it.getInt(0)) }
        }
        android.database.sqlite.SQLiteDatabase.openDatabase(copyBeforeUpdate.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(18, db.version)
            db.rawQuery("SELECT COUNT(*) FROM game", null).use { it.moveToFirst(); assertEquals(GAMES, it.getInt(0)) }
        }
    }

    private companion object {
        const val DB_NAME = "migration-18-19.db"
        const val FOLDERS = 3
        const val GAMES = 600
    }
}
