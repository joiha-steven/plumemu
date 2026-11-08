package com.plumemu.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Android's notification settings for Plumemu, for the Updates panel's "Turn on notifications" button: Android's permission
 * question shows no gamepad focus and an A press can land on "Don't allow", so the person can allow it here instead.
 */
internal object NoticeSettings {
    /** Android 8+: the app's notification settings; before: the app's details screen, which has the Notifications entry. */
    fun intentFor(
        context: Context,
        sdk: Int = Build.VERSION.SDK_INT,
    ): Intent =
        if (sdk >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }

    /** False (and one log line) when the device has no such screen. */
    fun open(activity: Activity): Boolean =
        try {
            activity.startActivity(intentFor(activity))
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("PlumUpdate", "Notification settings could not open (${e.javaClass.simpleName})")
            false
        }
}
