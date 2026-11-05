package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.TextWeight
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
    // Medium text and the Thin weight setting: the board's sizes and weights as drawn.
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        settings: PlumThemeSettings = PlumThemeSettings.Graphite,
        weight: TextWeight = TextWeight.Thin,
    ) {
        compose.setContent {
            PlumTheme(settings.copy(textSize = TextSize.Small, textWeight = weight)) {
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
        assertEquals("$text size (the info-line step)", 11.33.sp, style.fontSize)
        assertEquals("$text weight", weight, style.fontWeight?.weight)
        assertEquals("$text tracking (board 1.2 / 12 px)", 0.1.em, style.letterSpacing)
    }

    @Test
    fun aRegionIsTrackedCapsInItsColumnAndAlone() {
        show()
        assertCaps("USA", 600)
        assertCaps("EUR", 600)
        assertCaps("WLD", 600)
    }

    @Test
    fun aSystemsShortNameIsTheRegionsCaps() {
        show()
        // Focused on Graphite: the board's region weight on the slab (600; Paper 700).
        assertCaps("SNES", 600)
    }

    @Test
    fun aYearAloneTakesTheColumnsYearStyle() {
        show()
        val alone = styleOf("1995")
        assertEquals("the year step", 13.sp, alone.fontSize)
        assertEquals(400, alone.fontWeight?.weight)
        assertEquals(styleOf("1994").fontSize, alone.fontSize)
        assertEquals("focused year (board 500)", 500, styleOf("1993").fontWeight?.weight)
    }

    @Test
    fun aRowWithoutAYearKeepsItsRegionInTheColumn() {
        show()
        val withYear = node("USA").boundsInRoot
        val without = node("EUR").boundsInRoot
        assertEquals("region column, same box", withYear.left, without.left, 0.5f)
        assertEquals(withYear.right, without.right, 0.5f)
        // The year's right edge one region column (38 dp, wide enough for the 11.33 caps) before the region's (mdpi: 1 px per dp).
        assertEquals(38f, withYear.right - node("1994").boundsInRoot.right, 0.5f)
        assertTrue("the region box is the 38 dp column", withYear.width in 37.5f..38.5f)
    }

    @Test
    fun paperDrawsTheFocusedRegionHeavier() {
        show(PlumThemeSettings.Paper)
        assertCaps("SNES", 700)
        assertCaps("JPN", 700)
        assertCaps("USA", 600)
    }

    @Test
    fun theFocusedWeightsFollowTheTextWeightSetting() {
        // The default weight setting moves every face one step: the focused year stays a face above its resting rows, the region as heavy.
        show(weight = TextWeight.Medium)
        assertEquals(500, styleOf("1995").fontWeight?.weight)
        assertEquals(600, styleOf("1993").fontWeight?.weight)
        assertEquals(styleOf("USA").fontWeight, styleOf("JPN").fontWeight)
    }
}
