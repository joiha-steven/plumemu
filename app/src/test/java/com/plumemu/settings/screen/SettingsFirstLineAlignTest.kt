package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
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

/**
 * The first text line of the sections column and of the options column share one baseline (audit
 * At Large the options' first line sat 2 dp lower, the sections' rows being 40 high and the options' 44).
 */
abstract class SettingsFirstLineAlignBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    protected fun check() {
        var size by mutableStateOf(TextSize.Large)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Library), content = ShotContent.library)
            }
        }
        val problems = mutableListOf<String>()
        for (each in listOf(TextSize.Large, TextSize.Huge, TextSize.Largest)) {
            size = each
            compose.waitForIdle()
            val sections = compose.onAllNodesWithTag(SystemRowTags.LABEL, useUnmergedTree = true)
            val texts = compose.onAllNodes(hasAnyAncestor(hasTestTag(SettingsTags.OPTIONS)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            val sectionTops = (0..1).map { sections[it].getUnclippedBoundsInRoot().top.value }
            val optionTops = (0..1).map { texts[it].getUnclippedBoundsInRoot().top.value }
            if (kotlin.math.abs(sectionTops[0] - optionTops[0]) > TOLERANCE) problems += "${each.name}: first section text $sectionTops, first option text $optionTops"
        }
        assertEquals(problems.joinToString("\n"), 0, problems.size)
    }

    private companion object {
        const val TOLERANCE = 0.5f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsFirstLineAlign768Test : SettingsFirstLineAlignBase() {
    @Test fun theFirstTextLinesShareABaseline() = check()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsFirstLineAlign576Test : SettingsFirstLineAlignBase() {
    @Test fun theFirstTextLinesShareABaseline() = check()
}

/** The sections column, taller rows since the first-line fix, shows it scrolls: a fade exactly while it can scroll that way. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsSectionsScrollCueTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theSectionsColumnFadesWhereItContinues() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Library), content = ShotContent.library)
            }
        }
        compose.waitForIdle()
        val range = compose.onNodeWithTag(SettingsTags.SECTIONS, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        val down = range.value() < range.maxValue()
        val up = range.value() > 0f
        assertTrue("the sections column scrolls at Largest on 576", down || up)
        fun shown(suffix: String) = compose.onAllNodesWithTag(SettingsTags.SECTIONS + suffix, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        assertEquals("bottom fade iff it can scroll down", down, shown(FADE_BOTTOM_SUFFIX))
        assertEquals("top fade iff it can scroll up", up, shown(FADE_TOP_SUFFIX))
    }
}
