package com.plumemu.home

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.GAME_ROW_DRAWN_TAG
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Where the main screen's parts land, measured, against the board's own numbers
 * (RP5-1-systems / RP5-2-games markup: padding 14 0 12, bars 18 and 22, gap 10, strip padding
 * 0 24, columns 200 | 24 | list, system rows drawn 40 (34 designed, min 40) with 4 after Apps, game rows drawn 38 (36 designed, min 38) with gap 2,
 * games focused shift `peek − 224`, no-art list 672 at 768; the wide no-art list is
 * `width − 368` beside the text-only details column) and docs/design/rules.md.
 */
@RunWith(RobolectricTestRunner::class)
// Native graphics: real font metrics, so text widths (key caps) are measured as on a device.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun visible(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    private fun assertDp(
        what: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(what, expected.value, actual.value, TOLERANCE)

    private fun showSystemsFocused() = compose.setContent { PlumTheme(MediumText.Default) { HomeAt(FakeHomeContent.board(), BoardSystemsFocused) } }

    /** Super Nintendo focused on the system column with motion on, then A: the strip slides and settles. */
    private fun slideToGames() {
        lateinit var state: StripState
        compose.setContent {
            PlumTheme(MediumText.Default) {
                HomeAt(
                    FakeHomeContent.board(),
                    listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)),
                    isMotionOn = true,
                    onState = { state = it },
                )
            }
        }
        compose.runOnIdle { state.dispatch(Action.Enter) }
        compose.mainClock.advanceTimeBy(SLIDE_AND_MORE_MS)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("settled, not moving", false, state.isMoving) }
    }

    private fun assertBars(height: Dp) {
        val top = bounds(HomeTags.TOP_BAR)
        assertDp("top bar y = margin 14", 14.dp, top.top)
        assertDp("top bar height 18", 18.dp, top.bottom - top.top)
        val strip = bounds(HomeTags.STRIP)
        assertDp("content starts at 14 + 18 + 10", 42.dp, strip.top)
        assertDp("content ends 10 above the hint bar", height - 12.dp - 22.dp - 10.dp, strip.bottom)
        val hints = bounds(HomeTags.HINT_BAR)
        assertDp("hint bar height 22", 22.dp, hints.bottom - hints.top)
        assertDp("hint bar bottom at height − 12", height - 12.dp, hints.bottom)
    }

    private fun assertSystemsFocused(
        width: Dp,
        height: Dp,
    ) {
        showSystemsFocused()
        assertBars(height)
        val recent = bounds(HomeTags.system(0))
        assertDp("selector column starts at the screen edge, margin 24 inside", 0.dp, recent.left)
        assertDp("first row at 42", 42.dp, recent.top)
        assertDp("system column 224 = margin 24 + 200", 224.dp, recent.right - recent.left)
        assertDp("system rows 40 (34 designed, min 40)", 40.dp, recent.bottom - recent.top)
        assertDp("Super Nintendo after three rows and the 4 dp gap", 42.dp + 40.dp * 3 + 4.dp, bounds(HomeTags.system(3)).top)
        val first = bounds(HomeTags.game(0))
        assertDp("preview list after column and gap 24", 248.dp, first.left)
        assertDp("first game row at 42", 42.dp, first.top)
        assertDp("a game row's tap area is the row and its gap, 40", 40.dp, first.bottom - first.top)
        val drawn = compose.onAllNodesWithTag(GAME_ROW_DRAWN_TAG, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertDp("game rows drawn 38", 38.dp, drawn.bottom - drawn.top)
        assertDp("the drawn row starts with its tap area", first.top, drawn.top)
        val listWidth = if (width >= WIDE_MIN) width - 368.dp else width - 96.dp
        assertDp("no-art list: width − 368 beside the text-only details (wide), width − 96 (narrow)", listWidth, first.right - first.left)
        assertDp("rows 40 apart (38 and the 2 gap)", 82.dp, bounds(HomeTags.game(1)).top)
        assertDp("hint starts at the margin", 24.dp, bounds(HomeTags.hint(Action.Enter)).left)
    }

    private fun assertGamesFocused(width: Dp) {
        slideToGames()
        val snes = bounds(HomeTags.system(3))
        assertDp("system column ends at the 48 dp peek", 48.dp, snes.right)
        val snesVisible = visible(HomeTags.system(3))
        assertDp("48 dp of it on screen", 48.dp, snesVisible.right - snesVisible.left)
        val first = bounds(HomeTags.game(0))
        assertDp("list after peek and gap", 72.dp, first.left)
        if (width >= WIDE_MIN) {
            assertDp("list: width − 368", width - 368.dp, first.right - first.left)
            val details = bounds(HomeTags.DETAILS)
            assertDp("text-only details 24 after the list", first.right + 24.dp, details.left)
            assertDp("details end at the right margin", width - 24.dp, details.right)
        } else {
            assertDp("list ends at the right margin", width - 24.dp, first.right)
        }
        assertDp("hint starts at the margin", 24.dp, bounds(HomeTags.hint(Action.Play)).left)
    }

    /** Key caps as the board draws them: `padding 0 5px; border 1.5px; border-box` = text + 13. */
    private fun assertKeyCap(
        hint: Action,
        label: String,
        width: Dp,
    ) {
        val hintLeft = bounds(HomeTags.hint(hint)).left
        val labelLeft = compose.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot().left
        assertDp("$label: cap width (label − hint − gap 6)", width, labelLeft - hintLeft - 6.dp)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideKeyCapsMatchTheBoard() {
        showSystemsFocused()
        assertKeyCap(Action.OpenSettings, "Settings", 46.dp)
        assertKeyCap(Action.Enter, "Select", 20.dp)
        assertKeyCap(Action.Search, "Search", 20.dp)
        // The next hint starts 20 after the label of the one before (RP5-1: A Select, X Search, START Settings).
        val selectRight = compose.onNodeWithText("Select", useUnmergedTree = true).getUnclippedBoundsInRoot().right
        assertDp("hint gap 20", selectRight + 20.dp, bounds(HomeTags.hint(Action.Search)).left)
        val searchRight = compose.onNodeWithText("Search", useUnmergedTree = true).getUnclippedBoundsInRoot().right
        assertDp("hint gap 20", searchRight + 20.dp, bounds(HomeTags.hint(Action.OpenSettings)).left)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideTwoKeyCapMatchesTheBoard() {
        compose.setContent { PlumTheme(MediumText.Default) { HomeAt(FakeHomeContent.board(), BoardGamesFocused) } }
        assertKeyCap(Action.JumpLetterNext, "Switch system", 40.5.dp)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideSystemsFocused() = assertSystemsFocused(768.dp, 432.dp)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSystemsFocused() = assertSystemsFocused(576.dp, 432.dp)

    @Test
    @Config(qualifiers = WIDE)
    fun wideGamesFocusedAfterTheSlide() = assertGamesFocused(768.dp)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowGamesFocusedAfterTheSlide() = assertGamesFocused(576.dp)

    private companion object {
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val TOLERANCE = 0.5f
        const val SLIDE_AND_MORE_MS = 400L
    }
}

/** Wide screens start here (docs/design/rules.md › Width classes). */
private val WIDE_MIN = 768.dp
