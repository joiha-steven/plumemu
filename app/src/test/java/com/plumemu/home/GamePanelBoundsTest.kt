package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import com.plumemu.game.Beetle
import com.plumemu.game.DuckStation
import com.plumemu.game.Epsxe
import com.plumemu.game.GameFacts
import com.plumemu.game.sampleListing
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.GameMenuEntryId
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game panel's measurements against design/rules.md › Settings : wide,
 * the 248 side panel inside the 24 margin (x 496 at 768); narrow, the options width `width − 48`
 * (528 at 576) from the margin. Rows 40 high, 42 apart (the list's 2 gap), text 14 in.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GamePanelBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertDp(
        what: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(what, expected.value, actual.value, TOLERANCE)

    private fun showMenu(facts: GameFacts = sampleFacts()) =
        compose.setContent {
            PlumTheme(MediumText.Default) {
                HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(GameRef("snes-6", "Super Metroid"), PanelScreen.Menu(facts)))
            }
        }

    private fun assertRows() {
        val play = bounds(GamePanelTags.row(GameMenuEntryId.Play.name))
        val playWith = bounds(GamePanelTags.row(GameMenuEntryId.PlayWith.name))
        val info = bounds(GamePanelTags.row(GameMenuEntryId.Info.name))
        assertDp("row height", 40.dp, play.bottom - play.top)
        assertDp("rows 42 apart", 42.dp, playWith.top - play.top)
        assertDp("six rows", 42.dp * 5, info.top - play.top)
        assertTrue("Info fits inside the panel, above the hints", info.bottom <= bounds(GamePanelTags.PANEL).bottom)
        val panel = bounds(GamePanelTags.PANEL)
        val title = bounds(GamePanelTags.TITLE)
        assertDp("rows span the panel", panel.right - panel.left, play.right - play.left)
        assertDp("title text 14 in, like the rows' text", panel.left + 14.dp, title.left)
        // Strip top: 14 margin + 18 top bar + 10 gap; the title is the panel's first line.
        assertDp("panel starts with the strip", 42.dp, panel.top)
        assertDp("panel ends with the strip: 432 − 12 − 22 − 10", 388.dp, panel.bottom)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun wideIsTheSidePanelAtTheRight() {
        showMenu()
        val panel = bounds(GamePanelTags.PANEL)
        assertDp("panel width", 248.dp, panel.right - panel.left)
        assertDp("24 from the right edge", 768.dp - 24.dp, panel.right)
        assertDp("list visible up to the 24 gap: 72 + (768 − 368)", 72.dp + 400.dp + 24.dp, panel.left)
        assertRows()
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrowIsAStopOfTheOptionsWidth() {
        showMenu()
        val panel = bounds(GamePanelTags.PANEL)
        assertDp("options width", 528.dp, panel.right - panel.left)
        assertDp("from the margin", 24.dp, panel.left)
        assertRows()
    }

    /** A menu opened from Recent holds a seventh row (Remove from Recent); Info, the last, still ends inside the panel. */
    private fun assertRecentMenuFits() {
        showMenu(sampleFacts(canRemoveFromRecent = true))
        val remove = bounds(GamePanelTags.row(GameMenuEntryId.RemoveFromRecent.name))
        val hide = bounds(GamePanelTags.row(GameMenuEntryId.Hide.name))
        val info = bounds(GamePanelTags.row(GameMenuEntryId.Info.name))
        assertDp("Remove from Recent is the row after Hide, 42 apart", 42.dp, remove.top - hide.top)
        assertDp("row height", 40.dp, remove.bottom - remove.top)
        assertTrue("Info fits inside the panel", info.bottom <= bounds(GamePanelTags.PANEL).bottom)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theRecentMenuFitsAt768() = assertRecentMenuFits()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theRecentMenuFitsAt576() = assertRecentMenuFits()

    /** Every text in the Play with rows is drawn whole: its natural width fits the node it was given. */
    private fun assertPlayWithTextNotCut(what: String) {
        val rows = compose.onNodeWithTag(GamePanelTags.ROWS, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("$what: the rows have text", assertNoTextCut(rows, what) > 0)
    }

    private fun showPlayWith() =
        compose.setContent {
            PlumTheme(MediumText.Default) {
                val listing = sampleListing(current = Beetle.id, installed = listOf(DuckStation, Beetle), notInstalled = listOf(Epsxe))
                HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(GameRef("snes-6", "Super Metroid"), PanelScreen.PlayWith(listing)))
            }
        }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun playWithNamesAndTheCurrentBadgeAreNotCutAt768() {
        showPlayWith()
        assertPlayWithTextNotCut("768")
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun playWithNamesAndTheCurrentBadgeAreNotCutAt576() {
        showPlayWith()
        assertPlayWithTextNotCut("576")
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theCurrentBadgeIsInsideItsRowAndBelowTheName() {
        showPlayWith()
        val row = bounds(GamePanelTags.row(Beetle.id))
        val badge = compose.onNodeWithText("Current", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val name = compose.onNodeWithText(Beetle.name, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("row $row holds the badge $badge", row.left <= badge.left && row.top <= badge.top && row.right >= badge.right && row.bottom >= badge.bottom)
        assertTrue("badge $badge is under the name $name", badge.top >= name.bottom)
    }

    private companion object {
        const val TOLERANCE = 0.5f
    }
}
