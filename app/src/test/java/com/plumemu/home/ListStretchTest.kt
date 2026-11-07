package com.plumemu.home

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * With the systems focused and no room for the details column, the games list takes the width to the right margin: the region or year stands on that margin as in the full list, and no
 * piece of a third column shows. Measured on the pixels, so a label left short of the margin or a list run past it fails alike.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ListStretchBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun lastInk(settings: PlumThemeSettings): Pair<Float, Float> {
        val content = FakeHomeContent.board()
        compose.setContent { PlumTheme(settings) { HomeAt(content, listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX))) } }
        compose.waitForIdle()
        val d = compose.density.density
        val first = compose.onNodeWithTag(HomeTags.game(0), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val last = compose.onNodeWithTag(HomeTags.game(5), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        var ink = 0
        for (y in (first.top.value * d).toInt() until (last.bottom.value * d).toInt()) {
            val page = image.getPixel(0, y)
            for (x in image.width - 1 downTo ink + 1) {
                if (differs(image.getPixel(x, y), page)) {
                    ink = x
                    break
                }
            }
        }
        return ink + 1f to image.width - PlumDimens.stripMarginSide.value * d
    }

    private fun differs(
        a: Int,
        b: Int,
    ): Boolean = (0..2).any { abs((a shr (it * 8) and 0xFF) - (b shr (it * 8) and 0xFF)) > INK }

    protected fun theRowsReachTheRightMargin(
        settings: PlumThemeSettings,
        size: TextSize,
    ) {
        val (ink, margin) = lastInk(settings.copy(textSize = size))
        assertTrue("last ink of the rows at $ink px, the right margin at $margin px", abs(ink - margin) <= SLACK_DP * compose.density.density)
    }

    /** The slide to the games: the right-hand text moves from the margin to the full list's end with the strip, frame by frame, with no jump as the rows change width. */
    @Test
    fun theSlideCarriesTheRightHandText() {
        lateinit var strip: StripState
        val content = FakeHomeContent.board()
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { HomeAt(content, listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)), onState = { strip = it }, isMotionOn = true) } }
        compose.waitForIdle()
        val d = compose.density.density
        // Well below the first row, which takes the slab and its glow.
        val row = compose.onNodeWithTag(HomeTags.game(4), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val pastTheTitle = ((row.left.value + TITLE_DP) * d).toInt()
        // Where the right-hand text ends on the strip: the end of the first ink past the title, less the strip's slide. The details stand further right, past a gap.
        fun textEnd(): Float {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val page = image.getPixel(0, (row.top.value * d).toInt())
            val start = (pastTheTitle + strip.translationX).toInt().coerceAtLeast(0)
            val inked = (start until image.width).map { x -> ((row.top.value * d).toInt() until (row.bottom.value * d).toInt()).any { y -> differs(image.getPixel(x, y), page) } }
            val first = inked.indexOf(true)
            val gap = inked.drop(first).indexOfFirst { !it }.let { if (it < 0) inked.size - first else it }
            var end = first + gap
            while (end < inked.size && inked.subList(end, minOf(end + (GAP_DP * d).toInt(), inked.size)).any { it }) end++
            return start + end - strip.translationX
        }
        val focused = compose.onNodeWithTag(HomeTags.game(0), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val slabGaps = mutableListOf<Float>()
        // On the focused row, the slab's fill from the end of its right-hand text to the slab's rim: the same at every frame, so the text stays inside and the slab's end moves with it.
        fun slabPastText(): Float {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val top = ((focused.top.value + TEXT_INSET_DP) * d).toInt()
            val bottom = ((focused.bottom.value - TEXT_INSET_DP) * d).toInt()
            val start = (pastTheTitle + strip.translationX).toInt().coerceAtLeast(0)
            val inked = (start until image.width).map { x -> (top until bottom).any { y -> differs(image.getPixel(x, y), image.getPixel(start, y)) } }
            val first = inked.indexOf(true)
            var end = first
            for (x in first until inked.size) {
                if (x - end > (GAP_DP * d).toInt()) break
                if (inked[x]) end = x
            }
            // Along the middle of the row, from just past the text to the slab's rim; a text outside the slab has no rim there.
            val y = (top + bottom) / 2
            val past = start + end + (2 * d).toInt()
            val level = image.getPixel(past, y)
            var rim = past
            while (rim < image.width - 1 && (0..2).all { abs((image.getPixel(rim, y) shr (it * 8) and 0xFF) - (level shr (it * 8) and 0xFF)) < RIM }) rim++
            return (rim - (start + end)) / d
        }
        val seen = mutableListOf(textEnd())
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { strip.dispatch(Action.Enter) }
        repeat(FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            seen += textEnd()
            slabGaps += slabPastText()
        }
        assertTrue("the slab ends the same way past the text at every frame: $slabGaps", slabGaps.all { it in SLAB_PAST_TEXT })
        compose.mainClock.autoAdvance = true
        val steps = seen.zipWithNext { a, b -> abs(a - b) }
        val travel = abs(seen.first() - seen.last())
        assertTrue("the text moves between its two places: $seen", travel >= STILL_PX)
        assertTrue("no frame takes a jump: $seen", steps.all { it <= travel * MOST_OF_THE_SLIDE + JITTER_PX })
    }

    @Test fun graphiteMedium() = theRowsReachTheRightMargin(PlumThemeSettings.Graphite, TextSize.Medium)

    @Test fun graphiteLargest() = theRowsReachTheRightMargin(PlumThemeSettings.Graphite, TextSize.Largest)

    @Test fun paperMedium() = theRowsReachTheRightMargin(PlumThemeSettings.Paper, TextSize.Medium)

    @Test fun paperLargest() = theRowsReachTheRightMargin(PlumThemeSettings.Paper, TextSize.Largest)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class ListStretch768Test : ListStretchBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class ListStretch576Test : ListStretchBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class ListStretch832Test : ListStretchBase()

private const val FRAMES = 16

/** The slab's rim against its fill. */
private const val RIM = 10

/** Inside the slab, above and below the text's line. */
private const val TEXT_INSET_DP = 8f

/** In dp: the row's 14 dp end padding, a little either way for the rim and the letter's edge. */
private val SLAB_PAST_TEXT = 9f..18f

private const val JITTER_PX = 4

private const val STILL_PX = 40

/** Past the title of the row measured. */
private const val TITLE_DP = 230f

/** Inside the text: its letters are closer than this. */
private const val GAP_DP = 6f

/** The slide eases out over about a dozen frames: its first frame is its longest, never this share of the way. */
private const val MOST_OF_THE_SLIDE = 0.45f

/** Tracked caps end a little short of their box, so the ink may stop this far from the margin. */
private const val SLACK_DP = 2f

private const val INK = 40
