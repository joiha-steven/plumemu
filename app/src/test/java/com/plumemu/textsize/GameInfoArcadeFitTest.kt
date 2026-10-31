package com.plumemu.textsize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.ArcadeSetFacts
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.GameFacts
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.NameCertainty
import com.plumemu.game.NameFacts
import com.plumemu.game.NameOrigin
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Info of an arcade game, its Arcade set row (short name and the description's bracket texts) in
 * the File row's place, at every text size, the way `TextSizeScreensTest.gamePanelInfo` checks Info
 * (kept apart: that file is at the length limit): at the six sizes in Plum Sans and at Large and
 * Largest in Serif and Mono, no text is ellipsized below Largest, clipped, too short or on top of
 * another, and every fact row can be read whole with Up and Down ([InfoReadThrough]). Only a
 * deliberately long detail may end in an ellipsis below Largest.
 */
abstract class GameInfoArcadeFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun arcadeFacts(
        title: String,
        set: ArcadeSetFacts,
    ): GameFacts =
        sampleFacts(title = title).copy(
            systemName = "Arcade",
            regions = "USA",
            fileName = "${set.shortName}.zip",
            folderName = "ROMs/arcade",
            sizeBytes = 2L * 1024 * 1024,
            emulator = EmulatorToStart("FinalBurn Neo", isInstalled = true),
            isArcade = true,
            arcadeSet = set,
        )

    /** Info over the board with [facts] at [sizes]; the problems found, or none. */
    private fun problems(
        name: String,
        facts: GameFacts,
        sizes: List<Pair<PlumFont, TextSize>>,
        ellipsisAllowedAt: (TextSize) -> Boolean,
    ): List<String> {
        var size by mutableStateOf(sizes.first().second)
        var font by mutableStateOf(sizes.first().first)
        val info = InfoReadThrough(OpenPanel(GameRef("arcade-1", facts.title), PanelScreen.Info(facts)))
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { info.screen() }
        }
        val failures = mutableListOf<String>()
        for ((eachFont, each) in sizes) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNode()
            val label = "$name at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            // The facts may be cut at the edges of their scroll only if every row can be read whole with Up and Down.
            val allow = FitAllowance(setOf(GamePanelTags.ROWS, GamePanelTags.FACTS_SCROLL), ellipsisUnder = GamePanelTags.FACTS, isLargest = ellipsisAllowedAt(each))
            textProblems(root, allow).mapTo(failures) { "$label: $it" }
            info.unreadRows(compose).mapTo(failures) { "$label: $it" }
        }
        return failures
    }

    private fun assertNone(failures: List<String>) = assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())

    private val everySize =
        TextSize.entries.map { PlumFont.Sans to it } +
            listOf(PlumFont.Serif, PlumFont.Mono).flatMap { listOf(it to TextSize.Large, it to TextSize.Largest) }

    /**
     * A set whose detail fits the value's lines ("Final Fight (World, set 1)"). A one-line title: a
     * title of two lines at Largest Serif squeezes Info on the 768 side panel for every system
     * (measured with "Cadillacs and Dinosaurs", with or without the set row).
     */
    @Test
    fun infoWithTheArcadeSetFitsAtEverySize() =
        assertNone(problems("game_info_arcade", arcadeFacts("Final Fight", ArcadeSetFacts("ffight", listOf("World, set 1"))), everySize) { it == TextSize.Largest })

    /** The set that tells "Cadillacs and Dinosaurs" apart is whole, never ellipsized, at the default size. */
    @Test
    fun dinouIsShownWholeAtMedium() {
        val failures = problems("game_info_dinou", arcadeFacts("Cadillacs and Dinosaurs", ArcadeSetFacts("dinou", listOf("USA 930201"))), listOf(PlumFont.Sans to TextSize.Medium)) { false }
        compose.onNodeWithText("dinou · USA 930201", useUnmergedTree = true).assertExists()
        assertNone(failures)
    }

    /**
     * Details longer than the value's lines may end in an ellipsis; nothing else may go wrong. A
     * made-up very long one, and "astorm · World, 2 Players · FD1094 317-0154" (Alien Storm (World,
     * 2 Players) (FD1094 317-0154)), which is too long for two lines of the wide layout's side panel
     * at Medium already (768 and 837; whole at 576 and 667).
     */
    @Test
    fun aDeliberatelyLongDetailMayEndInAnEllipsis() {
        val long = ArcadeSetFacts("zorkqst", listOf("World, 2 Players, rev B, location test build", "FD1094 317-0154", "bootleg of the Japanese version with an extra test menu"))
        assertNone(problems("game_info_arcade_long", arcadeFacts("Zork Quest", long), everySize) { true })
    }

    @Test
    fun twoBracketGroupsMayEndInAnEllipsisWhenTooLong() {
        val failures = problems("game_info_astorm", arcadeFacts("Alien Storm", ArcadeSetFacts("astorm", listOf("World, 2 Players", "FD1094 317-0154"))), everySize) { true }
        compose.onNodeWithText("astorm \u00b7 World, 2 Players \u00b7 FD1094 317-0154", useUnmergedTree = true).assertExists()
        assertNone(failures)
    }

    /**
     * "Name from" under the facts scrolls with them, exempt from the clip check like them: each of its
     * lines (the label, where the name came from, a source long enough to wrap) must be read whole with
     * Up and Down too, at every size ([InfoReadThrough]). Not an arcade game: the case lives here because
     * this file already checks Info at every size and `TextSizeScreensTest` is at the length limit.
     */
    @Test
    fun nameFromIsReadWholeAtEverySize() {
        val name = NameFacts(NameOrigin.GameCode, NameCertainty.Sure, "Nintendo - Super Nintendo Entertainment System", hasFix = false)
        val facts =
            sampleFacts(title = "Super Metroid").copy(
                systemName = "Super Nintendo",
                regions = "USA,EUR",
                fileName = "Super Metroid.sfc",
                folderName = "ROMs/snes",
                sizeBytes = 3L * 1024 * 1024,
                emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true),
                name = name,
            )
        assertNone(problems("game_info_name_from", facts, everySize) { it == TextSize.Largest })
    }

    /** A description without brackets ("Gauntlet II"): the short name alone. */
    @Test
    fun noBracketsShowsTheShortNameAlone() {
        val failures = problems("game_info_gaunt2", arcadeFacts("Gauntlet II", ArcadeSetFacts("gaunt2", emptyList())), listOf(PlumFont.Sans to TextSize.Medium)) { false }
        compose.onNodeWithText("gaunt2", useUnmergedTree = true).assertExists()
        assertNone(failures)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GameInfoArcadeFit768Test : GameInfoArcadeFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class GameInfoArcadeFit837Test : GameInfoArcadeFitBase(837, 471)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class GameInfoArcadeFit667Test : GameInfoArcadeFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GameInfoArcadeFit576Test : GameInfoArcadeFitBase(576, 432)
