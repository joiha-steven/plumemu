package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import com.plumemu.graph.LibraryResetState
import com.plumemu.home.ArtPicture
import com.plumemu.home.HomeArtSource

/*
 * Settings › Statistics: read-only figures worked out
 * from what Plumemu already stores (the play history, the library, the stored box art). Nothing
 * is sent. "This week" comes from the per-session table, so it counts only from the update
 * on; it is hidden until a first session is recorded.
 */

/** What the page shows, read once ([SettingsStatisticsSource.read]). */
@Immutable
internal data class PlayStatistics(
    /** Every second played on a game still in the library, hidden ones included. */
    val totalSeconds: Long = 0,
    /**
     * Some game was played: its copies together reached a minute (`graph/PlayedGames.kt`). Until
     * then one calm line stands instead of the play figures; the same minute decides the counts.
     */
    val hasPlayed: Boolean = false,
    /** Seconds played from the start of the locale's week to now (sessions, clipped at the week start). */
    val thisWeekSeconds: Long = 0,
    /** Any session is recorded: until then there is no "This week" line, not even a 0. */
    val hasSessions: Boolean = false,
    /** The listed games played longest, longest first (at most [TOP_GAMES]). */
    val topGames: List<TopGame> = emptyList(),
    /** The system whose listed games were played longest; null when none was. */
    val topSystem: TopSystem? = null,
    /** Listed games played (a minute or more over their copies). */
    val playedGames: Int = 0,
    /** Listed games: what the main screen's lists show. */
    val libraryGames: Int = 0,
    /** Listed games with a saved box front; 0 (not counted) without box art set up. */
    val gamesWithBoxArt: Int = 0,
    /** Listed games box art is for (pinned shortcuts left out): the coverage's "of"; 0 without box art set up. */
    val boxArtGames: Int = 0,
    /**
     * Box art is set up: the most-played games show a cover slot, as the home grid
     * does, and the box-art line is shown. Without it there is neither.
     */
    val hasBoxArt: Boolean = false,
) {
    companion object {
        const val TOP_GAMES = 3
    }
}

/** One of the most-played games: its cover is the home grid's saved box front, null when it has none. */
@Immutable
internal data class TopGame(
    val id: Long,
    val title: String,
    val systemName: String,
    val seconds: Long,
    val cover: ArtPicture? = null,
    /** Recorded plays of the game (`play_session`, kept 400 days, from schema 5 on); 0 when none is stored: the panel leaves the line out. */
    val timesPlayed: Int = 0,
    /** Calendar days since it was last played (0 today); null when not stored. */
    val lastPlayedDaysAgo: Int? = null,
)

/** The most-played system, by the name the main screen shows. */
@Immutable
internal data class TopSystem(
    val id: String,
    val name: String,
    val seconds: Long,
)

/** Where Statistics' figures are. */
@Immutable
internal sealed interface StatisticsState {
    /** Not arrived yet: the page shows no rows, so nothing moves when they land. */
    data object NotRead : StatisticsState

    /** The read failed (logged by the reader); the page says so. */
    data object Failed : StatisticsState

    data class Read(val statistics: PlayStatistics) : StatisticsState
}

/** One row of Statistics, in the order shown. */
@Immutable
internal sealed interface StatisticRow {
    data class Total(val seconds: Long) : StatisticRow

    /** This week's play time; 0 means sessions exist but none this week ("None yet"). */
    data class ThisWeek(val seconds: Long) : StatisticRow

    /** [rank] counts from 1; [showsCover] keeps the cover slot (a no-art tile when [TopGame.cover] is null). */
    data class Game(val rank: Int, val game: TopGame, val showsCover: Boolean) : StatisticRow

    data class System(val system: TopSystem) : StatisticRow

    data object NothingPlayed : StatisticRow

    data class Played(val played: Int, val of: Int) : StatisticRow

    data class BoxArt(val withArt: Int, val of: Int) : StatisticRow

    data object NoGames : StatisticRow

    data object Unreadable : StatisticRow

    /** The app's version and build, as About shows it. */
    data object Version : StatisticRow
}

/**
 * The rows for [state]: none until the figures arrive; the play figures, or one calm line when
 * nothing was played; the library lines (one line when the library is empty); the version last.
 */
internal fun statisticRowsOf(state: StatisticsState): List<StatisticRow> =
    when (state) {
        StatisticsState.NotRead -> emptyList()
        StatisticsState.Failed -> listOf(StatisticRow.Unreadable, StatisticRow.Version)
        is StatisticsState.Read ->
            buildList {
                val figures = state.statistics
                if (figures.hasPlayed) {
                    add(StatisticRow.Total(figures.totalSeconds))
                    if (figures.hasSessions) add(StatisticRow.ThisWeek(figures.thisWeekSeconds))
                    figures.topGames.forEachIndexed { at, game -> add(StatisticRow.Game(at + 1, game, figures.hasBoxArt)) }
                    figures.topSystem?.let { add(StatisticRow.System(it)) }
                } else {
                    add(StatisticRow.NothingPlayed)
                }
                if (figures.libraryGames == 0) {
                    add(StatisticRow.NoGames)
                } else {
                    add(StatisticRow.Played(figures.playedGames, figures.libraryGames))
                    // Only shortcut games (never asked for box art): "0 of 0" would say nothing.
                    if (figures.hasBoxArt && figures.boxArtGames > 0) add(StatisticRow.BoxArt(figures.gamesWithBoxArt, figures.boxArtGames))
                }
                add(StatisticRow.Version)
            }
    }

/** Reads Statistics' figures for Settings (`graph/GraphStatistics.kt`). */
internal interface SettingsStatisticsSource {
    /** Reads every figure off the main thread; a failure is logged and comes back as [StatisticsState.Failed]. */
    suspend fun read(): StatisticsState

    /** Decodes the most-played games' covers (the home screen's loader). */
    val art: HomeArtSource

    object None : SettingsStatisticsSource {
        override suspend fun read(): StatisticsState = StatisticsState.Read(PlayStatistics())

        override val art: HomeArtSource = HomeArtSource.None
    }
}

/**
 * Statistics' figures for one opening of Settings: read once, off the main thread, right after
 * Settings' first frame (so opening it waits for nothing), not again on recomposition. Read on
 * opening rather than on reaching the section, because L1 / R1 skip a section without rows. Read
 * again when the library changes under the open page: a scan ends ([isScanning] goes false; Rescan,
 * and the scan after Reset library) or Reset library has forgotten the games ([libraryReset]).
 */
@Composable
internal fun rememberStatistics(
    source: SettingsStatisticsSource,
    isScanning: Boolean,
    libraryReset: LibraryResetState,
): StatisticsState {
    var state by remember(source) { mutableStateOf<StatisticsState>(StatisticsState.NotRead) }
    val scanning by rememberUpdatedState(isScanning)
    val reset by rememberUpdatedState(libraryReset)
    LaunchedEffect(source) {
        var before: Pair<Boolean, LibraryResetState>? = null
        // Read one at a time (collect, not collectLatest): a change that asks for nothing must not cancel a read.
        snapshotFlow { scanning to reset }.collect { now ->
            val previous = before
            before = now
            val scanEnded = previous != null && previous.first && !now.first
            val forgotten = previous != null && previous.second != LibraryResetState.Forgotten && now.second == LibraryResetState.Forgotten
            if (previous == null || scanEnded || forgotten) state = source.read()
        }
    }
    return state
}
