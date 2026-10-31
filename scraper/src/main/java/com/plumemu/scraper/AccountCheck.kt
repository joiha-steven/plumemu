package com.plumemu.scraper

/** What [Scraper.checkAccount] found about the person's ScreenScraper account. */
public sealed interface AccountCheck {

    /** ScreenScraper knows the account; its numbers are null when the answer left them out. */
    public data class Ok(
        val maxThreads: Int?,
        val requestsToday: Int?,
        val maxRequestsPerDay: Int?,
    ) : AccountCheck

    /** No username or password is set: nothing was asked. */
    public data object NoAccount : AccountCheck

    /**
     * Refused: a wrong username or password ([UnavailableReason.BadUserCredentials]), the app's
     * credentials ([UnavailableReason.BadDeveloperCredentials], [UnavailableReason.SoftwareBlocked])
     * or none in this build ([UnavailableReason.NoDeveloperCredentials], nothing asked). The scraper
     * stays stopped for these credentials, as after a refused scrape.
     */
    public data class Refused(val reason: UnavailableReason) : AccountCheck

    /** No connection, a timeout, or the connection broke. */
    public data object Offline : AccountCheck

    /** ScreenScraper's API is closed (HTTP 401 or 423, or closed to non-members); try in an hour. */
    public data object ServerClosed : AccountCheck

    /**
     * Today's request quota is used up (HTTP 430 or 431, or an earlier answer said so): nothing is
     * asked before [resumeAtMillis], midnight in Paris. The credentials were not checked.
     */
    public data class DailyQuota(val resumeAtMillis: Long) : AccountCheck

    /**
     * Any other answer: a server fault, an answer that is not the expected JSON (code 200), or
     * 429 (too many threads or requests), which is not retried here so a sign-in screen does not
     * wait half a minute; asking again later may work.
     */
    public data class ServerError(val code: Int) : AccountCheck
}
