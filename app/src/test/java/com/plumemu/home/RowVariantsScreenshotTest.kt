package com.plumemu.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun gba(id: String, title: String, file: String) = GameRowModel(id, title, "USA", systemId = "gba", systemAbbr = "GBA", fileName = file)

/** Rows of one title; two of them collide in two pairs, the others are unique. Tags are set by [withVariantTags], as the library does. */
internal fun variantRows(): List<GameRowModel> =
    withVariantTags(
        listOf(
            gba("1", "Metroid Fusion", "Metroid Fusion (USA).gba"),
            gba("2", "Mother 3", "Mother 3 (Japan).gba"),
            gba("3", "Mother 3", "Mother 3 (Japan) [T+Eng].gba"),
            gba("4", "Pokemon Emerald", "Pokemon Emerald (USA).gba"),
            gba("5", "Pokemon Emerald", "Pokemon Emerald (USA) (Rev 1).gba"),
            gba("6", "Zelda Hack", "Zelda Hack (USA) [Hack].gba"),
            gba("7", "Zelda Hack", "Zelda Hack (USA) (Beta).gba"),
        ),
    )

private val toList = listOf(Action.TapItem(Column.Systems, FIRST_SYSTEM_INDEX), Action.Enter)

private fun contentOf(rows: List<GameRowModel>) = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("gba", "Game Boy Advance", rows)), hasBoxArt = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class RowVariantsScreenshotTest {
    /** 768x432 and 576x432, Graphite and Paper: the words after the title in the row's small style; unique titles unchanged. */
    @Test
    fun twoRowsWithOneTitle() =
        ReferenceShots.capture("home_row_variants") {
            HomeAt(contentOf(variantRows()), toList)
        }
}

/** The label never runs into the right-hand text, and a narrow row cuts the label before the title. */
abstract class RowVariantsFitBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    protected fun check(label: String, size: TextSize, longTitle: Boolean) {
        val title = if (longTitle) "Pokemon Emerald Version Special Edition" else "Pokemon Emerald"
        val rows = withVariantTags(listOf(gba("a", title, "$title (USA).gba"), gba("b", title, "$title (USA) (Rev 1).gba")))
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { HomeAt(contentOf(rows), toList) } }
        compose.waitForIdle()
        val inRow = hasAnyAncestor(hasTestTag(HomeTags.game(1)))
        val text = compose.onNode(hasText("Rev 1", substring = true) and inRow, useUnmergedTree = true).fetchSemanticsNode()
        val trailing = compose.onNode(hasText("USA") and inRow, useUnmergedTree = true).fetchSemanticsNode()
        val layout = layoutOf(text)
        val visibleEnd = layout.getLineEnd(0, visibleEnd = true)
        println("VARIANT $label: text ${text.boundsInRoot}, trailing ${trailing.boundsInRoot}, visible $visibleEnd of ${layout.layoutInput.text.length}")
        assertTrue("$label: the label runs into the right-hand text", text.boundsInRoot.right <= trailing.boundsInRoot.left)
        assertTrue("$label: one line", layout.lineCount == 1)
        // The text is one line with one ellipsis, so a narrow row cuts the label (the end) before the title; room enough keeps it whole.
        if (!longTitle) assertTrue("$label: the label is cut though the row has room", visibleEnd == layout.layoutInput.text.length)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class RowVariantsFit576Test : RowVariantsFitBase() {
    @Test fun medium() = check("576x432 Medium", TextSize.Medium, longTitle = false)

    @Test fun largestLongTitle() = check("576x432 Largest long title", TextSize.Largest, longTitle = true)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class RowVariantsFit768Test : RowVariantsFitBase() {
    @Test fun medium() = check("768x432 Medium", TextSize.Medium, longTitle = false)

    @Test fun largestLongTitle() = check("768x432 Largest long title", TextSize.Largest, longTitle = true)
}
