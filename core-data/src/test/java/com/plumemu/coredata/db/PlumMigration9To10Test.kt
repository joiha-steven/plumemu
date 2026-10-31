package com.plumemu.coredata.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema 9 to 10 (box art's retry cap): the empty `scrape_failure` table appears; a stored box-art
 * result and a game keep every value; the migration can run again; the result opens with Room and
 * counts a failed day.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlumMigration9To10Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    private fun fill(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
        db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES (1, 'content://tree/1', 'ROMs', 10, 20)")
        db.execSQL(
            "INSERT INTO scraped_game (scrape_key, game_id, outcome, screenscraper_id, matched_by, is_person_choice, scraped_at, details_json, " +
                "title, year, publisher, players, genre, pictures, has_box_front, support_missing, support_attempts, ask_again) " +
                "VALUES ('crc:1234', NULL, 'MATCHED', '5678', 'HASH', 1, 60, '{}', 'Zork Quest', 1994, 'Infocom', '1', 'Adventure', '[]', 1, 0, 2, 0)",
        )
    }

    private fun scrapedRows(db: SupportSQLiteDatabase): List<List<String?>> =
        db.query("SELECT scrape_key, screenscraper_id, title, year, pictures, has_box_front, support_attempts FROM scraped_game").use { c ->
            buildList { while (c.moveToNext()) add((0 until 7).map { if (c.isNull(it)) null else c.getString(it) }) }
        }

    private fun tables(db: SupportSQLiteDatabase): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT IN ('android_metadata', 'room_master_table', 'sqlite_sequence')").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    @Test
    fun version9To10AddsAnEmptyFailureTableAndKeepsEveryStoredResult() {
        val before = helper.createDatabase(DB_NAME, 9).use { db ->
            fill(db)
            scrapedRows(db)
        }
        helper.runMigrationsAndValidate(DB_NAME, 10, true, PlumMigrations.MIGRATION_9_10).use { db ->
            assertEquals(true, "scrape_failure" in tables(db))
            assertEquals(before, scrapedRows(db))
            assertEquals(0, db.query("SELECT COUNT(*) FROM scrape_failure").use { it.moveToFirst(); it.getInt(0) })
        }
    }

    @Test
    fun version9To10CanRunAgainOnAFileItAlreadyMigrated() {
        helper.createDatabase(DB_NAME, 9).use(::fill)
        val db = helper.runMigrationsAndValidate(DB_NAME, 10, true, PlumMigrations.MIGRATION_9_10)
        db.execSQL("INSERT INTO scrape_failure (scrape_key, failed_days, last_day) VALUES ('crc:1234', 2, 20001)")
        PlumMigrations.MIGRATION_9_10.migrate(db)
        assertEquals(2, db.query("SELECT failed_days FROM scrape_failure").use { it.moveToFirst(); it.getInt(0) })
        db.close()
    }

    @Test
    fun aMigratedLibraryOpensAndCountsAFailedDay() = runTest {
        helper.createDatabase(DB_NAME, 9).use(::fill)
        val room =
            Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, DB_NAME)
                .addMigrations(*PlumMigrations.ALL)
                .allowMainThreadQueries()
                .build()
        assertEquals("Zork Quest", room.scrapedGameDao().get("crc:1234")?.title)
        room.scrapedGameDao().recordFailure("crc:1234", 20_000)
        assertEquals(1, room.scrapedGameDao().failure("crc:1234")?.failedDays)
        room.close()
    }

    private companion object {
        const val DB_NAME = "migration-9-10.db"
    }
}
