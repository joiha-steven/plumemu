package com.plumemu.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.plumemu.usage.EXTRA_OPENED_AFTER_UPDATE
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.plumemu.PlumApplication
import java.io.File
import java.io.IOException

/** What Android said about an install session (PackageInstaller's status, reduced). */
sealed interface InstallStatus {
    /**
     * Android needs the person's yes on its own screen; [show] starts that screen and is false when it
     * could not. [Updates] calls it only while Plumemu is in front, so it never opens over a game.
     */
    class NeedsConfirm(val show: () -> Boolean) : InstallStatus

    data object Success : InstallStatus

    /** The person said no on Android's screen. */
    data object Aborted : InstallStatus

    /** [message] is Android's, for the log only. */
    data class Failed(val code: Int, val message: String?) : InstallStatus
}

/** Installs a checked APK; the result comes back later through [Updates.onInstallStatus]. Tests give a fake. */
interface UpdateInstaller {
    /** Android 8+: whether Plumemu may install apps (Install unknown apps). Always true before 8, where Android asks itself. */
    fun canRequestInstalls(): Boolean

    /** Opens Android's Install unknown apps screen for Plumemu; false when the device has none. */
    fun openInstallPermission(): Boolean

    /** Copies [file] into an install session and commits it. Throws [IOException] when the session cannot be written. */
    fun commit(
        file: File,
        versionCode: Long,
    )

    /** Plumemu will not open itself after this install ([ReopenMarker]): the person left it, or Android's screen never opened. */
    fun forgetReopen()
}

/**
 * [UpdateInstaller] on `PackageInstaller` sessions (needs `REQUEST_INSTALL_PACKAGES`).
 * From Android 12, `USER_ACTION_NOT_REQUIRED` lets an update install without Android's screen when
 * Plumemu installed the version being replaced; otherwise Android asks the person.
 */
class SessionUpdateInstaller(
    private val context: Context,
) : UpdateInstaller {
    override fun canRequestInstalls(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            false
        }
    }

    override fun forgetReopen() = ReopenMarker.clear(context)

    override fun commit(
        file: File,
        versionCode: Long,
    ) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(file.length())
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        val sessionId = installer.createSession(params)
        try {
            ReopenMarker.write(context, versionCode)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("plumemu-$versionCode.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                session.commit(UpdateInstallReceiver.statusSender(context, sessionId))
            }
        } catch (e: IOException) {
            installer.abandonSession(sessionId)
            ReopenMarker.clear(context)
            throw e
        } catch (e: SecurityException) {
            ReopenMarker.clear(context)
            // The permission was taken back between the check and the commit.
            installer.abandonSession(sessionId)
            throw IOException("install session refused", e)
        }
    }
}

/**
 * "Plumemu is installing its own update to [versionCode]", written just before the session is
 * committed. Android stops the app to replace it and then sends no session status to it (it is in
 * the stopped state); [UpdateRestartReceiver] opens the main screen only when this marker names the
 * version now installed, so an install from adb, a file manager or a store never opens Plumemu.
 */
internal object ReopenMarker {
    private const val FILE = "update-installing"

    /** Older than this, the marker is from an install that never finished: ignored and deleted. */
    const val MAX_AGE_MS = 60L * 60 * 1000

    fun write(
        context: Context,
        versionCode: Long,
    ) = File(context.noBackupFilesDir, FILE).writeText("$versionCode ${System.currentTimeMillis()}")

    fun clear(context: Context) {
        File(context.noBackupFilesDir, FILE).delete()
    }

    /** Reads and deletes the marker; true when it names [installedVersionCode] and is recent. */
    fun take(
        context: Context,
        installedVersionCode: Long,
    ): Boolean {
        val file = File(context.noBackupFilesDir, FILE)
        val text = if (file.exists()) runCatching { file.readText() }.getOrNull() else null
        file.delete()
        return shouldReopen(text, installedVersionCode, System.currentTimeMillis())
    }

    fun shouldReopen(
        marker: String?,
        installedVersionCode: Long,
        now: Long,
    ): Boolean {
        val parts = marker?.trim()?.split(' ') ?: return false
        val code = parts.getOrNull(0)?.toLongOrNull() ?: return false
        val at = parts.getOrNull(1)?.toLongOrNull() ?: return false
        return code == installedVersionCode && now >= at && now - at <= MAX_AGE_MS
    }
}

/**
 * `MY_PACKAGE_REPLACED` (not exported; Android sends it to the replaced app even though it was
 * stopped): after Plumemu's own update ([ReopenMarker]), the main screen opens again. From Android
 * 10 Android may refuse a start from the background; a home app is restarted by Android anyway.
 */
class UpdateRestartReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val installed =
            try {
                @Suppress("DEPRECATION") // versionCode is the only field before API 28.
                context.packageManager.getPackageInfo(context.packageName, 0).let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                return
            }
        if (!ReopenMarker.take(context, installed)) return
        Log.i("PlumUpdate", "Updated to $installed; opening Plumemu again")
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        try {
            // Marked, so the open is counted as one after an update (app_open.from, usage/UsageOpen.kt).
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_OPENED_AFTER_UPDATE, true))
        } catch (e: RuntimeException) {
            Log.w("PlumUpdate", "Could not reopen after the update (${e.javaClass.simpleName})")
        }
    }
}

/**
 * Where Android reports an install session (not exported; reached only through the session's
 * PendingIntent). Android's confirm screen is started here; every other status goes to
 * [Updates.onInstallStatus]. On success Android has already replaced the app: this runs in the new
 * version's process, which opens the main screen again.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val updates = (context.applicationContext as? PlumApplication)?.graph?.updates
        val reduced =
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val app = context.applicationContext
                    confirmIntent(intent)?.let { confirm -> InstallStatus.NeedsConfirm { startConfirm(app, confirm) } }
                        ?: InstallStatus.Failed(status, "no confirm screen")
                }
                // Rarely seen: Android stops the app before it could hear this; UpdateRestartReceiver reopens it.
                PackageInstaller.STATUS_SUCCESS -> InstallStatus.Success
                PackageInstaller.STATUS_FAILURE_ABORTED -> InstallStatus.Aborted
                else -> InstallStatus.Failed(status, message)
            }
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION && status != PackageInstaller.STATUS_SUCCESS) ReopenMarker.clear(context)
        when {
            updates != null -> updates.onInstallStatus(reduced)
            // No graph to ask whether Plumemu is in front: the receiver runs in Plumemu's process, so show it.
            reduced is InstallStatus.NeedsConfirm -> reduced.show()
            else -> Log.w(TAG, "Install status $status with no graph")
        }
    }

    @Suppress("DEPRECATION") // The typed getParcelableExtra exists only from API 33.
    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)

    private fun startConfirm(
        context: Context,
        confirm: Intent,
    ): Boolean {
        return try {
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Android's install screen could not open (${e.javaClass.simpleName})")
            false
        }
    }

    companion object {
        private const val TAG = "PlumUpdate"

        fun statusSender(
            context: Context,
            sessionId: Int,
        ): android.content.IntentSender {
            val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
            // Mutable: the installer adds the status extras to it.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
        }
    }
}
