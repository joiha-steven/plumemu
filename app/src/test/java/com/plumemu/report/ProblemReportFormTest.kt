package com.plumemu.report

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreinput.TypedKey
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.telemetry.ProblemReports
import com.plumemu.telemetry.ReportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Report a problem form without a screen: the items and their
 * moves, the right and wrong ways to Send, the 4,000-character cap, the answers, and Plumemu's
 * keyboard on both fields ("Next", "Done" that does not send).
 */
class ProblemReportFormTest {
    private val form = ProblemReportForm(KeyboardChoice.Plumemu)
    private val down = Action.Move(Direction.Down)
    private val up = Action.Move(Direction.Up)

    private fun send(): ReportStep {
        while (form.focus != ReportItem.Send) form.onAction(down)
        return form.onAction(Action.Enter)
    }

    @Test
    fun opensOnTheTextWithTheDetailsOnAndNothingSaid() {
        assertEquals(ReportItem.Text, form.focus)
        assertTrue(form.includeDetails)
        assertNull(form.result)
        assertNull(form.mistake)
        assertFalse(form.isTrayUp)
        assertEquals(listOf(ReportItem.Text, ReportItem.Email, ReportItem.Details, ReportItem.Send, ReportItem.Leave), form.items)
    }

    @Test
    fun downAndUpWalkTheItemsAndStopAtTheEnds() {
        val seen = mutableListOf(form.focus)
        repeat(6) { form.onAction(down); seen += form.focus }
        assertEquals(listOf(ReportItem.Text, ReportItem.Email, ReportItem.Details, ReportItem.Send, ReportItem.Leave, ReportItem.Leave, ReportItem.Leave), seen)
        assertEquals(ReportStep.Stay(moved = true), form.onAction(up))
        assertEquals(ReportItem.Send, form.focus)
        repeat(5) { form.onAction(up) }
        assertEquals(ReportItem.Text, form.focus)
        assertEquals(ReportStep.Stay(moved = false), form.onAction(up))
    }

    @Test
    fun sendWithNoTextSaysSoAndSendsNothing() {
        assertEquals(ReportStep.Stay(moved = true), send())
        assertEquals(ReportMistake.NoText, form.mistake)
        assertEquals(ReportItem.Text, form.focus)
        form.onTextChange("   \n ")
        assertEquals(ReportStep.Stay(moved = true), send())
        assertEquals(ReportMistake.NoText, form.mistake)
        assertFalse(form.isSending)
    }

    @Test
    fun aBadEmailIsSaidAndFocused() {
        form.onTextChange("it crashed")
        form.onEmailChange("not an address")
        assertEquals(ReportStep.Stay(moved = true), send())
        assertEquals(ReportMistake.BadEmail, form.mistake)
        assertEquals(ReportItem.Email, form.focus)
        form.onEmailChange("a".repeat(250) + "@b.co")
        send()
        assertEquals(ReportMistake.EmailTooLong, form.mistake)
        // Typing clears the line.
        form.onEmailChange("me@example.com")
        assertNull(form.mistake)
    }

    @Test
    fun sendHandsOverTheTrimmedTextAnEmailOrNullAndTheSwitch() {
        form.onTextChange("  the grid is blank \n")
        assertEquals(ReportStep.Submit("the grid is blank", null, true), send())
        assertTrue(form.isSending)
    }

    @Test
    fun theSwitchFlipsWithAAndWithATapAndGoesWithTheReport() {
        form.onAction(down)
        form.onAction(down)
        assertEquals(ReportItem.Details, form.focus)
        form.onAction(Action.Enter)
        assertFalse(form.includeDetails)
        form.tap(form.items.indexOf(ReportItem.Details))
        assertTrue(form.includeDetails)
        form.tap(form.items.indexOf(ReportItem.Details))
        form.onTextChange("x")
        form.onEmailChange(" me@example.com ")
        assertEquals(ReportStep.Submit("x", "me@example.com", false), send())
    }

    @Test
    fun theTextStopsAtFourThousandCharactersAndTheEndIsKept() {
        form.onTextChange("a".repeat(3_999))
        form.onTextChange("a".repeat(3_999) + "b")
        assertEquals(4_000, form.text.length)
        assertTrue(form.isAtLimit)
        // A longer text is refused whole rather than losing its end.
        form.onTextChange(form.text + "c")
        assertEquals("a".repeat(3_999) + "b", form.text)
        form.onTextChange(form.text.dropLast(1))
        assertFalse(form.isAtLimit)
        assertEquals(4_000, ProblemReports.MAX_TEXT_CHARS)
    }

    @Test
    fun aPastePastTheLimitKeepsWhatFitsAndSaysTheTextIsAtItsLongest() {
        form.onTextChange("a".repeat(3_990))
        form.onTextChange("a".repeat(3_990) + "b".repeat(50))
        assertEquals("a".repeat(3_990) + "b".repeat(10), form.text)
        assertTrue(form.isAtLimit)
        // A paste into a full text adds nothing, and the end stays.
        form.onTextChange(form.text + "zzz")
        assertEquals("a".repeat(3_990) + "b".repeat(10), form.text)
        // From an empty field, a text far past the limit keeps its first 4,000 characters.
        val fresh = ProblemReportForm()
        fresh.onTextChange("x".repeat(9_000))
        assertEquals(4_000, fresh.text.length)
    }

    @Test
    fun aBuildThatCannotSendKeepsTheWordsAndSaysSo() {
        form.onTextChange("my words")
        send()
        form.showResult(ReportResult.Unavailable)
        assertFalse(form.isFinished)
        assertEquals("my words", form.text)
        assertEquals(ReportItem.Send, form.focus)
    }

    @Test
    fun nothingWorksWhileSendingExceptBack() {
        form.onTextChange("x")
        send()
        assertEquals(ReportStep.Stay(), form.onAction(down))
        assertEquals(ReportStep.Stay(), form.onAction(Action.Enter))
        assertEquals(ReportStep.Stay(), form.tap(0))
        form.onTextChange("changed")
        assertEquals("x", form.text)
        assertEquals(ReportStep.NotHandled, form.onAction(Action.Back))
    }

    @Test
    fun sentAndSavedClearTheFieldsAndLeaveOnlyDone() {
        for (answer in listOf(ReportResult.Sent, ReportResult.Saved)) {
            val f = ProblemReportForm()
            f.onTextChange("x")
            f.onEmailChange("a@b.co")
            f.onAction(Action.Move(Direction.Down)); f.onAction(Action.Move(Direction.Down)); f.onAction(Action.Move(Direction.Down))
            assertTrue(f.onAction(Action.Enter) is ReportStep.Submit)
            f.showResult(answer)
            assertEquals(answer, f.result)
            assertTrue(f.isFinished)
            assertEquals(listOf(ReportItem.Done), f.items)
            assertEquals(ReportItem.Done, f.focus)
            assertEquals("", f.text)
            assertEquals("", f.email)
            assertEquals(ReportStep.Leave, f.onAction(Action.Enter))
        }
    }

    @Test
    fun aRefusedOrUnsavedReportKeepsTheWordsAndLetsThePersonTryAgain() {
        form.onTextChange("my words")
        send()
        form.showResult(ReportResult.Refused)
        assertFalse(form.isSending)
        assertFalse(form.isFinished)
        assertEquals("my words", form.text)
        assertEquals(ReportItem.Send, form.focus)
        assertTrue(send() is ReportStep.Submit)
        form.showResult(ReportResult.NotSaved)
        assertEquals(ReportResult.NotSaved, form.result)
        assertEquals("my words", form.text)
    }

    @Test
    fun cancelLeavesAndBackIsTheHosts() {
        while (form.focus != ReportItem.Leave) form.onAction(down)
        assertEquals(ReportStep.Leave, form.onAction(Action.Enter))
        assertEquals(ReportStep.NotHandled, form.onAction(Action.Back))
    }

    @Test
    fun plumemuKeyboardTypesBothFieldsNextGoesToTheEmailAndDoneToSendWithoutSending() {
        assertEquals(TextEntry.Field, form.textEntry)
        form.onAction(Action.Enter)
        assertTrue(form.isTrayUp)
        assertEquals(TextEntry.Keyboard, form.textEntry)
        form.onAction(Action.Typed(TypedKey.Text("it crashed")))
        assertEquals("it crashed", form.text)
        assertEquals(com.plumemu.R.string.account_hint_next, reportActionWord(form))
        form.onAction(Action.Menu) // Next
        assertEquals(ReportItem.Email, form.focus)
        assertTrue("the tray stays up on the e-mail", form.isTrayUp)
        assertEquals(com.plumemu.R.string.settings_hint_done, reportActionWord(form))
        form.onAction(Action.Typed(TypedKey.Text("me@example.com")))
        assertEquals("me@example.com", form.email)
        val done = form.onAction(Action.Menu) // Done
        assertFalse("Done does not send", done is ReportStep.Submit)
        assertFalse(form.isSending)
        assertEquals(ReportItem.Send, form.focus)
        assertFalse(form.isTrayUp)
        assertEquals(TextEntry.None, form.textEntry)
        assertEquals(ReportStep.Submit("it crashed", "me@example.com", true), form.onAction(Action.Enter))
    }
}
