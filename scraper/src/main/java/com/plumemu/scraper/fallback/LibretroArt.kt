package com.plumemu.scraper.fallback

import com.plumemu.scraper.FallbackArt
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.GameInfo
import com.plumemu.scraper.GameMatch
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.OutcomeWords
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.TitleMatch
import com.plumemu.scraper.api.await
import com.plumemu.scraper.media.MediaDownloader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.security.MessageDigest

/**
 * Box art of a retro game from libretro's public thumbnail server, for
 * a game ScreenScraper has no box front for. Only systems in [LibretroSystems]; any other game is
 * [FallbackArt.NotApplicable] before a request is made.
 *
 * Two tries: the picture named like the ROM file (libretro names its pictures like the No-Intro and
 * Redump entries, special characters replaced by `_`), asked with a HEAD request; when the server
 * has none (404), the system's folder listing, cached per system ([INDEX_DIR] in the cache folder,
 * [INDEX_MAX_AGE_MILLIS] old at most), from which [pick] takes the one entry for the game.
 *
 * Plain requests with no credential, cookie or header of ours (this class never touches
 * `ScreenScraperApi`), one at a time ([oneAtATime]), the client's timeouts, no retry. A failure is
 * [FallbackArt.Failed]; the run decides what it means.
 *
 * [baseUrl] is a parameter so tests point it at a fake server.
 */
internal class LibretroArt(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val cacheDir: File,
    private val clock: () -> Long,
    private val ioDispatcher: CoroutineDispatcher,
    private val log: (String) -> Unit,
) : ArtFallbackSource {
    private val oneAtATime = Mutex()

    override val name = "libretro"

    override fun covers(request: ScrapeRequest) = LibretroSystems.playlistOf(request.systemId) != null

    /** The listing of the system asked last only: a listing is megabytes, the disk file keeps the others. An empty one is remembered too, so a broken folder is not downloaded again for every game. */
    private var lastIndex: Pair<String, Index>? = null

    /** Systems whose listing was empty or refused (4xx), with when: not downloaded again for [INDEX_MAX_AGE_MILLIS] while this scraper lives, however the systems interleave. */
    private val noListing = HashMap<String, Long>()

    private class Index(val fetchedAtMillis: Long, val names: List<String>)

    override suspend fun lookup(request: ScrapeRequest): FallbackArt {
        val playlist = LibretroSystems.playlistOf(request.systemId) ?: return FallbackArt.NotApplicable
        return oneAtATime.withLock {
            val stem = stemOf(request.rom.fileName)
            if (stem.isNotBlank()) {
                val name = sanitise(stem)
                when (val code = headStatus(pictureUrl(playlist, name))) {
                    HTTP_OK -> return@withLock found(playlist, name)
                    // A refusal of this one name (403, 414, 410...) is a miss, like a 404: the listing is tried. Only 429, 5xx and no answer are failures.
                    in HTTP_CLIENT_ERRORS -> if (code == HTTP_TOO_MANY_REQUESTS) {
                        log("libretro art: HTTP 429 for the picture named like the file")
                        return@withLock FallbackArt.Failed(FAILED)
                    }
                    else -> {
                        log("libretro art: HTTP $code for the picture named like the file")
                        return@withLock FallbackArt.Failed(FAILED)
                    }
                }
            }
            val index = when (val loaded = indexOf(playlist)) {
                is Loaded.Ok -> loaded.index
                Loaded.Missing -> return@withLock FallbackArt.NotFound(NONE)
                Loaded.Failed -> return@withLock FallbackArt.Failed(FAILED)
            }
            val hack = request.hack || isHack(request.rom.fileName) || isHack(request.title)
            val title = request.title.ifBlank { stem }
            val picked = pick(title, hack, index.names, request.regions)
            if (picked == null) {
                log("libretro art: no single entry named like the title")
                FallbackArt.NotFound(NONE)
            } else {
                found(playlist, picked)
            }
        }
    }

    override fun owns(url: String): Boolean = url.toHttpUrlOrNull()?.let {
        it.scheme == baseUrl.scheme && it.host == baseUrl.host && it.port == baseUrl.port &&
            it.encodedPath.startsWith(baseUrl.encodedPath)
    } ?: false

    private fun pictureUrl(playlist: String, name: String): HttpUrl = baseUrl.newBuilder()
        .addPathSegment(playlist).addPathSegment(BOXARTS).addPathSegment("$name.png").build()

    private fun indexUrl(playlist: String): HttpUrl = baseUrl.newBuilder()
        .addPathSegment(playlist).addPathSegment(BOXARTS).addPathSegment("").build()

    private fun found(playlist: String, name: String) = FallbackArt.Found(
        ScrapeResult.Matched(
            // The folder of the pictures: a short hash keeps it to the characters the media folder allows.
            match = GameMatch("libretro_" + sha256Hex("$playlist/$name").take(ID_HEX_DIGITS), MatchedBy.Fallback),
            info = GameInfo(null, null, null, null, null, null, null, null),
            media = listOf(MediaRef(MediaKind.BoxFront, url = pictureUrl(playlist, name).toString(), format = "png")),
            details = GameDetails(),
        ),
        OutcomeWords(MATCHED, NONE, FAILED),
    )

    /** The HTTP status of a HEAD request; -1 for a network failure (logged). */
    private suspend fun headStatus(url: HttpUrl): Int = try {
        http.newCall(Request.Builder().url(url).head().build()).await { it.code }
    } catch (e: IOException) {
        log("libretro art: network error ${e.javaClass.simpleName}")
        -1
    }

    private sealed interface Loaded {
        class Ok(val index: Index) : Loaded
        data object Missing : Loaded
        data object Failed : Loaded
    }

    /** The system's entries: memory, then the file in the cache folder, then the server. A failure is never stored. */
    private suspend fun indexOf(playlist: String): Loaded {
        val now = clock()
        noListing[playlist]?.takeIf { now - it in 0..INDEX_MAX_AGE_MILLIS }?.let { return Loaded.Missing }
        lastIndex?.takeIf { it.first == playlist && now - it.second.fetchedAtMillis in 0..INDEX_MAX_AGE_MILLIS }?.let {
            return Loaded.Ok(it.second)
        }
        readFile(playlist)?.takeIf { now - it.fetchedAtMillis in 0..INDEX_MAX_AGE_MILLIS }?.let {
            lastIndex = playlist to it
            return Loaded.Ok(it)
        }
        val names = try {
            http.newCall(Request.Builder().url(indexUrl(playlist)).get().build()).await { response ->
                when {
                    response.code == HTTP_OK -> parseListing(response.body.byteStream())
                    // 404, or any other refusal of this request (403 on a big listing...): the system has no listing for us.
                    response.code in HTTP_CLIENT_ERRORS && response.code != HTTP_TOO_MANY_REQUESTS -> {
                        log("libretro art: folder listing answered HTTP ${response.code}")
                        emptyList()
                    }
                    else -> {
                        log("libretro art: folder listing answered HTTP ${response.code}")
                        null
                    }
                }
            }
        } catch (e: IOException) {
            log("libretro art: folder listing network error ${e.javaClass.simpleName}")
            return Loaded.Failed
        } ?: return Loaded.Failed
        if (names.isEmpty()) {
            noListing[playlist] = now
            log("libretro art: warning: no usable folder listing (empty or refused; did its format change?); this system is not asked again until the app restarts or 14 days pass")
            return Loaded.Missing
        }
        val index = Index(now, names)
        lastIndex = playlist to index
        writeFile(playlist, index)
        return Loaded.Ok(index)
    }

    private fun indexFile(playlist: String) = File(File(cacheDir, INDEX_DIR), "${sha256Hex(playlist).take(ID_HEX_DIGITS)}.txt")

    private suspend fun readFile(playlist: String): Index? = withContext(ioDispatcher) {
        try {
            val file = indexFile(playlist)
            if (!file.isFile) return@withContext null
            val lines = file.readLines()
            val at = lines.firstOrNull()?.toLongOrNull() ?: return@withContext null
            Index(at, lines.drop(1)).takeIf { it.names.isNotEmpty() }
        } catch (e: IOException) {
            log("libretro art: the cached listing could not be read (${e.javaClass.simpleName})")
            null
        }
    }

    private suspend fun writeFile(playlist: String, index: Index) = withContext(ioDispatcher) {
        try {
            val file = indexFile(playlist)
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.bufferedWriter().use { out ->
                out.write(index.fetchedAtMillis.toString())
                index.names.forEach { out.write("\n"); out.write(it) }
            }
            if (!temp.renameTo(file)) temp.delete()
        } catch (e: IOException) {
            log("libretro art: the listing could not be cached (${e.javaClass.simpleName})")
        }
    }

    override suspend fun download(media: MediaRef, target: File): MediaDownload = oneAtATime.withLock {
        val url = media.url.toHttpUrlOrNull() ?: return@withLock MediaDownload.Broken
        try {
            http.newCall(Request.Builder().url(url).get().build()).await { response ->
                when {
                    response.code == HTTP_OK -> save(response.body.byteStream(), target)
                    response.code in HTTP_CLIENT_ERRORS && response.code != HTTP_TOO_MANY_REQUESTS -> {
                        log("libretro art: HTTP ${response.code} for a picture")
                        MediaDownload.Broken
                    }
                    else -> {
                        log("libretro art: HTTP ${response.code} from the thumbnail server")
                        MediaDownload.ServerError(response.code)
                    }
                }
            }
        } catch (e: IOException) {
            log("libretro art: picture network error ${e.javaClass.simpleName}")
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
            log("libretro art: the picture could not be written (${e.cause?.javaClass?.simpleName})")
            target.delete()
            return MediaDownload.StorageFailed
        }
        if (total == 0L || total > MediaDownloader.MAX_MEDIA_BYTES) {
            log("libretro art: picture is ${if (total == 0L) "empty" else "too big"}")
            target.delete()
            return MediaDownload.Broken
        }
        return MediaDownload.Done(target)
    }

    private class DiskFailure(cause: IOException) : RuntimeException(cause)

    companion object {
        const val BASE_URL = "https://thumbnails.libretro.com/"
        const val INDEX_DIR = "libretro-listings"
        const val INDEX_MAX_AGE_MILLIS = 14L * 24 * 60 * 60 * 1000

        const val MATCHED = "matched-libretro"
        const val NONE = "libretro-none"
        const val FAILED = "libretro-failed"

        private const val BOXARTS = "Named_Boxarts"
        private const val HTTP_OK = 200
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val HTTP_CLIENT_ERRORS = 400..499
        private const val BUFFER_BYTES = 32 * 1024
        private const val ID_HEX_DIGITS = 20
        private const val MAX_LISTING_BYTES = 32L * 1024 * 1024

        private val SPECIAL = Regex("""[&*/:\\`<>?|"]""")
        private val HREF = Regex("""href="([^"]+)\.png"""", RegexOption.IGNORE_CASE)
        private val GROUP = Regex("""\(([^)]*)\)|\[([^\]]*)]""")
        private val LANGUAGES = Regex("""[A-Za-z]{2}(,[A-Za-z]{2})*""")
        private val REVISION = Regex("""(rev|v)\s?[0-9][0-9a-z.]*|rev\s?[a-z]""", RegexOption.IGNORE_CASE)
        private val DISC = Regex("""disc\s*([0-9]+)(\s+of\s+[0-9]+)?""", RegexOption.IGNORE_CASE)
        private val PRE_RELEASE = Regex("""(beta|proto|prototype|demo|kiosk|sample|preview|promo)(\s.*)?""", RegexOption.IGNORE_CASE)
        private val HACK = Regex("""[(\[]\s*hack\b""", RegexOption.IGNORE_CASE)

        /** The file name libretro gives a No-Intro or Redump name: each character of [SPECIAL] becomes an underscore. */
        fun sanitise(name: String): String = name.replace(SPECIAL, "_")

        /** A ROM's file name without its extension (the part after the last dot, up to five characters, no spaces). */
        fun stemOf(fileName: String): String {
            val dot = fileName.lastIndexOf('.')
            if (dot <= 0) return fileName
            val extension = fileName.substring(dot + 1)
            return if (extension.length in 1..5 && ' ' !in extension) fileName.substring(0, dot) else fileName
        }

        fun isHack(text: String): Boolean = HACK.containsMatchIn(text)

        /** Region codes of the box-art setting to the region words No-Intro puts in `(...)`. */
        private val REGION_WORDS = mapOf(
            "us" to "USA", "wor" to "World", "eu" to "Europe", "jp" to "Japan", "fr" to "France", "de" to "Germany",
            "es" to "Spain", "it" to "Italy", "br" to "Brazil", "kr" to "Korea", "au" to "Australia", "cn" to "China",
            "tw" to "Taiwan", "ru" to "Russia", "uk" to "UK", "ca" to "Canada", "asi" to "Asia",
        )

        private val ALL_REGION_WORDS = setOf(
            "USA", "Europe", "Japan", "World", "Australia", "Asia", "Brazil", "Canada", "China", "France", "Germany",
            "Hong Kong", "Italy", "Korea", "Netherlands", "Russia", "Spain", "Sweden", "Taiwan", "Scandinavia", "UK",
            "Denmark", "Finland", "Norway", "Greece", "Portugal", "Poland", "Latin America", "Mexico", "India",
            "Argentina", "Austria", "Belgium", "Switzerland", "Ireland", "New Zealand", "Unknown", "Israel", "Turkey",
        )

        /** The file names (without `.png`) a folder listing's links name, percent and HTML escapes undone. */
        fun parseListing(body: InputStream): List<String> {
            val names = LinkedHashSet<String>()
            var read = 0L
            body.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    read += line.length
                    if (read > MAX_LISTING_BYTES) break
                    for (match in HREF.findAll(line)) {
                        val raw = match.groupValues[1].replace("&amp;", "&").replace("+", "%2B")
                        val name = try {
                            URLDecoder.decode(raw, "UTF-8")
                        } catch (e: IllegalArgumentException) {
                            continue
                        }
                        if ('/' !in name) names += name
                    }
                }
            }
            return names.toList()
        }

        /** What of a name's `(...)` and `[...]` groups is neither region, language nor revision: what makes it another release. */
        private fun extras(name: String): Set<String> = GROUP.findAll(name).map { (it.groupValues[1] + it.groupValues[2]).trim() }
            .filterNot { group -> group.split(", ").all { it in ALL_REGION_WORDS } || LANGUAGES.matches(group) || REVISION.matches(group) || DISC.matches(group) }
            .map { it.lowercase() }.toSet()

        /** The disc number in a name's `(Disc N)`, 0 for none: a multi-disc game's box is the first disc's. */
        private fun discOf(name: String): Int = GROUP.findAll(name).firstNotNullOfOrNull { DISC.matchEntire((it.groupValues[1] + it.groupValues[2]).trim())?.groupValues?.get(1)?.toIntOrNull() } ?: 0

        private fun regionTokens(name: String): List<String> = GROUP.findAll(name).map { it.groupValues[1].trim() }
            .filter { group -> group.isNotEmpty() && group.split(", ").all { it in ALL_REGION_WORDS } }
            .flatMap { it.split(", ") }.toList()

        /**
         * The one entry of [names] that is [title]: those [TitleMatch] accepts (a hack file takes only
         * hack entries, any other only non-hack ones); of several the exactly-named win unless a loser
         * looks like another release; entries differing in more than region, language and revision are
         * other releases: the plain one wins unless one looks like a remake or a year, else nothing.
         * Among the rest the first of [regions] present wins, then an entry without a revision.
         */
        internal fun pick(title: String, hack: Boolean, names: List<String>, regions: List<String>): String? {
            var candidates = names.filter { TitleMatch.matches(title, listOf(it)) && isHack(it) == hack }
            if (candidates.size > 1) {
                val (exact, losers) = candidates.partition { TitleMatch.exactName(title, listOf(it)) }
                if (exact.isNotEmpty() && !losers.any { TitleMatch.looksLikeOtherRelease(listOf(it)) }) candidates = exact
            }
            val groups = candidates.groupBy { extras(it) }
            val chosen = if (groups.size <= 1) candidates else {
                val plain = groups[emptySet()] ?: return null
                if (candidates.any { TitleMatch.looksLikeOtherRelease(listOf(it)) }) return null
                plain
            }
            if (chosen.isEmpty()) return null
            // Only betas, prototypes, demos... of the game exist here: not its box.
            if (chosen.all { name -> extras(name).any { PRE_RELEASE.matches(it) } }) return null
            val order = regions.mapNotNull { REGION_WORDS[it.lowercase()] }
            fun rank(name: String) = regionTokens(name).minOfOrNull { token -> order.indexOf(token).takeIf { it >= 0 } ?: order.size } ?: order.size
            return chosen.sortedWith(compareBy({ rank(it) }, { discOf(it) }, { GROUP.findAll(it).any { g -> REVISION.matches((g.groupValues[1] + g.groupValues[2]).trim()) } }, { it })).first()
        }

        private fun sha256Hex(text: String) =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
