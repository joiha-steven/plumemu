package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.test.onNodeWithTag
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

/**
 * At Medium every section row is whole inside the strip with the cursor on the last section (no scroll, none cut) at the
 * reference sizes. At larger text sizes a row cut at the top or bottom has the list's edge fade over that edge, so a cut is never a hard line under the title
 * or above the hint bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsSectionsFitTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check(wholeAtMedium: Boolean = true) {
        var size by mutableStateOf(TextSize.Small)
        val sections = SettingsSection.shownIn(SettingsContent())
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                SettingsAt(SettingsFocus(section = sections.last()))
            }
        }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val strip = compose.onNodeWithTag(SettingsTags.STRIP).getUnclippedBoundsInRoot()
            val fadeTop = compose.onAllFades(SettingsTags.SECTIONS + FADE_TOP_SUFFIX)
            val fadeBottom = compose.onAllFades(SettingsTags.SECTIONS + FADE_BOTTOM_SUFFIX)
            for (section in sections) {
                val row = compose.onNodeWithTag(SettingsTags.section(section)).getUnclippedBoundsInRoot()
                if (each == TextSize.Small && wholeAtMedium) {
                    if (row.top < strip.top - SLACK || row.bottom > strip.bottom + SLACK) problems += "$each: $section is cut (${row.top.value}..${row.bottom.value} in ${strip.top.value}..${strip.bottom.value})"
                    continue
                }
                if (row.top < strip.top - SLACK && fadeTop == null) problems += "$each: $section is cut at the top with no fade"
                if (row.bottom > strip.bottom + SLACK && fadeBottom == null) problems += "$each: $section is cut at the bottom with no fade"
            }
        }
        assertTrue("sections not fitting:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    /** The fade's height, or null when the list shows no fade at that edge. */
    private fun ComposeContentTestRule.onAllFades(tag: String): Dp? {
        val nodes = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        if (nodes.isEmpty()) return null
        return onAllNodesWithTag(tag, useUnmergedTree = true)[0].getUnclippedBoundsInRoot().let { it.bottom - it.top }
    }

    private companion object {
        val SLACK = 0.5.dp
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun at832x468() = check()

    // Nine 40 dp rows need 360 dp and the list has 346 dp at 432 dp high: the list scrolls at Medium (a cut row still needs its fade).
    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun at768x432() = check(wholeAtMedium = false)

    // Same as 768x432: 360 dp of rows in 346 dp.
    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun at576x432() = check(wholeAtMedium = false)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun at640x480Nova() = check()
}
