package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.components.TopBarTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.search.SearchState
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsSection
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
 * The charging bolt before the battery percentage in the top bar of the main screen, search, Settings and setup, with
 * Wi-Fi on, at every text size in all three fonts on a 576 and a 768 wide screen: it stands `topBarChargingGap` before
 * the digits and `topBarItemGap` after the Wi-Fi glyph, the clock keeps its whole text inside the screen, the
 * percentage ends at the side margin, and nothing overlaps. Its ink (measured in the drawn bitmap) is as tall as the
 * digits' and centred on them within 1 px. Without a charger there is no bolt. Prints the numbers.
 */
abstract class ChargingTopBarFitBase(
    private val label: String,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(text: String): DpRect = compose.onNode(hasText(text), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun tagged(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

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
        at: String,
        problems: MutableList<String>,
    ) {
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val clock = bounds(BOARD_CLOCK)
        val wifi = tagged(TopBarTags.WIFI)
        val bolt = tagged(TopBarTags.CHARGING)
        val battery = bounds(BOARD_BATTERY)
        compose.onNodeWithTag(TopBarTags.CHARGING, useUnmergedTree = true).assertContentDescriptionEquals("Charging")
        val beforeDigits = battery.left.value - bolt.right.value
        val afterWifi = bolt.left.value - wifi.right.value
        val sideMargin = root.right.value - battery.right.value
        println(
            "CHARGING $at: clock ${clock.left.value}..${clock.right.value}, bolt ${bolt.left.value}..${bolt.right.value}, " +
                "digits ${battery.left.value}..${battery.right.value}, bolt-digits $beforeDigits, wifi-bolt $afterWifi, margin $sideMargin",
        )
        if (!near(beforeDigits, PlumDimens.topBarChargingGap.value)) problems += "$at: bolt-digits gap $beforeDigits, not ${PlumDimens.topBarChargingGap.value}"
        if (!near(afterWifi, PlumDimens.topBarItemGap.value)) problems += "$at: wifi-bolt gap $afterWifi, not ${PlumDimens.topBarItemGap.value}"
        if (!near(sideMargin, PlumDimens.stripMarginSide.value)) problems += "$at: the percentage ends $sideMargin from the edge, not ${PlumDimens.stripMarginSide.value}"
        if (clock.left.value < 0f || clock.right.value > wifi.left.value) problems += "$at: the clock is pushed off or overlaps the Wi-Fi glyph"
        if (clock.right.value - clock.left.value <= 0f) problems += "$at: the clock has no width"
        if (bolt.top.value < 0f || bolt.bottom.value > root.bottom.value) problems += "$at: the bolt leaves the screen"

        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val bottom = ceil(battery.bottom.value * DP_TO_PX).toInt() + 2
        val glyph = inkRows(image, floor(bolt.left.value * DP_TO_PX).toInt(), ceil(bolt.right.value * DP_TO_PX).toInt(), bottom)
        val digits = inkRows(image, floor(battery.left.value * DP_TO_PX).toInt(), ceil(battery.right.value * DP_TO_PX).toInt(), bottom)
        if (glyph.isEmpty() || digits.isEmpty()) {
            problems += "$at: no ink found (bolt $glyph, digits $digits)"
            return
        }
        val glyphHeight = glyph.last - glyph.first + 1
        val digitHeight = digits.last - digits.first + 1
        val centreOff = (glyph.first + glyph.last) / 2f - (digits.first + digits.last) / 2f
        println("CHARGING-INK $at: bolt rows $glyph (h $glyphHeight px), digits rows $digits (h $digitHeight px), centre off $centreOff px")
        if (abs(centreOff) > 1f) problems += "$at: the bolt's ink centre is $centreOff px from the digits'"
        if (abs(glyphHeight - digitHeight) > 1) problems += "$at: the bolt's ink is $glyphHeight px tall, the digits' $digitHeight"
    }

    private fun eachSize(
        where: String,
        content: @androidx.compose.runtime.Composable (charging: Boolean) -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Small)
        var font by mutableStateOf(PlumFont.Sans)
        var charging by mutableStateOf(true)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { content(charging) } }
        val problems = mutableListOf<String>()
        for (eachFont in PlumFont.entries) {
            font = eachFont
            for (each in TextSize.entries) {
                size = each
                charging = true
                compose.waitForIdle()
                checkAt("$label $where ${eachFont.name} ${each.name}", problems)
            }
        }
        charging = false
        compose.waitForIdle()
        if (compose.onAllNodes(androidx.compose.ui.test.hasTestTag(TopBarTags.CHARGING), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            problems += "$label $where: a bolt shows off the charger"
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    protected fun mainScreen() = eachSize("home") { on -> HomeAt(FakeHomeContent.board(), BoardSystemsFocused, wifi = { true }, charging = { on }) }

    protected fun gamesFocused() = eachSize("games") { on -> HomeAt(FakeHomeContent.board(), BoardGamesFocused, wifi = { true }, charging = { on }) }

    protected fun settingsHeader() = eachSize("settings") { on -> SettingsAt(SettingsFocus(section = SettingsSection.Appearance), wifi = true, charging = on) }

    protected fun searchBar() =
        eachSize("search") { on ->
            val search = remember { SearchState().apply { open() } }
            HomeAt(FakeHomeContent.board(), BoardSystemsFocused, search = search, wifi = { true }, charging = { on })
        }

    protected fun setupBar() = eachSize("setup") { on -> SetupAt(SetupFakes.chooseFolder, wifi = true, charging = on) }

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
class ChargingTopBarFit576Test : ChargingTopBarFitBase("576x432") {
    @Test fun mainScreen_() = mainScreen()

    @Test fun gamesFocused_() = gamesFocused()

    @Test fun settingsHeader_() = settingsHeader()

    @Test fun searchBar_() = searchBar()

    @Test fun setupBar_() = setupBar()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class ChargingTopBarFit768Test : ChargingTopBarFitBase("768x432") {
    @Test fun mainScreen_() = mainScreen()

    @Test fun gamesFocused_() = gamesFocused()

    @Test fun settingsHeader_() = settingsHeader()

    @Test fun searchBar_() = searchBar()

    @Test fun setupBar_() = setupBar()
}
