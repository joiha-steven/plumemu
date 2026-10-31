package com.plumemu.coredata.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema 8 to 9 (folders whose name matches no system): a version 8 file made from 8.json with a
 * row in every table, filled as a typical library leaves a device (a picked folder and the
 * shortcuts' folder, directories, games with a favourite, a hidden mark and a fix, play time,
 * measured sessions, a chosen copy, a shortcut), migrates with every row and value of every table
 * kept, `unknown_files` 0 in every directory and no folder's system chosen; it can run again; the
 * result opens with Room and stores a choice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlumMigration8To9Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    @Test
    fun version8To9KeepsEveryRowOfEveryTableAndAddsNothingChosen() {
        val before = helper.createDatabase(DB_NAME, 8).use { db ->
            fillEveryVersion8Table(db)
            assertEquals("every table of schema 8 has rows to check", VERSION_8_TABLES, tablesOf(db))
            VERSION_8_TABLES.associateWith { table -> columnsOf(db, table).let { it to rowsOf(db, table, it) } }
        }
        helper.runMigrationsAndValidate(DB_NAME, 9, true, PlumMigrations.MIGRATION_8_9).use { db ->
            assertEquals(VERSION_8_TABLES + "folder_system", tablesOf(db))
            for ((table, columnsAndRows) in before) {
                val (columns, rows) = columnsAndRows
                assertTrue("$table: rows to compare", rows.isNotEmpty())
                assertEquals("$table: every row and value kept", rows, rowsOf(db, table, columns))
            }
            assertEquals("no folder's system chosen yet", 0, rowsOf(db, "folder_system", listOf("folder_id")).size)
            assertEquals(
                "every directory reads as holding no unknown files until it is listed again",
                listOf(listOf("0"), listOf("0"), listOf("0")),
                rowsOf(db, "scanned_directory", listOf("unknown_files")),
            )
        }
    }

    @Test
    fun version8To9CanRunAgainOnAFileItAlreadyMigrated() {
        helper.createDatabase(DB_NAME, 8).use(::fillEveryVersion8Table)
        val db = helper.runMigrationsAndValidate(DB_NAME, 9, true, PlumMigrations.MIGRATION_8_9)
        db.execSQL("UPDATE scanned_directory SET unknown_files = 4 WHERE document_id = 'dir/xbox'")
        db.execSQL("INSERT INTO folder_system (folder_id, relative_path, system_id, chosen_at) VALUES (1, 'Xbox', 'xbox', 70)")
        PlumMigrations.MIGRATION_8_9.migrate(db)
        assertEquals(
            listOf(listOf("1", "Xbox", "xbox", "70")),
            rowsOf(db, "folder_system", listOf("folder_id", "relative_path", "system_id", "chosen_at")),
        )
        assertEquals(listOf(listOf("0"), listOf("0"), listOf("4")), rowsOf(db, "scanned_directory", listOf("unknown_files")))
        db.close()
    }

    @Test
    fun aMigratedLibraryOpensWithItsMarksAndStoresAFolderChoice() = runTest {
        helper.createDatabase(DB_NAME, 8).use(::fillEveryVersion8Table)
        val room =
            Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, DB_NAME)
                .addMigrations(*PlumMigrations.ALL)
                .allowMainThreadQueries()
                .build()
        assertEquals(listOf(true), room.gameDao().observeSystemGames("snes").first().map { it.isFavorite })
        assertEquals(1_500L, room.playDao().play(1)?.totalSeconds)
        assertEquals(
            "nothing listed before the folders are listed again",
            emptyList<UnknownFolderRow>(),
            room.folderSystemDao().observeUnknownFolders().first(),
        )
        room.folderSystemDao().choose(1, "Xbox", "xbox", now = 80)
        assertEquals(listOf(FolderSystemEntity(1, "Xbox", "xbox", 80)), room.folderSystemDao().choices(1))
        assertEquals(1, room.scanDao().directories(1).count { it.relativePath == "Xbox" && it.lastModified == 0L })
        room.close()
    }

    /** One or more rows in each of schema 8's tables (8.json), every column given a value where it may have one. */
    private fun fillEveryVersion8Table(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo'), ('steam', 'Valve Steam')")
        db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES (1, 'content://tree/1', 'ROMs', 10, 20)")
        db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES (2, 'plumemu:pinned-shortcuts', '', 11, NULL)")
        db.execSQL(
            "INSERT INTO scanned_directory (folder_id, document_id, relative_path, last_modified) VALUES " +
                "(1, 'root', '', 5), (1, 'dir/snes', 'SNES', 30), (1, 'dir/xbox', 'Xbox', 31)",
        )
        insertGame(db, 1, 1, "Zork Quest (USA).sfc", "snes", shown = true)
        insertGame(db, 2, 1, "Zork Quest (Europe).sfc", "snes", shown = false)
        insertGame(db, 3, 2, "Broforce", "steam", shown = true)
        db.execSQL(
            "UPDATE game SET is_favorite = 1, hash = 'abc123', id_confidence = 'HIGH', fingerprint = 'f00d', " +
                "fingerprint_size = 1024, fingerprint_mtime = 99, pack_version = 7 WHERE id = 1",
        )
        db.execSQL("UPDATE game SET is_hidden = 1 WHERE id = 2")
        db.execSQL("INSERT INTO media (id, game_id, kind, path) VALUES (1, 1, 'BOX_FRONT', '/data/box/1.png')")
        db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES (1, 70000, 1500)")
        db.execSQL(
            "INSERT INTO play_session (id, game_id, system_id, started_at, ended_at, seconds, battery_seconds, charge_start_uah, " +
                "charge_end_uah, battery_draw_ua) VALUES (1, 1, 'snes', 40000, 70000, 30, 28, 2060000, 1940000, 379000), " +
                "(2, 1, 'snes', 10000, 20000, 1470, NULL, NULL, NULL, NULL)",
        )
        db.execSQL(
            "INSERT INTO emulator_choice (id, system_id, game_id, emulator_id, is_person_choice) VALUES " +
                "(1, 'snes', NULL, 'snes9x', 0), (2, 'snes', 1, 'retroarch.bsnes', 1)",
        )
        db.execSQL("INSERT INTO name_fix (fix_key, kind, title, title_source, game_id, fixed_at) VALUES ('crc:1234', 'TITLE', 'Zork Quest', 'PERSON', 1, 50)")
        db.execSQL(
            "INSERT INTO scraped_game (scrape_key, game_id, outcome, screenscraper_id, matched_by, is_person_choice, scraped_at, details_json, " +
                "title, year, publisher, players, genre, pictures, has_box_front, support_missing, support_attempts, ask_again) " +
                "VALUES ('crc:1234', 1, 'MATCHED', '5678', 'CRC', 1, 60, '{}', 'Zork Quest', 1994, 'Infocom', '1', 'Adventure', 'box-2D', 1, 0, 2, 0)",
        )
        db.execSQL(
            "INSERT INTO pinned_shortcut (game_id, package_name, shortcut_id, user_serial, label, added_at, is_available) " +
                "VALUES (3, 'app.gamenative', 'game_123', 0, 'Broforce', 40, 1)",
        )
        db.execSQL("INSERT INTO copy_choice (game_id, chosen_at) VALUES (2, 60)")
    }

    private fun insertGame(db: SupportSQLiteDatabase, id: Long, folder: Long, file: String, system: String, shown: Boolean) {
        db.execSQL(
            "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, " +
                "modified_at, title, sort_name, regions, disc_group, disc_count, system_id, is_primary_copy, file_title, " +
                "file_sort_name, file_regions, name_trust, id_method, needs_identify, is_hidden, is_favorite, is_pending, fix_keys, is_not_game) " +
                "VALUES (?, ?, ?, 'root', ?, ?, 1024, 99, ?, 'zork quest', '', NULL, 1, ?, ?, ?, 'zork quest', '', " +
                "'IDENTIFIER_EXACT', 'DAT_NAME', 0, 0, 0, 0, '', 0)",
            arrayOf<Any>(id, folder, "doc$id", file, file, file, system, if (shown) 1 else 0, file),
        )
    }

    private fun tablesOf(db: SupportSQLiteDatabase): Set<String> =
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT IN ('android_metadata', 'room_master_table', 'sqlite_sequence')",
        ).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val name = c.getColumnIndexOrThrow("name")
            buildList { while (c.moveToNext()) add(c.getString(name)) }
        }

    /** Every row of [table], [columns] as text (null kept), in rowid order. */
    private fun rowsOf(db: SupportSQLiteDatabase, table: String, columns: List<String>): List<List<String?>> =
        db.query("SELECT ${columns.joinToString { "`$it`" }} FROM `$table` ORDER BY rowid").use { c ->
            buildList { while (c.moveToNext()) add(columns.indices.map { if (c.isNull(it)) null else c.getString(it) }) }
        }

    private companion object {
        const val DB_NAME = "migration-8-9.db"

        /** Schema 8's tables (8.json). */
        val VERSION_8_TABLES =
            setOf(
                "system", "rom_folder", "scanned_directory", "game", "media", "play", "play_session", "emulator_choice", "name_fix",
                "scraped_game", "pinned_shortcut", "copy_choice",
            )
    }
}
