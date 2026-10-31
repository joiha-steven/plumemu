package com.plumemu.secondscreen

import com.plumemu.R
import com.plumemu.home.ArcadeAboutInfo
import com.plumemu.home.ArcadeInfo
import com.plumemu.home.SnesAboutInfo
import com.plumemu.home.SnesInfo
import com.plumemu.home.SwitchInfo
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
internal val SYSTEM_WITH_LAST = SecondScreenContent.System("Super Nintendo", 248, "Super Metroid", 1, info = SnesInfo)
internal val SYSTEM_WITHOUT_LAST =
    SecondScreenContent.System("Game Boy Advance", 1, info = SystemInfo("GBA", "Nintendo", SystemKind.Handheld, 2001, 2008))

/** A system still in production: "2017–present". */
internal val SYSTEM_IN_PRODUCTION = SecondScreenContent.System("Nintendo Switch", 87, "Hades", 3, info = SwitchInfo)

/** No facts (a game engine, a store): the badge alone, no facts line. */
internal val SYSTEM_NO_FACTS = SecondScreenContent.System("ScummVM", 12, "Monkey Island", 40, info = ArcadeInfo)

/** A long system name and the longest facts: the title takes its lines, the facts line drops the kind, then the maker. */
internal val SYSTEM_LONG_NAME =
    SecondScreenContent.System(
        "Nintendo Entertainment System (Famicom and Family Computer Disk System)",
        19,
        "Super Mario Bros.",
        2,
        info = SystemInfo("NES", "Nintendo of America Incorporated and Related Companies", SystemKind.Console, 1983, 1995),
    )

/** The shipped systems' about text: SNES with its last game, Arcade with none (its facts line says "many makers"). */
internal val SYSTEM_ABOUT_SNES = SecondScreenContent.System("Super Nintendo", 248, "Super Metroid", 1, info = SnesAboutInfo)
internal val SYSTEM_ABOUT_ARCADE = SecondScreenContent.System("Arcade", 1530, info = ArcadeAboutInfo)

/** A long name with about text: the title takes its lines first, so the about text is the part that gives way. */
internal val SYSTEM_LONG_NAME_ABOUT = SYSTEM_LONG_NAME.copy(info = SYSTEM_LONG_NAME.info!!.copy(about = R.string.system_about_nes))

/** Recent, Favorites: no badge, no facts line. */
internal val SYSTEM_RECENT = SecondScreenContent.System("Recent", 14, "Super Metroid", 1)

/** Every content kind with the name its shot has. */
internal val ALL_SAMPLES: List<Pair<String, SecondScreenContent>> =
    listOf(
        "game_star" to GAME_WITH_STAR,
        "game_never_played" to GAME_NEVER_PLAYED,
        "game_long_title" to GAME_LONG_TITLE,
        "game_no_region" to GAME_NO_REGION,
        "system_last_game" to SYSTEM_WITH_LAST,
        "system_no_last_game" to SYSTEM_WITHOUT_LAST,
        "system_in_production" to SYSTEM_IN_PRODUCTION,
        "system_no_facts" to SYSTEM_NO_FACTS,
        "system_long_name" to SYSTEM_LONG_NAME,
        "system_about_snes" to SYSTEM_ABOUT_SNES,
        "system_about_arcade" to SYSTEM_ABOUT_ARCADE,
        "system_recent" to SYSTEM_RECENT,
        "apps" to SecondScreenContent.Apps(128),
        "app" to SecondScreenContent.App("RetroArch"),
        "calm" to SecondScreenContent.Calm,
        "setup_question" to SecondScreenContent.SetupQuestion,
    )

/** A system whose last game has a very long title: the name is cut, "yesterday" stays. Shot on Black only. */
internal val SYSTEM_LONG_LAST_GAME = SecondScreenContent.System("PlayStation", 1284, LONG_TITLE, 1)
