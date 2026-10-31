package com.plumemu.coredata.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema 12 to 13 (Skipped sub-folders): the empty `skipped_subfolder` table appears;
 * every other value is kept; the migration can run again and keeps a skip stored in between; the
 * result opens with Room and skips nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlumMigration12To13Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    private fun fill(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
        db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES (1, 'content://tree/1', 'ROMs', 10, 20)")
        db.execSQL("INSERT INTO scanned_directory (folder_id, document_id, relative_path, last_modified, unknown_files) VALUES (1, 'root', '', 500, 0), (1, 'h', 'Hacks', 600, 0)")
        db.execSQL(
            "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, " +
                "modified_at, title, sort_name, regions, disc_group, disc_count, system_id, is_primary_copy, file_title, " +
                "file_sort_name, file_regions, name_trust, id_method, needs_identify, is_hidden, is_favorite, is_pending, fix_keys, is_not_game) " +
                "VALUES (1, 1, 'doc1', 'h', 'Hacks/Zork.sfc', 'Zork.sfc', 1024, 99, 'Zork', 'zork', '', NULL, 1, 'snes', 1, 'Zork', 'zork', '', " +
                "'FILE_NAME', NULL, 0, 0, 1, 0, '', 0)",
        )
        db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds, recent_removed) VALUES (1, 70000, 1500, 0)")
        db.execSQL("INSERT INTO folder_system (folder_id, relative_path, system_id, chosen_at) VALUES (1, 'Xbox', NULL, 5)")
    }

    private fun rows(db: SupportSQLiteDatabase, sql: String): List<List<String?>> =
        db.query(sql).use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
        }

    private fun everything(db: SupportSQLiteDatabase) =
        listOf("system", "rom_folder", "scanned_directory", "game", "play", "folder_system").map { rows(db, "SELECT * FROM $it ORDER BY 1, 2") }

    @Test
    fun version12To13AddsAnEmptySkippedTableAndKeepsEveryValue() {
        val before = helper.createDatabase(DB_NAME, 12).use { db ->
            fill(db)
            everything(db)
        }
        helper.runMigrationsAndValidate(DB_NAME, 13, true, PlumMigrations.MIGRATION_12_13).use { db ->
            assertEquals(before, everything(db))
            assertEquals(emptyList<List<String?>>(), rows(db, "SELECT * FROM skipped_subfolder"))
        }
    }

    @Test
    fun version12To13CanRunAgainAndKeepsASkipStoredInBetween() {
        helper.createDatabase(DB_NAME, 12).use(::fill)
        val db = helper.runMigrationsAndValidate(DB_NAME, 13, true, PlumMigrations.MIGRATION_12_13)
        db.execSQL("INSERT INTO skipped_subfolder (folder_id, relative_path, skipped_at) VALUES (1, 'Hacks', 9)")
        PlumMigrations.MIGRATION_12_13.migrate(db)
        assertEquals(listOf(listOf("1", "Hacks", "9")), rows(db, "SELECT * FROM skipped_subfolder"))
        db.close()
    }

    @Test
    fun aMigratedLibraryOpensWithItsGamesAndNothingSkipped() = runTest {
        helper.createDatabase(DB_NAME, 12).use(::fill)
        val room =
            Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, DB_NAME)
                .addMigrations(*PlumMigrations.ALL)
                .allowMainThreadQueries()
                .build()
        assertEquals(emptyList<SkippedSubfolderEntity>(), room.skippedSubfolderDao().observeAll().first())
        assertEquals(1, room.scanDao().gamesInFolder(1).size)
        room.close()
    }

    private companion object {
        const val DB_NAME = "migration-12-13.db"
    }
}
