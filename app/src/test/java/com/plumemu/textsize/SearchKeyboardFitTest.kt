package com.plumemu.textsize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.components.keyboard.KeyId
import com.plumemu.coreui.components.keyboard.keyboardKeyTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import com.plumemu.home.search.SearchTags
import com.plumemu.home.typingFor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Search with Plumemu's keyboard at every text size, in Plum Sans at the six sizes
 * and in Serif and Mono at Largest: the top bar's query, count, clock and battery are whole; the hint
 * bar sits on the tray under the keys, its hints whole and apart from the keys; the results run on
 * under the tray's top edge with no empty band between them; with no match the line sits above the
 * tray; on the results (tray down) a focused row deep in the list is fully shown.
 */
abstract class SearchKeyboardFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val content = FakeHomeContent.withFavorites()

    private fun check(
        name: String,
        search: SearchState,
        assertions: (root: SemanticsNode, label: String, failures: MutableList<String>) -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { HomeAt(content, BoardGamesFocused, search = search) } }
        val combinations = TextSize.entries.map { PlumFont.Sans to it } + listOf(PlumFont.Serif to TextSize.Largest, PlumFont.Mono to TextSize.Largest)
        val failures = mutableListOf<String>()
        for ((eachFont, each) in combinations) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val label = "$name at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            val allow = FitAllowance(isLargest = each == TextSize.Largest)
            for (tag in listOf(SearchTags.TOP_BAR, HomeTags.HINT_BAR)) {
                val part = findTagged(root, tag)
                if (part == null) failures += "$label: $tag is not drawn" else textProblems(part, allow).mapTo(failures) { "$label: $it" }
            }
            assertions(root, label, failures)
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    private fun bounds(
        root: SemanticsNode,
        tag: String,
    ): Rect? = findTagged(root, tag)?.boundsInRoot

    /** The tray along the bottom, the hint bar on it under the keys, the results running on under its top edge. */
    private fun trayChecks(
        root: SemanticsNode,
        label: String,
        failures: MutableList<String>,
    ) {
        val screen = root.boundsInRoot
        val tray = bounds(root, SearchTags.TRAY) ?: return run { failures += "$label: no tray" }
        val hints = bounds(root, HomeTags.HINT_BAR) ?: return
        val lastKey = bounds(root, keyboardKeyTag(KeyId(3, 3))) ?: return run { failures += "$label: no action key" }
        val topBar = bounds(root, SearchTags.TOP_BAR) ?: return
        if (tray.left > 1f || tray.right < screen.right - 1f || tray.bottom < screen.bottom - 1f) failures += "$label: the tray is not along the screen's bottom ($tray)"
        if (hints.top < tray.top || hints.bottom > tray.bottom + 1f) failures += "$label: the hint bar is not on the tray ($hints, tray $tray)"
        if (hints.top < lastKey.bottom - 1f) failures += "$label: the hint bar overlaps the keys (${hints.top} < ${lastKey.bottom})"
        if (tray.top < topBar.bottom) failures += "$label: the tray reaches the top bar"
        // The keys' labels are KeyboardFitTest's; here only that each key is placed on the screen.
        val keys = listOf(KeyId(0, 0), KeyId(0, 9), KeyId(2, 7), KeyId(3, 0)).mapNotNull { bounds(root, keyboardKeyTag(it)) }
        // The block: 10 columns of 44 (a cell is 48 wide, 40 high), 476 over all, centred; key rows of 36.
        val density = screen.width / widthDp
        val firstKey = bounds(root, keyboardKeyTag(KeyId(0, 0)))
        val lastTop = bounds(root, keyboardKeyTag(KeyId(0, 9)))
        if (firstKey != null && lastTop != null) {
            val gap = 4f * density
            val block = (lastTop.right - gap / 2) - (firstKey.left + gap / 2)
            if (kotlin.math.abs(block - 476f * density) > 1.5f) failures += "$label: the block is ${block / density} dp wide, not 476"
            val leftSpace = firstKey.left + gap / 2
            val rightSpace = screen.right - (lastTop.right - gap / 2)
            if (kotlin.math.abs(leftSpace - rightSpace) > 1.5f) failures += "$label: the block is not centred ($leftSpace left, $rightSpace right)"
            if (kotlin.math.abs(firstKey.height / density - 40f) > 0.5f) failures += "$label: a key cell is ${firstKey.height / density} dp high, not 40 (36 + the gap)"
        }
        if (keys.size != 4 || keys.any { it.left < -1f || it.right > screen.right + 1f || it.bottom > screen.bottom + 1f }) failures += "$label: a key is off the screen ($keys)"
    }

    @Test
    fun typingWithResultsUnderTheTray() =
        check("search_typing", typingFor("super", content)) { root, label, failures ->
            trayChecks(root, label, failures)
            val list = bounds(root, SearchTags.RESULTS)
            val tray = bounds(root, SearchTags.TRAY)
            if (list == null || tray == null) {
                failures += "$label: no results list"
            } else if (list.bottom < tray.top - 1f) {
                failures += "$label: an empty band between the results and the tray (${list.bottom} < ${tray.top})"
            }
            val first = findTagged(root, SearchTags.result(0))
            if (first == null || tray == null || first.boundsInRoot.top >= tray.top) failures += "$label: no result shows above the tray"
        }

    @Test
    fun noMatchWhileTyping() =
        check("search_no_match", typingFor("metroid prime", content)) { root, label, failures ->
            trayChecks(root, label, failures)
            val line = bounds(root, SearchTags.NO_MATCH)
            val tray = bounds(root, SearchTags.TRAY)
            if (line == null || tray == null || line.bottom > tray.top + 1f) failures += "$label: the no-match line is under the tray ($line, $tray)"
            findTagged(root, SearchTags.NO_MATCH)?.let { node -> textProblems(node, FitAllowance()).mapTo(failures) { "$label: $it" } }
        }

    @Test
    fun aDeepResultFocusedWithTheTrayDown() {
        val search =
            SearchState().apply {
                open()
                onQueryChange("super")
                showResults("super", SearchIndex.of(content).find("super"))
                onAction(Action.Menu) // START: to the results
                repeat(DEEP_ROW) { onAction(Action.Move(Direction.Down)) }
            }
        check("search_results", search) { root, label, failures ->
            if (findTagged(root, SearchTags.TRAY) != null) failures += "$label: the tray is still up on the results"
            val list = findTagged(root, SearchTags.RESULTS) ?: return@check run { failures += "$label: no results" }
            val focused = findTagged(root, SearchTags.result(DEEP_ROW)) ?: return@check run { failures += "$label: the focused row is not drawn" }
            notFullyShown(focused, list)?.let { failures += "$label: the focused row $it" }
            val hints = bounds(root, HomeTags.HINT_BAR)
            if (hints != null && focused.boundsInRoot.bottom > hints.top) failures += "$label: the focused row reaches the hint bar"
        }
    }

    private companion object {
        const val DEEP_ROW = 12
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SearchKeyboardFitNovaTest : SearchKeyboardFitBase(576, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SearchKeyboardFitRp5Test : SearchKeyboardFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SearchKeyboardFitRp4ProTest : SearchKeyboardFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class SearchKeyboardFitThorTest : SearchKeyboardFitBase(837, 471)
