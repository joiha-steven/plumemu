package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** What a ScreenScraper lookup of a game came back with (schema v3; v4 adds `ask_again`). */
enum class ScrapeOutcome {
    /** ScreenScraper knows the game; the row holds its details and saved pictures. */
    MATCHED,

    /** Not found by hash nor by name; asked again after [ScrapedGameDao.NO_MATCH_RETRY_MILLIS]. */
    NO_MATCH,
}

/**
 * How a [ScrapeOutcome.MATCHED] game was found. The name of a value is stored: never rename one.
 * Library's `ScraperTrust` gives the name trust of each.
 */
enum class ScrapeMatchedBy {
    /** ScreenScraper's ROM lookup with a CRC or a serial the identifier read: an exact match. */
    HASH,

    /** The ROM lookup with only the file name and size (no CRC, no serial known): a likely match. */
    FILE_NAME_SIZE,

    /** The name search, an exact title on the right system: a likely match. */
    NAME,
}

/**
 * What ScreenScraper said about one game, stored under the game's strongest fix key (the first
 * line of `game.fix_keys`, `game:<id>` when it has none: [FixKeys.forGame]), so it survives
 * renames, moves and rescans like a person's name fix ([NameFixEntity]). A row is never deleted
 * with its game, except the `game:<id>` fallback, which alone carries [gameId] (CASCADE), so a
 * reused row id never inherits another game's art.
 *
 * - [detailsJson] is the scraper's `GameDetails.toJson()` text as it was given; this module
 *   stores it and never reads it (core-data does not depend on scraper).
 * - [title], [year], [publisher], [players], [genre] are copied out of the details for the
 *   screens and later filters, which read them without decoding the JSON. [genre] is the main
 *   genre's English name.
 * - [pictures] is [ScrapedPictures]' JSON of every saved file (kind, size name, path, pixels).
 *   One column rather than a child table: the pictures are always written and read with their
 *   row, never looked up on their own, and a REPLACE of the row then cannot leave stale picture
 *   rows behind. [hasBoxFront] is copied out for the progress counts.
 * - [supportMissing]: the result had a disc or cartridge reference but no such picture was saved
 *   yet: the run's main pass saves the box front only, or the download failed, or the person
 *   asked again and the disc pass is to fetch it again (the earlier picture stays listed in
 *   [pictures] meanwhile). [ScrapedGameDao.pendingSupport] offers the game to the disc pass until
 *   [supportAttempts] (the disc pass's failed attempts in a row) reaches
 *   [ScrapedGameDao.MAX_SUPPORT_ATTEMPTS].
 * - [isPersonChoice]: the person picked this match; a run never replaces such a row.
 * - [askAgain]: the person asked for this game again (Settings' Download missing, Re-download; [ScrapedGameDao.markMissingToAskAgain], [ScrapedGameDao.markAllToAskAgain]). The row
 *   and its pictures stay until the run stores the new answer, which clears it, so nothing shown
 *   goes blank meanwhile. Never set on a person's choice.
 */
@Entity(
    tableName = "scraped_game",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["game_id"])],
)
data class ScrapedGameEntity(
    @PrimaryKey @ColumnInfo(name = "scrape_key") val scrapeKey: String,
    /** Set only on the `game:<id>` fallback key ([NameFixEntity.ROW_PREFIX]). */
    @ColumnInfo(name = "game_id") val gameId: Long?,
    val outcome: ScrapeOutcome,
    /** ScreenScraper's game id; null for [ScrapeOutcome.NO_MATCH]. */
    @ColumnInfo(name = "screenscraper_id") val screenScraperId: String?,
    @ColumnInfo(name = "matched_by") val matchedBy: ScrapeMatchedBy?,
    @ColumnInfo(name = "is_person_choice", defaultValue = "0") val isPersonChoice: Boolean = false,
    /** When the lookup was stored, epoch millis. */
    @ColumnInfo(name = "scraped_at") val scrapedAt: Long,
    @ColumnInfo(name = "details_json") val detailsJson: String?,
    val title: String?,
    val year: Int?,
    val publisher: String?,
    val players: String?,
    val genre: String?,
    @ColumnInfo(defaultValue = "''") val pictures: String = "",
    @ColumnInfo(name = "has_box_front", defaultValue = "0") val hasBoxFront: Boolean = false,
    @ColumnInfo(name = "support_missing", defaultValue = "0") val supportMissing: Boolean = false,
    /** Set by [ScrapedGameDao.storeFromRun]; 0 when the disc or cartridge was saved or there is none. */
    @ColumnInfo(name = "support_attempts", defaultValue = "0") val supportAttempts: Int = 0,
    /** Schema v4. */
    @ColumnInfo(name = "ask_again", defaultValue = "0") val askAgain: Boolean = false,
)

/** What a screen reads of a scraped game: everything but the details JSON. */
data class ScrapedGameSummary(
    @ColumnInfo(name = "scrape_key") val scrapeKey: String,
    val outcome: ScrapeOutcome,
    @ColumnInfo(name = "screenscraper_id") val screenScraperId: String?,
    val title: String?,
    val year: Int?,
    val publisher: String?,
    val players: String?,
    val genre: String?,
    /** [ScrapedPictures] JSON; decode with [ScrapedPictures.decode]. */
    val pictures: String,
    /**
     * When the row was stored: a re-download writes new files under the same paths, so a decoded
     * picture kept in memory is told apart by it.
     */
    @ColumnInfo(name = "scraped_at") val scrapedAt: Long = 0,
)

/**
 * One saved picture file. [kind] is the scraper's `MediaKind` serial name ([BOX_FRONT],
 * [SUPPORT]); [sizeName] the host's `MediaSize` name.
 */
data class ScrapedPicture(
    val kind: String,
    val sizeName: String,
    val path: String,
    val width: Int,
    val height: Int,
) {
    companion object {
        const val BOX_FRONT = "box-front"
        const val SUPPORT = "support"
    }
}

/** `scraped_game.pictures`: a JSON array of [ScrapedPicture]s. */
object ScrapedPictures {
    /**
     * Keys in this order, without spaces: [ScrapedGameDao.boxFrontSizes] reads `w` and `h` in SQL
     * after [objectStart]. Change the layout and change that query (`BoxFrontShapesTest.theSqlReadsTheLargeBoxFrontsSizeFromWhatTheEncoderWrote` checks both).
     */
    fun encode(pictures: List<ScrapedPicture>): String = JSONArray().apply {
        for (picture in pictures) {
            put(
                JSONObject()
                    .put("kind", picture.kind)
                    .put("size", picture.sizeName)
                    .put("path", picture.path)
                    .put("w", picture.width)
                    .put("h", picture.height),
            )
        }
    }.toString()

    /** How [encode] starts the object of a picture of [kind] and [sizeName], up to its path: `{"kind":…,"size":…,`. */
    fun objectStart(
        kind: String,
        sizeName: String,
    ): String = JSONObject().put("kind", kind).put("size", sizeName).toString().removeSuffix("}") + ","

    /**
     * The pictures in [stored]. An empty value is no picture; a damaged one (never written by
     * [encode]) is no picture too, so the game shows its placeholder rather than failing a list.
     */
    fun decode(stored: String): List<ScrapedPicture> {
        if (stored.isBlank()) return emptyList()
        return try {
            val array = JSONArray(stored)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                ScrapedPicture(item.getString("kind"), item.getString("size"), item.getString("path"), item.getInt("w"), item.getInt("h"))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}
