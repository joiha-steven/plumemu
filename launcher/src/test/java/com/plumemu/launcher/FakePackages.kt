package com.plumemu.launcher

import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

/** Installs fake apps in Robolectric's PackageManager. */
internal object FakePackages {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    fun install(
        packageName: String,
        vararg activities: String,
        label: String = packageName,
        launcher: Boolean = false,
        isGame: Boolean = false,
        /** False: Android still has a record but the app is gone with its data kept (`pm uninstall -k`). */
        installed: Boolean = true,
    ) {
        val shadow = shadowOf(context.packageManager)
        val application = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = label
            // Every installed app carries FLAG_INSTALLED on a real device.
            flags = flags or ApplicationInfo.FLAG_INSTALLED
            // Android's game mark, both ways: the category from API 26, the older flag below.
            if (isGame) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) category = ApplicationInfo.CATEGORY_GAME
                @Suppress("DEPRECATION") // FLAG_IS_GAME is the only game mark below API 26.
                flags = flags or ApplicationInfo.FLAG_IS_GAME
            }
        }
        shadow.installPackage(PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = application
        })
        // Robolectric sets FLAG_INSTALLED itself on install; clear it on its stored copy.
        if (!installed) {
            shadow.getInternalMutablePackageInfo(packageName).applicationInfo!!.flags =
                application.flags and ApplicationInfo.FLAG_INSTALLED.inv()
        }
        for (activity in activities) {
            val info = ActivityInfo().apply {
                this.packageName = packageName
                name = activity
                exported = true
                applicationInfo = application
                nonLocalizedLabel = label
            }
            shadow.addOrUpdateActivity(info)
            if (launcher) {
                val filter = IntentFilter("android.intent.action.MAIN").apply { addCategory("android.intent.category.LAUNCHER") }
                shadow.addIntentFilterForActivity(ComponentName(packageName, activity), filter)
            }
        }
    }

    fun uninstall(packageName: String) {
        // Robolectric still returns a deleted package to MATCH_UNINSTALLED_PACKAGES, where Android forgets a package
        // uninstalled without -k entirely.
        // deletePackage drops the activities; removePackage then forgets the package itself.
        val shadow = shadowOf(context.packageManager)
        shadow.deletePackage(packageName)
        shadow.removePackage(packageName)
    }
}
