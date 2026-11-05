package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
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

/**
 * Settings fills the page between its margins while a section is focused: the sections, the section's rows and, on a wide screen, the right
 * column with the first row's help in it, so no third of the page is left empty. Measured as text: the rightmost text of the page reaches the
 * right column's text line, and some text stands in the last fifth of the page. At 1920x1080 on the Pocket 6 (853x480 dp) and 768x432; 576x432 is the narrow two-column layout, which this change leaves as it was.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsFillsWidthTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check(sizes: List<TextSize>) {
        var size by mutableStateOf(TextSize.Small)
        var section by mutableStateOf(SettingsSection.Library)
        var density = Density(1f)
        var widthDp = 0f
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                density = LocalDensity.current
                widthDp = PlumTheme.layout.width.value
                SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = section), content = ShotContent.library)
            }
        }
        val problems = mutableListOf<String>()
        for (each in sizes) {
            // Statistics has no right column by design (its tiles span the page).
            for (which in SettingsSection.shownIn(ShotContent.library).filter { it != SettingsSection.Statistics }) {
                size = each
                section = which
                compose.waitForIdle()
                val dp = with(density) { 1.dp.toPx() }
                val strip = compose.onNodeWithTag(SettingsTags.STRIP, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val cap = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
                val inStrip = cap.filter { it.boundsInRoot.top >= strip.top && it.boundsInRoot.bottom <= strip.bottom && it.boundsInRoot.width > 0f }
                val rightmost = inStrip.maxOfOrNull { it.boundsInRoot.right } ?: 0f
                val widest = inStrip.maxOfOrNull { it.boundsInRoot.left } ?: 0f
                val page = widthDp * dp
                val margin = PlumDimens.stripMarginSide.value * dp
                // Some text starts in the last 30 % of the page (the right column, or the values of a column that runs to the margin) and the text reaches the margin's neighbourhood.
                if (widest < page * 0.7f) problems += "$each, $which: text starts no further right than ${(widest / dp).toInt()} dp of ${widthDp.toInt()}"
                if (rightmost < page - margin - 48 * dp) problems += "$each, $which: the rightmost text ends at ${(rightmost / dp).toInt()} dp of ${widthDp.toInt()}"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    @Config(qualifiers = "w853dp-h480dp-land-xhdpi")
    fun at1920x1080OnThePocket6() = check(listOf(TextSize.Small, TextSize.Large))

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun at768x432() = check(listOf(TextSize.Small, TextSize.Large))
}
