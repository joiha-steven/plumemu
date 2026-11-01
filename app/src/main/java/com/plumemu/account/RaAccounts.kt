package com.plumemu.account

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/*
 * The person's RetroAchievements account as Settings › Accounts sees it. The graph's side is
 * `graph/GraphAchievements.kt` (the account file, the check, the encrypted key) and
 * `graph/GraphRaAccounts.kt` (this interface over it).
 */

/** What the RetroAchievements row and its panel show. */
@Immutable
internal sealed interface RaAccountStatus {
    data object SignedOut : RaAccountStatus

    /** [isKeyRefused]: RetroAchievements refused the stored key; Settings asks to sign in again. */
    data class SignedIn(
        val userName: String,
        val points: Int,
        val isKeyRefused: Boolean = false,
    ) : RaAccountStatus {
        override fun toString(): String = "SignedIn(userName=***, points=$points, isKeyRefused=$isKeyRefused)"
    }
}

/**
 * What Settings can do with the RetroAchievements account. Each call returns at once; the answer comes
 * back through its callback on the main thread. A sign-in that has started finishes even if the screen
 * goes away (an accepted account is stored). Answers reuse [SignInResult] so the form shows them.
 */
internal interface RaAccounts {
    val status: Flow<RaAccountStatus>

    /** Asks RetroAchievements about [userName] and [apiKey]; when accepted, stores the name and the encrypted key. */
    fun signIn(
        userName: String,
        apiKey: String,
        onDone: (SignInResult) -> Unit,
    )

    /** Forgets the account (name and key); [onDone] gets whether it was written. */
    fun signOut(onDone: (Boolean) -> Unit)

    /** Tests that do not care. */
    object None : RaAccounts {
        override val status: Flow<RaAccountStatus> = flowOf(RaAccountStatus.SignedOut)

        override fun signIn(
            userName: String,
            apiKey: String,
            onDone: (SignInResult) -> Unit,
        ) = onDone(SignInResult.NotSaved)

        override fun signOut(onDone: (Boolean) -> Unit) = onDone(true)
    }
}
