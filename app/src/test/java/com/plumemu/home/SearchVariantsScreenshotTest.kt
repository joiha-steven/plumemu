package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import com.plumemu.identify.name.VariantTag
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Search results where two games share a title: `home_search_variants` at 768x432 and 576x432. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SearchVariantsScreenshotTest {
    @Test
    fun resultsOfTheSameTitle() =
        ReferenceShots.capture("home_search_variants", sizes = ReferenceShots.Size.entries) {
            val content =
                HomeContent.of(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    listOf(
                        SystemGames(
                            "nes",
                            "Nintendo Entertainment System",
                            listOf(
                                GameRowModel("a", "Super Mario Bros. 2", "USA"),
                                GameRowModel("b", "Super Mario Bros. 2", "USA", variantTags = listOf(VariantTag.Revision("1"))),
                                GameRowModel("c", "Super Mario Bros. 2", "USA", variantTags = listOf(VariantTag.Translation("fr"))),
                            ),
                        ),
                    ),
                )
            val search =
                SearchState().apply {
                    open()
                    onQueryChange("mario")
                    showResults("mario", SearchIndex.of(content).find("mario"))
                    toResults()
                }
            HomeAt(content, BoardGamesFocused, search = search)
        }
}
