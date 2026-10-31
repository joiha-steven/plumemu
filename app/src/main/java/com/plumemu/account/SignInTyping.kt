package com.plumemu.account

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
 * The sign-in form's two fields as Plumemu's keyboard types into them, so they
 * get the game panel's button map unchanged ([ownKeyboardOutcome]): the field is the one the cursor
 * is on, each with its own caret and keys ([SignInForm.usernameKeys], [SignInForm.passwordKeys]),
 * letters with shift and no capital put in by itself (names and passwords are typed as they are).
 * One per form, so the tray stays the same tray when "Next" moves from the username to the password
 * (a new tray key would slide it down and up again). The action key is [SignInForm.onOwnKeyboardAction]:
 * "Next", then "Sign in" ([signInActionWord]). What is not the keyboard's (the cursor on a button, A
 * and the D-pad with the tray down) stays the form's own rules: [onAction] hands it back as
 * [TypingOutcome.NotHandled]. Main thread only.
 */
@Stable
internal class SignInTyping(
    private val form: SignInForm,
) : TypingState {
    override val keyboardKind: KeyboardChoice get() = KeyboardChoice.Plumemu

    /** The cursor is on the username or the password and no check runs. */
    override val inField: Boolean get() = form.isOnField

    override val keyboard: KeyboardAsk get() = form.keyboard

    override val fieldText: String get() = if (form.focus == SignInItem.Password) form.password else form.username

    override fun replaceText(text: String) = if (form.focus == SignInItem.Password) form.onPasswordChange(text) else form.onUsernameChange(text)

    override val fieldKeys: FieldKeys get() = form.keysOf(form.focus)

    /** The form submits itself (its step is read by the form's own handling), so nothing is routed back. */
    override fun onKeyboardAction(): Action? {
        form.onOwnKeyboardAction()
        return null
    }

    /** The focused item's place in [SignInForm.items] (the form reports its own moves; nothing reads this to tell one). */
    override val cursor: Int get() = form.items.indexOf(form.focus)

    /** Typing in the field the cursor is on (nothing on a button: a tap there would press it). */
    override fun focusField() {
        if (form.isOnField) form.tap(form.items.indexOf(form.focus))
    }

    override fun onAction(action: Action): TypingOutcome = ownKeyboardOutcome(action) ?: TypingOutcome.NotHandled
}

/** The word on the tray's action key and after START in the hints: "Next" on the username, "Sign in" on the password. */
@StringRes
internal fun signInActionWord(form: SignInForm): Int = if (form.focus == SignInItem.Password) R.string.account_sign_in else R.string.account_hint_next
