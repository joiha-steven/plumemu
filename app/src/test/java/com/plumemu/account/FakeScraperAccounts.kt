package com.plumemu.account

import kotlinx.coroutines.flow.MutableStateFlow

/** [ScraperAccounts] for model tests: answers at once with [answer], or holds the answer until [release]. */
internal class FakeScraperAccounts(
    override val isAvailable: Boolean = true,
    initial: AccountStatus = if (isAvailable) AccountStatus.SignedOut else AccountStatus.NotInThisBuild,
) : ScraperAccounts {
    override val status = MutableStateFlow(initial)

    var answer: (String, String) -> SignInResult = { name, _ -> SignInResult.SignedIn(name) }

    /** True: [signIn] keeps its callback until [release], as while ScreenScraper is being asked. */
    var holdsAnswers = false
    private var held: (() -> Unit)? = null

    /**
     * True: a sign-in that worked answers its callback first and stores the status only on
     * [publishStatus], as the real graph may (its status flow follows the store).
     */
    var publishesStatusLate = false
    private var unpublished: AccountStatus? = null

    fun publishStatus() {
        unpublished?.let { status.value = it }
        unpublished = null
    }

    val signIns = mutableListOf<Pair<String, String>>()
    var signOuts = 0
    var signOutWorks = true

    override fun signIn(
        username: String,
        password: String,
        onDone: (SignInResult) -> Unit,
    ) {
        signIns += username to password
        val result = answer(username, password)
        val deliver = {
            if (result is SignInResult.SignedIn) {
                val signedIn = AccountStatus.SignedIn(result.username)
                if (publishesStatusLate) unpublished = signedIn else status.value = signedIn
            }
            onDone(result)
        }
        if (holdsAnswers) held = deliver else deliver()
    }

    fun release() {
        held?.invoke()
        held = null
    }

    override fun signOut(onDone: (Boolean) -> Unit) {
        signOuts++
        if (signOutWorks) status.value = AccountStatus.SignedOut
        onDone(signOutWorks)
    }
}
