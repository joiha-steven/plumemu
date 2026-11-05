package com.plumemu.home

import com.plumemu.coreui.theme.PlumDimens
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.components.NOTE_TAG
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.FieldDimens
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FixNameState
import com.plumemu.game.FixNameTags
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.OwnNameState
import com.plumemu.game.SAVE_KEY
import com.plumemu.game.PanelScreen
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleFinder
import com.plumemu.game.resultKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fix name… measured: the field is a panel row (40 high, the panel's full width, under
 * the title like the menu's first row), the results start one list gap under it, each 40 high
 * with its regions and year under it, all on the same left and right edges. Wide and narrow.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class NameFixBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val FIELD_ROW = FieldDimens.wellHeight + CollarSize.Field.topPadding + CollarSize.Field.bottomPadding

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertDp(
        what: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(what, expected.value, actual.value, 0.5f)

    private fun showFixName() {
        val finder =
            TitleFinder(
                listOf(
                    TitleChoice("Super Metroid", "Japan, USA, Europe, Korea, Brazil, Australia, Hong Kong", 1994, null),
                    TitleChoice("Super Metroid (Beta) with a title far too long for the panel width", "Japan", 1994, null),
                ),
            )
        val state = FixNameState("super metroid", finder)
        state.showResults(state.query, finder.find(state.query))
        state.onAction(Action.Move(Direction.Down))
        compose.setContent {
            PlumTheme(TableText.Default) {
                HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(GameRef("snes-6", "Super Metroid"), PanelScreen.FixName(state)))
            }
        }
    }

    private fun assertLayout() {
        val panel = bounds(GamePanelTags.PANEL)
        val field = bounds(FixNameTags.FIELD)
        val first = bounds(GamePanelTags.row(resultKey(0)))
        val second = bounds(GamePanelTags.row(resultKey(1)))
        assertDp("field row is the well and its collar high (board 74 px)", FIELD_ROW, field.bottom - field.top)
        // The field's outer edge sits on the line the titles and rows' texts keep (the rows' pills run wider).
        assertDp("field starts on the rows' text inset", first.left + PlumDimens.panelTitleStart, field.left)
        assertDp("field ends on the rows' text inset", first.right - PlumDimens.panelTitleStart, field.right)
        assertDp("results the board gap under the field", field.bottom + FieldDimens.gap, first.top)
        // The focused slab covers the title and its year; the year is tight under the title, the room goes between pairs.
        val year = compose.onAllNodes(hasTestTag(NOTE_TAG) and hasAnyAncestor(hasTestTag(GamePanelTags.row(resultKey(0)))), useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val title = compose.onAllNodes(hasText("Super Metroid") and hasAnyAncestor(hasTestTag(GamePanelTags.row(resultKey(0)))), useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertTrue("the year $year lies inside the slab $first", year.top >= first.top && year.bottom <= first.bottom && year.left >= first.left && year.right <= first.right)
        assertTrue("the year is ${year.top - title.bottom} under its title (3 dp at most)", year.top - title.bottom <= 3.dp)
        val toNext = second.top - year.bottom
        assertTrue("the room between pairs ($toNext) is more than the year's gap to its title", toNext > year.top - title.bottom)
        assertTrue("the second pair starts the room between pairs under the first slab", second.top >= first.bottom + PlumDimens.noteBelowGap - 0.5.dp && year.bottom + PlumDimens.noteInset <= first.bottom + 0.5.dp)
        assertTrue("everything inside the panel", second.right <= panel.right && field.left >= panel.left - 0.5.dp)
    }

    private fun assertOwnNameInset() {
        compose.setContent {
            PlumTheme(TableText.Default) {
                HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(GameRef("snes-6", "Super Metroid"), PanelScreen.OwnName(OwnNameState("Super Metroid"))))
            }
        }
        val panel = bounds(GamePanelTags.PANEL)
        val field = bounds(FixNameTags.OWN_FIELD)
        val save = bounds(GamePanelTags.row(SAVE_KEY))
        assertDp("own-name field starts on the rows' text inset", save.left + PlumDimens.panelTitleStart, field.left)
        assertDp("own-name field ends on the rows' text inset", save.right - PlumDimens.panelTitleStart, field.right)
        assertTrue("inside the panel", field.left >= panel.left && field.right <= panel.right)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun ownNameFieldOnTheRowsInsetWide() = assertOwnNameInset()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun ownNameFieldOnTheRowsInsetNarrow() = assertOwnNameInset()

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun fixNameOnTheRowsInsetAt640() {
        showFixName()
        assertLayout()
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun wide() {
        showFixName()
        assertLayout()
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrow() {
        showFixName()
        assertLayout()
    }
}
