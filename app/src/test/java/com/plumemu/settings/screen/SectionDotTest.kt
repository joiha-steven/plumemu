package com.plumemu.settings.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LeftColumnDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
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
 * In three columns the open section shows an accent dot only while the focus is elsewhere, and the focused section shows the accent bar
 * only: never both on one row, and the dot's middle is the bar's middle (measured on the drawn pixels of the sections column).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SectionDotTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** The accent-coloured pixels before the label in one row: extent in px and how many. */
    private class Spot(val first: Int, val last: Int, val top: Int, val bottom: Int) {
        val centreX get() = (first + last + 1) / 2f
    }

    private fun spotOf(column: SettingsColumn): Spot? {
        var accent = Color.Unspecified
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                accent = PlumTheme.colors.accent
                SettingsAt(SettingsFocus(column = column, section = SettingsSection.Library), content = ShotContent.library)
            }
        }
        compose.waitForIdle()
        val d = compose.density.density
        val sections = compose.onNodeWithTag(SettingsTags.SECTIONS, useUnmergedTree = true)
        val image = sections.captureToImage().toPixelMap()
        val columnBounds = sections.fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag(SettingsTags.section(SettingsSection.Library), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val top = (row.top - columnBounds.top).toInt().coerceAtLeast(0)
        val bottom = (row.bottom - columnBounds.top).toInt().coerceAtMost(image.height)
        var first = Int.MAX_VALUE
        var last = -1
        var topMost = Int.MAX_VALUE
        var bottomMost = -1
        // From the screen edge to the label's start: only the bar or the dot is drawn there.
        for (y in top until bottom) {
            for (x in 0 until minOf((LeftColumnDimens.sectionLabelStart.value * d).toInt(), image.width)) {
                val c = image[x, y]
                if (abs(c.red - accent.red) + abs(c.green - accent.green) + abs(c.blue - accent.blue) < 0.12f && c.alpha > 0.9f) {
                    first = minOf(first, x)
                    last = maxOf(last, x)
                    topMost = minOf(topMost, y)
                    bottomMost = maxOf(bottomMost, y)
                }
            }
        }
        return if (last < 0) null else Spot(first, last, topMost, bottomMost)
    }

    @Test
    fun theDotStandsWhereTheBarStands() {
        val d = compose.density.density
        val dot = spotOf(SettingsColumn.Options)
        assertTrue("the open section shows no dot while the focus is in the options", dot != null)
        // A dot is round and hintDot wide; a bar is barWidth wide and much taller.
        assertEquals("dot width in dp", PlumDimens.hintDot.value, (dot!!.last - dot.first + 1) / d, 1f)
        assertEquals("dot height in dp", PlumDimens.hintDot.value, (dot.bottom - dot.top + 1) / d, 1f)
        assertEquals("dot middle x in dp", LeftColumnDimens.sectionBarCentre.value, dot.centreX / d, 0.6f)
    }

    @Test
    fun theFocusedRowShowsTheBarAloneAtTheSameX() {
        val d = compose.density.density
        val bar = spotOf(SettingsColumn.Sections)
        assertTrue("the focused section shows no bar", bar != null)
        val barHeight = (bar!!.bottom - bar.top + 1) / d
        assertEquals("bar width in dp (a dot there too would widen it)", SlabDimens.barWidth.value, (bar.last - bar.first + 1) / d, 1f)
        assertTrue("the bar is taller than the dot ($barHeight dp)", barHeight > PlumDimens.hintDot.value + 2f)
        assertEquals("bar middle x in dp", LeftColumnDimens.sectionBarCentre.value, bar.centreX / d, 0.6f)
    }
}
