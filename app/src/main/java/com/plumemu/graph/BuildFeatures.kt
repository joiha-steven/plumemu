package com.plumemu.graph

import com.plumemu.BuildConfig

/**
 * What this build can reach. A build made from source names no statistics server and no update
 * manifest (app/build.gradle.kts: both come from Gradle properties only the release machine sets),
 * so those features are absent: no row for them, no question in setup, nothing sent, no update check.
 */
internal object BuildFeatures {
    /** Usage statistics, crash reports and problem reports all go to the one statistics server. */
    val hasStatistics: Boolean = BuildConfig.STATS_ENDPOINT.isNotBlank()

    /** Update checks go to the manifest; without its address there is no check. */
    val hasUpdates: Boolean = BuildConfig.UPDATE_MANIFEST_URL.isNotBlank()
}
