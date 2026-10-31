package com.plumemu.coredata.db

import androidx.room.Dao
import androidx.room.Query
import com.plumemu.coredata.settings.SortGamesSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What the library screens read, and the person's own marks on a game. Room re-runs a list query
 * after any write to `game`; the lists drop answers equal to the last one, so a scan batch of
 * another system does not redraw this one. No list shows a row marked `is_not_game` (an arcade
 * BIOS or device set, or a person's "Not a game").
 */
@Dao
abstract class GameDao {
    /**
     * The games a system shows in [sort] order: not hidden, and one copy per regional group. By
     * last played puts the games never played after the others, by sort name. By year puts the
     * oldest first (the year ScreenScraper gave, [YEAR_ORDER]) and the games with no year after, by sort name.
     */
    fun observeSystemGames(
        systemId: String,
        sort: SortGamesSetting = SortGamesSetting.Name,
    ): Flow<List<GameListRow>> =
        when (sort) {
            SortGamesSetting.Name -> systemGames(systemId)
            SortGamesSetting.LastPlayed -> systemGamesByLastPlayed(systemId)
            SortGamesSetting.Year -> systemGamesByYear(systemId)
        }.distinctUntilChanged()

    /** Favorites in [sort] order (Settings › Library › Sort games), hidden games left out. */
    fun observeFavorites(sort: SortGamesSetting = SortGamesSetting.Name): Flow<List<GameListRow>> =
        when (sort) {
            SortGamesSetting.Name -> favorites()
            SortGamesSetting.LastPlayed -> favoritesByLastPlayed()
            SortGamesSetting.Year -> favoritesByYear()
        }.distinctUntilChanged()

    /** The games the person hid (Settings › Library › Hidden games), by sort name. */
    fun observeHiddenGames(): Flow<List<HiddenGameRow>> = hiddenGames().distinctUntilChanged()

    /**
     * Recent: the [limit] games played last, newest first, leaving out hidden games and games the
     * person removed from Recent (`PlayDao.removeFromRecent` / `clearRecent`) until they are played again.
     */
    fun observeRecent(limit: Int): Flow<List<GameListRow>> = recent(limit).distinctUntilChanged()

    fun observeSystemsWithGames(): Flow<List<SystemGameCount>> = systemsWithGames().distinctUntilChanged()

    /** Games and systems per picked folder, counted as the lists show them (first-run setup's folder cards). */
    fun observeFolderCounts(): Flow<List<FolderGameCount>> = folderCounts().distinctUntilChanged()

    @Query("SELECT * FROM game WHERE id = :gameId")
    abstract suspend fun game(gameId: Long): GameEntity?

    /** What starting [gameId] needs, in one indexed row: its file, its picked folder and its system. */
    @Query(
        "SELECT game.id, game.system_id, game.document_id, game.file_name, rom_folder.tree_uri " +
            "FROM game JOIN rom_folder ON rom_folder.id = game.folder_id WHERE game.id = :gameId",
    )
    abstract suspend fun launchTarget(gameId: Long): GameLaunchTarget?

    @Query("UPDATE game SET is_favorite = :isFavorite WHERE id = :gameId")
    abstract suspend fun setFavorite(gameId: Long, isFavorite: Boolean)

    @Query("UPDATE game SET is_hidden = :isHidden WHERE id = :gameId")
    abstract suspend fun setHidden(gameId: Long, isHidden: Boolean)

    @Query(
        "SELECT id, title, regions, is_favorite, system_id, sort_name, file_name, " + LIST_SCRAPE_KEY + " FROM game " +
            "WHERE system_id = :systemId AND is_hidden = 0 AND is_not_game = 0 AND is_primary_copy = 1 " +
            "ORDER BY sort_name, id",
    )
    protected abstract fun systemGames(systemId: String): Flow<List<GameListRow>>

    @Query(
        "SELECT game.id, game.title, game.regions, game.is_favorite, game.system_id, game.sort_name, game.file_name, " + LIST_SCRAPE_KEY + " FROM game " +
            "LEFT JOIN play ON play.game_id = game.id " +
            "WHERE game.system_id = :systemId AND game.is_hidden = 0 AND is_not_game = 0 AND game.is_primary_copy = 1 " +
            "ORDER BY play.last_played_at IS NULL, play.last_played_at DESC, game.sort_name, game.id",
    )
    protected abstract fun systemGamesByLastPlayed(systemId: String): Flow<List<GameListRow>>

    @Query(
        "SELECT game.id, game.title, game.regions, game.is_favorite, game.system_id, game.sort_name, game.file_name, " + LIST_SCRAPE_KEY + " FROM game " +
            YEAR_JOIN +
            "WHERE game.system_id = :systemId AND game.is_hidden = 0 AND game.is_not_game = 0 AND game.is_primary_copy = 1 " +
            "ORDER BY " + YEAR_ORDER + ", game.sort_name, game.id",
    )
    protected abstract fun systemGamesByYear(systemId: String): Flow<List<GameListRow>>

    @Query(
        "SELECT game.id, game.title, game.regions, game.is_favorite, game.system_id, game.sort_name, game.file_name, " + LIST_SCRAPE_KEY + " FROM game " +
            YEAR_JOIN +
            "WHERE game.is_favorite = 1 AND game.is_hidden = 0 AND game.is_not_game = 0 " +
            "ORDER BY " + YEAR_ORDER + ", game.sort_name, game.id",
    )
    protected abstract fun favoritesByYear(): Flow<List<GameListRow>>

    @Query(
        "SELECT id, title, regions, is_favorite, system_id, sort_name, file_name, " + LIST_SCRAPE_KEY + " FROM game " +
            "WHERE is_favorite = 1 AND is_hidden = 0 AND is_not_game = 0 ORDER BY sort_name, id",
    )
    protected abstract fun favorites(): Flow<List<GameListRow>>

    @Query(
        "SELECT game.id, game.title, game.regions, game.is_favorite, game.system_id, game.sort_name, game.file_name, " + LIST_SCRAPE_KEY + " FROM game " +
            "LEFT JOIN play ON play.game_id = game.id " +
            "WHERE game.is_favorite = 1 AND game.is_hidden = 0 AND is_not_game = 0 " +
            "ORDER BY play.last_played_at IS NULL, play.last_played_at DESC, game.sort_name, game.id",
    )
    protected abstract fun favoritesByLastPlayed(): Flow<List<GameListRow>>

    // One row per arcade game (library's `ArcadeCopies`: Hide hides every copy, show again shows them
    // all): the copies the lists leave out are not listed. Console copies are listed as before.
    @Query(
        "SELECT id, title, regions, system_id FROM game WHERE is_hidden = 1 AND is_not_game = 0 " +
            "AND (is_primary_copy = 1 OR IFNULL(id_method, '') != '" + IdentifyDao.ARCADE_NAME + "') ORDER BY sort_name, id",
    )
    protected abstract fun hiddenGames(): Flow<List<HiddenGameRow>>

    @Query(
        "SELECT game.id, game.title, game.regions, game.is_favorite, game.system_id, game.sort_name, game.file_name, " + LIST_SCRAPE_KEY + " FROM play " +
            "JOIN game ON game.id = play.game_id WHERE game.is_hidden = 0 AND is_not_game = 0 AND play.recent_removed = 0 " +
            "ORDER BY play.last_played_at DESC LIMIT :limit",
    )
    protected abstract fun recent(limit: Int): Flow<List<GameListRow>>

    @Query(
        "SELECT system.id, system.name, COUNT(game.id) AS game_count FROM system " +
            "JOIN game ON game.system_id = system.id " +
            "WHERE game.is_hidden = 0 AND is_not_game = 0 AND game.is_primary_copy = 1 " +
            "GROUP BY system.id ORDER BY system.name",
    )
    protected abstract fun systemsWithGames(): Flow<List<SystemGameCount>>

    @Query(
        "SELECT folder_id, COUNT(id) AS game_count, COUNT(DISTINCT system_id) AS system_count FROM game " +
            "WHERE is_hidden = 0 AND is_not_game = 0 AND is_primary_copy = 1 GROUP BY folder_id",
    )
    protected abstract fun folderCounts(): Flow<List<FolderGameCount>>

    private companion object {
        /** Each list row's [GameListRow.scrapeKey]. */
        /** The scraped row of a game, for the year. */
        private const val YEAR_JOIN = "LEFT JOIN scraped_game ON scraped_game.scrape_key = " + ScrapedGameDao.SCRAPE_KEY + " "

        /** Oldest first; no year (none stored, or 0) after every year. */
        private const val YEAR_ORDER = "CASE WHEN scraped_game.year > 0 THEN scraped_game.year ELSE 2147483647 END"

        const val LIST_SCRAPE_KEY = ScrapedGameDao.SCRAPE_KEY + " AS scrape_key"
    }
}
