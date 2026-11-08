package com.plumemu.textsize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SettingsColumns
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsColumn
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.ShotContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings' columns by text size on a wide screen: three up to Large, two above (`PlumLayout.isSettingsThreeColumns`), and in
 * three columns every text of the sections column, every option row and every right-column panel is whole (no ellipsis, nothing
 * cut sideways, nothing on top of another) . One class per language and screen: English, Vietnamese, French and
 * Spanish at 768x432 and 832x468.
 */
abstract class SettingsColumnsFitBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun threeColumnsWhereTheyFitAndTwoAbove() {
        var size by mutableStateOf(TextSize.Small)
        var three = false
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                three = PlumTheme.layout.isSettingsThreeColumns
                SettingsAt(SettingsFocus(section = SettingsSection.Appearance))
            }
        }
        for (eachFont in listOf(PlumFont.Jost)) {
            for (each in TextSize.entries) {
                size = each
                compose.waitForIdle()
                assertEquals("three columns at $each in ${eachFont.name}", SettingsColumns.fits(each, eachFont), three)
                assertEquals("the divider is drawn in three columns only, at $each in ${eachFont.name}", three, hasDivider())
            }
        }
    }

    /** Every section, every option row of it focused (and the sections themselves focused), at each text size that uses three columns, . */
    @Test
    fun everythingIsWholeInThreeColumns() {
        var size by mutableStateOf(TextSize.Small)
        var focus by mutableStateOf(SettingsFocus(section = SettingsSection.Library))
        val content = ShotContent.library
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SettingsAt(focus, content = content) } }
        val stops = mutableListOf<SettingsFocus>()
        for (section in SettingsSection.shownIn(content)) {
            stops += SettingsFocus(column = SettingsColumn.Sections, section = section)
            val rows = section.rowsIn(content)
            if (rows.isEmpty()) stops += SettingsFocus(column = SettingsColumn.Options, section = section)
            rows.indices.forEach { stops += SettingsFocus(column = SettingsColumn.Options, section = section, rememberedOption = mapOf(section to it)) }
        }
        val failures = mutableListOf<String>()
        // The lists scroll and a right-column panel may drop what does not fit below (the colour name, sample rows): only sideways cuts count there.
        val allow = FitAllowance(scrollTags = setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, SettingsTags.PANEL))
        for (eachFont in listOf(PlumFont.Jost)) {
            for (each in TextSize.entries.filter { SettingsColumns.fits(it, eachFont) }) {
                size = each
                for (stop in stops) {
                    focus = stop
                    compose.waitForIdle()
                    val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
                    val label = "${stop.section}/${stop.column}/${stop.rememberedOption[stop.section] ?: 0} at ${each.name} ${eachFont.name}"
                    textProblems(root, allow).mapTo(failures) { "$label: $it" }
                }
            }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.distinct().take(40).joinToString("\n"), failures.isEmpty())
    }

    private fun hasDivider() = compose.onAllNodesWithTagUnmerged(SettingsTags.SECTIONS_DIVIDER)
}

private fun ComposeContentTestRule.onAllNodesWithTagUnmerged(tag: String): Boolean =
    onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColumnsFit768EnglishTest : SettingsColumnsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w768dp-h432dp-land-xhdpi")
class SettingsColumnsFit768VietnameseTest : SettingsColumnsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w768dp-h432dp-land-xhdpi")
class SettingsColumnsFit768FrenchTest : SettingsColumnsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w768dp-h432dp-land-xhdpi")
class SettingsColumnsFit768SpanishTest : SettingsColumnsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class SettingsColumnsFit832EnglishTest : SettingsColumnsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w832dp-h468dp-land-xhdpi")
class SettingsColumnsFit832VietnameseTest : SettingsColumnsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w832dp-h468dp-land-xhdpi")
class SettingsColumnsFit832FrenchTest : SettingsColumnsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w832dp-h468dp-land-xhdpi")
class SettingsColumnsFit832SpanishTest : SettingsColumnsFitBase()
