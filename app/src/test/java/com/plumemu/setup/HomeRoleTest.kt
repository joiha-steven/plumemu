package com.plumemu.setup

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.plumemu.MainActivity
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The intent behind "Yes" per Android version (RoleManager on 10+, Home settings on 6 to 9, none
 * when the device has neither), and the manifest that lets Android offer Plumemu as home app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeRoleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    @Config(sdk = [23])
    fun android6OpensHomeSettingsWhenSomethingAnswersIt() = homeSettingsIntentIsUsed()

    @Test
    @Config(sdk = [28])
    fun android9OpensHomeSettingsWhenSomethingAnswersIt() = homeSettingsIntentIsUsed()

    private fun homeSettingsIntentIsUsed() {
        assertNull("nothing answers Home settings: the prompt explains instead", homeRoleIntent(context))
        val settingsApp = ComponentName("com.android.settings", "com.android.settings.HomeSettings")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(settingsApp)
            addIntentFilterForActivity(settingsApp, IntentFilter(Settings.ACTION_HOME_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
        assertEquals(Settings.ACTION_HOME_SETTINGS, homeRoleIntent(context)?.action)
    }

    @Test
    @Config(sdk = [34])
    fun android10AndLaterAskForTheHomeRole() {
        val roles = context.getSystemService(RoleManager::class.java)
        assertNull("role not available on this device: the prompt explains instead", homeRoleIntent(context))
        shadowOf(roles).addAvailableRole(RoleManager.ROLE_HOME)
        val intent = homeRoleIntent(context)
        assertNotNull(intent)
        assertEquals(RoleManager.ROLE_HOME, intent?.getStringExtra(EXTRA_ROLE_NAME))
    }

    @Test
    @Config(sdk = [34])
    fun theRoleHeldMeansNothingToAsk() {
        val roles = context.getSystemService(RoleManager::class.java)
        assertEquals(false, isHomeApp(context))
        shadowOf(roles).addHeldRole(RoleManager.ROLE_HOME)
        assertEquals(true, isHomeApp(context))
    }

    @Test
    @Config(sdk = [34])
    fun mainActivityAnswersHomeAndKeepsOneInstance() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName)
        val resolved = context.packageManager.resolveActivity(home, 0)
        assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals("Home brings back the one main screen", ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
    }

    private companion object {
        /** `Intent.EXTRA_ROLE_NAME`, hidden in the SDK; the extra createRequestRoleIntent sets. */
        const val EXTRA_ROLE_NAME = "android.intent.extra.ROLE_NAME"
    }
}
