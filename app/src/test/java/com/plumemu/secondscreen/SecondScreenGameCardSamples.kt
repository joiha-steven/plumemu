package com.plumemu.secondscreen

import androidx.compose.ui.graphics.ImageBitmap
import com.plumemu.game.BadgeImages
import com.plumemu.graph.StoredAchievements
import com.plumemu.graph.StoredBadge
import com.plumemu.home.GameDetails

/** Samples of the game card, as the boards `SecondGame*` draw it (Super Metroid: 34 of 60, 410 / 680 points, 2 h 20 min in 9 sessions). */
internal const val BOARD_DESCRIPTION =
    "Samus Aran brings the last Metroid to a research station, and soon after its distress call she is back on Zebes, " +
        "where Ridley has taken the creature to rebuild the space pirates' base. The planet opens up as she finds new " +
        "suits and weapons: the Morph Ball, the Grapple Beam, the Speed Booster. Paths seen early are reached hours later, " +
        "and the map fills in as she goes. The ending depends on how quickly the planet is escaped, and a hundred per cent " +
        "of the items can be found by those who look under every wall and ceiling."

internal const val BOARD_FILE = "Super Metroid (Japan, USA) (En,Ja).sfc"

private val BADGES = List(5) { StoredBadge(if (it == 0) "Kraid's Lair cleared" else "Badge $it", "https://example.test/badge/$it.png", 1_000L - it) }

internal val BOARD_RA = StoredAchievements(earned = 34, total = 60, pointsEarned = 410, pointsTotal = 680, recent = BADGES)

/** 2 h 20 min over 9 sessions in the eight weeks, the board's bars (20, 40, 0, 10, 70, 30, 10 and 50 of 70 px). */
internal val BOARD_WEEKS = PlayWeeks(weekSeconds = listOf(730L, 1_461L, 0L, 365L, 2_557L, 1_096L, 365L, 1_826L), sessions = 9, totalSeconds = 8_400L)

/** The game card's content as the board has it. */
internal val BOARD_GAME =
    SecondScreenContent.Game(
        title = "Super Metroid",
        systemName = "Super Nintendo",
        systemShort = "SNES",
        lastPlayedDaysAgo = 2,
        playTimeMinutes = 140,
        gameId = 1L,
        fileName = BOARD_FILE,
        details = GameDetails(year = 1994, publisher = "Nintendo", genre = "Action", players = "1", description = BOARD_DESCRIPTION),
        weeks = BOARD_WEEKS,
        ra = BOARD_RA,
        achievements = BOARD_RA.count,
        raLatestDaysAgo = 2,
    )

/** Signed out, or no stored progress: the play tile takes the whole width. */
internal val BOARD_GAME_NO_RA = BOARD_GAME.copy(ra = null, achievements = null, raLatestDaysAgo = null)

/** Badges that only ever answer from the cache: [loads] counts the calls that could reach the network. */
internal class CacheOnlyBadges(private val pictures: Boolean = false) : BadgeImages {
    var loads = 0
    var cachedLoads = 0

    override fun cached(url: String, sizePx: Int): ImageBitmap? = null

    override suspend fun load(url: String, sizePx: Int): ImageBitmap? {
        loads++
        return null
    }

    override suspend fun loadCached(url: String, sizePx: Int): ImageBitmap? {
        cachedLoads++
        return if (pictures) ImageBitmap(sizePx, sizePx) else null
    }
}
