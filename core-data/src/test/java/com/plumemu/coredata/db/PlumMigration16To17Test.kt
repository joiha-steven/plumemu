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
 * Schema 16 to 17 (the second screen): with a row in every one of schema 16's tables, every old
 * value stays, `game.added_at` and the progress points are null on old rows, and the new
 * `ra_achievement` table is empty; the migration can run again and keeps what was stored in
 * between; the result opens with Room and counts no old game as new.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlumMigration16To17Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    /** One row (two for `game`, `play` and `ra_progress`) in each of the 20 tables schema 16 has. */
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
                    "needs_identify) VALUES ($id, 1, 'doc$id', 'root', '$name.sfc', '$name.sfc', 1024, 1700000000000, '$name', " +
                    "'${name.lowercase()}', 'USA', NULL, 1, 'snes', 1, 0, $id - 1, 'abc', 0, '$name', '${name.lowercase()}', " +
                    "'USA', 'DAT', 'CRC', 'EXACT', 'crc:123$id', 1024, 99, 7, 'crc:123$id', 0, 0)",
            )
        }
        db.execSQL("INSERT INTO media (id, game_id, kind, path) VALUES (1, 1, 'box', '/covers/zork.png')")
        db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds, recent_removed) VALUES (1, 70000, 1500, 1)")
        db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds, recent_removed) VALUES (2, 80000, 60, 0)")
        db.execSQL(
            "INSERT INTO play_session (id, game_id, system_id, started_at, ended_at, seconds, battery_seconds, charge_start_uah, " +
                "charge_end_uah, battery_draw_ua) VALUES (1, 1, 'snes', 60000, 70000, 1500, 1400, 4000000, 3800000, 500000)",
        )
        db.execSQL("INSERT INTO emulator_choice (id, system_id, game_id, emulator_id, is_person_choice) VALUES (1, 'snes', 2, 'snes9x', 1)")
        db.execSQL("INSERT INTO name_fix (fix_key, kind, title, title_source, game_id, fixed_at) VALUES ('crc:1231', 'TITLE', 'Zork I', 'PERSON', 1, 30)")
        db.execSQL(
            "INSERT INTO scraped_game (scrape_key, game_id, outcome, screenscraper_id, matched_by, is_person_choice, scraped_at, " +
                "details_json, title, year, publisher, players, genre, pictures, has_box_front, support_missing, support_attempts, " +
                "ask_again, rating) VALUES ('crc:1231', 1, 'FOUND', 42, 'CRC', 0, 40, '{}', 'Zork', 1980, 'Infocom', '1', 'Adventure', " +
                "'box', 1, 0, 0, 0, 17)",
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
        db.execSQL(
            "INSERT INTO ra_match (game_id, status, ra_game_id, md5, cannot_hash_reason, file_key, size_bytes, modified_at, checked_at) " +
                "VALUES (1, 'matched', 1446, 'd41d8cd98f00b204e9800998ecf8427e', NULL, 'Zork.sfc', 1024, 99, 5)",
        )
        db.execSQL(
            "INSERT INTO ra_progress (ra_game_id, achievements_total, awarded_softcore, awarded_hardcore, highest_award, updated_at) " +
                "VALUES (1446, 10, 10, 10, 'mastered', 6)",
        )
        db.execSQL(
            "INSERT INTO ra_progress (ra_game_id, achievements_total, awarded_softcore, awarded_hardcore, highest_award, updated_at) " +
                "VALUES (2000, 5, 1, 0, NULL, 6)",
        )
        db.execSQL(
            "INSERT INTO left_out_file (folder_id, directory_path, relative_path, reason, system_id, more) " +
                "VALUES (1, 'PS1', 'PS1/scph1001.bin', 'bios', 'psx', 0)",
        )
    }

    private fun rows(db: SupportSQLiteDatabase, sql: String): List<List<String?>> =
        db.query(sql).use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
        }

    /** Every table as schema 16 has it: the old columns only, so before and after compare value by value. */
    private fun everything(db: SupportSQLiteDatabase): Map<String, List<List<String?>>> =
        SCHEMA_16_TABLES.associateWith { table ->
            val columns = when (table) {
                "game" -> GAME_16_COLUMNS
                "ra_progress" -> "ra_game_id, achievements_total, awarded_softcore, awarded_hardcore, highest_award, updated_at"
                else -> "*"
            }
            rows(db, "SELECT $columns FROM $table ORDER BY 1, 2")
        }

    @Test
    fun version16To17KeepsEveryValueAndLeavesTheNewColumnsNullAndTheNewTableEmpty() {
        val before = helper.createDatabase(DB_NAME, 16).use { db ->
            fill(db)
            // The compared columns are every column schema 16 has.
            assertEquals(GAME_16_COLUMNS.split(", ").toSet(), rows(db, "PRAGMA table_info(game)").map { it[1] }.toSet())
            everything(db)
        }
        // The fill really put a row in every table, so "kept" below means something.
        assertTrue(before.filterValues { it.isEmpty() }.keys.toString(), before.values.all { it.isNotEmpty() })
        helper.runMigrationsAndValidate(DB_NAME, 17, true, PlumMigrations.MIGRATION_16_17).use { db ->
            assertEquals(before, everything(db))
            assertEquals("old games get no invented time", listOf(listOf("1", null), listOf("2", null)), rows(db, "SELECT id, added_at FROM game ORDER BY id"))
            assertEquals(
                "old progress has no points yet",
                listOf(listOf("1446", null, null), listOf("2000", null, null)),
                rows(db, "SELECT ra_game_id, points_earned, points_total FROM ra_progress ORDER BY 1"),
            )
            assertEquals(emptyList<List<String?>>(), rows(db, "SELECT * FROM ra_achievement"))
        }
    }

    @Test
    fun version16To17CanRunAgainAndKeepsWhatWasStoredInBetween() {
        helper.createDatabase(DB_NAME, 16).use(::fill)
        val db = helper.runMigrationsAndValidate(DB_NAME, 17, true, PlumMigrations.MIGRATION_16_17)
        db.execSQL("UPDATE game SET added_at = 1800000000000 WHERE id = 2")
        db.execSQL("UPDATE ra_progress SET points_earned = 50, points_total = 100 WHERE ra_game_id = 1446")
        db.execSQL(
            "INSERT INTO ra_achievement (ra_game_id, achievement_id, title, badge_name, points, earned_at, earned_hardcore_at) " +
                "VALUES (1446, 7, 'Win', '250336', 10, 1714557600000, NULL)",
        )
        val before = everything(db) + mapOf(
            "new" to rows(db, "SELECT id, added_at FROM game ORDER BY id") +
                rows(db, "SELECT ra_game_id, points_earned, points_total FROM ra_progress ORDER BY 1") +
                rows(db, "SELECT * FROM ra_achievement"),
        )
        PlumMigrations.MIGRATION_16_17.migrate(db)
        val after = everything(db) + mapOf(
            "new" to rows(db, "SELECT id, added_at FROM game ORDER BY id") +
                rows(db, "SELECT ra_game_id, points_earned, points_total FROM ra_progress ORDER BY 1") +
                rows(db, "SELECT * FROM ra_achievement"),
        )
        assertEquals(before, after)
        db.close()
    }

    @Test
    fun aMigratedLibraryOpensAndCountsNoOldGameAsNew() = runTest {
        helper.createDatabase(DB_NAME, 16).use(::fill)
        val room =
            Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, DB_NAME)
                .addMigrations(*PlumMigrations.all(TestDetailsRating))
                .allowMainThreadQueries()
                .build()
        assertEquals(2, room.scanDao().gamesInFolder(1).size)
        assertEquals(0, room.gameDao().observeAddedSince("snes", 0).first())
        assertNull(room.gameDao().game(1)?.addedAt)
        assertEquals(1446L, room.raDao().match(1)?.raGameId)
        assertEquals(RaSystemTotals(masteredGames = 1, gamesWithProgress = 1, gamesWithPoints = 0, pointsEarned = 0), room.raDao().observeSystemTotals("snes").first())
        assertEquals(emptyList<RaAchievementEntity>(), room.raDao().observeRecentAchievements(1446, 5).first())
        room.close()
    }

    @Test
    fun theFirstScanAfterTheUpgradeIsALaterScanAndCountsOnlyTheFileItAdds() = runTest {
        helper.createDatabase(DB_NAME, 16).use(::fill)
        val room =
            Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, DB_NAME)
                .addMigrations(*PlumMigrations.all(TestDetailsRating))
                .allowMainThreadQueries()
                .build()
        val now = 1_800_000_000_000L
        val weekAgo = now - 7L * 24 * 60 * 60 * 1000
        assertEquals("no old game counts as new", 0, room.gameDao().observeAddedSince("snes", weekAgo).first())
        // The library has scanned games, so library's scanner stamps this scan (LibraryScanner.scanAll).
        assertTrue(room.scanDao().hasScannedGames())
        val scan = room.scanDao()
        scan.applyBatch(ScanBatch(1, newGames = listOf(game(1, "doc3", title = "Doom")), addedAt = now))
        // The same old files found again: ignored inserts, their rows keep no time.
        scan.applyBatch(ScanBatch(1, newGames = listOf(game(1, "doc1"), game(1, "doc2")), addedAt = now))
        scan.finishScan(ScanFinish(1))

        assertEquals(1, room.gameDao().observeAddedSince("snes", weekAgo).first())
        assertEquals(listOf(null, null, now), scan.gamesInFolder(1).sortedBy { it.id }.map { it.addedAt })
        room.close()
    }

    private companion object {
        const val DB_NAME = "migration-16-17.db"

        const val GAME_16_COLUMNS =
            "id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, modified_at, title, sort_name, " +
                "regions, disc_group, disc_count, system_id, is_primary_copy, is_hidden, is_favorite, hash, is_pending, file_title, " +
                "file_sort_name, file_regions, name_trust, id_method, id_confidence, fingerprint, fingerprint_size, fingerprint_mtime, " +
                "pack_version, fix_keys, is_not_game, needs_identify"

        val SCHEMA_16_TABLES = listOf(
            "system", "rom_folder", "scanned_directory", "game", "media", "play", "play_session", "emulator_choice",
            "name_fix", "scraped_game", "pinned_shortcut", "copy_choice", "folder_system", "scrape_failure", "app_game",
            "app_kind_choice", "skipped_subfolder", "ra_match", "ra_progress", "left_out_file",
        )
    }
}
