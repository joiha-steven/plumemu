package com.plumemu.secondscreen

import com.plumemu.R
import com.plumemu.home.ArcadeAboutInfo
import com.plumemu.home.SnesAboutInfo
import com.plumemu.home.SnesInfo
import com.plumemu.home.SystemInfo
import com.plumemu.platforms.SystemKind

/** The two sizes of a second screen: the Thor's bottom screen (Roomy) and a 4:3 one (Compact), in dp. */
enum class SecondSize(
    val widthDp: Int,
    val heightDp: Int,
) {
    Roomy(538, 468),
    Compact(480, 360),
}

internal val APP_LONG_NAME = SecondScreenContent.App("The Extremely Long Application Name That Cannot Possibly Fit On A Single Line Of The Card", "com.example.averyveryverylongpackagename.withmanysegments.activity/.Main")

internal const val LONG_TITLE = "Final Fantasy VII International Disc 1 of 4 Special Collector's Edition with the Bonus Disc and More"

/** The emulator Play would start, as the feed reads it with the play facts. */
internal fun emulatorNamed(name: String) = EmulatorFact(com.plumemu.game.EmulatorToStart(name, isInstalled = true))

/** Nothing chosen for the system yet: the longest of the Info panel's emulator words. */
internal val EMULATOR_NOT_CHOSEN = EmulatorFact(com.plumemu.game.EmulatorToStart("", isInstalled = true, isNotChosenYet = true))

/** No emulator of the system is installed. */
internal val EMULATOR_NONE = EmulatorFact(null)

internal val GAME_WITH_STAR =
    SecondScreenContent.Game("Super Metroid", "Super Nintendo", "USA", isFavorite = true, lastPlayedDaysAgo = 1, playTimeMinutes = 200, emulator = emulatorNamed("Snes9x"))
internal val GAME_NEVER_PLAYED = SecondScreenContent.Game("Chrono Trigger", "Super Nintendo", "USA", emulator = EMULATOR_NOT_CHOSEN)
internal val GAME_LONG_TITLE =
    SecondScreenContent.Game(LONG_TITLE, "PlayStation", "Japan", isFavorite = true, lastPlayedDaysAgo = 9, playTimeMinutes = 45, emulator = emulatorNamed("DuckStation"))
internal val GAME_NO_REGION = SecondScreenContent.Game("Tetris", "Game Boy", null, lastPlayedDaysAgo = 0, playTimeMinutes = 20, emulator = EMULATOR_NONE)
/** Now for the entry samples: Wednesday noon; the "when" under a cover counts back from it. */
internal const val ENTRY_NOW = 1_794_052_800_000L
private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 24 * HOUR_MS

private val SAMPLE_TITLES = listOf("Super Metroid", "Zelda: A Link to the Past", "Chrono Trigger", "Donkey Kong Country", "F-Zero", "Super Mario World", "Kirby Super Star", "Earthbound")
private val SAMPLE_AGES = listOf(2 * HOUR_MS, 26 * HOUR_MS, 3 * DAY_MS, 5 * DAY_MS, 9 * DAY_MS, 20 * DAY_MS, 40 * DAY_MS, 90 * DAY_MS)

private fun sampleGames(
    count: Int,
    systemId: String = "snes",
    withTime: Boolean = true,
) = List(count) { EntryGame(it + 1L, SAMPLE_TITLES[it % SAMPLE_TITLES.size], systemId, null, if (withTime) ENTRY_NOW - SAMPLE_AGES[it % SAMPLE_AGES.size] else null) }

private fun systemFigures(
    covers: Int = 6,
    ra: SystemAchievements? = SystemAchievements(12, 31, 2480),
    newThisWeek: Int = 3,
    leftOut: Int = 4,
    emulator: com.plumemu.game.EmulatorToStart? = com.plumemu.game.EmulatorToStart("RetroArch", isInstalled = true, coreName = "Snes9x"),
) = SystemFigures(sampleGames(covers), 3_600L * 41, 36, ra, newThisWeek, leftOut, emulator, ENTRY_NOW)

/** Every part: six covers, three tiles with points, the status line, maker line and about text. */
internal val SYSTEM_WITH_LAST = SystemEntry("snes", "Super Nintendo", 248, SnesAboutInfo, figures = systemFigures())

/** Played, signed in to RA, no points stored: the tile says games with progress. */
internal val SYSTEM_RA_NO_POINTS = SYSTEM_WITH_LAST.copy(figures = systemFigures(ra = SystemAchievements(0, 5, null)))

/** Played but not signed in to RA, nothing new, nothing left out, no emulator read: two tiles and no status line. */
internal val SYSTEM_NO_RA = SYSTEM_WITH_LAST.copy(figures = systemFigures(ra = null, newThisWeek = 0, leftOut = 0, emulator = null))

/** Never played: no covers, zeros. */
internal val SYSTEM_NEVER_PLAYED =
    SystemEntry("gba", "Game Boy Advance", 1, SystemInfo("GBA", "Nintendo", SystemKind.Handheld, 2001, 2008), figures = SystemFigures(emptyList(), 0, 0, null, 0, 0, null, ENTRY_NOW))

/** A system with no game at all. */
internal val SYSTEM_EMPTY = SystemEntry("ps2", "PlayStation 2", 0, figures = SystemFigures(emptyList(), 0, 0, null, 0, 0, null, ENTRY_NOW))

/** A system with no info (no short name): no maker line, no about text. */
internal val SYSTEM_NO_INFO = SystemEntry("homebrew", "Homebrew", 12, null, figures = systemFigures(covers = 3, ra = null))

/** A long system name and the longest facts: the title takes its lines, the maker line drops the kind, then the maker. */
internal val SYSTEM_LONG_NAME =
    SystemEntry(
        "nes",
        "Nintendo Entertainment System (Famicom and Family Computer Disk System)",
        19,
        SystemInfo("NES", "Nintendo of America Incorporated and Related Companies", SystemKind.Console, 1983, 1995, about = R.string.system_about_nes),
        figures = systemFigures(),
    )

/** Vietnamese capitals with stacked marks in the name, a cover's title and the about text. */
internal val SYSTEM_STACKED_MARKS =
    SystemEntry(
        "snes",
        "\u1EA4\u1EB0\u1EA8 Super Nintendo \u1EC0\u1ED4",
        248,
        SnesAboutInfo,
        figures = systemFigures().copy(lastPlayed = sampleGames(6).mapIndexed { at, game -> if (at == 0) game.copy(title = "\u1EA4\u1EB0\u1EA8 \u1EC0\u1ED4\u1ED6") else game }),
    )

/** Arcade: its facts say many makers. */
internal val SYSTEM_ABOUT_ARCADE = SystemEntry("arcade", "Arcade", 1530, ArcadeAboutInfo, figures = systemFigures(ra = null))

private fun recentSystems() =
    mapOf(
        "snes" to EntrySystemLook("Super Nintendo", "SNES", 1.4f, caption = "SNES"),
        "gba" to EntrySystemLook("Game Boy Advance", "GBA", 1.5f, caption = "GBA"),
        "md" to EntrySystemLook("Mega Drive", "MD", 1.4f),
    )

private val WEEK_DAYS = listOf(0L, 5_400L, 2_700L, 9_000L, 0L, 0L, 0L)

private fun recentGames(count: Int) = List(count) { sampleGames(8)[it].copy(systemId = listOf("snes", "gba", "md")[it % 3]) }

/** Five covers with when and system, and the week with a bar per day. */
internal val RECENT_FULL = RecentEntry("Recent", 14, recentSystems(), figures = RecentFigures(recentGames(5), WeekFigures(WEEK_DAYS, 3, 4, 3_600L * 6, java.util.Calendar.MONDAY), ENTRY_NOW))

/** One game played, all in the week. */
internal val RECENT_ONE = RecentEntry("Recent", 1, recentSystems(), figures = RecentFigures(recentGames(1), WeekFigures(listOf(0, 0, 0, 1_800, 0, 0, 0), 3, 1, 0, java.util.Calendar.MONDAY), ENTRY_NOW))

/** Played, but not this week: empty bars and last week's time. */
internal val RECENT_NOTHING_THIS_WEEK =
    RecentEntry("Recent", 5, recentSystems(), figures = RecentFigures(recentGames(5), WeekFigures(List(7) { 0L }, 3, 0, 3_600L * 3, java.util.Calendar.MONDAY), ENTRY_NOW))

/** Nothing played yet: the header only. */
internal val RECENT_EMPTY = RecentEntry("Recent", 0, recentSystems(), figures = RecentFigures(emptyList(), WeekFigures(List(7) { 0L }, 3, 0, 0, java.util.Calendar.MONDAY), ENTRY_NOW))

private fun favoriteGames(count: Int) = List(count) { EntryGame(it + 1L, SAMPLE_TITLES[it % SAMPLE_TITLES.size], listOf("snes", "gba", "md")[it % 3], null) }

/** More than fit three rows: "N shown of M". */
internal val FAVORITES_MANY = FavoritesEntry("Favorites", 30, 6, favoriteGames(FAVORITES_CARRIED), recentSystems())

/** Fewer than one row. */
internal val FAVORITES_FEW = FavoritesEntry("Favorites", 3, 2, favoriteGames(3), recentSystems())

internal val FAVORITES_EMPTY = FavoritesEntry("Favorites", 0, 0, emptyList(), recentSystems())

/** The key the shots' fake icons have no icon for: its card shows the letter stand-in. */
internal const val APP_WITHOUT_ICON_KEY = "com.example.noicon/.Main"

/** The Apps entry: 128 apps, the first 120 carried as icons; the card draws as many as fit. */
internal val APPS_WITH_ICONS = SecondScreenContent.Apps(128, (0 until MAX_APP_ICONS).map { AppIconRef(if (it == 2) APP_WITHOUT_ICON_KEY else "com.example.app$it/.Main", "App $it") })

/** Every content kind with the name its shot has. */
internal val ALL_SAMPLES: List<Pair<String, SecondScreenContent>> =
    listOf(
        "game_star" to GAME_WITH_STAR,
        "game_never_played" to GAME_NEVER_PLAYED,
        "game_long_title" to GAME_LONG_TITLE,
        "game_no_region" to GAME_NO_REGION,
        "system_played" to SYSTEM_WITH_LAST,
        "system_ra_no_points" to SYSTEM_RA_NO_POINTS,
        "system_no_ra" to SYSTEM_NO_RA,
        "system_never_played" to SYSTEM_NEVER_PLAYED,
        "system_empty" to SYSTEM_EMPTY,
        "system_no_info" to SYSTEM_NO_INFO,
        "system_long_name" to SYSTEM_LONG_NAME,
        "system_stacked_marks" to SYSTEM_STACKED_MARKS,
        "system_about_arcade" to SYSTEM_ABOUT_ARCADE,
        "recent_full" to RECENT_FULL,
        "recent_one" to RECENT_ONE,
        "recent_nothing_this_week" to RECENT_NOTHING_THIS_WEEK,
        "recent_empty" to RECENT_EMPTY,
        "favorites_many" to FAVORITES_MANY,
        "favorites_few" to FAVORITES_FEW,
        "favorites_empty" to FAVORITES_EMPTY,
        "apps" to APPS_WITH_ICONS,
        "app" to SecondScreenContent.App("RetroArch", "com.retroarch/.Main"),
        "app_no_icon" to SecondScreenContent.App("Notes", APP_WITHOUT_ICON_KEY),
        "app_long_name" to APP_LONG_NAME,
        "calm" to SecondScreenContent.Calm,
        "setup_question" to SecondScreenContent.SetupQuestion,
    )
