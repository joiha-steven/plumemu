package com.plumemu.coredata.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema 17 to 18 on a library of real size: a database built at schema 17 with [GAMES] games keeps every row of every table to
 * the byte (a checksum of each table's old columns), the new column stays null, the migration runs again safely, and a file at
 * schema 18 opened by a build that only knows 17 is refused with its rows intact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlumMigration17To18Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun fill(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
        db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES (1, 'content://tree/1', 'ROMs', 10, 20)")
        for (id in 1..GAMES) {
            db.execSQL(
                "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, " +
                    "modified_at, title, sort_name, regions, disc_group, disc_count, system_id, is_primary_copy, is_hidden, " +
                    "is_favorite, hash, is_pending, file_title, file_sort_name, file_regions, name_trust, id_method, " +
                    "id_confidence, fingerprint, fingerprint_size, fingerprint_mtime, pack_version, fix_keys, is_not_game, " +
                    "needs_identify, added_at) VALUES ($id, 1, 'doc$id', 'root', 'g$id.sfc', 'g$id.sfc', ${1024 + id}, 1700000000000, " +
                    "'Game $id', 'game $id', 'USA', NULL, 1, 'snes', 1, ${id % 7 / 6}, ${id % 2}, 'abc$id', 0, 'Game $id', 'game $id', " +
                    "'USA', 'DAT', 'CRC', 'EXACT', 'crc:$id', 1024, 99, 7, 'crc:$id', 0, 0, ${if (id % 3 == 0) "NULL" else "1800000000$id"})",
            )
            if (id % 2 == 0) db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds, recent_removed) VALUES ($id, ${70000 + id}, $id, 0)")
            if (id % 4 != 0) {
                db.execSQL(
                    "INSERT INTO scraped_game (scrape_key, game_id, outcome, screenscraper_id, matched_by, is_person_choice, scraped_at, " +
                        "details_json, title, year, publisher, players, genre, pictures, has_box_front, support_missing, support_attempts, " +
                        "ask_again, rating) VALUES ('crc:$id', $id, 'MATCHED', $id, 'CRC', 0, 40, '{}', 'Game $id', 1990, 'Maker', '1', 'Action', ?, 1, 0, 0, 0, 17)",
                    arrayOf<Any>(ScrapedPictures.encode(listOf(ScrapedPicture(ScrapedPicture.BOX_FRONT, GRID, "/covers/$id.jpg", 300, 200 + id % 100)))),
                )
            }
        }
        db.execSQL("INSERT INTO name_fix (fix_key, kind, title, title_source, game_id, fixed_at) VALUES ('crc:1', 'TITLE', 'Zork I', 'PERSON', 1, 30)")
        db.execSQL(
            "INSERT INTO ra_progress (ra_game_id, achievements_total, awarded_softcore, awarded_hardcore, highest_award, updated_at, " +
                "points_earned, points_total) VALUES (1446, 10, 10, 10, 'mastered', 6, 5, 10)",
        )
        db.setTransactionSuccessful()
        db.endTransaction()
    }

    private fun column(db: SupportSQLiteDatabase, sql: String): List<String> =
        db.query(sql).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    /** table to row count and SHA-256 of every column schema 17 has, in rowid order (typed: 1 and '1' differ). */
    private fun checksums(db: SupportSQLiteDatabase): Map<String, Pair<Int, String>> =
        column(db, "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name != 'android_metadata' ORDER BY name")
            .associateWith { table ->
                val columns = column(db, "SELECT name FROM pragma_table_info('$table')").filter { it != "art_aspect" }.joinToString(", ") { "`$it`" }
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
    fun aLibraryOfRealSizeKeepsEveryRowToTheByteAndMigratesAtOnce() {
        val before = helper.createDatabase(DB_NAME, 17).use { db ->
            fill(db)
            checksums(db)
        }
        assertEquals(GAMES, before.getValue("game").first)
        val started = System.nanoTime()
        helper.runMigrationsAndValidate(DB_NAME, 18, true, PlumMigrations.MIGRATION_17_18).use { db ->
            val millis = (System.nanoTime() - started) / 1_000_000
            assertEquals(before, checksums(db))
            assertEquals("no row gets an invented shape", listOf("0"), column(db, "SELECT COUNT(art_aspect) FROM game"))
            println("MIGRATION_17_18: $GAMES games, ${before.getValue("game").second.take(16)} kept, $millis ms (open, migrate, validate)")
            assertTrue("took $millis ms", millis < MAX_MILLIS)
        }
    }

    @Test
    fun theMigrationItselfWritesNoRow() {
        val db = helper.createDatabase(DB_NAME, 17)
        fill(db)
        val changesBefore = column(db, "SELECT total_changes()")
        val started = System.nanoTime()
        PlumMigrations.MIGRATION_17_18.migrate(db)
        val micros = (System.nanoTime() - started) / 1_000
        println("MIGRATION_17_18.migrate alone: $micros us on $GAMES games")
        assertEquals("no row written", changesBefore, column(db, "SELECT total_changes()"))
        assertEquals(listOf("0"), column(db, "SELECT COUNT(art_aspect) FROM game"))
        db.close()
    }

    @Test
    fun theMigrationCanRunAgainAndKeepsWhatWasStoredInBetween() {
        helper.createDatabase(DB_NAME, 17).use(::fill)
        val db = helper.runMigrationsAndValidate(DB_NAME, 18, true, PlumMigrations.MIGRATION_17_18)
        db.execSQL("UPDATE game SET art_aspect = 0.75 WHERE id = 2")
        val before = checksums(db)
        PlumMigrations.MIGRATION_17_18.migrate(db)
        assertEquals(before, checksums(db))
        assertEquals(listOf("0.75"), column(db, "SELECT art_aspect FROM game WHERE id = 2"))
        db.close()
    }

    @Test
    fun aMigratedLibraryOpensListsNoShapeUntilAPassThenEveryCoverHasOne() = runBlocking {
        helper.createDatabase(DB_NAME, 17).use(::fill)
        val room =
            Room.databaseBuilder(context, PlumDatabase::class.java, DB_NAME)
                .addMigrations(*PlumMigrations.all(TestDetailsRating))
                .allowMainThreadQueries()
                .build()
        val listed = room.gameDao().observeSystemGames("snes").first()
        assertTrue(listed.isNotEmpty())
        assertTrue(listed.all { it.artAspect == null })
        val covers = (1..GAMES).count { it % 4 != 0 }
        assertEquals(covers, ArtAspects.sync(room.artAspectDao(), GRID, LARGE).rowsWritten)
        assertEquals("a second pass finds nothing to write", 0, ArtAspects.sync(room.artAspectDao(), GRID, LARGE).rowsWritten)
        assertEquals(300f / (200 + 1), room.gameDao().observeSystemGames("snes").first().first { it.id == 1L }.artAspect)
        assertNull("a game with no result keeps no shape", room.gameDao().game(4)?.artAspect)
        room.close()
    }

    /**
     * Going back: a build that only knows schema 17 has no way down, so opening a schema 18 file throws and
     * touches nothing; the file then opens again at 18 with every row. The copy taken before the update opens at 17.
     */
    @Test
    fun anOlderBuildRefusesTheFileAndNothingIsLost() {
        val database = helper.createDatabase(DB_NAME, 17).use { db ->
            fill(db)
            context.getDatabasePath(DB_NAME)
        }
        val copyBeforeUpdate = File(database.parentFile, "before-update.db")
        database.copyTo(copyBeforeUpdate)
        helper.runMigrationsAndValidate(DB_NAME, 18, true, PlumMigrations.MIGRATION_17_18).close()

        val refused = assertThrows(IllegalStateException::class.java) { helper.runMigrationsAndValidate(DB_NAME, 17, true) }
        assertNotEquals(null, refused.message)

        android.database.sqlite.SQLiteDatabase.openDatabase(database.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(18, db.version)
            db.rawQuery("SELECT COUNT(*) FROM game", null).use { it.moveToFirst(); assertEquals(GAMES, it.getInt(0)) }
        }
        android.database.sqlite.SQLiteDatabase.openDatabase(copyBeforeUpdate.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(17, db.version)
            db.rawQuery("SELECT COUNT(*) FROM game", null).use { it.moveToFirst(); assertEquals(GAMES, it.getInt(0)) }
        }
    }

    private companion object {
        const val DB_NAME = "migration-17-18.db"
        const val GAMES = 2_500
        const val GRID = "grid"
        const val LARGE = "large"

        /** On a test machine; the add-column itself takes microseconds, the rest is opening and validating. */
        const val MAX_MILLIS = 2_000L
    }
}
