package com.plumemu.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.plumemu.launcher.play.hasUsageAccess

/** Android's Usage access screen, for Statistics > Exact play time: whether Plumemu may read it, and a way to the screen to allow it. */
internal object UsageAccess {
    fun isGranted(context: Context): Boolean = hasUsageAccess(context)

    /** Opens the screen on Plumemu's own entry where the device supports it, else on the list; false when the device has none. */
    fun open(activity: Activity): Boolean {
        val intent = intentFor(activity) ?: return false
        return try {
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("PlumUsageAccess", "Usage access screen could not open (${e.javaClass.simpleName})")
            false
        }
    }

    private fun intentFor(context: Context): Intent? {
        val own = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        val list = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        return listOf(own, list).firstOrNull { context.packageManager.resolveActivity(it, 0) != null }
    }
}
