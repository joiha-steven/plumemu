package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens
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
 * What Settings' columns draw at their TOP edge, the twin of [SettingsListEndsBase]'s bottom checks, at the reference sizes in both themes: a row
 * the scrolled column cuts at its top edge leaves no sliver when under a third of it shows (the column hides it whole), and when a third or more shows it
 * fades into the edge: the pixel rows at the edge are the page, no hard slice, and nothing of it shows above the window.
 */
abstract class SettingsTopEdgeBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var theme by mutableStateOf(TableText.Graphite)
    private var density = Density(1f)
    private var focus by mutableStateOf(SettingsFocus())

    private val themes = listOf("Graphite" to TableText.Graphite, "Paper" to TableText.Paper)

    private fun px(dp: Dp) = with(density) { dp.toPx() }

    private fun eachFocus(
        focuses: List<SettingsFocus>,
        sizes: List<TextSize>,
        each: (SettingsFocus, String) -> Unit,
    ) {
        compose.setContent {
            density = LocalDensity.current
            PlumTheme(theme) { SettingsAt(focus, content = ShotContent.library) }
        }
        for (size in sizes) {
            for (at in focuses) {
                focus = at
                for ((name, shown) in themes) {
                    theme = shown.copy(textSize = size)
                    compose.waitForIdle()
                    each(at, "$name ${size.name}")
                }
            }
        }
    }

    private val fixedSections = shownSections.filter { it.options.isNotEmpty() && it != SettingsSection.Emulators }

    private fun rowsOf(section: SettingsSection) = section.rowsIn(ShotContent.library)

    private fun optionFocus(
        section: SettingsSection,
        row: Int,
    ) = SettingsFocus(column = SettingsColumn.Options, section = section, rememberedOption = mapOf(section to row))

    /**
     * Focus on the last section and on the last row of each list: a row the scrolled column cuts at its top edge with under a third showing draws nothing there
     * (the page colour all across it), and one showing a third or more is faded at the edge, at the default text size and at Largest.
     */
    @Test
    fun aRowCutAtTheTopEdgeShowsNoSliverAndFadesIn() {
        val problems = mutableListOf<String>()
        val focuses =
            listOf(SettingsFocus(column = SettingsColumn.Sections, section = shownSections.last())) +
                fixedSections.map { optionFocus(it, rowsOf(it).lastIndex) }
        eachFocus(focuses, listOf(TextSize.Small, TextSize.Largest)) { at, name ->
            val inSections = at.column == SettingsColumn.Sections
            val list = compose.onNodeWithTag(if (inSections) SettingsTags.SECTIONS else SettingsTags.OPTION_LIST).getUnclippedBoundsInRoot()
            val tags = if (inSections) shownSections.map { SettingsTags.section(it) } else rowsOf(at.section).map { SettingsTags.option(it) }
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val top = px(list.top)
            val x0 = if (inSections) 6 else px(list.left + SlabDimens.glowReach * 2).toInt()
            // Short of the column's divider, which stands at its right end and is no row.
            val x1 = minOf(px(list.right).toInt(), pixels.width) - 1 - px(1.dp).toInt()
            for ((i, tag) in tags.withIndex()) {
                val row = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
                if (px(row.top) >= top - 0.5f || px(row.bottom) <= top + 0.5f) continue
                // Cut by the top edge. Under a third showing: from the edge to the row's bottom nothing of it may show. Else it fades in: the first dp is the page.
                val shown = (px(row.bottom) - top) / (px(row.bottom) - px(row.top))
                val sliver = shown < 1f / 3f - 0.02f
                val limit = if (sliver) BG_TOLERANCE else FADED_EDGE_TOLERANCE
                val to = if (sliver) px(row.bottom).toInt() else minOf(top.toInt() + px(1.dp).toInt() + 1, px(row.bottom).toInt())
                var bad = 0
                var where = ""
                // From just above the edge, where nothing of the row may show either.
                for (y in top.toInt() - ABOVE_EDGE_PX until to) {
                    val bg = pixels[1, y]
                    val allowed = if (y <= top.toInt()) BG_TOLERANCE else limit
                    var x = x0
                    while (x <= x1) {
                        if (stepBetween(pixels[x, y], bg) > allowed) {
                            bad++
                            if (where.isEmpty()) where = "x=${x}px y=${y}px"
                        }
                        x += 2
                    }
                }
                if (bad > 0) problems += "${if (inSections) "sections" else at.section.name} $name row $i: $bad pixels of a row cut at the top edge (${(shown * 100).toInt()}% shows) are not the page at the edge (first at $where)"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun stepBetween(a: Color, b: Color): Float = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    private companion object {
        const val BG_TOLERANCE = 3f / 255f

        /** The ramp is still almost opaque in the edge's first dp: what is left of a row's ink there is a few levels. */
        const val FADED_EDGE_TOLERANCE = 24f / 255f
        const val ABOVE_EDGE_PX = 4
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class SettingsTopEdge832Test : SettingsTopEdgeBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsTopEdge768Test : SettingsTopEdgeBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsTopEdge576Test : SettingsTopEdgeBase()
