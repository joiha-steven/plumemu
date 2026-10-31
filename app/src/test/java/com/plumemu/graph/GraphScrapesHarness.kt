package com.plumemu.graph

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.plumemu.account.SignInResult
import com.plumemu.coredata.account.ScraperAccountStore
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.library.scrape.LibraryScrapeSource
import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.AccountLimits
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.UserCredentials
import com.plumemu.scraper.account.PasswordCipher
import com.plumemu.scraper.work.ScrapeScheduler
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before

/** What the graph's run and limits tests share: the graph, its fakes, a signed-in account. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal abstract class GraphScrapesHarness {
    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected val storeJob = Job()
    protected val scopeJob = SupervisorJob()
    protected val scope = CoroutineScope(Dispatchers.Default + scopeJob)
    protected val logLines = CopyOnWriteArrayList<String>()
    protected val store: ScraperAccountStore by lazy { ScraperAccountStore.create(context, CoroutineScope(Dispatchers.IO + storeJob)) }
    protected val db = Room.inMemoryDatabaseBuilder(context, PlumDatabase::class.java).build()
    protected val made = CopyOnWriteArrayList<FakeScraper>()
    protected var check: AccountCheck = AccountCheck.Ok(maxThreads = 1, requestsToday = 0, maxRequestsPerDay = 20_000)
    protected val inputs = FakeInputs()
    protected val mark = FakeRetryMark()
    protected val now = 1_000_000L
    protected lateinit var scrapes: GraphScrapes
    protected lateinit var accounts: GraphScraperAccounts

    /** The stored pause (`library.box_art_paused`) and whether writing it fails. */
    protected val paused = MutableStateFlow(false)
    protected var pauseWriteFails = false
    protected var libraryFails = false

    /** Every cancel and enqueue the graph asked of WorkManager, in order. */
    protected val scheduled = CopyOnWriteArrayList<String>()

    protected fun graph(isAvailable: Boolean = true): GraphScrapes {
        accounts =
            GraphScraperAccounts(
                isAvailable = isAvailable,
                store = { store },
                cipher = { FakeCipher },
                makeScraper = { user -> blockMaking?.await(); FakeScraper(user).also { made += it } },
                pauseStore = { null },
                scope = scope,
                io = Dispatchers.IO,
                main = Dispatchers.Unconfined,
                clock = { now },
                log = { logLines += it },
                onSignedIn = { scrapes.requestRun() },
                onSignedOut = { scrapes.cancelRuns() },
            )
        scrapes =
            GraphScrapes(
                accounts = accounts,
                librarySource = {
                    if (libraryFails) error("no database")
                    LibraryScrapeSource(db, { error("no file is opened") }, { scrapes.viewedSystem.value }, { "us" })
                },
                mediaDir = { File(context.noBackupFilesDir, "test-media") },
                enqueue = {
                    scheduled += "enqueue"
                    ScrapeScheduler.enqueue(context, wifiOnly = true)
                },
                cancel = {
                    scheduled += "cancel"
                    ScrapeScheduler.cancel(context)
                },
                inputs = inputs,
                retryMark = { mark },
                scope = scope,
                io = Dispatchers.IO,
                clock = { now },
                log = { logLines += it },
                paused = paused,
                writePaused = { isPaused ->
                    if (pauseWriteFails) throw java.io.IOException("disk full")
                    paused.value = isPaused
                },
                main = Dispatchers.Unconfined,
            )
        return scrapes
    }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        scopeJob.cancelAndJoin()
        storeJob.cancelAndJoin()
        db.close()
    }

    protected fun runs(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(ScrapeScheduler.UNIQUE_WORK_NAME).get().filter { !it.state.isFinished }

    protected suspend fun signIn(scrapes: GraphScrapes) {
        assertEquals(SignInResult.SignedIn("plumtester"), accounts.check("plumtester", PASSWORD))
    }

    /** The sign-in's request runs in the graph's scope: wait for the run it enqueues. */
    protected suspend fun awaitRun(): WorkInfo =
        withTimeout(5_000) {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ScrapeScheduler.UNIQUE_WORK_NAME).first { list -> list.any { !it.state.isFinished } }
        }.first { !it.state.isFinished }


    /** What the fake scraper's next check gives as limits (null: none), set by the limits tests. */
    protected var limitsToGive: AccountLimits? = null

    /** While set, making a scraper waits for it (the Keystore being slow). */
    @Volatile protected var blockMaking: java.util.concurrent.CountDownLatch? = null


    protected class FakeInputs : ScrapeStatusInputs {
        override val runs = MutableStateFlow<List<WorkInfo>>(emptyList())
        override val libraryChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val counts = ScrapeCounts(withBoxFront = 3, listed = 10, pending = 4)
        val countReads = AtomicInteger()
        @Volatile var countsFail = false
        @Volatile var pause: ScrapeResult.Paused? = null
        @Volatile var conditions = DeviceConditions(unmeteredNetwork = true, batteryLow = false)

        override suspend fun counts(): ScrapeCounts {
            countReads.incrementAndGet()
            return if (countsFail) error("database closed") else counts
        }

        override fun pause(): ScrapeResult.Paused? = pause

        override fun conditions(): DeviceConditions = conditions
    }

    protected class FakeRetryMark : RetryMark {
        @Volatile var id: UUID? = null

        override fun get(): UUID? = id

        override fun set(workId: UUID?) {
            id = workId
        }
    }

    // `user` is unused: the fake only has to match the real constructor.
    protected inner class FakeScraper(@Suppress("unused") val user: UserCredentials) : Scraper {
        override val available: Boolean = true

        override suspend fun scrape(request: ScrapeRequest): ScrapeResult = error("not used")

        override suspend fun download(media: MediaRef, target: File, maxWidth: Int?, maxHeight: Int?): MediaDownload = error("not used")

        val checks = AtomicInteger()
        override val accountLimits = kotlinx.coroutines.flow.MutableStateFlow<AccountLimits?>(null)

        override suspend fun checkAccount(): AccountCheck {
            checks.incrementAndGet()
            limitsToGive?.let { accountLimits.value = it }
            return check
        }
    }

    /** Stands in for the Keystore: the password itself is the "ciphertext" (never stored anywhere real). */
    protected object FakeCipher : PasswordCipher {
        override fun encrypt(plain: String): String = "c:$plain"

        override fun decrypt(stored: String): String? = stored.removePrefix("c:")
    }

    companion object {
        const val PASSWORD = "test-password-not-real"
    }
}
