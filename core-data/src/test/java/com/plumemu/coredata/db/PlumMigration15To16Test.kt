package com.plumemu.coredata.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.plumemu.coredata.settings.SortGamesSetting
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
 * Schema 15 to 16 (the Rating sort): `scraped_game.rating` is added and read from each row's
 * details. A row with a rating gets it; no rating, a 0 or out-of-range one, broken details, no
 * details, or a reader that throws keep null; every other value stays; more rows than one page
 * are all read; it runs again safely; the result opens with Room and sorts by rating.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlumMigration15To16Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    private val migration = PlumMigrations.migration15To16(TestDetailsRating)

    private fun details(rating: String?) = if (rating == null) """{"format":1,"details":{"players":"1"}}""" else """{"format":1,"details":{"rating":$rating}}"""

    /** Scraped rows by key: their details JSON (null: a no-match row). */
    private val scraped = linkedMapOf(
        "game:1" to details("17"),
        "crc:norating" to details(null),
        "crc:zero" to details("0"),
        "crc:high" to details("25"),
        "crc:broken" to """{"format":1,"details":{"rating":1""",
        "crc:empty" to "",
        "crc:text" to details("\"great\""),
        "crc:nomatch" to null,
    )

    private fun fill(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
        db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at) VALUES (1, 'content://tree/1', 'ROMs', 10)")
        for ((id, name) in listOf(1 to "Zork", 2 to "Mario")) {
            db.execSQL(
                "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, modified_at, title, sort_name, " +
                    "regions, disc_count, system_id, is_primary_copy, is_hidden, is_favorite, is_pending, file_title, file_sort_name, file_regions, " +
                    "name_trust, fix_keys, is_not_game, needs_identify) VALUES ($id, 1, 'd$id', 'root', '$name.sfc', '$name.sfc', 1, 1, '$name', " +
                    "'${name.lowercase()}', '', 1, 'snes', 1, 0, 1, 0, '$name', '${name.lowercase()}', '', 'FILE_NAME', '', 0, 0)",
            )
        }
        for ((key, json) in scraped) {
            db.execSQL(
                "INSERT INTO scraped_game (scrape_key, game_id, outcome, screenscraper_id, scraped_at, details_json, title, year, publisher, players, " +
                    "genre, pictures, has_box_front) VALUES (?, ?, ?, '42', 50, ?, 'Zork', 1980, 'Infocom', '1', 'Adventure', 'p', 1)",
                arrayOf<Any?>(key, if (key == "game:1") 1 else null, if (json == null) "NO_MATCH" else "MATCHED", json),
            )
        }
    }

    private fun rows(db: SupportSQLiteDatabase, sql: String): List<List<String?>> =
        db.query(sql).use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
        }

    private fun ratings(db: SupportSQLiteDatabase): Map<String, String?> =
        rows(db, "SELECT scrape_key, rating FROM scraped_game").associate { it[0]!! to it[1] }

    private fun everythingBut(db: SupportSQLiteDatabase): Map<String, List<List<String?>>> =
        TABLES.associateWith { rows(db, "SELECT * FROM $it ORDER BY 1, 2") }

    @Test
    fun version15To16ReadsTheRatingsAndKeepsEveryOtherValue() {
        val before = helper.createDatabase(DB_NAME, 15).use { db ->
            fill(db)
            everythingBut(db)
        }
        helper.runMigrationsAndValidate(DB_NAME, 16, true, migration).use { db ->
            assertEquals(
                mapOf(
                    "game:1" to "17", "crc:norating" to null, "crc:zero" to null, "crc:high" to null, "crc:broken" to null,
                    "crc:empty" to null, "crc:text" to null, "crc:nomatch" to null,
                ),
                ratings(db),
            )
            assertEquals(before, everythingBut(db))
            val kept = rows(db, "SELECT scrape_key, outcome, details_json, title, year, publisher, players, genre, pictures, has_box_front FROM scraped_game ORDER BY scrape_key")
            assertEquals(scraped.keys.sorted(), kept.map { it[0] })
            assertEquals(scraped.toSortedMap().values.toList(), kept.map { it[2] })
            assertTrue(kept.all { it.drop(3) == listOf("Zork", "1980", "Infocom", "1", "Adventure", "p", "1") })
        }
    }

    @Test
    fun aReaderThatThrowsLeavesThatRowWithoutARatingAndTheMigrationGoesOn() {
        helper.createDatabase(DB_NAME, 15).use(::fill)
        val picky = DetailsRating { json -> if ("25" in json) throw IllegalStateException("unreadable") else TestDetailsRating.of(json) }
        helper.runMigrationsAndValidate(DB_NAME, 16, true, PlumMigrations.migration15To16(picky)).use { db ->
            assertEquals("17", ratings(db)["game:1"])
            assertEquals(null, ratings(db)["crc:high"])
        }
    }

    @Test
    fun aReaderThatThrowsAnErrorLeavesThatRowWithoutARatingAndTheMigrationGoesOn() {
        helper.createDatabase(DB_NAME, 15).use(::fill)
        // As deeply nested text can make a recursive parser overflow its stack.
        val deep = DetailsRating { json -> if ("25" in json) throw StackOverflowError() else TestDetailsRating.of(json) }
        helper.runMigrationsAndValidate(DB_NAME, 16, true, PlumMigrations.migration15To16(deep)).use { db ->
            assertEquals("17", ratings(db)["game:1"])
            assertEquals(null, ratings(db)["crc:high"])
        }
    }

    @Test
    fun moreRowsThanOnePageAreAllRead() {
        helper.createDatabase(DB_NAME, 15).use { db ->
            fill(db)
            for (i in 0 until 345) {
                db.execSQL(
                    "INSERT INTO scraped_game (scrape_key, outcome, scraped_at, details_json, pictures) VALUES (?, 'MATCHED', 1, ?, '')",
                    arrayOf<Any?>("crc:many%04d".format(i), if (i % 5 == 0) "{" else details((i % 20 + 1).toString())),
                )
            }
        }
        helper.runMigrationsAndValidate(DB_NAME, 16, true, migration).use { db ->
            val read = ratings(db).filterKeys { it.startsWith("crc:many") }
            assertEquals(345, read.size)
            for ((key, value) in read) {
                val i = key.removePrefix("crc:many").toInt()
                assertEquals(key, if (i % 5 == 0) null else (i % 20 + 1).toString(), value)
            }
        }
    }

    @Test
    fun version15To16CanRunAgainAndKeepsWhatWasStoredInBetween() {
        helper.createDatabase(DB_NAME, 15).use(::fill)
        val db = helper.runMigrationsAndValidate(DB_NAME, 16, true, migration)
        // A run stored a new rating, and a person's later answer has none: a second pass changes neither.
        db.execSQL("UPDATE scraped_game SET rating = 9 WHERE scrape_key = 'crc:norating'")
        db.execSQL("UPDATE scraped_game SET rating = 4 WHERE scrape_key = 'game:1'")
        val before = rows(db, "SELECT * FROM scraped_game ORDER BY scrape_key")
        migration.migrate(db)
        assertEquals(before, rows(db, "SELECT * FROM scraped_game ORDER BY scrape_key"))
        db.close()
    }

    @Test
    fun aMigratedLibraryOpensAndSortsByRating() = runTest {
        helper.createDatabase(DB_NAME, 15).use(::fill)
        val room =
            Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, DB_NAME)
                .addMigrations(*PlumMigrations.all(TestDetailsRating))
                .allowMainThreadQueries()
                .build()
        assertTrue(room.gameDao().observeHasRating().first())
        // Zork's row (game:1) is rated 17; Mario has none and comes after.
        assertEquals(listOf("Zork", "Mario"), room.gameDao().observeSystemGames("snes", SortGamesSetting.Rating).first().map { it.title })
        room.close()
    }

    private companion object {
        const val DB_NAME = "migration-15-16.db"

        /** The tables the migration does not touch, compared whole. */
        val TABLES = listOf("system", "rom_folder", "game")
    }
}
