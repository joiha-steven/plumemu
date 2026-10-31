package com.plumemu.graph

import android.util.Log
import com.plumemu.BuildConfig
import com.plumemu.telemetry.SwitchPlace
import com.plumemu.telemetry.TelemetryConfig
import com.plumemu.telemetry.TelemetrySink
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the telemetry module's switch equal to the stored one (`advanced.usageStatistics`) for the life of the process. Settings › Advanced › Send usage statistics writes the store
 * only, so its moves arrive here with the place `settings`; so do a reset or a restored backup,
 * which also happen in Settings. Setup's Ready step tells the module itself first
 * (GraphSetupSource.setUsageStatistics), so its place is `setup` and this finds nothing to do.
 *
 * Reads the store itself, not [AppGraph.settings]: that one falls back to the defaults (off) after
 * an unexpected failure, which must never change what the person chose.
 */
internal fun AppGraph.followUsageStatistics(
    telemetry: TelemetrySink,
    stored: Flow<Boolean> = settingsStore.settings.map { it.advanced.usageStatistics },
): Job =
    appScope.launch {
        stored
            .distinctUntilChanged()
            .catch { e -> Log.w(TELEMETRY_TAG, "Usage statistics switch not followed (${e.javaClass.simpleName})") }
            .collect { isOn -> telemetry.setEnabled(isOn, SwitchPlace.Settings) }
    }

/** The stored switch once (null: nothing chosen yet), for the telemetry module's start (it decides before sending anything). Throws what the store throws. */
internal suspend fun AppGraph.storedUsageStatistics(): Boolean? =
    settingsStore.settings.first().let { s ->
        // Off with setup unfinished is nothing chosen yet (the default, or a key never written): the module sends no decline for it.
        if (!s.advanced.usageStatistics && !s.flags.setupCompleted) null else s.advanced.usageStatistics
    }

/** What this build tells the telemetry module about itself (app/build.gradle.kts). */
internal fun telemetryConfig(): TelemetryConfig =
    TelemetryConfig(
        endpoint = BuildConfig.STATS_ENDPOINT,
        appVersion = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        // The benchmark builds come from release but are tests: counted as debug, hidden on the dashboard.
        buildType = if (BuildConfig.BUILD_TYPE == "release") TelemetryConfig.BuildType.Release else TelemetryConfig.BuildType.Debug,
        allowLocalCleartext = BuildConfig.STATS_LOCAL_CLEARTEXT,
    )

private const val TELEMETRY_TAG = "Telemetry"
