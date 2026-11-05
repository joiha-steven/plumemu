package com.plumemu.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The steps changed for 0.1.2 (the confirm step's two rows, the folder step's two neutral points, a focused
 * folder card) at Small and Largest text, in the longer translations, at the reference sizes: every text is
 * drawn whole (no ellipsis) and ends inside the screen's width.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupFirstRunFitTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val steps =
        listOf(
            "confirm" to SetupFakes.confirm,
            "confirm again" to SetupFakes.confirmAgain,
            "folder empty" to SetupFakes.chooseFolder,
            "folder card focused" to SetupFakes.folderChosen.copy(focus = SetupControl.FolderCard0),
        )

    private fun check() {
        var size by mutableStateOf(TextSize.Small)
        var shown by mutableStateOf(SetupFakes.confirm)
        compose.setContent { PlumTheme(PlumThemeSettings.Default.copy(textSize = size)) { SetupAt(shown) } }
        for (text in listOf(TextSize.Small, TextSize.Largest)) {
            size = text
            for ((name, state) in steps) {
                shown = state
                compose.waitForIdle()
                val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
                val seen = visit(root, "$name at ${text.name}", root.boundsInRoot.width)
                assertTrue("$name at ${text.name}: no text was looked at", seen > 0)
            }
        }
    }

    private fun visit(
        node: SemanticsNode,
        what: String,
        width: Float,
    ): Int {
        var seen = 0
        if (node.layoutInfo.isPlaced && SemanticsProperties.Text in node.config) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val text = node.config[SemanticsProperties.Text].joinToString()
            assertTrue("$what: '$text' is ellipsised", layouts.none { layout -> (0 until layout.lineCount).any(layout::isLineEllipsized) })
            assertTrue("$what: '$text' ends at ${node.boundsInRoot.right} past the width $width", node.boundsInRoot.right <= width + 1f)
            seen++
        }
        return seen + node.children.sumOf { visit(it, what, width) }
    }

    @Test @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun englishAt832() = check()

    @Test @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun englishAt768() = check()

    @Test @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun englishAt576() = check()

    @Test @Config(qualifiers = "fr-w832dp-h468dp-land-xhdpi")
    fun frenchAt832() = check()

    @Test @Config(qualifiers = "fr-w768dp-h432dp-land-xhdpi")
    fun frenchAt768() = check()

    @Test @Config(qualifiers = "fr-w576dp-h432dp-land-xhdpi")
    fun frenchAt576() = check()

    @Test @Config(qualifiers = "vi-w832dp-h468dp-land-xhdpi")
    fun vietnameseAt832() = check()

    @Test @Config(qualifiers = "vi-w768dp-h432dp-land-xhdpi")
    fun vietnameseAt768() = check()

    @Test @Config(qualifiers = "vi-w576dp-h432dp-land-xhdpi")
    fun vietnameseAt576() = check()

    @Test @Config(qualifiers = "es-w576dp-h432dp-land-xhdpi")
    fun spanishAt576() = check()

    @Test @Config(qualifiers = "ja-w576dp-h432dp-land-xhdpi")
    fun japaneseAt576() = check()
}
