package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ListGlide
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.rememberListGlide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The list scrolls in the same layout pass as the focus moves (the audit's edge flicker): every
 * layout of the list, recorded as it happens, shows the focused row whole. A scroll launched from
 * a coroutine after the frame left one layout with the focus past the edge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class KeepFocusedVisibleTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** One layout pass of the list: the focus it was laid out for and where that row stood. */
    private data class Pass(
        val focus: Int,
        val top: Int?,
        val bottom: Int?,
        val windowEnd: Int,
        val glide: Float,
    )

    private var focus by mutableIntStateOf(0)

    /** The focus as of the last composition: read in layout, it lays the list out after each one. */
    private var composedFocus by mutableIntStateOf(0)
    private val passes = mutableListOf<Pass>()

    /** Per composition: the focus and the scroll position (px from the list's top) it ended with. */
    private val scrolls = mutableListOf<Pair<Int, Int>>()
    private val rowPx: Int get() = with(compose.density) { ROW.roundToPx() }
    private val windowPx: Int get() = with(compose.density) { VIEWPORT.roundToPx() }
    private lateinit var list: LazyListState
    private lateinit var scope: CoroutineScope
    private var glide: ListGlide? = null

    private fun showList(
        motion: Boolean,
        heightOf: (Int) -> Dp = { ROW },
    ) {
        compose.setContent {
            PlumTheme(PlumThemeSettings(animationsOn = motion)) {
                scope = rememberCoroutineScope()
                list = rememberLazyListState()
                val glide = rememberListGlide(stride = ROW).also { this.glide = it }
                Box(Modifier.size(200.dp, VIEWPORT)) {
                    LazyColumn(
                        state = list,
                        modifier =
                            Modifier
                                .size(200.dp, VIEWPORT)
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    // Reading the focus here also lays the list out again whenever it moves.
                                    // On a device a frame composes before it lays out. The test's idle
                                    // checks may lay out between the write and the composition: those
                                    // passes are not frames, so only passes after the composition count.
                                    val focused = composedFocus
                                    if (focused == Snapshot.withoutReadObservation { focus }) Snapshot.withoutReadObservation {
                                        val info = list.layoutInfo
                                        val item = info.visibleItemsInfo.firstOrNull { it.index == focused }
                                        val end = info.viewportEndOffset - info.afterContentPadding
                                        passes += Pass(focused, item?.offset, item?.let { it.offset + it.size }, end, glide.offset)
                                    }
                                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                                }.then(glide.modifier),
                        contentPadding = PaddingValues(vertical = glide.extra),
                    ) {
                        items(ROWS) { Box(Modifier.height(heightOf(it))) }
                    }
                    KeepFocusedVisible(list, glide) { focus }
                    val shown = focus
                    SideEffect {
                        composedFocus = shown
                        // Right after the composition, before any coroutine or layout: the scroll is
                        // already decided (a coroutine scroll would not have happened yet).
                        scrolls += shown to list.firstVisibleItemIndex * rowPx + list.firstVisibleItemScrollOffset
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * Moves the focus inside a frame, as a held key's frame-aligned repeat does: that frame then
     * composes the move, and only what happens while composing it can land in its layout.
     */
    private fun stepTo(row: Int) {
        compose.runOnIdle { scope.launch { withFrameNanos { focus = row } } }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun assertEveryCompositionScrolledToTheFocus() {
        assertTrue("composed after each move: $scrolls", scrolls.map { it.first }.containsAll((0..12).toList()))
        scrolls.forEach { (focused, scrolled) ->
            val top = focused * rowPx - scrolled
            assertTrue("focus $focused at $top..${top + rowPx} in 0..$windowPx when composed", top >= 0 && top + rowPx <= windowPx)
        }
    }

    private fun assertEveryPassShowsTheFocusWhole() {
        val laidOut = passes.toList()
        assertTrue("the list was laid out after each move: ${laidOut.map { it.focus }}", laidOut.map { it.focus }.containsAll((0..12).toList()))
        laidOut.forEach { pass ->
            assertTrue("focus ${pass.focus} laid out at ${pass.top}..${pass.bottom} in 0..${pass.windowEnd}", pass.top != null && pass.top >= 0 && pass.bottom!! <= pass.windowEnd)
        }
    }

    @Test
    fun withAnimationsOffTheScrollLandsInTheFocusFrame() {
        showList(motion = false)
        compose.mainClock.autoAdvance = false
        (1..12).forEach(::stepTo)
        (11 downTo 0).forEach(::stepTo)
        assertEveryCompositionScrolledToTheFocus()
        assertEveryPassShowsTheFocusWhole()
    }

    @Test
    fun withAnimationsOnTheJumpAndItsGlideStartInTheFocusFrame() {
        showList(motion = true)
        compose.mainClock.autoAdvance = false
        (1..12).forEach(::stepTo)
        assertEveryCompositionScrolledToTheFocus()
        assertEveryPassShowsTheFocusWhole()
        // The first move past the bottom edge: the rows jumped up and are drawn where they were.
        val firstJump = passes.first { it.glide != 0f }
        assertTrue("drawn lower by what the rows jumped (at most a row): ${firstJump.glide}", firstJump.glide > 0f && firstJump.glide <= rowPx)
        // A jump that comes as the last glide ends (same frame) still glides, and then all rests.
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("at rest after the glide", 0f, glide!!.offset)
    }

    @Test
    fun rowsOfDifferentHeightsEndWholeAfterEachStepWithAnimationsOff() = stepThroughMixedHeights(motion = false)

    @Test
    fun rowsOfDifferentHeightsEndWholeAfterEachStepWithAnimationsOn() = stepThroughMixedHeights(motion = true)

    /** A panel list: rows, a taller group heading every fifth item, a row with a note now and then. */
    private fun stepThroughMixedHeights(motion: Boolean) {
        showList(motion) { i -> if (i % 5 == 4) 90.dp else if (i % 7 == 3) 70.dp else ROW }
        compose.mainClock.autoAdvance = false
        for (row in 1 until ROWS) {
            stepTo(row)
            // An estimated place is checked against the next layout: one more frame at most.
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val info = list.layoutInfo
            val item = info.visibleItemsInfo.first { it.index == row }
            val end = info.viewportEndOffset - info.afterContentPadding
            assertTrue("row $row at ${item.offset}..${item.offset + item.size} in 0..$end", item.offset >= 0 && item.offset + item.size <= end)
        }
    }

    private companion object {
        val ROW = 42.dp
        val VIEWPORT = 200.dp
        const val ROWS = 40
    }
}
