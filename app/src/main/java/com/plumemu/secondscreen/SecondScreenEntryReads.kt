package com.plumemu.secondscreen

import com.plumemu.coredata.db.GameDao
import com.plumemu.coredata.db.PlayDao
import com.plumemu.coredata.db.PlayedGameRow
import com.plumemu.coredata.db.RaDao
import com.plumemu.coreui.theme.EntryCardDimens
import com.plumemu.game.EmulatorToStart
import com.plumemu.graph.GraphSystemFigures
import com.plumemu.graph.dayStartsOf
import com.plumemu.graph.daySecondsOf
import com.plumemu.graph.weekStartOf
import com.plumemu.home.GameArt
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The figures of the system column's cards, read once the cursor has rested ([SecondScreenFeed]), off the main thread, from what is
 * stored (no network). Each [fill] throws what the database throws; the feed shows the card without figures then.
 */
internal class SecondScreenEntryReads(
    private val figures: GraphSystemFigures,
    private val games: () -> GameDao,
    private val plays: () -> PlayDao,
    private val ra: () -> RaDao,
    /** Whether a RetroAchievements account is stored (the account file only). */
    private val isRaSignedIn: suspend () -> Boolean,
    /** The emulator Play would start for a system's games; null: none installed, or not read. */
    private val emulatorOf: suspend (String) -> EmulatorToStart?,
    /** The saved results under the keys (the home screen's source), by key. */
    private val artOf: suspend (List<String>) -> Map<String, GameArt>,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> TimeZone = TimeZone::getDefault,
    private val locale: () -> Locale = Locale::getDefault,
) {
    /** [content] with its figures read; null when there is nothing to read. */
    suspend fun fill(content: EntryContent): EntryContent? =
        when (content) {
            is SystemEntry -> if (content.figures == null) system(content) else null
            is RecentEntry -> if (content.figures == null) recent(content) else null
            is FavoritesEntry -> if (content.isArtRead) null else favorites(content)
        }

    private suspend fun system(content: SystemEntry): SystemEntry {
        val at = now()
        val id = content.systemId
        val covers = figures.lastPlayed(id, EntryCardDimens.SYSTEM_COVERS).first()
        val signedIn = isRaSignedIn()
        val totals = if (signedIn) ra().observeSystemTotals(id).first() else null
        val achievements =
            totals?.takeIf { it.gamesWithProgress > 0 || it.masteredGames > 0 }
                ?.let { SystemAchievements(it.masteredGames, it.gamesWithProgress, it.pointsEarned.takeIf { _ -> it.gamesWithPoints > 0 }) }
        val weekStart = weekStartOf(at, zone(), locale())
        return content.copy(
            figures =
                SystemFigures(
                    lastPlayed = withArt(covers.map { it.toGame(content.hasBoxArt) }, content.hasBoxArt),
                    playSeconds = figures.playSeconds(id),
                    playedGames = figures.playedGames(id),
                    achievements = achievements,
                    newThisWeek = games().observeAddedSince(id, weekStart).first(),
                    leftOutFiles = figures.leftOutFiles(id),
                    emulator = emulatorOf(id),
                    now = at,
                ),
        )
    }

    private suspend fun recent(content: RecentEntry): RecentEntry {
        val at = now()
        val covers = figures.recent(EntryCardDimens.RECENT_COVERS).first()
        return content.copy(
            figures = RecentFigures(withArt(covers.map { it.toGame(content.hasBoxArt) }, content.hasBoxArt), week(at), at),
        )
    }

    private suspend fun favorites(content: FavoritesEntry): FavoritesEntry = content.copy(games = withArt(content.games, content.hasBoxArt), isArtRead = true)

    /** The seven days of this week from the locale's first day, up to [at], and last week's total. */
    private suspend fun week(at: Long): WeekFigures {
        val tz = zone()
        val loc = locale()
        val weekStart = weekStartOf(at, tz, loc)
        val dayStarts = dayStartsOf(weekStart, tz)
        val today = dayStarts.indexOfLast { it <= at }.coerceIn(0, DAYS - 1)
        val days = daySecondsOf(plays().sessionsBetween(weekStart, at), dayStarts, today, at)
        val lastWeekStart = weekStartOf(weekStart - 1, tz, loc)
        return WeekFigures(
            daySeconds = days,
            todayIndex = today,
            games = figures.gamesPlayedThisWeek(),
            lastWeekSeconds = plays().secondsPlayedBetween(lastWeekStart, weekStart),
            firstDayOfWeek = Calendar.getInstance(tz, loc).firstDayOfWeek,
        )
    }

    /** [games] with each box front read (the small grid picture, else the large one); with box art off nothing is asked. */
    private suspend fun withArt(
        games: List<EntryGame>,
        hasBoxArt: Boolean,
    ): List<EntryGame> {
        val keys = if (hasBoxArt) games.mapNotNull { it.scrapeKey }.distinct() else emptyList()
        if (keys.isEmpty()) return games
        val results = artOf(keys)
        return games.map { game -> game.copy(art = game.scrapeKey?.let { results[it] }?.let { it.grid ?: it.large }) }
    }

    private fun PlayedGameRow.toGame(hasBoxArt: Boolean) = EntryGame(id, title, systemId, scrapeKey.takeIf { hasBoxArt }, lastPlayedAt)

    private companion object {
        const val DAYS = 7
    }
}
