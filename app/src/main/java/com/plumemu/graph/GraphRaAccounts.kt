package com.plumemu.graph

import com.plumemu.account.RaAccountStatus
import com.plumemu.account.RaAccounts
import com.plumemu.account.SignInResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings' view of [GraphAchievements]: sign-in and sign-out in [scope], answers on [main]. */
internal class GraphRaAccounts(
    private val achievements: GraphAchievements,
    private val scope: CoroutineScope,
    private val main: CoroutineDispatcher,
    private val log: (String) -> Unit,
) : RaAccounts {
    override val status: Flow<RaAccountStatus> get() = achievements.status

    override fun signIn(
        userName: String,
        apiKey: String,
        onDone: (SignInResult) -> Unit,
    ) {
        scope.launch {
            val result =
                try {
                    signInResultOf(achievements.signIn(userName, apiKey))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("RetroAchievements sign-in failed (${e.javaClass.simpleName})")
                    SignInResult.NotSaved
                }
            withContext(main) { onDone(result) }
        }
    }

    override fun signOut(onDone: (Boolean) -> Unit) {
        scope.launch {
            val done = achievements.signOut()
            withContext(main) { onDone(done) }
        }
    }
}

/** The one mapping from [GraphAchievements.signIn]'s answer to what the sign-in form says. */
internal fun signInResultOf(result: RaSignInResult): SignInResult =
    when (result) {
        is RaSignInResult.SignedIn -> SignInResult.SignedIn(result.userName)
        RaSignInResult.Missing -> SignInResult.Missing
        RaSignInResult.WrongKey -> SignInResult.RaWrongKey
        RaSignInResult.UnknownUser -> SignInResult.RaUnknownUser
        RaSignInResult.NoConnection -> SignInResult.Offline
        RaSignInResult.Busy -> SignInResult.RaBusy
        RaSignInResult.RateLimited -> SignInResult.RaRateLimited
        RaSignInResult.NotSaved -> SignInResult.NotSaved
    }
