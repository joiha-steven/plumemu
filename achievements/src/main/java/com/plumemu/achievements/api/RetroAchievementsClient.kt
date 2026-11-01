package com.plumemu.achievements.api

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * RetroAchievements' Web API with one person's [WebApiKey]: the four calls Plumemu uses.
 *
 * Building one does nothing: no client, no file, no request. Each call sends its request when it
 * is made, and never throws for a network or server failure: it returns a [RaResult.Failure].
 * Calls after the sign-in name the user by ULID ([RaProfile.ulid]).
 */
class RetroAchievementsClient internal constructor(
    private val http: RaHttp,
    private val cache: GameListCache,
    private val clock: () -> Long,
) {
    /**
     * [cacheDir]: where the game lists are saved (made on the first save). [httpClient] is only
     * read on the first call; the default is one client shared by every instance.
     */
    constructor(
        key: WebApiKey,
        cacheDir: File,
        log: (String) -> Unit = { android.util.Log.w(LOG_TAG, it) },
        httpClient: Lazy<OkHttpClient> = sharedHttpClient,
        baseUrl: String = BASE_URL,
    ) : this(
        RaHttp(httpClient, baseUrl, key, log, wait = { delay(it) }, clock = System::currentTimeMillis),
        GameListCache(cacheDir, log),
        System::currentTimeMillis,
    )

    /** The user named [user] (a user name or a ULID): the sign-in check. */
    suspend fun profile(user: String): RaResult<RaProfile> {
        val result = fetch(PROFILE, mapOf("u" to user), isUserCall = true) { RaParsers.profile(it) }
        return when {
            result is RaResult.Ok && result.value == null -> {
                http.logFailure(PROFILE, RaResult.UnknownUser, "empty profile")
                RaResult.UnknownUser
            }
            else -> result.map { checkNotNull(it) }
        }
    }

    /**
     * Every game of RetroAchievements' system [consoleId] with its MD5 hashes. Saved for
     * [GAME_LIST_MAX_AGE_MILLIS]: a fresh copy sends no request. When the request fails and a
     * copy is saved, that copy is returned with [RaResult.Ok.stale] naming the failure; except
     * [RaResult.WrongKey], returned as it is so a key refused later is noticed.
     */
    suspend fun gameList(consoleId: Int): RaResult<RaGameList> = cache.withConsoleLock(consoleId) {
        val now = clock()
        val saved = cache.read(consoleId)
        // A copy saved "in the future" (the clock was moved back) is not trusted as fresh.
        if (saved != null && saved.savedAtMillis <= now && now - saved.savedAtMillis < GAME_LIST_MAX_AGE_MILLIS) {
            return@withConsoleLock RaResult.Ok(saved.list)
        }
        val params = mapOf("i" to consoleId.toString(), "h" to "1", "f" to "1")
        when (val fetched = fetch(GAME_LIST, params, isUserCall = false) { RaParsers.gameList(consoleId, it) }) {
            is RaResult.Ok -> {
                cache.write(fetched.value, now)
                fetched
            }
            RaResult.WrongKey -> fetched
            is RaResult.Failure ->
                if (saved != null) RaResult.Ok(saved.list, Stale(saved.savedAtMillis, fetched)) else fetched
        }
    }

    /** Game [gameId]'s achievements and the progress of the user [ulid]. */
    suspend fun gameProgress(gameId: Int, ulid: String): RaResult<RaGameProgress> =
        fetch(GAME_PROGRESS, mapOf("g" to gameId.toString(), "u" to ulid), isUserCall = true) { RaParsers.gameProgress(it) }

    /**
     * Every game the user [ulid] has progress in, all pages read ([COMPLETION_PAGE_SIZE] each)
     * until the offset reaches the total. One failed page fails the whole call.
     */
    suspend fun completion(ulid: String): RaResult<List<RaCompletion>> {
        val all = LinkedHashMap<Int, RaCompletion>()
        var offset = 0
        repeat(MAX_COMPLETION_PAGES) {
            val params = mapOf("u" to ulid, "c" to COMPLETION_PAGE_SIZE.toString(), "o" to offset.toString())
            val page = when (val result = fetch(COMPLETION, params, isUserCall = true) { RaParsers.completionPage(it) }) {
                is RaResult.Ok -> result.value
                is RaResult.Failure -> return result
            }
            page.results.forEach { all[it.gameId] = it }
            // By the rows sent, not the rows kept: a page of unreadable rows still moves on.
            offset += page.rowCount
            if (page.rowCount == 0 || offset >= page.total) return RaResult.Ok(all.values.toList())
        }
        val failure = RaResult.BadAnswer("more than $MAX_COMPLETION_PAGES pages")
        http.logFailure(COMPLETION, failure, failure.why)
        return failure
    }

    private suspend fun <T> fetch(
        endpoint: String,
        params: Map<String, String>,
        isUserCall: Boolean,
        parse: (String) -> T,
    ): RaResult<T> = when (val fetched = http.get(endpoint, params, isUserCall)) {
        is Fetched.Failed -> fetched.failure
        is Fetched.Body -> try {
            RaResult.Ok(parse(fetched.text))
        } catch (e: UnexpectedAnswer) {
            val failure = RaResult.BadAnswer(e.message.orEmpty())
            http.logFailure(endpoint, failure, failure.why)
            failure
        }
    }

    companion object {
        const val BASE_URL: String = "https://retroachievements.org/API/"
        const val LOG_TAG: String = "PlumemuRA"
        const val GAME_LIST_MAX_AGE_MILLIS: Long = 14L * 24 * 60 * 60 * 1000
        const val COMPLETION_PAGE_SIZE: Int = 500

        internal const val PROFILE = "API_GetUserProfile.php"
        internal const val GAME_LIST = "API_GetGameList.php"
        internal const val GAME_PROGRESS = "API_GetGameInfoAndUserProgress.php"
        internal const val COMPLETION = "API_GetUserCompletionProgress.php"

        /** A bound on paging, so an answer whose total never comes is not followed for ever. */
        private const val MAX_COMPLETION_PAGES = 200

        /** One client for every instance, built on the first call that needs it. */
        val sharedHttpClient: Lazy<OkHttpClient> = lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
