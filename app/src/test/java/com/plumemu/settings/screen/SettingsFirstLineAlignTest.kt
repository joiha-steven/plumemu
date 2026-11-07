package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import kotlin.math.abs
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import com.plumemu.home.barColumns
import com.plumemu.home.slabMiddle
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

    /** An opened section's rows fade the same way (seen cut by a hard line on the Pocket 6): at its first row only the bottom, at its last only the top. */
    @Test
    fun anOpenedSectionFadesWhereItContinues() {
        val last = SettingsSection.Appearance.rowsIn(ShotContent.library).lastIndex
        var row by mutableStateOf(0)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                key(row) {
                    SettingsAt(
                        SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Appearance, rememberedOption = mapOf(SettingsSection.Appearance to row)),
                        content = ShotContent.library,
                    )
                }
            }
        }
        fun shown(suffix: String) = compose.onAllNodesWithTag(SettingsTags.OPTION_LIST + suffix, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        for (at in listOf(0, last)) {
            row = at
            compose.waitForIdle()
            val range = compose.onNodeWithTag(SettingsTags.OPTION_LIST, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            val down = range.value() < range.maxValue()
            val up = range.value() > 0f
            assertTrue("row $at: Appearance scrolls at Largest on 576", down || up)
            assertEquals("row $at: bottom fade iff it can scroll down", down, shown(FADE_BOTTOM_SUFFIX))
            assertEquals("row $at: top fade iff it can scroll up", up, shown(FADE_TOP_SUFFIX))
        }
        row = last
        compose.waitForIdle()
        assertTrue("at the last row the top fades", shown(FADE_TOP_SUFFIX))
    }

    /** A fade never dims the focused row: Text size opens low in Appearance at the largest text with a row past it, and its slab edge is as bright at its bottom as at its top. */
    @Test
    fun aFadeStopsShortOfTheFocusedRow() {
        val row = SettingsSection.Appearance.rowsIn(ShotContent.library).indexOf(SettingsOption.TextSize)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                SettingsAt(SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Appearance, rememberedOption = mapOf(SettingsSection.Appearance to row)), content = ShotContent.library)
            }
        }
        compose.waitForIdle()
        assertTrue("the rows continue past Text weight", compose.onAllNodesWithTag(SettingsTags.OPTION_LIST + FADE_BOTTOM_SUFFIX, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val d = compose.density.density
        val box = compose.onNodeWithTag(SettingsTags.option(SettingsOption.TextSize), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val x = ((box.left.value + (box.right.value - box.left.value) * 0.4f) * d).toInt()
        // The slab's border: the bottom side is a lighter line over the fill's dark end, a little past the row's bounds (the slab may bleed).
        val xs = (x - 5)..(x + 5)
        val from = ((box.top.value - 8) * d).toInt().coerceAtLeast(0)
        val until = ((box.bottom.value + 8) * d).toInt().coerceAtMost(image.height - 1)
        val top = box.top.value * d
        assertTrue("the focused row's slab is drawn", compose.onRoot().captureToImage().slabMiddle(compose.barColumns(box.left.value * d), top.toInt(), until) >= 0)
        fun lum(y: Int) = image.getPixel(x, y).let { android.graphics.Color.red(it) + android.graphics.Color.green(it) + android.graphics.Color.blue(it) }
        val bottom = ((top + 2 * d).toInt()..until - 2).any { y -> lum(y) - lum(y - 2) > BOTTOM_EDGE_STEP && lum(y) - lum(y + 2) > BOTTOM_EDGE_STEP }
        assertTrue("the focused row's slab edge is drawn at its bottom (the fade does not dim it)", bottom)
    }
}

/** How much lighter (summed channels, 0..765) the slab's bottom border is than the fill above and the shadow under it, at the least. */
private const val BOTTOM_EDGE_STEP = 12
