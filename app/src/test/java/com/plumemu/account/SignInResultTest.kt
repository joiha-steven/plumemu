package com.plumemu.account

import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.UnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every answer of `Scraper.checkAccount()` has its own message for the person. */
class SignInResultTest {
    @Test
    fun everyAccountCheckMapsToItsResult() {
        val cases =
            listOf(
                AccountCheck.Ok(maxThreads = 1, requestsToday = 0, maxRequestsPerDay = 10_000) to SignInResult.SignedIn("plumtester"),
                AccountCheck.NoAccount to SignInResult.Missing,
                AccountCheck.Refused(UnavailableReason.BadUserCredentials) to SignInResult.WrongCredentials,
                AccountCheck.Refused(UnavailableReason.NoDeveloperCredentials) to SignInResult.NotAvailable,
                AccountCheck.Refused(UnavailableReason.BadDeveloperCredentials) to SignInResult.AppRefused,
                AccountCheck.Refused(UnavailableReason.SoftwareBlocked) to SignInResult.AppRefused,
                AccountCheck.Offline to SignInResult.Offline,
                AccountCheck.ServerClosed to SignInResult.ServerClosed,
                AccountCheck.DailyQuota(resumeAtMillis = 1_234L) to SignInResult.DailyLimit(1_234L),
                AccountCheck.ServerError(503) to SignInResult.ServerError(503),
                AccountCheck.ServerError(429) to SignInResult.ServerError(429),
            )
        for ((check, expected) in cases) assertEquals("$check", expected, signInResultOf(check, "plumtester"))
    }
}
