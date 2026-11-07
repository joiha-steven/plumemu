package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * With the hint bar turned off the strip is taller. A grid opened at its true end must rest in that taller window: on a row's top, as it
 * opens at the top, with no room left under its last row for another whole row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HintsOffRestTest {
    private val compose = appComposeRule()

    @get:Rule val rules = rulesAround(compose)

    private fun bounds(index: Int): DpRect? {
        val tag = HomeTags.cover(index)
        if (compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) return null
        return compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().let { DpRect(it.left, it.top, it.right, it.bottom) }
    }

    /** Opens the grid at its first cover, then afresh at its last, and checks where the end rests. */
    private fun assertRestsInTheTallerWindow(
        lastCover: Int,
        grid: @androidx.compose.runtime.Composable (cover: Int) -> Unit,
    ) {
        var atEnd by mutableStateOf(false)
        compose.setContent { PlumTheme { key(atEnd) { grid(if (atEnd) lastCover else 0) } } }
        compose.waitForIdle()
        val firstTop = bounds(0)!!.top
        atEnd = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val tops = (0..lastCover).mapNotNull { bounds(it)?.top }.distinct().sortedBy { it.value }
        val lastRow = bounds(lastCover)!!
        val stride = tops.last() - tops[tops.size - 2]
        val shownTop = tops.first { it.value >= firstTop.value - 1f }
        assertEquals("the end rests on a row's top, where the grid opens", firstTop.value, shownTop.value, 1f)
        val screen = compose.onRoot().getUnclippedBoundsInRoot().bottom
        val clearOfFade: Dp = screen - PlumDimens.stripMarginBottom - PlumDimens.listFadeBand
        assertTrue("room for another row under the last at ${lastRow.bottom}, clear of the fade at $clearOfFade", clearOfFade - lastRow.bottom < stride)
    }

    private fun coverGrid() =
        assertRestsInTheTallerWindow(FakeHomeContent.large(200).entries[FakeHomeContent.BOARD_SNES_INDEX].rows.size - 1) { cover ->
            val content = FakeHomeContent.large(200)
            HomeAt(content, gridActions(FakeHomeContent.BOARD_SNES_INDEX, cover), gridIds = content.entries.map { it.id }.toSet(), showHints = false)
        }

    private fun appsGrid() =
        assertRestsInTheTallerWindow(AppsGridApps.size - 1) { cover ->
            HomeAt(appsGridLibrary(), gridActions(APPS_ENTRY, cover), gridIds = setOf(HomeContent.APPS_ID), showHints = false)
        }

    @Test @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theCoverGridAt768() = coverGrid()

    @Test @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theCoverGridAt576() = coverGrid()

    @Test @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun appsAt768() = appsGrid()

    @Test @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun appsAt576() = appsGrid()

    // Tall enough that a third row of apps fits only without the hint bar.
    @Test @Config(qualifiers = "w768dp-h484dp-land-xhdpi")
    fun appsOnATallerScreenTakeTheRowTheBarLeftRoomFor() = appsGrid()
}
