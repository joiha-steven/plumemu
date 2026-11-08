package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.MONO_FACE_FACTOR
import com.plumemu.coreui.theme.PlumFonts
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

/**
 * A game row's right-hand text in the list styles (the year at the 13 step, the region at the 11.33 step, 600 spaced 0.1 em, in columns
 * of 42.67 and 38 dp; both raised over the board's 10 and 8 dp on the Thor): the region is tracked caps 600 wherever it shows, alone or in its column, as is a system's
 * short name; a year alone is the column's year style; a row without a year keeps its region where a row with one has it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-mdpi")
class GameRowTrailingTest {
    // Small text: the board's sizes, at the one weight scale.
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        settings: PlumThemeSettings = PlumThemeSettings.Graphite,
    ) {
        compose.setContent {
            PlumTheme(settings.copy(textSize = TextSize.Small)) {
                Column(Modifier.width(400.dp)) {
                    GameRow(title = "Super Metroid", trailing = "1994", look = GameRowLook.Normal, region = "USA")
                    GameRow(title = "Star Fox", trailing = "", look = GameRowLook.Normal, region = "EUR")
                    GameRow(title = "Secret of Mana", trailing = "1993", look = GameRowLook.Focused, region = "JPN")
                    GameRow(title = "EarthBound", trailing = "WLD", look = GameRowLook.Normal, trailingIsCode = true)
                    GameRow(title = "Chrono Trigger", trailing = "1995", look = GameRowLook.Normal)
                    GameRow(title = "F-Zero", trailing = "SNES", look = GameRowLook.Focused, trailingIsCode = true)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun node(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().single()

    private fun styleOf(text: String): TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        node(text).config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.single().layoutInput.style
    }

    private fun assertCaps(
        text: String,
        weight: Int,
    ) {
        val style = styleOf(text)
        assertEquals("$text size (Mono 11 at the face factor)", (11f * MONO_FACE_FACTOR).sp, style.fontSize)
        assertEquals("$text weight", weight, style.fontWeight?.weight)
        assertEquals("$text family", PlumFonts.Mono, style.fontFamily)
        assertTrue("$text has no tracking (a mono face is already even)", style.letterSpacing.isUnspecified)
    }

    @Test
    fun aRegionIsTrackedCapsInItsColumnAndAlone() {
        show()
        assertCaps("USA", 500)
        assertCaps("EUR", 500)
        assertCaps("WLD", 500)
    }

    @Test
    fun aSystemsShortNameIsTheRegionsCaps() {
        show()
        // Focused on Graphite: Mono is never above its 500 face (the board's 600 and Paper's 700 are drawn at 500).
        assertCaps("SNES", 500)
    }

    @Test
    fun aYearAloneTakesTheColumnsYearStyle() {
        show()
        val alone = styleOf("1995")
        assertEquals("the year step (Mono 13 at the face factor)", (13f * MONO_FACE_FACTOR).sp, alone.fontSize)
        assertEquals(PlumFonts.Mono, alone.fontFamily)
        assertEquals(500, alone.fontWeight?.weight)
        assertEquals(styleOf("1994").fontSize, alone.fontSize)
        assertEquals("focused year (board 500)", 500, styleOf("1993").fontWeight?.weight)
    }

    /** The baseline of [text] in the root: its box's top plus its first line's baseline. */
    private fun baselineOf(text: String): Float {
        val layouts = mutableListOf<TextLayoutResult>()
        node(text).config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return node(text).boundsInRoot.top + layouts.single().getLineBaseline(0)
    }

    @Test
    fun aMonoYearAndRegionShareTheTitlesBaseline() {
        // Mono's year and tag sit on the Jost title's baseline (the row aligns them), at every text size, resting and focused.
        var size by mutableStateOf(TextSize.Small)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                Column(Modifier.width(500.dp)) {
                    GameRow(title = "Super Metroid", trailing = "1994", look = GameRowLook.Normal, region = "USA")
                    GameRow(title = "Secret of Mana", trailing = "1993", look = GameRowLook.Focused, region = "JPN")
                }
            }
        }
        for (step in TextSize.entries) {
            size = step
            compose.waitForIdle()
            for ((title, year, region) in listOf(Triple("Super Metroid", "1994", "USA"), Triple("Secret of Mana", "1993", "JPN"))) {
                assertEquals("$title year baseline at ${step.name}", baselineOf(title), baselineOf(year), 0.6f)
                assertEquals("$title region baseline at ${step.name}", baselineOf(title), baselineOf(region), 0.6f)
            }
        }
    }

    @Test
    fun aRowWithoutAYearKeepsItsRegionInTheColumn() {
        show()
        val withYear = node("USA").boundsInRoot
        val without = node("EUR").boundsInRoot
        assertEquals("region column, same box", withYear.left, without.left, 0.5f)
        assertEquals(withYear.right, without.right, 0.5f)
        // The year's right edge one region column (38 dp, wide enough for the Mono 11 caps) before the region's (mdpi: 1 px per dp).
        assertEquals(38f, withYear.right - node("1994").boundsInRoot.right, 0.5f)
        assertTrue("the region box is the 38 dp column", withYear.width in 37.5f..38.5f)
    }

    @Test
    fun paperDrawsTheFocusedRegionHeavier() {
        show(PlumThemeSettings.Paper)
        assertCaps("SNES", 500)
        assertCaps("JPN", 500)
        assertCaps("USA", 500)
    }

    @Test
    fun theFocusedWeightsFollowTheScale() {
        // Mono stays at 500 at every step of the scale.
        show()
        assertEquals(500, styleOf("1995").fontWeight?.weight)
        assertEquals("Mono never goes above its 500 face, focused or not", 500, styleOf("1993").fontWeight?.weight)
        assertEquals(styleOf("USA").fontWeight, styleOf("JPN").fontWeight)
    }
}
