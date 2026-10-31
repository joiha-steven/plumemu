package com.plumemu.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import com.plumemu.identify.name.VariantTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Two games of one title tell apart in search results the way they do in the system's list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SearchVariantRowsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val content =
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
                    ),
                ),
            ),
        )

    @Test
    fun searchHitsKeepTheVariantTagsOfTheirRows() {
        val hits = SearchIndex.of(content).find("mario")
        assertEquals(listOf(emptyList(), listOf(VariantTag.Revision("1"))), hits.map { it.row.variantTags })
    }

    @Test
    fun searchResultRowShowsTheVariantLabel() {
        val search =
            SearchState().apply {
                open()
                onQueryChange("mario")
                showResults("mario", SearchIndex.of(content).find("mario"))
                toResults()
            }
        compose.setContent { PlumTheme(MediumText.Default) { HomeAt(content, BoardGamesFocused, search = search) } }
        compose.waitForIdle()
        compose.onAllNodesWithText("Rev 1", substring = true, useUnmergedTree = true).assertCountEquals(1)
    }
}
