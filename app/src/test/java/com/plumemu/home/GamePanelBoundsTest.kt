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
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.SlabDimens
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
 * (528 at 576) from the margin. Rows 40 high, 40 apart (the card's rows have no gap, the board's pitch), text 14 in.
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
            PlumTheme(TableText.Default) {
                HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(GameRef("snes-6", "Super Metroid"), PanelScreen.Menu(facts)))
            }
        }

    private fun assertRows() {
        val play = bounds(GamePanelTags.row(GameMenuEntryId.Play.name))
        val playWith = bounds(GamePanelTags.row(GameMenuEntryId.PlayWith.name))
        val hide = bounds(GamePanelTags.row(GameMenuEntryId.Hide.name))
        assertDp("row height", 40.dp, play.bottom - play.top)
        assertDp("rows 40 apart", 40.dp, playWith.top - play.top)
        assertDp("six rows", 40.dp * 5, hide.top - play.top)
        assertTrue("Hide game, the last row, fits inside the panel, above the hints", hide.bottom <= bounds(GamePanelTags.PANEL).bottom)
        val panel = bounds(GamePanelTags.PANEL)
        val title = bounds(GamePanelTags.TITLE)
        assertDp("rows span the panel: the slab is the card's inset from each edge", panel.right - panel.left, play.right - play.left)
        assertDp("rows start where the panel does", panel.left, play.left)
        assertDp("title text starts panelTitleStart in from the rows' start", panel.left + PlumDimens.panelTitleStart, title.left)
        // Strip top: 14 margin + 18 top bar + 10 gap (the card's top edge sits there); the title is the first line, the board's top padding below it.
        assertDp("panel column starts the card's top padding below the strip", TableText.StripTop + CardDimens.padTop, panel.top)
        assertDp("panel ends with the strip: 432 − 9 − 22 − 9", 392.dp, panel.bottom)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun wideIsTheSidePanelAtTheRight() {
        showMenu()
        val panel = bounds(GamePanelTags.PANEL)
        assertEquals("panel width: the card less its inset on both sides (pixel rounding)", (CardDimens.menuWidth - PlumDimens.panelInset * 2).value, (panel.right - panel.left).value, 1f)
        assertDp("the card is 24 from the right edge", 768.dp - 24.dp - PlumDimens.panelInset, panel.right)
        assertRows()
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrowIsAStopOfTheOptionsWidth() {
        showMenu()
        val panel = bounds(GamePanelTags.PANEL)
        assertDp("options width: the page less both margins, less the card's inset", 528.dp - PlumDimens.panelInset * 2, panel.right - panel.left)
        assertDp("from the margin", 24.dp + PlumDimens.panelInset, panel.left)
        assertRows()
    }

    /** A menu opened from Recent holds a seventh row (Remove from Recent, before Hide game); Hide game, the last, still ends inside the panel. */
    private fun assertRecentMenuFits() {
        showMenu(sampleFacts(canRemoveFromRecent = true))
        val remove = bounds(GamePanelTags.row(GameMenuEntryId.RemoveFromRecent.name))
        val hide = bounds(GamePanelTags.row(GameMenuEntryId.Hide.name))
        assertDp("Remove from Recent is the row before Hide, 40 apart", 40.dp, hide.top - remove.top)
        assertDp("row height", 40.dp, remove.bottom - remove.top)
        assertTrue("Hide game, the last, fits inside the panel", hide.bottom <= bounds(GamePanelTags.PANEL).bottom)
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
            PlumTheme(TableText.Default) {
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
    fun theCurrentBadgeIsInsideItsRowOnTheNamesLine() {
        showPlayWith()
        val row = bounds(GamePanelTags.row(Beetle.id))
        val badge = compose.onNodeWithText("Current", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val name = compose.onNodeWithText(Beetle.name, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("row $row holds the badge $badge", row.left <= badge.left && row.top <= badge.top && row.right >= badge.right && row.bottom >= badge.bottom)
        assertTrue("badge $badge is beside the name $name, on its line", badge.left >= name.right && badge.top < name.bottom && badge.bottom > name.top)
    }

    private companion object {
        const val TOLERANCE = 0.5f
    }
}
