package com.plumemu.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A game row's title colour follows the focus while the screen runs. Preview (systems focused) and
 * Normal (games focused) differ only in the title's colour, which the row reads while drawing
 * (GameRow's `drawnLook`), not while composing; the screenshot tests set their focus before the
 * first frame, so only a change after it shows that the drawn colour follows. Read from the
 * title's pixels: the ink farthest from the row's background is the title's colour.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeTitleColourTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val content = FakeHomeContent.large()
    private val snes = FakeHomeContent.BOARD_SNES_INDEX
    private lateinit var state: StripState
    private lateinit var colors: PlumComposeColors

    private fun show() {
        compose.setContent {
            PlumTheme {
                colors = PlumTheme.colors
                HomeAt(content, listOf(Action.TapItem(Column.Systems, snes)), onState = { state = it })
            }
        }
        compose.waitForIdle()
    }

    private fun act(action: Action) {
        compose.runOnIdle { state.dispatch(action) }
        compose.waitForIdle()
    }

    /** The colour of row [row]'s title on the shown page of [entry]: the ink farthest from the background. */
    private fun titleColour(
        entry: Int,
        row: Int,
    ): Color {
        val title = content.entries[entry].rows[row].title
        val pixels = compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag(HomeTags.game(row))), useUnmergedTree = true).captureToImage().toPixelMap()
        val all = buildList { for (x in 0 until pixels.width) for (y in 0 until pixels.height) add(pixels[x, y]) }
        val background = all.groupingBy { it }.eachCount().maxBy { it.value }.key
        return all.maxBy { distance(it, background) }
    }

    /** Which title colour [colour] is: the nearest of muted (preview), text2 (games) and strong (the focused row). */
    private fun named(colour: Color): String = mapOf("muted" to colors.muted, "text2" to colors.text2, "strong" to colors.strong).minBy { (_, c) -> distance(colour, c) }.key

    private fun distance(
        a: Color,
        b: Color,
    ): Float = (a.red - b.red) * (a.red - b.red) + (a.green - b.green) * (a.green - b.green) + (a.blue - b.blue) * (a.blue - b.blue)

    @Test
    fun enterAndBackRecolourTheTitles() {
        show()
        assertEquals("systems focused: preview", "muted", named(titleColour(snes, ROW)))
        act(Action.Enter)
        assertEquals(Column.Games, state.focus.column)
        assertEquals("after Enter: the games' own colour", "text2", named(titleColour(snes, ROW)))
        act(Action.Back)
        assertEquals(Column.Systems, state.focus.column)
        assertEquals("after Back: preview again", "muted", named(titleColour(snes, ROW)))
    }

    /** A page kept beside the shown one follows Enter while hidden, and shows the right colour when L1 shows it. */
    @Test
    fun aKeptPageShownByL1HasTheGamesColour() {
        show()
        act(Action.Move(Direction.Down))
        assertEquals("SNES's page drawn in preview, now kept", snes + 1, state.focus.systemIndex)
        act(Action.Enter)
        act(Action.PreviousSystem)
        assertEquals(snes, state.focus.systemIndex)
        assertEquals(Column.Games, state.focus.column)
        assertEquals("the kept page, shown by L1", "text2", named(titleColour(snes, ROW)))
        assertEquals("its remembered row is the focused one", "strong", named(titleColour(snes, 0)))
    }

    private companion object {
        /** A row that is not the remembered one (row 0): only its title's colour changes. */
        const val ROW = 2
    }
}
