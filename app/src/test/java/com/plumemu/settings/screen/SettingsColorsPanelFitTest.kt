package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpRect
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.toThemeSettings
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
 * Settings › Colors' panel measured, on own colours so Custom has its tile too (11),
 * in every font at all six text sizes:
 * - every tile name fits its tile: at most two lines, broken only between words, never cut;
 * - the tiles start on the panel's row-text line (the title's left), the last column ends on the
 *   right one, they are 12 apart across and down, every picture has the same size;
 * - the focused tile's ring (the last tile, so the panel had to scroll) is inside the panel, and
 *   nothing sits nearer the panel's edge than the ring's room.
 */
abstract class SettingsColorsPanelFitBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val own =
        PlumSettings(
            appearance = AppearanceSettings(background = ColorSetting.Preset("Graphite")),
            flags = SetupFlags(setupCompleted = true),
        )

    protected fun tilesFitAndLineUp(label: String) {
        val entries = colorSetsPanelEntries(own)
        assertEquals("ten sets and Custom", ColorSet.entries.size + 1, entries.size)
        val last = entries.lastIndex
        var settings by mutableStateOf(own)
        compose.setContent {
            PlumTheme(settings.toThemeSettings()) {
                SettingsAt(panelAt(SettingsOption.Colors, PanelPage.ColorSets, last), settings)
            }
        }
        val problems = mutableListOf<String>()
        for (font in FontSetting.entries) {
            for (size in TextSizeSetting.entries) {
                settings = own.copy(appearance = own.appearance.copy(font = font, textSize = size))
                compose.waitForIdle()
                problems += check("$label $font $size", entries.size)
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().let { DpRect(it.left, it.top, it.right, it.bottom) }

    private fun check(
        at: String,
        count: Int,
    ): List<String> {
        val problems = mutableListOf<String>()
        val panel = bounds(SettingsTags.PANEL)
        val textLine = panel.left.value + PlumDimens.settingsPanelRowPadding.value
        val textEnd = panel.right.value - PlumDimens.settingsPanelRowPadding.value
        val tiles = (0 until count).map { bounds(SettingsTags.panelItem(it)) }
        val pictures = compose.onAllNodesWithTag(SettingsTags.COLOR_TILE, useUnmergedTree = true).fetchSemanticsNodes().map { it.size }
        if (pictures.size != count) problems += "$at: ${pictures.size} pictures for $count tiles"
        // Equal to the pixel: the row shares its width out by weight, so a leftover pixel goes to some tiles (1 px = 0.5 dp here).
        if (pictures.isNotEmpty() && (pictures.maxOf { it.width } - pictures.minOf { it.width } > 1 || pictures.maxOf { it.height } - pictures.minOf { it.height } > 1)) problems += "$at: pictures differ in size ${pictures.distinct()}"
        // Rows by top; columns from the first row.
        val rows = tiles.groupBy { Math.round(it.top.value * 2) }.toSortedMap().values.toList()
        val columns = rows.first().size
        println("COLORSFIT $at: $columns per row, ${rows.size} rows, tile ${tiles.first().right - tiles.first().left} wide, panel ${panel.left}..${panel.right}")
        for ((r, row) in rows.withIndex()) {
            if (r < rows.lastIndex && row.size != columns) problems += "$at: row $r has ${row.size} tiles, not $columns"
            if (!near(row.first().left.value, textLine)) problems += "$at: row $r starts at ${row.first().left}, the text line is $textLine"
            if (row.size == columns && !near(row.last().right.value, textEnd)) problems += "$at: row $r ends at ${row.last().right}, the text line's end is $textEnd"
            row.zipWithNext().forEach { (a, b) ->
                val gap = b.left.value - a.right.value
                if (!near(gap, PlumDimens.settingsColorTileGap.value)) problems += "$at: row $r gap $gap"
            }
            if (r > 0) {
                val gap = row.first().top.value - rows[r - 1].maxOf { it.bottom.value }
                if (!near(gap, PlumDimens.settingsColorTileGap.value)) problems += "$at: rows ${r - 1}..$r are $gap apart"
            }
        }
        // The focused (last) tile with its ring inside the panel, which scrolled to it.
        val ring = PlumDimens.focusRingOutset.value
        val focused = tiles.last()
        if (focused.left.value - ring < panel.left.value - TOLERANCE || focused.right.value + ring > panel.right.value + TOLERANCE) problems += "$at: the focused ring leaves the panel across"
        if (focused.bottom.value > panel.bottom.value + TOLERANCE) problems += "$at: the focused tile ends at ${focused.bottom}, the panel at ${panel.bottom}"
        // Names: at most two lines, broken between words only, never cut.
        val pictureWidth = pictures.firstOrNull()?.width ?: 0
        for (node in compose.onAllNodesWithTag(SettingsTags.COLOR_TILE_NAME, useUnmergedTree = true).fetchSemanticsNodes()) {
            nameProblem(node, pictureWidth)?.let { problems += "$at: $it" }
        }
        return problems
    }

    private fun near(
        a: Float,
        b: Float,
    ) = abs(a - b) <= TOLERANCE

    private companion object {
        const val TOLERANCE = 0.5f
    }
}

/** The name measured again at its tile's width (the semantics' layout is made without it): its problem, or null. */
internal fun nameProblem(
    node: SemanticsNode,
    tileWidthPx: Int,
): String? {
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    val input = layouts.single().layoutInput
    val text = node.config[SemanticsProperties.Text].joinToString("")
    val natural = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection).measure(input.text, input.style, constraints = Constraints(maxWidth = tileWidthPx))
    if (natural.lineCount > 2) return "'$text' needs ${natural.lineCount} lines"
    for (line in 0 until natural.lineCount - 1) {
        val end = natural.getLineEnd(line, visibleEnd = true)
        if (end < text.length && !text[end].isWhitespace()) return "'$text' is broken inside a word at $end"
    }
    if (natural.size.height > node.size.height + 1) return "'$text' needs ${natural.size.height} px, has ${node.size.height}"
    return null
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorsPanelFit768Test : SettingsColorsPanelFitBase() {
    @Test fun tilesFitAndLineUp() = tilesFitAndLineUp("768x432")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorsPanelFit576Test : SettingsColorsPanelFitBase() {
    @Test fun tilesFitAndLineUp() = tilesFitAndLineUp("576x432")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w960dp-h540dp-land-xhdpi")
class SettingsColorsPanelFitThorTest : SettingsColorsPanelFitBase() {
    @Test fun tilesFitAndLineUp() = tilesFitAndLineUp("960x540")
}
