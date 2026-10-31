package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.keyboard.aboveTray
import com.plumemu.report.ProblemReportBody
import com.plumemu.report.ProblemReportForm

/*
 * Settings › About › Report a problem: the row and its panel. The form
 * and its body are in the `report` package; the send is the telemetry module's `ProblemReports`.
 */

/** The report form open in the panel, if any; the model routes every action to it while [PanelPage.ProblemReport] has the focus. */
@Stable
internal class SettingsProblemReport {
    var form: ProblemReportForm? by mutableStateOf(null)
        private set

    /** A fresh form typed with [keyboard] (Settings › Controls › Keyboard when it opens). */
    fun open(keyboard: KeyboardChoice): ProblemReportForm = ProblemReportForm(keyboard).also { form = it }

    /** Shows [opened], a form already made (tests and shots). */
    fun openWith(opened: ProblemReportForm) {
        form = opened
    }

    fun close() {
        form = null
    }
}

/**
 * The row's panel: the form once the row is entered, else what the page is for (wide screens show
 * this beside the row; A opens the form).
 */
@Composable
internal fun ReportProblemPanel(
    model: SettingsModel,
    touch: SettingsTouch,
    settings: PlumSettings,
) {
    val form = model.report.form
    if (form != null && model.focus.page == PanelPage.ProblemReport) {
        ReportPage(form, settings.advanced.usageStatistics, touch)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(stringResource(R.string.settings_report_problem))
        PanelBody(stringResource(R.string.settings_report_problem_body))
    }
}

/**
 * The form under its title; scrolls when the text is large. While Plumemu's tray is up the scroll
 * ends at the tray's top, as the sign-in form's does.
 */
@Composable
private fun ReportPage(
    form: ProblemReportForm,
    statisticsOn: Boolean,
    touch: SettingsTouch,
) {
    val covers = settingsKeyboardCovers()
    val gap = PlumDimens.setupContentGap
    AccountPanelScroll(gap, Modifier.aboveTray({ form.isTrayUp }, covers)) {
        PanelTitle(stringResource(R.string.settings_report_problem))
        ProblemReportBody(form, statisticsOn, onTap = touch.onPanelItem, onAction = touch.onHint, gap = gap)
    }
}
