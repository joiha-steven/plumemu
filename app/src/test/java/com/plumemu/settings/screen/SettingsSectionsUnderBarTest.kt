package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A scrolled sections list draws nothing at or above the top bar's bottom edge, nor in the gap between the bar and the strip:
 * the bar region is the same pixels with the focus on the second section (list at rest, the first row without glow) as on the last
 * section (list scrolled up under the bar), and the gap shows only the background.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w853dp-h480dp-land-xhdpi")
class SettingsSectionsUnderBarTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun scrolledSectionsStayBelowTheTopBar() {
        var size by mutableStateOf(TextSize.Small)
        var last by mutableStateOf(false)
        var density = Density(1f)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                density = LocalDensity.current
                val sections = SettingsSection.shownIn(SettingsContent())
                // At rest the focus is on the second section: the first one's own glow reaches past the strip's top, over the bar's bottom rows, which is not what this checks.
                SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = if (last) sections.last() else sections[1]))
            }
        }
        val problems = mutableListOf<String>()
        for (each in listOf(TextSize.Small, TextSize.Large, TextSize.Huge, TextSize.Largest)) {
            size = each
            last = false
            compose.waitForIdle()
            val bar = compose.onNodeWithTag(SettingsTags.TOP_BAR, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val before = compose.onRoot().captureToImage().toPixelMap()
            last = true
            compose.waitForIdle()
            val after = compose.onRoot().captureToImage().toPixelMap()
            val strip = compose.onNodeWithTag(SettingsTags.STRIP, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            // The title's last pixel row (a descender) may touch the bar's edge: the check starts a few pixels under it.
            val gapTop = bar.bottom.toInt() + 3
            var peek = 0f
            var peekAt = ""
            for (y in gapTop until strip.top.toInt()) for (x in with(density) { 24.dp.roundToPx() } until with(density) { 170.dp.roundToPx() }) {
                val p = after[x, y]
                val bg = after[with(density) { 176.dp.roundToPx() }, y]
                val d = maxOf(abs(p.red - bg.red), abs(p.green - bg.green), abs(p.blue - bg.blue))
                if (d > peek) { peek = d; peekAt = "x=${x} y=${y}" }
            }
            if (peek > TOLERANCE) problems += "$each: a row cut by the scrolled list shows in the gap under the bar ($peekAt; bar ${bar.bottom.toInt()}..${strip.top.toInt()}), ${(peek * 255).toInt()}/255"
            // Under the title, left of the clock: the sections column's own width.
            val to = with(density) { 178.dp.roundToPx() }
            var worst = 0f
            var at = ""
            for (y in 0 until bar.bottom.toInt()) for (x in 0 until to) {
                val a = before[x, y]
                val b = after[x, y]
                val d = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
                if (d > worst) {
                    worst = d
                    at = "x=${x}px y=${y}px"
                }
            }
            if (worst > TOLERANCE) problems += "$each: the list draws above the bar's bottom edge (${bar.bottom.toInt()}px) at $at, ${(worst * 255).toInt()}/255"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val TOLERANCE = 2f / 255f
    }
}
