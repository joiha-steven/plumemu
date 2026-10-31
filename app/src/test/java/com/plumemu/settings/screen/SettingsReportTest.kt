package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TypedKey
import com.plumemu.report.ReportItem
import com.plumemu.telemetry.ReportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › About › Report a problem through the settings model: opening, typing, Send, the answers, leaving. */
internal class SettingsReportTest : SettingsModelHarness() {
    private val form get() = model.report.form

    private fun openForm() {
        openSection(SettingsSection.About)
        focusOption(SettingsOption.ReportProblem)
        press(Action.Enter)
    }

    private fun down() = press(Action.Move(Direction.Down))

    @Test
    fun theRowIsLastInAboutAndOpensTheForm() {
        assertEquals(SettingsOption.ReportProblem, SettingsSection.About.options.last())
        assertEquals(OptionKind.Opens, SettingsOption.ReportProblem.kind)
        openForm()
        assertEquals(PanelPage.ProblemReport, model.focus.page)
        assertEquals(SettingsColumn.Panel, model.focus.column)
        assertNotNull(form)
    }

    @Test
    fun typedWordsAreSentOnceWithTheDetailsSwitchAndTheAnswerShowsInTheForm() {
        openForm()
        press(Action.Enter) // A on the text: the tray
        press(Action.Typed(TypedKey.Text("grid is blank")))
        assertEquals("grid is blank", form!!.text)
        press(Action.Back) // puts the tray away
        down(); down(); down()
        assertEquals(ReportItem.Send, form!!.focus)
        press(Action.Enter)
        assertEquals(listOf(Triple("grid is blank", null, true)), actions.reports)
        assertTrue(form!!.isSending)
        press(Action.Enter) // while sending
        assertEquals(1, actions.reports.size)
        actions.reportAnswers.single()(ReportResult.Saved)
        assertTrue(form!!.isFinished)
        assertNull(model.work.notice)
        press(Action.Enter) // Done
        assertNull(form)
        assertEquals(SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun anEmptyReportIsNotSent() {
        openForm()
        down(); down(); down()
        press(Action.Enter)
        assertTrue(actions.reports.isEmpty())
        assertEquals(com.plumemu.report.ReportMistake.NoText, form!!.mistake)
    }

    @Test
    fun backLeavesWithoutSending() {
        openForm()
        press(Action.Back)
        assertNull(form)
        assertTrue(actions.reports.isEmpty())
    }

    @Test
    fun cancelLeavesWithoutSending() {
        openForm()
        down(); down(); down(); down()
        assertEquals(ReportItem.Leave, form!!.focus)
        press(Action.Enter)
        assertNull(form)
    }

    @Test
    fun leavingWhileItSendsSaysTheAnswerUnderTheRow() {
        openForm()
        form!!.onTextChange("x")
        down(); down(); down()
        press(Action.Enter)
        press(Action.Back)
        assertNull(form)
        actions.reportAnswers.single()(ReportResult.Sent)
        assertEquals(SettingsOption.ReportProblem to SettingsNotice.ReportSent, model.work.notice)
        assertEquals(R.string.report_sent, SettingsNotice.ReportSent.text)
    }

    @Test
    fun aFormStillOpenGetsItsAnswerAndNoNotice() {
        openForm()
        form!!.onTextChange("x")
        down(); down(); down()
        press(Action.Enter)
        actions.reportAnswers.single()(ReportResult.Refused)
        assertFalse(form!!.isSending)
        assertEquals(ReportResult.Refused, form!!.result)
        assertNull(model.work.notice)
    }
}
