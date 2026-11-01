package com.plumemu.achievements.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Each system's game list saved on disk, one file per console in [dir]. The file holds only the
 * fields Plumemu uses, written by Plumemu (never RetroAchievements' answer as it came), and no key
 * or user. Written to a temporary file and renamed, so a crash leaves the old file or the new one,
 * never half of one. Nothing touches the disk before the first [read] or [write]; [dir] is made then.
 */
internal class GameListCache(
    private val dir: File,
    private val log: (String) -> Unit,
) {
    class Saved(val list: RaGameList, val savedAtMillis: Long)

    /** Runs [block] with [consoleId]'s lock: two callers never fetch the same system at once. */
    suspend fun <T> withConsoleLock(consoleId: Int, block: suspend () -> T): T =
        LOCKS.getOrPut(fileOf(consoleId).path) { Mutex() }.withLock { block() }

    /** The saved list, or null when there is none or it cannot be read (logged; it is fetched again). */
    suspend fun read(consoleId: Int): Saved? = withContext(Dispatchers.IO) {
        val file = fileOf(consoleId)
        if (!file.exists()) return@withContext null
        try {
            val stored = lenientJson.decodeFromString(StoredList.serializer(), file.readText())
            if (stored.version != FORMAT_VERSION || stored.consoleId != consoleId) {
                log("game list cache: console $consoleId saved in another format; fetching again")
                return@withContext null
            }
            Saved(RaGameList(consoleId, stored.games.map { it.toGame() }), stored.savedAtMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A damaged file (IOException, SerializationException) is fetched again, never a crash.
            log("game list cache: console $consoleId unreadable (${e.javaClass.simpleName}); fetching again")
            null
        }
    }

    /** Saves [list]; a failure is logged and the caller keeps the list it has. */
    suspend fun write(list: RaGameList, savedAtMillis: Long): Unit = withContext(Dispatchers.IO) {
        val file = fileOf(list.consoleId)
        val temp = File(dir, "${file.name}.tmp")
        try {
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("cannot make the cache folder")
            val stored = StoredList(FORMAT_VERSION, list.consoleId, savedAtMillis, list.games.map(StoredGame::of))
            temp.writeText(lenientJson.encodeToString(StoredList.serializer(), stored))
            if (!temp.renameTo(file)) {
                // Some file systems refuse to rename over an existing file.
                file.delete()
                if (!temp.renameTo(file)) throw IOException("rename failed")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Any failure (I/O, a refused folder, a security exception): the list is kept in memory.
            log("game list cache: console ${list.consoleId} not saved (${e.javaClass.simpleName})")
            temp.delete()
        }
    }

    fun fileOf(consoleId: Int): File = File(dir, "ra-games-$consoleId.json")

    @Serializable
    private class StoredList(
        val version: Int,
        val consoleId: Int,
        val savedAtMillis: Long,
        val games: List<StoredGame>,
    )

    @Serializable
    private class StoredGame(
        val id: Int,
        val title: String,
        val consoleId: Int,
        val achievementCount: Int,
        val points: Int,
        val iconPath: String? = null,
        val hashes: List<String>,
    ) {
        fun toGame() = RaGame(id, title, consoleId, achievementCount, points, iconPath, hashes)

        companion object {
            fun of(game: RaGame) =
                StoredGame(game.id, game.title, game.consoleId, game.achievementCount, game.points, game.iconPath, game.hashes)
        }
    }

    private companion object {
        const val FORMAT_VERSION = 1

        /** Per file, process-wide: two clients on the same folder share the lock. */
        val LOCKS = ConcurrentHashMap<String, Mutex>()
    }
}
