package com.plumemu.graph

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumMigrations
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.IdName
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The schema 16 back-fill with the app's reader over details the real `GameDetails.toJson()` wrote:
 * its `LIKE '%"rating"%'` pre-filter matches the key the scraper really writes, and every rating
 * reads as the details show it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GraphRatingMigrationTest {
    private fun open(): SupportSQLiteDatabase {
        val config =
            SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                ).build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun ratingsWrittenByTheScraperAreReadBack() {
        val db = open()
        // Schema 15's scraped_game, cut to what the migration reads.
        db.execSQL("CREATE TABLE scraped_game (scrape_key TEXT NOT NULL PRIMARY KEY, details_json TEXT)")
        val stored = mapOf(
            "crc:a" to GameDetails(rating = 17, players = "1-2", publisher = IdName("3", "Nintendo")).toJson(),
            "crc:b" to GameDetails(rating = 1).toJson(),
            "crc:c" to GameDetails(rating = 20).toJson(),
            "crc:d" to GameDetails(rating = 0).toJson(),
            "crc:e" to GameDetails(players = "1").toJson(),
        )
        for ((key, json) in stored) db.execSQL("INSERT INTO scraped_game VALUES (?, ?)", arrayOf<Any?>(key, json))

        PlumMigrations.migration15To16(GraphDetailsRating).migrate(db)

        val ratings = db.query("SELECT scrape_key, rating FROM scraped_game ORDER BY scrape_key").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), if (c.isNull(1)) null else c.getInt(1)) }
        }
        assertEquals(mapOf("crc:a" to 17, "crc:b" to 1, "crc:c" to 20, "crc:d" to null, "crc:e" to null), ratings)
        db.close()
    }
}
