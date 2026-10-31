package com.plumemu.scraper.api

import android.os.Build
import com.plumemu.scraper.DeveloperCredentials
import com.plumemu.scraper.UserCredentials
import com.plumemu.scraper.timing.RequestRecord
import com.plumemu.scraper.timing.ScrapeTimingLog
import com.plumemu.scraper.timing.msSince
import com.plumemu.scraper.timing.request
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sends one ScreenScraper API v2 call and maps the answer ([ApiStatus]).
 *
 * The caller passes only the parameters that describe the game; this class adds credentials,
 * softname and `output=json`, so they never reach a cache key or a stored body. Log lines go
 * through [Redact]; exception messages are never logged (only the exception class).
 */
internal class ScreenScraperApi(
    private val http: OkHttpClient,
    baseUrl: String,
    private val developer: DeveloperCredentials,
    private val user: UserCredentials?,
    private val softname: String,
    private val log: (String) -> Unit,
    private val timing: ScrapeTimingLog = ScrapeTimingLog.Off,
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    /** Values to blank out of anything stored, wherever the server echoes them. */
    val secrets: List<String> = listOfNotNull(developer.devId, developer.devPassword, user?.password)
        .filter { it.isNotBlank() }

    /**
     * Values to blank out of log lines: [secrets] plus the person's username. Not blanked from
     * stored bodies everywhere, where a username like "mario" would eat game titles; there
     * [Redact.text] blanks the account's `ssuser` id instead.
     */
    val logSecrets: List<String> = (secrets + listOfNotNull(user?.username)).filter { it.isNotBlank() }

    /**
     * Never throws for a network failure: a broken connection, a timeout, a cancelled read or a
     * runtime error inside OkHttp all become [ApiOutcome.NetworkError], logged by class.
     *
     * Every request writes one timing line (no URL in it). [waitedMillis] is what the caller
     * waited in the gate before this call, reported on that line.
     */
    suspend fun get(endpoint: String, params: Map<String, String>, waitedMillis: Long = 0L): ApiOutcome {
        val url = urlFor(endpoint, params)
        val logged = Redact.text(url.toString(), logSecrets)
        val startedAt = System.nanoTime()
        var headersMs: Long? = null
        var bytes = 0L
        val (code, body) = try {
            http.newCall(Request.Builder().url(url).get().build()).await {
                headersMs = msSince(startedAt)
                // Reading the whole body first is what string() does anyway; the buffer's size is the byte count.
                val source = it.body.source()
                source.request(Long.MAX_VALUE)
                bytes = source.buffer.size
                it.code to it.body.string()
            }
        } catch (e: IOException) {
            log("$endpoint: network error ${e.javaClass.simpleName}, url $logged")
            timing.request(failed(endpoint, startedAt, headersMs, bytes, waitedMillis, e))
            return ApiOutcome.NetworkError
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            log("$endpoint: network error ${e.javaClass.simpleName}, url $logged")
            timing.request(failed(endpoint, startedAt, headersMs, bytes, waitedMillis, e))
            return ApiOutcome.NetworkError
        }
        timing.request(RequestRecord(endpoint.removeSuffix(".php"), null, code, bytes, msSince(startedAt), headersMs, waitedMillis))
        val outcome = ApiStatus.map(code, body)
        if (outcome !is ApiOutcome.Ok) log("$endpoint: HTTP $code '${excerpt(body)}', url $logged")
        return outcome
    }

    private fun failed(endpoint: String, startedAt: Long, headersMs: Long?, bytes: Long, waitedMillis: Long, e: Exception) =
        RequestRecord(endpoint.removeSuffix(".php"), null, 0, bytes, msSince(startedAt), headersMs, waitedMillis, e.javaClass.simpleName)

    /** The start of an error body for a log line; redacted before it is cut, so no half secret shows. */
    fun excerpt(body: String): String =
        Redact.text(body, logSecrets).replace('\n', ' ').take(LOGGED_BODY_CHARS)

    private fun urlFor(endpoint: String, params: Map<String, String>): HttpUrl {
        val builder = withCredentials(base.newBuilder().addPathSegment(endpoint).build()).newBuilder()
            .addQueryParameter("output", "json")
        params.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }

    /**
     * Whether [url] may get the credentials: the configured API's scheme, host and port, or https
     * on a screenscraper.fr host. A media URL comes from the JSON; an http:// one would send the
     * passwords in clear (API 23-27 allow cleartext), a foreign host would receive them.
     */
    fun mayCarryCredentials(url: HttpUrl): Boolean =
        (url.scheme == base.scheme && url.host == base.host && url.port == base.port) ||
            (url.isHttps && (url.host == SCREENSCRAPER_HOST || url.host.endsWith(".$SCREENSCRAPER_HOST")))

    /**
     * [url] with the credentials and softname added (any already there replaced), for one
     * request; never stored or logged. Media URLs come stripped of them ([Redact.stripCredentials]).
     */
    fun withCredentials(url: HttpUrl): HttpUrl {
        val builder = url.newBuilder()
        Redact.CREDENTIAL_PARAMS.forEach { builder.removeAllQueryParameters(it) }
        builder.addQueryParameter("devid", developer.devId)
            .addQueryParameter("devpassword", developer.devPassword)
            .addQueryParameter("softname", softname)
        if (user != null && !user.isBlank) {
            builder.addQueryParameter("ssid", user.username)
            builder.addQueryParameter("sspassword", user.password)
        }
        return builder.build()
    }

    companion object {
        const val JEU_INFOS = "jeuInfos.php"
        const val JEU_RECHERCHE = "jeuRecherche.php"
        const val SSUSER_INFOS = "ssuserInfos.php"
        private const val SCREENSCRAPER_HOST = "screenscraper.fr"
        private const val LOGGED_BODY_CHARS = 160

        /**
         * The engine's client. Below API 25 it also trusts the bundled ISRG roots
         * ([ScreenScraperTrust]), built once per process; a trust that cannot be built is [log]ged
         * and the client keeps the system trust, so this never throws for it.
         */
        fun defaultHttpClient(log: (String) -> Unit, sdkInt: Int = Build.VERSION.SDK_INT): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .let { ScreenScraperTrust.apply(it, sdkInt, log) }
            .build()
    }
}

/**
 * Runs the call on OkHttp's threads and [read]s the response there too, never on the caller's
 * thread (no NetworkOnMainThreadException). Cancelling the coroutine cancels the call, also while
 * the body is being read: the read then fails and its exception is dropped.
 */
internal suspend fun <T> Call.await(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val value = try {
                    response.use(read)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                    return
                }
                continuation.resume(value)
            }
        },
    )
}
