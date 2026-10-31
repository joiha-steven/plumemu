package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FixNameState
import com.plumemu.game.FixNameTags
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
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
            PlumTheme(MediumText.Default) {
                HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(GameRef("snes-6", "Super Metroid"), PanelScreen.FixName(state)))
            }
        }
    }

    private fun assertLayout() {
        val panel = bounds(GamePanelTags.PANEL)
        val field = bounds(FixNameTags.FIELD)
        val first = bounds(GamePanelTags.row(resultKey(0)))
        val second = bounds(GamePanelTags.row(resultKey(1)))
        assertDp("field row 40 high", 40.dp, field.bottom - field.top)
        assertDp("result row 40 high", 40.dp, first.bottom - first.top)
        assertDp("field spans the panel", panel.right - panel.left, field.right - field.left)
        assertDp("results on the field's left edge", field.left, first.left)
        assertDp("results on the field's right edge", field.right, first.right)
        assertDp("results one list gap (2) under the field", field.bottom + 2.dp, first.top)
        assertTrue("the second result sits under the first one's note line", second.top > first.bottom + 10.dp)
        assertTrue("everything inside the panel", second.right <= panel.right && field.left >= panel.left)
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
