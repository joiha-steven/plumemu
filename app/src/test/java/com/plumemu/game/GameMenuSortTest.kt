package com.plumemu.game

import com.plumemu.R
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sort choices: which are offered, the one in use marked, a pick stored, and the ways in (game menu, R3). */
@OptIn(ExperimentalCoroutinesApi::class)
class GameMenuSortTest {
    private val game = GameRef("7", "Chrono Trigger")

    private class FakeSorts(
        var current: SortGamesSetting = SortGamesSetting.Name,
        var hasYear: Boolean = false,
        var hasRating: Boolean = false,
    ) : SortActions {
        var failWith: Exception? = null

        override suspend fun choices() = SortChoices(current, hasYear, hasRating)

        override suspend fun set(sort: SortGamesSetting) {
            failWith?.let { throw it }
            current = sort
        }
    }

    private fun TestScope.controller(sorts: SortActions) = GameMenuController(FakeGameActions(), this, AppActions.None, sorts) {}

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.keys() = panel!!.content.focusableRows.map { it.key }

    private fun key(sort: SortGamesSetting) = SORT_KEY_PREFIX + sort.name

    private fun GameMenuController.openSortFromMenu() {
        repeat(keys().indexOf(GameMenuEntryId.SortBy.name)) { onAction(Action.Move(Direction.Down)) }
        onAction(Action.Enter)
    }

    @Test
    fun yearIsOfferedOnlyWhileSomeGameHasOneOrItIsInUse() {
        assertEquals(
            listOf(SortGamesSetting.Name, SortGamesSetting.LastPlayed, SortGamesSetting.MostPlayed, SortGamesSetting.RecentlyAdded),
            SortChoices(SortGamesSetting.Name, hasYear = false, hasRating = false).options,
        )
        assertEquals(SortGamesSetting.Year, SortChoices(SortGamesSetting.Name, hasYear = true, hasRating = false).options.last())
        assertTrue("the sort in use is never hidden", SortGamesSetting.Year in SortChoices(SortGamesSetting.Year, hasYear = false, hasRating = false).options)
    }

    @Test
    fun ratingIsOfferedLastOnlyWhileSomeGameHasOneOrItIsInUse() {
        val base = listOf(SortGamesSetting.Name, SortGamesSetting.LastPlayed, SortGamesSetting.MostPlayed, SortGamesSetting.RecentlyAdded)
        assertEquals("ratings alone: Rating without Year", base + SortGamesSetting.Rating, SortChoices(SortGamesSetting.Name, hasYear = false, hasRating = true).options)
        assertEquals("both: Year then Rating", base + SortGamesSetting.Year + SortGamesSetting.Rating, SortChoices(SortGamesSetting.Name, hasYear = true, hasRating = true).options)
        assertEquals("no ratings: no Rating", base + SortGamesSetting.Year, SortChoices(SortGamesSetting.Name, hasYear = true, hasRating = false).options)
        assertEquals(
            "the sort in use is never hidden",
            base + SortGamesSetting.Rating,
            SortChoices(SortGamesSetting.Rating, hasYear = false, hasRating = false).options,
        )
    }

    @Test
    fun ratingInUseIsMarkedAndFocusedAndAPickOfRatingIsStored() =
        run {
            val sorts = FakeSorts(current = SortGamesSetting.Rating)
            val menu = controller(sorts)
            menu.openSortMenu(game)
            val rows = menu.panel!!.content.focusableRows
            assertEquals("no ratings now, but it is in use", (SortGamesSetting.entries - SortGamesSetting.Year).map(::key), rows.map { it.key })
            assertEquals(key(SortGamesSetting.Rating), rows[menu.panel!!.focus].key)
            assertEquals(listOf(key(SortGamesSetting.Rating)), rows.filter { it.isCurrent }.map { it.key })
            menu.onAction(Action.TapItem(Column.Details, 0))
            assertEquals(SortGamesSetting.Name, sorts.current)
            sorts.hasRating = true
            menu.openSortMenu(game)
            menu.onAction(Action.TapItem(Column.Details, menu.keys().indexOf(key(SortGamesSetting.Rating))))
            assertEquals(SortGamesSetting.Rating, sorts.current)
            assertNull(menu.panel)
        }

    @Test
    fun theMenuListsSortByOnlyFromAListTheSortOrders() =
        run {
            val menu = controller(FakeSorts())
            menu.openMenu(game)
            assertFalse(GameMenuEntryId.SortBy.name in menu.keys())
            menu.close()
            menu.openMenu(game, canSort = true)
            assertTrue(GameMenuEntryId.SortBy.name in menu.keys())
            assertEquals(UiText.Res(R.string.game_menu_sort_by), menu.panel!!.content.focusableRows.first { it.key == GameMenuEntryId.SortBy.name }.label)
        }

    @Test
    fun sortByOpensTheChoicesWithTheCurrentOneMarkedAndFocused() =
        run {
            val menu = controller(FakeSorts(current = SortGamesSetting.MostPlayed))
            menu.openMenu(game, canSort = true)
            menu.openSortFromMenu()
            val rows = menu.panel!!.content.focusableRows
            assertEquals(SortGamesSetting.entries.filter { it != SortGamesSetting.Year && it != SortGamesSetting.Rating }.map(::key), rows.map { it.key })
            assertEquals(listOf(key(SortGamesSetting.MostPlayed)), rows.filter { it.isCurrent }.map { it.key })
            assertEquals("the cursor starts on the sort in use", key(SortGamesSetting.MostPlayed), rows[menu.panel!!.focus].key)
            menu.onAction(Action.Back)
            assertTrue("B goes back to the menu", GameMenuEntryId.SortBy.name in menu.keys())
        }

    @Test
    fun aPickStoresTheSortAndClosesThePanel() =
        run {
            val sorts = FakeSorts(hasYear = true)
            val menu = controller(sorts)
            menu.openMenu(game, canSort = true)
            menu.openSortFromMenu()
            menu.onAction(Action.TapItem(Column.Details, menu.keys().indexOf(key(SortGamesSetting.Year))))
            assertEquals(SortGamesSetting.Year, sorts.current)
            assertNull(menu.panel)
        }

    @Test
    fun r3OpensTheChoicesAloneAndAPickStoresIt() =
        run {
            val sorts = FakeSorts()
            val menu = controller(sorts)
            assertTrue(menu.openSortMenu(game))
            assertEquals(UiText.Res(R.string.game_sort_title), menu.panel!!.content.title)
            assertNull("no menu behind it: B closes", menu.panel!!.parent)
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Enter)
            assertEquals(SortGamesSetting.LastPlayed, sorts.current)
            assertNull(menu.panel)
        }

    @Test
    fun aFailedStoreShowsTheSaveMessageAndKeepsTheSort() =
        run {
            val sorts = FakeSorts().apply { failWith = IllegalStateException("disk") }
            val menu = controller(sorts)
            menu.openSortMenu(game)
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Enter)
            assertEquals(UiText.Res(R.string.game_error_save_title), menu.panel!!.content.title)
            assertEquals(SortGamesSetting.Name, sorts.current)
        }

    @Test
    fun aSecondOpenWhileAPanelIsUpIsIgnored() =
        run {
            val menu = controller(FakeSorts())
            menu.openSortMenu(game)
            assertFalse(menu.openSortMenu(game))
        }
}
