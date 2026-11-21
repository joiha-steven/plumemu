package com.plumemu.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.textsize.findTagged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The folder card on the Collections page, measured: the four covers (tall, wide and narrow boxes mixed) stand in a block that ends
 * flush at both sides of the column and has one gap between its rows, and the systems line never breaks between a system and its count.
 */
abstract class FolderCardEvenBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun game(system: String, abbr: String, n: Int) = GameRowModel("$system$n", "$abbr game $n", "USA", systemAbbr = abbr, systemId = system)

    /** Recent: 3DS 3, N64 2, DS 2, NSW 2, GB 1, NES 1; its first four games are a tall, a wide, a tall and a narrow box. */
    private fun content(): HomeContent {
        val tall = CoverShape(0.9f)
        val wide = CoverShape(1.4f)
        val narrow = CoverShape(0.6f)
        val recent =
            listOf(game("3ds", "3DS", 0), game("n64", "N64", 0), game("ds", "DS", 0), game("switch", "NSW", 0)) +
                listOf(game("3ds", "3DS", 1), game("n64", "N64", 1), game("ds", "DS", 1), game("switch", "NSW", 1), game("3ds", "3DS", 2), game("gb", "GB", 0), game("nes", "NES", 0))
        val systems =
            listOf(
                SystemGames("3ds", "Nintendo 3DS", recent.filter { it.systemId == "3ds" }, tall, code = "3DS"),
                SystemGames("n64", "Nintendo 64", recent.filter { it.systemId == "n64" }, wide, code = "N64"),
                SystemGames("ds", "Nintendo DS", recent.filter { it.systemId == "ds" }, tall, code = "NDS"),
                SystemGames("switch", "Nintendo Switch", recent.filter { it.systemId == "switch" }, narrow, code = "NSW"),
                SystemGames("gb", "Game Boy", recent.filter { it.systemId == "gb" }, wide, code = "GB"),
                SystemGames("nes", "NES", recent.filter { it.systemId == "nes" }, wide, code = "NES"),
            )
        return HomeContent.of(recent = recent, favorites = emptyList(), apps = emptyList(), systems = systems, otherFolders = emptyList())
    }

    private val recentCard = listOf(Action.TapItem(Column.Systems, 1), Action.Enter)

    private fun show(textSize: TextSize = TextSize.Small): SemanticsNode {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = textSize)) { HomeAt(content(), recentCard) } }
        compose.waitForIdle()
        return compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
    }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    @Test
    fun theCoversFormOneJustifiedRowSpanningTheColumn() = assertOneRowSpansColumn(TextSize.Small)

    @Test
    fun theCoversFormOneJustifiedRowSpanningTheColumnAtLargest() = assertOneRowSpansColumn(TextSize.Largest)

    /**
     * The contract: ONE row of covers, all one height, one gap, from the column's left edge to its right edge. It replaces the two-row
     * contract (and before it the pinned-columns one); a row is never scaled down to end short of the right margin.
     */
    private fun assertOneRowSpansColumn(size: TextSize) {
        val root = show(size)
        val mosaic = findTagged(root, FolderTags.MOSAIC)!!
        val details = findTagged(root, FolderTags.DETAILS)!!
        val covers = mosaic.descendants().filter { SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag] == HomeTags.DETAILS_COVER_WHOLE }.map { it.boundsInRoot }
        assertTrue("two to four covers in a row (${covers.size})", covers.size in 2..4)
        val gap = with(compose.density) { PlumDimens.factsColumnGap.toPx() }
        for (c in covers) assertEquals("one height", covers[0].height, c.height, 1f)
        for (c in covers) assertEquals("the row's top is the column's top", details.boundsInRoot.top, c.top, 1f)
        for (i in 1 until covers.size) assertEquals("one gap before cover $i", gap, covers[i].left - covers[i - 1].right, 1f)
        assertEquals("starts at the column's left edge", details.boundsInRoot.left, covers.first().left, 1f)
        assertEquals("ends at the column's right edge", details.boundsInRoot.right, covers.last().right, 1f)
        assertTrue("covers keep their own shapes (${covers.map { it.width.toInt() }})", covers.map { it.width.toInt() }.toSet().size > 1)
    }

    @Test
    fun theSystemsLineBreaksOnlyBetweenSystems() = assertSystemsBreakBetweenItems(TextSize.Small)

    @Test
    fun theSystemsLineBreaksOnlyBetweenSystemsAtLargest() = assertSystemsBreakBetweenItems(TextSize.Largest)

    private fun assertSystemsBreakBetweenItems(size: TextSize) {
        val root = show(size)
        val node = findTagged(root, FolderTags.SYSTEMS)!!
        val layout = mutableListOf<TextLayoutResult>().also { list -> node.descendants().first { SemanticsActions.GetTextLayoutResult in it.config }.config[SemanticsActions.GetTextLayoutResult].action?.invoke(list) }.single()
        val text = layout.layoutInput.text.text
        var start = 0
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line, visibleEnd = false)
            val first = text.substring(start, end).trimEnd()
            assertTrue("line ${line + 1} of '$text' ends inside a system: '$first'", first.endsWith("·") || first.endsWith("…"))
            start = end
        }
        assertTrue("'$text' keeps whole systems", text.removeSuffix("…").split(" · ", " · ").all { Regex("""\S+ \d+""").matches(it.trim()) || it.contains(' ') })
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class FolderCardEvenThorTest : FolderCardEvenBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class FolderCardEven768Test : FolderCardEvenBase()
