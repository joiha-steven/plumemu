package com.plumemu

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.plumemu.telemetry.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings › Advanced › Export logs: the log file ([AppLog.snapshot]: its own file and this app's lines of logcat) to Android's
 * share sheet, through the app's FileProvider ([LOG_AUTHORITY_SUFFIX], `res/xml/log_export_paths.xml`,
 * which serves the export folder only). [onDone] gets false, on the main thread, when there is no
 * log, the file could not be written or nothing can share it.
 */
internal fun ComponentActivity.shareLogFile(onDone: (Boolean) -> Unit) {
    lifecycleScope.launch {
        val header = "Plumemu ${BuildConfig.VERSION_NAME} (version code ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})"
        val file = withContext(Dispatchers.IO) { AppLog.snapshot(this@shareLogFile, header) }
        if (file == null) return@launch onDone(false)
        val shared =
            try {
                val uri = FileProvider.getUriForFile(this@shareLogFile, packageName + LOG_AUTHORITY_SUFFIX, file)
                val send =
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, AppLog.EXPORT_NAME)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // The share sheet's preview and the app picked read the file through this grant.
                send.clipData = ClipData.newRawUri(AppLog.EXPORT_NAME, uri)
                startActivity(Intent.createChooser(send, getString(R.string.settings_export_logs_chooser)))
                true
            } catch (e: ActivityNotFoundException) {
                AppLog.w(TAG, "Log export: no app can share it (${e.javaClass.simpleName})")
                false
            } catch (e: IllegalArgumentException) {
                AppLog.w(TAG, "Log export: the file is outside the shared folder (${e.javaClass.simpleName})")
                false
            }
        onDone(shared)
    }
}

/** The FileProvider's authority after the package name; the same as in AndroidManifest.xml. */
internal const val LOG_AUTHORITY_SUFFIX = ".logs"

private const val TAG = "LogExport"
