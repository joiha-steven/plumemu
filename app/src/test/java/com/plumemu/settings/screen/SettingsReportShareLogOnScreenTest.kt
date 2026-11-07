package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.report.ProblemReportForm
import com.plumemu.report.ReportItem
import com.plumemu.report.ReportTags
import com.plumemu.textsize.notFullyShown
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Report a problem's Share log at every text size: a failed share is read on the screen next to the button, not just stored,
 * and Send, Cancel and Share log are one width.
 */
abstract class SettingsReportShareLogOnScreenBase(
    private val shotSize: ReferenceShots.Size,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val geometry = SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)
    private val failure get() = ApplicationProvider.getApplicationContext<Context>().getString(R.string.settings_export_logs_failed)

    private fun modelWithForm() =
        // NoActions answers Share log with a failure.
        SettingsModel(NoActions, panelAt(SettingsOption.ReportProblem, PanelPage.ProblemReport, 0)).also {
            it.content = SettingsContent().withGamesListed()
            it.report.openWith(ProblemReportForm(KeyboardChoice.Plumemu))
        }

    private fun SettingsModel.press(action: Action) {
        onAction(action, PlumSettings(), geometry)
        compose.waitForIdle()
    }

    private fun shareFromTheButton(model: SettingsModel) {
        val form = checkNotNull(model.report.form)
        repeat(form.items.size) { if (form.focus != ReportItem.ShareLog) model.press(Action.Move(Direction.Down)) }
        model.press(Action.Enter)
    }

    private fun eachSize(check: (TextSize, SettingsModel) -> Unit) {
        var size by mutableStateOf(TextSize.Medium)
        var model by mutableStateOf(modelWithForm())
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                SettingsLayout(settingsForShot(), model, NoTouch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" })
            }
        }
        for (each in TextSize.entries) {
            size = each
            model = modelWithForm()
            compose.waitForIdle()
            check(each, model)
        }
    }

    /** Keys, not a focus set in advance: a shot taken at once stands before the form's scroll. */
    @Test
    fun shots() {
        var preset by mutableStateOf(ReferenceShots.Preset.Graphite)
        var model by mutableStateOf(modelWithForm())
        compose.setContent {
            PlumTheme(preset.settings) { SettingsLayout(settingsForShot(), model, NoTouch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" }) }
        }
        for (each in ReferenceShots.Preset.entries) {
            preset = each
            model = modelWithForm()
            compose.waitForIdle()
            shareFromTheButton(model)
            compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/${ReferenceShots.fileName("settings_report_share_log_failed", shotSize, each)}")
        }
    }

    @Test
    fun aFailedShareIsOnTheScreenByTheButton() {
        val problems = mutableListOf<String>()
        eachSize { size, model ->
            shareFromTheButton(model)
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val line = compose.onNodeWithText(failure, useUnmergedTree = true).fetchSemanticsNode()
            val button = compose.onNodeWithTag(ReportTags.item(ReportItem.ShareLog), useUnmergedTree = true).fetchSemanticsNode()
            notFullyShown(line, root)?.let { problems += "$size: the failure $it" }
            notFullyShown(button, root)?.let { problems += "$size: the button $it" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun theThreeButtonsAreOneWidth() {
        val problems = mutableListOf<String>()
        eachSize { size, _ ->
            val widths = listOf(ReportItem.Send, ReportItem.Leave, ReportItem.ShareLog).map { compose.onNodeWithTag(ReportTags.item(it), useUnmergedTree = true).fetchSemanticsNode().size.width }
            if (widths.toSet().size != 1) problems += "$size: $widths"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsReportShareLogOnScreen576Test : SettingsReportShareLogOnScreenBase(ReferenceShots.Size.Narrow)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class SettingsReportShareLogOnScreen640Test : SettingsReportShareLogOnScreenBase(ReferenceShots.Size.FourThree)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsReportShareLogOnScreen768Test : SettingsReportShareLogOnScreenBase(ReferenceShots.Size.Wide)
