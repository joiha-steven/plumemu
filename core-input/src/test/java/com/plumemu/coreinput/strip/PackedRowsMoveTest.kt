package com.plumemu.coreinput.strip

import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Test

/** Up and Down in a packed grid land on the nearest centre of the adjacent row; plain JVM. */
class PackedRowsMoveTest {
    // Rows: [0 1 2] [3 4] [5 6]; centres chosen so every case below is told apart.
    private val rows =
        PackedRows(
            rowStarts = listOf(0, 3, 5),
            centres = listOf(50f, 150f, 250f, 80f, 220f, 60f, 140f),
        )
    private val packed = SystemEntry("favorites", itemCount = 7, gridColumns = 3, packed = rows)
    private val content = StripContent(listOf(SystemEntry("recent", 0), packed), hasBoxArt = true)
    private val oneRow = StripContent(listOf(SystemEntry("recent", 0), packed.copy(packed = PackedRows(listOf(0), List(7) { it * 10f }))), hasBoxArt = true)

    private fun on(row: Int) = StripFocus(Column.Games, systemIndex = 1, systemId = "favorites").withRow(packed, row)

    private fun move(
        row: Int,
        direction: Direction,
        narrow: Boolean = false,
        on: StripContent = content,
    ) = StripReducer.reduce(on(row), Action.Move(direction), on, narrow).focus

    @Test
    fun downLandsOnTheNearestCentreAndTiesGoLeft() {
        assertEquals(3, move(0, Direction.Down).row)
        assertEquals("150 is 70 from 80 and 70 from 220: the left one", 3, move(1, Direction.Down).row)
        assertEquals(4, move(2, Direction.Down).row)
        assertEquals(6, move(4, Direction.Down).row)
        assertEquals(5, move(3, Direction.Down).row)
    }

    @Test
    fun upLandsOnTheNearestCentre() {
        assertEquals(0, move(3, Direction.Up).row)
        assertEquals(2, move(4, Direction.Up).row)
        assertEquals(3, move(5, Direction.Up).row)
        assertEquals("140 is 60 from 80 and 80 from 220", 3, move(6, Direction.Up).row)
    }

    @Test
    fun theFirstRowKeepsUpAndTheLastRowKeepsDown() {
        assertEquals(1, move(1, Direction.Up).row)
        assertEquals(6, move(6, Direction.Down).row)
        assertEquals("one row only", 3, move(3, Direction.Down, on = oneRow).row)
    }

    @Test
    fun leftAndRightFollowTheGamesAndEachRowsEnds() {
        assertEquals(1, move(0, Direction.Right).row)
        assertEquals(3, move(4, Direction.Left).row)
        assertEquals(Column.Systems, move(3, Direction.Left).column)
        assertEquals("the end of a row stays, as in an even grid", 2, move(2, Direction.Right).row)
        assertEquals(2, move(2, Direction.Right).row)
        assertEquals(Column.Details, move(2, Direction.Right, narrow = true).column)
        assertEquals("the last game", 6, move(6, Direction.Right).row)
    }

    @Test
    fun withoutAPackedTableTheGridMovesByColumns() {
        val even = StripContent(listOf(SystemEntry("recent", 0), packed.copy(packed = null)), hasBoxArt = true)
        assertEquals(3, move(0, Direction.Down, on = even).row)
        assertEquals(4, move(1, Direction.Down, on = even).row)
    }

    @Test
    fun theTableAnswersWhichRowAGameIsIn() {
        assertEquals(listOf(0, 0, 0, 1, 1, 2, 2), (0..6).map(rows::rowOf))
        assertEquals(true, rows.isRowEnd(2))
        assertEquals(true, rows.isRowEnd(6))
        assertEquals(false, rows.isRowEnd(0))
    }

    @Test
    fun aStaleTableIsNotUsed() {
        val state = packedStateWith(rows, itemCount = 9)
        assertEquals("falls back to columns (3)", 3, state.focusAfterDown(0))
        val good = packedStateWith(rows, itemCount = 7)
        assertEquals(3, good.focusAfterDown(1))
        good.setPackedRows("favorites", null)
        assertEquals("columns again once the grid is gone", 4, good.focusAfterDown(1))
    }

    @Test
    fun aReorderWithTheSameCountIsNotUsed() {
        val ids = listOf("a", "b", "c", "d", "e", "f", "g")
        val entry = SystemEntry("favorites", itemCount = 7, gridColumns = 3, itemIds = ids.reversed())
        val state = packedStateWith(rows.copy(ids = ids), itemCount = 7, entry = entry)
        assertEquals("the table is of another order: columns (3)", 3, state.focusAfterDown(0))
        val same = packedStateWith(rows.copy(ids = ids), itemCount = 7, entry = entry.copy(itemIds = ids))
        assertEquals("same games: the packed rows (1 -> 3)", 3, same.focusAfterDown(1))
    }

    @Test
    fun aSourceIsAskedAtEveryMoveAndASecondGridKeepsItsOwn() {
        val state = packedStateWith(rows, itemCount = 7)
        var current = rows
        val first: () -> PackedRows = { current }
        state.setPackedRowsSource("favorites", first)
        assertEquals(3, state.focusAfterDown(1))
        current = rows.copy(rowStarts = listOf(0, 5), centres = listOf(50f, 60f, 70f, 80f, 90f, 100f, 110f))
        assertEquals("the new table is used at once", 5, state.focusAfterDown(1))
        val second: () -> PackedRows = { rows }
        state.setPackedRowsSource("favorites", second)
        state.clearPackedRowsSource("favorites", first)
        assertEquals("the old grid going does not clear the new one's source", 3, state.focusAfterDown(1))
        state.clearPackedRowsSource("favorites", second)
        assertEquals("columns once the shown grid is gone", 4, state.focusAfterDown(1))
    }

    private fun StripState.focusAfterDown(row: Int): Int {
        focusRow(row)
        dispatch(Action.Move(Direction.Down))
        return focus.row
    }

    private fun packedStateWith(
        table: PackedRows,
        itemCount: Int,
        entry: SystemEntry = SystemEntry("favorites", itemCount = itemCount, gridColumns = 3),
    ): StripState {
        val state =
            StripState(
                StripFocus(Column.Games, systemIndex = 1, systemId = "favorites"),
                StripContent(listOf(SystemEntry("recent", 0), entry), hasBoxArt = true),
                StripGeometry(false, 24.dp, 100.dp, 400.dp, 20.dp, 24.dp, 24.dp),
                androidx.compose.ui.unit.Density(1f),
                false,
                androidx.compose.animation.core.snap(),
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            )
        state.setPackedRows("favorites", table)
        return state
    }
}
