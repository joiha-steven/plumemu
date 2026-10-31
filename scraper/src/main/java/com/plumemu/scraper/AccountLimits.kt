package com.plumemu.scraper

/**
 * What ScreenScraper last said the account may do (`ssuser` of any answer): shown in Settings › Library › ScreenScraper so a slow box-art run can be explained.
 * Each number is null when no answer carried it. It holds no name, id or password.
 *
 * @property maxThreads downloads the account may run at once.
 * @property maxRequestsPerMinute the account's per-minute budget.
 * @property requestsToday requests counted against the account today (as of the latest answer).
 * @property maxRequestsPerDay the account's daily budget.
 * @property maxDownloadKilobytesPerSecond the speed the account may download at, in KB/s.
 */
public data class AccountLimits(
    val maxThreads: Int? = null,
    val maxRequestsPerMinute: Int? = null,
    val requestsToday: Int? = null,
    val maxRequestsPerDay: Int? = null,
    val maxDownloadKilobytesPerSecond: Int? = null,
) {
    /** True when no number is known. */
    public val isEmpty: Boolean
        get() = maxThreads == null && maxRequestsPerMinute == null && requestsToday == null &&
            maxRequestsPerDay == null && maxDownloadKilobytesPerSecond == null

    /** This answer's numbers, a number the answer left out kept from [earlier]. */
    internal fun over(earlier: AccountLimits?): AccountLimits =
        if (earlier == null) this else AccountLimits(
            maxThreads = maxThreads ?: earlier.maxThreads,
            maxRequestsPerMinute = maxRequestsPerMinute ?: earlier.maxRequestsPerMinute,
            requestsToday = requestsToday ?: earlier.requestsToday,
            maxRequestsPerDay = maxRequestsPerDay ?: earlier.maxRequestsPerDay,
            maxDownloadKilobytesPerSecond = maxDownloadKilobytesPerSecond ?: earlier.maxDownloadKilobytesPerSecond,
        )
}
