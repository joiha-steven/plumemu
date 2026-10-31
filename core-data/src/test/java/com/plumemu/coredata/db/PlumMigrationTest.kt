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
 * Migrations on databases filled the way a test device's is: folders, games with favourites and
 * hidden marks, play time, emulator choices. Each version's test creates the previous version
 * from its committed schema JSON, migrates, lets Room validate the result against the entities,
 * and checks every row the person made is still there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlumMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    @Test
    fun version1FromSchemaJsonOpensWithTheCurrentEntities() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
        }
        val room = openRoom()
        // Room checks the identity hash of the file against the entities when it opens.
        room.openHelper.readableDatabase.query("SELECT name FROM system").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Super Nintendo", cursor.getString(0))
        }
        room.close()
    }

    @Test
    fun version1To2KeepsEveryPersonRowAndCopiesTheFileName() {
        helper.createDatabase(DB_NAME, 1).use(::fillVersion1)
        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, PlumMigrations.MIGRATION_1_2)
        assertPersonDataKept(db)
        db.query("SELECT file_title, file_sort_name, file_regions, title, name_trust, needs_identify, is_not_game, fix_keys, fingerprint, pack_version FROM game WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("Zork Quest", c.getString(0))
            assertEquals("zork quest", c.getString(1))
            assertEquals("USA,EUR", c.getString(2))
            assertEquals("Zork Quest", c.getString(3))
            assertEquals(NameTrustNames.FILE_NAME, c.getString(4))
            assertEquals(1, c.getInt(5))
            assertEquals(0, c.getInt(6))
            assertEquals("", c.getString(7))
            assertTrue(c.isNull(8))
            assertTrue("not named with any pack yet", c.isNull(9))
        }
        db.query("SELECT COUNT(*) FROM name_fix").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun version1To2CanRunAgainOnAFileItAlreadyMigrated() {
        helper.createDatabase(DB_NAME, 1).use(::fillVersion1)
        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, PlumMigrations.MIGRATION_1_2)
        db.execSQL("UPDATE game SET title = 'Identified', name_trust = 'IDENTIFIER_EXACT' WHERE id = 1")
        PlumMigrations.MIGRATION_1_2.migrate(db)
        assertPersonDataKept(db)
        db.query("SELECT title, file_title, name_trust FROM game WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("Identified", c.getString(0))
            assertEquals("Zork Quest", c.getString(1))
            assertEquals("IDENTIFIER_EXACT", c.getString(2))
        }
        db.close()
    }

    @Test
    fun version2To3KeepsEveryRowAndAddsAnEmptyScrapedGameTable() {
        createVersion2()
        val db = helper.runMigrationsAndValidate(DB_NAME, 3, true, PlumMigrations.MIGRATION_2_3)
        assertPersonDataKept(db)
        assertVersion2NamesKept(db)
        db.query("SELECT COUNT(*) FROM scraped_game").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun version2To3CanRunAgainAndKeepsStoredResults() {
        createVersion2()
        val db = helper.runMigrationsAndValidate(DB_NAME, 3, true, PlumMigrations.MIGRATION_2_3)
        db.execSQL(
            "INSERT INTO scraped_game (scrape_key, game_id, outcome, screenscraper_id, matched_by, scraped_at, details_json, " +
                "title, year, publisher, players, genre) VALUES ('crc:0badf00d:1024', NULL, 'MATCHED', '1245', 'HASH', 50, " +
                "'{}', 'Zork Quest', 1991, 'Zork Soft', '1-2', 'Adventure')",
        )
        PlumMigrations.MIGRATION_2_3.migrate(db)
        assertPersonDataKept(db)
        db.query("SELECT screenscraper_id, title, pictures, has_box_front, support_missing, is_person_choice, support_attempts FROM scraped_game").use { c ->
            c.moveToFirst()
            assertEquals(listOf("1245", "Zork Quest", "", 0, 0, 0, 0), listOf(c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6)))
        }
        db.close()
    }

    @Test
    fun version3To4KeepsEveryStoredResultAndAsksNothingAgain() {
        createVersion3WithAResult()
        val db = helper.runMigrationsAndValidate(DB_NAME, 4, true, PlumMigrations.MIGRATION_3_4)
        assertPersonDataKept(db)
        assertVersion2NamesKept(db)
        assertStoredResultKept(db)
        db.close()
    }

    @Test
    fun version3To4CanRunAgainOnAFileItAlreadyMigrated() {
        createVersion3WithAResult()
        val db = helper.runMigrationsAndValidate(DB_NAME, 4, true, PlumMigrations.MIGRATION_3_4)
        PlumMigrations.MIGRATION_3_4.migrate(db)
        assertPersonDataKept(db)
        assertStoredResultKept(db)
        db.close()
    }

    @Test
    fun version4To5KeepsEveryRowAndTotalAndAddsAnEmptyUsableSessionTable() = runTest {
        createVersion4()
        helper.runMigrationsAndValidate(DB_NAME, 5, true, PlumMigrations.MIGRATION_4_5).use { db ->
            assertPersonDataKept(db)
            assertVersion2NamesKept(db)
            assertStoredResultKept(db)
            assertEquals(0, countOf(db, "play_session"))
        }
        val room = openRoom()
        assertEquals("no session is made up from the old total", false, room.playDao().hasAnySession())
        assertEquals(3_600L, room.playDao().totalSeconds())
        room.playDao().addSession(2, endedAt = 50_000, seconds = 20)
        assertEquals(listOf(Triple(2L, "snes", 20L)), room.playDao().sessions(2).map { Triple(it.gameId, it.systemId, it.seconds) })
        assertEquals(20L, room.playDao().secondsPlayedBetween(0, 100_000))
        assertEquals("the old total is only added to", 3_620L, room.playDao().totalSeconds())
        assertEquals(PlayEntity(1, lastPlayedAt = 1_000, totalSeconds = 3_600), room.playDao().play(1))
        room.close()
    }

    @Test
    fun version4To5CanRunAgainOnAFileItAlreadyMigrated() {
        createVersion4()
        val db = helper.runMigrationsAndValidate(DB_NAME, 5, true, PlumMigrations.MIGRATION_4_5)
        db.execSQL("INSERT INTO play_session (game_id, system_id, started_at, ended_at, seconds) VALUES (1, 'snes', 10, 20, 10)")
        PlumMigrations.MIGRATION_4_5.migrate(db)
        assertPersonDataKept(db)
        assertStoredResultKept(db)
        db.query("SELECT game_id, system_id, started_at, ended_at, seconds FROM play_session").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(listOf(1L, "snes", 10L, 20L, 10L), listOf(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getLong(4)))
        }
        db.close()
    }

    @Test
    fun version2MigratesThroughEveryStepToVersion5() {
        createVersion2()
        val db = helper.runMigrationsAndValidate(
            DB_NAME, 5, true, PlumMigrations.MIGRATION_2_3, PlumMigrations.MIGRATION_3_4, PlumMigrations.MIGRATION_4_5,
        )
        assertPersonDataKept(db)
        assertVersion2NamesKept(db)
        assertEquals(0, countOf(db, "scraped_game"))
        assertEquals(0, countOf(db, "play_session"))
        db.close()
    }

    @Test
    fun version5To6KeepsEveryRowAndAddsAnEmptyUsableShortcutTable() = runTest {
        createVersion5WithASession()
        helper.runMigrationsAndValidate(DB_NAME, 6, true, PlumMigrations.MIGRATION_5_6).use { db ->
            assertPersonDataKept(db)
            assertVersion2NamesKept(db)
            assertStoredResultKept(db)
            assertSessionKept(db)
            assertEquals(0, countOf(db, "pinned_shortcut"))
            assertEquals("no folder is added by the migration", 1, countOf(db, "rom_folder"))
        }
        val room = openRoom()
        assertEquals(listOf("Zork Quest Deluxe", "Zork Quest II"), room.gameDao().observeSystemGames("snes").first().map { it.title })
        assertEquals(3_600L, room.playDao().totalSeconds())
        assertEquals(30L, room.playDao().secondsPlayedBetween(0, 100_000))
        val added = room.pinnedShortcutDao().add(shortcut())
        assertEquals("game_123", room.pinnedShortcutDao().byGame(added.gameId)?.shortcutId)
        assertEquals("the picked folder only", listOf(1L), room.scanDao().folders().map { it.id })
        room.close()
    }

    @Test
    fun version5To6CanRunAgainOnAFileItAlreadyMigrated() {
        createVersion5WithASession()
        val db = helper.runMigrationsAndValidate(DB_NAME, 6, true, PlumMigrations.MIGRATION_5_6)
        db.execSQL(
            "INSERT INTO pinned_shortcut (game_id, package_name, shortcut_id, user_serial, label, added_at) " +
                "VALUES (2, 'app.gamenative', 'game_123', 0, 'Broforce', 40)",
        )
        PlumMigrations.MIGRATION_5_6.migrate(db)
        assertPersonDataKept(db)
        assertSessionKept(db)
        db.query("SELECT game_id, package_name, shortcut_id, label, is_available FROM pinned_shortcut").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(listOf(2L, "app.gamenative", "game_123", "Broforce", 1), listOf(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4)))
        }
        db.close()
    }

    @Test
    fun version2MigratesThroughEveryStepToVersion6() {
        createVersion2()
        val db = helper.runMigrationsAndValidate(
            DB_NAME, 6, true,
            PlumMigrations.MIGRATION_2_3, PlumMigrations.MIGRATION_3_4, PlumMigrations.MIGRATION_4_5, PlumMigrations.MIGRATION_5_6,
        )
        assertPersonDataKept(db)
        assertVersion2NamesKept(db)
        assertEquals(0, countOf(db, "scraped_game"))
        assertEquals(0, countOf(db, "play_session"))
        assertEquals(0, countOf(db, "pinned_shortcut"))
        db.close()
    }

    @Test
    fun version6To7KeepsEveryRowAndAddsAnEmptyUsableCopyChoiceTable() = runTest {
        createVersion6WithAShortcut()
        helper.runMigrationsAndValidate(DB_NAME, 7, true, PlumMigrations.MIGRATION_6_7).use { db ->
            assertPersonDataKept(db)
            assertVersion2NamesKept(db)
            assertStoredResultKept(db)
            assertSessionKept(db)
            assertEquals(1, countOf(db, "pinned_shortcut"))
            assertEquals("no row plays another copy after the update", 0, countOf(db, "copy_choice"))
        }
        val room = openRoom()
        assertEquals(listOf("Zork Quest Deluxe", "Zork Quest II"), room.gameDao().observeSystemGames("snes").first().map { it.title })
        assertEquals(3_600L, room.playDao().totalSeconds())
        val copies = room.copyChoiceDao()
        copies.choose(shownId = 1, chosenId = 2, copies = listOf(1, 2), at = 50)
        assertEquals(listOf(CopyChoice(2, 50)), copies.choicesAmong(listOf(1, 2)))
        // The chosen copy's row going (its file removed) takes the choice with it.
        room.openHelper.writableDatabase.execSQL("DELETE FROM game WHERE id = 2")
        assertEquals(emptyList<CopyChoice>(), copies.choicesAmong(listOf(1, 2)))
        room.close()
    }

    @Test
    fun version6To7CanRunAgainOnAFileItAlreadyMigrated() {
        createVersion6WithAShortcut()
        val db = helper.runMigrationsAndValidate(DB_NAME, 7, true, PlumMigrations.MIGRATION_6_7)
        db.execSQL("INSERT INTO copy_choice (game_id, chosen_at) VALUES (2, 60)")
        PlumMigrations.MIGRATION_6_7.migrate(db)
        assertPersonDataKept(db)
        assertSessionKept(db)
        db.query("SELECT game_id, chosen_at FROM copy_choice").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(listOf(2L, 60L), listOf(c.getLong(0), c.getLong(1)))
        }
        db.close()
    }

    /**
     * A file shaped like 0.0.2-alpha's (schema 6, created from 6.json): regional copies of a console
     * game and two arcade sets of one game, one shown, with a favourite, play time and a session.
     * It opens with every migration, keeps its rows and marks, and Other versions finds its copies.
     */
    @Test
    fun anAlphaShapedVersion6LibraryOpensAndFindsItsCopies() = runTest {
        helper.createDatabase(DB_NAME, 6).use { db ->
            db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo'), ('arcade', 'Arcade')")
            db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES (1, 'content://tree/1', 'ROMs', 10, 20)")
            insertV6Game(db, 1, "snes", "Zork Quest (USA).sfc", "zork quest", "USA", shown = true, method = "DAT_NAME")
            insertV6Game(db, 2, "snes", "Zork Quest (Europe).sfc", "zork quest", "EUR", shown = false, method = "DAT_NAME")
            insertV6Game(db, 3, "arcade", "zorka.zip", "zorka", "", shown = true, method = "ARCADE_NAME")
            insertV6Game(db, 4, "arcade", "zorkau.zip", "zorkau", "", shown = false, method = "ARCADE_NAME")
            db.execSQL("UPDATE game SET is_favorite = 1 WHERE id = 1")
            db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES (3, 1000, 600)")
            db.execSQL("INSERT INTO play_session (game_id, system_id, started_at, ended_at, seconds) VALUES (3, 'arcade', 400, 1000, 600)")
        }
        val room = openRoom()
        assertEquals(listOf("Zork Quest (USA).sfc"), room.gameDao().observeSystemGames("snes").first().map { it.title })
        assertEquals(listOf(true), room.gameDao().observeSystemGames("snes").first().map { it.isFavorite })
        assertEquals(600L, room.playDao().play(3)?.totalSeconds)
        assertEquals(600L, room.playDao().secondsPlayedBetween(0, 2_000))
        val copies = room.copyChoiceDao()
        assertEquals(listOf(2L, 1L), copies.fileNameCopies(1).map { it.id })
        assertEquals("arcade rows are the arcade rule's, not the file name's", emptyList<Long>(), copies.fileNameCopies(3).map { it.id })
        assertEquals(setOf(3L, 4L), copies.arcadeSetsBeside(3).map { it.id }.toSet())
        assertEquals(false, copies.hasChoiceBeside(1))
        room.close()
    }

    private fun insertV6Game(db: SupportSQLiteDatabase, id: Long, system: String, file: String, sort: String, regions: String, shown: Boolean, method: String) {
        db.execSQL(
            "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, " +
                "modified_at, title, sort_name, regions, disc_group, disc_count, system_id, is_primary_copy, file_title, " +
                "file_sort_name, file_regions, name_trust, id_method, needs_identify, is_hidden, is_favorite, is_pending, fix_keys, is_not_game) " +
                "VALUES (?, 1, ?, 'root', ?, ?, 1024, 99, ?, ?, ?, NULL, 1, ?, ?, ?, ?, ?, 'IDENTIFIER_EXACT', ?, 0, 0, 0, 0, '', 0)",
            arrayOf<Any>(id, "doc$id", file, file, file, sort, regions, system, if (shown) 1 else 0, file, sort, regions, method),
        )
    }

    /** A v6 library: the v5 one with a pinned shortcut, as 0.0.2-alpha leaves a device. */
    private fun createVersion6WithAShortcut() {
        createVersion5WithASession()
        helper.runMigrationsAndValidate(DB_NAME, 6, true, PlumMigrations.MIGRATION_5_6).use { db ->
            db.execSQL(
                "INSERT INTO pinned_shortcut (game_id, package_name, shortcut_id, user_serial, label, added_at) " +
                    "VALUES (2, 'app.gamenative', 'game_123', 0, 'Broforce', 40)",
            )
        }
    }

    /** A v5 library: the v4 one with a play session of the favourite game. */
    private fun createVersion5WithASession() {
        createVersion4()
        helper.runMigrationsAndValidate(DB_NAME, 5, true, PlumMigrations.MIGRATION_4_5).use { db ->
            db.execSQL("INSERT INTO play_session (game_id, system_id, started_at, ended_at, seconds) VALUES (1, 'snes', 40000, 70000, 30)")
        }
    }

    private fun assertSessionKept(db: SupportSQLiteDatabase) {
        db.query("SELECT game_id, system_id, started_at, ended_at, seconds FROM play_session").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(listOf(1L, "snes", 40_000L, 70_000L, 30L), listOf(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getLong(4)))
        }
    }

    private fun shortcut() =
        NewPinnedShortcut(
            packageName = "app.gamenative", shortcutId = "game_123", userSerial = 0, label = "Broforce", sortName = "broforce",
            systemId = "steam", systemName = "Valve Steam", addedAt = 40,
        )

    /** A v4 library: the v3 one with its stored result, migrated and validated against 4.json. */
    private fun createVersion4() {
        createVersion3WithAResult()
        helper.runMigrationsAndValidate(DB_NAME, 4, true, PlumMigrations.MIGRATION_3_4).close()
    }

    private fun countOf(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    /** A v3 library (the v2 one) with one stored match that has its box front. */
    private fun createVersion3WithAResult() {
        createVersion2()
        helper.runMigrationsAndValidate(DB_NAME, 3, true, PlumMigrations.MIGRATION_2_3).use { db ->
            db.execSQL(
                "INSERT INTO scraped_game (scrape_key, game_id, outcome, screenscraper_id, matched_by, scraped_at, details_json, " +
                    "title, year, publisher, players, genre, pictures, has_box_front) VALUES ('crc:0badf00d:1024', NULL, 'MATCHED', '1245', " +
                    "'HASH', 50, '{}', 'Zork Quest', 1991, 'Zork Soft', '1-2', 'Adventure', '[{\"kind\":\"box-front\"}]', 1)",
            )
        }
    }

    private fun assertStoredResultKept(db: SupportSQLiteDatabase) {
        db.query("SELECT screenscraper_id, title, pictures, has_box_front, scraped_at, ask_again FROM scraped_game").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(
                listOf("1245", "Zork Quest", "[{\"kind\":\"box-front\"}]", 1, 50L, 0),
                listOf(c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getLong(4), c.getInt(5)),
            )
        }
    }

    @Test
    fun version1MigratesThroughEveryVersionAndOpensWithTheScrapedGameDao() = runTest {
        helper.createDatabase(DB_NAME, 1).use(::fillVersion1)
        val room = openRoom()
        assertNull(room.scrapedGameDao().get("crc:0badf00d:1024"))
        assertEquals(2, room.scrapedGameDao().countListed())
        room.close()
    }

    @Test
    fun migratedDatabaseServesTheListsWithMarksAndPlayTime() = runTest {
        helper.createDatabase(DB_NAME, 1).use(::fillVersion1)
        val room = openRoom()
        val games = room.gameDao().observeSystemGames("snes").first()
        assertEquals(listOf("Zork Quest", "Zork Quest II"), games.map { it.title })
        assertEquals(listOf(true, false), games.map { it.isFavorite })
        assertEquals(listOf("Hidden Zork"), room.gameDao().observeHiddenGames().first().map { it.title })
        assertEquals(3_600L, room.playDao().play(1)?.totalSeconds)
        assertEquals("snes9x", room.emulatorChoiceDao().gameChoice(1)?.emulatorId)
        assertEquals(3, room.identifyDao().countToIdentify(1))
        assertNull(room.identifyDao().fixOf(1))
        room.close()
    }

    /** A v2 library: the v1 rows migrated, one game named by the identifier and a person's fix. */
    private fun createVersion2() {
        helper.createDatabase(DB_NAME, 1).use(::fillVersion1)
        helper.runMigrationsAndValidate(DB_NAME, 2, true, PlumMigrations.MIGRATION_1_2).use { db ->
            db.execSQL("UPDATE game SET title = 'Zork Quest Deluxe', name_trust = 'IDENTIFIER_EXACT', fix_keys = 'crc:0badf00d:1024', needs_identify = 0 WHERE id = 1")
            db.execSQL("INSERT INTO name_fix (fix_key, kind, title, title_source, game_id, fixed_at) VALUES ('game:2', 'OWN_NAME', 'My Zork', NULL, 2, 30)")
        }
    }

    private fun assertVersion2NamesKept(db: SupportSQLiteDatabase) {
        db.query("SELECT title, name_trust, fix_keys FROM game WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals(listOf("Zork Quest Deluxe", "IDENTIFIER_EXACT", "crc:0badf00d:1024"), listOf(c.getString(0), c.getString(1), c.getString(2)))
        }
        db.query("SELECT fix_key, title FROM name_fix").use { c ->
            c.moveToFirst()
            assertEquals(listOf("game:2", "My Zork"), listOf(c.getString(0), c.getString(1)))
        }
    }

    private fun openRoom(): PlumDatabase =
        Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, DB_NAME)
            .addMigrations(*PlumMigrations.ALL)
            .allowMainThreadQueries()
            .build()

    /** A v1 library: one folder, three games (favourite, plain, hidden), play time, choices, media. */
    private fun fillVersion1(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
        db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES (1, 'content://tree/1', 'ROMs', 10, 20)")
        db.execSQL("INSERT INTO scanned_directory (folder_id, document_id, relative_path, last_modified) VALUES (1, 'root', '', 5)")
        insertV1Game(db, 1, "Zork Quest (USA, Europe).sfc", "Zork Quest", "zork quest", "USA,EUR", favorite = true, hidden = false)
        insertV1Game(db, 2, "Zork Quest II.sfc", "Zork Quest II", "zork quest ii", "", favorite = false, hidden = false)
        insertV1Game(db, 3, "Hidden Zork.sfc", "Hidden Zork", "hidden zork", "", favorite = false, hidden = true)
        db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES (1, 1000, 3600)")
        db.execSQL("INSERT INTO emulator_choice (system_id, game_id, emulator_id, is_person_choice) VALUES ('snes', NULL, 'retroarch-snes9x', 1)")
        db.execSQL("INSERT INTO emulator_choice (system_id, game_id, emulator_id, is_person_choice) VALUES ('snes', 1, 'snes9x', 1)")
        db.execSQL("INSERT INTO media (game_id, kind, path) VALUES (1, 'box', 'media/1.webp')")
    }

    private fun insertV1Game(db: SupportSQLiteDatabase, id: Long, file: String, title: String, sort: String, regions: String, favorite: Boolean, hidden: Boolean) {
        db.execSQL(
            "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, " +
                "modified_at, title, sort_name, regions, disc_group, disc_count, system_id, is_primary_copy, is_hidden, " +
                "is_favorite, hash, is_pending) VALUES (?, 1, ?, 'root', ?, ?, 1024, 99, ?, ?, ?, NULL, 1, 'snes', 1, ?, ?, NULL, 0)",
            arrayOf<Any>(id, "doc$id", file, file, title, sort, regions, if (hidden) 1 else 0, if (favorite) 1 else 0),
        )
    }

    private fun assertPersonDataKept(db: SupportSQLiteDatabase) {
        db.query("SELECT id, file_name, is_favorite, is_hidden, title, size_bytes FROM game ORDER BY id").use { c ->
            val rows = buildList { while (c.moveToNext()) add(listOf(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3), c.getString(4), c.getLong(5))) }
            assertEquals(3, rows.size)
            assertEquals(listOf(1, 0, 0), rows.map { it[2] })
            assertEquals(listOf(0, 0, 1), rows.map { it[3] })
            assertEquals(listOf(1024L, 1024L, 1024L), rows.map { it[5] })
        }
        db.query("SELECT game_id, last_played_at, total_seconds FROM play").use { c ->
            c.moveToFirst()
            assertEquals(listOf(1L, 1000L, 3600L), listOf(c.getLong(0), c.getLong(1), c.getLong(2)))
        }
        db.query("SELECT game_id, emulator_id, is_person_choice FROM emulator_choice ORDER BY emulator_id").use { c ->
            val rows = buildList { while (c.moveToNext()) add(Triple(if (c.isNull(0)) null else c.getLong(0), c.getString(1), c.getInt(2))) }
            assertEquals(listOf(Triple(null, "retroarch-snes9x", 1), Triple(1L, "snes9x", 1)), rows)
        }
        db.query("SELECT COUNT(*) FROM media WHERE game_id = 1").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT last_modified FROM scanned_directory WHERE folder_id = 1").use { c ->
            c.moveToFirst()
            assertEquals(5L, c.getLong(0))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
