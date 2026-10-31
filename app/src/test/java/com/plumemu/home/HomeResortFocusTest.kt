package com.plumemu.home

import androidx.compose.runtime.mutableStateOf
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The naming pass re-sorts a system's games while the person looks at them: the
 * focused row must stay on the same game, so A never plays another one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeResortFocusTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun aReSortUnderTheFocusKeepsTheSameGameFocused() {
        val board = FakeHomeContent.board()
        val snes = FakeHomeContent.BOARD_SNES_INDEX
        val content = mutableStateOf(board)
        lateinit var state: StripState
        val actions = listOf(Action.TapItem(Column.Systems, snes), Action.Enter, Action.TapItem(Column.Games, 2))
        compose.setContent { PlumTheme { HomeAt(content.value, actions, onState = { state = it }) } }
        val focused = compose.runOnIdle { board.entries[snes].rows[state.focus.row].id }

        // Names arrive: the same games, another order (the focused one moves).
        content.value = resorted(board, snes) { it.reversed() }
        compose.waitForIdle()
        compose.runOnIdle {
            val now = content.value.entries[state.focus.systemIndex]
            assertEquals("snes", now.id)
            assertEquals(focused, now.rows[state.focus.row].id)
        }

        // The focused game leaves the list: the cursor stays at the nearest row.
        val row = compose.runOnIdle { state.focus.row }
        content.value = resorted(content.value, snes) { rows -> rows.filter { it.id != focused } }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(row.coerceAtMost(content.value.entries[snes].count - 1), state.focus.row) }
    }

    private fun resorted(
        from: HomeContent,
        index: Int,
        change: (List<GameRowModel>) -> List<GameRowModel>,
    ): HomeContent =
        HomeContent.of(
            recent = from.entries[0].rows,
            favorites = from.entries[1].rows,
            apps = from.entries[2].rows,
            systems = from.entries.drop(FIRST_SYSTEM_INDEX).mapIndexed { i, entry ->
                val rows = if (i + FIRST_SYSTEM_INDEX == index) change(entry.rows) else entry.rows
                SystemGames(entry.id, entry.name.orEmpty(), rows, entry.coverShape)
            },
            hasBoxArt = from.hasBoxArt,
            showApps = true,
        )
}
