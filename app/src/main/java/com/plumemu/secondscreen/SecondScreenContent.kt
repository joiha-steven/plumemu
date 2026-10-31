package com.plumemu.secondscreen

import androidx.compose.runtime.Immutable
import com.plumemu.battery.BatteryPace
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.game.EmulatorToStart
import com.plumemu.home.ArtPicture
import com.plumemu.home.GameDetails
import com.plumemu.home.SystemInfo
import com.plumemu.settings.screen.PlayStatistics

/**
 * What the second screen shows while the person is picking. Plain values, derived from the home focus by `secondScreenContentOf`; the play
 * facts are filled in after the settle by [SecondScreenPlayFacts].
 */
@Immutable
internal sealed interface SecondScreenContent {
    /** The game under the cursor. */
    data class Game(
        val title: String,
        val systemName: String,
        val region: String? = null,
        val isFavorite: Boolean = false,
        val lastPlayedDaysAgo: Int? = null,
        val playTimeMinutes: Int? = null,
        /** The library's game id, whose play facts are read after the settle; null for a game the database does not hold. */
        val gameId: Long? = null,
        /**
         * False until the play facts have been read ([SecondScreenPlayFacts]): the card shows no
         * facts line then, never "Not played yet" before the answer.
         */
        val isPlayKnown: Boolean = true,
        /**
         * The key its ScreenScraper result is stored under; set only while an account is signed in
         * (the home screen's gate, `HomeContent.hasBoxArt`), so the card reads art for nobody else.
         */
        val scrapeKey: String? = null,
        /** The saved box front, read with the play facts after the settle; null: none, or not read yet (the card without art). */
        val art: ArtPicture? = null,
        /**
         * Its ScreenScraper facts and description (the top screen's details column shows the same):
         * from the row when it carries them (the fakes), else read with the cover after the settle.
         * A game with any of them, or with [art], gets the details card (layout C); null: not
         * read yet, or no result.
         */
        val details: GameDetails? = null,
        /**
         * The emulator Play would start, read with the play facts; it opens the played line
         * ("DuckStation · Played 3 days ago"). Null: not read (no reader: the benchmark, tests).
         */
        val emulator: EmulatorFact? = null,
        /**
         * The file Play launches, as the Info panel's "File" row shows it (`GameEntity.fileName`, with
         * its extension; the card drops the extension, [fileNameWithoutExtension]). Read with the
         * play facts; null until read, or for a game the database does not hold (no line).
         */
        val fileName: String? = null,
        /**
         * The top screen shows this game in its system's grid, cover and all:
         * the details card leaves its cover out (nothing decoded for it) and gives the description
         * the room ([SecondScreenDimens.descriptionMaxLinesBesideGrid]). False in a list.
         */
        val isCoverOnTopScreen: Boolean = false,
        /**
         * The game's usual battery pace, read with the play facts; the line under the
         * played line. Null: never measured, or not read (no line).
         */
        val batteryPace: BatteryPace? = null,
    ) : SecondScreenContent

    /** A system, Recent or Favorites: its name, its size, and the last game played in it. */
    data class System(
        val name: String,
        val gameCount: Int,
        val lastGameTitle: String? = null,
        val lastPlayedDaysAgo: Int? = null,
        /** Where its last game is read from after the settle; null: nothing to read (no facts line). */
        val lastPlayFrom: LastPlayLookup? = null,
        /** The system's badge and facts line; null for Recent, Favorites and a system with no short name. */
        val info: SystemInfo? = null,
    ) : SecondScreenContent

    /** The Apps entry. */
    data class Apps(
        val appCount: Int,
    ) : SecondScreenContent

    /** One Android app. */
    data class App(
        val name: String,
    ) : SecondScreenContent

    /** Nothing to say (Settings before its figures arrive, search before a result, an empty library): only the Plumemu mark. */
    data object Calm : SecondScreenContent

    /** The setup question is up: one quiet line showing which screen is meant. */
    data object SetupQuestion : SecondScreenContent

    /**
     * Settings is open and its Statistics figures have been read: the same figures, from the
     * same read ([com.plumemu.settings.screen.rememberStatistics]), as one larger card. Never for a
     * failed read or an empty library (Calm then).
     */
    data class Statistics(
        val statistics: PlayStatistics,
    ) : SecondScreenContent
}

/** The emulator Play would start for a game, once read; [toStart] null when none is installed. */
@Immutable
internal data class EmulatorFact(
    val toStart: EmulatorToStart?,
)

/** Where a [SecondScreenContent.System] card's last game comes from. */
@Immutable
sealed interface LastPlayLookup {
    /** Recent: its first game, already known; only its play is read. */
    data class FirstGame(
        val gameId: Long,
        val title: String,
    ) : LastPlayLookup

    /** A system: the newest play among the games its list shows. */
    data class InSystem(
        val systemId: String,
    ) : LastPlayLookup

    /** Favorites: the newest play among the favourites. */
    data object AmongFavorites : LastPlayLookup
}
