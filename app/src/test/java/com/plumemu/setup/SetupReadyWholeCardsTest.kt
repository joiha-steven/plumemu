package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Ready step's point is its counts: opened, the column rests on whole cards (none cut by its top or bottom),
 * the counts line in view, and Start playing whole above the hints.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupReadyWholeCardsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var height by mutableStateOf(432)
    private var state by mutableStateOf(SetupFakes.chooseFolder)

    private val longest =
        SetupFakes.ready.copy(
            emulators = EmulatorCoverage(1, 10, listOf("Game Boy", "Game Boy Advance", "Game Boy Color", "Atari 2600", "Sega Saturn", "Neo Geo", "Nintendo DS", "PlayStation", "Dreamcast")),
            hasScreenScraper = true,
            boxArtLine = BoxArtLine.SignInAgain,
        )

    private fun check(size: TextSize, ready: SetupUiState, countsAlwaysSeen: Boolean) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default.copy(textSize = size)) {
                key(height) { Box(Modifier.height(height.dp)) { SetupAt(state) } }
            }
        }
        for (h in 400..440 step 4) {
            state = SetupFakes.chooseFolder
            height = h
            compose.waitForIdle()
            state = ready
            compose.waitForIdle()
            assertWhole(h, countsAlwaysSeen)
        }
    }

    private fun assertWhole(h: Int, countsAlwaysSeen: Boolean) {
        val scroll = compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        // The frame reaches the shadow room past the window and the ring's reach; its draw clip cuts that back.
        val top = scroll.top.value + PlumDimens.focusRingOutset.value + SlabDimens.listEndRoom.value
        val bottom = scroll.bottom.value - PlumDimens.focusRingOutset.value - SlabDimens.listEndRoom.value
        val tags =
            listOf(SetupTags.GAME_COUNT, SetupTags.EMULATORS, SetupTags.BOX_ART_COMING, SetupTags.USAGE_SWITCH, SetupTags.control(SetupControl.StartPlaying))
        for (tag in tags) {
            for (b in boundsOf(tag)) {
                val cut = (b.top.value < top - TOL && b.bottom.value > top + TOL) || (b.top.value < bottom - TOL && b.bottom.value > bottom + TOL)
                assertTrue("At $h dp high $tag (${b.top}..${b.bottom}) is cut by the column ($top..$bottom)", !cut)
            }
        }
        if (countsAlwaysSeen) {
            val counts = compose.onNodeWithTag(SetupTags.GAME_COUNT, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("At $h dp high the counts line (${counts.top}..${counts.bottom}) is not in view (the whole screen is $h)", counts.top.value >= 0f && counts.bottom.value <= h)
            val hints = compose.onNodeWithTag(SetupTags.HINTS, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val start = compose.onNodeWithTag(SetupTags.control(SetupControl.StartPlaying), useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("At $h dp high Start playing ends at ${start.bottom}, past the hints' top ${hints.top}", start.bottom <= hints.top)
        }
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun wideLargeRestsOnWholeCards() = check(TextSize.Large, longest, countsAlwaysSeen = true)

    /** At Largest the title column has no height left for the counts under its body: they sit in the scrolling column (whole when it is scrolled to them), not squeezed in the title column. */
    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun wideLargestKeepsTheCountsInTheColumn() {
        check(TextSize.Largest, longest, countsAlwaysSeen = false)
        val scroll = compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val counts = compose.onNodeWithTag(SetupTags.GAME_COUNT, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the counts (left ${counts.left}) are not in the scrolling column (left ${scroll.left})", counts.left >= scroll.left)
        assertTrue("the counts (${counts.top}..${counts.bottom}) are squeezed under one line (${ONE_LINE})", counts.bottom - counts.top >= ONE_LINE)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrowLargeRestsOnWholeCards() = check(TextSize.Large, longest, countsAlwaysSeen = false)

    private fun boundsOf(tag: String): List<Bounds> =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false).map {
            Bounds(Dp(it.positionInRoot.y / compose.density.density), Dp((it.positionInRoot.y + it.size.height) / compose.density.density))
        }

    private data class Bounds(val top: Dp, val bottom: Dp)

    private companion object {
        const val TOL = 0.6f
        val ONE_LINE = 20.dp
    }
}
