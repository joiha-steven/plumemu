package com.plumemu.telemetry

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * What the app tells the module about itself. The app reads it from its BuildConfig
 * (`STATS_ENDPOINT`, version name and code, build type).
 */
public class TelemetryConfig(
    /** The server's base, `https://plumemu.stevensdesk.com`; the API is under `/api/v1/`. */
    public val endpoint: String,
    public val appVersion: String,
    public val versionCode: Long,
    /** `release` or `debug`; every build that is not the release build counts as debug. */
    public val buildType: BuildType,
    /**
     * Only a debug build pointed at a local `wrangler dev` sets this: plain `http://` is then allowed
     * to the emulator's Mac (10.0.2.2) and to localhost, nowhere else. HTTPS otherwise.
     */
    public val allowLocalCleartext: Boolean = false,
) {
    public enum class BuildType(override val wire: String) : WireValue {
        Release("release"),
        Debug("debug"),
    }

    /** The API's base URL, or null when [endpoint] is not one this build may send to. */
    internal fun apiBase(): HttpUrl? {
        val url = endpoint.toHttpUrlOrNull() ?: return null
        val allowed = url.isHttps || (allowLocalCleartext && url.host in LOCAL_HOSTS)
        return if (allowed) url else null
    }

    private companion object {
        val LOCAL_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")
    }
}
