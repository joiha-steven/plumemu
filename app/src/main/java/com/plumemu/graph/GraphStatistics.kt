package com.plumemu.graph

import com.plumemu.coredata.db.PlayDao
import com.plumemu.coredata.db.ScrapedGameDao
import com.plumemu.coredata.db.SessionSpan
import com.plumemu.home.HomeArtSource
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.platforms.PlatformCatalog
import com.plumemu.settings.screen.PlayStatistics
import com.plumemu.settings.screen.SettingsStatisticsSource
import com.plumemu.settings.screen.StatisticsState
import com.plumemu.settings.screen.TopGame
import com.plumemu.settings.screen.TopSystem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Settings › Statistics' figures, read on [io] from what is already stored: the play history
 * (`PlayDao`), the library's listed games and their saved box fronts (`ScrapedGameDao`'s counts,
 * the ones Accounts › ScreenScraper shows), and the most-played games' covers through the home
 * screen's own results ([art]). Each database part is opened on the reading thread. Budget: under
 * 100 ms on a 10,000-game library (`GraphStatisticsTest.aReadOfTenThousandGamesStaysUnderItsBudget`).
 * A game counts by its group of regional copies ([playedGamesOf]).
 * This week's play time comes from `play_session` (`PlayDao.sessionsBetween` from [weekStartOf] to
 * now, split per day by [daySecondsOf]) in the same read, with `hasAnySession` saying whether a session was ever recorded.
 */
internal class GraphStatistics(
    private val plays: () -> PlayDao,
    private val scraped: () -> ScrapedGameDao,
    /** The platform data, for the names the main screen shows; null names systems by their stored name. */
    private val catalog: suspend () -> PlatformCatalog?,
    /** Box art is set up: only then do the most-played games show covers. */
    private val hasBoxArt: suspend () -> Boolean,
    override val art: HomeArtSource,
    private val io: CoroutineDispatcher,
    private val log: (String) -> Unit,
    /** Wall-clock milliseconds; "this week" runs from the week's start to this. */
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> TimeZone = TimeZone::getDefault,
    private val locale: () -> Locale = Locale::getDefault,
    /** Systems whose copies of one game have different file names (the arcade systems), for [arcadeGames]. */
    private val arcadeSystems: List<String> = ArcadeSystems.IDS.toList(),
    /** Which arcade game a copy belongs to ([arcadeGamesOf]: pack lookups); none by default. */
    private val arcadeGames: ArcadeGames = ArcadeGames.None,
) : SettingsStatisticsSource {
    override suspend fun read(): StatisticsState =
        withContext(io) {
            try {
                StatisticsState.Read(figures())
            } catch (e: Exception) {
                // Room cancels its own scope when its database closes: that cancellation is the read
                // failing, not Settings going away (which this rethrows).
                coroutineContext.ensureActive()
                // The class only: the page says it could not read; a title or path never reaches the log.
                log("Settings: statistics could not be read (${e.javaClass.simpleName})")
                StatisticsState.Failed
            }
        }

    /** Every figure, in one pass; throws what the database throws. */
    internal suspend fun figures(): PlayStatistics {
        val dao = plays()
        val games = playedGamesOf(dao, arcadeSystems, arcadeGames)
        // Listed and played, longest first, each with the copy the lists show.
        val listedGames = mostPlayedOf(games)
        val listed = listedGames.mapNotNull { game -> game.shown?.let { it to game.seconds } }
        val top = listed.take(PlayStatistics.TOP_GAMES)
        val names = if (listed.isEmpty()) null else catalog()
        fun nameOf(id: String, stored: String) = names?.system(id)?.displayName ?: stored
        val systems =
            listed
                .groupBy { it.first.systemId }
                .map { (id, group) -> TopSystem(id, nameOf(id, group.first().first.systemName), group.sumOf { it.second }) }
                .sortedWith(compareByDescending<TopSystem> { it.seconds }.thenBy { it.id })
                .take(PlayStatistics.TOP_SYSTEMS)
        val hasBoxArt = hasBoxArt()
        val covers = if (hasBoxArt && top.isNotEmpty()) art.observe(top.map { it.first.scrapeKey }).first() else emptyMap()
        val results = scraped()
        val to = now()
        val topGames = top.map { (copy, seconds) -> TopGame(copy.id, copy.title, nameOf(copy.systemId, copy.systemName), copy.systemId, seconds, covers[copy.scrapeKey]?.grid) }
        val hasSessions = dao.hasAnySession()
        val dayStarts = dayStartsOf(weekStartOf(to, zone(), locale()), zone())
        val today = (0..6).lastOrNull { dayStarts[it] <= to } ?: 0
        // One query for the week, split per day here: past days whole, today up to now, later days 0.
        val weekDays = if (hasSessions) daySecondsOf(dao.sessionsBetween(dayStarts[0], to), dayStarts, today, to) else emptyList()
        return PlayStatistics(
            thisWeekSeconds = weekDays.sum(),
            weekDaySeconds = weekDays,
            todayIndex = if (hasSessions) today else 0,
            hasSessions = hasSessions,
            totalSeconds = dao.totalSeconds(),
            hasPlayed = games.any { it.isPlayed },
            topGames = topGames,
            topSystems = systems,
            playedGames = listed.size,
            libraryGames = results.countListed(),
            // no box art set up, no box-art line, so the coverage is not even counted.
            gamesWithBoxArt = if (hasBoxArt) results.countWithBoxFront() else 0,
            // Pinned shortcuts get no box art yet: the coverage is out of the others.
            boxArtGames = if (hasBoxArt) results.countListedForBoxArt() else 0,
            hasBoxArt = hasBoxArt,
        )
    }
}

/**
 * Midnight (in [zone]) of the first day of the week, in the week [at] falls in; the first day is
 * the [locale]'s (Monday in en-GB and de, Sunday in en-US). Walks back whole days from [at] rather
 * than setting DAY_OF_WEEK, which can land in the next week for some locales' minimal-days rule.
 */
internal fun weekStartOf(
    at: Long,
    zone: TimeZone,
    locale: Locale,
): Long {
    val calendar = Calendar.getInstance(zone, locale)
    calendar.timeInMillis = at
    val back = (calendar.get(Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek + 7) % 7
    calendar.add(Calendar.DAY_OF_YEAR, -back)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

/**
 * Seconds played on each of the 7 days bounded by [dayStarts], days after [today] 0 and today up to [to]: each
 * session counted as `PlayDao.secondsPlayedBetween` counts it over each day's range (spread evenly over its start to
 * end; one whose end is not after its start whole on the day its end falls in).
 */
internal fun daySecondsOf(
    sessions: List<SessionSpan>,
    dayStarts: List<Long>,
    today: Int,
    to: Long,
): List<Long> =
    List(7) { day ->
        if (day > today) return@List 0L
        val from = dayStarts[day]
        val until = minOf(dayStarts[day + 1], to)
        sessions.sumOf { span ->
            when {
                span.endedAt <= span.startedAt -> if (span.endedAt in from until until) span.seconds else 0L
                else -> {
                    val overlap = minOf(span.endedAt, until) - maxOf(span.startedAt, from)
                    if (overlap > 0 && span.endedAt >= from) span.seconds * overlap / (span.endedAt - span.startedAt) else 0L
                }
            }
        }
    }

/**
 * The 8 midnights (in [zone]) that bound the 7 days from [weekStart]: each day's start and the end
 * of the last. Walked with `Calendar.add(DAY_OF_YEAR, 1)`, so a daylight-saving day is 23 or 25 hours.
 */
internal fun dayStartsOf(
    weekStart: Long,
    zone: TimeZone,
): List<Long> {
    val calendar = Calendar.getInstance(zone)
    calendar.timeInMillis = weekStart
    return List(8) {
        val at = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        at
    }
}
