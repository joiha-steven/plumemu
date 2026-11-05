package com.plumemu.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.TopBar
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.search.SearchState
import com.plumemu.home.search.SearchTags
import com.plumemu.home.search.SearchTopBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Search's top bar is the shared one: the bar is as high as another screen's, its title has the shared title's size and weight on
 * the same baseline inside it, the query field is centred in it and no taller than it, and the field's collar (the only part that
 * reaches past the bar) keeps clear of the screen's top edge. The reference is the shared [TopBar] with the same title, stacked above.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SearchTopBarAlignTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    private fun baseline(node: SemanticsNode): Float = node.boundsInRoot.top + layoutOf(node).firstBaseline

    @Test
    fun theBarTheTitleAndTheFieldMatchTheSharedBar() {
        var size by mutableStateOf(TextSize.Small)
        var density = Density(1f)
        val title = "Search"
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                density = LocalDensity.current
                Column {
                    TopBar(place = title, emphasis = title, clock = { "5:36 PM" }, battery = { "70%" }, modifier = Modifier.testTag("reference_bar"))
                    Spacer(Modifier.height(20.dp))
                    SearchTopBar(SearchState(), clock = { "5:36 PM" }, battery = { "70%" })
                }
            }
        }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val reference = compose.onNodeWithTag("reference_bar").fetchSemanticsNode().boundsInRoot
            val bar = compose.onNodeWithTag(SearchTags.TOP_BAR).fetchSemanticsNode().boundsInRoot
            val titles = compose.onAllNodesWithText(title).fetchSemanticsNodes()
            assertEquals("one title per bar at $each", 2, titles.size)
            val referenceBase = baseline(titles[0]) - reference.top
            val base = baseline(titles[1]) - bar.top
            val shared = layoutOf(titles[0]).layoutInput.style
            val shown = layoutOf(titles[1]).layoutInput.style
            val field = compose.onNodeWithTag(SearchTags.FIELD).fetchSemanticsNode().boundsInRoot
            val dp = with(density) { 1.dp.toPx() }
            if (abs(bar.height - reference.height) > dp / 2) problems += "$each: bar ${bar.height / dp} dp high, shared bar ${reference.height / dp}"
            if (shown.fontSize != shared.fontSize || shown.fontWeight != shared.fontWeight) problems += "$each: title ${shown.fontSize} ${shown.fontWeight}, shared bar ${shared.fontSize} ${shared.fontWeight}"
            if (abs(base - referenceBase) > dp / 2) problems += "$each: title baseline ${base / dp} dp into the bar, shared bar ${referenceBase / dp}"
            if (abs((field.top + field.bottom) / 2 - (bar.top + bar.bottom) / 2) > dp / 2) problems += "$each: field centre is not the bar's centre"
            if (field.height > bar.height + dp / 2) problems += "$each: field ${field.height / dp} dp high in a ${bar.height / dp} dp bar"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun theFieldsCollarKeepsClearOfTheScreensTopEdge() {
        var size by mutableStateOf(TextSize.Small)
        var density = Density(1f)
        var page = Color.Black
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                density = LocalDensity.current
                page = PlumTheme.colors.bg
                // The bar sits the strip's top margin down, as on screen.
                Column(Modifier.background(PlumTheme.colors.bg).padding(top = PlumDimens.stripMarginTop)) {
                    SearchTopBar(SearchState(), clock = { "5:36 PM" }, battery = { "70%" })
                }
            }
        }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val field = compose.onNodeWithTag(SearchTags.FIELD).fetchSemanticsNode().boundsInRoot
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val x = ((field.left + field.right) / 2).toInt()
            var top = 0
            while (top < field.top.toInt() && maxOf(abs(pixels[x, top].red - page.red), abs(pixels[x, top].green - page.green), abs(pixels[x, top].blue - page.blue)) < 3f / 255f) top++
            val clear = top / with(density) { 1.dp.toPx() }
            println("COLLAR $each clear=$clear dp")
            if (clear < MIN_CLEAR.value) problems += "$each: the field's collar starts $clear dp from the top edge, the board's is ${MIN_CLEAR.value}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        /** The board's: the field at y 24 px with its collar 9 px out (1152 px board, 1.5 px per dp). */
        val MIN_CLEAR = 10.dp
    }
}
