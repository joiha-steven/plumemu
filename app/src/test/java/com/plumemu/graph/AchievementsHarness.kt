package com.plumemu.graph

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.achievements.account.ApiKeyCipher
import com.plumemu.achievements.api.RetroAchievementsClient
import com.plumemu.achievements.hash.RaHashResult
import com.plumemu.coredata.account.RetroAchievementsAccountStore
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RaDao
import com.plumemu.coredata.db.RaUnmatchedGame
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

/**
 * [GraphAchievements] with the real account file, an in-memory library database, a fake
 * RetroAchievements server ([server], answers set per endpoint in [answers]), a fake cipher and a
 * fake hasher. Counters say what was built or touched, so a test can prove a signed-out app
 * builds and asks nothing.
 */
internal class AchievementsHarness(private val folder: File) : AutoCloseable {
    val context: Context = ApplicationProvider.getApplicationContext()
    private val storeJob = Job()
    private val scopeJob = SupervisorJob()
    val store: RetroAchievementsAccountStore by lazy { RetroAchievementsAccountStore.create(context, CoroutineScope(Dispatchers.IO + storeJob)) }
    val db: PlumDatabase = Room.inMemoryDatabaseBuilder(context, PlumDatabase::class.java).build()
    val logLines = CopyOnWriteArrayList<String>()

    /** An endpoint held here answers only once its deferred completes ([hold], [release]); [arrived] says the request came. */
    private val holds = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val arrived = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    fun hold(endpoint: String) {
        holds[endpoint] = CompletableDeferred()
        arrived[endpoint] = CompletableDeferred()
    }

    suspend fun awaitArrived(endpoint: String) = withTimeout(WAIT_MS) { arrived.getValue(endpoint).await() }

    fun release(endpoint: String) {
        holds.remove(endpoint)?.complete(Unit)
    }

    /** Called with each file name the fake hasher reads. */
    @Volatile var onHash: (String) -> Unit = {}

    /** Answers by endpoint file name (`API_GetGameList.php`...); an endpoint without one answers 404. */
    val answers = ConcurrentHashMap<String, () -> MockResponse>()
    val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val endpoint = request.url.pathSegments.last()
                holds[endpoint]?.let { hold ->
                    arrived.getOrPut(endpoint) { CompletableDeferred() }.complete(Unit)
                    runBlocking { withTimeout(WAIT_MS) { hold.await() } }
                }
                return answers[endpoint]?.invoke() ?: MockResponse.Builder().code(404).build()
            }
        }
        start()
    }

    val clientsBuilt = AtomicInteger()
    val daoReads = AtomicInteger()
    val libraryReads = AtomicInteger()
    val decryptions = AtomicInteger()
    val hashed = CopyOnWriteArrayList<String>()

    /** What the fake hasher gives a file, by file name; others hash to [MD5]. */
    val hashes = ConcurrentHashMap<String, RaHashResult>()
    var playing = false
    var unmetered = true
    val now = AtomicLong(START)
    val marks = FakeMarks()

    private val files = ConcurrentHashMap<Long, File>()
    private val folderId: Long = runBlocking { db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 0)) }

    val achievements = GraphAchievements(
        store = lazy { store },
        cipher = lazy { FakeCipher(decryptions) },
        clientFor = { key ->
            clientsBuilt.incrementAndGet()
            RetroAchievementsClient(key, File(folder, "lists"), log = { logLines += it }, baseUrl = server.url("/API/").toString())
        },
        raDao = {
            daoReads.incrementAndGet()
            db.raDao()
        },
        library = {
            libraryReads.incrementAndGet()
            FakeLibrary(db.raDao())
        },
        refreshMarks = marks,
        gates = object : RefreshGates {
            override suspend fun isPlaying() = playing

            override fun isUnmetered() = unmetered
        },
        scope = CoroutineScope(Dispatchers.Default + scopeJob),
        io = Dispatchers.IO,
        clock = { now.get() },
        log = { logLines += it },
        hash = { _, rom ->
            hashed += rom.fileName
            onHash(rom.fileName)
            hashes[rom.fileName] ?: RaHashResult.Hashed(MD5)
        },
    )

    /** A library game of [systemId] whose file is a small real file named [fileName]. */
    fun addGame(fileName: String, title: String, systemId: String = "snes"): Long = runBlocking {
        val row = GameEntity(
            folderId = folderId, documentId = fileName, parentDocumentId = "root", relativePath = fileName, fileName = fileName,
            sizeBytes = 4, modifiedAt = 1, title = title, sortName = title.lowercase(), regions = "", discGroup = null, discCount = 1,
            systemId = systemId, isPrimaryCopy = true,
        )
        db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity(systemId, systemId)), listOf(row)))
        val id = db.scanDao().gamesInFolder(folderId).single { it.documentId == fileName }.id
        files[id] = File(folder, fileName).apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        id
    }

    suspend fun signIn() = store.signIn(USER, ULID, CIPHERTEXT, 120)

    override fun close() = runBlocking {
        holds.values.forEach { it.complete(Unit) }
        server.close()
        db.close()
        scopeJob.cancelAndJoin()
        storeJob.cancelAndJoin()
    }

    private inner class FakeLibrary(private val dao: RaDao) : RaLibrary {
        override suspend fun game(gameId: Long): RaLibraryGame? {
            val row = db.gameDao().game(gameId) ?: return null
            return RaLibraryGame(row.id, row.systemId, row.title, row.fileTitle) {
                val file = files.getValue(gameId)
                if (!file.exists()) throw java.io.IOException("gone")
                FileRaRomSource(file).let { RaLibraryFile(it, it.lastModifiedMillis) }
            }
        }

        override suspend fun unmatched(systemIds: List<String>): List<RaUnmatchedGame> = dao.unmatchedGames(systemIds)
    }

    private class FakeCipher(private val decryptions: AtomicInteger) : ApiKeyCipher {
        override fun encrypt(plain: String): String = CIPHERTEXT

        override fun decrypt(stored: String): String? {
            decryptions.incrementAndGet()
            return if (stored == CIPHERTEXT) KEY else null
        }
    }

    class FakeMarks : RefreshMarks {
        @Volatile var success = 0L

        @Volatile var failure = 0L

        override fun lastSuccess() = success

        override fun lastFailure() = failure

        override fun markSuccess(at: Long) {
            success = at
            failure = 0
        }

        override fun markFailure(at: Long) {
            failure = at
        }

        override fun clear() {
            success = 0
            failure = 0
        }

        /** Starts at the current version (no one-time re-check) unless a test sets it lower. */
        @Volatile var version = GraphAchievements.MATCHER_VERSION

        override fun matcherVersion() = version

        override fun markMatcherVersion(version: Int) {
            this.version = version
        }
    }

    companion object {
        const val KEY = "testKeyAbcdef0123456789Zz"
        const val USER = "plumtesterRA"
        const val ULID = "00003EMFWR7XB8SDPEHB3K56ZQ"
        const val CIPHERTEXT = "Y2lwaGVydGV4dA"
        const val MD5 = "0123456789abcdef0123456789abcdef"
        const val START = 1_700_000_000_000L
        const val DAY = GraphAchievements.DAY_MILLIS

        const val GAME_LIST = "API_GetGameList.php"
        const val GAME_PROGRESS = "API_GetGameInfoAndUserProgress.php"
        const val COMPLETION = "API_GetUserCompletionProgress.php"

        fun ok(body: String): MockResponse = MockResponse.Builder().code(200).body(body).build()

        fun status(code: Int): MockResponse = MockResponse.Builder().code(code).body("{}").build()

        /** The SNES list (console 3) with game [raId] under [md5]. */
        fun gameList(raId: Int = 11, md5: String = MD5): String =
            """[{"ID":$raId,"Title":"Zork","ConsoleID":3,"NumAchievements":3,"Points":30,"Hashes":["$md5"]}]"""

        fun progress(raId: Int = 11, awarded: Int = 1, hardcore: Int = 0, award: String? = null): String {
            val earned = (1..3).joinToString(",") { n ->
                val date = if (n <= awarded) ""","DateEarned":"2026-01-0$n 10:00:00"""" else ""
                """"$n":{"ID":$n,"Title":"A$n","Description":"Do $n","Points":${n * 5},"BadgeName":"10$n","DisplayOrder":$n$date}"""
            }
            val kind = award?.let { "\"$it\"" } ?: "null"
            return """{"ID":$raId,"Title":"Zork RA","ConsoleID":3,"NumAchievements":3,"NumAwardedToUser":$awarded,""" +
                """"NumAwardedToUserHardcore":$hardcore,"HighestAwardKind":$kind,"Achievements":{$earned}}"""
        }

        fun completion(vararg rows: String): String = """{"Count":${rows.size},"Total":${rows.size},"Results":[${rows.joinToString(",")}]}"""

        fun completionRow(raId: Int, title: String, award: String?, awarded: Int = 3, consoleId: Int = 3): String {
            val kind = award?.let { "\"$it\"" } ?: "null"
            return """{"GameID":$raId,"Title":"$title","ConsoleID":$consoleId,"MaxPossible":3,"NumAwarded":$awarded,"NumAwardedHardcore":$awarded,"HighestAwardKind":$kind}"""
        }
    }
}
