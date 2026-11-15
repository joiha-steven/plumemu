package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.rememberListGlide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A grid kept built beside the shown one (GridPages) takes no part in the bar fades: only a shown grid's scroll and focus count in [BarRunners]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w1024dp-h576dp-land-xhdpi")
class GridEdgeFadesShownTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var shown by mutableStateOf(true)
    private val runners = BarRunners()

    @Test
    fun aHiddenGridScrolledDownKeepsNoTopFadeOnAndLeavesTheBottomFadeAlone() {
        compose.setContent {
            PlumTheme {
                val grid = rememberLazyGridState()
                val glide = rememberListGlide(stride = 40.dp)
                val fades = remember { GridEdgeFades(grid, glide, 0f, liftShare = 0.05f, ringPx = 4f, isShown = { shown }) { 53 } }
                DisposableEffect(fades) {
                    runners.add(fades)
                    onDispose { runners.remove(fades) }
                }
                LaunchedEffect(grid) { grid.scrollToItem(50) }
                LazyVerticalGrid(GridCells.Fixed(1), Modifier.size(200.dp), state = grid) { items(100) { Box(Modifier.height(40.dp)) } }
            }
        }
        compose.waitForIdle()
        assertTrue("shown and scrolled: the top fade shows", runners.topFadeShows(10f))
        shown = false
        compose.waitForIdle()
        assertFalse("hidden: the top fade is not its business", runners.topFadeShows(10f))
        assertEquals("hidden: no room is asked of the bottom fade", 1f, runners.bottomFadeReach(10f), 0f)
    }
}
