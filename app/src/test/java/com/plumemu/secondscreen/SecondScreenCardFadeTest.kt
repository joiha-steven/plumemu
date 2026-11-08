package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The second screen moves like the top one (design/rules.md › Second screen, Motion), frame by frame: the window's first
 * card is whole at once; a late fact of the same game fills it in place; another game comes in from below when the cursor
 * went down (its ink starts lower and only rises to rest) while it fades in; with Animations off every card is whole at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCardFadeTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var content by mutableStateOf<SecondScreenContent>(GAME_NEVER_PLAYED)

    @Before
    fun forget() = SecondScreenMotion.reset()

    private fun show(animationsOn: Boolean) {
        RuntimeEnvironment.setQualifiers("w${SecondSize.Roomy.widthDp}dp-h${SecondSize.Roomy.heightDp}dp-land-xhdpi")
        compose.mainClock.autoAdvance = false
        compose.setContent { PlumTheme(PlumThemeSettings.Default.copy(animationsOn = animationsOn)) { SecondScreenCard(content) } }
        compose.mainClock.advanceTimeBy(1L)
    }

    private fun frame(): PixelMap = compose.onNodeWithTag(SecondScreenTags.CARD).captureToImage().toPixelMap()

    private fun PixelMap.brightest(): Float {
        var best = 0f
        for (y in 0 until height) for (x in 0 until width) best = maxOf(best, this[x, y].red)
        return best
    }

    /**
     * The top row of the card's ink, -1 with none: the first row with a pixel brighter than halfway between the card's
     * face and its brightest pixel, so a fading card is placed the same as a whole one.
     */
    private fun PixelMap.inkTop(): Int {
        val face = this[width / 2, height - 2].red
        val bright = brightest()
        if (bright - face < 0.1f) return -1
        val half = face + (bright - face) / 2
        return (0 until height).firstOrNull { y -> (0 until width).any { x -> this[x, y].red > half } } ?: -1
    }

    /** The cursor offered [card] at row [row] of the games of system 3. */
    private fun offered(
        card: SecondScreenContent,
        row: Int,
    ) = SecondScreenMotion.noteOffered(card, StripFocus(Column.Games, 3, "snes", mapOf("snes" to row)), 12)

    @Test
    fun theFirstCardIsWholeAtOnceAndALateFactFillsItInPlace() {
        show(animationsOn = true)
        val atOnce = frame().brightest()
        assertTrue("drawn at once: $atOnce", atOnce > 0.5f)
        compose.runOnIdle { content = (content as SecondScreenContent.Game).copy(playTimeMinutes = 5) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the same game's late fact: no swap", atOnce, frame().brightest(), 0.02f)
    }

    @Test
    fun anotherGameBelowComesUpFromBelowAndSettles() {
        offered(GAME_NEVER_PLAYED, 4)
        offered(GAME_WITH_STAR, 5)
        show(animationsOn = true)
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS.toLong())
        compose.runOnIdle { content = GAME_WITH_STAR }
        val rows = mutableListOf<Int>()
        repeat(PlumMotion.GLIDE_MS / 16 + 2) {
            compose.mainClock.advanceTimeByFrame()
            rows += frame().inkTop()
        }
        compose.mainClock.advanceTimeBy(PlumMotion.GLIDE_MS.toLong())
        val rest = frame().inkTop()
        // The first frame still draws the card that was there; frames with nothing drawn yet are left out.
        val seen = rows.drop(1).filter { it >= 0 }
        assertTrue("starts below its place: $rest $rows", seen.first() > rest + 2)
        // 16 dp at 2 px per dp: never further than that.
        assertTrue("never more than the shift: $rest $rows", seen.all { it - rest <= 32 })
        assertTrue("only rises: $rows", seen.zipWithNext().all { (a, b) -> b <= a })
        assertEquals("ends at rest", rest, seen.last())
    }

    @Test
    fun withAnimationsOffEveryCardIsWholeAtOnce() {
        offered(GAME_NEVER_PLAYED, 4)
        offered(GAME_WITH_STAR, 5)
        show(animationsOn = false)
        val atOnce = frame().brightest()
        compose.runOnIdle { content = GAME_WITH_STAR }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("a new card on its first frame", atOnce, frame().brightest(), 0.02f)
        val first = frame().inkTop()
        compose.mainClock.advanceTimeBy(PlumMotion.GLIDE_MS + 50L)
        assertEquals("and it does not move", first, frame().inkTop())
    }

    @Test
    fun theFirstCardAfterTheCalmPageIsWholeOnItsFirstFrame() {
        content = SecondScreenContent.Calm
        show(animationsOn = true)
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS.toLong())
        compose.runOnIdle { content = GAME_WITH_STAR }
        // The first frame still draws the page that was there; the card's first frame is the next.
        compose.mainClock.advanceTimeByFrame()
        frame()
        compose.mainClock.advanceTimeByFrame()
        val first = frame().brightest()
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS.toLong())
        assertTrue("drawn: $first", first > 0.2f)
        assertEquals("whole on its first frame (a wake does not move)", frame().brightest(), first, 0.02f)
    }

    @Test
    fun statisticsAfterTheCalmPageStillSettlesIn() {
        content = SecondScreenContent.Calm
        show(animationsOn = true)
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS.toLong())
        compose.runOnIdle { content = SecondScreenContent.Statistics(SecondScreenStatisticsSamples.withCovers) }
        val seen = mutableListOf<Float>()
        repeat(PlumMotion.SCREEN_MS / 16 + 2) {
            compose.mainClock.advanceTimeByFrame()
            seen += frame().brightest()
        }
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS.toLong())
        val whole = frame().brightest()
        val moving = seen.drop(1)
        assertTrue("starts faint: $whole $seen", moving.first() < whole - 0.2f)
        assertTrue("only brightens: $seen", moving.zipWithNext().all { (a, b) -> b >= a - 0.01f })
        assertEquals("ends whole", whole, moving.last(), 0.02f)
    }
}
