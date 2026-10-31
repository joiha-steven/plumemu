package com.plumemu.scraper.media

import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.api.ApiOutcome
import com.plumemu.scraper.api.ApiStatus
import com.plumemu.scraper.api.Redact
import com.plumemu.scraper.api.ScreenScraperApi
import com.plumemu.scraper.api.await
import com.plumemu.scraper.hash.toLowerHex
import com.plumemu.scraper.timing.RequestRecord
import com.plumemu.scraper.timing.ScrapeTimingLog
import com.plumemu.scraper.timing.msSince
import com.plumemu.scraper.timing.request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.security.MessageDigest

/**
 * One media download: credentials added to the stored URL in memory, the body streamed into a
 * file while its md5 is computed, on OkHttp's thread. The caller runs it inside the request gate.
 *
 * Asked with a maximum width or height, ScreenScraper scales the picture down (`maxwidth`,
 * `maxheight`); the result no longer matches the JSON's md5, so the md5 is checked only for a
 * full-size download. The saver's decode is the check for a scaled one. A scaled box front is
 * also asked as a JPEG (`outputformat=jpg`, see [BoxFrontRequest]); a disc stays PNG.
 *
 * Log lines name the stored URL (no credentials) and the exception class, never its message.
 * Every request that goes out also writes one timing line (no URL in it).
 */
internal class MediaDownloader(
    private val http: OkHttpClient,
    private val api: ScreenScraperApi,
    private val log: (String) -> Unit,
    private val maxBytes: Long = MAX_MEDIA_BYTES,
    private val timing: ScrapeTimingLog = ScrapeTimingLog.Off,
) {
    sealed interface Fetch {
        /** The body is in the target file; for a full-size download its md5 matched (when one was given). */
        data object Done : Fetch

        /** Not usable; the target file was removed. */
        data object Broken : Fetch

        /** A non-200 answer (or no answer) the scraper decides about: pause, refusal, retry. */
        data class Stopped(val outcome: ApiOutcome) : Fetch
    }

    /** What one request moved and when its headers came, for the timing line. */
    private class Transfer {
        var code = 0
        var bytes = 0L
        var headersMs: Long? = null
        var error: String? = null
    }

    /**
     * Never throws for a network failure; the body is read on OkHttp's thread ([await]).
     * [waitedMillis] is what the caller waited in the gate before this call, for the timing line.
     */
    suspend fun fetch(media: MediaRef, target: File, maxWidth: Int? = null, maxHeight: Int? = null, waitedMillis: Long = 0L): Fetch {
        val stored = Redact.stripCredentials(media.url)
        val parsed = media.url.toHttpUrlOrNull() ?: return Fetch.Broken.also { log("media: not a URL, $stored") }
        if (!api.mayCarryCredentials(parsed)) {
            log("media: host or scheme is not ScreenScraper's, not downloaded: $stored")
            return Fetch.Broken
        }
        val url = api.withCredentials(parsed).newBuilder().apply {
            maxWidth?.let { setQueryParameter(MAX_WIDTH, it.toString()) }
            maxHeight?.let { setQueryParameter(MAX_HEIGHT, it.toString()) }
            if (BoxFrontRequest.asksJpeg(media.kind, scaled = maxWidth != null || maxHeight != null)) {
                setQueryParameter(OUTPUT_FORMAT, BoxFrontRequest.JPEG_FORMAT)
            }
        }.build()
        val expectedMd5 = media.md5.takeIf { maxWidth == null && maxHeight == null }
        val transfer = Transfer()
        val startedAt = System.nanoTime()
        val fetched = try {
            http.newCall(Request.Builder().url(url).get().build()).await { answer ->
                transfer.headersMs = msSince(startedAt)
                transfer.code = answer.code
                if (answer.code == HTTP_OK) save(answer, expectedMd5, target, stored, transfer) else refused(answer, stored, transfer)
            }
        } catch (e: IOException) {
            networkError(e, target, stored, transfer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            networkError(e, target, stored, transfer)
        }
        val kind = media.kind.name
        timing.request(RequestRecord("media", kind, transfer.code, transfer.bytes, msSince(startedAt), transfer.headersMs, waitedMillis, transfer.error))
        return fetched
    }

    private fun networkError(e: Exception, target: File, stored: String, transfer: Transfer): Fetch {
        transfer.error = e.javaClass.simpleName
        log("media: network error ${e.javaClass.simpleName}, $stored")
        target.delete()
        return Fetch.Stopped(ApiOutcome.NetworkError)
    }

    private fun refused(response: Response, stored: String, transfer: Transfer): Fetch {
        val text = try {
            response.peekBody(ERROR_BODY_BYTES).string()
        } catch (e: IOException) {
            e.javaClass.simpleName
        }
        transfer.bytes = text.toByteArray().size.toLong()
        log("media: HTTP ${response.code} '${api.excerpt(text)}', $stored")
        return Fetch.Stopped(ApiStatus.map(response.code, text))
    }

    private fun save(response: Response, expectedMd5: String?, target: File, stored: String, transfer: Transfer): Fetch {
        val digest = MessageDigest.getInstance("MD5")
        val written = try {
            target.parentFile?.mkdirs()
            copy(response, target, digest, transfer)
        } catch (e: IOException) {
            // The connection broke (or the run was cancelled): asking again later may work.
            return networkError(e, target, stored, transfer)
        }
        val problem = when {
            written > maxBytes -> "over $maxBytes bytes"
            written == 0L -> "empty"
            expectedMd5 != null && !expectedMd5.equals(digest.digest().toLowerHex(), ignoreCase = true) -> "md5 mismatch"
            else -> null
        } ?: return Fetch.Done
        log("media: $problem, $stored")
        target.delete()
        return Fetch.Broken
    }

    /** Bytes written, or more than [maxBytes] when the body is too big (copy stops there). */
    private fun copy(response: Response, target: File, digest: MessageDigest, transfer: Transfer): Long {
        var total = 0L
        try {
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (total <= maxBytes) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        total += read
                    }
                }
            }
        } finally {
            transfer.bytes = total
        }
        return total
    }

    companion object {
        private const val HTTP_OK = 200
        private const val ERROR_BODY_BYTES: Long = 4_096
        private const val BUFFER_BYTES = 64 * 1024
        private const val MAX_WIDTH = "maxwidth"
        private const val MAX_HEIGHT = "maxheight"
        private const val OUTPUT_FORMAT = "outputformat"

        /** A box front is a few hundred KB; anything this big is not one. */
        const val MAX_MEDIA_BYTES: Long = 16L * 1024 * 1024
    }
}
