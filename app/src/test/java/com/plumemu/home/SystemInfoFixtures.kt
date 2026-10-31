package com.plumemu.home

import com.plumemu.R
import com.plumemu.platforms.PlatformCatalog
import com.plumemu.platforms.PlatformLoadResult
import com.plumemu.platforms.PlatformLoader
import com.plumemu.platforms.SystemKind
import java.io.File

/** The platform data's facts for four systems, as `platforms.json` ships them. */
internal val SnesInfo = SystemInfo("SNES", "Nintendo", SystemKind.Console, 1990, 2003)
internal val NdsInfo = SystemInfo("DS", "Nintendo", SystemKind.Handheld, 2004)
internal val SwitchInfo = SystemInfo("NSW", "Nintendo", SystemKind.Console, 2017, inProduction = true)
internal val ArcadeInfo = SystemInfo("ARC")

/** SNES and Arcade as the app builds them for the shipped systems: with their about text (and Arcade's "many makers" facts). */
internal val SnesAboutInfo = SnesInfo.copy(about = R.string.system_about_snes)
internal val ArcadeAboutInfo = SystemInfo("ARC", manyMakers = true, about = R.string.system_about_arcade)

/** Board system ids that stand in for the shipped systems in [withSystemInfo]: the board has no Switch or Arcade. */
internal const val BOARD_SWITCH_ID = "gba"
internal const val BOARD_ARCADE_ID = "gc"

/** The board with the info of SNES (snes), DS (nds), Switch (in gba's place) and Arcade (in gc's place). */
internal fun HomeContent.withSystemInfo(): HomeContent =
    withEntries(
        entries.map { entry ->
            when (entry.id) {
                "snes" -> entry.copy(info = SnesInfo)
                "nds" -> entry.copy(info = NdsInfo)
                BOARD_SWITCH_ID -> entry.copy(info = SwitchInfo)
                BOARD_ARCADE_ID -> entry.copy(info = ArcadeInfo)
                else -> entry
            }
        },
    )

/** The board with SNES carrying its about text. */
internal fun HomeContent.withSnesAbout(): HomeContent = withEntries(entries.map { if (it.id == "snes") it.copy(info = SnesAboutInfo) else it })

/** The board with Arcade's info (facts "Arcade games from many makers" and its about text) in gc's place. */
internal fun HomeContent.withArcadeAbout(): HomeContent = withEntries(entries.map { if (it.id == BOARD_ARCADE_ID) it.copy(info = ArcadeAboutInfo) else it })

/** The words the facts line is made of in English (strings.xml), for tests that need no resources. */
internal fun SystemInfo.factsIn(
    sep: String = " · ",
    present: String = "present",
): List<String> =
    SystemFacts(
        maker,
        kind?.let {
            when (it) {
                SystemKind.Console -> "Home console"
                SystemKind.Handheld -> "Handheld"
                SystemKind.Arcade -> "Arcade board"
                SystemKind.Computer -> "Computer"
                SystemKind.AddOn -> "Add-on"
                SystemKind.Os -> "Operating system"
            }
        },
        SystemFacts.yearsOf(this, present),
        noMaker = if (manyMakers) "Arcade games from many makers" else null,
    ).candidates(sep)

/** The catalog the app ships (`platforms.json`), for tests that need the real ids and facts. */
internal val shippedCatalog: PlatformCatalog =
    (PlatformLoader.load(File("../platforms/src/main/assets/platforms.json").readText(), "shipped") as PlatformLoadResult.Loaded).catalog

internal fun shippedSystem(id: String) = shippedCatalog.system(id)!!
