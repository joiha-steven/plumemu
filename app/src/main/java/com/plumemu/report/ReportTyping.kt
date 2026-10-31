package com.plumemu.report

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.game.FieldKeys
import com.plumemu.game.KeyboardAsk
import com.plumemu.game.TypingOutcome
import com.plumemu.game.TypingState
import com.plumemu.game.ownKeyboardOutcome
import com.plumemu.keyboard.KeyboardChoice

/**
 * The report form's two fields as Plumemu's keyboard types into them, so they get the game
 * panel's button map unchanged ([ownKeyboardOutcome]): the field is the one the cursor is on, each
 * with its own caret and keys. One per form, so the tray stays the same tray when "Next" moves
 * from the text to the e-mail. What is not the keyboard's (the cursor on a row or a button, A and
 * the D-pad with the tray down) stays the form's own rules ([TypingOutcome.NotHandled]). Main thread only.
 */
@Stable
internal class ReportTyping(
    private val form: ProblemReportForm,
) : TypingState {
    override val keyboardKind: KeyboardChoice get() = KeyboardChoice.Plumemu

    override val inField: Boolean get() = form.isOnField

    override val keyboard: KeyboardAsk get() = form.keyboard

    override val fieldText: String get() = if (form.focus == ReportItem.Email) form.email else form.text

    override fun replaceText(text: String) = if (form.focus == ReportItem.Email) form.onEmailChange(text) else form.onTextChange(text)

    override val fieldKeys: FieldKeys get() = form.keysOf(form.focus)

    /** The form moves itself (its step is read by the form's own handling), so nothing is routed back. */
    override fun onKeyboardAction(): Action? {
        form.onOwnKeyboardAction()
        return null
    }

    override val cursor: Int get() = form.items.indexOf(form.focus)

    override fun focusField() {
        if (form.isOnField) form.tap(form.items.indexOf(form.focus))
    }

    override fun onAction(action: Action): TypingOutcome = ownKeyboardOutcome(action) ?: TypingOutcome.NotHandled
}

/** The word on the tray's action key and after START in the hints: "Next" on the text, "Done" on the e-mail. */
@StringRes
internal fun reportActionWord(form: ProblemReportForm): Int = if (form.focus == ReportItem.Email) R.string.settings_hint_done else R.string.account_hint_next
