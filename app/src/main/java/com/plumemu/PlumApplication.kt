package com.plumemu

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import com.plumemu.graph.AppGraph
import com.plumemu.graph.BuildFeatures
import com.plumemu.graph.ContentReadHost
import com.plumemu.graph.ContentReadWork
import com.plumemu.graph.followUsageStatistics
import com.plumemu.graph.storedUsageStatistics
import com.plumemu.graph.telemetryConfig
import com.plumemu.language.AppLocale
import com.plumemu.scraper.DeveloperCredentials
import com.plumemu.scraper.work.ScraperHost
import com.plumemu.scraper.work.ScraperWork
import com.plumemu.telemetry.AppLog
import com.plumemu.telemetry.CrashCapture
import com.plumemu.telemetry.Telemetry
import com.plumemu.telemetry.TelemetryHost

/**
 * Holds the one [AppGraph] of the process. Creating it reads nothing, so startup stays light. It
 * is the scrape worker's [ScraperHost]: a run gets its scraper, games and folders from the graph
 * (null, so no request, without a usable ScreenScraper account). It is the statistics send
 * worker's [TelemetryHost] too.
 *
 * Open for the app's Robolectric tests only (`TestPlumApplication`, robolectric.properties), which
 * make no [Telemetry], so no test sends anything (nor installs the crash handler or the log file).
 */
open class PlumApplication : Application(), Configuration.Provider, ScraperHost, TelemetryHost, ContentReadHost {
    lateinit var graph: AppGraph
        private set

    /** Usage statistics; started here, so the first launch is counted at once. */
    final override var telemetry: Telemetry? = null
        private set

    // WorkManager's startup initializer is removed in the manifest; this builds it on
    // first use instead.
    override val workManagerConfiguration: Configuration =
        Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()

    // Settings › Advanced › Language before Android 13 (AppLocale): text read from the application's own
    // context (the Shortcuts list's name) follows a new pick from the next start; Activities at once.
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLocale.wrap(base))

    override fun onCreate() {
        super.onCreate()
        val statistics = makeTelemetry()
        telemetry = statistics
        graph = AppGraph(this, telemetry = statistics, problemReports = statistics?.problems)
        if (statistics != null) {
            // Reads its files and sends on its own background scope; nothing here waits for it.
            statistics.start()
            graph.followUsageStatistics(statistics)
        }
        // Shows in CI and on a device whether the build got the credentials; never a value.
        val state = if (DeveloperCredentials.buildHasCredentials) "present" else "absent"
        Log.i(TAG, "ScreenScraper developer credentials: $state")
    }

    /** The process's statistics; the tests' Application answers null. */
    protected open fun makeTelemetry(): Telemetry? {
        // The log file is local (Settings › Advanced › Export logs): kept in every build.
        AppLog.install(this)
        // A build from source names no statistics server: no crash files, nothing started, nothing sent.
        if (!BuildFeatures.hasStatistics) return null
        val config = telemetryConfig()
        // First, so a crash in the rest of startup is caught and the log file has its lines.
        CrashCapture.install(this, config)
        // The stored switch is read at start, before anything is queued or sent (the graph exists by then).
        return Telemetry.create(this, config, storedSwitch = { graph.storedUsageStatistics() })
    }

    override suspend fun scraperWork(): ScraperWork? = graph.scrapes.scraperWork()

    override fun contentReadWork(): ContentReadWork? = graph.content.work()

    private companion object {
        const val TAG = "PlumApplication"
    }
}
