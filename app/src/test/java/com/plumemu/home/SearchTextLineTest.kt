package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.search.SearchTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Search's typed text starts where the result titles do (82 vs 87 dp before), on the Thor too. */
abstract class SearchTextLineBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    protected fun check() {
        val content = FakeHomeContent.withFavorites()
        val search = typingFor("super", content)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { HomeAt(content, BoardGamesFocused, search = search) } }
        compose.waitForIdle()
        val typed = compose.onNodeWithTag(SearchTags.QUERY, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value
        val title = compose.onNodeWithTag(SearchTags.result(0), useUnmergedTree = true).getUnclippedBoundsInRoot().left.value
        // The row's own padding is the title's room; the typed text must start on the same line as the title's text.
        assertEquals("typed text x vs result row x + padding", title + com.plumemu.coreui.theme.PlumDimens.gameRowPadding.value, typed, 0.5f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w768dp-h432dp-land-xhdpi")
class SearchTextLineRp5Test : SearchTextLineBase() {
    @Test fun typedTextIsOnTheResultsTextLine() = check()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w837dp-h471dp-land-367dpi")
class SearchTextLineThorTest : SearchTextLineBase() {
    @Test fun typedTextIsOnTheResultsTextLine() = check()
}
