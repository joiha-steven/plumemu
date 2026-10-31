package com.plumemu.update

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** The real client: HTTP errors, timeouts and a lost server each map to their own failure; no redirect is followed. */
class OkHttpUpdateHttpTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private val http =
        OkHttpUpdateHttp(Dispatchers.IO) {
            OkHttpUpdateHttp.defaultClient().newBuilder().readTimeout(300, TimeUnit.MILLISECONDS).build()
        }

    private suspend fun failureOf(block: suspend () -> Unit): UpdateNetException {
        try {
            block()
        } catch (e: UpdateNetException) {
            return e
        }
        fail("no failure")
        throw AssertionError()
    }

    @Test
    fun textIsRead() =
        runTest {
            server.enqueue(MockResponse.Builder().body("{\"schema\":1}").build())
            assertEquals("{\"schema\":1}", http.text(server.url("/update/latest.json").toString(), 1024))
        }

    @Test
    fun anHttpErrorCarriesItsCode() =
        runTest {
            server.enqueue(MockResponse.Builder().code(503).build())
            val e = failureOf { http.text(server.url("/update/latest.json").toString(), 1024) }
            assertEquals(NetFailure.Http, e.failure)
            assertEquals(503, e.code)
        }

    @Test
    fun aRedirectIsNotFollowed() =
        runTest {
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "https://elsewhere.example/latest.json").build())
            val e = failureOf { http.text(server.url("/update/latest.json").toString(), 1024) }
            assertEquals(NetFailure.Http, e.failure)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun aBodyPastTheLimitIsRefused() =
        runTest {
            server.enqueue(MockResponse.Builder().body("x".repeat(2048)).build())
            assertEquals(NetFailure.Http, failureOf { http.text(server.url("/update/latest.json").toString(), 1024) }.failure)
        }

    @Test
    fun aSilentServerIsATimeout() =
        runTest {
            server.enqueue(MockResponse.Builder().body("late body").throttleBody(1, 2, TimeUnit.SECONDS).build())
            assertEquals(NetFailure.Timeout, failureOf { http.text(server.url("/update/latest.json").toString(), 1024) }.failure)
        }

    @Test
    fun aClosedConnectionIsOffline() =
        runTest {
            server.enqueue(MockResponse.Builder().body(okio.Buffer().write(ByteArray(200_000))).onResponseBody(SocketEffect.CloseSocket()).build())
            val e = failureOf { http.text(server.url("/update/latest.json").toString(), 1_000_000) }
            assertTrue(e.failure == NetFailure.Offline)
        }

    @Test
    fun noServerAtAllIsOffline() =
        runTest {
            val url = server.url("/update/latest.json").toString()
            server.close()
            assertEquals(NetFailure.Offline, failureOf { http.text(url, 1024) }.failure)
        }

    @Test
    fun aFileIsOpenedWithItsLength() =
        runTest {
            server.enqueue(MockResponse.Builder().body("apkbytes").build())
            http.open(server.url("/update/p.apk").toString()).use { body ->
                assertEquals(8L, body.length)
                assertEquals("apkbytes", body.stream.readBytes().decodeToString())
            }
        }

    @Test
    fun noClientIsMadeBeforeTheFirstRequest() {
        var made = 0
        OkHttpUpdateHttp(Dispatchers.IO) { made++; OkHttpClient() }
        assertEquals(0, made)
    }

    /**
     * A cancel that lands after execute() has answered, before the body
     * reaches the caller, still closes the response (else its connection leaks until the GC finds it).
     */
    @Test
    fun aCancelRightAfterTheAnswerClosesTheResponse() =
        runBlocking {
            server.enqueue(MockResponse.Builder().body("apk bytes").build())
            val closed = AtomicBoolean(false)
            val caller = AtomicReference<Job?>(null)
            val cancelling =
                OkHttpUpdateHttp(Dispatchers.IO) {
                    OkHttpUpdateHttp.defaultClient().newBuilder().addInterceptor { chain ->
                        val response = chain.proceed(chain.request())
                        val inner = response.body
                        val tracked =
                            object : ResponseBody() {
                                override fun contentType() = inner.contentType()

                                override fun contentLength() = inner.contentLength()

                                override fun source() = inner.source()

                                override fun close() {
                                    closed.set(true)
                                    inner.close()
                                }
                            }
                        // The person's Cancel, exactly between the answer and the hand-over.
                        caller.get()!!.cancel()
                        response.newBuilder().body(tracked).build()
                    }.build()
                }
            val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) { cancelling.open(server.url("/update/a.apk").toString()) }
            caller.set(job)
            job.start()
            job.join()
            assertTrue(job.isCancelled)
            assertTrue("the response was closed", closed.get())
        }
}
