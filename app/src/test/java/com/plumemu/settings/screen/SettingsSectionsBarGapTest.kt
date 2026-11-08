package com.plumemu.settings.screen

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.HINT_KEY_CAP_TAG
import com.plumemu.coreui.components.slabOverhang
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.footerGapToKeys
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.home.rulesAround
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sections column ends [footerGapToKeys] above the hint bar at every reference size: with the cursor on the last
 * section (About) the list has scrolled to its end, and its last row must not touch the bar. At Medium every row fits, so
 * each one is checked. The drawn edge counts, not the row's bounds: the focused slab is drawn [slabOverhang] past its row, and
 * that edge too stays clear of the hint keys' top. Every text size runs in Jost (three columns up to Large on wide screens,
 * two above), so both layouts are measured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsSectionsBarGapTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** Every text size, so the default (Large) is covered too, with and without a second screen (one more section); the list scrolls from some size on, then only the last row counts. */
    private fun check(secondScreen: Boolean) = checkList(secondScreen, options = false)

    /** The sections column not scrolled to its end (cursor on the first section, or an option focused: three columns keep the sections on screen), so rows run past the strip's bottom. */
    private fun checkUnscrolled(cursor: SectionsCursor) = checkList(false, options = false, cursor = cursor)

    private enum class SectionsCursor { Last, First, OptionFocused }

    /** [options]: the options column of Appearance with the cursor on its last row, instead of the sections column. */
    private fun checkList(
        secondScreen: Boolean,
        options: Boolean,
        cursor: SectionsCursor = SectionsCursor.Last,
    ) {
        var size by mutableStateOf(TextSize.Small)
        // With a second screen the list has one more section.
        val content = if (secondScreen) SettingsContent().withSecondScreen(SecondScreen(4, "Screen-2", ScreenKind.BuiltIn), SecondScreenSettings()) else SettingsContent()
        val sections = SettingsSection.shownIn(content)
        val last = sections.last()
        val lastOption = SettingsSection.Appearance.options.last()
        var density = Density(1f)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                density = LocalDensity.current
                SettingsAt(
                    when {
                        options -> optionsAt(lastOption)
                        cursor == SectionsCursor.First -> SettingsFocus(section = sections.first())
                        cursor == SectionsCursor.OptionFocused -> optionsAt(SettingsOption.Accent)
                        else -> SettingsFocus(section = last)
                    },
                    content = content,
                )
            }
        }
        val problems = mutableListOf<String>()
        for (eachFont in listOf(PlumFont.Jost)) for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val at = "$eachFont $each"
            // What the bar draws highest: the top of the hint keys' collars (lip and groove above the resting cap), not the bar's own bounds.
            val caps = compose.onAllNodesWithTag(HINT_KEY_CAP_TAG, useUnmergedTree = true).fetchSemanticsNodes()
            val barTop = with(density) { caps.minOf { it.boundsInRoot.top }.toDp() } - (CollarSize.Hint.lip + CollarSize.Hint.groove)
            val stripBottom = compose.onNodeWithTag(SettingsTags.STRIP).getUnclippedBoundsInRoot().bottom
            if (options) {
                val row = compose.onNodeWithTag(SettingsTags.option(lastOption)).getUnclippedBoundsInRoot()
                if (barTop - row.bottom < footerGapToKeys(each) - SLACK) problems += "$at: ${lastOption} ends ${(barTop - row.bottom).value} dp above the bar"
                val drawn = row.bottom + with(density) { slabOverhang((row.bottom - row.top).toPx(), density).toDp() }
                if (barTop - drawn < DRAWN_GAP) problems += "$at: ${lastOption}'s slab is drawn to ${(barTop - drawn).value} dp above the bar"
                continue
            }
            for (section in sections) {
                val row = compose.onNodeWithTag(SettingsTags.section(section)).getUnclippedBoundsInRoot()
                val gap = barTop - row.bottom
                // Rows below the strip's end are scrolled out (the list ends flush with the strip): only the last must be in.
                val counts = (section == last && cursor == SectionsCursor.Last) || row.bottom <= stripBottom
                // A row the strip cuts shows its top part in the 10 dp above the keys: it must not reach into the gap above them.
                val cutIn = row.top < stripBottom && row.bottom > stripBottom && stripBottom > barTop - footerGapToKeys(each) + SLACK
                if (cutIn) problems += "$at ${cursor.name}: $section is cut by the strip ending ${(barTop - stripBottom).value} dp above the bar"
                if (counts && gap < footerGapToKeys(each) - SLACK) problems += "$at ${cursor.name}: $section ends ${gap.value} dp above the bar"
                val drawn = row.bottom + with(density) { slabOverhang((row.bottom - row.top).toPx(), density).toDp() }
                if (section == last && cursor == SectionsCursor.Last && barTop - drawn < DRAWN_GAP) problems += "$at: $section's slab is drawn to ${(barTop - drawn).value} dp above the bar"
            }
        }
        assertTrue("needs the footer gap less the collars' reach:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    private companion object {
        val SLACK = 0.5.dp

        /** The drawn slab edge of the last row keeps at least this much clear of the keys. */
        val DRAWN_GAP = 4.dp
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun at832x468() = check(false)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun at832x468WithSecondScreen() = check(true)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun at768x432() = check(false)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun at768x432WithSecondScreen() = check(true)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun at576x432() = check(false)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun at576x432WithSecondScreen() = check(true)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun at640x480Nova() = check(false)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun at640x480NovaWithSecondScreen() = check(true)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun optionsAt832x468() = checkList(false, options = true)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun optionsAt768x432() = checkList(false, options = true)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun optionsAt576x432() = checkList(false, options = true)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun optionsAt640x480() = checkList(false, options = true)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun unscrolledFirstAt832x468() = checkUnscrolled(SectionsCursor.First)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun unscrolledOptionFocusedAt832x468() = checkUnscrolled(SectionsCursor.OptionFocused)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun unscrolledFirstAt768x432() = checkUnscrolled(SectionsCursor.First)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun unscrolledOptionFocusedAt768x432() = checkUnscrolled(SectionsCursor.OptionFocused)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun unscrolledFirstAt576x432() = checkUnscrolled(SectionsCursor.First)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun unscrolledOptionFocusedAt576x432() = checkUnscrolled(SectionsCursor.OptionFocused)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun unscrolledFirstAt640x480() = checkUnscrolled(SectionsCursor.First)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun unscrolledOptionFocusedAt640x480() = checkUnscrolled(SectionsCursor.OptionFocused)
}
