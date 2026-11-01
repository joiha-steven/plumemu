package com.plumemu.graph

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.plumemu.achievements.account.KeystoreApiKeyCipher
import com.plumemu.achievements.api.RetroAchievementsClient
import com.plumemu.content.CoilBadgeImages
import com.plumemu.coredata.account.RetroAchievementsAccountStore
import com.plumemu.game.BadgeImages
import com.plumemu.library.scan.ContentResolverDocumentTree
import com.plumemu.library.storage.AndroidStorageRoots
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The graph's [GraphAchievements]: every part is a lambda or a [Lazy], so making it builds and
 * reads nothing. Game lists are saved under the cache folder ([RA_CACHE_FOLDER]).
 */
internal fun graphAchievementsFor(
    context: Context,
    graph: AppGraph,
    io: CoroutineDispatcher,
    log: (String) -> Unit,
): GraphAchievements {
    val app = context.applicationContext
    return GraphAchievements(
        store = lazy { RetroAchievementsAccountStore.create(app) },
        cipher = lazy { KeystoreApiKeyCipher() },
        clientFor = { key -> RetroAchievementsClient(key, File(app.cacheDir, RA_CACHE_FOLDER)) },
        raDao = { graph.database.raDao() },
        library = {
            DatabaseRaLibrary(
                database = { graph.database },
                openTree = { ContentResolverDocumentTree(app.contentResolver, Uri.parse(it)) },
                roots = AndroidStorageRoots(app),
            )
        },
        refreshMarks = SharedPreferencesRefreshMarks(lazy { app.getSharedPreferences(SharedPreferencesRefreshMarks.FILE_NAME, Context.MODE_PRIVATE) }),
        gates = object : RefreshGates {
            private val playing = playingAway({ graph.startup.isInFront }) { graph.launching()?.playTime?.isPending() == true }

            override suspend fun isPlaying(): Boolean = playing()

            override fun isUnmetered(): Boolean = deviceConditionsOf(app).unmeteredNetwork
        },
        scope = graph.appScope,
        io = io,
        log = log,
    )
}

/** Under `cacheDir`: RetroAchievements' game lists, one file per console. */
internal const val RA_CACHE_FOLDER = "retroachievements"

/** [RefreshMarks] in a small SharedPreferences file, opened on first use. */
internal class SharedPreferencesRefreshMarks(private val prefs: Lazy<SharedPreferences>) : RefreshMarks {
    override fun lastSuccess(): Long = prefs.value.getLong(LAST_SUCCESS, 0L)

    override fun lastFailure(): Long = prefs.value.getLong(LAST_FAILURE, 0L)

    override fun markSuccess(at: Long) {
        prefs.value.edit().putLong(LAST_SUCCESS, at).remove(LAST_FAILURE).apply()
    }

    override fun markFailure(at: Long) {
        prefs.value.edit().putLong(LAST_FAILURE, at).apply()
    }

    override fun clear() {
        prefs.value.edit().remove(LAST_SUCCESS).remove(LAST_FAILURE).apply()
    }

    override fun matcherVersion(): Int = prefs.value.getInt(MATCHER_VERSION, 0)

    override fun markMatcherVersion(version: Int) {
        prefs.value.edit().putInt(MATCHER_VERSION, version).apply()
    }

    companion object {
        const val FILE_NAME = "retroachievements_refresh"
        private const val LAST_SUCCESS = "last_success_ms"
        private const val LAST_FAILURE = "last_failure_ms"
        private const val MATCHER_VERSION = "matcher_version"
    }
}

/** Under `cacheDir`: RetroAchievements' badge pictures (Coil's disk cache). */
internal const val RA_BADGE_FOLDER = "retroachievements-badges"

/** Info's badge pictures, cached on disk; Coil's loader is made at the first badge drawn. */
internal fun achievementBadgesFor(app: Context): BadgeImages = CoilBadgeImages(app, File(app.cacheDir, RA_BADGE_FOLDER))
