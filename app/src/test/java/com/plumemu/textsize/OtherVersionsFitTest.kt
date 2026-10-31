package com.plumemu.textsize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.plumemu.R
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.UiText
import com.plumemu.game.VersionItem
import com.plumemu.game.VersionListing
import com.plumemu.game.sampleArcadeVersions
import com.plumemu.game.sampleFacts
import com.plumemu.game.versionGoneMessage
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
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
 * The game menu with Other versions… (its value the current version) and the Other versions panel
 * at every text size, as `GameInfoArcadeFitTest` checks Info: at the six sizes in Plum Sans and at
 * Large and Largest in Serif and Mono, no text is ellipsized below Largest, clipped, too short or
 * on top of another. A label that carries its file name (two copies with one region) may end in an
 * ellipsis: file names run as long as their makers like.
 */
abstract class OtherVersionsFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val game = GameRef("arcade-1", "Cadillacs and Dinosaurs")

    private fun problems(
        name: String,
        panel: OpenPanel,
        sizes: List<Pair<PlumFont, TextSize>>,
        ellipsisAllowedAt: (TextSize) -> Boolean,
    ): List<String> {
        var size by mutableStateOf(sizes.first().second)
        var font by mutableStateOf(sizes.first().first)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }
        }
        val failures = mutableListOf<String>()
        for ((eachFont, each) in sizes) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNode()
            val label = "$name at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            val allow = FitAllowance(setOf(GamePanelTags.ROWS), ellipsisUnder = GamePanelTags.ROWS, isLargest = ellipsisAllowedAt(each))
            textProblems(root, allow).mapTo(failures) { "$label: $it" }
        }
        return failures
    }

    private fun assertNone(failures: List<String>) = assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())

    private val everySize =
        TextSize.entries.map { PlumFont.Sans to it } +
            listOf(PlumFont.Serif, PlumFont.Mono).flatMap { listOf(it to TextSize.Large, it to TextSize.Largest) }

    private val menu get() = OpenPanel(game, PanelScreen.Menu(sampleFacts(title = game.title, isArcade = true, versions = sampleArcadeVersions())), focus = 2)

    @Test
    fun theMenuWithOtherVersionsAndItsValueFitsAtEverySize() =
        assertNone(problems("game_menu_versions", menu, everySize) { it == TextSize.Largest })

    @Test
    fun theVersionsAndTheCurrentBadgeFitAtEverySize() {
        val failures = problems("game_versions", OpenPanel(game, PanelScreen.Versions(sampleArcadeVersions(currentIndex = 1)), focus = 1, parent = menu), everySize) { it == TextSize.Largest }
        assertNone(failures)
    }

    @Test
    fun theVersionGoneMessageFitsAtEverySize() =
        assertNone(problems("game_version_gone", OpenPanel(game, PanelScreen.Message(versionGoneMessage()), parent = menu), everySize) { it == TextSize.Largest })

    @Test
    fun theCurrentVersionIsWholeAtMedium() {
        val failures = problems("game_menu_versions_medium", menu, listOf(PlumFont.Sans to TextSize.Medium)) { false }
        compose.onNodeWithText("World 930201", useUnmergedTree = true).assertExists()
        assertNone(failures)
    }

    @Test
    fun aLabelWithItsFileNameMayEndInAnEllipsis() {
        val twins =
            VersionListing(
                listOf(
                    VersionItem("11", UiText.Format(R.string.game_version_with_file, listOf(UiText.Plain("USA"), UiText.Plain("Zork Quest - The Moonlit Harbor (USA) (En,Fr,De,Es,It).sfc"))), isCurrent = true),
                    VersionItem("15", UiText.Format(R.string.game_version_with_file, listOf(UiText.Plain("USA"), UiText.Plain("Zork Quest - The Moonlit Harbor (USA) (En,Fr,De,Es,It).zip"))), isCurrent = false),
                ),
            )
        assertNone(problems("game_versions_long", OpenPanel(game, PanelScreen.Versions(twins), parent = menu), everySize) { true })
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class OtherVersionsFit768Test : OtherVersionsFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class OtherVersionsFit837Test : OtherVersionsFitBase(837, 471)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class OtherVersionsFit667Test : OtherVersionsFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class OtherVersionsFit576Test : OtherVersionsFitBase(576, 432)
