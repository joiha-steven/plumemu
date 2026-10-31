package com.plumemu.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.plumemu.launcher.RecordingLog
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** An app that ships no icon is told from one that has its own, so the screen can draw Plumemu's tile instead of the robot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 34])
class AppDefaultIconTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val log = RecordingLog()

    private fun install(pkg: String, iconResource: Int, appIconResource: Int = 0): ComponentName {
        val application = ApplicationInfo().apply {
            packageName = pkg
            icon = appIconResource
        }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = application
        })
        val activity = ActivityInfo().apply {
            packageName = pkg
            name = "$pkg.Main"
            icon = iconResource
            applicationInfo = application
        }
        shadowOf(context.packageManager).addOrUpdateActivity(activity)
        return ComponentName(pkg, activity.name)
    }

    @Test
    fun noIconResourceAndTheSystemDefaultResourceAreDefaults() {
        assertTrue(AppIcons.isDefaultIconResource(0))
        assertTrue(AppIcons.isDefaultIconResource(android.R.drawable.sym_def_app_icon))
        assertFalse(AppIcons.isDefaultIconResource(0x7f010001))
    }

    @Test
    fun anActivityWithNoIconOfItsOwnIsTheDefault() {
        val drawable = ColorDrawable(Color.RED)
        assertTrue(AppIcons.isSystemDefault(context.packageManager, install("com.dual.spike", 0), drawable))
        assertTrue(
            "the application's icon counts when the activity has none: here the system's",
            AppIcons.isSystemDefault(context.packageManager, install("com.sys.def", 0, android.R.drawable.sym_def_app_icon), drawable),
        )
    }

    @Test
    fun anActivityWithItsOwnIconIsNotTheDefault() {
        val drawable = ColorDrawable(Color.RED)
        assertFalse(AppIcons.isSystemDefault(context.packageManager, install("com.own.icon", 0x7f010001), drawable))
        assertFalse(AppIcons.isSystemDefault(context.packageManager, install("com.app.icon", 0, 0x7f010002), drawable))
    }

    @Test
    fun aDefaultIconIsAnsweredNoneNotCachedAndNotAskedAgainUntilThePackageChanges() = runTest {
        val asked = mutableListOf<ComponentName>()
        var isDefault = true
        val icons = PackageManagerAppIcons(
            iconOf = { component ->
                asked += component
                ColorDrawable(Color.RED)
            },
            maxBytes = 1024 * 1024,
            log = log,
            io = UnconfinedTestDispatcher(),
            isDefault = { _, _ -> isDefault },
        )
        val app = LaunchableApp("Dual Spike", "com.dual.spike", "com.dual.spike.Main")
        assertEquals(AppIcon.None, icons.load(app, 48))
        assertEquals(AppIcon.None, icons.load(app, 48))
        assertEquals("asked once", 1, asked.size)
        assertEquals(null, icons.cached(app, 48))
        isDefault = false
        icons.invalidate("com.dual.spike")
        assertTrue("an update that ships an icon shows it", icons.load(app, 48) is AppIcon.Picture)
    }
}
