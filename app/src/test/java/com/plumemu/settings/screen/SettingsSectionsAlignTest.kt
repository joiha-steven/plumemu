package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
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
 * The Settings sections column lines up with the page, like the home systems column:
 * every section label starts on the left line of the title text and the first hint key cap (the page
 * margin), focused or not; the focus dot sits in the margin, inside the selector; the selector starts at
 * x = 0 (square left corners, rounded right ones) and ends where it always did (margin + 200); the option
 * panel on the right has not moved. At the four reference sizes and all six text sizes.
 */
abstract class SettingsSectionsAlignBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun unclipped(matcher: SemanticsMatcher): DpRect = compose.onNode(matcher, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun inFocusedRow(tag: String) = hasTestTag(tag) and hasAnyAncestor(hasTestTag(SettingsTags.section(FOCUSED)))

    protected fun check(label: String) {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = FOCUSED), content = ShotContent.library)
            }
        }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val at = "$label ${each.name}"
            fun expect(what: String, ok: Boolean, numbers: String) {
                if (!ok) problems += "$at: $what ($numbers)"
            }
            fun near(a: Float, b: Float) = kotlin.math.abs(a - b) <= TOLERANCE
            val header = unclipped(hasText("Settings") and !hasAnyAncestor(hasTestTag(SettingsTags.STRIP))).left.value
            val hint = unclipped(hasTestTag(SettingsTags.HINT_CONFIRM)).left.value
            val labels = compose.onAllNodesWithTag(SystemRowTags.LABEL, useUnmergedTree = true)
            val lefts = labels.fetchSemanticsNodes().indices.map { labels[it].getUnclippedBoundsInRoot().left.value }
            val focusedLabel = unclipped(inFocusedRow(SystemRowTags.LABEL)).left.value
            val pill = unclipped(inFocusedRow(SystemRowTags.PILL))
            val labelBox = unclipped(inFocusedRow(SystemRowTags.LABEL))
            val panelLeft = unclipped(hasTestTag(SettingsTags.PANEL)).left.value
            val pinned = PANEL_LEFT[label]
            expect("header = hint key cap", near(header, hint), "header $header, hint $hint")
            expect("every section's label", lefts.size == SettingsSection.entries.size, "${lefts.size} of ${SettingsSection.entries.size}")
            expect("every label = header", lefts.all { near(it, header) }, "header $header, labels ${lefts.distinct()}")
            expect("focused label = header", near(focusedLabel, header), "focused $focusedLabel, header $header")
            val inset = (PlumDimens.stripMarginSide - PlumDimens.gameRowPadding).value
            expect("pill inset from the screen edge", near(pill.left.value, inset), "pill left ${pill.left.value}, inset $inset")
            expect("pill right = margin + 200", near(pill.right.value, (PlumDimens.stripMarginSide + PlumDimens.settingsSectionsWidth).value), "pill right ${pill.right.value}")
            expect("label is a game row's padding from the pill's left", near(focusedLabel - pill.left.value, PlumDimens.gameRowPadding.value), "${focusedLabel - pill.left.value}")
            expect("label centred on the pill (baseline as unfocused)", near((labelBox.top.value + labelBox.bottom.value) / 2, (pill.top.value + pill.bottom.value) / 2), "label ${labelBox.top.value}..${labelBox.bottom.value}, pill ${pill.top.value}..${pill.bottom.value}")
            expect("option panel has not moved", pinned != null && near(panelLeft, pinned), "panel left $panelLeft, pinned $pinned")
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** A finger on the bled-out part of a row, 4 dp from the screen edge, selects that section. */
    protected fun tapAtScreenEdge() {
        val tapped = mutableListOf<SettingsSection>()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                val model = remember { SettingsModel(NoActions, SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Library)).also { it.content = ShotContent.library } }
                SettingsLayout(
                    settingsForShot(), model, SettingsTouch({ tapped += it }, {}, {}, {}, {}, {}, {}), TEST_VERSION,
                    clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY },
                )
            }
        }
        val row = compose.onNodeWithTag(SettingsTags.section(FOCUSED)).getUnclippedBoundsInRoot()
        // In root coordinates: the finger lands 4 dp from the screen edge, left of where the row used to start.
        compose.onRoot().performTouchInput { click(Offset(4.dp.toPx(), ((row.top + row.bottom) / 2).toPx())) }
        assertEquals("a tap at x = 4 dp", listOf(FOCUSED), tapped)
    }

    companion object {
        const val TOLERANCE = 0.5f
        internal val FOCUSED = SettingsSection.Emulators

        /** Where the option panel's left edge was before this change, by screen size (measured on main; the same at all six text sizes). 667 was 891 before: with a section focused the options column now ends at the right margin (PlumLayout.settingsOptionsPreviewWidth) so the section preview's values are no longer cut, and the panel after it moved to the screen edge. */
        val PANEL_LEFT: Map<String, Float> = mapOf("576x432" to 800f, "667x375" to 667f, "768x432" to 720f, "837x471" to 789f)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsSectionsAlign768Test : SettingsSectionsAlignBase() {
    @Test fun labelsTitleAndHintsShareOneLeftLine() = check("768x432")

    @Test fun aTapAtTheScreenEdgeSelectsTheSection() = tapAtScreenEdge()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class SettingsSectionsAlign837Test : SettingsSectionsAlignBase() {
    @Test fun labelsTitleAndHintsShareOneLeftLine() = check("837x471")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SettingsSectionsAlign667Test : SettingsSectionsAlignBase() {
    @Test fun labelsTitleAndHintsShareOneLeftLine() = check("667x375")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsSectionsAlign576Test : SettingsSectionsAlignBase() {
    @Test fun labelsTitleAndHintsShareOneLeftLine() = check("576x432")
}
