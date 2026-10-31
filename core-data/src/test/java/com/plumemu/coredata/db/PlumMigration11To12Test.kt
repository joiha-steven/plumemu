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
 * Schema 11 to 12 (Remove from Recent): `play.recent_removed` appears, 0 on every row,
 * so every played game stays in Recent; every other value is kept; the migration can run again;
 * the result opens with Room and Recent lists the old plays.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlumMigration11To12Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    private fun fill(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
        db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES (1, 'content://tree/1', 'ROMs', 10, 20)")
        val insertGame =
            "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, " +
                "modified_at, title, sort_name, regions, disc_group, disc_count, system_id, is_primary_copy, file_title, " +
                "file_sort_name, file_regions, name_trust, id_method, needs_identify, is_hidden, is_favorite, is_pending, fix_keys, is_not_game) " +
                "VALUES (?, 1, ?, 'root', ?, ?, 1024, 99, ?, ?, '', NULL, 1, 'snes', 1, ?, ?, '', 'FILE_NAME', NULL, 0, 0, ?, 0, '', 0)"
        db.execSQL(insertGame, arrayOf<Any?>(1, "doc1", "Zork.sfc", "Zork.sfc", "Zork Quest", "zork quest", "Zork Quest", "zork quest", 1))
        db.execSQL(insertGame, arrayOf<Any?>(2, "doc2", "Mana.sfc", "Mana.sfc", "Mana", "mana", "Mana", "mana", 0))
        db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES (1, 70000, 1500), (2, 80000, 60)")
        db.execSQL("INSERT INTO play_session (game_id, system_id, started_at, ended_at, seconds) VALUES (1, 'snes', 68500, 70000, 1500)")
    }

    private fun rows(db: SupportSQLiteDatabase, sql: String): List<List<String?>> =
        db.query(sql).use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
        }

    private fun everythingButPlay(db: SupportSQLiteDatabase) =
        listOf(
            rows(db, "SELECT * FROM system ORDER BY id"),
            rows(db, "SELECT * FROM rom_folder ORDER BY id"),
            rows(db, "SELECT * FROM game ORDER BY id"),
            rows(db, "SELECT * FROM play_session ORDER BY id"),
        )

    @Test
    fun version11To12AddsRecentRemovedAsZeroAndKeepsEveryValue() {
        val before = helper.createDatabase(DB_NAME, 11).use { db ->
            fill(db)
            everythingButPlay(db)
        }
        helper.runMigrationsAndValidate(DB_NAME, 12, true, PlumMigrations.MIGRATION_11_12).use { db ->
            assertEquals(before, everythingButPlay(db))
            assertEquals(
                listOf(listOf("1", "70000", "1500", "0"), listOf("2", "80000", "60", "0")),
                rows(db, "SELECT game_id, last_played_at, total_seconds, recent_removed FROM play ORDER BY game_id"),
            )
        }
    }

    @Test
    fun version11To12CanRunAgainOnAFileItAlreadyMigrated() {
        helper.createDatabase(DB_NAME, 11).use(::fill)
        val db = helper.runMigrationsAndValidate(DB_NAME, 12, true, PlumMigrations.MIGRATION_11_12)
        db.execSQL("UPDATE play SET recent_removed = 1 WHERE game_id = 2")
        PlumMigrations.MIGRATION_11_12.migrate(db)
        assertEquals(listOf(listOf("0"), listOf("1")), rows(db, "SELECT recent_removed FROM play ORDER BY game_id"))
        db.close()
    }

    @Test
    fun aMigratedLibraryOpensAndRecentListsTheOldPlaysNewestFirst() = runTest {
        helper.createDatabase(DB_NAME, 11).use(::fill)
        val room =
            Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, DB_NAME)
                .addMigrations(*PlumMigrations.ALL)
                .allowMainThreadQueries()
                .build()
        assertEquals(listOf(2L, 1L), room.gameDao().observeRecent(20).first().map { it.id })
        room.close()
    }

    private companion object {
        const val DB_NAME = "migration-11-12.db"
    }
}
