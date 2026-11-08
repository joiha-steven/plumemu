package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.components.TopBar
import com.plumemu.coreui.components.TopBarTags
import com.plumemu.coreui.components.readableDanger
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.TypeRole
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
 * The top bar's right side: the clock and the battery percentage at the bar's own step (never under the games list's tag and year),
 * no battery drawing, and a low level shown on the number in the danger colour.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class TopBarStatusTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Graphite)
    private var battery by mutableStateOf("82%")
    private var charging by mutableStateOf(false)
    private var danger = Color.Unspecified
    private var background = Color.Unspecified

    private fun show() =
        compose.setContent {
            PlumTheme(settings) {
                danger = readableDanger(PlumTheme.colors.danger, PlumTheme.colors.bg, PlumTheme.colors.strong)
                background = PlumTheme.colors.bg
                Box(Modifier.fillMaxWidth()) {
                    TopBar("Super Nintendo · 248 games", "Super Nintendo", { "21:40" }, { battery }, wifi = { true }, charging = { charging }, chargingDescription = "Charging")
                }
            }
        }

    private fun fontSizeOf(text: String): Float {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style.fontSize.value
    }

    @Test
    fun theClockAndThePercentageAreNeverUnderTheListTagAndYear() {
        show()
        for (font in listOf(PlumFont.Jost)) {
            for (size in TextSize.entries) {
                settings = PlumThemeSettings.Graphite.copy(textSize = size)
                compose.waitForIdle()
                val floor = maxOf(TypeRole.Tag, TypeRole.RowYear).scaledSizeSp(font, size)
                val bar = TypeRole.TopBar.scaledSizeSp(font, size)
                for (text in listOf("21:40", "82%")) {
                    val got = fontSizeOf(text)
                    assertEquals("$font $size '$text'", bar, got, 0.01f)
                    assertTrue("$font $size '$text' $got is under the tag step $floor", got >= floor)
                }
            }
        }
    }

    @Test
    fun nothingIsDrawnBetweenTheWifiGlyphAndThePercentage() {
        show()
        compose.waitForIdle()
        val wifi = compose.onNodeWithTag(TopBarTags.WIFI, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val percent = compose.onNodeWithTag(TopBarTags.BATTERY_TEXT, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("the percentage follows the Wi-Fi glyph at the bar's item gap", PlumDimens.topBarItemGap.value, percent.left.value - wifi.right.value, 0.5f)
    }

    /** How many pixels inside the percentage's box are the danger colour (within a small difference). */
    private fun dangerPixels(): Int {
        compose.waitForIdle()
        val box = compose.onNodeWithTag(TopBarTags.BATTERY_TEXT, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val want = danger.toArgb()
        val density = compose.density.density
        var count = 0
        for (y in (box.top.value * density).toInt() until (box.bottom.value * density).toInt().coerceAtMost(image.height)) {
            for (x in (box.left.value * density).toInt() until (box.right.value * density).toInt().coerceAtMost(image.width)) {
                val p = image.getPixel(x, y)
                val d = abs(android.graphics.Color.red(p) - android.graphics.Color.red(want)) + abs(android.graphics.Color.green(p) - android.graphics.Color.green(want)) + abs(android.graphics.Color.blue(p) - android.graphics.Color.blue(want))
                if (d <= 40) count++
            }
        }
        return count
    }

    @Test
    fun aLowLevelShowsOnTheNumberInTheDangerColourOnBothThemes() {
        show()
        for (base in listOf(PlumThemeSettings.Graphite, PlumThemeSettings.Paper)) {
            settings = base
            battery = "12%"
            charging = false
            assertTrue("12% not charging has danger ink", dangerPixels() > 20)
            battery = "82%"
            assertEquals("82% has none", 0, dangerPixels())
            battery = "12%"
            charging = true
            assertEquals("12% on the charger has none", 0, dangerPixels())
            charging = false
            battery = "16%"
            assertEquals("16% is not low", 0, dangerPixels())
        }
    }

    @Test
    fun theDangerColourStaysWithinTheContrastFloorOnTheBarsBackground() {
        show()
        for (base in listOf(PlumThemeSettings.Graphite, PlumThemeSettings.Paper)) {
            settings = base
            compose.waitForIdle()
            val ratio = contrast(danger, background)
            assertTrue("danger on the bar: $ratio is under 4.5", ratio >= 4.5)
        }
    }

    /** WCAG contrast ratio of two opaque colours. */
    private fun contrast(a: Color, b: Color): Double {
        fun lum(c: Color): Double {
            fun lin(v: Float): Double = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
        }
        val x = lum(a)
        val y = lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
