package com.plumemu.scraper.fallback

import com.plumemu.scraper.FallbackArt
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.GameInfo
import com.plumemu.scraper.GameMatch
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.OutcomeWords
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.TitleMatch
import com.plumemu.scraper.api.await
import com.plumemu.scraper.media.MediaDownloader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Box art of a Steam game from Steam's public image CDN, for a game
 * ScreenScraper has no box front for. The app id is the one in the game's `.steam` file, else the
 * one Steam's store search gives for the title when exactly one app is named like it.
 *
 * Both hosts get plain GET requests with no credentials, headers or cookies of ours: the client is
 * OkHttp's with no interceptor, and ScreenScraper's credentials are only ever added by
 * `ScreenScraperApi.withCredentials`, which this class never calls. One request at a time
 * ([oneAtATime]), the client's timeouts, no retry: a failure is [FallbackArt.Failed] and the run
 * decides.
 *
 * [storeSearchUrl] and [imagesUrl] are parameters so tests point them at a fake server.
 */
internal class SteamArt(
    private val http: OkHttpClient,
    private val storeSearchUrl: HttpUrl,
    private val imagesUrl: HttpUrl,
    private val log: (String) -> Unit,
) : ArtFallbackSource {
    private val oneAtATime = Mutex()

    override val name = "steam"

    override fun covers(request: ScrapeRequest) = request.systemId == STEAM_SYSTEM

    override suspend fun lookup(request: ScrapeRequest): FallbackArt {
        if (request.systemId != STEAM_SYSTEM) return FallbackArt.NotApplicable
        return oneAtATime.withLock {
            val appId = request.steamAppId
                ?: appIdInFile(request)
                ?: when (val byName = appIdByName(request.title)) {
                    is ByName.Found -> byName.appId
                    ByName.None -> return@withLock FallbackArt.NotFound(NONE)
                    ByName.Failed -> return@withLock FallbackArt.Failed(FAILED)
                }
            FallbackArt.Found(boxResult(appId), OutcomeWords(MATCHED, NONE, FAILED))
        }
    }

    override fun owns(url: String): Boolean = url.toHttpUrlOrNull()?.let {
        it.scheme == imagesUrl.scheme && it.host == imagesUrl.host && it.port == imagesUrl.port &&
            it.encodedPath.startsWith(imagesUrl.encodedPath)
    } ?: false

    private fun boxResult(appId: Long) = ScrapeResult.Matched(
        match = GameMatch("steam_$appId", MatchedBy.Fallback),
        // The title stays the one the game has; Steam's store gives nothing else we read.
        info = GameInfo(null, null, null, null, null, null, null, null),
        media = IMAGE_FILES.map { file ->
            MediaRef(MediaKind.BoxFront, url = imagesUrl.newBuilder().addPathSegment(appId.toString()).addPathSegment(file).build().toString(), format = "jpg")
        },
        details = GameDetails(),
    )

    /** The app id a `.steam` file holds; null for another file, an unreadable one (a pinned shortcut has no file) or other content. */
    private fun appIdInFile(request: ScrapeRequest): Long? {
        if (!request.rom.fileName.endsWith(".steam", ignoreCase = true)) return null
        val bytes = try {
            request.rom.open().use { readUpTo(it, MAX_FILE_BYTES) }
        } catch (e: IOException) {
            log("steam art: the .steam file could not be read (${e.javaClass.simpleName}); asking by name")
            return null
        }
        return parseAppId(bytes)
    }

    private sealed interface ByName {
        class Found(val appId: Long) : ByName
        data object None : ByName
        data object Failed : ByName
    }

    private suspend fun appIdByName(title: String): ByName {
        if (title.isBlank()) return ByName.None
        val url = storeSearchUrl.newBuilder()
            .addQueryParameter("term", title)
            .addQueryParameter("l", "english")
            .addQueryParameter("cc", "US")
            .build()
        val items = try {
            http.newCall(Request.Builder().url(url).get().build()).await { response ->
                if (response.code in HTTP_CLIENT_ERRORS && response.code != HTTP_TOO_MANY_REQUESTS) {
                    // A refusal of this one search (403, 414...) is "not found", not a failing source.
                    log("steam art: store search answered HTTP ${response.code}")
                    emptyList()
                } else if (response.code != HTTP_OK) {
                    log("steam art: store search answered HTTP ${response.code}")
                    null
                } else {
                    Json { ignoreUnknownKeys = true; coerceInputValues = true }
                        .decodeFromString<StoreSearch>(response.peekBody(MAX_SEARCH_BYTES).string()).items
                }
            }
        } catch (e: IOException) {
            log("steam art: store search network error ${e.javaClass.simpleName}")
            return ByName.Failed
        } catch (e: SerializationException) {
            log("steam art: store search answer was not understood")
            return ByName.Failed
        } ?: return ByName.Failed
        return pick(title, items)?.let { ByName.Found(it) } ?: ByName.None.also { log("steam art: no single app named like the title") }
    }

    override suspend fun download(media: MediaRef, target: File): MediaDownload = oneAtATime.withLock {
        val url = media.url.toHttpUrlOrNull() ?: return@withLock MediaDownload.Broken
        try {
            http.newCall(Request.Builder().url(url).get().build()).await { response ->
                when {
                    response.code == HTTP_OK -> save(response.body.byteStream(), target)
                    // The CDN answers 404 for a picture the game does not have; the next one is tried.
                    response.code in HTTP_CLIENT_ERRORS && response.code != HTTP_TOO_MANY_REQUESTS -> {
                        log("steam art: HTTP ${response.code} for ${url.encodedPathSegments.lastOrNull()}")
                        MediaDownload.Broken
                    }
                    else -> {
                        log("steam art: HTTP ${response.code} from the image CDN")
                        MediaDownload.ServerError(response.code)
                    }
                }
            }
        } catch (e: IOException) {
            log("steam art: image network error ${e.javaClass.simpleName}")
            target.delete()
            MediaDownload.NetworkDown
        }
    }

    /** A failure to read the body is the network's (it propagates); a failure to write the file is the disk's. */
    private fun save(body: InputStream, target: File): MediaDownload {
        var total = 0L
        try {
            target.parentFile?.mkdirs()
            val file = try {
                target.outputStream()
            } catch (e: IOException) {
                throw DiskFailure(e)
            }
            body.use { input ->
                file.use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (total <= MediaDownloader.MAX_MEDIA_BYTES) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        try {
                            output.write(buffer, 0, read)
                        } catch (e: IOException) {
                            throw DiskFailure(e)
                        }
                        total += read
                    }
                }
            }
        } catch (e: DiskFailure) {
            log("steam art: the picture could not be written (${e.cause?.javaClass?.simpleName})")
            target.delete()
            return MediaDownload.StorageFailed
        }
        if (total == 0L || total > MediaDownloader.MAX_MEDIA_BYTES) {
            log("steam art: picture is ${if (total == 0L) "empty" else "too big"}")
            target.delete()
            return MediaDownload.Broken
        }
        return MediaDownload.Done(target)
    }

    private class DiskFailure(cause: IOException) : RuntimeException(cause)

    @Serializable
    internal class StoreSearch(val items: List<Item> = emptyList())

    @Serializable
    internal class Item(
        @SerialName("id") val id: Long? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("type") val type: String? = null,
    )

    companion object {
        const val STORE_SEARCH_URL = "https://store.steampowered.com/api/storesearch/"
        const val IMAGES_URL = "https://cdn.akamai.steamstatic.com/steam/apps/"

        /** The one Plumemu system this source covers: its games are Steam apps. */
        const val STEAM_SYSTEM = "steam"

        const val MATCHED = "matched-steam-cdn"
        const val NONE = "steam-none"
        const val FAILED = "steam-failed"

        /** Portrait first, its 2x, then the landscape header every Steam app has. */
        private val IMAGE_FILES = listOf("library_600x900.jpg", "library_600x900_2x.jpg", "header.jpg")

        private const val HTTP_OK = 200
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val HTTP_CLIENT_ERRORS = 400..499
        private const val MAX_FILE_BYTES = 1025
        private const val MAX_SEARCH_BYTES = 256L * 1024
        private const val BUFFER_BYTES = 32 * 1024
        private val DIGITS = Regex("^[0-9]{1,10}$")

        /**
         * The app id in a `.steam` file's bytes (as the platforms module reads it, without
         * depending on it): digits only after a byte order mark, NULs (UTF-16) and spaces; 1 to Int.MAX_VALUE.
         */
        fun parseAppId(bytes: ByteArray): Long? {
            val kept = bytes.filter { it != NUL && it !in BOM_BYTES }.toByteArray()
            val text = String(kept, Charsets.ISO_8859_1).trim()
            if (!DIGITS.matches(text)) return null
            return text.toLong().takeIf { it in 1..Int.MAX_VALUE }
        }

        private const val NUL: Byte = 0
        private val BOM_BYTES = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0xFF.toByte(), 0xFE.toByte())

        /**
         * The one app whose name passes [TitleMatch] for [title]; null for none or for several. Like
         * the ScreenScraper name search: of several, those named exactly like the title win, unless a
         * loser is another release (a remake), where nothing is guessed.
         */
        internal fun pick(title: String, items: List<Item>): Long? {
            var candidates = items
                .filter { it.id != null && it.name != null && (it.type == null || it.type == "app") && TitleMatch.matches(title, listOf(it.name)) }
                .distinctBy { it.id }
            if (candidates.size > 1) {
                val (exact, losers) = candidates.partition { TitleMatch.exactName(title, listOf(it.name)) }
                if (exact.isNotEmpty() && !losers.any { TitleMatch.looksLikeOtherRelease(listOf(it.name)) }) candidates = exact
            }
            return candidates.singleOrNull()?.id
        }

        private fun readUpTo(input: InputStream, limit: Int): ByteArray {
            val buffer = ByteArray(limit)
            var filled = 0
            while (filled < limit) {
                val read = input.read(buffer, filled, limit - filled)
                if (read < 0) break
                filled += read
            }
            return buffer.copyOf(filled)
        }
    }
}
