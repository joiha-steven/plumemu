package com.plumemu.home

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The main screen: favorites with their star (and the games hint bar with Y and X, in
 * all three fonts at both sizes), the Favorites list holding a game just taken out, search with
 * results and with no match.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeD3ScreenshotTest {
    @Test
    fun gamesWithFavorites() =
        ReferenceShots.capture("home_favorites", fonts = PlumFont.entries) {
            HomeAt(FakeHomeContent.withFavorites(longTitle = true), BoardGamesFocused)
        }

    @Test
    fun favoritesListHoldingAGameTakenOut() =
        ReferenceShots.capture("home_favorites_held") {
            val live = FakeHomeContent.withFavorites()
            val marks = FavoriteMarks()
            // EarthBound (second in Favorites) taken out with Y while in the list; the library answered.
            marks.requested("snes-2", false, live.entries[1].rows)
            val answered = live.marked("snes-2", false)
            marks.settle(answered)
            HomeAt(marks.shown(answered), listOf(Action.TapItem(Column.Systems, 1), Action.Enter, Action.TapItem(Column.Games, 1)))
        }

    @Test
    fun searchWithResults() =
        ReferenceShots.capture("home_search_results", sizes = ReferenceShots.Size.entries) {
            HomeAt(FakeHomeContent.withFavorites(), BoardGamesFocused, search = searchFor("super", FakeHomeContent.withFavorites(), onResults = true))
        }

    @Test
    fun searchWithNoMatch() =
        ReferenceShots.capture("home_search_no_match") {
            HomeAt(FakeHomeContent.withFavorites(), BoardGamesFocused, search = searchFor("metroid prime", FakeHomeContent.withFavorites(), onResults = false))
        }

    /** Typing (Plumemu's keyboard): "super" with its results running on under the tray, o focused. */
    @Test
    fun searchTyping() =
        ReferenceShots.capture("home_search_typing", sizes = ReferenceShots.Size.entries) {
            HomeAt(FakeHomeContent.withFavorites(), BoardGamesFocused, search = typingFor("super", FakeHomeContent.withFavorites()))
        }

    /** The digits and symbols page (Y), "Y ABC" in the hints. */
    @Test
    fun searchSymbols() =
        ReferenceShots.capture("home_search_symbols") {
            HomeAt(FakeHomeContent.withFavorites(), BoardGamesFocused, search = typingFor("super", FakeHomeContent.withFavorites()).apply { onAction(Action.ToggleFavorite) })
        }

    /** The tightest case: Largest text on the Nova (and the RP5), the tray at its 36 dp keys. */
    @Test
    fun searchTypingLargest() =
        ReferenceShots.capture("home_search_typing", sizes = ReferenceShots.Size.entries, textSize = TextSize.Largest) {
            HomeAt(FakeHomeContent.withFavorites(), BoardGamesFocused, search = typingFor("super", FakeHomeContent.withFavorites()))
        }

    private fun searchFor(
        query: String,
        content: HomeContent,
        onResults: Boolean,
    ): SearchState =
        SearchState().apply {
            open()
            onQueryChange(query)
            showResults(query, SearchIndex.of(content).find(query))
            if (onResults) toResults()
        }
}

/** Search typing [query] with its results answered and the keys' focus moved to o. */
internal fun typingFor(
    query: String,
    content: HomeContent,
): SearchState =
    SearchState().apply {
        open()
        onQueryChange(query)
        showResults(query, SearchIndex.of(content).find(query))
        repeat(8) { onAction(Action.Move(com.plumemu.coreinput.Direction.Right)) }
    }
