package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.search.SearchState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Search opened with an empty field and Plumemu's keyboard up: `home_search_empty` at 768x432 and 576x432. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SearchEmptyScreenshotTest {
    @Test
    fun emptyField() =
        ReferenceShots.capture("home_search_empty", sizes = ReferenceShots.Size.entries) {
            HomeAt(FakeHomeContent.withFavorites(), BoardGamesFocused, search = SearchState().apply { open() })
        }
}
