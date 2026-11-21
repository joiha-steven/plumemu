package com.plumemu.coreinput.strip

import android.os.Bundle
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.plumemu.coreui.testing.ReferenceShots

/** The folder level inside the Collections entry: Favorites, Collections (folders), Apps, one system. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class StripFolderLevelTest {
    private fun folder(id: String, count: Int, columns: Int = 1) = SystemEntry(id, count, columns, itemIds = List(count) { "$id-$it" }, isFolder = true)

    private val recent = folder("recent", 3)
    private val empty = folder("most_played", 0)
    private val own = folder("collection:7", 2, columns = 2)

    // Rows of the folder list: three folders then "New collection..." (no folder behind it).
    private fun collections(vararg folders: SystemEntry) =
        SystemEntry(
            "collections",
            itemCount = folders.size + 1,
            hasDetails = false,
            itemIds = folders.map { it.id } + "new_collection",
            folders = folders.toList(),
            isFolderList = true,
        )

    private fun content(vararg folders: SystemEntry) =
        StripContent(
            listOf(SystemEntry("favorites", 2), collections(*folders), SystemEntry("apps", 4), SystemEntry("snes", 9)),
            hasBoxArt = true,
        )

    private val all = content(recent, empty, own)
    private val enter = Action.Enter
    private val back = Action.Back
    private fun move(d: Direction) = Action.Move(d)

    private fun reduce(focus: StripFocus, action: Action, on: StripContent = all) = StripReducer.reduce(focus, action, on, isNarrow = false)

    /** The cursor on the folder list's row [row], games column. */
    private fun onFolders(row: Int = 0) = StripFocus(Column.Games, 1, "collections", rows = mapOf("collections" to row))

    @Test
    fun aOnAFolderOpensItAndFocusesItsFirstGame() {
        val reduction = reduce(onFolders(0), enter)
        assertEquals(OpenFolder("collections", "recent"), reduction.focus.openFolder)
        assertEquals(Column.Games, reduction.focus.column)
        assertEquals(0, reduction.focus.row)
        assertTrue(reduction.intents.isEmpty())
    }

    @Test
    fun aTapOnAFolderRowOpensItAtOnce() {
        val reduction = reduce(onFolders(0), Action.TapItem(Column.Games, 2))
        assertEquals(OpenFolder("collections", "collection:7"), reduction.focus.openFolder)
        assertEquals("the tap also moved the list's row", 2, reduction.focus.rowOf("collections"))
        // From the system column a tap on the peeking list opens too.
        val fromColumn = reduce(StripFocus(Column.Systems, 1, "collections"), Action.TapItem(Column.Games, 1))
        assertEquals("most_played", fromColumn.focus.openFolder?.id)
        assertEquals(Column.Games, fromColumn.focus.column)
    }

    @Test
    fun anEmptyFolderOpensAndTheCursorStaysInItsGamesColumn() {
        val opened = reduce(onFolders(1), enter).focus
        assertEquals("most_played", opened.openFolder?.id)
        assertEquals(Column.Games, opened.column)
        val state = reduce(opened, enter)
        assertTrue("A on an empty folder plays nothing", state.intents.isEmpty())
    }

    @Test
    fun aOnNewCollectionOpensNothingAndAsksTheScreen() {
        val reduction = reduce(onFolders(3), enter)
        assertNull(reduction.focus.openFolder)
        assertEquals(listOf<StripIntent>(StripIntent.ActivateRow("collections", "new_collection")), reduction.intents)
        val tapped = reduce(onFolders(0), Action.TapItem(Column.Games, 3))
        assertEquals(listOf<StripIntent>(StripIntent.ActivateRow("collections", "new_collection")), tapped.intents)
    }

    @Test
    fun backFromAFolderShowsTheFoldersWithTheFocusOnThatFolder() {
        val inside = reduce(onFolders(2), enter).focus
        val closed = reduce(inside, back).focus
        assertNull(closed.openFolder)
        assertEquals(Column.Games, closed.column)
        assertEquals(2, closed.row)
        // Back from the folder list goes to the system column, as today.
        assertEquals(Column.Systems, reduce(closed, back).focus.column)
    }

    @Test
    fun aFolderOpenedFromItsRowAlwaysStartsAtItsFirstGame() {
        val inside = reduce(onFolders(0), enter).focus
        val moved = reduce(inside, move(Direction.Down)).focus
        assertEquals(1, moved.row)
        val back = reduce(reduce(moved, back).focus, move(Direction.Down)).focus
        val second = reduce(back, enter).focus
        assertEquals(0, second.row)
        // A on a folder left on its second game opens it on the first again (it used to open on the row left, the second cover).
        val again = reduce(reduce(reduce(second, this.back).focus, move(Direction.Up)).focus, enter).focus
        assertEquals("recent", again.openFolder?.id)
        assertEquals(0, again.row)
    }

    @Test
    fun yRThreeSelectAndStartDoNothingOnAFolderRow() {
        val focus = onFolders(0)
        for (action in listOf(Action.ToggleFavorite, Action.Sort, Action.ToggleView, Action.Menu, Action.JumpLetterNext, Action.JumpLetterPrevious)) {
            val reduction = reduce(focus, action)
            assertTrue("$action sends nothing on a folder row", reduction.intents.isEmpty())
            assertEquals("$action leaves the cursor", focus.row to focus.column, reduction.focus.row to reduction.focus.column)
            assertNull(reduction.focus.openFolder)
        }
        assertTrue("a long press only focuses the row", reduce(focus, Action.LongPressItem(Column.Games, 2)).intents.isEmpty())
        assertEquals(2, reduce(focus, Action.LongPressItem(Column.Games, 2)).focus.row)
        assertEquals("X still searches", listOf<StripIntent>(StripIntent.OpenSearch), reduce(focus, Action.Search).intents)
    }

    @Test
    fun startInAnEmptyFolderOpensNothingAsYAndR3Do() {
        val opened = reduce(onFolders(1), enter).focus
        for (action in listOf(Action.Menu, Action.ToggleFavorite, Action.Sort)) {
            assertTrue("$action in an empty folder emits nothing", reduce(opened, action).intents.isEmpty())
        }
    }

    @Test
    fun selectInAnEmptyFolderDoesNotFlipItsSavedView() {
        val opened = reduce(onFolders(1), enter).focus
        assertTrue("SELECT in an empty folder emits nothing", reduce(opened, Action.ToggleView).intents.isEmpty())
    }

    @Test
    fun insideAFolderTheKeysActOnItsGames() {
        val inside = reduce(onFolders(0), enter).focus
        assertEquals(listOf<StripIntent>(StripIntent.ToggleFavorite("recent", 0)), reduce(inside, Action.ToggleFavorite).intents)
        assertEquals(listOf<StripIntent>(StripIntent.ToggleView("recent")), reduce(inside, Action.ToggleView).intents)
        assertEquals(listOf<StripIntent>(StripIntent.Play("recent", 0)), reduce(inside, enter).intents)
        assertEquals(listOf<StripIntent>(StripIntent.OpenGameMenu("recent", 0)), reduce(inside, Action.Menu).intents)
    }

    @Test
    fun aFolderGridMovesByItsColumns() {
        val inside = reduce(onFolders(2), enter).focus
        assertEquals(Column.Games, inside.column)
        assertEquals("collection:7", inside.openFolder?.id)
        assertEquals(1, reduce(inside, move(Direction.Right)).focus.row)
    }

    @Test
    fun l2AndR2LeaveTheFolderAndComingBackReopensItWithItsFocus() {
        var focus = reduce(reduce(onFolders(0), enter).focus, move(Direction.Down)).focus
        assertEquals(1, focus.row)
        focus = reduce(focus, Action.NextSystem).focus
        assertEquals("apps", focus.systemId)
        assertEquals("the folder is remembered while away", "recent", focus.openFolder?.id)
        focus = reduce(focus, Action.PreviousSystem).focus
        assertEquals("collections", focus.systemId)
        assertEquals("recent", focus.openFolder?.id)
        assertEquals(1, focus.row)
        assertEquals(Column.Games, focus.column)
    }

    @Test
    fun aFolderIsKeptByIdWhenANewCollectionShiftsTheIndexes() {
        val inside = reduce(onFolders(2), enter).focus
        val another = folder("collection:3", 4)
        val shifted = content(recent, empty, another, own)
        val now = inside.reconciled(shifted, hasDetailsStop = false)
        assertEquals("collection:7", now.openFolder?.id)
        assertEquals(2, now.shownIn(shifted.systems[1]).itemCount)
    }

    @Test
    fun aDeletedFolderIsClosedAndTheListKeepsItsRow() {
        val inside = reduce(onFolders(2), enter).focus
        val gone = content(recent, empty).let { inside.reconciled(it, hasDetailsStop = false) }
        assertNull(gone.openFolder)
    }

    @Test
    fun theOpenFolderSurvivesAProcessDeath() {
        val inside = reduce(onFolders(2), enter).focus
        val bundle = StripFocusBundle.save(inside)
        val parcel = android.os.Parcel.obtain()
        try {
            bundle.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = Bundle.CREATOR.createFromParcel(parcel)
            assertEquals(inside, StripFocusBundle.restore(restored))
        } finally {
            parcel.recycle()
        }
        assertNull(StripFocusBundle.restore(StripFocusBundle.save(onFolders(1))).openFolder)
    }

    @Test
    fun pushRightToPlayNeverArmsAFolderRow() {
        val reduction = StripReducer.reduce(onFolders(0), move(Direction.Right), all, isNarrow = false, pushRightPlays = true)
        assertTrue(!reduction.armsRight)
    }
}
