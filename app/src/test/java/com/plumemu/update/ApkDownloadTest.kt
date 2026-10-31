package com.plumemu.update

import com.plumemu.update.UpdateFixtures.apkBytes
import com.plumemu.update.UpdateFixtures.apkUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The download checks size and SHA-256 as it writes; whatever fails leaves no file behind. */
class ApkDownloadTest {
    @get:Rule val folder = TemporaryFolder()

    private val manifest =
        UpdateManifest(41, "0.0.3-alpha", apkUrl, apkBytes.size.toLong(), UpdateFixtures.sha256(apkBytes), 23, "2026-10-23", emptyList(), null)
    private val http = FakeUpdateHttp().apply { files[apkUrl] = apkBytes }
    private val dir by lazy { File(folder.root, "updates") }

    private fun downloader() = ApkDownloader(http, dir, Dispatchers.Unconfined)

    private fun leftFiles(): List<String> = dir.listFiles()?.map { it.name }.orEmpty()

    @Test
    fun aWholeMatchingFileIsKeptWithProgressUpToItsSize() =
        runTest {
            val progress = mutableListOf<Long>()
            val result = downloader().download(manifest) { done, total -> progress += done; assertEquals(manifest.size, total) }
            val file = (result as DownloadResult.Done).file
            assertArrayEquals(apkBytes, file.readBytes())
            assertEquals(listOf("plumemu-41.apk"), leftFiles())
            assertEquals(0L, progress.first())
            assertEquals(manifest.size, progress.last())
            // Once per whole percent at most, not once per buffer.
            assertTrue(progress.size <= 102)
        }

    @Test
    fun aWrongHashDeletesTheFile() =
        runTest {
            val result = downloader().download(manifest.copy(sha256 = "0".repeat(64))) { _, _ -> }
            assertEquals(UpdateFailure.HashMismatch, (result as DownloadResult.Failed).failure)
            assertEquals(emptyList<String>(), leftFiles())
        }

    @Test
    fun aShorterFileThanPromisedDeletesTheFile() =
        runTest {
            http.files[apkUrl] = apkBytes.copyOf(apkBytes.size - 10)
            http.claimedLength = -1
            val result = downloader().download(manifest) { _, _ -> }
            assertEquals(UpdateFailure.SizeMismatch, (result as DownloadResult.Failed).failure)
            assertEquals(emptyList<String>(), leftFiles())
        }

    @Test
    fun aLongerFileStopsAtTheLimitAndIsDeleted() =
        runTest {
            http.files[apkUrl] = apkBytes + ByteArray(1_000_000)
            http.claimedLength = -1
            val result = downloader().download(manifest) { _, _ -> }
            assertEquals(UpdateFailure.SizeMismatch, (result as DownloadResult.Failed).failure)
            assertEquals(emptyList<String>(), leftFiles())
        }

    @Test
    fun aServerLengthThatDisagreesIsRefusedBeforeReading() =
        runTest {
            http.claimedLength = 12
            val result = downloader().download(manifest) { _, _ -> }
            assertEquals(UpdateFailure.SizeMismatch, (result as DownloadResult.Failed).failure)
        }

    @Test
    fun aConnectionLostHalfWayIsOfflineAndLeavesNothing() =
        runTest {
            http.failAfterBytes = 100_000
            val result = downloader().download(manifest) { _, _ -> }
            assertEquals(UpdateFailure.Offline, (result as DownloadResult.Failed).failure)
            assertEquals(emptyList<String>(), leftFiles())
        }

    @Test
    fun anHttpErrorIsReported() =
        runTest {
            http.files.clear()
            val result = downloader().download(manifest) { _, _ -> }
            assertEquals(UpdateFailure.Http, (result as DownloadResult.Failed).failure)
        }

    @Test
    fun aCancelStopsTheDownloadAndDeletesThePart() =
        runBlocking {
            val reachedHalf = CompletableDeferred<Unit>()
            val job =
                launch(Dispatchers.IO) {
                    ApkDownloader(http, dir, Dispatchers.IO).download(manifest) { done, _ ->
                        if (done > manifest.size / 2) {
                            reachedHalf.complete(Unit)
                            // Hold the copy here until the cancel lands.
                            Thread.sleep(50)
                        }
                    }
                }
            withTimeout(5_000) { reachedHalf.await() }
            job.cancel()
            job.join()
            assertTrue(job.isCancelled)
            assertEquals(emptyList<String>(), leftFiles())
        }

    /**
     * Cancel must end a stalled read without closing the body from the cancelling thread: OkHttp's
     * Response.close() there discards the rest of the body by reading the socket while the download's
     * own read still runs, and okio threw IllegalStateException("Unbalanced enter/exit") out of the
     * cancel. The job failed with it instead of ending cancelled, the scope's handler got it, and in
     * the unit tests it surfaced in the next runTest of the JVM (UpdatesFrontTest,
     * "UncaughtExceptionsBeforeTest"). Here a body behaves the same way.
     */
    @Test
    fun aCancelEndsAStalledReadWithoutClosingTheBodyFromAnotherThread() =
        runBlocking {
            val reading = CountDownLatch(1)
            val cancelled = CountDownLatch(1)
            val readsNow = AtomicBoolean(false)
            val okHttpLike =
                object : UpdateHttp {
                    override suspend fun text(
                        url: String,
                        maxBytes: Int,
                    ): String = error("not asked")

                    override suspend fun open(url: String): UpdateBody {
                        val stalled =
                            object : InputStream() {
                                override fun read(): Int {
                                    readsNow.set(true)
                                    reading.countDown()
                                    cancelled.await(5, TimeUnit.SECONDS)
                                    readsNow.set(false)
                                    throw IOException("Canceled")
                                }

                                override fun read(
                                    b: ByteArray,
                                    off: Int,
                                    len: Int,
                                ): Int = read()
                            }
                        return UpdateBody(apkBytes.size.toLong(), stalled, onCancel = { cancelled.countDown() }) {
                            check(!readsNow.get()) { "Unbalanced enter/exit" }
                        }
                    }
                }
            val uncaught = mutableListOf<Throwable>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> synchronized(uncaught) { uncaught += e } })
            try {
                var cause: Throwable? = null
                val job = scope.launch { ApkDownloader(okHttpLike, dir, Dispatchers.IO).download(manifest) { _, _ -> } }
                job.invokeOnCompletion { cause = it }
                assertTrue(reading.await(5, TimeUnit.SECONDS))
                job.cancel()
                withTimeout(5_000) { job.join() }
                assertTrue("ended as a cancel, not $cause", cause is CancellationException)
                assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
                assertEquals(emptyList<String>(), leftFiles())
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun leftoversAreCleared() {
        dir.mkdirs()
        File(dir, "plumemu-40.apk.part").writeText("torn")
        downloader().clearLeftovers()
        assertEquals(emptyList<String>(), leftFiles())
    }
}
