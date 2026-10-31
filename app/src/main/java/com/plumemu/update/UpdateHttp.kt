package com.plumemu.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/** Why a request failed, in the words the panel uses. */
enum class NetFailure { Offline, Timeout, Http }

/** A request that failed; [code] is the HTTP status for [NetFailure.Http]. */
class UpdateNetException(
    val failure: NetFailure,
    val code: Int = 0,
    cause: Throwable? = null,
) : IOException("${failure.name}${if (code != 0) " $code" else ""}", cause)

/**
 * A response body being read; [length] is -1 when the server did not say. Close it when done, on the
 * thread that reads it. [cancel] is what another thread may call to end a read stalled in [stream]
 * ([onCancel], or [onClose] when the body has nothing lighter).
 */
class UpdateBody(
    val length: Long,
    val stream: InputStream,
    private val onCancel: (() -> Unit)? = null,
    private val onClose: () -> Unit,
) : AutoCloseable {
    override fun close() = onClose()

    fun cancel() = (onCancel ?: onClose)()
}

/** The two requests the update feature makes, both to the manifest's server. Tests give a fake. */
interface UpdateHttp {
    /** The text at [url], at most [maxBytes]; throws [UpdateNetException]. */
    suspend fun text(
        url: String,
        maxBytes: Int,
    ): String

    /** Opens [url] for reading; a cancelled coroutine closes it. Throws [UpdateNetException]. */
    suspend fun open(url: String): UpdateBody
}

/**
 * The app's [UpdateHttp] on OkHttp. Redirects are not followed: the privacy notes promise requests
 * go to the update host only, and a redirect would send the device elsewhere. No cookies, no cache,
 * no headers of ours.
 */
class OkHttpUpdateHttp(
    private val io: CoroutineDispatcher,
    /** Made on the first request, never while the main screen starts. */
    clientFactory: () -> OkHttpClient = ::defaultClient,
) : UpdateHttp {
    private val client by lazy(clientFactory)

    override suspend fun text(
        url: String,
        maxBytes: Int,
    ): String =
        withContext(io) {
            val call = client.newCall(Request.Builder().url(url).build())
            closeOnCancel(call::cancel) {
                mapped {
                    call.execute().use { response ->
                    if (!response.isSuccessful) throw UpdateNetException(NetFailure.Http, response.code)
                    val source = response.body.source()
                    // One byte past the limit says the body is too long without reading it all.
                    source.request(maxBytes.toLong() + 1)
                    if (source.buffer.size > maxBytes) throw UpdateNetException(NetFailure.Http, response.code)
                        source.buffer.readUtf8()
                    }
                }
            }
        }

    override suspend fun open(url: String): UpdateBody {
        // Set once execute() has answered: a cancel that lands after that, before the body reaches
        // the caller, would drop it unclosed (withContext throws instead of returning it).
        val opened = AtomicReference<UpdateBody?>(null)
        try {
            return withContext(io) {
                val call = client.newCall(Request.Builder().url(url).build())
                closeOnCancel(call::cancel) {
                    mapped {
                        val response = call.execute()
                        if (!response.isSuccessful) {
                            response.close()
                            throw UpdateNetException(NetFailure.Http, response.code)
                        }
                        val body = response.body
                        // Cancelling the call is what unblocks a read stalled in another thread. Only that
                        // is done from the other thread: closing the response there discards the rest of the
                        // body by reading the socket while this read still runs, and okio threw
                        // "Unbalanced enter/exit" out of the cancel.
                        UpdateBody(body.contentLength(), MappedStream(body.byteStream()), onCancel = call::cancel) {
                            call.cancel()
                            response.close()
                        }.also { opened.set(it) }
                    }
                }
            }
        } catch (e: CancellationException) {
            opened.get()?.close()
            throw e
        }
    }

    /** Reads that fail half-way are mapped too, so the downloader sees one kind of failure. */
    private class MappedStream(
        private val inner: InputStream,
    ) : InputStream() {
        override fun read(): Int = mapped { inner.read() }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = mapped { inner.read(b, off, len) }

        override fun close() = inner.close()
    }

    companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
    }
}

/**
 * Runs [block] (which may block its thread on the network) and calls [close] at once if the coroutine
 * is cancelled meanwhile, from another coroutine: a blocked socket read never sees a cancel by itself
 * (OkHttp's read timeout is 30 s). [close] is not called when [block] ends on its own.
 */
internal suspend fun <T> closeOnCancel(
    close: () -> Unit,
    block: suspend () -> T,
): T =
    coroutineScope {
        val finished = AtomicBoolean(false)
        val watcher =
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    if (!finished.get()) close()
                }
            }
        try {
            block()
        } finally {
            finished.set(true)
            watcher.cancel()
        }
    }

/** Turns a network exception into an [UpdateNetException]; anything else passes through. */
internal inline fun <T> mapped(block: () -> T): T =
    try {
        block()
    } catch (e: UpdateNetException) {
        throw e
    } catch (e: InterruptedIOException) {
        // OkHttp's timeouts are InterruptedIOExceptions (SocketTimeoutException is one too).
        throw UpdateNetException(NetFailure.Timeout, cause = e)
    } catch (e: IOException) {
        throw UpdateNetException(NetFailure.Offline, cause = e)
    }
