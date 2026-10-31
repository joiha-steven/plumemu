package com.plumemu.coredata.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema 10 to 11 (installed apps as games): the empty `app_game` and `app_kind_choice`
 * tables appear; a scanned game, a pinned shortcut game, their play time and marks keep every value;
 * the migration can run again; the result opens with Room and lists an app game.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlumMigration10To11Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    private fun fill(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo'), ('steam', 'Valve Steam')")
        db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES (1, 'content://tree/1', 'ROMs', 10, 20)")
        db.execSQL(
            "INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) " +
                "VALUES (2, '" + PinnedShortcutDao.FOLDER_TREE_URI + "', '', 30, NULL)",
        )
        val insertGame =
            "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, " +
                "modified_at, title, sort_name, regions, disc_group, disc_count, system_id, is_primary_copy, file_title, " +
                "file_sort_name, file_regions, name_trust, id_method, needs_identify, is_hidden, is_favorite, is_pending, fix_keys, is_not_game) " +
                "VALUES (?, ?, ?, 'root', ?, ?, 1024, 99, ?, ?, '', NULL, 1, ?, 1, ?, ?, '', 'FILE_NAME', NULL, 0, ?, ?, 0, '', 0)"
        db.execSQL(insertGame, arrayOf<Any?>(1, 1, "doc1", "Zork.sfc", "Zork.sfc", "Zork Quest", "zork quest", "snes", "Zork Quest", "zork quest", 0, 1))
        db.execSQL(insertGame, arrayOf<Any?>(2, 2, "shortcut:0/app.gamenative/game_123", "", "Broforce", "Broforce", "broforce", "steam", "Broforce", "broforce", 1, 0))
        db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES (1, 70000, 1500), (2, 80000, 60)")
        db.execSQL(
            "INSERT INTO pinned_shortcut (game_id, package_name, shortcut_id, user_serial, label, added_at, is_available) " +
                "VALUES (2, 'app.gamenative', 'game_123', 0, 'Broforce', 40, 1)",
        )
    }

    private fun rows(db: SupportSQLiteDatabase, sql: String): List<List<String?>> =
        db.query(sql).use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
        }

    private fun everything(db: SupportSQLiteDatabase) =
        listOf(
            rows(db, "SELECT * FROM system ORDER BY id"),
            rows(db, "SELECT * FROM rom_folder ORDER BY id"),
            rows(db, "SELECT * FROM game ORDER BY id"),
            rows(db, "SELECT * FROM play ORDER BY game_id"),
            rows(db, "SELECT * FROM pinned_shortcut ORDER BY game_id"),
        )

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun version10To11AddsEmptyAppTablesAndKeepsGamesShortcutsAndPlayTime() {
        val before = helper.createDatabase(DB_NAME, 10).use { db ->
            fill(db)
            everything(db)
        }
        helper.runMigrationsAndValidate(DB_NAME, 11, true, PlumMigrations.MIGRATION_10_11).use { db ->
            assertEquals(before, everything(db))
            assertEquals(0, count(db, "app_game"))
            assertEquals(0, count(db, "app_kind_choice"))
        }
    }

    @Test
    fun version10To11CanRunAgainOnAFileItAlreadyMigrated() {
        helper.createDatabase(DB_NAME, 10).use(::fill)
        val db = helper.runMigrationsAndValidate(DB_NAME, 11, true, PlumMigrations.MIGRATION_10_11)
        db.execSQL("INSERT INTO app_kind_choice (package_name, kind, chosen_at) VALUES ('com.example.game', 'game', 5)")
        PlumMigrations.MIGRATION_10_11.migrate(db)
        assertEquals(1, count(db, "app_kind_choice"))
        db.close()
    }

    @Test
    fun aMigratedLibraryOpensAndListsAnAppGameNextToTheOldRows() = runTest {
        helper.createDatabase(DB_NAME, 10).use(::fill)
        val room =
            Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, DB_NAME)
                .addMigrations(*PlumMigrations.ALL)
                .allowMainThreadQueries()
                .build()
        val sync = room.appGameDao().sync(listOf(NewAppGame("com.example.game", "Example", "example", "android", "Android games")), now = 90)
        assertEquals(AppGameSync(added = 1, updated = 0, removed = 0), sync)
        assertEquals("Zork Quest", room.gameDao().game(1)?.title)
        assertTrue(room.gameDao().game(1)!!.isFavorite)
        assertEquals("app.gamenative", room.pinnedShortcutDao().byGame(2)?.packageName)
        assertEquals(listOf(1L), room.scanDao().folders().map { it.id })
        room.close()
    }

    private companion object {
        const val DB_NAME = "migration-10-11.db"
    }
}
