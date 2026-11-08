package com.plumemu.home

import androidx.compose.runtime.rememberCoroutineScope
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumMotion
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The focus's motion (docs/design/rules.md > Focus and current; `FocusLift`): the focused item's lift grows over [PlumMotion.FOCUS_GROW_MS], the
 * one the focus left shrinks over [PlumMotion.FOCUS_LEAVE_MS] (the two rims cross-fade, none travels), with animations off it switches in
 * one frame, and at rest nothing runs. Driven by the compose test clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class FocusOutlineTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun outline(
        opening: Int,
        motionOn: Boolean = true,
    ): FocusOutline {
        lateinit var scope: CoroutineScope
        compose.mainClock.autoAdvance = false
        compose.setContent { scope = rememberCoroutineScope() }
        compose.mainClock.advanceTimeByFrame()
        return FocusOutline(opening, scope, { motionOn }, { 1f })
    }

    @Test
    fun anOpeningFocusIsWholeInTheFirstFrameAndNothingRuns() {
        val outline = outline(opening = 0)
        assertEquals(1f, outline.liftOf(0), 0f)
        assertEquals(0f, outline.liftOf(1), 0f)
        assertFalse(outline.isAnimating)
    }

    @Test
    fun theNewItemGrowsAndTheOldShrinksTogetherOverTheTokens() {
        val outline = outline(opening = 0)
        outline.moveTo(1)
        // The frame of the move: the new item has not started to grow, the old has not started to shrink.
        assertEquals(0f, outline.liftOf(1), 0.02f)
        assertEquals(1f, outline.liftOf(0), 0.02f)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS / 2L)
        val grown = outline.liftOf(1)
        val left = outline.liftOf(0)
        assertTrue("growing: $grown", grown > 0.05f && grown < 1f)
        assertTrue("shrinking: $left", left > 0f && left < 0.95f)
        // Decelerate: more than half of the way at half of the time.
        assertTrue("decelerates: $grown", grown > 0.5f)
        assertEquals("cross-fade: the two add up to the whole", 1f, grown + left, 0.02f)
        assertTrue(outline.isAnimating)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS / 2L + 40L)
        assertEquals(1f, outline.liftOf(1), 0f)
        assertEquals("the old one is let go", 0f, outline.liftOf(0), 0f)
        assertFalse("done at the tokens", outline.isAnimating)
        assertEquals(listOf(1), outline.liftedItems())
    }

    @Test
    fun withAnimationsOffTheSwitchIsOneFrame() {
        val outline = outline(opening = 0, motionOn = false)
        outline.moveTo(1)
        assertEquals(1f, outline.liftOf(1), 0f)
        assertEquals(0f, outline.liftOf(0), 0f)
        assertFalse(outline.isAnimating)
        assertEquals(listOf(1), outline.liftedItems())
    }

    @Test
    fun aHeldKeyLeavesATrailOfItemsSettlingNoneCutShort() {
        val outline = outline(opening = 0)
        outline.moveTo(1)
        compose.mainClock.advanceTimeBy(40L)
        outline.moveTo(2)
        compose.mainClock.advanceTimeBy(40L)
        val items = outline.liftedItems()
        assertEquals("the focused one first, then those still settling", listOf(2, 0, 1), items.sortedBy { if (it == 2) -1 else it })
        assertTrue("item 1 is part of the way back: ${outline.liftOf(1)}", outline.liftOf(1) in 0.01f..0.99f)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 100L)
        assertEquals(listOf(2), outline.liftedItems())
        assertFalse(outline.isAnimating)
    }

    @Test
    fun anItemFocusedAgainWhileLeavingGrowsFromWhereItIs() {
        val outline = outline(opening = 0)
        outline.moveTo(1)
        compose.mainClock.advanceTimeBy(60L)
        val partway = outline.liftOf(0)
        outline.moveTo(0)
        assertEquals("no jump", partway, outline.liftOf(0), 0.02f)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 100L)
        assertEquals(1f, outline.liftOf(0), 0f)
    }

    @Test
    fun whenFocusLeavesTheGridTheLiftFadesAndThenNothingIsLeft() {
        val outline = outline(opening = 0)
        outline.moveTo(NO_COVER)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_LEAVE_MS / 2L)
        assertTrue(outline.liftOf(0) in 0.01f..0.99f)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_LEAVE_MS.toLong())
        assertTrue(outline.liftedItems().isEmpty())
        assertFalse(outline.isAnimating)
    }

    @Test
    fun aGridWithNoFocusHasNoLiftAndAFocusAppearsInPlace() {
        val outline = outline(opening = NO_COVER)
        assertTrue(outline.liftedItems().isEmpty())
        outline.moveTo(3)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS / 2L)
        assertTrue(outline.liftOf(3) in 0.01f..0.99f)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS.toLong())
        assertEquals(1f, outline.liftOf(3), 0f)
    }
}
