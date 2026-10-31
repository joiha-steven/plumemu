package com.plumemu.graph

import com.plumemu.telemetry.TelemetrySink
import com.plumemu.usage.updateCheckEventOf
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.plumemu.BuildConfig
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.settings.screen.SettingsUpdateActions
import com.plumemu.update.ApkDownloader
import com.plumemu.update.FileUpdateStore
import com.plumemu.update.NetworkKind
import com.plumemu.update.OkHttpUpdateHttp
import com.plumemu.update.PackageManagerApkInspector
import com.plumemu.update.SessionUpdateInstaller
import com.plumemu.update.Updates
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.io.File

/*
 * In-app updates' Android side: the manifest's address from the build,
 * the record in no-backup storage, downloads in the cache, the network from ConnectivityManager.
 * Built on first use (AppGraph.updates); nothing is read until a check or Settings asks.
 */

private const val UPDATE_TAG = "PlumUpdate"

/** Under `noBackupFilesDir`: the last check and what it found ([FileUpdateStore]). */
private const val UPDATE_RECORD_FILE = "update-record.json"

/** Under `cacheDir`: the APK being downloaded or checked; emptied when an install ends and at the first check of a process. */
private const val UPDATE_DOWNLOAD_FOLDER = "updates"

internal fun graphUpdates(
    context: Context,
    settings: StateFlow<PlumSettings?>,
    scope: CoroutineScope,
    io: CoroutineDispatcher,
    telemetry: TelemetrySink? = null,
): Updates {
    val app = context.applicationContext
    val http = OkHttpUpdateHttp(io)
    return Updates(
        scope = scope,
        io = io,
        checkDaily = { settings.filterNotNull().first().updates.checkDaily },
        network = { networkKind(app) },
        http = http,
        manifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
        installedVersionCode = { installedVersionCode(app) },
        deviceSdk = Build.VERSION.SDK_INT,
        store = FileUpdateStore(File(app.noBackupFilesDir, UPDATE_RECORD_FILE)) { Log.w(UPDATE_TAG, it) },
        downloader = ApkDownloader(http, File(app.cacheDir, UPDATE_DOWNLOAD_FOLDER), io),
        inspector = PackageManagerApkInspector(app),
        installer = SessionUpdateInstaller(app),
        wallClock = System::currentTimeMillis,
        onChecked = { manual, outcome -> telemetry?.track(updateCheckEventOf(manual, outcome)) },
        info = { Log.i(UPDATE_TAG, it) },
        log = { Log.w(UPDATE_TAG, it) },
    )
}

/**
 * None without an active network; Metered as Android judges it (mobile data, a hotspot marked
 * metered); Wifi for Wi-Fi or Ethernet that is not metered, the only kind the daily check uses
 * (the setting is "on Wi-Fi"); Unmetered for any other.
 */
internal fun networkKind(context: Context): NetworkKind {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkKind.None
    return try {
        val active = connectivity.activeNetwork ?: return NetworkKind.None
        val capabilities = connectivity.getNetworkCapabilities(active)
        networkKindOf(
            isMetered = connectivity.isActiveNetworkMetered,
            isWifiOrEthernet = capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)),
        )
    } catch (e: SecurityException) {
        Log.w(UPDATE_TAG, "Network state not readable")
        NetworkKind.None
    }
}

internal fun networkKindOf(
    isMetered: Boolean,
    isWifiOrEthernet: Boolean,
): NetworkKind =
    when {
        isMetered -> NetworkKind.Metered
        isWifiOrEthernet -> NetworkKind.Wifi
        else -> NetworkKind.Unmetered
    }

// `versionCode` is deprecated from API 28; it is read only below that level.
@Suppress("DEPRECATION")
private fun installedVersionCode(context: Context): Long =
    try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (e: PackageManager.NameNotFoundException) {
        BuildConfig.VERSION_CODE.toLong()
    }

/** What Settings › About drives of [updates]. */
internal fun settingsUpdateActions(updates: Updates): SettingsUpdateActions =
    object : SettingsUpdateActions {
        override fun checkNow() {
            updates.checkNow()
        }

        override fun startUpdate(allowMetered: Boolean) {
            updates.startUpdate(allowMetered)
        }

        override fun cancel() = updates.cancel()

        override fun openInstallPermission(): Boolean = updates.openInstallPermission()
    }
