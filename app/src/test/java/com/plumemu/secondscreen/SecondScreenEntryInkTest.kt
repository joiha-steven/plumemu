package com.plumemu.secondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.ProvideSecondScreenText
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.LocalBadgeImages
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
 * The system column cards' text styles take their line from the shared card styles. Nothing of a glyph may be cut by it: each style
 * draws the tallest marks the translations use (Vietnamese capitals with stacked marks: a system name, a cover caption, the about
 * text, every label), clipped to its own line box, and lights as many pixels as the same text unclipped (a cut glyph loses pixels).
 * The texts on the board's shorter line boxes are drawn in their cards instead, where nothing may cut what overreaches the box.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w538dp-h468dp-land-xhdpi")
class SecondScreenEntryInkTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var textSize by mutableStateOf(TextSize.Largest)
    private var clipped by mutableStateOf(false)
    private var which by mutableStateOf(0)

    private val marks = "\u1EA4\u1EB0\u1EA8 \u1EC0\u1ED4\u1ED6 \u1EA4 gjpqy"

    private fun stylesOf(styles: EntryStyles): List<Pair<String, TextStyle>> =
        listOf(
            "title" to styles.title, "count" to styles.count, "label" to styles.label, "big" to styles.big, "small" to styles.small, "status" to styles.status,
            "foot" to styles.foot, "captionWhen" to styles.captionWhen, "captionSystem" to styles.captionSystem, "weekDay" to styles.weekDay,
        ).filterNot { it.first in OVERREACHING }

    @Test
    fun stackedMarksAreNotCutAtTheLargestTextSize() {
        var names = emptyList<String>()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = textSize)) {
                val each = stylesOf(rememberEntryStyles())
                names = each.map { it.first }
                val style = each[which].second.copy(color = Color.White)
                val line = with(LocalDensity.current) { style.lineHeight.toDp() }
                Box(Modifier.background(Color.Black).padding(24.dp)) {
                    Column(Modifier.width(400.dp)) {
                        BasicText(
                            marks,
                            style = style,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.fillMaxWidth().height(line).let { if (clipped) it.clipToBounds() else it },
                        )
                    }
                }
            }
        }
        val failures = mutableListOf<String>()
        for (size in listOf(TextSize.Medium, TextSize.Largest)) {
            for (at in names.indices) {
                textSize = size
                which = at
                clipped = false
                compose.waitForIdle()
                val natural = lit()
                clipped = true
                compose.waitForIdle()
                val tight = lit()
                assertTrue("$size ${names.getOrNull(at)}: something is drawn", natural > 50)
                if (tight < natural - natural / 100) failures += "$size ${names.getOrNull(at)}: $tight lit pixels clipped, $natural unclipped"
            }
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    /**
     * Four texts sit on the board's own line boxes, which Vietnamese stacked capitals and descenders overreach: big, small,
     * captionWhen and captionSystem (the line-box check above leaves them out). What counts is that nothing around them cuts the
     * ink, so each lowest and highest line of its parent is drawn in its real card and must reach exactly as far as the same text
     * drawn on its own: the small line at a tile's foot (big sits above it in the same tile) and the system caption at a cover
     * cell's foot (the time sits above it in the same cell; on the system card the time is the foot of the same cell).
     */
    @Test
    fun theBoardLineTextsDrawTheirMarksWholeInTheirCards() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = textSize)) {
                ProvideSecondScreenText { CompositionLocalProvider(LocalBadgeImages provides CacheOnlyBadges()) {
                    Box(Modifier.size(538.dp, 468.dp)) {
                        val styles = rememberEntryStyles()
                        val alone = Modifier.padding(24.dp).boardLine(styles.small, styles)
                        when (host) {
                            Host.Tile -> SecondScreenCard(SYSTEM_WITH_LAST.copy(name = shown))
                            Host.Caption -> SecondScreenCard(RECENT_FULL.copy(systems = RECENT_FULL.systems.mapValues { (_, look) -> look.copy(caption = shown) }))
                            Host.AloneSmall -> SmallLine(stringResource(R.string.second_screen_on_system, shown), styles, modifier = alone)
                            Host.AloneCaption -> BasicText(shown, style = styles.captionSystem, modifier = Modifier.padding(24.dp).boardLine(styles.captionSystem, styles))
                        }
                    }
                } }
            }
        }
        for (size in listOf(TextSize.Medium, TextSize.Largest)) {
            textSize = size
            for ((inCard, alone, text) in listOf(Triple(Host.Tile, Host.AloneSmall, "on "), Triple(Host.Caption, Host.AloneCaption, ""))) {
                val card = inkReach(inCard, text)
                val own = inkReach(alone, text)
                assertTrue("$size $inCard: the marks reach past the line box (${own.first}, ${own.second}), so the check means something", own.first < 0)
                assertEquals("$size $inCard: highest and lowest ink row from the line's top, in the card and on its own", own, card)
            }
        }
    }

    private enum class Host { Tile, Caption, AloneSmall, AloneCaption }

    private var host by mutableStateOf(Host.Tile)
    private var shown by mutableStateOf("")

    /** Short enough to stay on one line in a tile at the largest text size. */
    private val fewMarks = "\u1EA4\u1EB0\u1EC4 gjy"

    /** The rows (from the line's top) where the marks' ink starts and ends: the same frame with the marks and with a space. */
    private fun inkReach(
        on: Host,
        before: String,
    ): Pair<Int, Int> {
        host = on
        shown = fewMarks
        compose.waitForIdle()
        val box = compose.onAllNodesWithText("$before$fewMarks")[0].getBoundsInRoot()
        val drawn = pixelsOf()
        // A space, not nothing: an empty caption falls back to the system's name.
        shown = " "
        compose.waitForIdle()
        assertEquals("$on: the line stays where it was", box.top, compose.onAllNodesWithText("$before ")[0].getBoundsInRoot().top)
        val blank = pixelsOf()
        val density = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
        val left = (box.left.value * density).toInt()
        val right = (box.right.value * density).toInt().coerceAtMost(drawn.width)
        val top = (box.top.value * density).roundToInt()
        val bottom = (box.bottom.value * density).roundToInt()
        val rows = (top - REACH).coerceAtLeast(0) until (bottom + REACH).coerceAtMost(drawn.height)

        fun change(
            x: Int,
            y: Int,
        ): Int {
            val a = drawn.pixels[y * drawn.width + x]
            val b = blank.pixels[y * drawn.width + x]
            return listOf(16, 8, 0).maxOf { shift -> abs((a shr shift and 0xFF) - (b shr shift and 0xFF)) }
        }
        val full = rows.maxOf { y -> (left until right).maxOf { x -> change(x, y) } }
        val inked = rows.filter { y -> (left until right).any { x -> change(x, y) > full / 4 } }
        assertTrue("$on: no ink found", inked.isNotEmpty())
        return inked.first() - top to inked.last() - top
    }

    private class Pixels(val width: Int, val height: Int, val pixels: IntArray)

    private fun pixelsOf(): Pixels {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return Pixels(bitmap.width, bitmap.height, pixels)
    }

    private fun lit(): Int {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { (it and 0xFF) > 0x40 }
    }

    private companion object {
        /** On the board's line boxes, which the marks overreach: checked in their cards instead. */
        val OVERREACHING = setOf("big", "small", "captionWhen", "captionSystem")

        /** Rows looked at past the line box, more than any mark reaches. */
        const val REACH = 16
    }
}
