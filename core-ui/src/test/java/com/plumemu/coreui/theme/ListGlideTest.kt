package com.plumemu.coreui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The glide's arithmetic, and the glide and the fade on a real composition's frames. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ListGlideTest {
    @get:Rule
    val compose = createComposeRule()

    private val glideNanos = PlumMotion.GLIDE_MS * 1_000_000L

    @Test
    fun aJumpFromRestGlidesTheWholeJumpOver200msOnTheDecelerateCurve() {
        val plan = GlideMath.plan(current = 0f, movedPx = -42f, sinceLastJumpNanos = Long.MAX_VALUE, limitPx = 42f)
        assertEquals("drawn where the rows were: one row lower", 42f, plan.from)
        assertEquals(200_000_000L, plan.durationNanos)
        assertEquals(glideNanos, plan.durationNanos)
        assertEquals(PlumMotion.Decelerate, plan.easing)
    }

    @Test
    fun theDecelerateCurveStartsFastAndLandsSoftly() {
        assertEquals(0f, PlumMotion.Decelerate.transform(0f), 1e-4f)
        assertEquals(1f, PlumMotion.Decelerate.transform(1f), 1e-4f)
        assertTrue("more than half the way after a quarter of the time", PlumMotion.Decelerate.transform(0.25f) > 0.5f)
        // Landing: the last 10% of the time covers under 1% of the distance.
        assertTrue("soft landing", PlumMotion.Decelerate.transform(0.9f) > 0.99f)
        assertEquals("the token is a different curve from the old ease-out", false, PlumMotion.Decelerate == PlumMotion.EaseOut)
    }

    @Test
    fun aRepeatStartsFromWhereTheRowsAreDrawnNowNeverFromZero() {
        // A held key: the last jump 80 ms ago has 10 px left to glide; the next row jumps 42 px.
        val plan = GlideMath.plan(current = 10f, movedPx = -42f, sinceLastJumpNanos = 80_000_000L, limitPx = 84f)
        assertEquals("what is drawn now plus the new row: continuous", 52f, plan.from)
        // One 42 px row per 80 ms, with 10 px owed on top (a quarter row): the repeats' speed, 52/42 gaps, so the lag stays.
        assertEquals("the repeats' speed over what is owed", 80_000_000.0 * 52 / 42, plan.durationNanos.toDouble(), 1_000.0)
        // 1.5 rows owed on top of the row (63 px, 2.5 gaps in all): what is owed past 1.25 gaps counts at half weight.
        val behind = GlideMath.plan(current = 63f, movedPx = -42f, sinceLastJumpNanos = 80_000_000L, limitPx = 105f)
        assertEquals("1.25 + (2.5 - 1.25) / 2 gaps", 150_000_000.0, behind.durationNanos.toDouble(), 1_000.0)
        assertEquals("constant speed", LinearEasing, plan.easing)
        val steady = GlideMath.plan(current = 0f, movedPx = -42f, sinceLastJumpNanos = 80_000_000L, limitPx = 84f)
        assertEquals("with nothing owed it is exactly the gap: it lands as the next repeat comes", 80_000_000L, steady.durationNanos)
        // Moving back up while gliding down: the jumps cancel out.
        assertEquals(0f, GlideMath.plan(current = 42f, movedPx = 42f, sinceLastJumpNanos = 50_000_000L, limitPx = 42f).from)
    }

    @Test
    fun aRepeatsGlideIsNeverShorterThan32msNorLongerThan200ms() {
        val fast = GlideMath.plan(current = 10f, movedPx = -42f, sinceLastJumpNanos = 16_000_000L, limitPx = 84f)
        val atRest = GlideMath.plan(current = 0f, movedPx = -42f, sinceLastJumpNanos = 16_000_000L, limitPx = 84f)
        assertEquals("a gap under 32 ms is taken as 32 ms", PlumMotion.GLIDE_HELD_MIN_MS * 1_000_000L, atRest.durationNanos)
        assertTrue("the same for what is owed on top", fast.durationNanos >= PlumMotion.GLIDE_HELD_MIN_MS * 1_000_000L)
        val slow = GlideMath.plan(current = 0f, movedPx = -42f, sinceLastJumpNanos = 190_000_000L, limitPx = 84f)
        assertEquals("the gap itself", 190_000_000L, slow.durationNanos)
        val owedLong = GlideMath.plan(current = 40f, movedPx = -42f, sinceLastJumpNanos = 190_000_000L, limitPx = 84f)
        assertEquals("never longer than 200 ms", glideNanos, owedLong.durationNanos)
        val later = GlideMath.plan(current = 0f, movedPx = -42f, sinceLastJumpNanos = 450_000_000L, limitPx = 84f)
        assertEquals("a press after the last glide ended is a fresh 200 ms glide", glideNanos, later.durationNanos)
    }

    @Test
    fun theGlideNeverDrawsRowsFurtherThanTheExtraRowCovers() {
        assertEquals(42f, GlideMath.plan(current = 30f, movedPx = -42f, sinceLastJumpNanos = 33_000_000L, limitPx = 42f).from)
        assertEquals(-42f, GlideMath.plan(current = -30f, movedPx = 420f, sinceLastJumpNanos = Long.MAX_VALUE, limitPx = 42f).from)
    }

    @Test
    fun aSettleEndsExactlyAtZero() {
        assertEquals(42f, SettleMath.valueAt(42f, 0L, glideNanos, PlumMotion.EaseOut))
        val half = SettleMath.valueAt(42f, glideNanos / 2, glideNanos, PlumMotion.EaseOut)
        assertTrue("ease-out: more than half the way after half the time ($half)", half < 21f && half > 0f)
        assertEquals(0f, SettleMath.valueAt(42f, glideNanos, glideNanos, PlumMotion.EaseOut))
        assertEquals(21f, SettleMath.valueAt(42f, 50L, 100L, LinearEasing))
    }

    @Test
    fun theFramePeriodIsTheShortestGapAndIgnoresDroppedFrames() {
        assertEquals(8_333_333L, SettleMath.framePeriod(SettleMath.DEFAULT_FRAME_NANOS, 8_333_333L))
        assertEquals("a dropped frame is a longer gap", 8_333_333L, SettleMath.framePeriod(8_333_333L, 16_666_667L))
        assertEquals("noise", 8_333_333L, SettleMath.framePeriod(8_333_333L, 1_000_000L))
    }

    @Test
    fun withMotionOnAJumpShowsAtOnceThenGlidesToRestAndStops() {
        val glide = showList(motion = true)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { glide.jumped(-rowPx) }
        assertEquals("the frame of the jump still shows the rows where they were", rowPx, glide.offset)
        compose.mainClock.advanceTimeBy(PlumMotion.GLIDE_MS / 2L)
        assertTrue("gliding: ${glide.offset}", glide.offset > 0f && glide.offset < rowPx)
        compose.mainClock.advanceTimeBy(PlumMotion.GLIDE_MS.toLong())
        assertEquals(0f, glide.offset)
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertFalse("at rest nothing runs", glide.isGliding) }
    }

    @Test
    fun aJumpGlidesNoFurtherThanTheRoomItIsGiven() {
        val glide = showList(motion = true)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { glide.jumped(-rowPx, roomBelowPx = rowPx / 4) }
        assertEquals("drawn at most the room from its place", rowPx / 4, glide.offset)
        compose.runOnIdle { glide.jumped(-rowPx, roomBelowPx = 0f) }
        assertEquals("no room: drawn at its place at once, the running glide dropped", 0f, glide.offset)
        compose.runOnIdle { glide.jumped(-rowPx) }
        compose.runOnIdle { glide.jumped(0f, roomBelowPx = rowPx / 2) }
        assertEquals("a focus move without a scroll brings the running glide within its room", rowPx / 2, glide.offset)
        compose.runOnIdle { glide.jumped(0f, roomAbovePx = 0f) }
        assertEquals("room above does not limit rows drawn below their place", rowPx / 2, glide.offset)
    }

    // Cadences of a hold in 16 ms test frames (HoldRepeat: first repeat after 300 ms, then 80 ms
    // constant, or 120 ms getting 20% quicker down to 32 ms, each rounded to whole frames).
    @Test
    fun aHoldAtTheConstantRepeatRateIsContinuousEvenAndNeverCut() = hold("constant 80 ms", List(14) { 5 })

    @Test
    fun aHoldWithAcceleratingRepeatsIsContinuousEvenAndNeverCut() = hold("accelerating 120 to 32 ms", listOf(7, 6, 5, 4, 3, 2, 2, 2, 2, 2, 2, 2))

    @Test
    fun theFastestRepeatIsAlsoContinuousAndWithinARow() = hold("fastest 32 ms", List(14) { 2 })

    /**
     * Press, then the first repeat after 300 ms, then repeats [gapFrames] apart. Every step (the
     * first repeat and the change from its 200 ms glide to a held step included) is drawn
     * continuously: the rows never jump forward (a cut) by more than 1% of a row, never back, and a
     * held step moves at an even speed.
     */
    private fun hold(
        name: String,
        gapFrames: List<Int>,
    ) {
        val glide = showList(motion = true)
        compose.mainClock.autoAdvance = false
        val row = rowPx
        val firstRepeatFrames = 19 // 304 ms
        var worstCut = 0f
        var worstStep = -1
        var resets = 0
        val frameSteps = mutableListOf<List<Float>>()
        val gaps = listOf(firstRepeatFrames) + gapFrames
        var before = 0f
        for (step in 0..gaps.size) {
            // The frames of the wait before this jump were advanced by the previous step; this is the last one.
            if (step > 0) compose.mainClock.advanceTimeByFrame()
            before = glide.offset
            compose.runOnIdle { glide.jumped(-row) }
            val after = glide.offset
            if (step > 0) {
                val cut = before + row - after
                if (cut > worstCut) {
                    worstCut = cut
                    worstStep = step
                }
                if (after < before) resets++
            }
            assertTrue("never trails by more than the room ($after)", after <= row * 2f + 0.5f)
            val nextWait = gaps.getOrNull(step) ?: 6
            val samples = mutableListOf(after)
            repeat(nextWait - 1) {
                compose.mainClock.advanceTimeByFrame()
                samples += glide.offset
                assertTrue("never below the layout while held", glide.offset >= 0f)
            }
            frameSteps += samples
        }
        println("HOLD $name: worst cut ${worstCut / row * 100}% of a row at step $worstStep")
        assertEquals("no repeat resets the drawn offset ($name)", 0, resets)
        assertTrue(
            "$name: a step cuts ${worstCut / row * 100}% of a row at step $worstStep (limit 1%)",
            worstCut <= row * 0.01f,
        )
        // Even speed inside every held step (a step after the press's own 200 ms glide, step 2 on).
        for (step in 2 until frameSteps.size - 1) {
            val moves = frameSteps[step].zipWithNext { x, y -> x - y }
            if (moves.size < 2) continue
            assertTrue("$name: step $step moves forward every frame: $moves", moves.all { it > 0f })
            val ratio = moves.max() / moves.min()
            assertTrue("$name: speed inside step $step swings ${ratio}x: $moves (limit 1.3x)", ratio <= 1.3f)
        }
        compose.mainClock.advanceTimeBy(PlumMotion.GLIDE_MS * 2L)
        assertEquals(0f, glide.offset)
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertFalse("at rest nothing runs", glide.isGliding) }
    }

    @Test
    fun aJumpThatComesMidGlideDoesNotStopTheRowsMoving() {
        val glide = showList(motion = true)
        compose.mainClock.autoAdvance = false
        val row = rowPx
        compose.runOnIdle { glide.jumped(-row) }
        compose.mainClock.advanceTimeBy(64)
        val mid = glide.offset
        assertTrue("part of the way ($mid)", mid > 0f && mid < row)
        compose.runOnIdle { glide.jumped(-row) }
        assertEquals("continues from the drawn offset", minOf(mid + row, row * 2f), glide.offset, 0.5f)
        val afterJump = glide.offset
        compose.mainClock.advanceTimeByFrame()
        assertTrue("and keeps moving the next frame", glide.offset < afterJump)
    }

    @Test
    fun withAnimationsOffTheListJumpsAndComposesNoExtraRow() {
        val glide = showList(motion = false)
        compose.runOnIdle {
            assertEquals(0.dp, glide.extra)
            glide.jumped(-rowPx)
            assertEquals("a plain jump", 0f, glide.offset)
            assertFalse(glide.isGliding)
        }
    }

    @Test
    fun theExtraRowsAreComposedOutsideTheVisibleListWithoutMovingIt() {
        lateinit var list: LazyListState
        compose.setContent {
            PlumTheme {
                list = rememberLazyListState(initialFirstVisibleItemIndex = 5)
                val glide = rememberListGlide(stride = ROW)
                Box(Modifier.size(200.dp, VIEWPORT)) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.size(200.dp, VIEWPORT).testTag("list").then(glide.modifier),
                        contentPadding = PaddingValues(vertical = glide.extra),
                    ) {
                        items(40) { i -> Box(Modifier.height(ROW).testTag("row$i")) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("list").getBoundsInRoot()
        assertEquals("the list keeps its size", VIEWPORT, bounds.bottom - bounds.top)
        val visible = list.layoutInfo.visibleItemsInfo.map { it.index }
        assertTrue("a row above the window is composed: $visible", 4 in visible)
        assertTrue("and one below: $visible", 10 in visible)
        assertEquals("the scroll position is unchanged", 5, list.firstVisibleItemIndex)
        val first = compose.onNodeWithTag("row5").getBoundsInRoot()
        assertEquals("row 5 starts at the top of the visible list", bounds.top, first.top)
    }

    @Test
    fun aFadeStartsWhenTheKeyChangesAndNotOnTheFirstContent() {
        var key by mutableStateOf("snes")
        lateinit var fade: ContentFade
        val composedAlpha = mutableListOf<Pair<String, Float>>()
        compose.setContent {
            PlumTheme {
                fade = rememberContentFade(key)
                // What the frame that composes the new content draws it with.
                composedAlpha += key to fade.alpha
            }
        }
        compose.waitForIdle()
        assertEquals("the first content shows at once", "snes" to 1f, composedAlpha.last())
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { key = "nes" }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the new content starts transparent in its first frame", "nes" to 0f, composedAlpha.first { it.first == "nes" })
        compose.mainClock.advanceTimeBy(PlumMotion.FADE_IN_MS / 2L)
        assertTrue("half way: ${fade.alpha}", fade.alpha > 0f && fade.alpha < 1f)
        compose.mainClock.advanceTimeBy(PlumMotion.FADE_IN_MS.toLong())
        assertEquals(1f, fade.alpha)
    }

    @Test
    fun withAnimationsOffNothingFades() {
        var key by mutableStateOf("snes")
        lateinit var fade: ContentFade
        compose.setContent { PlumTheme(PlumThemeSettings(animationsOn = false)) { fade = rememberContentFade(key) } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { key = "nes" }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1f, fade.alpha)
    }

    private fun showList(motion: Boolean): ListGlide {
        lateinit var glide: ListGlide
        compose.setContent {
            PlumTheme(PlumThemeSettings(animationsOn = motion)) {
                // Frame time, not the machine's clock, tells a held key's repeats apart.
                CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                    glide = rememberListGlide(stride = ROW)
                    LazyColumn(
                        modifier = Modifier.size(200.dp, VIEWPORT).then(glide.modifier),
                        contentPadding = PaddingValues(vertical = glide.extra),
                    ) {
                        items(40) { Box(Modifier.height(ROW)) }
                    }
                }
            }
        }
        compose.waitForIdle()
        return glide
    }

    private val rowPx: Float get() = with(compose.density) { ROW.toPx() }

    private companion object {
        val ROW = 42.dp
        val VIEWPORT = 200.dp
    }
}
