package com.plumemu.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Info's facts at Largest in Serif on 667 x 375 (they scroll), pressed frame by frame on the test clock
 * with motion on: a held Down's presses, each a few frames into the last one's 180 ms slide, stop at
 * exactly the places single presses with motion off stop at; and a Down during a finger's fling is
 * not dropped: the fling gives way and the facts go where the press aimed from where the fling was.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class InfoFactsPressFramesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val facts =
        sampleFacts(title = "Super Metroid").copy(
            systemName = "Super Nintendo",
            regions = "USA,EUR",
            fileName = "Super Metroid.sfc",
            folderName = "ROMs/snes",
            sizeBytes = 3L * 1024 * 1024,
            emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true),
            name = NameFacts(NameOrigin.GameCode, NameCertainty.Sure, "MAME software lists", hasFix = false),
        )
    private val screen = PanelScreen.Info(facts)
    private val panel = OpenPanel(GameRef("7", facts.title), screen)
    private var isMotionOn by mutableStateOf(false)

    private fun show() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest, font = PlumFont.Serif, animationsOn = isMotionOn)) {
                HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel)
            }
        }
        compose.waitForIdle()
    }

    private fun scrollNode() = compose.onNodeWithTag(GamePanelTags.FACTS_SCROLL, useUnmergedTree = true).fetchSemanticsNode()

    private fun value(): Int = scrollNode().config[SemanticsProperties.VerticalScrollAxisRange].value().roundToInt()

    private fun press(isDown: Boolean): Boolean = compose.runOnUiThread<Boolean> { screen.scroll.press(isDown) }

    /** Where single presses stop, motion off, from the top down to the end. */
    private fun stopsOnePressAtATime(): List<Int> {
        val stops = mutableListOf<Int>()
        while (compose.runOnIdle { screen.scroll.press(true) }) stops += compose.runOnIdle { value() }
        while (compose.runOnIdle { screen.scroll.press(false) }) Unit
        assertEquals("back at the top", 0, compose.runOnIdle { value() })
        return stops
    }

    private fun frames(count: Int) = compose.mainClock.advanceTimeBy(count * FRAME_MS)

    @Test
    fun aHeldDownStopsWhereSinglePressesDo() {
        show()
        val stops = stopsOnePressAtATime()
        assertTrue("the facts scroll here: $stops", stops.size >= 2)
        isMotionOn = true
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        // Two presses three frames apart (the repeat's pace), then the slide ends: the second stop, exactly.
        var pressed = 0
        while (pressed < stops.size) {
            assertTrue(press(true))
            pressed++
            frames(3)
            if (pressed < stops.size) {
                assertTrue(press(true))
                pressed++
            }
            frames(SETTLE_FRAMES)
            assertEquals("after $pressed presses held", stops[pressed - 1], value())
        }
        assertEquals("at the end the key does nothing", false, press(true))
    }

    @Test
    fun aDownDuringAFlingIsNotDroppedWithMotionOn() = downDuringAFling(motion = true)

    @Test
    fun aDownDuringAFlingIsNotDroppedWithMotionOff() = downDuringAFling(motion = false)

    private fun downDuringAFling(motion: Boolean) {
        isMotionOn = motion
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(GamePanelTags.FACTS_SCROLL, useUnmergedTree = true).performTouchInput { swipeUp(startY = bottom - 1f, endY = centerY, durationMillis = 40) }
        frames(2)
        val from = value()
        val node = scrollNode()
        val viewport = node.size.height
        val max = node.config[SemanticsProperties.VerticalScrollAxisRange].maxValue().roundToInt()
        assertTrue("the fling is still going: $from of $max", from in 1 until max)
        frames(1)
        val during = value()
        assertTrue("still flinging: $from then $during", during > from)
        val aimed = nextFactsScroll(during, viewport, max, rowsInContent(during), isDown = true)
        assertTrue(press(true))
        frames(SETTLE_FRAMES)
        val end = value()
        assertTrue("the press aimed at $aimed from $during, the facts ended at $end", abs(end - aimed) <= 1)
    }

    /** Info's rows as the scroll's content has them (px), from what is on screen at scroll [value]. */
    private fun rowsInContent(value: Int): List<IntRange> {
        val top = scrollNode().positionInRoot.y
        val root = compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNode()
        val tags = (0 until 8).map(GamePanelTags::fact) + (0 until 3).map(GamePanelTags::nameFromLine)
        return tags.mapNotNull { tag -> findTag(root, tag) }.map { row ->
            val at = (row.positionInRoot.y - top).roundToInt() + value
            at until at + row.size.height
        }
    }

    private fun findTag(
        node: androidx.compose.ui.semantics.SemanticsNode,
        tag: String,
    ): androidx.compose.ui.semantics.SemanticsNode? {
        if (SemanticsProperties.TestTag in node.config && node.config[SemanticsProperties.TestTag] == tag) return node
        return node.children.firstNotNullOfOrNull { findTag(it, tag) }
    }

    private companion object {
        const val FRAME_MS = 16L

        /** Past the 180 ms slide. */
        const val SETTLE_FRAMES = 30
    }
}
