package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.graph.LibraryResetState
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
 * The two-button confirms that have no list (Reset all settings, Reset library, also when the reset
 * could not be done, and Remove folder) at Largest in Plum Sans and Plum Serif: the focused button,
 * ring included, is whole inside the panel; after a reset, with nothing focused there, the buttons
 * under the result line can be scrolled to (the panel scrolls rather than squeezing them).
 */
abstract class SettingsConfirmFitBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private class Case(val label: String, val focus: SettingsFocus, val done: SettingsOption? = null, val status: SettingsStatus = SettingsStatus())

    private val cases =
        listOf(
            Case("Reset all settings", panelAt(SettingsOption.ResetAll, PanelPage.Confirm, SettingsModel.CONFIRM_RESET)),
            Case("Reset library", panelAt(SettingsOption.ResetLibrary, PanelPage.Confirm, SettingsModel.CONFIRM_RESET)),
            Case(
                "Reset library, could not be reset",
                panelAt(SettingsOption.ResetLibrary, PanelPage.Confirm, SettingsModel.CONFIRM_RESET),
                done = SettingsOption.ResetLibrary,
                status = SettingsStatus(libraryReset = LibraryResetState.Failed),
            ),
            Case("Remove folder", panelAt(SettingsOption.RomFolders, PanelPage.RemoveFolder, SettingsModel.CONFIRM_RESET).copy(folderId = 2)),
        )

    protected fun theFocusedButtonFits(label: String) {
        var theme by mutableStateOf(PlumThemeSettings.Graphite)
        var case by mutableStateOf(cases.first())
        var model by mutableStateOf(SettingsModel(NoActions, case.focus))
        compose.setContent {
            PlumTheme(theme) {
                SettingsLayout(settingsForShot(), model, NoTouch, TEST_VERSION, { BOARD_CLOCK }, { BOARD_BATTERY }, status = case.status)
            }
        }
        val problems = mutableListOf<String>()
        for (font in listOf(PlumFont.Sans, PlumFont.Serif)) {
            for (each in cases) {
                theme = PlumThemeSettings.Graphite.copy(font = font, textSize = TextSize.Largest)
                case = each
                // A confirm opens on Cancel; Reset is reached by a tap, as a person does. A panel composed with
                // Reset already focused scrolls nothing (no layout yet when its request runs), which is not the app's path.
                val opensOnCancel = each.done == null && each.focus.panelIndex == SettingsModel.CONFIRM_RESET
                model = SettingsModel(NoActions, if (opensOnCancel) each.focus.copy(panelIndex = SettingsModel.CONFIRM_CANCEL) else each.focus).also { it.content = ShotContent.library }
                compose.waitForIdle()
                if (opensOnCancel) {
                    compose.runOnIdle { model.onTapPanelItem(SettingsModel.CONFIRM_RESET, PlumSettings(), SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)) }
                    compose.waitForIdle()
                    assertEquals("${font.name} ${each.label}: a tap on Reset focuses it", SettingsModel.CONFIRM_RESET, model.focus.panelIndex)
                }
                // The reset was asked for (A on Reset): its result line shows above the buttons. On a wide
                // screen the panel stays beside the options; a narrow one has no panel then.
                if (each.done != null) {
                    if (label.startsWith("576")) continue
                    compose.runOnIdle { model.onAction(Action.Enter, PlumSettings(), SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)) }
                    compose.waitForIdle()
                }
                val at = "$label ${font.name} ${each.label}"
                val panel = compose.onNodeWithTag(SettingsTags.PANEL, useUnmergedTree = true).getUnclippedBoundsInRoot()
                val outset = PlumDimens.focusRingOutset.value
                if (each.done == null) {
                    // The focused button, ring included, is whole inside the panel (the panel scrolls it into view).
                    val button = compose.onNodeWithTag(SettingsTags.panelItem(each.focus.panelIndex), useUnmergedTree = true).getUnclippedBoundsInRoot()
                    println("CONFIRMFIT $at: focused button ${button.top}..${button.bottom}, panel ${panel.top}..${panel.bottom}")
                    if (button.bottom.value + outset > panel.bottom.value + TOLERANCE) problems += "$at: the focused button's ring ends at ${button.bottom.value + outset}, the panel at ${panel.bottom}"
                    if (button.top.value - outset < panel.top.value - TOLERANCE) problems += "$at: the focused button's ring starts at ${button.top.value - outset}, above the panel's ${panel.top}"
                } else {
                    // Nothing focused in the panel: the buttons may sit below its bottom only inside a scroll that reaches them.
                    val scroll = compose.onNodeWithTag(SettingsTags.panelItem(SettingsModel.CONFIRM_RESET), useUnmergedTree = true)
                    val button = scroll.getUnclippedBoundsInRoot()
                    println("CONFIRMFIT $at: Reset button ${button.top}..${button.bottom}, panel ${panel.top}..${panel.bottom}")
                    scroll.performScrollTo()
                    compose.waitForIdle()
                    val scrolled = scroll.getUnclippedBoundsInRoot()
                    if (scrolled.bottom.value > panel.bottom.value + TOLERANCE) problems += "$at: the Reset button ends at ${scrolled.bottom} even scrolled, the panel at ${panel.bottom}"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val TOLERANCE = 0.5f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsConfirmFit768Test : SettingsConfirmFitBase() {
    @Test fun theFocusedButtonFits() = theFocusedButtonFits("768x432")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsConfirmFit576Test : SettingsConfirmFitBase() {
    @Test fun theFocusedButtonFits() = theFocusedButtonFits("576x432")
}
