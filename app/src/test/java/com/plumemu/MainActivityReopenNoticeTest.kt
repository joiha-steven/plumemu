package com.plumemu

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.app.NotificationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.NoticeLine
import com.plumemu.update.ReopenNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The main screen tells the reopen check that it resumed, and a notice posted after an update goes when Plumemu is in front. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class MainActivityReopenNoticeTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val shadowManager = shadowOf(app.getSystemService(NotificationManager::class.java))

    @Test
    fun resumingCountsForTheReopenCheck() {
        val before = ReopenNotice.resumeCount()
        Robolectric.buildActivity(MainActivity::class.java).setup()
        assertTrue(ReopenNotice.resumeCount() > before)
    }

    @Test
    fun startingPlumemuCancelsAPostedNotice() {
        assertTrue(ReopenNotice.post(app))
        assertEquals(1, shadowManager.allNotifications.size)
        Robolectric.buildActivity(MainActivity::class.java).setup()
        // The cancel runs off the main thread once the startup work reports the front.
        val until = System.nanoTime() + 10_000_000_000L
        while (shadowManager.allNotifications.isNotEmpty() && System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertEquals(0, shadowManager.allNotifications.size)
    }

    @Test
    fun aRecreatedActivityStillHearsAndroidsAnswer() {
        shadowManager.setNotificationsEnabled(false)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val noticeAsk = (app as PlumApplication).graph.updates.noticeAsk
        noticeAsk.ask(controller.get().noticePermission::ask)
        assertEquals(NoticeLine.Reason, (app as PlumApplication).graph.updates.view.value.notice)
        val code = shadowOf(controller.get()).peekNextStartedActivityForResult().requestCode
        controller.recreate()
        // The old Activity's asker is gone; Android's answer reaches the new one.
        controller.get().onRequestPermissionsResult(code, arrayOf(Manifest.permission.POST_NOTIFICATIONS), intArrayOf(PackageManager.PERMISSION_DENIED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(NoticeLine.NotificationsOff, (app as PlumApplication).graph.updates.view.value.notice)
    }
}
