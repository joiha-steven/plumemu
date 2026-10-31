package com.plumemu.home

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Appearance › Left edge = Letters where the focused list has no letters to show
 * (Recent, a list sorted by last played or year) left the peek blank: the counts faded for a rail that never came.
 * There the peek shows the default, the game counts; the stored setting is not changed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeLeftEdgeNoLettersTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = List(12) { i -> GameRowModel("g$i", "${'A' + i}ar game", "USA", systemAbbr = "SNES", systemId = "snes") }

    private fun show(
        sortedByName: Boolean,
        focusedEntry: (HomeContent) -> Int,
    ): HomeContent {
        val content = HomeContent.of(games, emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", games)), sortedByName = sortedByName, leftEdge = LeftEdgeSetting.Letters)
        val index = focusedEntry(content)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Medium)) {
                HomeAt(content, listOf(Action.TapItem(Column.Systems, index), Action.Enter, Action.TapItem(Column.Games, 0)))
            }
        }
        compose.waitForIdle()
        return content
    }

    /** True when the count of system row [index] draws anything: more than one colour in its box. */
    private fun countIsDrawn(index: Int): Boolean {
        val node = compose.onNode(hasTestTag(SystemRowTags.COUNT) and hasAnyAncestor(hasTestTag(HomeTags.system(index))), useUnmergedTree = true)
        val pixels = node.captureToImage().toPixelMap()
        val colours = HashSet<Int>()
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) colours += pixels[x, y].hashCode()
        return colours.size > 1
    }

    private fun railLetterCount() = compose.onAllNodes(hasAnyAncestor(hasTestTag(HomeTags.RAIL)), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun aListSortedByLastPlayedShowsTheCountsInThePeek() {
        val content = show(sortedByName = false) { it.entries.indexOfFirst { entry -> entry.id == "snes" } }
        assertFalse("no letters to show", content.entries.first { it.id == "snes" }.hasLetterRail)
        assertTrue("the peek is not blank: the system's count is drawn", countIsDrawn(content.entries.indexOfFirst { it.id == "snes" }))
        assertTrue("and Recent's count", countIsDrawn(0))
    }

    @Test
    fun recentShowsTheCountsInThePeek() {
        show(sortedByName = true) { 0 }
        assertTrue("Recent has no letters: its count is drawn", countIsDrawn(0))
    }

    @Test
    fun aListByNameStillShowsTheRail() {
        show(sortedByName = true) { it.entries.indexOfFirst { entry -> entry.id == "snes" } }
        assertTrue("the rail's letters are drawn", railLetterCount() > 0)
    }
}
