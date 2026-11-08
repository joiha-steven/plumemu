package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TextSize
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home empty-state buttons at the narrow screens and the largest text, in English, French, Vietnamese and Japanese: no label ends in an
 * ellipsis or is cut, every button stays between the systems column and the right margin and clear of the right edge fade, and where the
 * label fits the room (640) it is one line.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeEmptyButtonsOneLineTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun texts(node: SemanticsNode): List<SemanticsNode> =
        (if (SemanticsProperties.Text in node.config) listOf(node) else emptyList()) + node.children.flatMap(::texts)

    private fun check(
        why: EmptyLibrary,
        oneLine: Boolean,
    ) {
        val notices = HomeNotices(emptyLibrary = MutableStateFlow(why))
        var columnDp = 0f
        compose.setContent {
            PlumTheme(TableText.Default.copy(textSize = TextSize.Largest)) {
                columnDp = PlumTheme.layout.systemsColumnWidth.value
                CompositionLocalProvider(LocalHomeNotices provides notices) { HomeAt(FakeHomeContent.noGames()) }
            }
        }
        compose.waitForIdle()
        val density = compose.density.density
        val screen = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val fadeLeft =
            compose.onAllNodesWithTag(HomeTags.EDGE_FADE, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.left ?: screen.right
        val message = compose.onNodeWithTag(HomeTags.EMPTY_MESSAGE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val minLeft = (PlumDimens.stripMarginSide.value + columnDp + PlumDimens.columnGap.value) * density
        val maxRight = minOf(screen.right - PlumDimens.stripMarginSide.value * density, fadeLeft)
        val buttons = buttonsFor(why)
        assertTrue(buttons.isNotEmpty())
        buttons.forEach { button ->
            val node = compose.onNodeWithTag(HomeTags.emptyButton(button), useUnmergedTree = true).fetchSemanticsNode()
            val b = node.boundsInRoot
            val at = "$why $button $b (room $minLeft..$maxRight)"
            assertTrue("$at: left of the systems column and its gap", b.left >= minLeft - 1f)
            assertTrue("$at: right of the right margin or inside the edge fade", b.right <= maxRight + 1f)
            assertTrue("$at: outside the empty message $message", b.left >= message.left - 1f && b.right <= message.right + 1f)
            texts(node).forEach { t ->
                val layout = mutableListOf<TextLayoutResult>()
                t.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
                val l = layout.first()
                val what = "$at '${t.config[SemanticsProperties.Text].joinToString()}'"
                for (line in 0 until l.lineCount) assertFalse("$what ends in an ellipsis (line $line)", l.isLineEllipsized(line))
                assertTrue("$what is wider than its button", t.boundsInRoot.left >= b.left - 1f && t.boundsInRoot.right <= b.right + 1f)
                if (oneLine) assertEquals("$what breaks over ${l.lineCount} lines", 1, l.lineCount)
            }
        }
    }

    @Test fun english576NoGames() = check(EmptyLibrary.NoGames, oneLine = false)

    @Test fun english576NoFiles() = check(EmptyLibrary.NoFiles, oneLine = false)

    @Test fun english576NoFolder() = check(EmptyLibrary.NoFolder, oneLine = false)

    @Test
    @Config(qualifiers = "fr-w576dp-h432dp-land-xhdpi")
    fun french576NoGames() = check(EmptyLibrary.NoGames, oneLine = false)

    @Test
    @Config(qualifiers = "fr-w576dp-h432dp-land-xhdpi")
    fun french576NoFiles() = check(EmptyLibrary.NoFiles, oneLine = false)

    @Test
    @Config(qualifiers = "fr-w576dp-h432dp-land-xhdpi")
    fun french576NoFolder() = check(EmptyLibrary.NoFolder, oneLine = false)

    @Test
    @Config(qualifiers = "vi-w576dp-h432dp-land-xhdpi")
    fun vietnamese576NoGames() = check(EmptyLibrary.NoGames, oneLine = false)

    @Test
    @Config(qualifiers = "vi-w576dp-h432dp-land-xhdpi")
    fun vietnamese576NoFiles() = check(EmptyLibrary.NoFiles, oneLine = false)

    @Test
    @Config(qualifiers = "vi-w576dp-h432dp-land-xhdpi")
    fun vietnamese576NoFolder() = check(EmptyLibrary.NoFolder, oneLine = false)

    @Test
    @Config(qualifiers = "ja-w576dp-h432dp-land-xhdpi")
    fun japanese576NoGames() = check(EmptyLibrary.NoGames, oneLine = false)

    @Test
    @Config(qualifiers = "ja-w576dp-h432dp-land-xhdpi")
    fun japanese576NoFiles() = check(EmptyLibrary.NoFiles, oneLine = false)

    /** The Nova's 640: the room holds the English labels whole. */
    @Test
    @Config(qualifiers = "w640dp-h432dp-land-xhdpi")
    fun english640NoGamesOneLine() = check(EmptyLibrary.NoGames, oneLine = true)

    @Test
    @Config(qualifiers = "w640dp-h432dp-land-xhdpi")
    fun english640NoFilesOneLine() = check(EmptyLibrary.NoFiles, oneLine = true)

    /** The library error's button, at the largest text: whole and inside the margins. */
    private fun checkLibraryError() {
        compose.setContent {
            PlumTheme(TableText.Default.copy(textSize = TextSize.Largest)) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                com.plumemu.LibraryError(androidx.compose.runtime.remember { com.plumemu.coreinput.InputRouter(scope, onDebug = {}) }, onRetry = {}, onSettings = {})
            }
        }
        compose.waitForIdle()
        val density = compose.density.density
        val screen = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val node = compose.onNodeWithTag(com.plumemu.MainScreenTags.TRY_AGAIN, useUnmergedTree = true).fetchSemanticsNode()
        val b = node.boundsInRoot
        val margin = PlumDimens.stripMarginSide.value * density
        assertTrue("library error button $b inside the margins of $screen", b.left >= margin - 1f && b.right <= screen.right - margin + 1f)
        texts(node).forEach { t ->
            val layout = mutableListOf<TextLayoutResult>()
            t.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
            val l = layout.first()
            for (line in 0 until l.lineCount) assertFalse("library error label ends in an ellipsis", l.isLineEllipsized(line))
        }
    }

    @Test fun english576LibraryError() = checkLibraryError()

    @Test
    @Config(qualifiers = "vi-w576dp-h432dp-land-xhdpi")
    fun vietnamese576LibraryError() = checkLibraryError()
}
