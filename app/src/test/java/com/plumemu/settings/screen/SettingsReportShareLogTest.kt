package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.report.ReportItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Report a problem's Share log: what Export logs did, by keys and by touch, the form left as it is. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class SettingsReportShareLogTest : SettingsModelHarness() {
    private fun openForm() {
        model.content = SettingsContent(hasStatistics = true)
        openSection(SettingsSection.About)
        focusOption(SettingsOption.ReportProblem)
        press(Action.Enter)
        assertEquals(PanelPage.ProblemReport, model.focus.page)
    }

    private val form get() = checkNotNull(model.report.form)

    private fun toShareLog() {
        repeat(form.items.size) { if (form.focus != ReportItem.ShareLog) move(Direction.Down) }
        assertEquals(ReportItem.ShareLog, form.focus)
    }

    @Test
    fun aSharesTheLogAndTheFormStays() {
        openForm()
        toShareLog()
        press(Action.Enter)
        assertEquals(1, actions.logExports)
        assertNull(model.work.notice)
        assertEquals(PanelPage.ProblemReport, model.focus.page)
    }

    @Test
    fun aFailureIsSaidInTheForm() {
        actions.canShareLogs = false
        openForm()
        toShareLog()
        press(Action.Enter)
        assertEquals(SettingsOption.ReportProblem to SettingsNotice.ExportLogsFailed, model.work.notice)
    }

    @Test
    fun aLaterShareThatWorksClearsTheFailure() {
        actions.canShareLogs = false
        openForm()
        toShareLog()
        press(Action.Enter)
        actions.canShareLogs = true
        press(Action.Enter)
        assertEquals(2, actions.logExports)
        assertNull(model.work.notice)
    }

    @Test
    fun aTapSharesTheLog() {
        openForm()
        model.onTapPanelItem(form.items.indexOf(ReportItem.ShareLog), actions.shown, wide)
        assertEquals(1, actions.logExports)
    }

    @Test
    fun withNoStatisticsServerExportLogsIsTheWay() {
        model.content = SettingsContent(hasStatistics = false)
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.ExportLogs)
        press(Action.Enter)
        assertEquals(1, actions.logExports)
    }
}
