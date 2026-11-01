package com.plumemu.home

import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Search's sunken field with either keyboard set, results shown: `home_search_field_<own|android>` at 768x432 and 576x432, Graphite and Paper. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SearchFieldScreenshotTest {
    private fun resultsFor(
        keyboard: KeyboardChoice,
        content: HomeContent,
    ) = SearchState(keyboardKind = keyboard).apply {
        open()
        onQueryChange("super")
        showResults("super", SearchIndex.of(content).find("super"))
        toResults()
    }

    @Test
    fun withPlumemusKeyboard() =
        ReferenceShots.capture("home_search_field_own") {
            HomeAt(FakeHomeContent.withFavorites(), BoardGamesFocused, search = resultsFor(KeyboardChoice.Plumemu, FakeHomeContent.withFavorites()))
        }

    @Test
    fun withAndroidsKeyboard() =
        ReferenceShots.capture("home_search_field_android") {
            HomeAt(FakeHomeContent.withFavorites(), BoardGamesFocused, search = resultsFor(KeyboardChoice.Android, FakeHomeContent.withFavorites()))
        }
}
