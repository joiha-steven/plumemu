package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreinput.Column
import com.plumemu.coreui.components.TopBarTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.home.search.SearchState
import com.plumemu.setup.SetupAt
import com.plumemu.setup.SetupFakes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The Wi-Fi glyph in the top bar of the main screen, search, Settings and setup, at all six text
 * sizes in all three fonts on a 576 and a 768 wide screen. It measures INK in the drawn bitmap,
 * not layout boxes (a row centres boxes whatever is drawn in them): the glyph's ink is as tall as
 * the battery digits' ink and its centre is within 1 px of theirs. The gap on each side of the
 * glyph is the clock-to-battery gap, and nothing overlaps or leaves the screen. Prints the numbers.
 */
abstract class WifiTopBarAlignBase(
    private val label: String,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(text: String): DpRect = compose.onNode(hasText(text), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun wifiBounds(): DpRect = compose.onNodeWithTag(TopBarTags.WIFI, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** First and last row with ink in the columns [left, right) of [image], above row [bottom]. */
    private fun inkRows(
        image: android.graphics.Bitmap,
        left: Int,
        right: Int,
        bottom: Int,
    ): IntRange {
        val background = image.getPixel(image.width - 1, 0)
        fun differs(
            x: Int,
            y: Int,
        ): Boolean {
            val a = image.getPixel(x, y)
            val sum = abs(android.graphics.Color.red(a) - android.graphics.Color.red(background)) +
                abs(android.graphics.Color.green(a) - android.graphics.Color.green(background)) +
                abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(background))
            return sum > INK_DIFFERENCE
        }
        val rows = (0 until bottom.coerceAtMost(image.height)).filter { y -> (left until right.coerceAtMost(image.width)).any { x -> differs(x, y) } }
        return if (rows.isEmpty()) IntRange.EMPTY else rows.first()..rows.last()
    }

    private fun checkAt(
        where: String,
        withWifi: Boolean,
        size: TextSize,
        font: PlumFont,
        problems: MutableList<String>,
    ) {
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val clock = bounds(BOARD_CLOCK)
        val battery = bounds(BOARD_BATTERY)
        val at = "$label $where ${font.name} ${size.name}"
        val gap = PlumDimens.topBarItemGap.value
        if (!withWifi) {
            val between = battery.left.value - clock.right.value
            if (!near(between, gap)) problems += "$at: clock-battery gap $between, not $gap"
            return
        }
        val wifi = wifiBounds()
        val before = wifi.left.value - clock.right.value
        val after = battery.left.value - wifi.right.value
        if (!near(before, gap)) problems += "$at: gap before the glyph $before, the clock-battery gap is $gap"
        if (!near(after, gap)) problems += "$at: gap after the glyph $after, the clock-battery gap is $gap"
        if (wifi.left.value < clock.right.value || wifi.right.value > battery.left.value) problems += "$at: the glyph overlaps the clock or the battery"
        if (wifi.top.value < 0f || wifi.bottom.value > root.bottom.value || battery.right.value > root.right.value) problems += "$at: something leaves the screen"

        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val bottom = ceil((battery.bottom.value) * DP_TO_PX).toInt() + 2
        val glyph = inkRows(image, floor(wifi.left.value * DP_TO_PX).toInt(), ceil(wifi.right.value * DP_TO_PX).toInt(), bottom)
        val digits = inkRows(image, floor(battery.left.value * DP_TO_PX).toInt(), ceil(battery.right.value * DP_TO_PX).toInt(), bottom)
        if (glyph.isEmpty() || digits.isEmpty()) {
            problems += "$at: no ink found (glyph $glyph, digits $digits)"
            return
        }
        val glyphHeight = glyph.last - glyph.first + 1
        val digitHeight = digits.last - digits.first + 1
        val centreOff = (glyph.first + glyph.last) / 2f - (digits.first + digits.last) / 2f
        println("WIFI-INK $at: glyph rows $glyph (h $glyphHeight px), digits rows $digits (h $digitHeight px), centre off $centreOff px")
        if (abs(centreOff) > 1f) problems += "$at: the glyph's ink centre is $centreOff px from the digits'"
        if (abs(glyphHeight - digitHeight) > 1) problems += "$at: the glyph's ink is $glyphHeight px tall, the digits' $digitHeight"
    }

    private fun eachSize(
        where: String,
        content: @androidx.compose.runtime.Composable (wifi: Boolean) -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Small)
        var font by mutableStateOf(PlumFont.Sans)
        var wifi by mutableStateOf(true)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { content(wifi) } }
        val problems = mutableListOf<String>()
        for (eachFont in PlumFont.entries) {
            font = eachFont
            for (each in TextSize.entries) {
                size = each
                for (on in listOf(false, true)) {
                    wifi = on
                    compose.waitForIdle()
                    checkAt(where, on, each, eachFont, problems)
                }
            }
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    protected fun mainScreen() = eachSize("home") { on -> HomeAt(FakeHomeContent.board(), BoardSystemsFocused, wifi = { on }) }

    protected fun settingsHeader() = eachSize("settings") { on -> SettingsAt(SettingsFocus(section = SettingsSection.Appearance), wifi = on) }

    protected fun searchBar() =
        eachSize("search") { on ->
            val search = remember { SearchState().apply { open() } }
            HomeAt(FakeHomeContent.board(), BoardSystemsFocused, search = search, wifi = { on })
        }

    protected fun setupBar() = eachSize("setup") { on -> SetupAt(SetupFakes.chooseFolder, wifi = on) }

    private fun near(
        a: Float,
        b: Float,
    ) = abs(a - b) <= 0.5f

    private companion object {
        /** The reference screenshots are xhdpi. */
        const val DP_TO_PX = 2f

        /** Summed colour difference from the background that counts as ink. */
        const val INK_DIFFERENCE = 120
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class WifiTopBarAlign576Test : WifiTopBarAlignBase("576x432") {
    @Test fun mainScreen_() = mainScreen()

    @Test fun settingsHeader_() = settingsHeader()

    @Test fun searchBar_() = searchBar()

    @Test fun setupBar_() = setupBar()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class WifiTopBarAlign768Test : WifiTopBarAlignBase("768x432") {
    @Test fun mainScreen_() = mainScreen()

    @Test fun settingsHeader_() = settingsHeader()

    @Test fun searchBar_() = searchBar()

    @Test fun setupBar_() = setupBar()
}
