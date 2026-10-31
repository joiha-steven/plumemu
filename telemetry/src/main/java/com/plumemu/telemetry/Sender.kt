package com.plumemu.telemetry

import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** What the module does with a batch after the server answered. */
internal enum class Answer {
    /** 2xx: stored (unknown items dropped by the server, still accepted). Remove it. */
    Accepted,

    /**
     * 400, 413 and every other 4xx but 429 (404 / 405 until the server is deployed): the batch
     * can never be accepted. Remove it and log; sending it again would loop.
     */
    Dropped,

    /** 429, 5xx, no network: keep it and try again later with back-off. */
    Retry,
}

/**
 * Posts one JSON body to an endpoint of the API and reads the answer. Blocking: the caller is on
 * a background thread. Never logs the body or a header; a failure is one line with the endpoint,
 * the status or the exception's class.
 */
internal class Sender(
    /** Called once, on the first request (a background thread): building a client loads the trust store. */
    clientFactory: () -> OkHttpClient,
    private val base: HttpUrl,
    private val log: (String) -> Unit,
) {
    private val client by lazy(clientFactory)

    fun post(
        path: String,
        body: JsonObject,
    ): Answer {
        val url = base.newBuilder().encodedPath(path).build()
        val request =
            Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(JSON))
                .build()
        return try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                when {
                    code in 200..299 -> Answer.Accepted
                    code == 429 || code >= 500 -> {
                        log("Statistics: $path answered $code; kept for later")
                        Answer.Retry
                    }
                    else -> {
                        log("Statistics: $path answered $code; batch dropped")
                        Answer.Dropped
                    }
                }
            }
        } catch (e: IOException) {
            log("Statistics: $path not reached (${e.javaClass.simpleName}); kept for later")
            Answer.Retry
        } catch (e: RuntimeException) {
            // OkHttp or the platform refusing the call; never thrown at the app (failures never reach the person).
            log("Statistics: $path failed (${e.javaClass.simpleName}); kept for later")
            Answer.Retry
        }
    }

    companion object {
        const val EVENTS = "/api/v1/events"
        const val DECLINED = "/api/v1/declined"
        const val CRASHES = "/api/v1/crashes"
        const val REPORTS = "/api/v1/reports"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
