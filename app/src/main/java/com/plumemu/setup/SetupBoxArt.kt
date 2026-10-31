package com.plumemu.setup

import com.plumemu.account.AccountStatus
import com.plumemu.account.ScraperAccounts
import com.plumemu.account.SignInForm
import com.plumemu.account.SignInResult
import com.plumemu.account.signInConfirmHint
import com.plumemu.account.SignInStep
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.sound.Feel
import com.plumemu.sound.Outcome

/*
 * Setup's "Box art from ScreenScraper" step:
 * after the second screen question, while the scan runs, only in a build with developer
 * credentials. It asks first ("Use my account" / "Not now", SetupControl.UseBoxArt / SkipBoxArt);
 * only Use my account shows the sign-in form, account/SignInForm.kt, shared with Settings.
 */

/** After the second screen question: box art in a build that has it, else the scan. */
internal fun boxArtOrScan(accounts: ScraperAccounts): SetupStep = if (accounts.isAvailable) SetupStep.BoxArt else SetupStep.Scan

/**
 * A fresh form for the step. Already signed in when an account is stored and usable, or when
 * [signedInHere] names a sign-in this setup saw succeed (the stored status may not show it yet). A
 * stored account whose password ScreenScraper refused gives a form with its name filled in, to type
 * the password again (Settings' "Sign in again"). Null in a build without the step.
 */
internal fun newBoxArtForm(
    accounts: ScraperAccounts,
    status: AccountStatus,
    signedInHere: String? = null,
    /** Settings › Controls › Keyboard: Plumemu's tray or Android's keyboard. */
    keyboard: KeyboardChoice = KeyboardChoice.Plumemu,
): SignInForm? {
    if (!accounts.isAvailable) return null
    if (signedInHere != null) return SignInForm(signedInAs = signedInHere, keyboardKind = keyboard)
    val stored = status as? AccountStatus.SignedIn ?: return SignInForm(keyboardKind = keyboard)
    return if (stored.isRefused) {
        SignInForm(initialUsername = stored.username, keyboardKind = keyboard)
    } else {
        SignInForm(signedInAs = stored.username, keyboardKind = keyboard)
    }
}

/** The step opens on its form, not the question: signed in, or a refused account to sign in again. */
internal fun opensOnTheForm(
    form: SignInForm?,
    status: AccountStatus,
): Boolean = form != null && (form.signedInAs != null || (status as? AccountStatus.SignedIn)?.isRefused == true)

/**
 * Every action but Back on the box art step's form goes to the form: Sign in asks the graph (the
 * form shows "Checking…" and then the answer), Skip and Continue call [goOn] (the scan's step
 * next).
 */
internal fun onBoxArtAction(
    form: SignInForm,
    action: Action,
    accounts: ScraperAccounts,
    feel: Feel,
    /** A sign-in worked (from its own answer, before the stored status may show it). */
    onSignedIn: (username: String) -> Unit,
    goOn: () -> Unit,
): SetupEffect? {
    onBoxArtStep(form, form.onAction(action), accounts, feel, onSignedIn, goOn)
    return null
}

/** A tap on a key of Plumemu's tray over the form: as the same key pressed with A (its action key may sign in, once). */
internal fun onBoxArtKey(
    form: SignInForm,
    event: KeyboardEvent,
    accounts: ScraperAccounts,
    feel: Feel,
    onSignedIn: (username: String) -> Unit,
) = onBoxArtStep(form, form.onKeyboardKey(event), accounts, feel, onSignedIn, goOn = {})

private fun onBoxArtStep(
    form: SignInForm,
    step: SignInStep,
    accounts: ScraperAccounts,
    feel: Feel,
    onSignedIn: (username: String) -> Unit,
    goOn: () -> Unit,
) {
    when (step) {
        is SignInStep.Stay -> if (step.moved) feel.report(Outcome.Moved)
        is SignInStep.Submit -> {
            accounts.signIn(step.username, step.password) { result ->
                form.showResult(result)
                if (result is SignInResult.SignedIn) onSignedIn(result.username)
            }
            feel.report(Outcome.Activated)
        }
        SignInStep.Leave, SignInStep.Done -> {
            goOn()
            feel.report(Outcome.Activated)
        }
        SignInStep.NotHandled -> Unit
    }
}

/** What A does on the focused item, for the hint bar; none while the check runs. */
internal fun boxArtConfirmHint(form: SignInForm): Int? = signInConfirmHint(form, R.string.setup_box_art_skip)
