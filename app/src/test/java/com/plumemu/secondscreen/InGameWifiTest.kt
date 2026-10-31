package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpOffset
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.BatteryReading
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The Wi-Fi glyph on the in-game card's battery line. Like WifiTopBarAlignTest it measures INK in
 * the drawn bitmap, not layout boxes: the glyph's ink is as tall as the battery digits' ink and
 * centred on them within 1 px, at both card sizes and at the corners of the pixel shift. It is
 * there only while connected and only on a battery line. Prints the numbers.
 */
abstract class InGameWifiBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var wifi by mutableStateOf(true)
    private var battery by mutableStateOf<BatteryReading?>(BatteryReading(percent = 82, isCharging = false))
    private var shift by mutableStateOf(DpOffset.Zero)

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { InGameCard(time = "21:47", battery = battery, wifi = wifi, shift = { shift }) }
    }

    private fun glyphs() = compose.onAllNodesWithTag(InGameTags.WIFI, useUnmergedTree = true)

    @Test
    fun theGlyphIsThereOnlyWhileConnected() {
        compose.waitForIdle()
        glyphs().assertCountEquals(1)
        wifi = false
        compose.waitForIdle()
        glyphs().assertCountEquals(0)
        wifi = true
        compose.waitForIdle()
        glyphs().assertCountEquals(1)
    }

    @Test
    fun noBatteryLineMeansNoGlyph() {
        battery = null
        compose.waitForIdle()
        glyphs().assertCountEquals(0)
    }

    @Test
    fun theGlyphSitsBeforeTheBatteryGlyphAndTheTextAndInsideTheBlock() {
        compose.waitForIdle()
        val battery = compose.onNodeWithTag(InGameTags.BATTERY, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val glyph = compose.onNodeWithTag(InGameTags.WIFI, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val text = compose.onNode(hasText("82%"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val block = compose.onNodeWithTag(InGameTags.BLOCK).getUnclippedBoundsInRoot()
        assertTrue("glyph $glyph before the battery glyph $battery", glyph.right.value < battery.left.value)
        assertTrue("battery glyph $battery before text $text", battery.right.value < text.left.value)
        assertTrue("glyph $glyph before text $text", glyph.right.value < text.left.value)
        assertTrue("glyph $glyph inside the block $block", glyph.left.value >= block.left.value && glyph.right.value <= block.right.value)
    }

    @Test
    fun theGlyphsInkIsAsTallAsTheDigitsAndCentredOnThemAtEveryShift() {
        val problems = mutableListOf<String>()
        for (point in listOf(GridPoint(0, 0), GridPoint(PixelShift.COLUMNS - 1, PixelShift.ROWS - 1))) {
            shift = PixelShift.offsetOf(point)
            for (percent in listOf(82, 100, 12)) {
                battery = BatteryReading(percent, isCharging = false)
                compose.waitForIdle()
                measure("${size.name} $point $percent%", percent, problems)
            }
        }
        assertTrue("${problems.size} problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** The brightest value of each channel among the pixels inside the Wi-Fi glyph's own box. */
    private fun brightestInGlyph(): Triple<Int, Int, Int> {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val glyph = compose.onNodeWithTag(InGameTags.WIFI, useUnmergedTree = true).getUnclippedBoundsInRoot()
        var red = 0
        var green = 0
        var blue = 0
        for (y in floor(glyph.top.value * DP_TO_PX).toInt() until ceil(glyph.bottom.value * DP_TO_PX).toInt()) {
            for (x in floor(glyph.left.value * DP_TO_PX).toInt() until ceil(glyph.right.value * DP_TO_PX).toInt()) {
                val c = image.getPixel(x, y)
                red = maxOf(red, android.graphics.Color.red(c))
                green = maxOf(green, android.graphics.Color.green(c))
                blue = maxOf(blue, android.graphics.Color.blue(c))
            }
        }
        println("INGAME-WIFI-COLOUR ${size.name}: brightest in the glyph r=$red g=$green b=$blue")
        return Triple(red, green, blue)
    }

    /** The glyph takes the battery line's grey, never the time's brighter #646464. */
    @Test
    fun theGlyphIsTheBatteryLinesGreyAndNoBrighter() {
        compose.waitForIdle()
        val (red, green, blue) = brightestInGlyph()
        assertTrue("some ink drawn: $red", red > 0)
        assertTrue("no brighter than #555555: $red $green $blue", red <= 0x55 && green <= 0x55 && blue <= 0x55)
        assertTrue("it is the line's grey, not dimmer than #494949: $red", red >= 0x49)
    }

    /** At 15% and below the glyph is the line's dim red, #8E3740. */
    @Test
    fun theGlyphIsTheLowBatteryRedWhenLow() {
        battery = BatteryReading(percent = 12, isCharging = false)
        compose.waitForIdle()
        val (red, green, blue) = brightestInGlyph()
        assertTrue("red channel up to #8E: $red", red in 0x80..0x8E)
        assertTrue("green no more than #37: $green", green in 1..0x37)
        assertTrue("blue no more than #40: $blue", blue in 1..0x40)
    }

    private fun measure(
        at: String,
        percent: Int,
        problems: MutableList<String>,
    ) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val glyph = compose.onNodeWithTag(InGameTags.WIFI, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val text = compose.onNode(hasText("$percent%"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        // Only the battery line's rows: the time above shares these columns.
        val top = floor(minOf(text.top.value, glyph.top.value) * DP_TO_PX).toInt() - 1
        val bottom = ceil(maxOf(text.bottom.value, glyph.bottom.value) * DP_TO_PX).toInt() + 2
        val glyphRows = inkRows(image, floor(glyph.left.value * DP_TO_PX).toInt(), ceil(glyph.right.value * DP_TO_PX).toInt(), top, bottom)
        val textRows = inkRows(image, floor(text.left.value * DP_TO_PX).toInt(), ceil(text.right.value * DP_TO_PX).toInt(), top, bottom)
        if (glyphRows.isEmpty() || textRows.isEmpty()) {
            problems += "$at: no ink (glyph $glyphRows, text $textRows)"
            return
        }
        val glyphHeight = glyphRows.last - glyphRows.first + 1
        val textHeight = textRows.last - textRows.first + 1
        val centreOff = (glyphRows.first + glyphRows.last) / 2f - (textRows.first + textRows.last) / 2f
        println("INGAME-WIFI-INK $at: glyph rows $glyphRows (h $glyphHeight px), digits rows $textRows (h $textHeight px), centre off $centreOff px")
        if (abs(centreOff) > 1f) problems += "$at: the glyph's ink centre is $centreOff px from the digits'"
        if (abs(glyphHeight - textHeight) > 1) problems += "$at: the glyph's ink is $glyphHeight px tall, the digits' $textHeight"
    }

    /** First and last row with any lit pixel in the columns [left, right) between rows [top] and [bottom]: the card is black. */
    private fun inkRows(
        image: android.graphics.Bitmap,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
    ): IntRange {
        val rows =
            (top.coerceAtLeast(0) until bottom.coerceAtMost(image.height)).filter { y ->
                (left until right.coerceAtMost(image.width)).any { x -> image.getPixel(x, y) and RGB != 0 }
            }
        return if (rows.isEmpty()) IntRange.EMPTY else rows.first()..rows.last()
    }

    private companion object {
        /** The reference qualifiers are xhdpi. */
        const val DP_TO_PX = 2f

        const val RGB = 0x00FFFFFF
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameWifiRoomyTest : InGameWifiBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameWifiCompactTest : InGameWifiBase(SecondSize.Compact)
