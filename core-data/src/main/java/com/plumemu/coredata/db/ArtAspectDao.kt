package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/** One game's stored shape and the size of its saved box front ([ArtAspectDao.artSizes]); 0 for a picture it does not have. */
data class ArtSizes(
    val id: Long,
    @ColumnInfo(name = "art_aspect") val artAspect: Float?,
    @ColumnInfo(name = "grid_width") val gridWidth: Int,
    @ColumnInfo(name = "grid_height") val gridHeight: Int,
    @ColumnInfo(name = "large_width") val largeWidth: Int,
    @ColumnInfo(name = "large_height") val largeHeight: Int,
)

/** A game's new stored shape; a null [aspect] clears it. */
data class ArtAspectChange(
    val gameId: Long,
    val aspect: Float?,
)

/** The reads and writes of `game.art_aspect`; [ArtAspects] decides. */
@Dao
abstract class ArtAspectDao {
    /**
     * Every game's stored shape beside the size of its saved box front: one query for the whole library, six numbers a game,
     * cut from `pictures` in SQL as in [ScrapedGameDao.boxFrontSizes].
     */
    suspend fun artSizes(gridSizeName: String, largeSizeName: String): List<ArtSizes> =
        artSizesOf(ScrapedPictures.objectStart(ScrapedPicture.BOX_FRONT, gridSizeName), ScrapedPictures.objectStart(ScrapedPicture.BOX_FRONT, largeSizeName))

    @Query(
        "SELECT game.id, game.art_aspect, " + GRID_WIDTH + " AS grid_width, " + GRID_HEIGHT + " AS grid_height, " +
            LARGE_WIDTH + " AS large_width, " + LARGE_HEIGHT + " AS large_height " +
            "FROM game LEFT JOIN scraped_game ON scraped_game.scrape_key = " + ScrapedGameDao.SCRAPE_KEY + " AND scraped_game.outcome = 'MATCHED'",
    )
    protected abstract suspend fun artSizesOf(grid: String, large: String): List<ArtSizes>

    @Query("UPDATE game SET art_aspect = :aspect WHERE id = :gameId")
    protected abstract suspend fun setArtAspect(gameId: Long, aspect: Float?)

    /** All of [changes] in one transaction: one change for the list queries. [ArtAspects.sync] cuts a long list into chunks. */
    @Transaction
    open suspend fun write(changes: List<ArtAspectChange>) {
        for (change in changes) setArtAspect(change.gameId, change.aspect)
    }

    private companion object {
        // 0 for a row that does not hold the picture: substr from 0 would read another picture's size.
        private const val GRID = "substr(scraped_game.pictures, instr(scraped_game.pictures, :grid))"
        private const val LARGE = "substr(scraped_game.pictures, instr(scraped_game.pictures, :large))"
        private const val HAS_GRID = "CASE WHEN instr(scraped_game.pictures, :grid) > 0 THEN CAST(substr(" + GRID + ", instr(" + GRID
        private const val HAS_LARGE = "CASE WHEN instr(scraped_game.pictures, :large) > 0 THEN CAST(substr(" + LARGE + ", instr(" + LARGE
        private const val WIDTH_END = ", '\"w\":') + 4) AS INTEGER) ELSE 0 END"
        private const val HEIGHT_END = ", '\"h\":') + 4) AS INTEGER) ELSE 0 END"
        private const val GRID_WIDTH = HAS_GRID + WIDTH_END
        private const val GRID_HEIGHT = HAS_GRID + HEIGHT_END
        private const val LARGE_WIDTH = HAS_LARGE + WIDTH_END
        private const val LARGE_HEIGHT = HAS_LARGE + HEIGHT_END
    }
}
