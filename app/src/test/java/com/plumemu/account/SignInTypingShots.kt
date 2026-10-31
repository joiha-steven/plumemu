package com.plumemu.account

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.TypedKey
import com.plumemu.keyboard.KeyboardChoice

/**
 * The sign-in form typing with Plumemu's keyboard for the shots and fit
 * checks of setup and Settings: driven by its own actions, as in use, the tray up on the username
 * ("Next") or on the password ("Sign in", its dots), or on the password again after a refused
 * sign-in (the message line under it). A made-up name and password only, never a real account.
 */
internal object SignInTypingShots {
    enum class State { Username, Password, Wrong }

    const val USERNAME = "plumtester"

    /** Eight characters: what the dots show. */
    private const val PASSWORD = "testonly"

    /** [stacked]: Settings' narrow panel puts Sign in over Cancel. */
    fun form(
        state: State,
        stacked: Boolean = false,
    ): SignInForm {
        val form = SignInForm(stackedButtons = stacked, keyboardKind = KeyboardChoice.Plumemu)
        form.onAction(Action.Enter)
        form.onAction(Action.Typed(TypedKey.Text(USERNAME)))
        if (state == State.Username) return form
        form.onAction(Action.Menu) // Next: the password, the tray still up
        form.onAction(Action.Typed(TypedKey.Text(PASSWORD)))
        if (state == State.Password) return form
        form.onAction(Action.Menu) // Sign in
        form.showResult(SignInResult.WrongCredentials)
        form.onAction(Action.Enter) // the password's tray again
        check(form.isTrayUp && form.focus == SignInItem.Password) { "the fixture's form is not typing on the password" }
        return form
    }
}
