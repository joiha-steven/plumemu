package com.plumemu.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The letter rail in the system peek (shown with Settings › Appearance › Left edge set to Letters): letters centred in the 48 dp peek, equal
 * slots over the column's height, the focused game's letter in the accent, a window when they do not fit,
 * nothing for Recent, and gone while the systems are focused.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeLetterRailBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(
        content: HomeContent,
        actions: List<Action>,
        size: TextSize = TextSize.Medium,
    ) = compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { HomeAt(content.withLeftEdge(LeftEdgeSetting.Letters), actions) } }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun railMin(size: TextSize): Dp = PlumDimens.railLetterMinHeight * size.heightScale

    /** The letters in the rail's tree, top to bottom. */
    private fun lettersShown(): List<String> =
        (listOf('#') + ('A'..'Z')).filter { compose.onAllNodesWithTag(HomeTags.railLetter(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.map { it.toString() }

    private fun gamesFocused(index: Int, row: Int = 0) = listOf(Action.TapItem(Column.Systems, index), Action.Enter, Action.TapItem(Column.Games, row))

    private fun systemWith(vararg titles: String) = SystemGames("snes", "Super Nintendo", titles.mapIndexed { i, t -> GameRowModel("g$i", t, "USA") })

    private fun alphabet(last: Char = 'Z'): HomeContent {
        val titles = listOf("1942") + ('A'..last).map { "${it}ar game" }
        return HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(systemWith(*titles.toTypedArray())))
    }

    @Test
    fun lettersAreCentredInThePeekAndEvenlySpread() {
        show(FakeHomeContent.board(), gamesFocused(FakeHomeContent.BOARD_SNES_INDEX, FakeHomeContent.BOARD_REMEMBERED_ROW))
        compose.waitForIdle()
        val rail = bounds(HomeTags.RAIL)
        assertEquals("the rail is the peek: from the screen edge, 48 dp wide", 0f, rail.left.value, 0.5f)
        assertEquals(PlumDimens.peekWidth.value, rail.width.value, 0.5f)
        val letters = listOf('C', 'D', 'E', 'F', 'M', 'S', 'Y')
        val boxes = letters.map { bounds(HomeTags.railLetter(it)) }
        for ((letter, box) in letters.zip(boxes)) {
            assertEquals("$letter centred in the peek", rail.left.value + rail.width.value / 2, box.left.value + box.width.value / 2, 0.5f)
        }
        val pitch = boxes.zipWithNext { a, b -> b.top.value - a.top.value }
        // Slots are weighted over whole pixels, so a pitch may differ by one pixel (0.5 dp at xhdpi).
        assertTrue("equal slots: $pitch", pitch.all { kotlin.math.abs(it - pitch.first()) <= 0.5f + 0.01f })
        assertEquals("the slots fill the column", rail.height.value, pitch.average().toFloat() * letters.size, 1.5f)
    }

    @Test
    fun theFocusedGamesLetterFollowsTheFocus() {
        show(FakeHomeContent.board(), gamesFocused(FakeHomeContent.BOARD_SNES_INDEX, FakeHomeContent.BOARD_REMEMBERED_ROW))
        compose.waitForIdle()
        // Super Metroid is "S": its slot exists and the rail lists the SNES letters only.
        compose.onNodeWithTag(HomeTags.railLetter('S'), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.railLetter('Q'), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun recentHasAnEmptyPeek() {
        show(FakeHomeContent.mixedSystems(), gamesFocused(0))
        compose.waitForIdle()
        compose.onNodeWithTag(HomeTags.RAIL, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun favoritesAndAppsShowTheirLetters() {
        show(FakeHomeContent.mixedSystems(), gamesFocused(1))
        compose.waitForIdle()
        compose.onNodeWithTag(HomeTags.railLetter('C'), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theRailGoesWhileTheSystemsAreFocused() {
        show(FakeHomeContent.board(), listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)))
        compose.waitForIdle()
        compose.onNodeWithTag(HomeTags.RAIL, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theRailKeepsItsLettersWhileItFadesWithTheColumn() {
        // Back from the games to the systems: the rail is not dropped at once, it fades out with the strip's slide.
        lateinit var strip: StripState
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                HomeAt(FakeHomeContent.board().withLeftEdge(LeftEdgeSetting.Letters), gamesFocused(FakeHomeContent.BOARD_SNES_INDEX, FakeHomeContent.BOARD_REMEMBERED_ROW), onState = { strip = it })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(HomeTags.railLetter('S'), useUnmergedTree = true).assertExists()
        compose.runOnIdle { strip.dispatch(Action.Back) }
        compose.waitForIdle()
        compose.onNodeWithTag(HomeTags.railLetter('S'), useUnmergedTree = true).assertExists()
    }

    @Test
    fun aListWithOneLetterShowsOneCentredLetter() {
        show(HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(systemWith("Sonic", "Space Harrier", "Streets of Rage"))), gamesFocused(FIRST_SYSTEM_INDEX))
        compose.waitForIdle()
        compose.onAllNodesWithTag(HomeTags.RAIL, useUnmergedTree = true).assertCountEquals(1)
        val rail = bounds(HomeTags.RAIL)
        val letter = bounds(HomeTags.railLetter('S'))
        assertEquals(rail.top.value + rail.height.value / 2, letter.top.value + letter.height.value / 2, 0.5f)
    }

    @Test
    fun allLettersShowWhenTheyFit() {
        show(alphabet(last = 'S'), gamesFocused(FIRST_SYSTEM_INDEX))
        compose.waitForIdle()
        val capacity = railCapacity(bounds(HomeTags.RAIL).height, railMin(TextSize.Medium))
        assertTrue("20 letters fit a 432 dp screen at Medium ($capacity)", capacity >= 20)
        assertEquals(20, lettersShown().size)
    }

    @Test
    fun aWindowAroundTheFocusedLetterShowsWhenTheyDoNotFitAtLargest() {
        show(alphabet(), gamesFocused(FIRST_SYSTEM_INDEX, row = 14), TextSize.Largest)
        compose.waitForIdle()
        val rail = bounds(HomeTags.RAIL)
        val shown = lettersShown()
        assertTrue("fewer than 27 letters at Largest: $shown", shown.size in 1 until 27)
        assertTrue("the focused letter (N, row 14) is in the window: $shown", "N" in shown)
        val min: Dp = railMin(TextSize.Largest)
        val slots = shown.map { bounds(HomeTags.railLetter(it[0])) }
        assertTrue("every slot has the least room ($min): ${slots.map { it.height.value }}", slots.all { it.height.value >= min.value - 0.5f })
        assertTrue("the window stays inside the rail", slots.all { it.top.value >= rail.top.value - 0.5f && it.top.value + it.height.value <= rail.top.value + rail.height.value + 0.5f })
    }
}
