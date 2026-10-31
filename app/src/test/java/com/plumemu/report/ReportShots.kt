package com.plumemu.report

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TypedKey
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.telemetry.ProblemReports
import com.plumemu.telemetry.ReportResult

/**
 * The report form's states for the shots and the fit checks: each one built by the form's own
 * actions, as in use, and made fresh each time (a form is mutable). Made-up words and addresses only.
 */
internal object ReportShots {
    enum class State { Empty, Typing, LongText, NoDetails, NoText, Sending, Saved, Sent, Refused }

    const val WORDS = "The system list is empty after I rescan and the grid stays blank until I restart"

    private fun down(
        form: ProblemReportForm,
        times: Int,
    ) = repeat(times) { form.onAction(Action.Move(Direction.Down)) }

    fun form(state: State): ProblemReportForm {
        val form = ProblemReportForm(KeyboardChoice.Plumemu)
        when (state) {
            State.Empty -> Unit
            State.Typing -> {
                form.onAction(Action.Enter)
                form.onAction(Action.Typed(TypedKey.Text(WORDS)))
            }
            State.LongText -> {
                // Typed the way a person would reach the cap: the text is full, the e-mail long.
                form.onTextChange("word ".repeat(ProblemReports.MAX_TEXT_CHARS / 5))
                form.onEmailChange("someone.with.a.very.long.name@a-rather-long-domain-name.example.org")
            }
            State.NoDetails -> {
                form.onTextChange(WORDS)
                down(form, 2)
                form.onAction(Action.Enter)
            }
            State.NoText -> {
                down(form, 3)
                form.onAction(Action.Enter)
            }
            State.Sending -> {
                form.onTextChange(WORDS)
                down(form, 3)
                form.onAction(Action.Enter)
            }
            State.Saved, State.Sent, State.Refused -> {
                form.onTextChange(WORDS)
                form.onEmailChange("me@example.com")
                down(form, 3)
                form.onAction(Action.Enter)
                form.showResult(
                    when (state) {
                        State.Saved -> ReportResult.Saved
                        State.Sent -> ReportResult.Sent
                        else -> ReportResult.Refused
                    },
                )
            }
        }
        return form
    }
}
