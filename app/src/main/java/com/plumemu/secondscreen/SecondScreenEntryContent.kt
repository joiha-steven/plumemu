package com.plumemu.secondscreen

import androidx.compose.runtime.Immutable
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.game.EmulatorToStart
import com.plumemu.home.ArtPicture
import com.plumemu.home.SystemInfo

/*
 * The system column's cards (a system, Recent, Favorites). Each is derived at the cursor with what the home screen already holds
 * (names, counts, the first favourites), and its figures are read after the settle ([SecondScreenEntryReads]): until then only the header shows.
 */

/** The names and shape a game's cover needs of its system (a mixed list such as Recent holds several systems). */
@Immutable
internal data class EntrySystemLook(
    val name: String,
    val short: String,
    /** Width over height of the system's cover shape: the poster's, and a cover's before its art is known. */
    val aspect: Float,
    /** The caption under a cover on Recent and Favorites ("SNES", "Mega Drive"); empty: [name]. */
    val caption: String = "",
)

/** One cover on a card: the game, and its box front once read (null: the poster stands in). */
@Immutable
internal data class EntryGame(
    val id: Long,
    val title: String,
    val systemId: String,
    /** The key its saved result is read under; null while box art is off (the poster). */
    val scrapeKey: String?,
    /** When it was last played (milliseconds); null where the card shows no time. */
    val playedAt: Long? = null,
    val art: ArtPicture? = null,
)

/** The cards of the system column. */
@Immutable
internal sealed interface EntryContent : SecondScreenContent {
    val name: String

    /** The number beside the name. */
    val gameCount: Int

    /** [this] with the figures [previous] (the card of the same entry before) had read, when this one has none yet. */
    fun keepingFiguresOf(previous: SecondScreenContent): SecondScreenContent
}

/** A system's own entry. */
@Immutable
internal data class SystemEntry(
    val systemId: String,
    override val name: String,
    override val gameCount: Int,
    /** Its badge, facts line and about text; null for a system with no short name (the foot is left out). */
    val info: SystemInfo? = null,
    /** The system's cover shape, width over height: its posters'. */
    val coverAspect: Float = CoverShape.Landscape.widthOverHeight,
    /** Box art is on (an account is signed in): the covers read their pictures; off, they are posters. */
    val hasBoxArt: Boolean = false,
    val figures: SystemFigures? = null,
) : EntryContent {
    override fun keepingFiguresOf(previous: SecondScreenContent): SecondScreenContent =
        if (figures == null && previous is SystemEntry && previous.systemId == systemId && previous.figures != null) copy(figures = previous.figures) else this
}

/** What a system card reads after the settle. */
@Immutable
internal data class SystemFigures(
    /** Last played, newest first, at most [com.plumemu.coreui.theme.EntryCardDimens.SYSTEM_COVERS]. */
    val lastPlayed: List<EntryGame>,
    val playSeconds: Long,
    val playedGames: Int,
    /** Null while signed out, or with no stored progress on the system (the tile is left out). */
    val achievements: SystemAchievements?,
    val newThisWeek: Int,
    val leftOutFiles: Int,
    /** The emulator Play would start for the system; null: none installed. */
    val emulator: EmulatorToStart?,
    /** The clock the times are counted from. */
    val now: Long,
)

/** The person's RetroAchievements totals on one system. */
@Immutable
internal data class SystemAchievements(
    val mastered: Int,
    val gamesWithProgress: Int,
    /** Points earned; null when no game of the system has its points stored yet. */
    val points: Long?,
)

/** Recent. */
@Immutable
internal data class RecentEntry(
    override val name: String,
    override val gameCount: Int,
    /** The systems of its games, by id. */
    val systems: Map<String, EntrySystemLook> = emptyMap(),
    val hasBoxArt: Boolean = false,
    val figures: RecentFigures? = null,
) : EntryContent {
    override fun keepingFiguresOf(previous: SecondScreenContent): SecondScreenContent =
        if (figures == null && previous is RecentEntry && previous.figures != null) copy(figures = previous.figures) else this
}

/** What the Recent card reads after the settle. */
@Immutable
internal data class RecentFigures(
    val games: List<EntryGame>,
    val week: WeekFigures,
    val now: Long,
)

/** This week's play: seconds per day from the week's first day, the day it is, the week's games, and last week's time. */
@Immutable
internal data class WeekFigures(
    val daySeconds: List<Long>,
    val todayIndex: Int,
    val games: Int,
    val lastWeekSeconds: Long,
    /** The week's first day, 1 = Sunday as `Calendar.DAY_OF_WEEK`; the bars' names start from it. */
    val firstDayOfWeek: Int,
) {
    val totalSeconds: Long get() = daySeconds.sum()
}

/** Favorites. */
@Immutable
internal data class FavoritesEntry(
    override val name: String,
    override val gameCount: Int,
    /** How many systems the favourites are on. */
    val systemCount: Int,
    /** The first favourites in the list's order, at most [FAVORITES_CARRIED]: more than any card can show. */
    val games: List<EntryGame>,
    val systems: Map<String, EntrySystemLook> = emptyMap(),
    val hasBoxArt: Boolean = false,
    /** The pictures have been read (or none are to be). */
    val isArtRead: Boolean = !hasBoxArt,
) : EntryContent {
    override fun keepingFiguresOf(previous: SecondScreenContent): SecondScreenContent =
        if (!isArtRead && previous is FavoritesEntry && previous.isArtRead && previous.games.map { it.id } == games.map { it.id }) copy(games = previous.games, isArtRead = true) else this
}

/** The most favourites a card carries: three rows of the narrowest covers. */
internal const val FAVORITES_CARRIED = 24
