package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.search.SearchTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Search's results rest as the games list does: above the keyboard's tray as above the hint bar, whole rows, and the next row running under the edge, no more than one cut there. */
abstract class SearchResultsWholeRowsBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    protected fun check() {
        val content = FakeHomeContent.withFavorites()
        val search = typingFor("a", content)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { HomeAt(content, BoardGamesFocused, search = search) } }
        compose.waitForIdle()
        val list = compose.onNodeWithTag(SearchTags.ROWS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val shown = (0 until search.results.size).filter { compose.onAllNodesWithTag(SearchTags.result(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("the list has more rows than fit: ${search.results.size} found, ${shown.size} shown", shown.size in 2 until search.results.size)
        val cut = mutableListOf<Int>()
        for (row in shown) {
            val bounds = compose.onNodeWithTag(SearchTags.result(row), useUnmergedTree = true).getUnclippedBoundsInRoot()
            // A row wholly past the end is the glide's, composed ahead and clipped; a row's gap is its own tap area.
            if (bounds.top.value >= list.bottom.value) continue
            val past = bounds.bottom.value - list.bottom.value
            if (past > PlumDimens.listRowGap.value + 0.5f) cut += row
        }
        assertTrue("at most one row runs past the list's end: $cut", cut.size <= 1)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SearchResultsWholeRows768Test : SearchResultsWholeRowsBase() {
    @Test fun resultsRestOnWholeRowsAboveTheTray() = check()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SearchResultsWholeRows576Test : SearchResultsWholeRowsBase() {
    @Test fun resultsRestOnWholeRowsAboveTheTray() = check()
}
