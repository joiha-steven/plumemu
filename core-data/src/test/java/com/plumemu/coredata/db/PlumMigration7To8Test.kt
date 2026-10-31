package com.plumemu.coredata.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema 7 to 8 (battery use per play): a version 7 file made from 7.json, filled as a
 * build after 0.0.2-alpha leaves a device (a favourite, play time, two sessions, a chosen copy),
 * migrates with every row kept and its sessions read as "not measured"; it can run again; the
 * result opens with Room and stores and reads battery figures. 0.0.2-alpha itself leaves schema 6
 * (tag `alpha.0.0.2` has schemas up to 6.json): a version 6 file with a row in every table goes
 * through 6 to 7 to 8 with every row of every table kept. The earlier steps are in PlumMigrationTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlumMigration7To8Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    @Test
    fun version7To8KeepsEverySessionAndReadsItAsNotMeasured() {
        helper.createDatabase(DB_NAME, 7).use(::fillVersion7)
        helper.runMigrationsAndValidate(DB_NAME, 8, true, PlumMigrations.MIGRATION_7_8).use { db ->
            assertVersion7Kept(db)
            db.query("SELECT battery_seconds, charge_start_uah, charge_end_uah, battery_draw_ua FROM play_session").use { c ->
                assertEquals(2, c.count)
                while (c.moveToNext()) for (column in 0..3) assertTrue("old sessions were not measured", c.isNull(column))
            }
        }
    }

    @Test
    fun version7To8CanRunAgainOnAFileItAlreadyMigrated() {
        helper.createDatabase(DB_NAME, 7).use(::fillVersion7)
        val db = helper.runMigrationsAndValidate(DB_NAME, 8, true, PlumMigrations.MIGRATION_7_8)
        db.execSQL("UPDATE play_session SET battery_seconds = 600, battery_draw_ua = 400000 WHERE id = 2")
        PlumMigrations.MIGRATION_7_8.migrate(db)
        assertVersion7Kept(db)
        db.query("SELECT battery_seconds, battery_draw_ua FROM play_session WHERE id = 2").use { c ->
            c.moveToFirst()
            assertEquals("a second run leaves stored figures alone", listOf(600L, 400_000L), listOf(c.getLong(0), c.getLong(1)))
        }
        db.close()
    }

    @Test
    fun aMigratedLibraryOpensAndStoresAPlaysBatteryUse() = runTest {
        helper.createDatabase(DB_NAME, 7).use(::fillVersion7)
        val room =
            Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, DB_NAME)
                .addMigrations(*PlumMigrations.ALL)
                .allowMainThreadQueries()
                .build()
        assertEquals(listOf(true), room.gameDao().observeSystemGames("snes").first().map { it.isFavorite })
        assertEquals(1_500L, room.playDao().play(1)?.totalSeconds)
        assertNull("old sessions give no usual pace", room.playDao().usualDraw(1))
        room.playDao().addSession(1, endedAt = 90_000, seconds = 600, battery = SessionBattery(540, 3_000_000, 2_950_000, 333_000))
        assertEquals(333_000L, room.playDao().usualDraw(1))
        assertEquals(2_100L, room.playDao().play(1)?.totalSeconds)
        room.close()
    }

    @Test
    fun anAlphaVersion6LibraryGoesThroughSevenToEightWithEveryRowOfEveryTableKept() {
        val before = helper.createDatabase(DB_NAME, 6).use { db ->
            fillEveryVersion6Table(db)
            assertEquals("every table of schema 6 has rows to check", VERSION_6_TABLES, tablesOf(db))
            VERSION_6_TABLES.associateWith { table -> columnsOf(db, table) to rowsOf(db, table, columnsOf(db, table)) }
        }
        helper.runMigrationsAndValidate(DB_NAME, 8, true, PlumMigrations.MIGRATION_6_7, PlumMigrations.MIGRATION_7_8).use { db ->
            assertEquals(VERSION_6_TABLES + "copy_choice", tablesOf(db))
            for ((table, columnsAndRows) in before) {
                val (columns, rows) = columnsAndRows
                assertTrue("$table: rows to compare", rows.isNotEmpty())
                assertEquals("$table: every row and value kept", rows, rowsOf(db, table, columns))
            }
            assertEquals("no copy chosen yet", 0, rowsOf(db, "copy_choice", listOf("game_id")).size)
            db.query("SELECT battery_seconds, charge_start_uah, charge_end_uah, battery_draw_ua FROM play_session").use { c ->
                assertEquals(2, c.count)
                while (c.moveToNext()) for (column in 0..3) assertTrue("old sessions were not measured", c.isNull(column))
            }
        }
    }

    /** One or two rows in each of schema 6's tables (6.json), every column given a value where it may have one. */
    private fun fillEveryVersion6Table(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
        db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES (1, 'content://tree/1', 'ROMs', 10, 20)")
        db.execSQL("INSERT INTO scanned_directory (folder_id, document_id, relative_path, last_modified) VALUES (1, 'dir/snes', 'SNES', 30)")
        insertGame(db, 1, "Zork Quest (USA).sfc", shown = true)
        insertGame(db, 2, "Zork Quest (Europe).sfc", shown = false)
        db.execSQL("UPDATE game SET is_favorite = 1, hash = 'abc123', id_confidence = 'HIGH', fingerprint = 'f00d', fingerprint_size = 1024, fingerprint_mtime = 99, pack_version = 7 WHERE id = 1")
        db.execSQL("INSERT INTO media (id, game_id, kind, path) VALUES (1, 1, 'BOX_FRONT', '/data/box/1.png')")
        db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES (1, 70000, 1500)")
        db.execSQL("INSERT INTO play_session (id, game_id, system_id, started_at, ended_at, seconds) VALUES (1, 1, 'snes', 40000, 70000, 30)")
        db.execSQL("INSERT INTO play_session (id, game_id, system_id, started_at, ended_at, seconds) VALUES (2, 1, 'snes', 10000, 20000, 1470)")
        db.execSQL("INSERT INTO emulator_choice (id, system_id, game_id, emulator_id, is_person_choice) VALUES (1, 'snes', NULL, 'snes9x', 0), (2, 'snes', 1, 'retroarch.bsnes', 1)")
        db.execSQL("INSERT INTO name_fix (fix_key, kind, title, title_source, game_id, fixed_at) VALUES ('zork quest|snes', 'TITLE', 'Zork Quest', 'PERSON', 1, 50)")
        db.execSQL(
            "INSERT INTO scraped_game (scrape_key, game_id, outcome, screenscraper_id, matched_by, is_person_choice, scraped_at, details_json, " +
                "title, year, publisher, players, genre, pictures, has_box_front, support_missing, support_attempts, ask_again) " +
                "VALUES ('crc:1234', 1, 'MATCHED', '5678', 'CRC', 1, 60, '{}', 'Zork Quest', 1994, 'Infocom', '1', 'Adventure', 'box-2D', 1, 0, 2, 0)",
        )
        db.execSQL(
            "INSERT INTO pinned_shortcut (game_id, package_name, shortcut_id, user_serial, label, added_at, is_available) " +
                "VALUES (2, 'app.gamenative', 'game_123', 0, 'Broforce', 40, 1)",
        )
    }

    private fun tablesOf(db: SupportSQLiteDatabase): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT IN ('android_metadata', 'room_master_table', 'sqlite_sequence')").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

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

    private fun fillVersion7(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
        db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES (1, 'content://tree/1', 'ROMs', 10, 20)")
        insertGame(db, 1, "Zork Quest (USA).sfc", shown = true)
        insertGame(db, 2, "Zork Quest (Europe).sfc", shown = false)
        db.execSQL("UPDATE game SET is_favorite = 1 WHERE id = 1")
        db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES (1, 70000, 1500)")
        db.execSQL("INSERT INTO play_session (id, game_id, system_id, started_at, ended_at, seconds) VALUES (1, 1, 'snes', 40000, 70000, 30)")
        db.execSQL("INSERT INTO play_session (id, game_id, system_id, started_at, ended_at, seconds) VALUES (2, 1, 'snes', 10000, 20000, 1470)")
        db.execSQL("INSERT INTO copy_choice (game_id, chosen_at) VALUES (2, 60)")
    }

    private fun insertGame(db: SupportSQLiteDatabase, id: Long, file: String, shown: Boolean) {
        db.execSQL(
            "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, " +
                "modified_at, title, sort_name, regions, disc_group, disc_count, system_id, is_primary_copy, file_title, " +
                "file_sort_name, file_regions, name_trust, id_method, needs_identify, is_hidden, is_favorite, is_pending, fix_keys, is_not_game) " +
                "VALUES (?, 1, ?, 'root', ?, ?, 1024, 99, ?, 'zork quest', '', NULL, 1, 'snes', ?, ?, 'zork quest', '', " +
                "'IDENTIFIER_EXACT', 'DAT_NAME', 0, 0, 0, 0, '', 0)",
            arrayOf<Any>(id, "doc$id", file, file, file, if (shown) 1 else 0, file),
        )
    }

    private fun assertVersion7Kept(db: SupportSQLiteDatabase) {
        db.query("SELECT id, game_id, system_id, started_at, ended_at, seconds FROM play_session ORDER BY id").use { c ->
            val rows = buildList { while (c.moveToNext()) add(listOf(c.getLong(0), c.getLong(1), c.getString(2), c.getLong(3), c.getLong(4), c.getLong(5))) }
            assertEquals(listOf(listOf(1L, 1L, "snes", 40_000L, 70_000L, 30L), listOf(2L, 1L, "snes", 10_000L, 20_000L, 1_470L)), rows)
        }
        db.query("SELECT is_favorite FROM game WHERE id = 1").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT total_seconds FROM play WHERE game_id = 1").use { c -> c.moveToFirst(); assertEquals(1_500L, c.getLong(0)) }
        db.query("SELECT game_id, chosen_at FROM copy_choice").use { c -> c.moveToFirst(); assertEquals(listOf(2L, 60L), listOf(c.getLong(0), c.getLong(1))) }
    }

    private companion object {
        const val DB_NAME = "migration-7-8.db"

        /** Schema 6's tables (6.json), what 0.0.2-alpha leaves on a device. */
        val VERSION_6_TABLES =
            setOf(
                "system", "rom_folder", "scanned_directory", "game", "media", "play", "play_session", "emulator_choice", "name_fix",
                "scraped_game", "pinned_shortcut",
            )
    }
}
