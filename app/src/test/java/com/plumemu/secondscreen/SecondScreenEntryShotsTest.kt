package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.SecondScreenText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.game.EmulatorToStart
import com.plumemu.home.SnesAboutInfo
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

private const val NOW = ENTRY_NOW
private const val H = 3_600_000L
private const val D = 24 * H

private fun game(
    id: Long,
    title: String,
    system: String,
    ago: Long,
) = EntryGame(id, title, system, null, NOW - ago)

private val LOOKS =
    mapOf(
        "snes" to EntrySystemLook("Super Nintendo", "SNES", 1.4f),
        "md" to EntrySystemLook("Mega Drive", "MD", 0.7f),
        "gba" to EntrySystemLook("Game Boy Advance", "GBA", 0.7f),
        "ps" to EntrySystemLook("PlayStation", "PS", 0.7f),
        "gb" to EntrySystemLook("Game Boy", "GB", 0.7f),
    )

/** The boards' `SecondSystem`: Super Nintendo with six covers, three tiles, one status line and its foot. */
private val BOARD_SYSTEM =
    SystemEntry(
        "snes", "Super Nintendo", 248, SnesAboutInfo, 1.4f,
        figures =
            SystemFigures(
                lastPlayed =
                    listOf(
                        game(1, "Super Metroid", "snes", 2 * H), game(2, "Chrono Trigger", "snes", 26 * H), game(3, "Secret of Mana", "snes", 3 * D),
                        game(4, "F-Zero", "snes", 9 * D), game(5, "Contra III", "snes", 20 * D), game(6, "Star Fox", "snes", 34 * D),
                    ),
                playSeconds = 42 * 3_600L + 10 * 60, playedGames = 37, achievements = SystemAchievements(5, 12, 2_140), newThisWeek = 3, leftOutFiles = 4,
                emulator = EmulatorToStart("RetroArch", isInstalled = true, coreName = "Snes9x"), now = NOW,
            ),
    )

/** The boards' `SecondRecent`: five covers with when and system, and the week. */
private val BOARD_RECENT =
    RecentEntry(
        "Recent", 24, LOOKS,
        figures =
            RecentFigures(
                listOf(
                    game(1, "Super Metroid", "snes", 2 * H), game(2, "Sonic", "md", 26 * H), game(3, "Metroid Fusion", "gba", 30 * H),
                    game(4, "Crash", "ps", 3 * D), game(5, "Tetris", "gb", 9 * D),
                ),
                WeekFigures(listOf(5_400L, 0, 9_000, 2_700, 0, 12_000, 2_400), 6, 5, 3 * 3_600L + 5 * 60, Calendar.MONDAY), NOW,
            ),
    )

private val FAVORITE_TITLES =
    listOf(
        "Super Metroid" to "snes", "F-Zero" to "snes", "Sonic" to "md", "Crash" to "ps", "Chrono Trigger" to "snes", "Contra III" to "snes", "Star Fox" to "snes",
        "Tetris" to "gb", "Doom" to "md", "Mario Kart" to "snes", "Metroid Fusion" to "gba", "Ico" to "ps", "Zelda" to "gba", "Kirby" to "gb", "Mother 3" to "gba",
        "Gradius" to "snes", "Pokemon" to "gb", "Wario Land" to "gb",
    )

/** The boards' `SecondFavorites`: 18 favourites on 6 systems, as many shown as three rows hold. */
private val BOARD_FAVORITES = FavoritesEntry("Favorites", 18, 6, FAVORITE_TITLES.mapIndexed { at, (title, system) -> EntryGame(at + 1L, title, system, null) }, LOOKS)

/**
 * The three system column cards at the Thor's bottom screen (538x468 dp, 369 dpi) on Graphite and Paper, and at 4:3 (480x360): the
 * PNGs compared with the design boards (`SecondSystem`, `SecondRecent`, `SecondFavorites` and their Paper versions).
 */
abstract class SecondScreenEntryShotsBase(
    private val label: String,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(SecondScreenText.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    private fun hostTheCard() {
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content) } }
    }

    protected fun shootAll(looks: List<Pair<String, PlumThemeSettings>>) {
        hostTheCard()
        val mismatches = mutableListOf<String>()
        for ((look, base) in looks) {
            for ((name, card) in listOf("system" to BOARD_SYSTEM, "recent" to BOARD_RECENT, "favorites" to BOARD_FAVORITES)) {
                settings = base
                content = card
                compose.waitForIdle()
                val file = "second_screen_${name}_board_${label}_$look"
                try {
                    compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$file.png")
                } catch (e: AssertionError) {
                    mismatches += "$file: ${e.message}"
                }
            }
        }
        if (mismatches.isNotEmpty()) fail("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w538dp-h468dp-land-369dpi")
class SecondScreenEntryShotsRoomyTest : SecondScreenEntryShotsBase("roomy") {
    @Test
    fun theThreeCardsOnGraphiteAndPaper() = shootAll(listOf("graphite" to SecondScreenText.Graphite, "paper" to SecondScreenText.Paper))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w480dp-h360dp-land-369dpi")
class SecondScreenEntryShotsCompactTest : SecondScreenEntryShotsBase("compact") {
    @Test
    fun theThreeCardsOnGraphite() = shootAll(listOf("graphite" to SecondScreenText.Graphite))
}
