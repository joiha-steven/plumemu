package com.plumemu.account

import java.util.TimeZone
import org.junit.rules.ExternalResource

/** Times in UTC while a test runs, so shots do not depend on the machine's zone; the zone is put back after. */
class UtcTimeZone : ExternalResource() {
    private var before: TimeZone? = null

    override fun before() {
        before = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    override fun after() {
        TimeZone.setDefault(before)
    }
}

/**
 * The sign-in states the shots and the fit checks draw: one form per state, made fresh each time
 * (a form is mutable; every size and theme of a shot gets its own).
 */
internal object AccountShots {
    /** A fixed resume time (22:00 UTC, midnight in Paris in summer), shown in UTC: the shots stay the same on every machine. */
    const val RESUME_AT_MILLIS = 1_792_792_800_000L


    enum class State { Empty, Checking, Wrong, Offline, Closed, DailyLimit, ServerError, SignedIn }

    /** [stacked]: Settings' narrow panel puts Sign in over Cancel. */
    fun form(
        state: State,
        stacked: Boolean = false,
    ): SignInForm {
        if (state == State.Empty) return SignInForm(stackedButtons = stacked)
        if (state == State.SignedIn) return SignInForm(signedInAs = "plumtester", stackedButtons = stacked)
        val form = SignInForm(stackedButtons = stacked)
        form.onUsernameChange("plumtester")
        form.onPasswordChange("hunter2")
        form.tap(form.items.indexOf(SignInItem.SignIn))
        val result =
            when (state) {
                State.Wrong -> SignInResult.WrongCredentials
                State.Offline -> SignInResult.Offline
                State.Closed -> SignInResult.ServerClosed
                State.DailyLimit -> SignInResult.DailyLimit(RESUME_AT_MILLIS)
                State.ServerError -> SignInResult.ServerError(503)
                else -> null
            }
        result?.let(form::showResult)
        return form
    }
}
