package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Y's star before the library answers, and the Favorites list held still while the person is in it. */
class FavoriteMarksTest {
    private fun row(
        id: String,
        isFavorite: Boolean = false,
    ) = GameRowModel(id, "Game $id", "USA", isFavorite = isFavorite)

    /** The library: SNES a, b, c, d; b and c are favorites. */
    private fun library(favorites: Set<String> = setOf("b", "c")): HomeContent {
        val snes = listOf("a", "b", "c", "d").map { row(it, it in favorites) }
        return HomeContent.of(
            recent = emptyList(),
            favorites = snes.filter { it.isFavorite },
            apps = listOf(GameRowModel("app", "Browser", "")),
            systems = listOf(SystemGames("snes", "Super Nintendo", snes)),
        )
    }

    private fun HomeContent.entry(id: String) = entries.single { it.id == id }

    private fun HomeContent.stars(entry: String) = entry(entry).rows.map { it.id to it.isFavorite }

    @Test
    fun withNothingPendingTheLibraryIsShownAsItIs() {
        val live = library()
        assertSame(live, FavoriteMarks().shown(live))
    }

    @Test
    fun yShowsTheStarAtOnceAndTheCountUpdatesBeforeTheLibraryAnswers() {
        val marks = FavoriteMarks()
        marks.requested("a", true, favoritesRows = null)
        val shown = marks.shown(library())
        assertEquals(listOf("a" to true, "b" to true, "c" to true, "d" to false), shown.stars("snes"))
        assertEquals("Favorites counts the new one at once", 3, shown.entry(HomeContent.FAVORITES_ID).shownCount)
        // The library answers with the same mark: nothing is pending any more.
        val answered = library(setOf("a", "b", "c"))
        marks.settle(answered)
        assertSame(answered, marks.shown(answered))
    }

    @Test
    fun aFailedWritePutsTheRowBack() {
        val marks = FavoriteMarks()
        marks.requested("a", true, favoritesRows = null)
        marks.failed("a", true)
        assertEquals(listOf("a" to false, "b" to true, "c" to true, "d" to false), marks.shown(library()).stars("snes"))
    }

    @Test
    fun twoQuickPressesEndWhereTheyStarted() {
        val marks = FavoriteMarks()
        marks.requested("a", true, favoritesRows = null)
        marks.requested("a", false, favoritesRows = null)
        // The first write landed, the second has not: the screen keeps the second press.
        marks.settle(library(setOf("a", "b", "c")))
        assertEquals(false, marks.shown(library(setOf("a", "b", "c"))).entry("snes").rows.first().isFavorite)
        marks.settle(library())
        assertEquals(listOf("a" to false, "b" to true, "c" to true, "d" to false), marks.shown(library()).stars("snes"))
    }

    @Test
    fun inTheFavoritesListAGameTakenOutStaysUntilTheListIsLeftAndComesBackOnY() {
        val marks = FavoriteMarks()
        val onScreen = library().entry(HomeContent.FAVORITES_ID).rows
        marks.requested("b", false, favoritesRows = onScreen)
        // The library answers: b is no longer a favorite and leaves its query.
        val answered = library(setOf("c"))
        marks.settle(answered)
        val held = marks.shown(answered)
        assertEquals("b stays in its place, star gone", listOf("b" to false, "c" to true), held.stars(HomeContent.FAVORITES_ID))
        assertEquals("the count says one", 1, held.entry(HomeContent.FAVORITES_ID).shownCount)
        assertEquals("the focus rules still see two rows", 2, held.entry(HomeContent.FAVORITES_ID).count)

        // Y again on the held row: back, in the same place.
        marks.requested("b", true, favoritesRows = held.entry(HomeContent.FAVORITES_ID).rows)
        marks.settle(library())
        assertEquals(listOf("b" to true, "c" to true), marks.shown(library()).stars(HomeContent.FAVORITES_ID))

        // Out again, then the person leaves the list: it follows the library.
        marks.requested("b", false, favoritesRows = null)
        marks.settle(answered)
        marks.release()
        assertEquals(listOf("c" to true), marks.shown(answered).stars(HomeContent.FAVORITES_ID))
    }

    @Test
    fun aHeldGameThatLeftTheLibraryLeavesTheHeldList() {
        val marks = FavoriteMarks()
        marks.requested("b", false, favoritesRows = library().entry(HomeContent.FAVORITES_ID).rows)
        // b was hidden meanwhile (or a rescan removed it): it is in no system's list any more.
        val hidden =
            HomeContent.of(
                emptyList(),
                listOf(row("c", true)),
                emptyList(),
                listOf(SystemGames("snes", "Super Nintendo", listOf(row("a"), row("c", true), row("d")))),
            )
        assertEquals(listOf("c" to true), marks.shown(hidden).stars(HomeContent.FAVORITES_ID))
    }

    @Test
    fun aGameHiddenAfterBeingTakenOutLeavesAndNothingStaysPending() {
        // m-eur is only in Favorites (not the primary copy); taken out with Y, then hidden from its menu.
        val marks = FavoriteMarks()
        val before = withCopy(true)
        marks.requested("m-eur", false, favoritesRows = before.entry(HomeContent.FAVORITES_ID).rows, inSystemList = false)
        val after = withCopy(false)
        marks.settle(after)
        marks.hidden("m-eur")
        assertEquals(listOf("b" to true), marks.shown(after).stars(HomeContent.FAVORITES_ID))
        assertEquals(1, marks.shown(after).entry(HomeContent.FAVORITES_ID).shownCount)
    }

    @Test
    fun yOnAGameThatLeftTheLibraryIsNeverCountedAndDoesNotStayPending() {
        val marks = FavoriteMarks()
        // "x" is in no list (removed by a rescan) when its late Y arrives.
        marks.requested("x", true, favoritesRows = null)
        assertEquals("the count does not grow", 2, marks.shown(library()).entry(HomeContent.FAVORITES_ID).shownCount)
        marks.settle(library())
        assertSame("nothing pending", library().entries.size, marks.shown(library()).entries.size)
        marks.requested("a", true, favoritesRows = null)
        marks.settle(library())
        assertEquals("only a counts", 3, marks.shown(library()).entry(HomeContent.FAVORITES_ID).shownCount)
    }

    /** SNES a and b (b a favorite); "m-eur" a favorite that is not the primary copy, listed only in Favorites when [isFavorite]. */
    private fun withCopy(isFavorite: Boolean) =
        HomeContent.of(
            emptyList(),
            listOfNotNull(row("b", true), if (isFavorite) row("m-eur", true) else null),
            emptyList(),
            listOf(SystemGames("snes", "Super Nintendo", listOf(row("a"), row("b", true)))),
        )

    @Test
    fun aFavoriteOnlyInFavoritesHoldsItsPlaceAndItsPendingMarkClears() {
        // "m-eur" is a favorite but no longer the primary copy: Favorites lists it, no system does.
        val marks = FavoriteMarks()
        marks.requested("m-eur", false, favoritesRows = withCopy(true).entry(HomeContent.FAVORITES_ID).rows, inSystemList = false)
        marks.settle(withCopy(true))
        assertEquals("not written yet: the star is off on screen", listOf("b" to true, "m-eur" to false), marks.shown(withCopy(true)).stars(HomeContent.FAVORITES_ID))
        val answered = withCopy(false)
        marks.settle(answered)
        assertEquals("written: the row keeps its place until the list is left", listOf("b" to true, "m-eur" to false), marks.shown(answered).stars(HomeContent.FAVORITES_ID))
        marks.release()
        assertSame("nothing pending any more", answered, marks.shown(answered))
    }

    @Test
    fun appsAreNeverMarked() {
        val marks = FavoriteMarks()
        marks.requested("app", true, favoritesRows = null)
        assertEquals(false, marks.shown(library()).entry(HomeContent.APPS_ID).rows.single().isFavorite)
    }
}
