package com.plumemu.telemetry

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * What the module remembers between launches, in its own DataStore. The file lives in the app's
 * no-backup folder: kept across updates, never in a backup (so a restored phone is a new install),
 * gone when the app's data is cleared (a new install id then).
 */
internal data class TelemetryState(
    /** Null only before the first launch has made one. */
    val installId: String?,
    /** Statistics on; on from the first launch. */
    val enabled: Boolean,
    /** `stats_declined` was decided for this install: never a second one. */
    val declineDecided: Boolean,
    /** Where it was switched off, while that one decline is still to be delivered. */
    val declinePending: SwitchPlace?,
    /** `first_open` was queued for this install (the first launch with statistics on, or the first switch-on): never a second one. */
    val firstOpenSent: Boolean = true,
)

internal class StateStore(
    file: File,
    scope: CoroutineScope,
) {
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    suspend fun read(): TelemetryState = decode(store.data.first())

    suspend fun write(state: TelemetryState) {
        store.edit { p ->
            val id = state.installId
            if (id == null) p.remove(Keys.installId) else p[Keys.installId] = id
            p[Keys.enabled] = state.enabled
            p[Keys.declineDecided] = state.declineDecided
            p[Keys.firstOpenSent] = state.firstOpenSent
            val pending = state.declinePending
            if (pending == null) p.remove(Keys.declinePending) else p[Keys.declinePending] = pending.wire
        }
    }

    private fun decode(p: Preferences) =
        TelemetryState(
            installId = p[Keys.installId],
            // A file from before this key: an install with an id had its first_open when statistics were on from the first launch.
            firstOpenSent = p[Keys.firstOpenSent] ?: (p[Keys.installId] != null),
            enabled = p[Keys.enabled] ?: true,
            declineDecided = p[Keys.declineDecided] ?: false,
            declinePending = p[Keys.declinePending]?.let { wire -> SwitchPlace.entries.firstOrNull { it.wire == wire } },
        )

    private object Keys {
        val installId = stringPreferencesKey("install_id")
        val enabled = booleanPreferencesKey("enabled")
        val declineDecided = booleanPreferencesKey("decline_decided")
        val firstOpenSent = booleanPreferencesKey("first_open_sent")
        val declinePending = stringPreferencesKey("decline_pending")
    }

    companion object {
        /** DataStore wants this extension. */
        const val FILE_NAME = "telemetry.preferences_pb"
    }
}
