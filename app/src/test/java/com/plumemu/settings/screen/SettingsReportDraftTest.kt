package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TypedKey
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.telemetry.ReportResult
import com.plumemu.report.ReportItem
import com.plumemu.report.ReportStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Report a problem keeps what was typed until it is sent or saved: B, Cancel and Home (Settings closing) lose nothing. */
internal class SettingsReportDraftTest : SettingsModelHarness() {
    private val form get() = model.report.form

    private fun openForm() {
        openSection(SettingsSection.About)
        focusOption(SettingsOption.ReportProblem)
        press(Action.Enter)
    }

    private fun typeWords() {
        press(Action.Enter) // A on the text: the tray
        press(Action.Typed(TypedKey.Text("grid is blank")))
    }

    /** Settings closed and opened again: a new model over the same actions, as `remember(actions)` makes one. */
    private fun reopened() = SettingsModel(actions).report.open(KeyboardChoice.Android)

    @Test
    fun backKeepsTheWordsForTheNextOpening() {
        openForm()
        typeWords()
        press(Action.Back) // the tray
        press(Action.Back) // the form
        assertEquals("grid is blank", reopened().text)
    }

    @Test
    fun cancelKeepsTheWordsForTheNextOpening() {
        openForm()
        typeWords()
        press(Action.Back)
        repeat(4) { press(Action.Move(Direction.Down)) }
        press(Action.Enter) // Cancel
        assertEquals("grid is blank", reopened().text)
    }

    @Test
    fun homeKeepsTheWordsWithoutAnyLeavingStep() {
        openForm()
        typeWords()
        model.onDispose() // Settings closes with the form still open
        assertEquals("grid is blank", reopened().text)
    }

    @Test
    fun aSavedReportClearsTheDraft() {
        openForm()
        typeWords()
        press(Action.Back)
        repeat(3) { press(Action.Move(Direction.Down)) }
        press(Action.Enter) // Send
        actions.reportAnswers.single()(ReportResult.Saved)
        assertEquals("", reopened().text)
    }

    @Test
    fun aFailedSendKeepsTheDraft() {
        openForm()
        typeWords()
        press(Action.Back)
        repeat(3) { press(Action.Move(Direction.Down)) }
        press(Action.Enter)
        actions.reportAnswers.single()(ReportResult.Refused)
        assertEquals("grid is blank", reopened().text)
    }

    @Test
    fun theEmailIsKeptToo() {
        openForm()
        form!!.onEmailChange("a@b.co")
        press(Action.Back)
        assertEquals("a@b.co", reopened().email)
    }

    private fun sendAndLeave() {
        openForm()
        typeWords()
        press(Action.Back)
        repeat(3) { press(Action.Move(Direction.Down)) }
        press(Action.Enter) // Send
        press(Action.Back) // leaves while it is on its way
    }

    @Test
    fun typingOnAfterReopeningWithPlumemusKeyboardContinuesTheWords() {
        openForm()
        typeWords()
        press(Action.Back)
        press(Action.Back)
        val again = SettingsModel(actions).report.open(KeyboardChoice.Plumemu)
        again.onAction(Action.Enter)
        again.onAction(Action.Typed(TypedKey.Text("x")))
        assertEquals("grid is blankx", again.text)
    }

    @Test
    fun typingOnInTheEmailAfterReopeningContinuesTheAddress() {
        openForm()
        form!!.onEmailChange("a@b.co")
        press(Action.Back)
        val again = SettingsModel(actions).report.open(KeyboardChoice.Plumemu)
        again.onAction(Action.Move(Direction.Down))
        again.onAction(Action.Enter)
        again.onAction(Action.Typed(TypedKey.Text("m")))
        assertEquals("a@b.com", again.email)
    }

    @Test
    fun aFormOpenedWhileASendRunsWaitsAndCannotSendTwice() {
        sendAndLeave()
        val again = reopened()
        assertTrue(again.isSending)
        repeat(3) { again.onAction(Action.Move(Direction.Down)) }
        assertFalse(again.onAction(Action.Enter) is ReportStep.Submit)
        assertEquals(1, actions.reports.size)
    }

    @Test
    fun theAnswerOfASendMadeBeforeReachesTheFormOpenedAfterIt() {
        sendAndLeave()
        val again = reopened()
        actions.reportAnswers.single()(ReportResult.Sent)
        assertFalse(again.isSending)
        assertTrue(again.isFinished)
        assertEquals("", again.text)
        assertEquals("", reopened().text)
    }

    @Test
    fun aFailedSendUnlocksTheFormOpenedMeanwhileWithItsWords() {
        sendAndLeave()
        val again = reopened()
        actions.reportAnswers.single()(ReportResult.Refused)
        assertFalse(again.isSending)
        assertEquals("grid is blank", again.text)
        assertEquals(ReportItem.Send, again.focus)
    }

    @Test
    fun anAnswerClearsOnlyTheWordsThatWereSent() {
        openForm()
        typeWords()
        press(Action.Back)
        repeat(3) { press(Action.Move(Direction.Down)) }
        press(Action.Enter)
        val draft = actions.reportDraft
        draft.text = "something typed after"
        actions.reportAnswers.single()(ReportResult.Sent)
        assertEquals("something typed after", draft.text)
    }
}
