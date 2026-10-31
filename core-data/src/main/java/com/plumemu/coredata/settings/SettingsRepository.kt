package com.plumemu.coredata.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/** Where settings problems are written; key names and exception classes only. */
internal fun interface SettingsLog {
    fun warn(message: String)
}

internal object AndroidSettingsLog : SettingsLog {
    override fun warn(message: String) {
        Log.w("PlumSettings", message)
    }
}

/**
 * The person's settings, stored with Preferences DataStore in the app's own folder. One instance
 * per process (DataStore refuses two on the same file): the app keeps it in its graph.
 *
 * [settings] reads the file off the main thread and emits the whole model whenever any value
 * changes. A file that cannot be parsed is replaced by defaults (one log line). A read error
 * gives defaults without touching the file and the read is tried again, later and later
 * (1 s doubling up to about 4 min), so a change made once the storage answers again still applies.
 */
class SettingsRepository internal constructor(
    private val store: DataStore<Preferences>,
    private val log: SettingsLog,
    private val retryDelayMs: (attempt: Long) -> Long = ::readRetryDelayMs,
) {
    private val codec = SettingsCodec(log)

    val settings: Flow<PlumSettings> =
        store.data
            .retryWhen { e, attempt ->
                if (e !is IOException) return@retryWhen false
                log.warn("Settings could not be read (${e.javaClass.simpleName}, attempt ${attempt + 1}); using defaults until a read works")
                emit(emptyPreferences())
                delay(retryDelayMs(attempt))
                true
            }.map(codec::decode)
            .distinctUntilChanged()

    /**
     * One read of the file as it is now, for a backup: unlike [settings], a
     * read error is thrown, not turned into defaults, so a backup never carries defaults unseen.
     */
    suspend fun readOnce(): PlumSettings = codec.decode(store.data.first())

    /** Reads, changes and writes in one step, so two changes made at once never lose each other. */
    suspend fun update(transform: (PlumSettings) -> PlumSettings) {
        store.edit { prefs -> codec.encode(transform(codec.decode(prefs)), prefs) }
    }

    suspend fun setAppearance(appearance: AppearanceSettings) = update { it.copy(appearance = appearance) }

    suspend fun setControls(controls: ControlsSettings) = update { it.copy(controls = controls) }

    suspend fun setLibrary(library: LibrarySettings) = update { it.copy(library = library) }

    suspend fun setHomeScreen(homeScreen: HomeScreenSettings) = update { it.copy(homeScreen = homeScreen) }

    suspend fun setSecondScreen(secondScreen: SecondScreenSettings) = update { it.copy(secondScreen = secondScreen) }

    suspend fun setAdvanced(advanced: AdvancedSettings) = update { it.copy(advanced = advanced) }

    suspend fun markSetupCompleted() = update { it.copy(flags = it.flags.copy(setupCompleted = true)) }

    suspend fun markHomeRoleAsked() = update { it.copy(flags = it.flags.copy(homeRoleAsked = true)) }

    companion object {
        const val FILE_NAME = "settings"

        /** The app's settings file. Call once per process. */
        fun create(
            context: Context,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ): SettingsRepository {
            val app = context.applicationContext
            return create({ app.preferencesDataStoreFile(FILE_NAME) }, scope, AndroidSettingsLog)
        }

        internal fun create(
            file: () -> File,
            scope: CoroutineScope,
            log: SettingsLog,
        ): SettingsRepository {
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { e ->
                    log.warn("Settings file was damaged (${e.javaClass.simpleName}); replaced with defaults")
                    emptyPreferences()
                },
                scope = scope,
                produceFile = file,
            )
            return SettingsRepository(store, log)
        }
    }
}

private const val FIRST_RETRY_MS = 1_000L
private const val MAX_RETRY_DOUBLINGS = 8

/** 1 s, 2 s, 4 s … then about 4 min between tries: no busy loop while storage is broken. */
internal fun readRetryDelayMs(attempt: Long): Long = FIRST_RETRY_MS shl attempt.coerceAtMost(MAX_RETRY_DOUBLINGS.toLong()).toInt()
