package com.plumemu.textsize

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameMenuEntryId
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
import com.plumemu.graph.AchievementCount
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.appComposeRule
import com.plumemu.home.assertNoTextCut
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The eight-row game menu (Box art and Achievements "12 of 40 ›" both listed) at the Largest text size, with the cursor on
 * each of the last three rows: no text is cut or ellipsized, and the focused row is whole. The last row may be cut by the
 * card and faded while the list scrolls. English and French (the longest labels of the translations).
 */
abstract class GameMenuAchievementsFitBase(private val label: String) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val game = GameRef("snes-1", "Super Metroid")

    private fun problemsAt(
        focus: Int,
        font: PlumFont,
    ): List<String> {
        val panel = OpenPanel(game, PanelScreen.Menu(sampleFacts(title = game.title, canFixBoxArt = true, achievementCount = AchievementCount(12, 40))), focus = focus)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest, font = font)) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }
        }
        compose.waitForIdle()
        val root = compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNode()
        val where = "$label focus $focus ${font.name}"
        val found = textProblems(root, FitAllowance(scrollTags = setOf(GamePanelTags.ROWS))).map { "$where: $it" }.toMutableList()
        // The focused row is whole, and its texts are not cut.
        val row = compose.onNodeWithTag(GamePanelTags.row(panel.content.focusableRows[focus].key), useUnmergedTree = true).fetchSemanticsNode()
        notFullyShown(row, root)?.let { found += "$where: the focused row $it" }
        runCatching { assertNoTextCut(row, where) }.onFailure { found += it.message.orEmpty() }
        return found
    }

    private fun assertNone(failures: List<String>) = assertTrue(failures.size.toString() + " problems:\n" + failures.take(20).joinToString("\n"), failures.isEmpty())

    @Test
    fun achievementsRowFocused() = assertNone(problemsAt(5, PlumFont.Sans))

    @Test
    fun infoRowFocused() = assertNone(problemsAt(6, PlumFont.Sans))

    @Test
    fun hideRowFocusedLast() = assertNone(problemsAt(7, PlumFont.Sans))

    @Test
    fun boxArtRowFocused() = assertNone(problemsAt(4, PlumFont.Sans))

    @Test
    fun achievementsRowFocusedInSerif() = assertNone(problemsAt(5, PlumFont.Serif))

    @Test
    fun achievementsRowFocusedInMono() = assertNone(problemsAt(5, PlumFont.Mono))
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GameMenuAchievementsFit576Test : GameMenuAchievementsFitBase("en 576")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GameMenuAchievementsFit768Test : GameMenuAchievementsFitBase("en 768")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w576dp-h432dp-land-xhdpi")
class GameMenuAchievementsFit576FrenchTest : GameMenuAchievementsFitBase("fr 576")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w768dp-h432dp-land-xhdpi")
class GameMenuAchievementsFit768FrenchTest : GameMenuAchievementsFitBase("fr 768")
