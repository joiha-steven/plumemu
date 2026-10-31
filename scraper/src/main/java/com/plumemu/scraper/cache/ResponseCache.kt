package com.plumemu.scraper.cache

import com.plumemu.scraper.hash.toLowerHex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * ScreenScraper answers kept on disk for [ttlMillis] (30 days), one file per request.
 *
 * Only two answers are kept: a found game (HTTP 200) and "not found" (HTTP 404), so a game
 * ScreenScraper does not know is not asked again on every run. Errors are never kept.
 *
 * The request key is built by the caller from the request parameters WITHOUT credentials
 * ([keyOf]); bodies are the trimmed answer ([com.plumemu.scraper.api.CachedAnswer], a few KB
 * instead of 1-2 MB: no header, no ssuser, media URLs stripped of credentials), so no file holds
 * a password. A file is written to a temporary name and renamed, so a reader never sees half of it.
 */
internal class ResponseCache(
    private val dir: File,
    private val clock: () -> Long,
    private val ioDispatcher: CoroutineDispatcher,
    private val log: (String) -> Unit,
    private val ttlMillis: Long = TTL_MILLIS,
) {
    /** Orders a rename against a delete of an expired file of the same name. */
    private val lock = Any()

    sealed interface Hit {
        data class Found(val body: String) : Hit
        data object NotFound : Hit
    }

    suspend fun get(key: String): Hit? = withContext(ioDispatcher) {
        val file = fileFor(key)
        if (!file.exists()) return@withContext null
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            log("response cache: could not read ${file.name}: ${e.javaClass.simpleName}")
            return@withContext null
        }
        val hit = if (isFresh(text)) hitOf(text) else null
        // Expired, from the future (clock moved back) or unreadable: drop it and ask again.
        if (hit == null) deleteIfStale(file)
        hit
    }

    private fun isFresh(text: String): Boolean {
        val header = text.substringBefore('\n').split(' ')
        val age = header.getOrNull(1)?.toLongOrNull()?.let { clock() - it } ?: return false
        return header.firstOrNull() == FORMAT && age in 0..ttlMillis
    }

    private fun hitOf(text: String): Hit? = when (text.substringBefore('\n').split(' ').getOrNull(2)) {
        STATUS_FOUND -> Hit.Found(text.substringAfter('\n'))
        STATUS_NOT_FOUND -> Hit.NotFound
        else -> null
    }

    /**
     * Deletes [file] only if it is still stale when read again under the lock: a write may have
     * renamed a fresh answer over it since it was read.
     */
    private fun deleteIfStale(file: File) = synchronized(lock) {
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            null
        }
        if (text == null || !isFresh(text) || hitOf(text) == null) file.delete()
    }

    suspend fun putFound(key: String, body: String) = write(key, STATUS_FOUND, body)

    suspend fun putNotFound(key: String) = write(key, STATUS_NOT_FOUND, "")

    private suspend fun write(key: String, status: String, body: String) = withContext(ioDispatcher) {
        val file = fileFor(key)
        try {
            dir.mkdirs()
            val tmp = File.createTempFile(file.name, ".tmp", dir)
            tmp.writeText("$FORMAT ${clock()} $status\n$body", Charsets.UTF_8)
            if (!synchronized(lock) { tmp.renameTo(file) }) {
                tmp.delete()
                log("response cache: could not store ${file.name}")
            }
        } catch (e: IOException) {
            log("response cache: could not store ${file.name}: ${e.javaClass.simpleName}")
        }
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return File(dir, digest.toLowerHex() + ".resp")
    }

    companion object {
        const val TTL_MILLIS: Long = 30L * 24 * 60 * 60 * 1000
        /**
         * v3: trimmed answers ([com.plumemu.scraper.api.CachedAnswer]) with every text field
         * and the screenshot, title screen and manual references. A file of another format (v1
         * held the raw answer, v2 a narrower trim) is a miss and is deleted when read.
         */
        private const val FORMAT = "v3"
        private const val STATUS_FOUND = "200"
        private const val STATUS_NOT_FOUND = "404"

        /**
         * A request key from the endpoint and its parameters, sorted, names and values
         * URL-encoded so a title holding `&` or `=` cannot collide with another request. The
         * caller passes only the parameters that describe the game; credentials are added later,
         * by the API client.
         */
        fun keyOf(endpoint: String, params: Map<String, String>): String =
            endpoint + "?" + params.toSortedMap().entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
