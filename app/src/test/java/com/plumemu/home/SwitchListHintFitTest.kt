package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsPanelTags
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.ShotContent
import com.plumemu.settings.screen.optionsAt
import com.plumemu.textsize.FitAllowance
import com.plumemu.textsize.findTagged
import com.plumemu.textsize.notFullyShown
import com.plumemu.textsize.textProblems
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "Switch list" (the L2 R2 hint) and Controls › "Switch list with" with its explanation, in every language at 576x432 and the largest
 * text. The hint bar may give the hint up when it is full; a hint that is placed is whole on one line. The Settings row and the
 * explanation are not cut, not ellipsized and not clipped.
 */
abstract class SwitchListHintFitBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Why the L2 R2 hint is not whole on one line; "gave way" when the bar left it out; null when it is placed and whole. */
    private fun hintProblem(): String? {
        val nodes = compose.onAllNodesWithTag(HomeTags.hint(Action.JumpLetterNext), useUnmergedTree = true).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }
        val hint = nodes.firstOrNull() ?: return GAVE_WAY
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val label = context.getString(R.string.home_hint_switch_system)
        var problem: String? = null
        var seen = 0

        fun visit(node: SemanticsNode) {
            if (node.layoutInfo.isPlaced && SemanticsProperties.Text in node.config) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val layout = layouts.first()
                val natural = layout.multiParagraph.intrinsics.maxIntrinsicWidth
                val given = node.boundsInRoot.width
                if (layout.lineCount != 1 || natural > given + 1f || layout.isLineEllipsized(0)) problem = "'${node.config[SemanticsProperties.Text].joinToString()}': ${layout.lineCount} lines, needs $natural px, has $given px"
                if (node.config[SemanticsProperties.Text].joinToString() == label) seen++
            }
            node.children.forEach(::visit)
        }
        visit(hint)
        notFullyShown(hint, root)?.let { problem = "hint $it" }
        assertTrue("the label '$label' is in the placed hint", seen == 1)
        return problem
    }

    private var grid by mutableStateOf(false)
    private var size by mutableStateOf(TextSize.Largest)
    private var isSet = false

    private fun showHome(
        asGrid: Boolean,
        at: TextSize,
    ) {
        grid = asGrid
        size = at
        if (!isSet) {
            isSet = true
            compose.setContent {
                PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                    key(grid) { HomeAt(FakeHomeContent.withArtwork(), BoardGamesFocused, gridIds = if (grid) setOf("snes") else emptySet()) }
                }
            }
        }
        compose.waitForIdle()
    }

    /** At every text size, in a list and in a grid: the hint is whole on one line or the bar left it out. Returns "view size: placed|gave way" for each. */
    private fun checkAllSizes(): List<String> {
        val report = mutableListOf<String>()
        for (asGrid in listOf(false, true)) {
            for (at in TextSize.entries) {
                showHome(asGrid, at)
                val problem = hintProblem()
                val view = if (asGrid) "grid" else "list"
                assertTrue("$view at ${at.name}: $problem", problem == null || problem == GAVE_WAY)
                // A grid that is not one would make the grid case a copy of the list case.
                if (asGrid) assertTrue("the grid is drawn at ${at.name}", compose.onAllNodesWithTag(HomeTags.GRID, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
                report += "$view ${at.name}: ${problem ?: "placed"}"
            }
        }
        return report
    }

    @Test
    fun theHintIsWholeOrGivenUpAtEverySize() {
        val report = checkAllSizes()
        println("RESULT ${label()} ${context.resources.configuration.screenWidthDp}dp ${report.joinToString("; ")}")
        // The bar drops this hint first, so at 576 no state places it; at 768 English places it, which proves the probe finds a placed hint.
        if (label().startsWith("en") && context.resources.configuration.screenWidthDp >= 768) {
            assertTrue("L2 R2 is placed at some size in English: $report", report.any { it.contains("placed") })
        }
    }

    @Test
    fun theHintAtLargestIsWholeOrGivenUp() {
        for (asGrid in listOf(false, true)) {
            showHome(asGrid, TextSize.Largest)
            val problem = hintProblem()
            assertTrue("${if (asGrid) "grid" else "list"} at Largest: $problem", problem == null || problem == GAVE_WAY)
        }
    }

    @Test
    fun theSettingsRowAndItsExplanationAreNotCut() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                SettingsAt(optionsAt(SettingsOption.SwitchSystemWith), content = ShotContent.library)
            }
        }
        compose.waitForIdle()
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val allow = FitAllowance(setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, SettingsPanelTags.EXPLAIN), isLargest = true)
        val mine = setOf(context.getString(R.string.settings_switch_system_with), context.getString(R.string.settings_switch_system_body))
        val all = textProblems(root, allow)
        val problems = all.filter { p -> mine.any { p.contains("'$it'") } }
        assertTrue("${problems.size} problems:\n" + problems.joinToString("\n"), problems.isEmpty())
        val labelNode = compose.onAllNodesWithText(context.getString(R.string.settings_switch_system_with), useUnmergedTree = true).fetchSemanticsNodes().first()
        val layouts = mutableListOf<TextLayoutResult>()
        labelNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val labelLines = layouts.first().lineCount
        val row = findTagged(root, SettingsTags.option(SettingsOption.SwitchSystemWith))
        assertTrue("the Switch list with row is drawn", row != null)
        row?.let { r -> notFullyShown(r, root)?.let { assertTrue("the row $it", false) } }
        println("RESULT ${label()} ${context.resources.configuration.screenWidthDp}dp settings: label lines=$labelLines; other problems on this screen (not mine): ${all.size - problems.size}")
    }

    private fun label() = context.resources.configuration.locales[0].toLanguageTag()

    private companion object {
        const val GAVE_WAY = "gave way"
    }
}

private const val Q = "w576dp-h432dp-land-xhdpi"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = Q)
class SwitchListHintFitEnglishTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-$Q")
class SwitchListHintFitSpanishTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-$Q")
class SwitchListHintFitFrenchTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-$Q")
class SwitchListHintFitJapaneseTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ko-$Q")
class SwitchListHintFitKoreanTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-$Q")
class SwitchListHintFitVietnameseTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-$Q")
class SwitchListHintFitChineseTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-rTW-$Q")
class SwitchListHintFitTraditionalChineseTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SwitchListHintFit768EnglishTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w768dp-h432dp-land-xhdpi")
class SwitchListHintFit768VietnameseTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w768dp-h432dp-land-xhdpi")
class SwitchListHintFit768SpanishTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w768dp-h432dp-land-xhdpi")
class SwitchListHintFit768FrenchTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-w768dp-h432dp-land-xhdpi")
class SwitchListHintFit768JapaneseTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ko-w768dp-h432dp-land-xhdpi")
class SwitchListHintFit768KoreanTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-w768dp-h432dp-land-xhdpi")
class SwitchListHintFit768ChineseTest : SwitchListHintFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-rTW-w768dp-h432dp-land-xhdpi")
class SwitchListHintFit768TraditionalChineseTest : SwitchListHintFitBase()
