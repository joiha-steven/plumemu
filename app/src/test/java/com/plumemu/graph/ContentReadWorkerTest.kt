package com.plumemu.graph

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.plumemu.library.scan.ContentReadBudget
import com.plumemu.library.scan.ContentReadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Application of these tests: a [ContentReadHost] whose work each test sets. */
class ContentHostApp : Application(), ContentReadHost {
    @Volatile var work: ContentReadWork? = null

    override fun contentReadWork(): ContentReadWork? = work
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ContentHostApp::class)
internal class ContentReadWorkerTest {
    private val logs = mutableListOf<String>()
    private var boxArtRequests = 0
    private var budgetSeen: ContentReadBudget? = null

    private fun app(): ContentHostApp = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app(),
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private var playing = false
    private var reads = 0

    private fun host(read: suspend (ContentReadBudget) -> ContentReadResult) {
        app().work = ContentReadWork(
            readContent = { budget -> budgetSeen = budget; reads++; read(budget) },
            requestBoxArt = { boxArtRequests++ },
            log = { logs += it },
            holdForPlay = { playing },
        )
    }

    /** Reads [files] one by one as the pass does, asking the budget's stop before each; a game starts after [startsAfter] files. */
    private fun filesHost(files: Int, startsAfter: Int, backBeforeTheEnd: Boolean = false) = host { budget ->
        var read = 0
        for (i in 0 until files) {
            if (i == startsAfter) playing = true
            if (budget.stopBeforeNextFile?.invoke() == true) {
                if (backBeforeTheEnd) playing = false // the return's request comes now and is dropped (KEEP: this run still runs)
                return@host ContentReadResult(read = read, written = read, moreLeft = true)
            }
            read++
        }
        ContentReadResult(read = read, written = read)
    }

    @Test fun `a game started mid-run stops it between files with no follow-up, and the return's run goes on`() {
        filesHost(files = 5, startsAfter = 2)

        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertTrue("held, not queued again at once", uniqueWork().isEmpty())
        assertTrue(logs.any { it.contains("stopped between files while a game is played") })
        assertEquals("what was read still asks for box art", 1, boxArtRequests)

        playing = false // back from the game: the app's return asks for a run (HeldForPlayTest)
        filesHost(files = 3, startsAfter = 99)
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertTrue(uniqueWork().isEmpty())
    }

    @Test fun `a person back while the held run still ends gets a follow-up from it`() {
        filesHost(files = 5, startsAfter = 2, backBeforeTheEnd = true)

        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals("the resume is not lost", WorkInfo.State.ENQUEUED, uniqueWork().single().state)
        assertTrue(logs.any { it.contains("the person is back") })
    }

    @Test fun `a run that begins while a game is played reads nothing`() {
        playing = true
        host { ContentReadResult(read = 1, written = 1) }
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(0, reads)
        assertTrue(uniqueWork().isEmpty())
    }

    private fun runWorker(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder.from(app(), ContentReadWorker::class.java).build().doWork()
    }

    private fun uniqueWork(): List<WorkInfo> =
        WorkManager.getInstance(app()).getWorkInfosForUniqueWork(ContentReadScheduler.UNIQUE_WORK_NAME).get()

    @Test fun `a request needs a battery that is not low and no network, and keeps the run already waiting`() {
        ContentReadScheduler.enqueue(app())
        val first = uniqueWork().single()
        ContentReadScheduler.enqueue(app())

        assertEquals("plumemu.content", ContentReadScheduler.UNIQUE_WORK_NAME)
        assertEquals(listOf(first.id), uniqueWork().map { it.id })
        val constraints = ContentReadScheduler.request().workSpec.constraints
        assertTrue(constraints.requiresBatteryNotLow())
        assertEquals(NetworkType.NOT_REQUIRED, constraints.requiredNetworkType)
    }

    @Test fun `a run reads with the time budget`() {
        host { ContentReadResult() }
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(ContentReadWork.DEFAULT_BUDGET_MILLIS, budgetSeen?.maxMillis)
        assertEquals(null, budgetSeen?.maxBytes)
        assertTrue(ContentReadWork.DEFAULT_BUDGET_MILLIS < 10 * 60_000L)
    }

    @Test fun `files left and progress made queue one follow-up, and a second run adds none`() {
        host { ContentReadResult(read = 3, written = 2, failed = 1, moreLeft = true) }

        assertEquals(ListenableWorker.Result.success(), runWorker())
        val queued = uniqueWork()
        assertEquals(1, queued.size)
        assertEquals(WorkInfo.State.ENQUEUED, queued.single().state)

        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals("no second follow-up", 1, uniqueWork().size)
        assertTrue(logs.any { it.contains("already waits") })
    }

    @Test fun `failed reads alone count as progress`() {
        host { ContentReadResult(failed = 2, moreLeft = true) }
        runWorker()
        assertEquals(1, uniqueWork().size)
    }

    @Test fun `files left but nothing read or failed queue nothing`() {
        host { ContentReadResult(skipped = 40, moreLeft = true) }

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertTrue(uniqueWork().isEmpty())
    }

    @Test fun `reads that were all skipped are no progress so no follow-up and no box art`() {
        host { ContentReadResult(read = 50, written = 0, skipped = 50, moreLeft = true) }

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertTrue(uniqueWork().isEmpty())
        assertEquals(0, boxArtRequests)
    }

    @Test fun `a finished pass queues nothing`() {
        host { ContentReadResult(read = 5, written = 5, moreLeft = false) }
        runWorker()
        assertTrue(uniqueWork().isEmpty())
    }

    @Test fun `box art is asked for after a run that read files, not after one that read none`() {
        host { ContentReadResult(read = 2, written = 2) }
        runWorker()
        assertEquals(1, boxArtRequests)

        host { ContentReadResult(skipped = 3, failed = 1) }
        runWorker()
        assertEquals(1, boxArtRequests)
    }

    @Test fun `an error is logged with its class and the run fails without a crash`() {
        host { throw IllegalStateException("database closed") }

        assertEquals(ListenableWorker.Result.failure(), runWorker())

        assertTrue(logs.single().contains("IllegalStateException"))
        assertFalse("the message may carry a file name", logs.single().contains("database closed"))
        assertEquals(0, boxArtRequests)
    }

    @Test fun `a stop by the system is not logged as an error`() {
        host { throw CancellationException("stopped") }

        val thrown = runCatching { runWorker() }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertTrue(logs.isEmpty())
    }

    @Test fun `no work from the host means success and nothing done`() {
        app().work = null
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertTrue(uniqueWork().isEmpty())
    }

    @Test fun `a scan that ends asks for a run only while files remain`() = runBlocking {
        var rows = true
        var enqueued = 0
        val content = GraphContent(
            hasRows = { rows },
            read = { ContentReadResult() },
            enqueue = { enqueued++ },
            requestBoxArt = {},
            log = { logs += it },
        )

        content.scanEnded()
        rows = false
        content.scanEnded()

        assertEquals(1, enqueued)
    }

    @Test fun `a failed check for files is logged and asks for nothing`() = runBlocking {
        var enqueued = 0
        val content = GraphContent(
            hasRows = { throw IllegalStateException("closed") },
            read = { ContentReadResult() },
            enqueue = { enqueued++ },
            requestBoxArt = {},
            log = { logs += it },
        )

        content.scanEnded()

        assertEquals(0, enqueued)
        assertTrue(logs.single().contains("IllegalStateException"))
    }
}
