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
 * Schema 13 to 14 (RetroAchievements): with a row in every one of schema 13's tables, every value
 * stays and the two new tables are empty; the migration can run again and keeps what was stored in
 * between; the result opens with Room, serves the library and has no match or progress.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlumMigration13To14Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    /** One row (two for `game`) in each of the 17 tables schema 13 has. */
    private fun fill(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
        db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES (1, 'content://tree/1', 'ROMs', 10, 20)")
        db.execSQL("INSERT INTO scanned_directory (folder_id, document_id, relative_path, last_modified, unknown_files) VALUES (1, 'root', '', 500, 2)")
        for ((id, name) in listOf(1 to "Zork", 2 to "Mario")) {
            db.execSQL(
                "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, " +
                    "modified_at, title, sort_name, regions, disc_group, disc_count, system_id, is_primary_copy, is_hidden, " +
                    "is_favorite, hash, is_pending, file_title, file_sort_name, file_regions, name_trust, id_method, " +
                    "id_confidence, fingerprint, fingerprint_size, fingerprint_mtime, pack_version, fix_keys, is_not_game, " +
                    "needs_identify) VALUES ($id, 1, 'doc$id', 'root', '$name.sfc', '$name.sfc', 1024, 99, '$name', " +
                    "'${name.lowercase()}', 'USA', NULL, 1, 'snes', 1, 0, $id - 1, 'abc', 0, '$name', '${name.lowercase()}', " +
                    "'USA', 'DAT', 'CRC', 'EXACT', 'crc:1234', 1024, 99, 7, 'crc:1234', 0, 0)",
            )
        }
        db.execSQL("INSERT INTO media (id, game_id, kind, path) VALUES (1, 1, 'box', '/covers/zork.png')")
        db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds, recent_removed) VALUES (1, 70000, 1500, 1)")
        db.execSQL(
            "INSERT INTO play_session (id, game_id, system_id, started_at, ended_at, seconds, battery_seconds, charge_start_uah, " +
                "charge_end_uah, battery_draw_ua) VALUES (1, 1, 'snes', 60000, 70000, 1500, 1400, 4000000, 3800000, 500000)",
        )
        db.execSQL("INSERT INTO emulator_choice (id, system_id, game_id, emulator_id, is_person_choice) VALUES (1, 'snes', 2, 'snes9x', 1)")
        db.execSQL("INSERT INTO name_fix (fix_key, kind, title, title_source, game_id, fixed_at) VALUES ('crc:1234', 'TITLE', 'Zork I', 'PERSON', 1, 30)")
        db.execSQL(
            "INSERT INTO scraped_game (scrape_key, game_id, outcome, screenscraper_id, matched_by, is_person_choice, scraped_at, " +
                "details_json, title, year, publisher, players, genre, pictures, has_box_front, support_missing, support_attempts, " +
                "ask_again) VALUES ('crc:1234', 1, 'FOUND', 42, 'CRC', 0, 40, '{}', 'Zork', 1980, 'Infocom', '1', 'Adventure', " +
                "'box', 1, 0, 0, 0)",
        )
        db.execSQL(
            "INSERT INTO pinned_shortcut (game_id, package_name, shortcut_id, user_serial, label, added_at, is_available) " +
                "VALUES (2, 'com.example', 's1', 0, 'Mario', 50, 1)",
        )
        db.execSQL("INSERT INTO copy_choice (game_id, chosen_at) VALUES (2, 60)")
        db.execSQL("INSERT INTO folder_system (folder_id, relative_path, system_id, chosen_at) VALUES (1, 'Xbox', NULL, 5)")
        db.execSQL("INSERT INTO scrape_failure (scrape_key, failed_days, last_day) VALUES ('game:2', 2, 20000)")
        db.execSQL("INSERT INTO app_game (game_id, package_name, added_at) VALUES (2, 'com.example.game', 70)")
        db.execSQL("INSERT INTO app_kind_choice (package_name, kind, chosen_at) VALUES ('com.example.port', 'PORT', 80)")
        db.execSQL("INSERT INTO skipped_subfolder (folder_id, relative_path, skipped_at) VALUES (1, 'Hacks', 9)")
    }

    private fun rows(db: SupportSQLiteDatabase, sql: String): List<List<String?>> =
        db.query(sql).use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
        }

    private fun everything(db: SupportSQLiteDatabase): Map<String, List<List<String?>>> =
        SCHEMA_13_TABLES.associateWith { rows(db, "SELECT * FROM $it ORDER BY 1, 2") }

    @Test
    fun version13To14AddsTwoEmptyTablesAndKeepsEveryValueOfEveryTable() {
        val before = helper.createDatabase(DB_NAME, 13).use { db ->
            fill(db)
            everything(db)
        }
        // The fill really put a row in every table, so "kept" below means something.
        assertTrue(before.filterValues { it.isEmpty() }.keys.toString(), before.values.all { it.isNotEmpty() })
        helper.runMigrationsAndValidate(DB_NAME, 14, true, PlumMigrations.MIGRATION_13_14).use { db ->
            assertEquals(before, everything(db))
            assertEquals(emptyList<List<String?>>(), rows(db, "SELECT * FROM ra_match"))
            assertEquals(emptyList<List<String?>>(), rows(db, "SELECT * FROM ra_progress"))
        }
    }

    @Test
    fun version13To14CanRunAgainAndKeepsWhatWasStoredInBetween() {
        helper.createDatabase(DB_NAME, 13).use(::fill)
        val db = helper.runMigrationsAndValidate(DB_NAME, 14, true, PlumMigrations.MIGRATION_13_14)
        db.execSQL(
            "INSERT INTO ra_match (game_id, status, ra_game_id, md5, cannot_hash_reason, file_key, size_bytes, modified_at, checked_at) " +
                "VALUES (1, 'matched', 1446, 'd41d8cd98f00b204e9800998ecf8427e', NULL, 'Zork.sfc', 1024, 99, 5)",
        )
        db.execSQL(
            "INSERT INTO ra_progress (ra_game_id, achievements_total, awarded_softcore, awarded_hardcore, highest_award, updated_at) " +
                "VALUES (1446, 10, 10, 10, 'mastered', 6)",
        )
        val before = everything(db)
        PlumMigrations.MIGRATION_13_14.migrate(db)
        assertEquals(before, everything(db))
        assertEquals(1, rows(db, "SELECT * FROM ra_match").size)
        assertEquals(1, rows(db, "SELECT * FROM ra_progress").size)
        db.close()
    }

    @Test
    fun aMigratedLibraryOpensWithItsGamesAndNoAchievementData() = runTest {
        helper.createDatabase(DB_NAME, 13).use(::fill)
        val room =
            Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, DB_NAME)
                .addMigrations(*PlumMigrations.ALL)
                .allowMainThreadQueries()
                .build()
        assertEquals(2, room.scanDao().gamesInFolder(1).size)
        assertEquals(emptySet<Long>(), room.raDao().masteredGameIds().first())
        assertNull(room.raDao().match(1))
        room.close()
    }

    @Test
    fun aMigrationThatFailsLeavesVersion13WithEveryValue() {
        val before = helper.createDatabase(DB_NAME, 13).use { db ->
            fill(db)
            // A stray table of the same name and another shape: the new schema cannot be valid.
            db.execSQL("CREATE TABLE ra_progress (something TEXT)")
            everything(db)
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val room = Room.databaseBuilder(context, PlumDatabase::class.java, DB_NAME).addMigrations(*PlumMigrations.ALL).build()
        val failure = runCatching { room.openHelper.writableDatabase }.exceptionOrNull()
        room.close()
        assertTrue("the open should fail, got $failure", failure is IllegalStateException)
        android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath(DB_NAME).path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        ).use { raw ->
            assertEquals(13, raw.version)
            val after = SCHEMA_13_TABLES.associateWith { table ->
                raw.rawQuery("SELECT * FROM $table ORDER BY 1, 2", null).use { c ->
                    buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
                }
            }
            assertEquals(before, after)
            assertEquals(0, raw.rawQuery("SELECT name FROM sqlite_master WHERE name = 'ra_match'", null).use { it.count })
        }
    }

    private companion object {
        const val DB_NAME = "migration-13-14.db"

        val SCHEMA_13_TABLES = listOf(
            "system", "rom_folder", "scanned_directory", "game", "media", "play", "play_session", "emulator_choice",
            "name_fix", "scraped_game", "pinned_shortcut", "copy_choice", "folder_system", "scrape_failure", "app_game",
            "app_kind_choice", "skipped_subfolder",
        )
    }
}
