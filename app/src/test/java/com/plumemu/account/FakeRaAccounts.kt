package com.plumemu.account

import kotlinx.coroutines.flow.MutableStateFlow

/** [RaAccounts] for model tests: answers at once with [answer], or holds the answer until [release]. */
internal class FakeRaAccounts : RaAccounts {
    override val status = MutableStateFlow<RaAccountStatus>(RaAccountStatus.SignedOut)

    var answer: (String, String) -> SignInResult = { name, _ -> SignInResult.SignedIn(name) }

    /** Points the account shows once a sign-in worked. */
    var pointsAfterSignIn = 1234

    /** True: [signIn] keeps its callback until [release], as while RetroAchievements is being asked. */
    var holdsAnswers = false
    private var held: (() -> Unit)? = null

    val signIns = mutableListOf<Pair<String, String>>()
    var signOuts = 0
    var signOutWorks = true

    override fun signIn(
        userName: String,
        apiKey: String,
        onDone: (SignInResult) -> Unit,
    ) {
        signIns += userName to apiKey
        val result = answer(userName, apiKey)
        val deliver = {
            if (result is SignInResult.SignedIn) status.value = RaAccountStatus.SignedIn(result.username, pointsAfterSignIn)
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
        if (signOutWorks) status.value = RaAccountStatus.SignedOut
        onDone(signOutWorks)
    }
}
