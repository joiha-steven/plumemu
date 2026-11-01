package com.plumemu.achievements.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * The person's Web API key. [toString] never shows it, so a key that reaches a log line or an
 * exception message by accident still does not leak.
 */
class WebApiKey(val value: String) {
    init {
        require(value.isNotBlank()) { "a Web API key is needed" }
    }

    override fun equals(other: Any?): Boolean = other is WebApiKey && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "WebApiKey(***)"
}

/** What one GET came back with: the body of a 200, or the failure. */
internal sealed interface Fetched {
    class Body(val text: String) : Fetched

    class Failed(val failure: RaResult.Failure) : Fetched
}

/**
 * Sends one Web API call with the key added (`y=`), waits once on 429 / 5xx, and maps the answer
 * ([RaStatus]). Nothing is built or sent before the first [get]: the OkHttp client is [http]'s
 * lazy value. Log lines carry the endpoint, the cause and the URL with `y` and `u` blanked;
 * exception messages are never logged, only the exception's class.
 */
internal class RaHttp(
    private val http: Lazy<OkHttpClient>,
    private val baseUrl: String,
    private val key: WebApiKey,
    private val log: (String) -> Unit,
    private val wait: suspend (millis: Long) -> Unit,
    private val clock: () -> Long,
    /** A Retry-After longer than this is not waited for: the call fails at once. */
    private val maxWaitSeconds: Long = MAX_WAIT_SECONDS,
    /** The wait when a 429 or 5xx gives no Retry-After. */
    private val defaultWaitSeconds: Long = DEFAULT_WAIT_SECONDS,
) {
    private val base: HttpUrl by lazy { baseUrl.toHttpUrl() }

    /** [isUserCall]: the call names a user (`u=`), so a 404 means that user is unknown. */
    suspend fun get(endpoint: String, params: Map<String, String>, isUserCall: Boolean): Fetched {
        val url = urlFor(endpoint, params)
        var waited = false
        while (true) {
            val reply = send(endpoint, url) ?: return Fetched.Failed(RaResult.NoConnection)
            if (reply.code == HTTP_OK) return Fetched.Body(reply.body)
            if (!waited && RaStatus.isRetryable(reply.code)) {
                val seconds = reply.retryAfterSeconds ?: defaultWaitSeconds
                if (seconds <= maxWaitSeconds) {
                    log("$endpoint: HTTP ${reply.code}, asking again in $seconds s")
                    wait(seconds * MILLIS_PER_SECOND)
                    waited = true
                    continue
                }
            }
            val failure = RaStatus.failure(reply.code, isUserCall, reply.retryAfterSeconds)
            log("$endpoint: ${failure.javaClass.simpleName} (HTTP ${reply.code}), url ${logged(url)}")
            return Fetched.Failed(failure)
        }
    }

    /** A failure found after the answer came (a body of the wrong shape), logged the same way. */
    fun logFailure(endpoint: String, failure: RaResult.Failure, detail: String) {
        log("$endpoint: ${failure.javaClass.simpleName} ($detail)")
    }

    private class Reply(val code: Int, val body: String, val retryAfterSeconds: Long?)

    /** One request; null when there is no connection, it timed out or the answer broke off. */
    private suspend fun send(endpoint: String, url: HttpUrl): Reply? = try {
        http.value.newCall(Request.Builder().url(url).get().build()).await { response ->
            Reply(response.code, response.body.string(), retryAfterSeconds(response))
        }
    } catch (e: IOException) {
        log("$endpoint: no connection (${e.javaClass.simpleName}), url ${logged(url)}")
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        // OkHttp can throw runtime errors of its own (a bad TLS setup on old Android).
        log("$endpoint: no connection (${e.javaClass.simpleName}), url ${logged(url)}")
        null
    }

    /** Retry-After as seconds (a number, or an HTTP date from now); null when absent or unreadable. */
    private fun retryAfterSeconds(response: Response): Long? {
        val raw = response.header("Retry-After")?.trim() ?: return null
        raw.toLongOrNull()?.let { return it.coerceAtLeast(0) }
        val date = response.headers.getDate("Retry-After") ?: return null
        return ((date.time - clock() + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).coerceAtLeast(0)
    }

    private fun urlFor(endpoint: String, params: Map<String, String>): HttpUrl {
        val builder = base.newBuilder().addPathSegment(endpoint)
        params.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        builder.addQueryParameter("y", key.value)
        return builder.build()
    }

    private fun logged(url: HttpUrl): String = Redact.text(url.toString(), listOf(key.value))

    companion object {
        const val MAX_WAIT_SECONDS = 10L
        const val DEFAULT_WAIT_SECONDS = 2L
        private const val HTTP_OK = 200
        private const val MILLIS_PER_SECOND = 1000L
    }
}
