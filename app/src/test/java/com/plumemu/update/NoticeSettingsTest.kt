package com.plumemu.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** "Turn on notifications" opens Android's notification settings for Plumemu: the app's own screen on Android 8+, its details screen before. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class NoticeSettingsTest {
    private class NoScreen : Activity() {
        override fun startActivity(intent: Intent) = throw ActivityNotFoundException("none")
    }

    @Test
    fun onAndroid8AndLaterItIsTheAppNotificationSettings() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertTrue(NoticeSettings.open(activity))
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(activity.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    @Config(sdk = [23])
    fun beforeAndroid8ItIsTheAppDetailsScreen() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertTrue(NoticeSettings.open(activity))
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals(Uri.parse("package:${activity.packageName}"), intent.data)
        assertNull(intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun theIntentFollowsTheGivenLevel() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, NoticeSettings.intentFor(activity, 26).action)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, NoticeSettings.intentFor(activity, 25).action)
    }

    @Test
    fun aDeviceWithoutTheScreenGetsFalseNotACrash() {
        val activity = Robolectric.buildActivity(NoScreen::class.java).setup().get()
        assertFalse(NoticeSettings.open(activity))
    }
}
