package com.plumemu.account

import androidx.compose.runtime.Immutable
import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.UnavailableReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/*
 * The person's ScreenScraper account as setup and Settings see it:
 * box art only with the person's own account. The graph's side is `graph/GraphScraperAccounts.kt`.
 */

/** What the account row and its status line show. */
@Immutable
internal sealed interface AccountStatus {
    /** This build has no developer credentials: nothing asks for an account. */
    data object NotInThisBuild : AccountStatus

    data object SignedOut : AccountStatus

    /**
     * An account is stored. [isRefused]: ScreenScraper refused its password after it was stored.
     * [dailyLimitUntilMillis]: the scraper saved a daily-quota pause that has not ended yet.
     */
    data class SignedIn(
        val username: String,
        val isRefused: Boolean = false,
        val dailyLimitUntilMillis: Long? = null,
    ) : AccountStatus {
        override fun toString(): String = "SignedIn(username=***, isRefused=$isRefused, dailyLimitUntilMillis=$dailyLimitUntilMillis)"
    }
}

/** How a sign-in ended, in the person's terms (one message each). */
@Immutable
internal sealed interface SignInResult {
    data class SignedIn(val username: String) : SignInResult {
        override fun toString(): String = "SignedIn(username=***)"
    }

    /** The username or the password is empty: nothing was asked. */
    data object Missing : SignInResult

    data object WrongCredentials : SignInResult

    data object Offline : SignInResult

    data object ServerClosed : SignInResult

    /** ScreenScraper's daily limit is reached; asking again works after [resumeAtMillis] (midnight in Paris). */
    data class DailyLimit(val resumeAtMillis: Long) : SignInResult

    data class ServerError(val code: Int) : SignInResult

    /** ScreenScraper refused the app's own credentials or blocked the software (not the person's fault). */
    data object AppRefused : SignInResult

    /** The password could not be encrypted or the account could not be written (logged by class). */
    data object NotSaved : SignInResult

    /** This build has no developer credentials. */
    data object NotAvailable : SignInResult
}

/** The one mapping from the scraper's answer to what the person is told. */
internal fun signInResultOf(
    check: AccountCheck,
    username: String,
): SignInResult =
    when (check) {
        is AccountCheck.Ok -> SignInResult.SignedIn(username)
        AccountCheck.NoAccount -> SignInResult.Missing
        is AccountCheck.Refused ->
            when (check.reason) {
                UnavailableReason.BadUserCredentials -> SignInResult.WrongCredentials
                UnavailableReason.NoDeveloperCredentials -> SignInResult.NotAvailable
                UnavailableReason.BadDeveloperCredentials, UnavailableReason.SoftwareBlocked -> SignInResult.AppRefused
            }
        AccountCheck.Offline -> SignInResult.Offline
        AccountCheck.ServerClosed -> SignInResult.ServerClosed
        is AccountCheck.DailyQuota -> SignInResult.DailyLimit(check.resumeAtMillis)
        is AccountCheck.ServerError -> SignInResult.ServerError(check.code)
    }

/**
 * What setup and Settings can do with the account. Each call returns at once; the answer comes
 * back through its callback on the main thread. A sign-in that has started finishes even if the
 * screen goes away (an accepted account is stored).
 */
internal interface ScraperAccounts {
    /** The build carries developer credentials; false means no step, no row, no request. */
    val isAvailable: Boolean

    val status: Flow<AccountStatus>

    /** Asks ScreenScraper about [username] and [password]; when accepted, stores the name and the encrypted password. */
    fun signIn(
        username: String,
        password: String,
        onDone: (SignInResult) -> Unit,
    )

    /** Forgets the account; [onDone] gets whether it was written. */
    fun signOut(onDone: (Boolean) -> Unit)

    /** A build without developer credentials (and tests that do not care). */
    object Unavailable : ScraperAccounts {
        override val isAvailable: Boolean = false
        override val status: Flow<AccountStatus> = flowOf(AccountStatus.NotInThisBuild)

        override fun signIn(
            username: String,
            password: String,
            onDone: (SignInResult) -> Unit,
        ) = onDone(SignInResult.NotAvailable)

        override fun signOut(onDone: (Boolean) -> Unit) = onDone(true)
    }
}
