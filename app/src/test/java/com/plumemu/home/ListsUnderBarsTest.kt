package com.plumemu.home

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.BAR_FADE_BOTTOM
import com.plumemu.coreui.components.BAR_FADE_TOP
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Home's lists run under both bars (design/rules.md › Lists that scroll): a row clear of the screen's one fade is drawn at full strength, the first and last whole rows of a games
 * list at rest carrying the same ink as a row in the middle, in both colour sets, with the list at its top and scrolled; the next row goes under the bar, and can be tapped.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ListsUnderBarsBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(
        settings: PlumThemeSettings,
        focusRow: Int,
        systemBand: Boolean = false,
    ) {
        val content = FakeHomeContent.large(2_000)
        val actions = listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter, Action.TapItem(Column.Games, focusRow))
        compose.setContent { PlumTheme(settings) { HomeAt(content, actions, showSystemInfo = systemBand) } }
        compose.waitForIdle()
    }

    private fun boundsOf(row: Int): DpRect? {
        val tag = HomeTags.game(row)
        if (compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) return null
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
        val seen = node.getBoundsInRoot()
        val all = node.getUnclippedBoundsInRoot()
        // A row's node carries the gap after it, which the last row may leave outside the list.
        val whole = (seen.bottom - seen.top).value >= (all.bottom - all.top).value - PlumDimens.listRowGap.value - 0.5f && seen.bottom.value <= fadeTop() + 0.5f
        return seen.takeIf { whole && (seen.bottom - seen.top).value > 1f }
    }

    /** The strongest difference from the page colour inside a row, on the row's left half (the title), any channel. */
    private fun Bitmap.ink(
        row: DpRect,
        background: Int,
    ): Int {
        val d = compose.density.density
        var most = 0
        for (y in (row.top.value * d).toInt() until (row.bottom.value * d).toInt().coerceAtMost(height)) {
            for (x in (row.left.value * d).toInt() until ((row.left.value + row.right.value) / 2 * d).toInt().coerceAtMost(width)) {
                val p = getPixel(x, y)
                val diff = maxOf(abs((p shr 16 and 255) - (background shr 16 and 255)), abs((p shr 8 and 255) - (background shr 8 and 255)), abs((p and 255) - (background and 255)))
                if (diff > most) most = diff
            }
        }
        return most
    }

    private fun check(
        settings: PlumThemeSettings,
        focusRow: Int,
        label: String,
    ) {
        show(settings, focusRow)
        val shown = (0..1_000).mapNotNull { row -> boundsOf(row)?.let { row to it } }.filter { it.first != focusRow }
        assertTrue("$label: rows shown", shown.size >= 4)
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val background = image.getPixel(2, image.height / 2)
        val reference = shown.map { image.ink(it.second, background) }.max()
        for ((row, bounds) in listOf(shown.first(), shown.last())) {
            val ink = image.ink(bounds, background)
            assertTrue("$label: whole row $row has ink $ink of $reference", ink >= reference * 0.95f)
        }
    }

    private fun hint() = compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).getBoundsInRoot()

    private fun hintTop() = hint().top.value

    /** Where the bottom fade starts: its band above the footer gap. */
    private fun fadeTop() = hintTop() - PlumDimens.footerGap.value - PlumDimens.listFadeBand.value

    /** Rows go under the hint bar: at rest the next row shows past the last whole one, up to the bar, with the fade over its end; no bigger than the fade's own band of it is hidden. */
    private fun theNextRowRunsUnderTheBar(settings: PlumThemeSettings) {
        show(settings, 0)
        val whole = (0..60).mapNotNull(::boundsOf).last()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val background = image.getPixel(2, image.height / 2)
        val under = DpRect(whole.left, whole.bottom + PlumDimens.listRowGap, whole.right, hint().top - PlumDimens.footerGap - PlumDimens.listFadeBand)
        val ink = if (under.top >= under.bottom) 0 else image.ink(under, background)
        assertTrue("ink $ink past the last whole row, ${under.top} to ${under.bottom}", under.top >= under.bottom || ink > STILL_BACKGROUND)
    }

    /** The one fade lies across the whole screen, tight under the top bar and on the footer gap above the hint bar, one height. */
    @Test
    fun oneFadeAcrossTheScreen() {
        show(graphite, 40)
        val bottom = compose.onNodeWithTag(BAR_FADE_BOTTOM, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val top = compose.onNodeWithTag(BAR_FADE_TOP, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bar = compose.onNodeWithTag(HomeTags.TOP_BAR, useUnmergedTree = true).getBoundsInRoot()
        val root = compose.onRoot().getBoundsInRoot()
        assertEquals("bottom ends the footer gap above the hint bar", hint().top.value - PlumDimens.footerGap.value, bottom.bottom.value, 0.6f)
        assertEquals("top starts under the title bar", bar.bottom.value, top.top.value, 0.6f)
        assertEquals("one height", (bottom.bottom - bottom.top).value, (top.bottom - top.top).value, 0.01f)
        assertEquals("the band", PlumDimens.listFadeBand.value, (bottom.bottom - bottom.top).value, 0.01f)
        assertEquals("as wide as the screen", (root.right - root.left).value, (bottom.right - bottom.left).value, 1f)
    }

    /** A tap on the row under the bar is no dead zone: it takes the focus, and the row comes out whole before it wears the slab. */
    @Test
    fun aTapOnThePartRowTakesItWhole() {
        val content = FakeHomeContent.large(2_000)
        var state: StripState? = null
        val actions = listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter)
        compose.setContent { PlumTheme(graphite) { HomeAt(content, actions, showSystemInfo = false, onAction = { state?.dispatch(it) }, onState = { state = it }) } }
        compose.waitForIdle()
        val whole = (0..60).filter { boundsOf(it) != null }.last()
        val partRow = whole + 1
        val node = compose.onNodeWithTag(HomeTags.game(partRow), useUnmergedTree = true)
        val seen = node.getBoundsInRoot()
        assertTrue("row $partRow shows under the bar, ${seen.top} to ${seen.bottom}", (seen.bottom - seen.top).value > 1f)
        node.performTouchInput { click(Offset(centerX, 4f)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val entry = content.entries[FakeHomeContent.BOARD_SNES_INDEX].id
        assertEquals("the tapped row has the focus", partRow, state?.focus?.rowOf(entry))
        assertTrue("row $partRow is whole above the fade", boundsOf(partRow)?.let { it.bottom.value <= fadeTop() + 0.5f } == true)
    }

    /** At the true end of a list the last row rests whole above the fade. */
    @Test
    fun theLastRowRestsWholeAboveTheBar() {
        val content = FakeHomeContent.large(2_000)
        val rows = content.entries[FakeHomeContent.BOARD_SNES_INDEX].rows.size
        val actions = listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter, Action.TapItem(Column.Games, rows - 1))
        compose.setContent { PlumTheme(paper) { HomeAt(content, actions, showSystemInfo = false) } }
        compose.waitForIdle()
        val last = boundsOf(rows - 1)
        assertTrue("last row $last whole above the fade, hint at ${hint().top}", last != null && last.bottom.value <= fadeTop() + 0.5f)
    }

    private val graphite = PlumThemeSettings.Graphite
    private val paper = PlumThemeSettings.Paper

    @Test fun graphiteAtTheTop() = check(graphite, 0, "Graphite, top")

    @Test fun paperAtTheTop() = check(paper, 0, "Paper, top")

    @Test fun graphiteLargest() = check(graphite.copy(textSize = TextSize.Largest), 0, "Graphite, Largest")

    @Test fun paperLargest() = check(paper.copy(textSize = TextSize.Largest), 0, "Paper, Largest")

    @Test fun theNextRowRunsUnder() = theNextRowRunsUnderTheBar(graphite)

    @Test fun theNextRowRunsUnderWithFadesOff() = theNextRowRunsUnderTheBar(paper.copy(edgeFadeOn = false))

    @Test fun graphiteDeep() = check(graphite, 40, "Graphite, deep")

    @Test fun paperDeepLargest() = check(paper.copy(textSize = TextSize.Largest), 40, "Paper, deep, Largest")

    private companion object {
        /** A channel difference a person cannot see; an anti-aliased background is not perfectly flat in a capture. */
        const val STILL_BACKGROUND = 3
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class ListsUnderBars768Test : ListsUnderBarsBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class ListsUnderBars576Test : ListsUnderBarsBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class ListsUnderBars832Test : ListsUnderBarsBase()
