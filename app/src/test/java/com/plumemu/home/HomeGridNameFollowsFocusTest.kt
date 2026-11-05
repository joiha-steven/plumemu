package com.plumemu.home

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideGridSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The hint bar's name line always names the game whose cover holds the focus in the packed grid. Rows hold different box
 * counts (a few wide pictures), where taking a row number for a game number would show; the expected game comes from
 * [expectedPacking] (worked out by hand), not from the focus the screen reports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeGridNameFollowsFocusTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val rows get() = FakeHomeContent.withArtwork().entries[boardIndexOf("snes")].rows
    private var layout: PlumLayout? = null

    /** Games 4 to 6 are wide, so the rows holding them have fewer boxes than the first (a wide one may stand alone, where Left stays). */
    private fun aspectOf(index: Int) = if (index in 4..6) WIDE else LANDSCAPE

    private fun show(size: GridSize): StripState {
        lateinit var state: StripState
        val source = realCoverSource(aspectOf = { _, index -> aspectOf(index) })
        compose.setContent {
            PlumTheme {
                ProvideGridSize(size) {
                    layout = PlumTheme.layout
                    HomeWithArt(source, gridActions(boardIndexOf("snes"), 0), gridIds = setOf("snes"), onState = { state = it })
                }
            }
        }
        compose.waitForIdle()
        return state
    }

    /** The name line is one text with year and publisher after the title, so the title is matched as a part. */
    private fun nameLineHas(title: String) =
        compose.onAllNodes(hasText(title, substring = true) and hasAnyAncestor(hasTestTag(HomeTags.HINT_BAR)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun walk(size: GridSize) {
        val state = show(size)
        val packing = expectedPacking(rows.indices.map { aspectOf(it) }, checkNotNull(layout), FakeHomeContent.withArtwork().entries[boardIndexOf("snes")].coverShape)
        assertNotEquals("rows must differ in box count: ${packing.rows}", packing.rows[0].size, packing.rows[1].size)
        var game = 0
        assertTrue("the first name is shown", nameLineHas(rows[game].title))
        val moves = listOf(Direction.Right, Direction.Right, Direction.Down, Direction.Up, Direction.Left, Direction.Down)
        for (move in moves) {
            val before = game
            game = when (move) {
                Direction.Right -> game + 1
                Direction.Left -> game - 1
                Direction.Down -> packing.down(game)
                else -> packing.up(game)
            }
            compose.runOnIdle { state.dispatch(Action.Move(move)) }
            compose.waitForIdle()
            val step = "$size $move from game $before"
            assertEquals("$step: focus lands on the expected game", game, compose.runOnIdle { state.focus.row })
            assertTrue("$step: its cover is drawn", compose.onAllNodesWithTag(HomeTags.cover(game), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            assertTrue("$step: the name line says '${rows[game].title}'", nameLineHas(rows[game].title))
            if (!rows[game].title.contains(rows[before].title)) assertTrue("$step: the name line dropped '${rows[before].title}'", !nameLineHas(rows[before].title))
        }
    }

    @Test
    fun nameFollowsFocusAtMedium() = walk(GridSize.Medium)

    @Test
    fun nameFollowsFocusAtSmall() = walk(GridSize.Small)

    private companion object {
        const val LANDSCAPE = 1.4f
        const val WIDE = 3.0f
    }
}
