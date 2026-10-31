package com.plumemu.update

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.UpdateFixtures.apkBytes
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Cancel with the real OkHttp client: a download whose body stalls must end at once
 * on Cancel, not when the 30 s read timeout fires, and the next Update press must download in full.
 * Robolectric only for org.json in the manifest reader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class UpdatesCancelTest {
    @get:Rule val folder = TemporaryFolder()

    private val server = MockWebServer()
    /** What escaped the scope's jobs: kept here and failed in [stop], so it can never leak into a later test's runTest. */
    private val uncaught = mutableListOf<Throwable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> synchronized(uncaught) { uncaught += e } })
    private val installer = FakeInstaller()
    private val logs = mutableListOf<String>()

    @Before fun start() = server.start()

    @After
    fun stop() {
        // Wait for every job to end before the server and the folder go, then fail on anything that escaped.
        runBlocking { withTimeout(10_000) { scope.coroutineContext.job.cancelAndJoin() } }
        server.close()
        assertEquals("uncaught in the update scope", emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
    }

    private fun updates(
        manifest: UpdateManifest,
        http: UpdateHttp = OkHttpUpdateHttp(Dispatchers.IO) { OkHttpUpdateHttp.defaultClient() },
    ): Updates =
        Updates(
            scope = scope,
            io = Dispatchers.IO,
            checkDaily = { false },
            network = { NetworkKind.Wifi },
            http = http,
            manifestUrl = server.url("/update/latest.json").toString(),
            installedVersionCode = { UpdateFixtures.INSTALLED },
            deviceSdk = 23,
            store = MemoryUpdateStore(UpdateRecord(lastSuccessAt = 1L, found = manifest)),
            downloader = ApkDownloader(http, File(folder.root, "updates"), Dispatchers.IO),
            inspector = FakeInspector(),
            installer = installer,
            wallClock = { 2L },
            info = {},
            log = { synchronized(logs) { logs += it } },
        )

    private fun foundAt(apkUrl: String): UpdateManifest =
        UpdateManifests.read(UpdateFixtures.manifestJson(), UpdateFixtures.MANIFEST_URL, UpdateFixtures.INSTALLED, 23)
            .let { (it as ManifestVerdict.Newer).manifest }
            .copy(apkUrl = apkUrl)

    @Test
    fun cancelEndsAStalledDownloadAndTheNextPressCompletes() =
        runBlocking {
            // The first answer sends 1 KB every 10 s: without closing the call the read would hang for 30 s.
            server.enqueue(MockResponse.Builder().body(okio.Buffer().write(apkBytes)).throttleBody(1024, 10, TimeUnit.SECONDS).build())
            server.enqueue(MockResponse.Builder().body(okio.Buffer().write(apkBytes)).build())
            val updates = updates(foundAt(server.url("/update/plumemu.apk").toString()))
            updates.onFront().join()

            val first = updates.startUpdate()!!
            withTimeout(5_000) { while (server.requestCount < 1) delay(10) }
            delay(200)
            updates.cancel()
            val second = updates.startUpdate()
            withTimeout(3_000) { first.join() }
            assertTrue("the first download ended on Cancel", first.isCancelled)

            withTimeout(10_000) { second!!.join() }
            assertEquals(2, server.requestCount)
            assertEquals(1, installer.committed.size)
            assertArrayEquals(apkBytes, installer.committed.single().first)
            assertTrue("not overwritten by the first job's Cancelled: ${updates.view.value.step}", updates.view.value.step !is UpdateStep.Cancelled)
        }

    /**
     * The first download takes half a second to end after Cancel (a slow socket close). The next press
     * waits for it: else the first's end would delete the shared file and say Cancelled over the second.
     */
    @Test
    fun theNextPressWaitsForTheCancelledDownloadToEnd() =
        runBlocking {
            val opened = CountDownLatch(1)
            val http =
                object : UpdateHttp {
                    var opens = 0

                    override suspend fun text(
                        url: String,
                        maxBytes: Int,
                    ): String = throw UpdateNetException(NetFailure.Http, 404)

                    override suspend fun open(url: String): UpdateBody {
                        if (++opens > 1) return UpdateBody(apkBytes.size.toLong(), ByteArrayInputStream(apkBytes)) {}
                        val closed = CountDownLatch(1)
                        val stalled =
                            object : InputStream() {
                                override fun read(): Int {
                                    opened.countDown()
                                    closed.await()
                                    throw IOException("closed")
                                }
                            }
                        return UpdateBody(apkBytes.size.toLong(), stalled) { thread { Thread.sleep(SLOW_CLOSE_MS); closed.countDown() } }
                    }
                }
            val updates = updates(foundAt(UpdateFixtures.apkUrl), http)
            updates.onFront().join()
            val first = updates.startUpdate()!!
            assertTrue(opened.await(5, TimeUnit.SECONDS))
            updates.cancel()
            val second = updates.startUpdate()!!
            withTimeout(10_000) { second.join() }
            assertTrue("the first ended before the second downloaded", first.isCompleted)
            assertEquals(1, installer.committed.size)
            assertArrayEquals(apkBytes, installer.committed.single().first)
            delay(SLOW_CLOSE_MS)
            assertTrue("not overwritten by the first job's Cancelled: ${updates.view.value.step}", updates.view.value.step !is UpdateStep.Cancelled)
        }

    private companion object {
        const val SLOW_CLOSE_MS = 500L
    }
}
