package com.plumemu.scraper

import com.plumemu.scraper.AnswerChecks.isGameOfSystem
import com.plumemu.scraper.AnswerChecks.isHashAnswerOk
import com.plumemu.scraper.AnswerChecks.isJunk
import com.plumemu.scraper.fallback.ArtFallbacks
import com.plumemu.scraper.AnswerChecks.isNameMatch
import com.plumemu.scraper.api.ApiEnvelope
import com.plumemu.scraper.api.ApiGame
import com.plumemu.scraper.api.ApiOutcome
import com.plumemu.scraper.api.CachedAnswer
import com.plumemu.scraper.api.GameMapper
import com.plumemu.scraper.api.SsUser
import com.plumemu.scraper.api.ScreenScraperApi
import com.plumemu.scraper.api.ScreenScraperApi.Companion.JEU_INFOS
import com.plumemu.scraper.api.ScreenScraperApi.Companion.JEU_RECHERCHE
import com.plumemu.scraper.api.ScreenScraperApi.Companion.SSUSER_INFOS
import com.plumemu.scraper.api.ScreenScraperJson
import com.plumemu.scraper.cache.ResponseCache
import com.plumemu.scraper.gate.ParisTime
import com.plumemu.scraper.gate.RequestGate
import com.plumemu.scraper.hash.FileHashCache
import com.plumemu.scraper.hash.RomHasher
import com.plumemu.scraper.media.MediaDownloader
import com.plumemu.scraper.timing.GameTiming
import com.plumemu.scraper.timing.RunTiming
import com.plumemu.scraper.timing.guarded
import com.plumemu.scraper.timing.msSince
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

/**
 * The real scraper: hash lookup first (`jeuInfos.php`), then a name search (`jeuRecherche.php`)
 * when the hash is unknown and the system is known. Answers are cached for 30 days, trimmed to
 * what is read ([CachedAnswer]); requests go through one [RequestGate] per scraper, so make one
 * scraper per session and share it.
 */
internal class ScreenScraperScraper(
    private val config: ScraperConfig,
    http: OkHttpClient = ScreenScraperApi.defaultHttpClient(config.log),
    fullHashLimitBytes: Long = RomHasher.FULL_HASH_LIMIT_BYTES,
) : Scraper {
    override val available: Boolean = true

    private val log = config.log
    private val timing = config.timingLog.guarded()
    private val api = ScreenScraperApi(http, config.baseUrl, config.developer, config.user, config.softname, log, timing)
    private val gate = RequestGate(config.clock)

    override val requestSlots: StateFlow<Int> get() = gate.threadLimits

    private val limits = MutableStateFlow<AccountLimits?>(null)
    override val accountLimits: StateFlow<AccountLimits?> get() = limits.asStateFlow()
    private val responses = ResponseCache(File(config.cacheDir, RESPONSES_DIR), config.clock, config.ioDispatcher, log)
    private val hasher = RomHasher(
        cache = config.hashCache
            ?: FileHashCache(File(File(config.cacheDir, HASHES_DIR), FileHashCache.FILE_NAME), config.ioDispatcher, log),
        ioDispatcher = config.ioDispatcher,
        fullHashLimitBytes = fullHashLimitBytes,
    )

    private val downloader = MediaDownloader(http, api, log, timing = timing)
    private val fallbacks = ArtFallbacks.forConfig(config, http)

    /** Stands in for the run's [RunTiming] when a call comes from outside a run. */
    private val sessionTiming = RunTiming()

    /** The pause an earlier process saved is read once, on the IO dispatcher, before the first request. */
    private val savedPauseLock = Mutex()
    @Volatile private var savedPauseLoaded = false

    internal sealed interface Lookup {
        class Found(val response: ApiEnvelope) : Lookup
        data object NotFound : Lookup
        class Stop(val result: ScrapeResult) : Lookup
    }

    /** What one request gave, read inside its gate slot so its stop is set before the next starts. */
    private class Answer(val outcome: ApiOutcome, val parsed: ApiEnvelope?, val stop: ScrapeResult?)

    /** A request that went out, or one the gate held back. */
    private sealed interface Sent<out T> {
        class Done<T>(val value: T) : Sent<T>
        class Held(val result: ScrapeResult) : Sent<Nothing>
    }

    override suspend fun scrape(request: ScrapeRequest): ScrapeResult = HackParent.take(request, scrapeOnce(request), log) { p, k -> lookup(JEU_RECHERCHE, p, request.fresh, k) }
    private suspend fun scrapeOnce(request: ScrapeRequest): ScrapeResult {
        gate.refusal()?.let { return ScrapeResult.Unavailable(it.reason) }
        loadSavedPause()
        val systemeId = SystemIds.screenScraperId(request.systemId)
        // A Steam game's file holds only its app id: neither hashed nor sent, the game is found by name.
        if (SystemIds.isNameOnly(request.systemId)) return searchByName(request, systemeId)
        // With the library's hints the file is never opened (so the ROM is never read a second time).
        val game = coroutineContext[GameTiming]
        val params = request.hints?.let { hintParams(request.rom, it, systemeId).also { game?.hash(0, "hints") } } ?: run {
            val hashes = try {
                hasher.hash(request.rom)
            } catch (e: IOException) {
                log("could not hash ${request.rom.fileName}: ${e.javaClass.simpleName}")
                return ScrapeResult.Failed(FailureReason.RomUnreadable)
            }
            hashParams(request.rom, hashes, systemeId)
        }
        return when (val byHash = lookup(JEU_INFOS, params, fresh = request.fresh)) {
            is Lookup.Stop -> byHash.result
            is Lookup.Found -> {
                // A serial sent with a crc is no safer: a fan translation's crc is in no database, its game
                // code is another system's, and ScreenScraper answers that system's game.
                val serialOnly = "serialnum" in params
                val jeu = byHash.response.response?.jeu?.takeIf { !it.id.isNullOrBlank() }
                when {
                    jeu == null -> searchByName(request, systemeId)
                    isHashAnswerOk(jeu, systemeId, serialOnly, request.hints?.serialSystemId?.let(SystemIds::screenScraperId)) -> matched(jeu, MatchedBy.Hash, request)
                    else -> {
                        // ScreenScraper matches a serial across systems (a 3DS game for a SNES cartridge).
                        log("hash answer rejected for ${request.rom.fileName}${if (serialOnly) " (serial-only lookup)" else ""}: asked system $systemeId, answered ${jeu.systeme?.id ?: "none"}" +
                            if (isJunk(jeu)) ", junk entry" else "")
                        searchByName(request, systemeId)
                    }
                }
            }
            Lookup.NotFound -> searchByName(request, systemeId)
        }
    }

    /**
     * The name search after a hash miss. ScreenScraper's search chokes on punctuation (a colon gave
     * an empty entry), so a title that finds no accepted game is asked again as [TitleMatch.searchVariants]
     * (at most two more requests); the answers are always judged against the original title. The
     * first answer with an accepted game ends the search; two ids in it are narrowed to the games with a name equal to the title (case and accents only), else NoMatch.
     */
    private suspend fun searchByName(request: ScrapeRequest, systemeId: Int?): ScrapeResult {
        if (systemeId == null || request.title.isBlank()) return ScrapeResult.NoMatch
        val accepted = { game: ApiGame -> isNameMatch(game, request.title, systemeId) }
        val ofSystem = { game: ApiGame -> isGameOfSystem(game, systemeId) }
        for ((index, query) in (listOf(request.title) + TitleMatch.searchVariants(request.title)).withIndex()) {
            val params = mapOf("recherche" to query, "systemeid" to systemeId.toString())
            // A variant's answer keeps every game of the system: the containment rule below needs to
            // know the answer holds one game only.
            when (val byName = lookup(JEU_RECHERCHE, params, fresh = request.fresh, keepGame = if (index == 0) accepted else ofSystem)) {
                is Lookup.Stop -> return byName.result
                Lookup.NotFound -> Unit
                is Lookup.Found -> {
                    val games = byName.response.response?.jeux.orEmpty()
                    // Several different games pass the match ("Limbo", "Limbo (Special Edition)"): keep
                    // those with a name equal to the title (case and accents only), but only when every
                    // loser is an edition of it, not another release ("Prey (2017)", a remake). Still more
                    // than one (a regional twin with the same name) is NoMatch, and the person picks one
                    // (fix-a-match).
                    var candidates = games.filter(accepted)
                    if (candidates.map { it.id.orEmpty().trim() }.distinct().size > 1) {
                        val (exact, losers) = candidates.partition { TitleMatch.exactName(request.title, it.noms.map { n -> n.text }) }
                        if (exact.isNotEmpty() && !losers.any { TitleMatch.looksLikeOtherRelease(it.noms.map { n -> n.text }) }) candidates = exact
                    }
                    if (candidates.isNotEmpty()) {
                        if (index > 0) log("name search found the game with a variant of the title")
                        return if (candidates.map { it.id.orEmpty().trim() }.distinct().size == 1) {
                            matched(candidates.first(), MatchedBy.Name, request)
                        } else {
                            ScrapeResult.NoMatch
                        }
                    }
                    // ScreenScraper may prefix a series ("999 - Nine Hours Nine Persons Nine Doors"):
                    // a variant's one game of the system whose name holds the whole title counts.
                    val ofSystemGames = games.filter(ofSystem)
                    val single = ofSystemGames.firstOrNull()
                    if (index > 0 && single != null && ofSystemGames.map { it.id.orEmpty().trim() }.distinct().size == 1 &&
                        TitleMatch.containedIn(request.title, single.noms.map { it.text })
                    ) {
                        log("name search matched by containment of the title in a variant's only answer")
                        return matched(single, MatchedBy.Name, request)
                    }
                }
            }
        }
        return ScrapeResult.NoMatch
    }

    private fun matched(game: ApiGame, by: MatchedBy, request: ScrapeRequest) =
        GameMapper.matched(game, by, request.regions, request.languages)

    /**
     * One endpoint: the cache first (not when [fresh]: the person asked for this game again),
     * then the network (unless stopped), then the cache again. The answer is trimmed
     * ([CachedAnswer], name results to those [keepGame] accepts) before it is used or stored, so
     * a new answer and its cached copy give the same result. A fresh answer replaces the cached
     * one; a fresh request that fails leaves it as it was.
     */
    private suspend fun lookup(
        endpoint: String,
        params: Map<String, String>,
        fresh: Boolean,
        keepGame: (ApiGame) -> Boolean = { true },
    ): Lookup {
        val key = ResponseCache.keyOf(endpoint, params)
        val game = coroutineContext[GameTiming]
        if (!fresh) {
            val readAt = System.nanoTime()
            val hit = responses.get(key)
            if (hit != null) game?.cacheHit(msSince(readAt))
            when (hit) {
                is ResponseCache.Hit.Found -> parse(endpoint, hit.body)?.let { return Lookup.Found(it) }
                ResponseCache.Hit.NotFound -> return Lookup.NotFound
                null -> Unit
            }
        }
        val answer = when (val asked = ask(endpoint, params)) {
            is Asked.Stop -> return Lookup.Stop(asked.result)
            is Asked.Answered -> asked.answer
        }
        return when (answer.outcome) {
            is ApiOutcome.Ok -> {
                val parsed = answer.parsed ?: return Lookup.Stop(ScrapeResult.Failed(FailureReason.Server(HTTP_OK)))
                val storeAt = System.nanoTime()
                val trimmed = CachedAnswer.trim(parsed, keepGame)
                // No full-text redaction here: it would blank a title word equal to a password.
                // The trimmed answer has no header or ssuser, and its URLs are stripped.
                responses.putFound(key, CachedAnswer.encode(trimmed))
                game?.store(msSince(storeAt))
                Lookup.Found(trimmed)
            }
            else -> Lookup.NotFound.also { responses.putNotFound(key) }
        }
    }

    private sealed interface Asked {
        /** HTTP 200 (parsed, or null when not the expected JSON) or 404. */
        class Answered(val answer: Answer) : Asked
        class Stop(val result: ScrapeResult) : Asked
    }

    /**
     * One request through the gate, never cached: a thread limit retried ([send]), an answer as
     * anonymous with an account set refused, a pause or refusal set inside the slot.
     */
    private suspend fun ask(endpoint: String, params: Map<String, String>): Asked {
        val game = coroutineContext[GameTiming]
        val sent = send({ it.outcome == ApiOutcome.ThreadLimit }) { waited ->
            val outcome = api.get(endpoint, params, waited)
            val parseAt = System.nanoTime()
            val parsed = (outcome as? ApiOutcome.Ok)?.let { parse(endpoint, it.body) }
            game?.parse(msSince(parseAt))
            val stop = when {
                parsed != null && answeredAsAnonymous(parsed) -> {
                    log("$endpoint: answered as anonymous although an account is set: user credentials refused")
                    stopFor(ApiOutcome.Refused(UnavailableReason.BadUserCredentials))
                }
                outcome is ApiOutcome.Ok || outcome == ApiOutcome.NotFound -> null
                else -> stopFor(outcome)
            }
            if (stop == null && parsed != null) readAccount(parsed)
            Answer(outcome, parsed, stop)
        }
        val answer = when (sent) {
            is Sent.Held -> return Asked.Stop(sent.result)
            is Sent.Done -> sent.value
        }
        answer.stop?.let { return Asked.Stop(it) }
        return Asked.Answered(answer)
    }

    override suspend fun search(title: String, systemId: String, regions: List<String>): SearchOutcome =
        searchGames(title, SystemIds.screenScraperId(systemId), regions, keep = { true })

    override suspend fun searchExactTitle(title: String, systemId: String, regions: List<String>): SearchOutcome {
        // Without the system a same-titled game of another system could pass: nothing is asked.
        val systemeId = SystemIds.screenScraperId(systemId) ?: return SearchOutcome.NothingFound
        return searchGames(title, systemeId, regions, keep = { isNameMatch(it, title, systemeId) })
    }

    /**
     * `jeuRecherche.php` for [title] (on [systemeId] when known), never cached; the games [keep] accepts,
     * each id once. When none is accepted the title's [TitleMatch.searchVariants] are asked in turn
     * (each a request), the first with an accepted game ends it.
     */
    private suspend fun searchGames(title: String, systemeId: Int?, regions: List<String>, keep: (ApiGame) -> Boolean): SearchOutcome {
        gate.refusal()?.let { return SearchOutcome.Unavailable(it.reason) }
        val query = title.trim()
        if (query.isEmpty()) return SearchOutcome.NothingFound
        loadSavedPause()
        for (text in listOf(query) + TitleMatch.searchVariants(query)) {
            val params = buildMap {
                put("recherche", text)
                systemeId?.let { put("systemeid", it.toString()) }
            }
            val answer = when (val asked = ask(JEU_RECHERCHE, params)) {
                is Asked.Stop -> return searchStop(asked.result)
                is Asked.Answered -> asked.answer
            }
            if (answer.outcome !is ApiOutcome.Ok) continue
            val parsed = answer.parsed ?: return SearchOutcome.Failed(FailureReason.Server(HTTP_OK))
            // No result comes back as "jeux":[{}]: a game without an id is not a game.
            val games = parsed.response?.jeux.orEmpty().filter { !it.id.isNullOrBlank() }
            val hits = games.filter(keep)
                .distinctBy { it.id.orEmpty().trim() }
                .map { GameMapper.searchHit(it, regions) }
            if (hits.isNotEmpty()) return SearchOutcome.Found(hits, truncated = games.size >= MAX_SEARCH_RESULTS)
        }
        return SearchOutcome.NothingFound
    }

    private fun searchStop(stop: ScrapeResult): SearchOutcome = when (stop) {
        is ScrapeResult.Paused -> SearchOutcome.Paused(stop.resumeAtMillis, stop.reason)
        is ScrapeResult.Unavailable -> SearchOutcome.Unavailable(stop.reason)
        is ScrapeResult.Failed -> SearchOutcome.Failed(stop.reason)
        // A stop is never a game; kept exhaustive.
        is ScrapeResult.Matched, ScrapeResult.NoMatch -> SearchOutcome.Failed(FailureReason.Server(0))
    }

    override suspend fun byGameId(gameId: String, request: ScrapeRequest): ScrapeResult {
        gate.refusal()?.let { return ScrapeResult.Unavailable(it.reason) }
        val id = gameId.trim()
        if (!GAME_ID.matches(id)) {
            log("byGameId: not a ScreenScraper game id (${id.length} characters); nothing asked")
            return ScrapeResult.NoMatch
        }
        loadSavedPause()
        return when (val found = lookup(JEU_INFOS, mapOf("gameid" to id), fresh = request.fresh)) {
            is Lookup.Stop -> found.result
            is Lookup.Found -> found.response.response?.jeu?.takeIf { !it.id.isNullOrBlank() }
                ?.let { matched(it, MatchedBy.PersonChoice, request) }
                ?: ScrapeResult.NoMatch
            Lookup.NotFound -> ScrapeResult.NoMatch
        }
    }

    /**
     * `jeuInfos.php` answers a wrong username or password with HTTP 200 as if no account were
     * given: `ssuser.numid` "0". With an account set, that is a refusal. Only "0" is: a missing or
     * blank `numid` is not, since no real signed-in answer has been seen yet.
     */
    private fun answeredAsAnonymous(parsed: ApiEnvelope): Boolean {
        val user = config.user ?: return false
        if (user.isBlank) return false
        return isAnonymous(parsed.response?.ssuser ?: return false)
    }

    private fun isAnonymous(ssuser: SsUser): Boolean = ssuser.numid == ANONYMOUS_NUMID

    override suspend fun checkAccount(): AccountCheck {
        val user = config.user
        if (user == null || user.isBlank) return AccountCheck.NoAccount
        loadSavedPause()
        val check = gate.runWaited(stopped = ::heldCheck) { waited ->
            // Read inside the slot, so a refusal or pause is set before the next request starts.
            accountCheckFor(api.get(SSUSER_INFOS, emptyMap(), waited))
        }
        // A 429 is not retried here (a person waits on the sign-in screen), but still throttles.
        if (check == AccountCheck.ServerError(HTTP_TOO_MANY_REQUESTS)) gate.onThreadLimit()
        return check
    }

    private suspend fun accountCheckFor(outcome: ApiOutcome): AccountCheck = when (outcome) {
        is ApiOutcome.Ok -> {
            val parsed = parse(SSUSER_INFOS, outcome.body)
            val ssuser = parsed?.response?.ssuser
            when {
                // Not JSON, or JSON without the account: not an answer about the credentials.
                parsed == null || ssuser == null -> AccountCheck.ServerError(HTTP_OK)
                isAnonymous(ssuser) ->
                    if (parsed.response?.serveurs?.closefornomember?.trim() == "1") AccountCheck.ServerClosed
                    else accountCheckOf(stopFor(ApiOutcome.Refused(UnavailableReason.BadUserCredentials)))
                else -> {
                    readAccount(parsed)
                    AccountCheck.Ok(
                        maxThreads = ssuser.maxthreads.number(),
                        requestsToday = ssuser.requeststoday.number(),
                        maxRequestsPerDay = ssuser.maxrequestsperday.number(),
                    )
                }
            }
        }
        ApiOutcome.NotFound -> AccountCheck.ServerError(HTTP_NOT_FOUND)
        else -> accountCheckOf(stopFor(outcome))
    }

    private fun accountCheckOf(stop: ScrapeResult): AccountCheck = when (stop) {
        is ScrapeResult.Unavailable -> AccountCheck.Refused(stop.reason)
        is ScrapeResult.Paused -> when (stop.reason) {
            PauseReason.DailyQuota -> AccountCheck.DailyQuota(stop.resumeAtMillis)
            PauseReason.ServerClosed -> AccountCheck.ServerClosed
        }
        ScrapeResult.Failed(FailureReason.Network) -> AccountCheck.Offline
        is ScrapeResult.Failed -> AccountCheck.ServerError((stop.reason as? FailureReason.Server)?.code ?: 0)
        is ScrapeResult.Matched, ScrapeResult.NoMatch -> AccountCheck.ServerError(0)
    }

    private fun heldCheck(stop: RequestGate.Stop): AccountCheck = accountCheckOf(held(stop))

    private fun String?.number(): Int? = this?.trim()?.toIntOrNull()

    override suspend fun fallbackArt(request: ScrapeRequest, stopped: Set<String>): FallbackArt = fallbacks.lookup(request, stopped)
    override suspend fun download(media: MediaRef, target: File, maxWidth: Int?, maxHeight: Int?): MediaDownload {
        fallbacks.ownerOf(media.url)?.let { return it.download(media, target) } // never ScreenScraper's gate or credentials
        loadSavedPause()
        val sent = send({ it.first == MediaDownloader.Fetch.Stopped(ApiOutcome.ThreadLimit) }) { waited ->
            val fetched = downloader.fetch(media, target, maxWidth, maxHeight, waited)
            fetched to (fetched as? MediaDownloader.Fetch.Stopped)?.let { stopFor(it.outcome) }
        }
        val (fetched, stop) = when (sent) {
            is Sent.Held -> return mediaStop(sent.result)
            is Sent.Done -> sent.value
        }
        return when (fetched) {
            MediaDownloader.Fetch.Done -> MediaDownload.Done(target)
            MediaDownloader.Fetch.Broken -> MediaDownload.Broken
            is MediaDownloader.Fetch.Stopped -> mediaStop(stop ?: ScrapeResult.Failed(FailureReason.Network))
        }
    }

    private fun mediaStop(stop: ScrapeResult): MediaDownload = when (stop) {
        is ScrapeResult.Paused -> MediaDownload.Paused(stop.resumeAtMillis, stop.reason)
        is ScrapeResult.Unavailable -> MediaDownload.Unavailable(stop.reason)
        ScrapeResult.Failed(FailureReason.Network) -> MediaDownload.NetworkDown
        // Only a server fault (5xx) or a 429 that outlasted its retries may pass; any other 4xx for
        // a picture (404, which is also what stopFor gives a picture that is not there, 410, 451...) will not, and must not keep its game undelivered forever.
        is ScrapeResult.Failed -> {
            val code = (stop.reason as? FailureReason.Server)?.code ?: 0
            if (code in HTTP_CLIENT_ERRORS && code != HTTP_TOO_MANY_REQUESTS) MediaDownload.Broken
            else MediaDownload.ServerError(code)
        }
        else -> MediaDownload.Broken
    }

    /**
     * Turns a non-OK outcome into a result and, for a refusal or a pause, sets it in the gate.
     * Called inside the gate slot of the request that got it.
     */
    private suspend fun stopFor(outcome: ApiOutcome): ScrapeResult = when (outcome) {
        is ApiOutcome.Refused -> {
            gate.refuse(outcome.reason)
            ScrapeResult.Unavailable(outcome.reason)
        }
        ApiOutcome.DailyQuota -> paused(ParisTime.nextMidnight(config.clock()), PauseReason.DailyQuota)
        ApiOutcome.ServerClosed -> paused(config.clock() + SERVER_CLOSED_PAUSE_MILLIS, PauseReason.ServerClosed)
        ApiOutcome.ThreadLimit -> ScrapeResult.Failed(FailureReason.Server(HTTP_TOO_MANY_REQUESTS))
        is ApiOutcome.ServerError -> ScrapeResult.Failed(FailureReason.Server(outcome.code))
        ApiOutcome.NetworkError -> ScrapeResult.Failed(FailureReason.Network)
        // Only a media download gets here with these: a picture that is not there.
        is ApiOutcome.Ok, ApiOutcome.NotFound -> ScrapeResult.Failed(FailureReason.Server(HTTP_NOT_FOUND))
    }

    private suspend fun paused(untilMillis: Long, reason: PauseReason): ScrapeResult {
        pauseUntil(untilMillis, reason)
        val pause = gate.pause() ?: RequestGate.Pause(untilMillis, reason)
        return ScrapeResult.Paused(pause.untilMillis, pause.reason)
    }

    /** Sets the gate's pause and saves it (on the IO dispatcher), so a new process keeps to it too. */
    private suspend fun pauseUntil(untilMillis: Long, reason: PauseReason) {
        gate.pauseUntil(untilMillis, reason)
        val store = config.pauseStore ?: return
        val pause = gate.pause() ?: return
        withContext(config.ioDispatcher) { store.save(ScrapeResult.Paused(pause.untilMillis, pause.reason)) }
    }

    private suspend fun loadSavedPause() {
        val store = config.pauseStore ?: return
        if (savedPauseLoaded) return
        // Parallel callers wait here (suspended, no thread blocked) until the first has set the
        // pause; a load that fails or is cancelled leaves the flag unset, so the next call retries.
        savedPauseLock.withLock {
            if (savedPauseLoaded) return
            readSavedPause(store)
            savedPauseLoaded = true
        }
    }

    private suspend fun readSavedPause(store: PauseStore) {
        // A pause saved by an earlier process still holds: ask nothing before its resume time.
        // No pause lasts past the next Paris midnight (at most a day away), so one further out
        // was saved under a wrong clock: dropped, or it would block scraping for that long.
        val saved = withContext(config.ioDispatcher) { store.load() } ?: return
        if (saved.resumeAtMillis > config.clock() + MAX_SAVED_PAUSE_MILLIS) {
            log("saved pause is ${saved.resumeAtMillis - config.clock()} ms away, more than a day: dropped")
            withContext(config.ioDispatcher) { store.clear() }
            return
        }
        gate.pauseUntil(saved.resumeAtMillis, saved.reason)
    }

    /**
     * One request through the gate; a thread-limit answer is retried after 2, 4, 8 and 16 s, then
     * given up. Each try goes through the gate again, so a stop set meanwhile holds it back.
     */
    private suspend fun <T> send(isThreadLimit: (T) -> Boolean, call: suspend (waitedMillis: Long) -> T): Sent<T> {
        var attempt = 0
        while (true) {
            val sent: Sent<T> = gate.runWaited(stopped = { Sent.Held(held(it)) }) { waited -> Sent.Done(call(waited)) }
            if (sent !is Sent.Done || !isThreadLimit(sent.value)) return sent
            gate.onThreadLimit()
            if (attempt == THREAD_LIMIT_BACKOFF_MILLIS.size) return sent
            val backoff = THREAD_LIMIT_BACKOFF_MILLIS[attempt++]
            timing.line("event=thread-limit attempt=$attempt backoff=${backoff}ms slots=${gate.threadLimit}")
            coroutineContext[GameTiming]?.backoff(backoff)
            delay(backoff)
        }
    }

    private fun held(stop: RequestGate.Stop): ScrapeResult = when (stop) {
        is RequestGate.Pause -> ScrapeResult.Paused(stop.untilMillis, stop.reason)
        is RequestGate.Refusal -> ScrapeResult.Unavailable(stop.reason)
    }

    private suspend fun readAccount(parsed: ApiEnvelope) {
        val user = parsed.response?.ssuser ?: return
        gate.updateFromAccount(user.maxthreads?.trim()?.toIntOrNull(), user.maxrequestspermin?.trim()?.toIntOrNull())
        val seen = AccountLimits(
            maxThreads = user.maxthreads.number(),
            maxRequestsPerMinute = user.maxrequestspermin.number(),
            requestsToday = user.requeststoday.number(),
            maxRequestsPerDay = user.maxrequestsperday.number(),
            maxDownloadKilobytesPerSecond = user.maxdownloadspeed.number(),
        )
        if (!seen.isEmpty) limits.update { seen.over(it) }
        logLimits(user)
        val today = user.requeststoday?.trim()?.toIntOrNull()
        val perDay = user.maxrequestsperday?.trim()?.toIntOrNull()
        if (today != null && perDay != null && perDay > 0 && today >= perDay) {
            pauseUntil(ParisTime.nextMidnight(config.clock()), PauseReason.DailyQuota)
        }
    }

    /**
     * The account's limits and the gate's effective slots and spacing, once per run and again
     * when one of them changes (`requeststoday` grows with every request, so it is shown but
     * does not count as a change).
     */
    private suspend fun logLimits(user: SsUser) {
        fun text(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() } ?: "?"
        val limits = "maxthreads=${text(user.maxthreads)} maxrequestspermin=${text(user.maxrequestspermin)} " +
            "maxrequestsperday=${text(user.maxrequestsperday)} maxdownloadspeed=${text(user.maxdownloadspeed)}KBps " +
            "slots=${gate.threadLimit} spacing=${gate.spacingMillis}ms"
        val run = coroutineContext[RunTiming] ?: sessionTiming
        val line = "limits $limits requeststoday=${text(user.requeststoday)}"
        run.remember(line)
        if (!run.changed(limits)) return
        timing.line(line)
    }

    private fun parse(endpoint: String, body: String): ApiEnvelope? = try {
        ScreenScraperJson.decodeFromString(ApiEnvelope.serializer(), body)
    } catch (e: SerializationException) {
        log("$endpoint: answer is not the expected JSON (${e.javaClass.simpleName})")
        null
    } catch (e: IllegalArgumentException) {
        log("$endpoint: answer is not the expected JSON (${e.javaClass.simpleName})")
        null
    }

    private fun hashParams(rom: RomSource, hashes: RomHashes, systemeId: Int?): Map<String, String> = buildMap {
        hashes.crc32?.let { put("crc", it) }
        hashes.md5?.let { put("md5", it) }
        hashes.sha1?.let { put("sha1", it) }
        systemeId?.let { put("systemeid", it.toString()) }
        put("romtype", "rom")
        put("romnom", rom.fileName)
        put("romtaille", rom.sizeBytes.toString())
    }

    private fun hintParams(rom: RomSource, hints: RomHints, systemeId: Int?): Map<String, String> = buildMap {
        hints.crc32?.trim()?.takeIf { it.isNotEmpty() }?.let { put("crc", it.uppercase(java.util.Locale.ROOT)) }
        hints.serial?.trim()?.takeIf { it.isNotEmpty() }?.let { put("serialnum", it) }
        systemeId?.let { put("systemeid", it.toString()) }
        put("romtype", "rom")
        put("romnom", rom.fileName)
        put("romtaille", (hints.sizeBytes ?: rom.sizeBytes).toString())
    }

    companion object {
        const val RESPONSES_DIR = "screenscraper-responses"
        const val HASHES_DIR = "screenscraper-hashes"
        private const val HTTP_OK = 200

        /** `jeuRecherche.php` answers at most this many games (seen live): a full answer may be cut. */
        private const val MAX_SEARCH_RESULTS = 30
        private const val ANONYMOUS_NUMID = "0"

        /** ScreenScraper game ids are numbers; anything else is not sent (and never becomes a folder). */
        private val GAME_ID = Regex("[0-9]{1,12}")
        private const val HTTP_NOT_FOUND = 404
        private val HTTP_CLIENT_ERRORS = 400..499
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val SERVER_CLOSED_PAUSE_MILLIS: Long = 60L * 60 * 1000
        private const val MAX_SAVED_PAUSE_MILLIS: Long = 25L * 60 * 60 * 1000
        private val THREAD_LIMIT_BACKOFF_MILLIS = longArrayOf(2_000, 4_000, 8_000, 16_000)
    }
}
