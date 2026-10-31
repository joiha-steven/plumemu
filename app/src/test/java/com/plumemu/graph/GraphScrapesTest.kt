package com.plumemu.graph

import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.plumemu.PlumApplication
import com.plumemu.account.AccountStatus
import com.plumemu.account.SignInResult
import com.plumemu.library.scrape.AskAgain
import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.PauseReason
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.UnavailableReason
import com.plumemu.scraper.work.ScrapeRunEnd
import com.plumemu.scraper.work.ScraperHost
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's side of the run: no work and no request without a usable account, one
 * scraper per account, a refusal marks the account, a sign-in asks for one run on Wi-Fi with a
 * battery that is not low, and the status follows its inputs.
 */
internal class GraphScrapesTest : GraphScrapesHarness() {

    @Test
    fun withoutAnAccountThereIsNoWorkAndNoRun() = runBlocking<Unit> {
        val scrapes = graph()
        assertNull(scrapes.scraperWork())
        scrapes.requestRun().join()
        assertEquals(emptyList<WorkInfo>(), runs())
        assertTrue("no scraper was made", made.isEmpty())
    }

    @Test
    fun aBuildWithoutDeveloperCredentialsHasNoWorkAndNoRun() = runBlocking<Unit> {
        val scrapes = graph(isAvailable = false)
        assertEquals(SignInResult.NotAvailable, accounts.check("plumtester", PASSWORD))
        assertNull(scrapes.scraperWork())
        scrapes.requestRun().join()
        assertEquals(emptyList<WorkInfo>(), runs())
        assertEquals(ScrapeStatus.NotInThisBuild, scrapes.status.first())
    }

    @Test
    fun aSignInAsksForOneRunOnWifiWithABatteryNotLow() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        val run = awaitRun()
        assertEquals(WorkInfo.State.ENQUEUED, run.state)
        assertEquals(NetworkType.UNMETERED, run.constraints.requiredNetworkType)
        assertTrue(run.constraints.requiresBatteryNotLow())

        // A scan that ends asks again: the waiting run is kept, not doubled.
        scrapes.requestRun().join()
        assertEquals(listOf(run.id), runs().map { it.id })
    }

    @Test
    fun signingOutStopsTheWaitingRunAndGivesNoWork() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        awaitRun()

        assertTrue(accounts.signOutNow())

        assertEquals(emptyList<WorkInfo>(), runs())
        assertNull(scrapes.scraperWork())
    }

    @Test
    fun aRefusedSignInAsksForNoRun() = runBlocking<Unit> {
        check = AccountCheck.Refused(UnavailableReason.BadUserCredentials)
        val scrapes = graph()
        assertEquals(SignInResult.WrongCredentials, accounts.check("plumtester", PASSWORD))
        scrapes.requestRun().join()
        assertEquals(emptyList<WorkInfo>(), runs())
    }

    @Test
    fun theWorkUsesTheAccountsOneScraperTheAppsSizesAndTheDevicesMemory() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        val first = checkNotNull(scrapes.scraperWork())
        val second = checkNotNull(scrapes.scraperWork())

        assertSame("one scraper per account for the process", first.scraper, second.scraper)
        assertEquals(1, made.size)
        assertEquals(ScrapeMediaSizes.all, first.mediaSizes)
        assertEquals(File(context.noBackupFilesDir, "test-media"), first.mediaDir)
        assertNull("null asks the device", first.lowRamDevice)
        assertTrue(first.source is ScrapeRunSource)
        assertEquals("an empty library has nothing to ask", emptyList<ScrapeRequest>(), first.source.pending(20))
    }

    @Test
    fun aRunRefusedForTheUserMarksTheAccountAndNothingMoreIsAsked() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        awaitRun()
        val work = checkNotNull(scrapes.scraperWork())

        work.onUnavailable(UnavailableReason.BadUserCredentials)

        assertEquals(AccountStatus.SignedIn("plumtester", isRefused = true), accounts.status.first())
        assertEquals(ScrapeStatus.Refused, scrapes.status.first())
        assertNull(scrapes.scraperWork())
        WorkManager.getInstance(context).cancelAllWork().result.get()
        scrapes.requestRun().join()
        assertEquals(emptyList<WorkInfo>(), runs())
    }

    @Test
    fun otherReasonsLeaveTheAccountAsItIs() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        val work = checkNotNull(scrapes.scraperWork())

        work.onUnavailable(UnavailableReason.BadDeveloperCredentials)
        work.onUnavailable(UnavailableReason.SoftwareBlocked)

        assertEquals(AccountStatus.SignedIn("plumtester", isRefused = false), accounts.status.first())
        assertSame(work.scraper, scrapes.scraperWork()?.scraper)
    }

    @Test
    fun theStatusFollowsTheAccountAndTheRun() = runBlocking<Unit> {
        val scrapes = graph()
        assertEquals(ScrapeStatus.SignedOut, scrapes.status.first())
        signIn(scrapes)
        withTimeout(5_000) { scrapes.status.first { it == ScrapeStatus.Idle(inputs.counts) } }

        inputs.runs.value = listOf(info(WorkInfo.State.ENQUEUED))
        inputs.conditions = DeviceConditions(unmeteredNetwork = false, batteryLow = false)
        withTimeout(5_000) { scrapes.status.first { it == ScrapeStatus.WaitingForWifi(inputs.counts) } }

        inputs.runs.value = listOf(info(WorkInfo.State.RUNNING))
        withTimeout(5_000) { scrapes.status.first { it == ScrapeStatus.Running(inputs.counts, null) } }

        inputs.pause = ScrapeResult.Paused(now + 60_000, PauseReason.DailyQuota)
        inputs.runs.value = listOf(info(WorkInfo.State.ENQUEUED))
        withTimeout(5_000) { scrapes.status.first { it == ScrapeStatus.Paused(inputs.counts, now + 60_000, PauseReason.DailyQuota) } }
    }

    // Settings' Pause, Resume and ask again.

    private suspend fun answerOf(call: (onDone: (Boolean) -> Unit) -> Unit): Boolean {
        val answer = kotlinx.coroutines.CompletableDeferred<Boolean>()
        call { answer.complete(it) }
        return withTimeout(5_000) { answer.await() }
    }

    @Test
    fun pauseStopsTheRunGivesNoWorkAsksForNoneAndResumeAsksForOne() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        awaitRun()
        assertTrue("stored", answerOf { scrapes.setPaused(true, it) })
        assertTrue(paused.value)
        assertEquals("the waiting run is cancelled", emptyList<WorkInfo>(), runs())
        assertNull("a worker started anyway gets no work", scrapes.scraperWork())
        scheduled.clear()
        scrapes.requestRun().join()
        assertEquals("a scan's request asks for nothing while paused", emptyList<String>(), scheduled)
        assertEquals(emptyList<WorkInfo>(), runs())
        withTimeout(5_000) { scrapes.status.first { it == ScrapeStatus.PausedByPerson(inputs.counts) } }

        assertTrue(answerOf { scrapes.setPaused(false, it) })
        assertEquals(listOf("enqueue"), scheduled)
        awaitRun()
        assertTrue(scrapes.scraperWork() != null)
        withTimeout(5_000) { scrapes.status.first { it !is ScrapeStatus.PausedByPerson } }
    }

    @Test
    fun thePersonsPauseComesBeforeTheRunsStates() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        inputs.runs.value = listOf(info(WorkInfo.State.RUNNING))
        inputs.pause = ScrapeResult.Paused(now + 60_000, PauseReason.DailyQuota)
        paused.value = true
        withTimeout(5_000) { scrapes.status.first { it == ScrapeStatus.PausedByPerson(inputs.counts) } }
    }

    @Test
    fun aPauseThatCannotBeStoredChangesNothingAndSaysSo() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        awaitRun()
        pauseWriteFails = true
        scheduled.clear()
        assertEquals(false, answerOf { scrapes.setPaused(true, it) })
        assertEquals("the run goes on", emptyList<String>(), scheduled)
        assertEquals(1, runs().size)
        assertTrue(logLines.toString(), logLines.any { "box art pause" in it && "IOException" in it })
    }

    @Test
    fun askingAgainMarksTheLibraryThenStartsAFreshRun() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        awaitRun()
        scheduled.clear()
        assertTrue(answerOf { scrapes.askAgain(AskAgain.All, null, it) })
        assertEquals("a run going would stop on a game it delivered coming back", listOf("cancel", "enqueue"), scheduled)
        assertEquals(1, runs().size)
        assertTrue(logLines.none { "Could not" in it })
    }

    @Test
    fun askingAgainWhilePausedOrSignedOutMarksButAsksForNoRun() = runBlocking<Unit> {
        val scrapes = graph()
        assertTrue("signed out: marked, nothing asked", answerOf { scrapes.askAgain(AskAgain.Missing, setOf("snes"), it) })
        assertEquals(emptyList<String>(), scheduled)
        signIn(scrapes)
        awaitRun()
        paused.value = true
        scheduled.clear()
        assertTrue(answerOf { scrapes.askAgain(AskAgain.Missing, null, it) })
        assertEquals("waits for Resume", emptyList<String>(), scheduled)
    }

    @Test
    fun aLibraryThatCannotBeMarkedSaysSoAndStartsNothing() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        awaitRun()
        libraryFails = true
        scheduled.clear()
        assertEquals(false, answerOf { scrapes.askAgain(AskAgain.All, null, it) })
        assertEquals(emptyList<String>(), scheduled)
        assertTrue(logLines.toString(), logLines.any { "box art ask again" in it })
    }

    @Test
    fun anInputThatFailsIsLoggedAndTheStatusStillComes() = runBlocking<Unit> {
        inputs.countsFail = true
        val scrapes = graph()
        signIn(scrapes)
        withTimeout(5_000) { scrapes.status.first { it == ScrapeStatus.Idle(null) } }
        assertTrue(logLines.toString(), logLines.any { it == "Could not read or start the box art counts (IllegalStateException)" })
    }

    @Test
    fun theApplicationIsTheWorkersHostAndGivesNoWorkWithoutAnAccount() = runBlocking<Unit> {
        val app = context as PlumApplication
        assertTrue(app is ScraperHost)
        assertNull(app.scraperWork())
    }

    private fun info(state: WorkInfo.State, id: UUID = UUID.randomUUID(), attempts: Int = 0) = WorkInfo(id, state, emptySet(), Data.EMPTY, Data.EMPTY, attempts, 0)

    @Test
    fun signedOutTheStatusReadsAndWatchesNothingElse() = runBlocking<Unit> {
        val scrapes = graph()
        val seen = CopyOnWriteArrayList<ScrapeStatus>()
        val collecting = scope.launch { scrapes.status.collect { seen += it } }
        withTimeout(5_000) { while (ScrapeStatus.SignedOut !in seen) delay(10) }

        inputs.libraryChanges.emit(Unit)
        delay(100)

        assertEquals("no COUNT query while signed out", 0, inputs.countReads.get())
        assertEquals("the game tables are not watched", 0, inputs.libraryChanges.subscriptionCount.value)
        assertEquals("WorkManager is not watched", 0, inputs.runs.subscriptionCount.value)

        signIn(scrapes)
        withTimeout(5_000) { while (ScrapeStatus.Idle(inputs.counts) !in seen) delay(10) }
        val reads = inputs.countReads.get()
        inputs.libraryChanges.emit(Unit)
        withTimeout(5_000) { while (inputs.countReads.get() == reads) delay(10) }

        accounts.signOutNow()
        withTimeout(5_000) { while (inputs.libraryChanges.subscriptionCount.value > 0 || seen.last() != ScrapeStatus.SignedOut) delay(10) }
        val afterSignOut = inputs.countReads.get()
        inputs.libraryChanges.emit(Unit)
        delay(100)
        assertEquals(afterSignOut, inputs.countReads.get())
        collecting.cancelAndJoin()
    }

    @Test
    fun retryingShowsOnlyForTheWorkWhoseLastRunEndedInARetry() = runBlocking<Unit> {
        val scrapes = graph()
        signIn(scrapes)
        val workId = UUID.randomUUID()
        // Started once, then stopped for a lost constraint: an attempt count, no retry.
        inputs.runs.value = listOf(info(WorkInfo.State.ENQUEUED, workId, attempts = 1))
        withTimeout(5_000) { scrapes.status.first { it == ScrapeStatus.Starting(inputs.counts) } }

        val work = checkNotNull(scrapes.scraperWork())
        work.onEnded(ScrapeRunEnd.Retry, workId)
        assertEquals(workId, mark.id)
        inputs.libraryChanges.emit(Unit)
        withTimeout(5_000) { scrapes.status.first { it == ScrapeStatus.Retrying(inputs.counts, null) } }

        work.onEnded(ScrapeRunEnd.Done, UUID.randomUUID())
        assertEquals("another end leaves the mark", workId, mark.id)
        checkNotNull(scrapes.scraperWork())
        assertNull("a run that starts clears it", mark.id)
        inputs.libraryChanges.emit(Unit)
        withTimeout(5_000) { scrapes.status.first { it == ScrapeStatus.Starting(inputs.counts) } }
    }

    @Test
    fun theRetryMarkOutlivesTheObjectInItsFile() {
        val id = UUID.randomUUID()
        SharedPreferencesRetryMark(context) { logLines += it }.set(id)
        assertEquals(id, SharedPreferencesRetryMark(context) { logLines += it }.get())
        SharedPreferencesRetryMark(context) { logLines += it }.set(null)
        assertNull(SharedPreferencesRetryMark(context) { logLines += it }.get())
    }

}
