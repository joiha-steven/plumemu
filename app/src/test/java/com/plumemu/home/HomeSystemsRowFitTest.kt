package com.plumemu.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.components.SystemRowTags
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

/**
 * Measured on the home strip at 768x432 and 576x432: the peek's counts (1,000 and up) keep inside
 * the peek like a 3-digit count, every system row is at least 40 high and equal, no row reaches into the hint bar's zone,
 * and a system's name is cut only when it does not fit before its count.
 */
abstract class HomeSystemsRowFitBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** Counts of one to five digits, and the longest real names of the platform data (displayName). */
    private fun library(): HomeContent {
        fun system(
            id: String,
            name: String,
            count: Int,
        ) = SystemGames(id, name, List(count) { GameRowModel("$id-$it", "$name $it", "USA", systemId = id) }, code = id.take(3).uppercase())
        return HomeContent.of(
            recent = List(12) { GameRowModel("r$it", "Game $it", "USA") },
            favorites = emptyList(),
            apps = List(41) { GameRowModel("a$it", "App $it", "") },
            systems =
                listOf(
                    system("gba", "Game Boy Advance", 1_284),
                    system("snes", "Super Nintendo", 248),
                    system("big", "Biggest Count", 99_999),
                    system("ngpc", "Neo Geo Pocket Color", 12_000),
                    system("fds", "Famicom Disk System", 1_284),
                    system("zx", "Sinclair ZX Spectrum", 1_000),
                    system("nes", "Nintendo", 42),
                    system("gb", "Game Boy", 9_999),
                    system("ps1", "PlayStation", 7),
                    system("n64", "Nintendo 64", 555),
                    system("md", "Mega Drive", 3_000),
                ),
        )
    }

    private fun show(
        size: TextSize,
        actions: List<Action>,
    ) = compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { HomeAt(library(), actions) } }

    private fun gamesFocused(system: Int) = listOf(Action.TapItem(Column.Systems, system), Action.Enter, Action.TapItem(Column.Games, 0))

    private fun systemsFocused(system: Int) = listOf(Action.TapItem(Column.Systems, system))

    private fun rowIndexes(): List<Int> = (0 until 16).filter { compose.onAllNodes(hasTestTag(HomeTags.system(it)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun inRow(
        index: Int,
        tag: String,
    ) = compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(HomeTags.system(index))), useUnmergedTree = true)

    /** B1: in the peek a 4- and 5-digit count starts no further left than a 3-digit one, and every count ends where it did. */
    protected fun peekCountsFit(size: TextSize) {
        show(size, gamesFocused(5))
        compose.waitForIdle()
        val rows = rowIndexes()
        val threeDigitLeft = inRow(FIRST + 1, SystemRowTags.COUNT).getUnclippedBoundsInRoot().left.value
        val problems = mutableListOf<String>()
        for (index in rows) {
            val row = compose.onNodeWithTag(HomeTags.system(index), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val count = inRow(index, SystemRowTags.COUNT).getUnclippedBoundsInRoot()
            if (kotlin.math.abs(count.right.value - (row.right.value - 10f)) > 0.5f) problems += "row $index: count ends ${count.right.value}, row ends ${row.right.value}"
            if (count.left.value < threeDigitLeft - 0.5f) problems += "row $index: count starts ${count.left.value}, a 3-digit count starts $threeDigitLeft"
        }
        assertTrue("$size: ${problems.size} problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** B2: equal rows, at least 40 high, none in the hint bar's zone, at the peek and with the column open. */
    protected fun rowsAreEvenAndClearOfTheHintBar(
        size: TextSize,
        open: Boolean,
        system: Int = 5,
    ) {
        show(size, if (open) systemsFocused(system) else gamesFocused(system))
        compose.waitForIdle()
        val hintTop = compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value
        val heights = mutableListOf<Float>()
        val problems = mutableListOf<String>()
        for (index in rowIndexes()) {
            val node = compose.onNodeWithTag(HomeTags.system(index), useUnmergedTree = true)
            val whole = node.getUnclippedBoundsInRoot()
            val seen = node.getBoundsInRoot()
            if (seen.bottom.value > hintTop + 0.5f) problems += "row $index seen to ${seen.bottom.value}, the hint bar starts at $hintTop"
            if (seen.bottom.value - seen.top.value > 0.5f && seen.bottom.value - seen.top.value >= whole.bottom.value - whole.top.value - 0.5f) heights += whole.bottom.value - whole.top.value
        }
        assertTrue("$size open=$open: ${heights.size} whole rows $heights", heights.size >= 4)
        if (heights.any { it < 40f - 0.05f }) problems += "rows under 40: $heights"
        if (heights.max() - heights.min() > 0.5f) problems += "rows uneven: $heights"
        assertTrue("$size open=$open: ${problems.size} problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** B3: a name is cut only when it does not fit before its count and the normal gap; "Game Boy Advance" fits beside a 4-digit count. */
    protected fun namesAreCutOnlyWhenTheyMustBe(size: TextSize) {
        show(size, systemsFocused(5))
        compose.waitForIdle()
        val problems = mutableListOf<String>()
        for (index in rowIndexes()) {
            val label = inRow(index, SystemRowTags.LABEL)
            val bounds = label.getUnclippedBoundsInRoot()
            val count = inRow(index, SystemRowTags.COUNT).getUnclippedBoundsInRoot()
            val text = label.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
            val layouts = mutableListOf<TextLayoutResult>()
            label.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.first()
            val natural = layout.multiParagraph.maxIntrinsicWidth / layout.layoutInput.density.density
            val room = count.left.value - 12f - bounds.left.value
            val cut = bounds.right.value - bounds.left.value < natural - 0.5f
            val gap = count.left.value - bounds.right.value
            if (cut && kotlin.math.abs(gap - 12f) > 0.5f) problems += "row $index '$text': cut ($natural wide) but the gap before the count is $gap, not 12"
            if (!cut && natural > room + 0.5f) problems += "row $index '$text': needs $natural, room $room, yet not cut"
            if (text == "Game Boy Advance" && cut) problems += "row $index '$text': cut, needs $natural, room $room"
        }
        assertTrue("$size: ${problems.size} problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    companion object {
        /** Recent, Favorites (empty: left out), Apps: the first system is the fourth entry... see [FIRST_SYSTEM_INDEX]. */
        val FIRST = FIRST_SYSTEM_INDEX

        /** The last entry of [library]: Recent, Favorites, Apps and eleven systems. */
        const val LAST = 13
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeSystemsRowFit768Test : HomeSystemsRowFitBase() {
    @Test fun peekCountsFitLarge() = peekCountsFit(TextSize.Large)

    @Test fun peekCountsFitLargest() = peekCountsFit(TextSize.Largest)

    @Test fun peekRowsAreEvenAtLarge() = rowsAreEvenAndClearOfTheHintBar(TextSize.Large, open = false)

    @Test fun openRowsAreEvenAtLarge() = rowsAreEvenAndClearOfTheHintBar(TextSize.Large, open = true)

    @Test fun openRowsAreEvenAtMedium() = rowsAreEvenAndClearOfTheHintBar(TextSize.Medium, open = true)

    @Test fun openRowsAreEvenAtLargest() = rowsAreEvenAndClearOfTheHintBar(TextSize.Largest, open = true)

    @Test fun lastRowKeepsOutOfTheHintBarInThePeek() = rowsAreEvenAndClearOfTheHintBar(TextSize.Large, open = false, system = LAST)

    @Test fun lastRowKeepsOutOfTheHintBarWithTheColumnOpen() = rowsAreEvenAndClearOfTheHintBar(TextSize.Large, open = true, system = LAST)

    @Test fun namesAreCutOnlyWhenTheyMustBeAtMedium() = namesAreCutOnlyWhenTheyMustBe(TextSize.Medium)

    @Test fun namesAreCutOnlyWhenTheyMustBeAtLarge() = namesAreCutOnlyWhenTheyMustBe(TextSize.Large)

    @Test fun namesAreCutOnlyWhenTheyMustBeAtLargest() = namesAreCutOnlyWhenTheyMustBe(TextSize.Largest)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeSystemsRowFit576Test : HomeSystemsRowFitBase() {
    @Test fun peekCountsFitLarge() = peekCountsFit(TextSize.Large)

    @Test fun peekCountsFitLargest() = peekCountsFit(TextSize.Largest)

    @Test fun peekRowsAreEvenAtLarge() = rowsAreEvenAndClearOfTheHintBar(TextSize.Large, open = false)

    @Test fun openRowsAreEvenAtLarge() = rowsAreEvenAndClearOfTheHintBar(TextSize.Large, open = true)

    @Test fun openRowsAreEvenAtMedium() = rowsAreEvenAndClearOfTheHintBar(TextSize.Medium, open = true)

    @Test fun openRowsAreEvenAtLargest() = rowsAreEvenAndClearOfTheHintBar(TextSize.Largest, open = true)

    @Test fun lastRowKeepsOutOfTheHintBarInThePeek() = rowsAreEvenAndClearOfTheHintBar(TextSize.Large, open = false, system = LAST)

    @Test fun lastRowKeepsOutOfTheHintBarWithTheColumnOpen() = rowsAreEvenAndClearOfTheHintBar(TextSize.Large, open = true, system = LAST)

    @Test fun namesAreCutOnlyWhenTheyMustBeAtMedium() = namesAreCutOnlyWhenTheyMustBe(TextSize.Medium)

    @Test fun namesAreCutOnlyWhenTheyMustBeAtLarge() = namesAreCutOnlyWhenTheyMustBe(TextSize.Large)

    @Test fun namesAreCutOnlyWhenTheyMustBeAtLargest() = namesAreCutOnlyWhenTheyMustBe(TextSize.Largest)
}
