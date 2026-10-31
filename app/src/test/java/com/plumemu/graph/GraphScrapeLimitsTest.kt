package com.plumemu.graph

import com.plumemu.account.AccountStatus
import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.AccountLimits
import com.plumemu.scraper.PauseReason
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.UnavailableReason
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The account's limits for the ScreenScraper panel: the one ask, and when it is not made. */
internal class GraphScrapeLimitsTest : GraphScrapesHarness() {

    // The account's limits for the ScreenScraper panel.

    private val limitsAnswer = AccountLimits(maxThreads = 1, requestsToday = 1_234, maxRequestsPerDay = 10_000, maxDownloadKilobytesPerSecond = 128)

    /** Signs in with a scraper whose sign-in check gave no limits; later checks give [limitsAnswer]. */
    private suspend fun signedInWithoutLimits(): FakeScraper {
        val scrapes = graph()
        signIn(scrapes)
        val scraper = made.single()
        limitsToGive = limitsAnswer
        return scraper
    }

    /** Settings is open and collecting until [condition] holds (the limits flow starts its work on collection). */
    private suspend fun visitUntil(condition: () -> Boolean) {
        val job = scope.launch { scrapes.accountLimits.collect { } }
        try {
            withTimeout(5_000) { while (!condition()) delay(10) }
        } finally {
            job.cancelAndJoin()
        }
    }

    /** Settings is open and collecting for [millis]. */
    private suspend fun visitFor(millis: Long) {
        val job = scope.launch { scrapes.accountLimits.collect { } }
        delay(millis)
        job.cancelAndJoin()
    }

    /** A new graph for the next round of a test that makes several: the old scraper and its scope are left behind. */
    private fun tearDownGraph() {
        made.clear()
        paused.value = false
    }

    @Test
    fun theLimitsAreAskedOnceWhenUnknownAndSignedIn() = runBlocking<Unit> {
        val scraper = signedInWithoutLimits()
        val before = scraper.checks.get()

        assertEquals(limitsAnswer, withTimeout(5_000) { scrapes.accountLimits.first { it != null } })
        assertEquals("one ssuserInfos ask", before + 1, scraper.checks.get())

        // Settings opens again: the values are known, nothing is asked.
        assertEquals(limitsAnswer, withTimeout(5_000) { scrapes.accountLimits.first { it != null } })
        delay(200)
        assertEquals(before + 1, scraper.checks.get())
    }

    @Test
    fun knownLimitsAreNotAskedAgain() = runBlocking<Unit> {
        val scraper = signedInWithoutLimits()
        scraper.accountLimits.value = limitsAnswer.copy(requestsToday = 5)
        val before = scraper.checks.get()

        assertEquals(5, withTimeout(5_000) { scrapes.accountLimits.first { it != null } }?.requestsToday)
        delay(200)
        assertEquals(before, scraper.checks.get())
    }

    @Test
    fun signedOutNothingIsAskedAndNothingIsShown() = runBlocking<Unit> {
        val scrapes = graph()
        assertNull(withTimeout(5_000) { scrapes.accountLimits.first() })
        assertTrue("no scraper was made", made.isEmpty())
    }

    @Test
    fun nothingIsAskedWhileTheDailyQuotaPausesRequests() = runBlocking<Unit> {
        val scraper = signedInWithoutLimits()
        inputs.pause = ScrapeResult.Paused(now + 60_000, PauseReason.DailyQuota)
        val before = scraper.checks.get()

        visitFor(300)
        delay(200)
        assertEquals(before, scraper.checks.get())

        // The pause ends: the next visit may ask.
        inputs.pause = null
        assertEquals(limitsAnswer, withTimeout(5_000) { scrapes.accountLimits.first { it != null } })
        assertEquals(before + 1, scraper.checks.get())
    }

    @Test
    fun noConnectionIsForgottenSoTheNextVisitAsksAgain() = runBlocking<Unit> {
        val scraper = signedInWithoutLimits()
        val before = scraper.checks.get()
        check = AccountCheck.Offline
        limitsToGive = null

        visitUntil { scraper.checks.get() > before }
        withTimeout(5_000) { while (scraper.checks.get() == before) delay(10) }
        delay(100)

        check = AccountCheck.Ok(maxThreads = 1, requestsToday = 0, maxRequestsPerDay = 20_000)
        limitsToGive = limitsAnswer
        assertEquals(limitsAnswer, withTimeout(5_000) { scrapes.accountLimits.first { it != null } })
        assertEquals(before + 2, scraper.checks.get())
    }

    @Test
    fun aCheckThatGaveNoLimitsIsNotAskedAgainOnTheNextVisit() = runBlocking<Unit> {
        for (answer in listOf(AccountCheck.ServerError(500), AccountCheck.Ok(maxThreads = 1, requestsToday = null, maxRequestsPerDay = null))) {
            check = AccountCheck.Ok(maxThreads = 1, requestsToday = 0, maxRequestsPerDay = 20_000)
            val scraper = signedInWithoutLimits()
            limitsToGive = null
            check = answer
            val before = scraper.checks.get()

            visitUntil { scraper.checks.get() > before }
            withTimeout(5_000) { while (scraper.checks.get() == before) delay(10) }
            delay(100)
            // Settings opens again.
            visitFor(300)
            delay(200)

            assertEquals("$answer: one ask only", before + 1, scraper.checks.get())
            tearDownGraph()
        }
    }

    @Test
    fun aWrongPasswordMarksTheAccountRefusedAndKeepsItSignedIn() = runBlocking<Unit> {
        val scraper = signedInWithoutLimits()
        limitsToGive = null
        check = AccountCheck.Refused(UnavailableReason.BadUserCredentials)

        visitUntil { scraper.checks.get() > 1 }
        val status = withTimeout(5_000) { accounts.status.first { it is AccountStatus.SignedIn && it.isRefused } }

        assertEquals(AccountStatus.SignedIn("plumtester", isRefused = true), status)
        assertEquals(2, scraper.checks.get())
    }

    @Test
    fun nothingIsAskedWhileThePersonHasPausedBoxArt() = runBlocking<Unit> {
        val scraper = signedInWithoutLimits()
        paused.value = true
        val before = scraper.checks.get()

        visitFor(300)
        delay(200)
        assertEquals(before, scraper.checks.get())

        paused.value = false
        assertEquals(limitsAnswer, withTimeout(5_000) { scrapes.accountLimits.first { it != null } })
    }

    @Test
    fun theLimitsAreUnknownAtOnceWhileTheScraperIsStillBeingMade() = runBlocking<Unit> {
        signIn(graph())
        // A new process: the account is stored, the scraper must be made again, and that hangs.
        val gate = java.util.concurrent.CountDownLatch(1)
        blockMaking = gate
        try {
            val fresh = graph()
            assertNull(withTimeout(2_000) { fresh.accountLimits.first() })
        } finally {
            gate.countDown()
        }
    }

}
