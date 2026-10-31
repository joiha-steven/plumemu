package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.report.ProblemReportForm
import com.plumemu.report.ReportShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › About › Report a problem at 768x432 and 576x432, Graphite
 * and Paper: the empty form, with the tray up, a text and an e-mail at their longest, the switch
 * off with statistics off, an empty Send, sending, and each answer. Plumemu's keyboard (the default).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsReportScreenshotTest {
    private fun shot(
        name: String,
        state: ReportShots.State,
        statisticsOn: Boolean = true,
    ) = ReferenceShots.capture(name) { ReportSettings(state, statisticsOn) }

    @Test fun empty() = shot("settings_report_empty", ReportShots.State.Empty)

    @Test fun typing() = shot("settings_report_typing", ReportShots.State.Typing)

    @Test fun longText() = shot("settings_report_long_text", ReportShots.State.LongText)

    @Test fun noDetailsStatisticsOff() = shot("settings_report_no_details", ReportShots.State.NoDetails, statisticsOn = false)

    @Test fun noText() = shot("settings_report_no_text", ReportShots.State.NoText)

    @Test fun sending() = shot("settings_report_sending", ReportShots.State.Sending)

    @Test fun saved() = shot("settings_report_saved", ReportShots.State.Saved)

    @Test fun sent() = shot("settings_report_sent", ReportShots.State.Sent)

    @Test fun refused() = shot("settings_report_refused", ReportShots.State.Refused)
}

/** Settings with the report form open in About's panel, in [state]. */
@Composable
internal fun ReportSettings(
    state: ReportShots.State,
    statisticsOn: Boolean = true,
) {
    ReportSettingsWith(remember(state) { ReportShots.form(state) }, statisticsOn)
}

/** Settings with [form] open in About's panel, as it is. */
@Composable
internal fun ReportSettingsWith(
    form: ProblemReportForm,
    statisticsOn: Boolean = true,
) {
    val shown: PlumSettings = settingsForShot().let { it.copy(advanced = it.advanced.copy(usageStatistics = statisticsOn)) }
    val model =
        remember(form) {
            SettingsModel(NoActions, panelAt(SettingsOption.ReportProblem, PanelPage.ProblemReport, 0)).also { it.report.openWith(form) }
        }
    CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
        SettingsLayout(shown, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
    }
}
